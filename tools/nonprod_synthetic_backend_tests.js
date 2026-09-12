#!/usr/bin/env node
'use strict';

/*
 * Isolated RTC Community backend authorization test runner.
 *
 * Commands:
 *   node tools/nonprod_synthetic_backend_tests.js smoke
 *   node tools/nonprod_synthetic_backend_tests.js support-cases
 *   node tools/nonprod_synthetic_backend_tests.js operations-lifecycle
 *   node tools/nonprod_synthetic_backend_tests.js alerts
 *   node tools/nonprod_synthetic_backend_tests.js community-media-storage
 *   node tools/nonprod_synthetic_backend_tests.js profile-media-storage
 *
 * It is deliberately fail-closed to the approved zero-data test project. Runtime
 * configuration, generated synthetic credentials, TOTP seeds, state, and results
 * live only in ignored owner-only local files. No token, password, key, or factor
 * seed is emitted to stdout or written to the repository.
 */

const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');

const PROJECT_REF = 'eqwstpdjoineycrkhpht';
const MARKER = 'SYNTHETIC-RTC-20260825';
const ROOT = path.resolve(__dirname, '..');
const LOCAL_RUNTIME = path.join(ROOT, 'runtime.local.properties');
const TEST_HOME = path.join(os.homedir(), '.config', 'rtc-community-test');
const CREDENTIAL_VAULT = path.join(TEST_HOME, 'nonprod-synthetic-auth.tsv');
const STATE_PATH = path.join(TEST_HOME, 'nonprod-backend-test-state.json');
const RESULT_PATH = path.join(TEST_HOME, 'nonprod-backend-test-results.json');
const MFA_VAULTS = {
  SYSTEM_ADMIN: path.join(TEST_HOME, 'nonprod-system-admin-totp.json'),
  SYSTEM_ADMIN_APPROVER: path.join(TEST_HOME, 'nonprod-system-admin-approver-totp.json'),
};
const REQUIRED_ROLES = [
  'RESIDENT_A', 'RESIDENT_B', 'CASE_STAFF', 'CONTENT_EDITOR',
  'MODERATOR', 'EVIDENCE_REVIEWER', 'SYSTEM_ADMIN', 'SYSTEM_ADMIN_APPROVER',
];

function fail(message) { throw new Error(message); }
function ownOnly(filePath) {
  const mode = fs.statSync(filePath).mode & 0o777;
  if ((mode & 0o077) !== 0) fail(`Refusing ${path.basename(filePath)}: file is not owner-only.`);
}
function writePrivateJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true, mode: 0o700 });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  fs.chmodSync(filePath, 0o600);
}
function loadProperties() {
  if (!fs.existsSync(LOCAL_RUNTIME)) fail('Ignored non-production runtime.local.properties is required.');
  const props = {};
  for (const raw of fs.readFileSync(LOCAL_RUNTIME, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const index = line.indexOf('=');
    if (index > 0) props[line.slice(0, index).trim()] = line.slice(index + 1).trim();
  }
  const url = props['supabase.nonproduction.url'];
  const key = props['supabase.nonproduction.publishableKey'];
  if (!url || !key) fail('Missing non-production Supabase URL or publishable key.');
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || parsed.hostname !== `${PROJECT_REF}.supabase.co`) {
    fail('Refusing to run outside the approved isolated non-production project.');
  }
  return { url: parsed.origin, key };
}
function loadCredentials() {
  if (!fs.existsSync(CREDENTIAL_VAULT)) fail('Synthetic credential vault is missing.');
  ownOnly(CREDENTIAL_VAULT);
  const entries = new Map();
  for (const line of fs.readFileSync(CREDENTIAL_VAULT, 'utf8').split(/\r?\n/)) {
    if (!line) continue;
    const [role, email, password, ...extra] = line.split('|');
    if (!role || !email || !password || extra.length) fail('Synthetic credential vault has an invalid record.');
    if (entries.has(role)) fail(`Duplicate synthetic role in credential vault: ${role}.`);
    entries.set(role, { email, password });
  }
  if (entries.size !== REQUIRED_ROLES.length || REQUIRED_ROLES.some((role) => !entries.has(role))) {
    fail('Synthetic credential vault does not exactly match the approved role allow-list.');
  }
  return entries;
}
function decodeJwt(accessToken) {
  const parts = accessToken.split('.');
  if (parts.length !== 3) fail('Auth did not return a JWT-shaped access token.');
  const data = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(data.padEnd(Math.ceil(data.length / 4) * 4, '='), 'base64').toString('utf8'));
}
function base32Decode(value) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0; let buffer = 0; const bytes = [];
  for (const char of value.toUpperCase().replace(/=+$/g, '').replace(/\s/g, '')) {
    const index = chars.indexOf(char);
    if (index < 0) fail('TOTP factor vault contains an invalid base32 secret.');
    buffer = (buffer << 5) | index; bits += 5;
    if (bits >= 8) { bytes.push((buffer >>> (bits - 8)) & 0xff); bits -= 8; }
  }
  return Buffer.from(bytes);
}
function totp(secret) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30)));
  const digest = crypto.createHmac('sha1', base32Decode(secret)).update(counter).digest();
  const offset = digest[digest.length - 1] & 15;
  return String((digest.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(6, '0');
}
async function nextTotp(secret, previousCode) {
  const deadline = Date.now() + 35_000;
  let code = previousCode;
  while (Date.now() < deadline && code === previousCode) {
    await new Promise((resolve) => setTimeout(resolve, 1_000));
    code = totp(secret);
  }
  if (code === previousCode) fail('Timed out waiting for a fresh isolated TOTP window.');
  return code;
}
async function request(runtime, endpoint, { method = 'POST', token, body, headers = {} } = {}) {
  const requestHeaders = { apikey: runtime.key, ...headers };
  if (token) requestHeaders.authorization = `Bearer ${token}`;
  if (body !== undefined) requestHeaders['content-type'] = 'application/json';
  const response = await fetch(`${runtime.url}${endpoint}`, {
    method, headers: requestHeaders, body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = { non_json: true }; }
  }
  return { ok: response.ok, status: response.status, data };
}
async function signIn(runtime, credential) {
  const result = await request(runtime, '/auth/v1/token?grant_type=password', {
    body: { email: credential.email, password: credential.password },
  });
  if (!result.ok || typeof result.data?.access_token !== 'string' || typeof result.data?.user?.id !== 'string') {
    fail(`Isolated synthetic sign-in failed with HTTP ${result.status}.`);
  }
  const claims = decodeJwt(result.data.access_token);
  if (claims.sub !== result.data.user.id || claims.aal !== 'aal1') fail('Expected a matching fresh `aal1` session.');
  return { token: result.data.access_token, userId: result.data.user.id, claims };
}
function loadMfa(role) {
  const filePath = MFA_VAULTS[role];
  if (!filePath || !fs.existsSync(filePath)) fail(`MFA vault for ${role} is missing.`);
  ownOnly(filePath);
  const record = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  if (record.projectRef !== PROJECT_REF || typeof record.factorId !== 'string' || typeof record.secret !== 'string') {
    fail(`MFA vault for ${role} does not match the isolated project.`);
  }
  return record;
}
async function stepUp(runtime, session, role) {
  const factor = loadMfa(role);
  let challenge = await request(runtime, `/auth/v1/factors/${encodeURIComponent(factor.factorId)}/challenge`, {
    token: session.token, body: {},
  });
  if (!challenge.ok || typeof challenge.data?.id !== 'string') fail(`MFA challenge failed with HTTP ${challenge.status}.`);
  const initialCode = totp(factor.secret);
  let verified = await request(runtime, `/auth/v1/factors/${encodeURIComponent(factor.factorId)}/verify`, {
    token: session.token, body: { challenge_id: challenge.data.id, code: initialCode },
  });
  if (!verified.ok && verified.status === 422) {
    const retryCode = await nextTotp(factor.secret, initialCode);
    challenge = await request(runtime, `/auth/v1/factors/${encodeURIComponent(factor.factorId)}/challenge`, {
      token: session.token, body: {},
    });
    if (!challenge.ok || typeof challenge.data?.id !== 'string') fail(`MFA retry challenge failed with HTTP ${challenge.status}.`);
    verified = await request(runtime, `/auth/v1/factors/${encodeURIComponent(factor.factorId)}/verify`, {
      token: session.token, body: { challenge_id: challenge.data.id, code: retryCode },
    });
  }
  if (!verified.ok || typeof verified.data?.access_token !== 'string') fail(`MFA verification failed with HTTP ${verified.status}.`);
  const claims = decodeJwt(verified.data.access_token);
  if (claims.sub !== session.userId || claims.aal !== 'aal2') fail('MFA verification did not produce a matching `aal2` session.');
  return { token: verified.data.access_token, userId: session.userId, claims };
}
async function rpc(runtime, token, functionName, args = {}) {
  return request(runtime, `/rest/v1/rpc/${functionName}`, { token, body: args });
}
async function table(runtime, token, tableName, query = 'select=*&limit=1') {
  return request(runtime, `/rest/v1/${tableName}?${query}`, { method: 'GET', token });
}
async function storageObject(runtime, token, bucket, objectPath, { method = 'GET', body, contentType } = {}) {
  const requestHeaders = { apikey: runtime.key, authorization: `Bearer ${token}` };
  if (contentType) requestHeaders['content-type'] = contentType;
  const encodedPath = objectPath.split('/').map(encodeURIComponent).join('/');
  const response = await fetch(`${runtime.url}/storage/v1/object/${encodeURIComponent(bucket)}/${encodedPath}`, {
    method, headers: requestHeaders, body,
  });
  // Deliberately do not retain or print object bytes; policy assertions need status only.
  await response.arrayBuffer();
  return { ok: response.ok, status: response.status, data: null };
}
async function storageDelete(runtime, token, bucket, objectPaths) {
  const response = await fetch(`${runtime.url}/storage/v1/object/${encodeURIComponent(bucket)}`, {
    method: 'DELETE',
    headers: { apikey: runtime.key, authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ prefixes: objectPaths }),
  });
  // Match the pinned Android Storage client: bucket endpoint plus a JSON prefixes array.
  // Do not retain or print server response content or object metadata.
  await response.arrayBuffer();
  return { ok: response.ok, status: response.status, data: null };
}
async function storageObjectInfo(runtime, token, bucket, objectPath) {
  const encodedPath = objectPath.split('/').map(encodeURIComponent).join('/');
  const response = await fetch(`${runtime.url}/storage/v1/object/info/${encodeURIComponent(bucket)}/${encodedPath}`, {
    method: 'GET', headers: { apikey: runtime.key, authorization: `Bearer ${token}` },
  });
  // Verify Storage metadata rather than cached content delivery; response data is discarded.
  await response.arrayBuffer();
  return { ok: response.ok, status: response.status, data: null };
}
function errorClass(result) {
  const raw = result.data && typeof result.data === 'object' ? `${result.data.code || ''}/${result.data.message || ''}` : '';
  return raw.slice(0, 220);
}
function firstRow(data) { return Array.isArray(data) && data.length > 0 ? data[0] : null; }
class Results {
  constructor(command) { this.command = command; this.items = []; }
  pass(name, detail) { this.items.push({ name, outcome: 'PASS', detail }); }
  fail(name, detail) { this.items.push({ name, outcome: 'FAIL', detail }); }
  expectOk(name, result) {
    if (result.ok) this.pass(name, `HTTP ${result.status}`);
    else this.fail(name, `expected success, got HTTP ${result.status} (${errorClass(result)})`);
    return result.ok;
  }
  expectDenied(name, result) {
    if (!result.ok) this.pass(name, `denied at server boundary: HTTP ${result.status}`);
    else this.fail(name, `expected server denial, got HTTP ${result.status}`);
    return !result.ok;
  }
  expectNoDisclosure(name, result) {
    if (!result.ok) this.pass(name, `direct access denied: HTTP ${result.status}`);
    else if (Array.isArray(result.data) && result.data.length === 0) this.pass(name, 'direct read returned no rows');
    else this.fail(name, `expected no direct disclosure, got HTTP ${result.status}`);
  }
  expectNoMutation(name, result) {
    if (!result.ok) this.pass(name, `mutation denied at server boundary: HTTP ${result.status}`);
    else if (Array.isArray(result.data) && result.data.length === 0) this.pass(name, 'mutation returned no affected rows');
    else this.fail(name, `expected no mutation, got HTTP ${result.status}`);
  }
  summary() { return { passed: this.items.filter((x) => x.outcome === 'PASS').length, failed: this.items.filter((x) => x.outcome === 'FAIL').length }; }
}
async function sessions(runtime, credentials) {
  const output = {};
  for (const role of REQUIRED_ROLES) output[role] = await signIn(runtime, credentials.get(role));
  return output;
}
async function smoke(runtime, auth, results) {
  results.expectOk('anonymous public directory metrics', await rpc(runtime, null, 'get_public_directory_metrics'));
  const search = await rpc(runtime, null, 'search_public_directory', { p_query: 'synthetic-no-match-20260825', p_limit: 10, p_offset: 0 });
  if (results.expectOk('anonymous public directory non-match search', search) && Array.isArray(search.data) && search.data.length === 0) {
    results.pass('anonymous public directory non-match result is empty', 'zero rows');
  } else if (search.ok) {
    results.fail('anonymous public directory non-match result is empty', 'expected zero rows');
  }
  results.expectDenied('anonymous protected support-case RPC', await rpc(runtime, null, 'list_my_support_cases'));
  for (const [role, session] of Object.entries(auth)) {
    results.expectNoDisclosure(`${role} direct community_cases read`, await table(runtime, session.token, 'community_cases', 'select=id&limit=1'));
    results.expectNoDisclosure(`${role} direct case_messages read`, await table(runtime, session.token, 'case_messages', 'select=id&limit=1'));
  }
  results.expectDenied('resident access-management search', await rpc(runtime, auth.RESIDENT_A.token, 'access_search_verified_account', { email_query: 'resident-b@rtc-nonprod.test' }));
  results.expectDenied('System Administrator aal1 access-management search', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'access_search_verified_account', { email_query: 'resident-b@rtc-nonprod.test' }));
}
function loadState() {
  if (!fs.existsSync(STATE_PATH)) return { projectRef: PROJECT_REF, marker: MARKER };
  ownOnly(STATE_PATH);
  const state = JSON.parse(fs.readFileSync(STATE_PATH, 'utf8'));
  if (state.projectRef !== PROJECT_REF || state.marker !== MARKER) fail('Local test state does not belong to this isolated test run.');
  return state;
}
async function supportCases(runtime, auth, results) {
  const state = loadState();
  if (!state.supportCaseId) {
    const submitted = await rpc(runtime, auth.RESIDENT_A.token, 'submit_support_case', {
      p_title: `${MARKER} resident support case`,
      p_description: `${MARKER} synthetic authorization test only.`,
      p_category: 'SYNTHETIC_TEST', p_priority: 3, p_location_label: 'Synthetic Locality', p_details: { marker: MARKER },
    });
    if (!results.expectOk('Resident A submits synthetic support case', submitted) || typeof submitted.data !== 'string') return;
    state.supportCaseId = submitted.data;
    writePrivateJson(STATE_PATH, state);
  } else {
    results.pass('resume marked synthetic support case', 'using ignored local state; no duplicate case created');
  }
  results.expectDenied('Resident B cannot read Resident A support-case messages', await rpc(runtime, auth.RESIDENT_B.token, 'list_support_case_messages', { p_case_id: state.supportCaseId }));
  results.expectDenied('Resident B cannot message Resident A support case', await rpc(runtime, auth.RESIDENT_B.token, 'add_support_case_message', { p_case_id: state.supportCaseId, p_body: `${MARKER} prohibited cross-resident message`, p_attachments: [] }));
  results.expectDenied('unassigned CASE_STAFF cannot read Resident A support-case messages', await rpc(runtime, auth.CASE_STAFF.token, 'list_support_case_messages', { p_case_id: state.supportCaseId }));
  results.expectDenied('System Administrator aal1 cannot assign support case', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'assign_support_case', { p_case_id: state.supportCaseId, p_staff_id: auth.CASE_STAFF.userId, p_note: `${MARKER} denied without MFA` }));
  const adminAal2 = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  results.expectOk('System Administrator aal2 assigns CASE_STAFF', await rpc(runtime, adminAal2.token, 'assign_support_case', { p_case_id: state.supportCaseId, p_staff_id: auth.CASE_STAFF.userId, p_note: `${MARKER} controlled synthetic assignment` }));
  const assigned = await rpc(runtime, auth.CASE_STAFF.token, 'list_assigned_support_cases');
  if (results.expectOk('assigned CASE_STAFF lists assigned support case', assigned) && Array.isArray(assigned.data) && assigned.data.some((row) => row.id === state.supportCaseId)) {
    results.pass('assigned CASE_STAFF sees only assigned synthetic case', 'case ID found through guarded RPC');
  } else if (assigned.ok) results.fail('assigned CASE_STAFF sees only assigned synthetic case', 'expected assigned case was absent');
  results.expectOk('assigned CASE_STAFF updates synthetic support case', await rpc(runtime, auth.CASE_STAFF.token, 'update_assigned_support_case_state', { p_case_id: state.supportCaseId, p_state: 'IN_PROGRESS', p_note: `${MARKER} assigned staff state update` }));
  const residentThread = await rpc(runtime, auth.RESIDENT_A.token, 'list_support_case_messages', { p_case_id: state.supportCaseId });
  if (results.expectOk('Resident A reads own support-case thread', residentThread) && Array.isArray(residentThread.data) && residentThread.data.some((row) => String(row.body || '').includes(MARKER))) {
    results.pass('Resident A sees assigned-staff synthetic update', 'guarded message thread includes marker');
  } else if (residentThread.ok) results.fail('Resident A sees assigned-staff synthetic update', 'expected marker message was absent');
}
async function communityModeration(runtime, auth, results) {
  const state = loadState();
  results.expectOk('Resident A accepts community guidelines', await rpc(runtime, auth.RESIDENT_A.token, 'accept_community_guidelines'));
  results.expectOk('Resident B accepts community guidelines', await rpc(runtime, auth.RESIDENT_B.token, 'accept_community_guidelines'));
  if (!state.communityPostId) {
    const draft = await rpc(runtime, auth.RESIDENT_A.token, 'create_community_post_draft', { p_body: `${MARKER} resident post for moderation authorization.` });
    if (!results.expectOk('Resident A creates marked community post draft', draft) || typeof draft.data !== 'string') return;
    const published = await rpc(runtime, auth.RESIDENT_A.token, 'publish_community_post', { p_post_id: draft.data });
    if (!results.expectOk('Resident A publishes marked community post', published) || typeof published.data !== 'string') return;
    state.communityPostId = published.data;
    writePrivateJson(STATE_PATH, state);
  } else {
    results.pass('resume marked community post', 'using ignored local state; no duplicate post created');
  }
  results.expectDenied('Resident A cannot report own community post', await rpc(runtime, auth.RESIDENT_A.token, 'report_community_post', { p_post_id: state.communityPostId, p_reason_code: 'SPAM', p_detail: `${MARKER} prohibited self-report` }));
  results.expectDenied('Resident B cannot moderate community post', await rpc(runtime, auth.RESIDENT_B.token, 'moderate_community_item', { p_subject_type: 'POST', p_subject_id: state.communityPostId, p_action_type: 'HIDE', p_reason: `${MARKER} prohibited resident moderation`, p_pinned_until: null }));
  results.expectDenied('Resident B cannot access moderation queue', await rpc(runtime, auth.RESIDENT_B.token, 'moderation_list_queue', { p_state: 'OPEN', p_limit: 20 }));
  if (!state.moderationReportId) {
    const report = await rpc(runtime, auth.RESIDENT_B.token, 'report_community_post', { p_post_id: state.communityPostId, p_reason_code: 'OTHER', p_detail: `${MARKER} synthetic report for moderation access test` });
    if (!results.expectOk('Resident B reports Resident A community post', report) || typeof report.data !== 'string') return;
    state.moderationReportId = report.data;
    writePrivateJson(STATE_PATH, state);
  } else {
    results.pass('resume marked community report', 'using ignored local state; no duplicate report created');
  }
  results.expectDenied('Resident B cannot decide community report', await rpc(runtime, auth.RESIDENT_B.token, 'moderation_decide_report', { p_report_id: state.moderationReportId, p_decision: 'DISMISS', p_reason: `${MARKER} prohibited resident decision` }));
  const queue = await rpc(runtime, auth.MODERATOR.token, 'moderation_list_queue', { p_state: 'OPEN', p_limit: 20 });
  if (results.expectOk('MODERATOR lists marked community report', queue) && Array.isArray(queue.data) && queue.data.some((row) => row.report_id === state.moderationReportId)) {
    results.pass('MODERATOR queue contains marked report', 'report ID found through guarded queue RPC');
  } else if (queue.ok) results.fail('MODERATOR queue contains marked report', 'expected report was absent');
  if (!state.moderationDecisionCompleted) {
    const decision = await rpc(runtime, auth.MODERATOR.token, 'moderation_decide_report', { p_report_id: state.moderationReportId, p_decision: 'DISMISS', p_reason: `${MARKER} controlled moderator dismissal` });
    if (results.expectOk('MODERATOR dismisses marked community report', decision)) {
      state.moderationDecisionCompleted = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else {
    results.pass('resume marked moderation decision', 'decision was already recorded in ignored local state');
  }
}

async function publicCommunityVisibility(runtime, auth, results) {
  const state = loadState();
  if (!state.communityPostId) fail('Public Community visibility test requires the marked published post from the moderation slice.');
  const publishedSearch = await rpc(runtime, null, 'search_public_directory', { p_query: 'synthetic-rtc-20260825', p_limit: 20, p_offset: 0 });
  if (results.expectOk('anonymous search returns published marked Community post', publishedSearch) && Array.isArray(publishedSearch.data) && publishedSearch.data.some((row) => row.result_type === 'COMMUNITY' && row.result_id === state.communityPostId)) {
    results.pass('anonymous public search exposes published Community post only through search contract', 'published marked post is discoverable');
  } else if (publishedSearch.ok) results.fail('anonymous public search exposes published marked Community post only through search contract', 'expected published marked post was absent');
  if (!state.communityUnpublishedDraftId) {
    const draft = await rpc(runtime, auth.RESIDENT_A.token, 'create_community_post_draft', { p_body: 'SYNTHETIC-RTC-20260825-HIDDEN-DRAFT unpublished visibility test' });
    if (!results.expectOk('Resident A creates marked unpublished Community draft', draft) || typeof draft.data !== 'string') return;
    state.communityUnpublishedDraftId = draft.data;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume marked unpublished Community draft', 'using ignored local state; no duplicate draft created');
  const hiddenSearch = await rpc(runtime, null, 'search_public_directory', { p_query: 'synthetic-rtc-20260825-hidden-draft', p_limit: 20, p_offset: 0 });
  if (results.expectOk('anonymous search executes against unpublished draft marker', hiddenSearch) && Array.isArray(hiddenSearch.data) && hiddenSearch.data.length === 0) {
    results.pass('anonymous public search does not disclose unpublished Community draft', 'zero rows');
  } else if (hiddenSearch.ok) results.fail('anonymous public search does not disclose unpublished Community draft', 'expected zero rows');
  const hiddenDirect = await table(runtime, null, 'community_posts', `select=id&id=eq.${encodeURIComponent(state.communityUnpublishedDraftId)}`);
  if (hiddenDirect.ok && Array.isArray(hiddenDirect.data) && hiddenDirect.data.length === 0) results.pass('anonymous direct Community table read does not disclose unpublished draft', 'zero rows');
  else results.fail('anonymous direct Community table read does not disclose unpublished draft', `expected zero rows, got HTTP ${hiddenDirect.status}`);
}

async function communityMediaStorage(runtime, auth, results) {
  const state = loadState();
  if (!state.communityUnpublishedDraftId) fail('Community Storage test requires the marked unpublished draft from the public-visibility slice.');
  const bucket = 'rtc-community-media';
  const objectPath = `${auth.RESIDENT_A.userId}/${state.communityUnpublishedDraftId}/synthetic-storage-policy.png`;
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL9WAAAAABJRU5ErkJggg==', 'base64');
  let markerUploaded = false;
  try {
    // A prior interrupted run may have left only this bounded synthetic marker. Try
    // cleanup through the same API the Android client uses, but do not mask a later
    // upload or policy failure if that stale cleanup is denied.
    await storageDelete(runtime, auth.RESIDENT_A.token, bucket, [objectPath]);
    results.expectDenied('Resident B cannot upload media under Resident A draft path', await storageObject(runtime, auth.RESIDENT_B.token, bucket, objectPath, { method: 'POST', body: png, contentType: 'image/png' }));
    results.expectDenied('Resident A cannot upload media under nonexistent draft path', await storageObject(runtime, auth.RESIDENT_A.token, bucket, `${auth.RESIDENT_A.userId}/00000000-0000-0000-0000-000000000000/synthetic-storage-policy.png`, { method: 'POST', body: png, contentType: 'image/png' }));
    const uploaded = await storageObject(runtime, auth.RESIDENT_A.token, bucket, objectPath, { method: 'POST', body: png, contentType: 'image/png' });
    if (!results.expectOk('Resident A uploads bounded marker PNG to own Community draft path', uploaded)) return;
    markerUploaded = true;
    results.expectOk('Resident A reads own unregistered draft media for protected preview and cleanup', await storageObject(runtime, auth.RESIDENT_A.token, bucket, objectPath));
    results.expectDenied('Resident B cannot read Resident A unregistered draft media object', await storageObject(runtime, auth.RESIDENT_B.token, bucket, objectPath));
    const crossOwnerDelete = await storageDelete(runtime, auth.RESIDENT_B.token, bucket, [objectPath]);
    const markerAfterCrossOwnerDelete = await storageObjectInfo(runtime, auth.RESIDENT_A.token, bucket, objectPath);
    if (!crossOwnerDelete.ok) {
      results.pass('Resident B cannot delete Resident A draft media object', `denied at server boundary: HTTP ${crossOwnerDelete.status}`);
    } else if (markerAfterCrossOwnerDelete.ok) {
      results.pass('Resident B cannot delete Resident A draft media object', `HTTP ${crossOwnerDelete.status} acknowledged no mutation; marker metadata remained owner-visible`);
    } else {
      results.fail('Resident B cannot delete Resident A draft media object', `marker was unavailable to Resident A after HTTP ${crossOwnerDelete.status}`);
    }
    const deleted = await storageDelete(runtime, auth.RESIDENT_A.token, bucket, [objectPath]);
    if (results.expectOk('Resident A deletes marker media while own post remains draft', deleted)) {
      const markerAfterOwnerDelete = await storageObjectInfo(runtime, auth.RESIDENT_A.token, bucket, objectPath);
      if (!markerAfterOwnerDelete.ok) {
        markerUploaded = false;
        results.pass('Resident A cleanup confirmation finds marker unavailable after delete', `HTTP ${markerAfterOwnerDelete.status}`);
      } else {
        results.fail('Resident A cleanup confirmation finds marker unavailable after delete', `marker metadata remained available after HTTP ${deleted.status}`);
      }
    }
  } finally {
    if (markerUploaded) {
      const cleanup = await storageDelete(runtime, auth.RESIDENT_A.token, bucket, [objectPath]);
      const markerAfterCleanup = await storageObjectInfo(runtime, auth.RESIDENT_A.token, bucket, objectPath);
      if (cleanup.ok && !markerAfterCleanup.ok) {
        markerUploaded = false;
        results.pass('Resident A best-effort cleanup removes marker after interrupted assertion', `HTTP ${cleanup.status}`);
      } else {
        results.fail('Resident A best-effort cleanup removes marker after interrupted assertion', `expected marker removal, cleanup HTTP ${cleanup.status}, verification HTTP ${markerAfterCleanup.status}`);
      }
    }
  }
}

async function profileMediaStorage(runtime, auth, results) {
  const bucket = 'rtc-profile-media';
  const objectPath = `${auth.RESIDENT_A.userId}/synthetic-profile-storage-policy.png`;
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL9WAAAAABJRU5ErkJggg==', 'base64');
  let markerUploaded = false;
  try {
    // Constrain stale-run recovery to this exact non-production marker path.
    await storageDelete(runtime, auth.RESIDENT_A.token, bucket, [objectPath]);
    results.expectDenied('Resident B cannot upload profile media under Resident A path', await storageObject(runtime, auth.RESIDENT_B.token, bucket, objectPath, { method: 'POST', body: png, contentType: 'image/png' }));
    const uploaded = await storageObject(runtime, auth.RESIDENT_A.token, bucket, objectPath, { method: 'POST', body: png, contentType: 'image/png' });
    if (!results.expectOk('Resident A uploads bounded marker PNG to own profile-media path', uploaded)) return;
    markerUploaded = true;
    results.expectOk('Resident A reads own private profile-media marker', await storageObject(runtime, auth.RESIDENT_A.token, bucket, objectPath));
    results.expectDenied('Resident B cannot read Resident A private profile-media marker', await storageObject(runtime, auth.RESIDENT_B.token, bucket, objectPath));
    const crossOwnerDelete = await storageDelete(runtime, auth.RESIDENT_B.token, bucket, [objectPath]);
    const markerAfterCrossOwnerDelete = await storageObjectInfo(runtime, auth.RESIDENT_A.token, bucket, objectPath);
    if (!crossOwnerDelete.ok) {
      results.pass('Resident B cannot delete Resident A profile-media marker', `denied at server boundary: HTTP ${crossOwnerDelete.status}`);
    } else if (markerAfterCrossOwnerDelete.ok) {
      results.pass('Resident B cannot delete Resident A profile-media marker', `HTTP ${crossOwnerDelete.status} acknowledged no mutation; marker metadata remained owner-visible`);
    } else {
      results.fail('Resident B cannot delete Resident A profile-media marker', `marker metadata was unavailable to Resident A after HTTP ${crossOwnerDelete.status}`);
    }
    const deleted = await storageDelete(runtime, auth.RESIDENT_A.token, bucket, [objectPath]);
    if (results.expectOk('Resident A deletes own profile-media marker', deleted)) {
      const markerAfterOwnerDelete = await storageObjectInfo(runtime, auth.RESIDENT_A.token, bucket, objectPath);
      if (!markerAfterOwnerDelete.ok) {
        markerUploaded = false;
        results.pass('Resident A profile-media cleanup confirmation finds marker unavailable after delete', `HTTP ${markerAfterOwnerDelete.status}`);
      } else {
        results.fail('Resident A profile-media cleanup confirmation finds marker unavailable after delete', `marker metadata remained available after HTTP ${deleted.status}`);
      }
    }
  } finally {
    if (markerUploaded) {
      const cleanup = await storageDelete(runtime, auth.RESIDENT_A.token, bucket, [objectPath]);
      const markerAfterCleanup = await storageObjectInfo(runtime, auth.RESIDENT_A.token, bucket, objectPath);
      if (cleanup.ok && !markerAfterCleanup.ok) {
        markerUploaded = false;
        results.pass('Resident A best-effort profile-media cleanup removes marker after interrupted assertion', `HTTP ${cleanup.status}`);
      } else {
        results.fail('Resident A best-effort profile-media cleanup removes marker after interrupted assertion', `expected marker removal, cleanup HTTP ${cleanup.status}, verification HTTP ${markerAfterCleanup.status}`);
      }
    }
  }
}

async function communitySocialControls(runtime, auth, results) {
  const state = loadState();
  if (!state.communityPostId) fail('Community social-control test requires the marked post from the moderation slice.');
  const feedQuery = `select=id,author_id&id=eq.${encodeURIComponent(state.communityPostId)}`;
  const visibleBefore = await table(runtime, auth.RESIDENT_B.token, 'community_post_feed', feedQuery);
  if (results.expectOk('Resident B initially sees marked published post', visibleBefore) && Array.isArray(visibleBefore.data) && visibleBefore.data.some((row) => row.id === state.communityPostId)) {
    results.pass('marked post is initially visible to Resident B', 'published post is visible before social-control action');
  } else if (visibleBefore.ok) results.fail('marked post is initially visible to Resident B', 'expected marked post was absent before test');
  results.expectDenied('Resident B cannot block self', await rpc(runtime, auth.RESIDENT_B.token, 'set_community_block', { p_target_id: auth.RESIDENT_B.userId, p_blocked: true }));
  results.expectDenied('Resident B cannot directly insert Community block row', await request(runtime, '/rest/v1/community_blocks', { method: 'POST', token: auth.RESIDENT_B.token, body: { blocker_id: auth.RESIDENT_B.userId, blocked_id: auth.RESIDENT_A.userId }, headers: { Prefer: 'return=minimal' } }));
  results.expectOk('Resident B blocks Resident A through guarded RPC', await rpc(runtime, auth.RESIDENT_B.token, 'set_community_block', { p_target_id: auth.RESIDENT_A.userId, p_blocked: true }));
  const hiddenByBlock = await table(runtime, auth.RESIDENT_B.token, 'community_post_feed', feedQuery);
  if (hiddenByBlock.ok && Array.isArray(hiddenByBlock.data) && hiddenByBlock.data.length === 0) results.pass('Resident B block hides Resident A marked post', 'block visibility rule applied at server boundary');
  else results.fail('Resident B block hides Resident A marked post', `expected zero feed rows, got HTTP ${hiddenByBlock.status}`);
  results.expectOk('Resident B removes marked Community block', await rpc(runtime, auth.RESIDENT_B.token, 'set_community_block', { p_target_id: auth.RESIDENT_A.userId, p_blocked: false }));
  const restoredAfterBlock = await table(runtime, auth.RESIDENT_B.token, 'community_post_feed', feedQuery);
  if (restoredAfterBlock.ok && Array.isArray(restoredAfterBlock.data) && restoredAfterBlock.data.some((row) => row.id === state.communityPostId)) results.pass('Resident B unblocking restores marked post visibility', 'block removal restores server-side visibility');
  else results.fail('Resident B unblocking restores marked post visibility', `expected marked feed row, got HTTP ${restoredAfterBlock.status}`);
  results.expectDenied('Resident B cannot mute self', await rpc(runtime, auth.RESIDENT_B.token, 'set_community_mute', { p_target_id: auth.RESIDENT_B.userId, p_muted: true }));
  results.expectOk('Resident B mutes Resident A through guarded RPC', await rpc(runtime, auth.RESIDENT_B.token, 'set_community_mute', { p_target_id: auth.RESIDENT_A.userId, p_muted: true }));
  const hiddenByMute = await table(runtime, auth.RESIDENT_B.token, 'community_post_feed', feedQuery);
  if (hiddenByMute.ok && Array.isArray(hiddenByMute.data) && hiddenByMute.data.length === 0) results.pass('Resident B mute hides Resident A marked post', 'mute visibility rule applied at server boundary');
  else results.fail('Resident B mute hides Resident A marked post', `expected zero feed rows, got HTTP ${hiddenByMute.status}`);
  results.expectOk('Resident B removes marked Community mute', await rpc(runtime, auth.RESIDENT_B.token, 'set_community_mute', { p_target_id: auth.RESIDENT_A.userId, p_muted: false }));
  const restoredAfterMute = await table(runtime, auth.RESIDENT_B.token, 'community_post_feed', feedQuery);
  if (restoredAfterMute.ok && Array.isArray(restoredAfterMute.data) && restoredAfterMute.data.some((row) => row.id === state.communityPostId)) results.pass('Resident B unmuting restores marked post visibility', 'mute removal restores server-side visibility');
  else results.fail('Resident B unmuting restores marked post visibility', `expected marked feed row, got HTTP ${restoredAfterMute.status}`);
}

async function operationsReadModels(runtime, auth, results) {
  results.expectDenied('Resident A cannot view Operations system health', await rpc(runtime, auth.RESIDENT_A.token, 'ops_list_system_health'));
  results.expectDenied('Resident A cannot view administrative activity', await rpc(runtime, auth.RESIDENT_A.token, 'ops_list_administrative_activity', { p_limit: 20, p_offset: 0, p_category: null }));
  results.expectDenied('System Administrator aal1 cannot view Operations system health', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'ops_list_system_health'));
  results.expectNoDisclosure('System Administrator direct audit-events read', await table(runtime, auth.SYSTEM_ADMIN.token, 'audit_events', 'select=id&limit=1'));
  const admin = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  const health = await rpc(runtime, admin.token, 'ops_list_system_health');
  if (results.expectOk('System Administrator aal2 views Operations system health', health) && Array.isArray(health.data) && health.data.some((row) => row.service_key === 'SCHEDULED_JOBS' && row.status === 'AMBER') && health.data.some((row) => row.service_key === 'NOTIFICATION_DELIVERY' && row.status === 'AMBER')) {
    results.pass('Operations health reports unavailable scheduler and delivery as AMBER', 'no false healthy status for disabled infrastructure');
  } else if (health.ok) results.fail('Operations health reports unavailable scheduler and delivery as AMBER', 'required explicit AMBER unavailable entries were absent');
  results.expectDenied('System Administrator aal2 rejects invalid administrative-activity category', await rpc(runtime, admin.token, 'ops_list_administrative_activity', { p_limit: 20, p_offset: 0, p_category: 'SECRETS' }));
  const activity = await rpc(runtime, admin.token, 'ops_list_administrative_activity', { p_limit: 100, p_offset: 0, p_category: 'OPERATIONS' });
  if (results.expectOk('System Administrator aal2 views Operations activity', activity) && Array.isArray(activity.data) && activity.data.some((row) => row.event_type === 'OPERATIONS_SYSTEM_HEALTH_VIEWED')) {
    results.pass('Operations activity includes guarded health-view audit event', 'event is returned only through guarded read model');
  } else if (activity.ok) results.fail('Operations activity includes guarded health-view audit event', 'expected audit event was absent');
  results.expectDenied('unsafe community-alert retry contract remains unavailable', await rpc(runtime, admin.token, 'ops_retry_failed_community_alert', { p_alert_id: '00000000-0000-0000-0000-000000000000', p_reason: `${MARKER} retry must remain unavailable`, p_confirmation: 'CONFIRM', p_incident_id: null }));
}

async function operationsLifecycle(runtime, auth, results) {
  const state = loadState();
  const incidentTitle = `${MARKER} Operations lifecycle incident`;
  const incidentImpact = `${MARKER} controlled non-production incident lifecycle coverage.`;
  const closeSummary = `${MARKER} controlled incident closure after lifecycle verification.`;
  const controlReason = `${MARKER} temporary maintenance control verification.`;
  const controlMessage = `${MARKER} synthetic maintenance status only.`;
  const controlAuditNote = `${MARKER} explicit audit note for control lifecycle verification.`;

  results.expectNoDisclosure('Resident A direct operational work-item read', await table(runtime, auth.RESIDENT_A.token, 'operational_work_items', 'select=id&limit=1'));
  results.expectNoDisclosure('System Administrator direct operational incident read', await table(runtime, auth.SYSTEM_ADMIN.token, 'operational_incidents', 'select=id&limit=1'));
  results.expectNoDisclosure('System Administrator direct operational control read', await table(runtime, auth.SYSTEM_ADMIN.token, 'operational_controls', 'select=id&limit=1'));
  results.expectDenied('Resident A cannot list Operations work queue', await rpc(runtime, auth.RESIDENT_A.token, 'ops_list_work_queue'));
  results.expectDenied('Resident A cannot open Operations incident', await rpc(runtime, auth.RESIDENT_A.token, 'ops_create_incident', { p_title: incidentTitle, p_impact_summary: incidentImpact, p_severity: 'HIGH', p_owner_id: null }));
  results.expectDenied('System Administrator aal1 cannot open Operations incident', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'ops_create_incident', { p_title: incidentTitle, p_impact_summary: incidentImpact, p_severity: 'HIGH', p_owner_id: null }));
  results.expectDenied('System Administrator aal1 cannot activate operational control', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'ops_set_operational_control', { p_control_type: 'MAINTENANCE_BANNER', p_enabled: true, p_reason: controlReason, p_display_message: controlMessage, p_expires_at: new Date(Date.now() + 3_600_000).toISOString(), p_incident_id: null, p_confirmation: 'CONFIRM OPERATIONAL CONTROL', p_audit_note: controlAuditNote }));

  const admin = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  const approver = await stepUp(runtime, auth.SYSTEM_ADMIN_APPROVER, 'SYSTEM_ADMIN_APPROVER');
  if (!state.operationsIncidentId) {
    const created = await rpc(runtime, admin.token, 'ops_create_incident', {
      p_title: incidentTitle,
      p_impact_summary: incidentImpact,
      p_severity: 'HIGH',
      p_owner_id: null,
    });
    if (!results.expectOk('System Administrator aal2 opens marked Operations incident', created) || typeof created.data !== 'string') return;
    state.operationsIncidentId = created.data;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume marked Operations incident', 'using ignored local state; no duplicate incident created');

  const incidents = await rpc(runtime, admin.token, 'ops_list_incidents');
  if (results.expectOk('System Administrator aal2 lists marked Operations incident', incidents) && Array.isArray(incidents.data) && incidents.data.some((row) => row.id === state.operationsIncidentId)) {
    results.pass('guarded incident read model contains marked incident', 'incident ID returned through System Administrator RPC');
  } else if (incidents.ok) results.fail('guarded incident read model contains marked incident', 'expected marked incident was absent');

  const queue = await rpc(runtime, admin.token, 'ops_list_work_queue');
  if (results.expectOk('System Administrator aal2 lists Operations work queue', queue)) {
    const work = Array.isArray(queue.data) ? queue.data.find((row) => row.source_type === 'OPERATIONAL_INCIDENT' && row.source_id === state.operationsIncidentId) : null;
    if (work && typeof work.id === 'string') {
      state.operationsWorkItemId = work.id;
      writePrivateJson(STATE_PATH, state);
      results.pass('incident creation produces marked System Administrator work item', 'work item is returned through guarded queue RPC');
    } else results.fail('incident creation produces marked System Administrator work item', 'expected incident work item was absent');
  }
  if (!state.operationsWorkItemId) return;

  const caseStaffQueue = await rpc(runtime, auth.CASE_STAFF.token, 'ops_list_work_queue');
  if (results.expectOk('CASE_STAFF can list only role-scoped Operations queue', caseStaffQueue) && Array.isArray(caseStaffQueue.data) && !caseStaffQueue.data.some((row) => row.id === state.operationsWorkItemId)) {
    results.pass('CASE_STAFF does not receive System Administrator incident work item', 'role scope is enforced by guarded queue RPC');
  } else if (caseStaffQueue.ok) results.fail('CASE_STAFF does not receive System Administrator incident work item', 'restricted incident work item was disclosed');
  results.expectDenied('CASE_STAFF cannot claim System Administrator incident work item', await rpc(runtime, auth.CASE_STAFF.token, 'ops_claim_work_item', { p_work_item_id: state.operationsWorkItemId }));

  if (!state.operationsLifecycleComplete) {
    results.expectDenied('System Administrator aal1 cannot reassign Operations work item', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'ops_reassign_work_item', { p_work_item_id: state.operationsWorkItemId, p_new_owner: auth.SYSTEM_ADMIN_APPROVER.userId, p_reason: `${MARKER} denied without MFA` }));
    results.expectOk('System Administrator aal2 claims marked Operations work item', await rpc(runtime, admin.token, 'ops_claim_work_item', { p_work_item_id: state.operationsWorkItemId }));
    results.expectDenied('CASE_STAFF cannot release System Administrator-owned work item', await rpc(runtime, auth.CASE_STAFF.token, 'ops_release_work_item', { p_work_item_id: state.operationsWorkItemId, p_reason: `${MARKER} prohibited cross-owner release` }));
    results.expectOk('System Administrator aal2 marks claimed work ready for review', await rpc(runtime, admin.token, 'ops_mark_work_ready_for_review', { p_work_item_id: state.operationsWorkItemId, p_note: `${MARKER} controlled readiness transition` }));
    results.expectOk('System Administrator aal2 releases own ready work item', await rpc(runtime, admin.token, 'ops_release_work_item', { p_work_item_id: state.operationsWorkItemId, p_reason: `${MARKER} controlled release transition` }));
    results.expectOk('System Administrator aal2 reassigns work to independent administrator', await rpc(runtime, admin.token, 'ops_reassign_work_item', { p_work_item_id: state.operationsWorkItemId, p_new_owner: auth.SYSTEM_ADMIN_APPROVER.userId, p_reason: `${MARKER} controlled independent reassignment` }));
    results.expectDenied('former owner cannot release reassigned Operations work item', await rpc(runtime, admin.token, 'ops_release_work_item', { p_work_item_id: state.operationsWorkItemId, p_reason: `${MARKER} prohibited former-owner release` }));
    const approverQueue = await rpc(runtime, approver.token, 'ops_list_work_queue');
    if (results.expectOk('independent administrator lists reassigned work item', approverQueue) && Array.isArray(approverQueue.data) && approverQueue.data.some((row) => row.id === state.operationsWorkItemId && row.assigned_to_me === true)) {
      results.pass('reassigned work item is scoped to its new owner', 'guarded queue marks independent administrator ownership');
    } else if (approverQueue.ok) results.fail('reassigned work item is scoped to its new owner', 'expected assigned work item was absent or not marked owned');
    results.expectOk('independent administrator releases reassigned work item', await rpc(runtime, approver.token, 'ops_release_work_item', { p_work_item_id: state.operationsWorkItemId, p_reason: `${MARKER} controlled owner release` }));

    results.expectDenied('System Administrator aal2 rejects invalid control confirmation', await rpc(runtime, admin.token, 'ops_set_operational_control', { p_control_type: 'MAINTENANCE_BANNER', p_enabled: true, p_reason: controlReason, p_display_message: controlMessage, p_expires_at: new Date(Date.now() + 3_600_000).toISOString(), p_incident_id: state.operationsIncidentId, p_confirmation: 'CONFIRM', p_audit_note: controlAuditNote }));
    const activatedControl = await rpc(runtime, admin.token, 'ops_set_operational_control', { p_control_type: 'MAINTENANCE_BANNER', p_enabled: true, p_reason: controlReason, p_display_message: controlMessage, p_expires_at: new Date(Date.now() + 3_600_000).toISOString(), p_incident_id: state.operationsIncidentId, p_confirmation: 'CONFIRM OPERATIONAL CONTROL', p_audit_note: controlAuditNote });
    if (!results.expectOk('System Administrator aal2 activates marked maintenance control', activatedControl) || typeof activatedControl.data !== 'string') return;
    state.operationsControlId = activatedControl.data;
    writePrivateJson(STATE_PATH, state);
    const activeControl = await rpc(runtime, admin.token, 'ops_get_active_community_controls');
    if (results.expectOk('System Administrator aal2 reads active marked maintenance control', activeControl) && firstRow(activeControl.data)?.maintenance_message === controlMessage) {
      results.pass('guarded controls read model returns marked maintenance message', 'active control is visible through guarded RPC');
    } else if (activeControl.ok) results.fail('guarded controls read model returns marked maintenance message', 'expected active maintenance message was absent');
    results.expectOk('System Administrator aal2 ends marked maintenance control', await rpc(runtime, admin.token, 'ops_set_operational_control', { p_control_type: 'MAINTENANCE_BANNER', p_enabled: false, p_reason: `${MARKER} controlled end of test maintenance control`, p_display_message: '', p_expires_at: null, p_incident_id: null, p_confirmation: 'CONFIRM OPERATIONAL CONTROL', p_audit_note: `${MARKER} test control cleanup` }));
    const inactiveControl = await rpc(runtime, admin.token, 'ops_get_active_community_controls');
    if (results.expectOk('System Administrator aal2 reads ended maintenance control state', inactiveControl) && firstRow(inactiveControl.data)?.maintenance_message == null) {
      results.pass('ended maintenance control no longer appears active', 'guarded controls read model clears maintenance message');
    } else if (inactiveControl.ok) results.fail('ended maintenance control no longer appears active', 'maintenance message remained active after controlled end');

    results.expectDenied('System Administrator aal2 rejects resolved incident without closing summary', await rpc(runtime, admin.token, 'ops_update_incident', { p_incident_id: state.operationsIncidentId, p_state: 'RESOLVED', p_closing_summary: '' }));
    results.expectOk('System Administrator aal2 resolves marked Operations incident', await rpc(runtime, admin.token, 'ops_update_incident', { p_incident_id: state.operationsIncidentId, p_state: 'RESOLVED', p_closing_summary: closeSummary }));
    state.operationsLifecycleComplete = true;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume completed Operations lifecycle', 'guarded lifecycle state is retained in ignored local state');

  const resolvedIncidents = await rpc(runtime, admin.token, 'ops_list_incidents');
  if (results.expectOk('System Administrator aal2 verifies marked incident resolution', resolvedIncidents) && Array.isArray(resolvedIncidents.data) && resolvedIncidents.data.some((row) => row.id === state.operationsIncidentId && row.state === 'RESOLVED' && String(row.closing_summary || '').includes(MARKER))) {
    results.pass('marked incident is resolved with the required closing summary', 'guarded incident read model reflects controlled closure');
  } else if (resolvedIncidents.ok) results.fail('marked incident is resolved with the required closing summary', 'expected resolved incident evidence was absent');
  const activity = await rpc(runtime, admin.token, 'ops_list_administrative_activity', { p_limit: 200, p_offset: 0, p_category: 'OPERATIONS' });
  if (results.expectOk('System Administrator aal2 reads Operations lifecycle audit evidence', activity) && Array.isArray(activity.data) && activity.data.some((row) => String(row.details || '').includes(MARKER))) {
    results.pass('Operations activity contains marked lifecycle audit evidence', 'guarded administrative activity contains synthetic marker');
  } else if (activity.ok) results.fail('Operations activity contains marked lifecycle audit evidence', 'expected marked lifecycle audit evidence was absent');
}

async function alertLifecycle(runtime, auth, results) {
  const state = loadState();
  const normalTitle = `${MARKER} ordinary alert`;
  const normalSummary = `${MARKER} ordinary alert summary.`;
  const normalBody = `${MARKER} ordinary in-app alert detail.`;
  const safetyTitle = `${MARKER} safety alert`;
  const safetySummary = `${MARKER} safety alert summary.`;
  const safetyBody = `${MARKER} safety in-app alert detail.`;
  const safetyReason = `${MARKER} controlled safety test operational reason.`;

  results.expectNoDisclosure('anonymous Community alert inbox read', await table(runtime, null, 'community_alert_inbox', 'select=id&limit=1'));
  results.expectDenied('Resident A cannot create ordinary Community alert', await rpc(runtime, auth.RESIDENT_A.token, 'create_confirmed_community_alert', { p_confirmation: 'PUBLISH COMMUNITY ALERT', p_category: 'COMMUNITY_UPDATE', p_title: normalTitle, p_summary: normalSummary, p_body: normalBody }));
  results.expectDenied('Content Editor cannot create safety alert', await rpc(runtime, auth.CONTENT_EDITOR.token, 'create_confirmed_community_alert', { p_confirmation: 'SEND SAFETY ALERT', p_category: 'SAFETY_EMERGENCY', p_title: safetyTitle, p_summary: safetySummary, p_body: safetyBody, p_emergency_reason: safetyReason }));
  results.expectDenied('System Administrator aal1 cannot create safety alert', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'create_confirmed_community_alert', { p_confirmation: 'SEND SAFETY ALERT', p_category: 'SAFETY_EMERGENCY', p_title: safetyTitle, p_summary: safetySummary, p_body: safetyBody, p_emergency_reason: safetyReason }));

  if (!state.ordinaryAlertId) {
    const normal = await rpc(runtime, auth.CONTENT_EDITOR.token, 'create_confirmed_community_alert', {
      p_confirmation: 'PUBLISH COMMUNITY ALERT',
      p_category: 'COMMUNITY_UPDATE',
      p_title: normalTitle,
      p_summary: normalSummary,
      p_body: normalBody,
    });
    if (!results.expectOk('Content Editor publishes marked ordinary Community alert', normal) || typeof normal.data !== 'string') return;
    state.ordinaryAlertId = normal.data;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume marked ordinary Community alert', 'using ignored local state; no duplicate alert created');

  const editorDashboard = await rpc(runtime, auth.CONTENT_EDITOR.token, 'get_community_alert_dashboard');
  if (results.expectOk('Content Editor views own alert dashboard', editorDashboard) && Array.isArray(editorDashboard.data) && editorDashboard.data.some((row) => row.id === state.ordinaryAlertId && row.status === 'PUBLISHED' && row.dispatch_state === 'PENDING')) {
    results.pass('ordinary alert dashboard reports published PENDING dispatch state', 'no unsupported dispatcher success is claimed');
  } else if (editorDashboard.ok) results.fail('ordinary alert dashboard reports published PENDING dispatch state', 'expected marked ordinary alert with PENDING dispatch state was absent');

  const administrator = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  results.expectDenied('System Administrator aal2 rejects invalid safety confirmation', await rpc(runtime, administrator.token, 'create_confirmed_community_alert', { p_confirmation: 'PUBLISH COMMUNITY ALERT', p_category: 'SAFETY_EMERGENCY', p_title: safetyTitle, p_summary: safetySummary, p_body: safetyBody, p_emergency_reason: safetyReason }));
  if (!state.safetyAlertId) {
    const safety = await rpc(runtime, administrator.token, 'create_confirmed_community_alert', {
      p_confirmation: 'SEND SAFETY ALERT',
      p_category: 'SAFETY_EMERGENCY',
      p_title: safetyTitle,
      p_summary: safetySummary,
      p_body: safetyBody,
      p_emergency_reason: safetyReason,
    });
    if (!results.expectOk('System Administrator aal2 publishes marked safety alert', safety) || typeof safety.data !== 'string') return;
    state.safetyAlertId = safety.data;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume marked safety Community alert', 'using ignored local state; no duplicate alert created');

  const residentInbox = await table(runtime, auth.RESIDENT_B.token, 'community_alert_inbox', 'select=notification_id,id,category,read_at&order=inbox_created_at.desc&limit=100');
  let ordinaryNotificationId = null;
  if (results.expectOk('Resident B views own Community alert inbox', residentInbox) && Array.isArray(residentInbox.data)) {
    const ordinary = residentInbox.data.find((row) => row.id === state.ordinaryAlertId);
    const safety = residentInbox.data.find((row) => row.id === state.safetyAlertId);
    if (ordinary && safety && typeof ordinary.notification_id === 'string') {
      ordinaryNotificationId = ordinary.notification_id;
      state.residentBOrdinaryAlertNotificationId = ordinaryNotificationId;
      writePrivateJson(STATE_PATH, state);
      results.pass('Resident B inbox contains marked ordinary and safety alerts', 'recipient-scoped inbox exposes canonical alert records');
    } else results.fail('Resident B inbox contains marked ordinary and safety alerts', 'expected marked alerts were absent from recipient inbox');
  }
  if (!ordinaryNotificationId) ordinaryNotificationId = state.residentBOrdinaryAlertNotificationId;
  if (!ordinaryNotificationId) return;

  const prohibitedReadUpdate = await request(runtime, `/rest/v1/notification_events?id=eq.${encodeURIComponent(ordinaryNotificationId)}`, {
    method: 'PATCH',
    token: auth.RESIDENT_A.token,
    body: { read_at: new Date().toISOString() },
    headers: { Prefer: 'return=representation' },
  });
  results.expectNoMutation('Resident A cannot mark Resident B alert notification read', prohibitedReadUpdate);
  const ownReadUpdate = await request(runtime, `/rest/v1/notification_events?id=eq.${encodeURIComponent(ordinaryNotificationId)}`, {
    method: 'PATCH',
    token: auth.RESIDENT_B.token,
    body: { read_at: new Date().toISOString() },
    headers: { Prefer: 'return=representation' },
  });
  results.expectOk('Resident B marks own Community alert notification read', ownReadUpdate);
  const refreshedInbox = await table(runtime, auth.RESIDENT_B.token, 'community_alert_inbox', `select=notification_id,id,read_at&notification_id=eq.${encodeURIComponent(ordinaryNotificationId)}&limit=1`);
  if (results.expectOk('Resident B refreshes own read state', refreshedInbox) && firstRow(refreshedInbox.data)?.read_at) {
    results.pass('resident-owned read state is reflected through the inbox contract', 'own notification read timestamp is present');
  } else if (refreshedInbox.ok) results.fail('resident-owned read state is reflected through the inbox contract', 'expected read timestamp was absent');

  results.expectNoDisclosure('Resident A cannot read Resident B support-notification preference', await table(runtime, auth.RESIDENT_A.token, 'account_preferences', `select=user_id,support_notifications&user_id=eq.${auth.RESIDENT_B.userId}&limit=1`));
  const residentBPreferences = await table(runtime, auth.RESIDENT_B.token, 'account_preferences', `select=user_id,support_notifications&user_id=eq.${auth.RESIDENT_B.userId}&limit=1`);
  const originalSupportPreference = firstRow(residentBPreferences.data)?.support_notifications ?? true;
  if (results.expectOk('Resident B reads own support-notification preference', residentBPreferences)) {
    const prohibitedPreferenceUpdate = await request(runtime, `/rest/v1/account_preferences?user_id=eq.${encodeURIComponent(auth.RESIDENT_B.userId)}`, {
      method: 'PATCH',
      token: auth.RESIDENT_A.token,
      body: { support_notifications: !originalSupportPreference },
      headers: { Prefer: 'return=representation' },
    });
    results.expectNoMutation('Resident A cannot change Resident B support-notification preference', prohibitedPreferenceUpdate);
    const changedSupportPreference = !originalSupportPreference;
    const ownPreferenceUpdate = await request(runtime, '/rest/v1/account_preferences', {
      method: 'POST',
      token: auth.RESIDENT_B.token,
      body: { user_id: auth.RESIDENT_B.userId, support_notifications: changedSupportPreference },
      headers: { Prefer: 'resolution=merge-duplicates,return=representation' },
    });
    if (results.expectOk('Resident B persists own support-notification preference', ownPreferenceUpdate)) {
      const confirmedPreference = await table(runtime, auth.RESIDENT_B.token, 'account_preferences', `select=user_id,support_notifications&user_id=eq.${auth.RESIDENT_B.userId}&limit=1`);
      if (results.expectOk('Resident B re-reads own persisted support preference', confirmedPreference) && firstRow(confirmedPreference.data)?.support_notifications === changedSupportPreference) {
        results.pass('support-notification preference is confirmed by server readback', 'owner readback matched the persisted value');
      } else if (confirmedPreference.ok) results.fail('support-notification preference is confirmed by server readback', 'owner readback did not match the persisted value');
      const restoredPreference = await request(runtime, '/rest/v1/account_preferences', {
        method: 'POST',
        token: auth.RESIDENT_B.token,
        body: { user_id: auth.RESIDENT_B.userId, support_notifications: originalSupportPreference },
        headers: { Prefer: 'resolution=merge-duplicates,return=representation' },
      });
      results.expectOk('Resident B restores support-notification preference baseline', restoredPreference);
    }
  }

  const residentBExperience = await table(runtime, auth.RESIDENT_B.token, 'account_preferences', `select=user_id,reading_mode,theme_preference&user_id=eq.${auth.RESIDENT_B.userId}&limit=1`);
  const originalReadingMode = firstRow(residentBExperience.data)?.reading_mode ?? false;
  const originalThemePreference = String(firstRow(residentBExperience.data)?.theme_preference ?? 'system').toLowerCase();
  if (results.expectOk('Resident B reads own experience preferences', residentBExperience)) {
    const changedReadingMode = !originalReadingMode;
    const changedThemePreference = originalThemePreference === 'dark' ? 'light' : 'dark';
    const ownExperienceUpdate = await request(runtime, `/rest/v1/account_preferences?user_id=eq.${encodeURIComponent(auth.RESIDENT_B.userId)}`, {
      method: 'PATCH',
      token: auth.RESIDENT_B.token,
      body: { reading_mode: changedReadingMode, theme_preference: changedThemePreference },
      headers: { Prefer: 'return=representation' },
    });
    if (results.expectOk('Resident B persists own reading-mode and theme preferences', ownExperienceUpdate)) {
      const confirmedExperience = await table(runtime, auth.RESIDENT_B.token, 'account_preferences', `select=user_id,reading_mode,theme_preference&user_id=eq.${auth.RESIDENT_B.userId}&limit=1`);
      if (results.expectOk('Resident B re-reads own persisted experience preferences', confirmedExperience) && firstRow(confirmedExperience.data)?.reading_mode === changedReadingMode && String(firstRow(confirmedExperience.data)?.theme_preference).toLowerCase() === changedThemePreference) {
        results.pass('reading-mode and theme preferences are confirmed by server readback', 'owner readback matched both persisted values');
      } else if (confirmedExperience.ok) results.fail('reading-mode and theme preferences are confirmed by server readback', 'owner readback did not match both persisted values');
      const restoredExperience = await request(runtime, `/rest/v1/account_preferences?user_id=eq.${encodeURIComponent(auth.RESIDENT_B.userId)}`, {
        method: 'PATCH',
        token: auth.RESIDENT_B.token,
        body: { reading_mode: originalReadingMode, theme_preference: originalThemePreference },
        headers: { Prefer: 'return=representation' },
      });
      results.expectOk('Resident B restores experience-preference baseline', restoredExperience);
    }
  }

  results.expectNoDisclosure('Resident A cannot read Resident B profile display name', await table(runtime, auth.RESIDENT_A.token, 'profiles', `select=id,display_name&id=eq.${auth.RESIDENT_B.userId}&limit=1`));
  const residentBProfile = await table(runtime, auth.RESIDENT_B.token, 'profiles', `select=id,display_name&id=eq.${auth.RESIDENT_B.userId}&limit=1`);
  const originalDisplayName = firstRow(residentBProfile.data)?.display_name;
  if (results.expectOk('Resident B reads own profile display name', residentBProfile) && typeof originalDisplayName === 'string') {
    const prohibitedProfileUpdate = await request(runtime, `/rest/v1/profiles?id=eq.${encodeURIComponent(auth.RESIDENT_B.userId)}`, {
      method: 'PATCH',
      token: auth.RESIDENT_A.token,
      body: { display_name: `${MARKER} prohibited profile mutation` },
      headers: { Prefer: 'return=representation' },
    });
    results.expectNoMutation('Resident A cannot change Resident B profile display name', prohibitedProfileUpdate);
    const changedDisplayName = `${MARKER} Resident B profile`;
    const ownProfileUpdate = await request(runtime, `/rest/v1/profiles?id=eq.${encodeURIComponent(auth.RESIDENT_B.userId)}`, {
      method: 'PATCH',
      token: auth.RESIDENT_B.token,
      body: { display_name: changedDisplayName },
      headers: { Prefer: 'return=representation' },
    });
    if (results.expectOk('Resident B persists own profile display name', ownProfileUpdate)) {
      const confirmedProfile = await table(runtime, auth.RESIDENT_B.token, 'profiles', `select=id,display_name&id=eq.${auth.RESIDENT_B.userId}&limit=1`);
      if (results.expectOk('Resident B re-reads own persisted profile display name', confirmedProfile) && firstRow(confirmedProfile.data)?.display_name === changedDisplayName) {
        results.pass('profile display name is confirmed by server readback', 'owner readback matched the persisted value');
      } else if (confirmedProfile.ok) results.fail('profile display name is confirmed by server readback', 'owner readback did not match the persisted value');
      const restoredProfile = await request(runtime, `/rest/v1/profiles?id=eq.${encodeURIComponent(auth.RESIDENT_B.userId)}`, {
        method: 'PATCH',
        token: auth.RESIDENT_B.token,
        body: { display_name: originalDisplayName },
        headers: { Prefer: 'return=representation' },
      });
      results.expectOk('Resident B restores profile display-name baseline', restoredProfile);
    }
  }

  const adminDashboard = await rpc(runtime, administrator.token, 'get_community_alert_dashboard');
  if (results.expectOk('System Administrator aal2 views alert dashboard', adminDashboard) && Array.isArray(adminDashboard.data) && adminDashboard.data.some((row) => row.id === state.ordinaryAlertId && Number(row.read_count) >= 1) && adminDashboard.data.some((row) => row.id === state.safetyAlertId && row.category === 'SAFETY_EMERGENCY' && row.dispatch_state === 'PENDING')) {
    results.pass('administrator dashboard aggregates marked ordinary-alert read evidence and retains safety PENDING state', 'aggregate dashboard returns no named recipient data or unsupported delivery success');
  } else if (adminDashboard.ok) results.fail('administrator dashboard aggregates marked ordinary-alert read evidence and retains safety PENDING state', 'expected ordinary read aggregate or safety PENDING state was absent');
}

async function privacyAnalytics(runtime, auth, results) {
  results.expectOk('Resident A records permitted APP_ACTIVE signal', await rpc(runtime, auth.RESIDENT_A.token, 'record_privacy_analytics_activity', { p_event_type: 'APP_ACTIVE' }));
  results.expectOk('Resident A records permitted DIRECTORY_SEARCH signal', await rpc(runtime, auth.RESIDENT_A.token, 'record_privacy_analytics_activity', { p_event_type: 'DIRECTORY_SEARCH' }));
  results.expectDenied('Resident A cannot record unsupported privacy signal', await rpc(runtime, auth.RESIDENT_A.token, 'record_privacy_analytics_activity', { p_event_type: 'ROLE_CHANGE' }));
  results.expectNoDisclosure('Resident A direct privacy activity read', await table(runtime, auth.RESIDENT_A.token, 'admin_privacy_analytics_activity_events', 'select=id&limit=1'));
  results.expectNoDisclosure('System Administrator direct privacy total read', await table(runtime, auth.SYSTEM_ADMIN.token, 'admin_privacy_analytics_daily_totals', 'select=metric&limit=1'));
  results.expectDenied('Resident A cannot open privacy dashboard', await rpc(runtime, auth.RESIDENT_A.token, 'admin_privacy_analytics_dashboard', { p_period: 'TODAY' }));
  results.expectDenied('System Administrator aal1 cannot open privacy dashboard', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'admin_privacy_analytics_dashboard', { p_period: 'TODAY' }));
  const admin = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  const dashboard = await rpc(runtime, admin.token, 'admin_privacy_analytics_dashboard', { p_period: 'TODAY' });
  if (results.expectOk('System Administrator aal2 opens privacy dashboard', dashboard) && Array.isArray(dashboard.data) && dashboard.data.some((row) => row.metric === 'DIRECTORY_SEARCHES' && Number(row.metric_value) >= 1)) {
    results.pass('privacy dashboard aggregates permitted DIRECTORY_SEARCH signal', 'aggregate metric is present');
  } else if (dashboard.ok) results.fail('privacy dashboard aggregates permitted DIRECTORY_SEARCH signal', 'expected directory-search aggregate was absent');
  const locations = await rpc(runtime, admin.token, 'admin_privacy_analytics_location_summary', { p_period: 'TODAY' });
  if (results.expectOk('System Administrator aal2 opens suppressed locality summary', locations) && Array.isArray(locations.data) && locations.data.every((row) => row.locality_label !== 'Synthetic Locality')) {
    results.pass('privacy locality group below five is suppressed', 'raw single-case locality is not disclosed');
  } else if (locations.ok) results.fail('privacy locality group below five is suppressed', 'raw locality was disclosed or suppression result was incomplete');
  results.expectDenied('System Administrator aal2 rejects invalid privacy lookup purpose', await rpc(runtime, admin.token, 'admin_privacy_exact_account_lookup', { p_email: 'resident-a@rtc-nonprod.test', p_purpose: 'BROWSING', p_explanation: `${MARKER} invalid purpose` }));
  const lookup = await rpc(runtime, admin.token, 'admin_privacy_exact_account_lookup', { p_email: 'resident-a@rtc-nonprod.test', p_purpose: 'ACCOUNT_SUPPORT', p_explanation: `${MARKER} controlled synthetic support lookup` });
  if (results.expectOk('System Administrator aal2 performs purpose-bound exact lookup', lookup) && firstRow(lookup.data)?.email === 'resident-a@rtc-nonprod.test') {
    results.pass('purpose-bound exact lookup returns requested verified synthetic account', 'target email matches guarded request');
  } else if (lookup.ok) results.fail('purpose-bound exact lookup returns requested verified synthetic account', 'expected synthetic account was absent');
  const audit = await rpc(runtime, admin.token, 'admin_privacy_analytics_audit_events', { maximum_rows: 100 });
  if (results.expectOk('System Administrator aal2 reads privacy audit', audit) && Array.isArray(audit.data) && audit.data.some((row) => String(row.details || '').includes(MARKER))) {
    results.pass('privacy audit records purpose-bound synthetic lookup', 'guarded audit contains marker');
  } else if (audit.ok) results.fail('privacy audit records purpose-bound synthetic lookup', 'expected marker was absent');
}

async function editorialWorkflow(runtime, auth, results) {
  const state = loadState();
  results.expectDenied('Content Editor aal1 cannot create safety-sensitive notice', await rpc(runtime, auth.CONTENT_EDITOR.token, 'editorial_create_notice_draft', { p_title: `${MARKER} restricted safety notice`, p_body: `${MARKER} restricted safety body`, p_category: 'Safety', p_safety_sensitive: true, p_corrects_notice_id: null }));
  if (!state.editorialNoticeId) {
    const draft = await rpc(runtime, auth.CONTENT_EDITOR.token, 'editorial_create_notice_draft', { p_title: `${MARKER} editorial notice`, p_body: `${MARKER} independently reviewed editorial notice.`, p_category: 'Synthetic Test', p_safety_sensitive: false, p_corrects_notice_id: null });
    if (!results.expectOk('Content Editor creates marked normal notice draft', draft) || typeof draft.data !== 'string') return;
    state.editorialNoticeId = draft.data;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume marked editorial notice', 'using ignored local state; no duplicate notice created');
  if (!state.editorialSubmitted) {
    const submitted = await rpc(runtime, auth.CONTENT_EDITOR.token, 'editorial_submit_notice', { p_notice_id: state.editorialNoticeId, p_note: `${MARKER} submit for independent review` });
    if (results.expectOk('Content Editor submits marked notice', submitted)) {
      state.editorialSubmitted = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume marked editorial submission', 'submission is recorded in ignored local state');
  results.expectDenied('Content Editor cannot review own notice', await rpc(runtime, auth.CONTENT_EDITOR.token, 'editorial_review_notice', { p_notice_id: state.editorialNoticeId, p_outcome: 'APPROVE', p_note: `${MARKER} prohibited author review`, p_publish_mode: 'PUBLISH', p_scheduled_at: null }));
  results.expectDenied('Evidence Reviewer lacks editorial review authority', await rpc(runtime, auth.EVIDENCE_REVIEWER.token, 'editorial_review_notice', { p_notice_id: state.editorialNoticeId, p_outcome: 'APPROVE', p_note: `${MARKER} prohibited evidence-reviewer editorial approval`, p_publish_mode: 'PUBLISH', p_scheduled_at: null }));
  const administratorReviewer = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  results.expectDenied('independent System Administrator aal2 cannot schedule isolated notice', await rpc(runtime, administratorReviewer.token, 'editorial_review_notice', { p_notice_id: state.editorialNoticeId, p_outcome: 'APPROVE', p_note: `${MARKER} schedule is intentionally unavailable`, p_publish_mode: 'SCHEDULE', p_scheduled_at: new Date(Date.now() + 3600_000).toISOString() }));
  if (!state.editorialPublished) {
    const reviewed = await rpc(runtime, administratorReviewer.token, 'editorial_review_notice', { p_notice_id: state.editorialNoticeId, p_outcome: 'APPROVE', p_note: `${MARKER} independent administrator approval and immediate publication`, p_publish_mode: 'PUBLISH', p_scheduled_at: null });
    if (results.expectOk('independent System Administrator aal2 publishes marked notice', reviewed)) {
      state.editorialPublished = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume marked editorial publication', 'publication is recorded in ignored local state');
  results.expectNoDisclosure('Content Editor direct lifecycle-event read', await table(runtime, auth.CONTENT_EDITOR.token, 'official_notice_lifecycle_events', 'select=id&limit=1'));

  results.expectDenied('System Administrator aal1 cannot create safety-sensitive notice', await rpc(runtime, auth.SYSTEM_ADMIN.token, 'editorial_create_notice_draft', { p_title: `${MARKER} admin safety notice`, p_body: `${MARKER} safety-sensitive MFA test`, p_category: 'Safety', p_safety_sensitive: true, p_corrects_notice_id: null }));
  const safetyAuthor = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  if (!state.safetyNoticeId) {
    const draft = await rpc(runtime, safetyAuthor.token, 'editorial_create_notice_draft', { p_title: `${MARKER} admin safety notice`, p_body: `${MARKER} safety-sensitive MFA test`, p_category: 'Safety', p_safety_sensitive: true, p_corrects_notice_id: null });
    if (!results.expectOk('System Administrator aal2 creates marked safety-sensitive notice', draft) || typeof draft.data !== 'string') return;
    state.safetyNoticeId = draft.data;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume marked safety-sensitive notice', 'using ignored local state; no duplicate safety notice created');
  if (!state.safetyNoticeSubmitted) {
    const submitted = await rpc(runtime, safetyAuthor.token, 'editorial_submit_notice', { p_notice_id: state.safetyNoticeId, p_note: `${MARKER} safety-sensitive submission with MFA` });
    if (results.expectOk('System Administrator aal2 submits marked safety-sensitive notice', submitted)) {
      state.safetyNoticeSubmitted = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume marked safety-sensitive submission', 'submission is recorded in ignored local state');
  const safetyReviewer = await stepUp(runtime, auth.SYSTEM_ADMIN_APPROVER, 'SYSTEM_ADMIN_APPROVER');
  if (!state.safetyNoticePublished) {
    const reviewed = await rpc(runtime, safetyReviewer.token, 'editorial_review_notice', { p_notice_id: state.safetyNoticeId, p_outcome: 'APPROVE', p_note: `${MARKER} independent safety-sensitive approval`, p_publish_mode: 'PUBLISH', p_scheduled_at: null });
    if (results.expectOk('independent Administrator aal2 publishes marked safety-sensitive notice', reviewed)) {
      state.safetyNoticePublished = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume marked safety-sensitive publication', 'publication is recorded in ignored local state');
}

async function accessManagement(runtime, auth, results) {
  const state = loadState();
  const admin = await stepUp(runtime, auth.SYSTEM_ADMIN, 'SYSTEM_ADMIN');
  const approver = await stepUp(runtime, auth.SYSTEM_ADMIN_APPROVER, 'SYSTEM_ADMIN_APPROVER');
  const lookup = await rpc(runtime, admin.token, 'access_search_verified_account', { email_query: 'evidence-reviewer@rtc-nonprod.test' });
  if (results.expectOk('System Administrator aal2 searches verified synthetic account', lookup) && firstRow(lookup.data)?.effective_role === 'EVIDENCE_REVIEWER') {
    results.pass('verified account search reports baseline reviewer role', 'guarded search returned expected role');
  } else if (lookup.ok) results.fail('verified account search reports baseline reviewer role', 'expected EVIDENCE_REVIEWER role before promotion');
  results.expectDenied('System Administrator cannot change own role', await rpc(runtime, admin.token, 'access_save_role_assignment', { target_user: auth.SYSTEM_ADMIN.userId, requested_role: 'RESIDENT', change_reason: `${MARKER} prohibited self-role test` }));

  if (!state.accessPromotionRequestId) {
    const requested = await rpc(runtime, admin.token, 'access_save_role_assignment', { target_user: auth.EVIDENCE_REVIEWER.userId, requested_role: 'SYSTEM_ADMIN', change_reason: `${MARKER} independent approval promotion test` });
    const requestRow = firstRow(requested.data);
    if (!results.expectOk('System Administrator requests reviewer promotion', requested) || requestRow?.result !== 'PENDING_APPROVAL' || typeof requestRow.request_id !== 'string') return;
    state.accessPromotionRequestId = requestRow.request_id;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume reviewer promotion request', 'using ignored local state; no duplicate request created');

  results.expectDenied('requesting administrator cannot decide own promotion request', await rpc(runtime, admin.token, 'access_decide_role_change_request', { request: state.accessPromotionRequestId, approve: true, decision_reason: `${MARKER} prohibited same-admin decision` }));
  results.expectDenied('approver administrator aal1 cannot decide promotion request', await rpc(runtime, auth.SYSTEM_ADMIN_APPROVER.token, 'access_decide_role_change_request', { request: state.accessPromotionRequestId, approve: true, decision_reason: `${MARKER} denied without MFA` }));
  if (!state.accessPromotionApproved) {
    const approved = await rpc(runtime, approver.token, 'access_decide_role_change_request', { request: state.accessPromotionRequestId, approve: true, decision_reason: `${MARKER} independent approval` });
    if (results.expectOk('independent administrator aal2 approves promotion', approved)) {
      state.accessPromotionApproved = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume reviewer promotion approval', 'promotion completion is recorded in ignored local state');

  const promoted = await rpc(runtime, admin.token, 'access_search_verified_account', { email_query: 'evidence-reviewer@rtc-nonprod.test' });
  if (results.expectOk('administrator verifies promoted reviewer role', promoted) && firstRow(promoted.data)?.effective_role === 'SYSTEM_ADMIN') {
    results.pass('reviewer promotion took effect only after independent approval', 'guarded search reports SYSTEM_ADMIN');
  } else if (promoted.ok) results.fail('reviewer promotion took effect only after independent approval', 'expected SYSTEM_ADMIN role after approval');

  if (!state.accessDemotionRequestId) {
    const requested = await rpc(runtime, admin.token, 'access_save_role_assignment', { target_user: auth.EVIDENCE_REVIEWER.userId, requested_role: 'EVIDENCE_REVIEWER', change_reason: `${MARKER} restore test-role baseline` });
    const requestRow = firstRow(requested.data);
    if (!results.expectOk('System Administrator requests reviewer baseline restoration', requested) || requestRow?.result !== 'PENDING_APPROVAL' || typeof requestRow.request_id !== 'string') return;
    state.accessDemotionRequestId = requestRow.request_id;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume reviewer baseline-restoration request', 'using ignored local state; no duplicate request created');
  if (!state.accessDemotionApproved) {
    const approved = await rpc(runtime, approver.token, 'access_decide_role_change_request', { request: state.accessDemotionRequestId, approve: true, decision_reason: `${MARKER} restore reviewer test role` });
    if (results.expectOk('independent administrator restores reviewer baseline role', approved)) {
      state.accessDemotionApproved = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume reviewer baseline restoration', 'baseline restoration is recorded in ignored local state');

  if (!state.accessFloorRequestId) {
    const requested = await rpc(runtime, admin.token, 'access_save_role_assignment', { target_user: auth.SYSTEM_ADMIN_APPROVER.userId, requested_role: 'RESIDENT', change_reason: `${MARKER} two-administrator-floor test` });
    const requestRow = firstRow(requested.data);
    if (!results.expectOk('System Administrator requests controlled administrator demotion', requested) || requestRow?.result !== 'PENDING_APPROVAL' || typeof requestRow.request_id !== 'string') return;
    state.accessFloorRequestId = requestRow.request_id;
    writePrivateJson(STATE_PATH, state);
  } else results.pass('resume two-administrator-floor request', 'using ignored local state; no duplicate request created');
  results.expectDenied('two-administrator floor blocks approval of administrator demotion', await rpc(runtime, approver.token, 'access_decide_role_change_request', { request: state.accessFloorRequestId, approve: true, decision_reason: `${MARKER} prohibited below-floor approval` }));
  if (!state.accessFloorRequestRejected) {
    const rejected = await rpc(runtime, approver.token, 'access_decide_role_change_request', { request: state.accessFloorRequestId, approve: false, decision_reason: `${MARKER} close expected floor-protection request` });
    if (results.expectOk('independent administrator rejects retained floor-protection request', rejected)) {
      state.accessFloorRequestRejected = true;
      writePrivateJson(STATE_PATH, state);
    }
  } else results.pass('resume floor-protection request cleanup', 'rejection is recorded in ignored local state');
  const audit = await rpc(runtime, admin.token, 'access_list_role_audit_events', { maximum_rows: 100 });
  if (results.expectOk('System Administrator aal2 reads role-management audit', audit) && Array.isArray(audit.data) && audit.data.some((row) => String(row.reason || '').includes(MARKER))) {
    results.pass('role-management audit contains marked synthetic decisions', 'guarded audit contains marker');
  } else if (audit.ok) results.fail('role-management audit contains marked synthetic decisions', 'expected marker was absent');
}

async function main() {
  const command = process.argv[2];
  if (!['smoke', 'support-cases', 'moderation', 'public-visibility', 'community-controls', 'community-media-storage', 'profile-media-storage', 'operations', 'operations-lifecycle', 'alerts', 'privacy', 'editorial', 'access-management'].includes(command)) fail('Usage: node tools/nonprod_synthetic_backend_tests.js <smoke|support-cases|moderation|public-visibility|community-controls|community-media-storage|profile-media-storage|operations|operations-lifecycle|alerts|privacy|editorial|access-management>');
  const runtime = loadProperties();
  const credentials = loadCredentials();
  const auth = await sessions(runtime, credentials);
  const results = new Results(command);
  await smoke(runtime, auth, results);
  if (command === 'support-cases') await supportCases(runtime, auth, results);
  if (command === 'moderation') await communityModeration(runtime, auth, results);
  if (command === 'public-visibility') await publicCommunityVisibility(runtime, auth, results);
  if (command === 'community-controls') await communitySocialControls(runtime, auth, results);
  if (command === 'community-media-storage') await communityMediaStorage(runtime, auth, results);
  if (command === 'profile-media-storage') await profileMediaStorage(runtime, auth, results);
  if (command === 'operations') await operationsReadModels(runtime, auth, results);
  if (command === 'operations-lifecycle') await operationsLifecycle(runtime, auth, results);
  if (command === 'alerts') await alertLifecycle(runtime, auth, results);
  if (command === 'privacy') await privacyAnalytics(runtime, auth, results);
  if (command === 'editorial') await editorialWorkflow(runtime, auth, results);
  if (command === 'access-management') await accessManagement(runtime, auth, results);
  const summary = results.summary();
  writePrivateJson(RESULT_PATH, { projectRef: PROJECT_REF, marker: MARKER, command, ranAt: new Date().toISOString(), summary, items: results.items });
  for (const item of results.items) console.log(`[${item.outcome}] ${item.name} — ${item.detail}`);
  console.log(`[SUMMARY] passed=${summary.passed} failed=${summary.failed} local_results=${path.basename(RESULT_PATH)}`);
  if (summary.failed > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(`[FAIL] ${error instanceof Error ? error.message : 'Unexpected isolated backend test failure.'}`);
  process.exitCode = 1;
});
