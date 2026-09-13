import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, rm, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { generateKeyPair, exportJWK, createLocalJWKSet, SignJWT } from 'jose';
import { PGlite } from '@electric-sql/pglite';
import sharp from 'sharp';
import { createApp } from '../src/app.js';
import { createDatabase } from '../src/database.js';
import { createTomTom } from '../src/tomtom.js';
import { readConfig } from '../src/config.js';

let db, engine, server, base, mediaDir, privateKey, token, userBToken, config, keyResolver;
const issuer = 'https://identity.rtc.example/auth/v1';
const audience = 'authenticated';
const user = randomUUID();
const userB = randomUUID();
const validReport = { body: 'Water supply has stopped on our street.', category: 'INFRASTRUCTURE', priority: 'HIGH', latitude: -28.333, longitude: 23.066 };
async function signed(subject = user, options = {}) {
  return new SignJWT({}).setProtectedHeader({ alg: 'ES256', kid: 'test-key' }).setSubject(subject)
    .setIssuer(options.issuer ?? issuer).setAudience(options.audience ?? audience)
    .setIssuedAt().setExpirationTime(options.expiration ?? '1h').sign(privateKey);
}
async function request(path, { method = 'GET', body, key, bearer = token, headers = {} } = {}) {
  const response = await fetch(`${base}${path}`, {
    method, headers: { ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}),
      ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
  });
  const data = response.headers.get('content-type')?.includes('application/json') ? await response.json() : Buffer.from(await response.arrayBuffer());
  return { status: response.status, headers: response.headers, data };
}
function postForm(text, bytes) {
  const form = new FormData();
  form.set('body', text);
  if (bytes) form.set('image', new Blob([bytes], { type: 'image/png' }), 'photo.png');
  return form;
}
async function createProvider() {
  const id = randomUUID();
  await db.query(`INSERT INTO rtc_api.providers(id,name,rate_cents,latitude,longitude) VALUES ($1,'RTC Plumber',35000,-28.33,23.06)`, [id]);
  return id;
}

before(async () => {
  mediaDir = await mkdtemp(join(tmpdir(), 'rtc-server-test-'));
  if (process.env.TEST_DATABASE_URL) {
    const url = new URL(process.env.TEST_DATABASE_URL);
    if (!url.pathname.endsWith('_test')) throw new Error('TEST_DATABASE_URL database name must end in _test; schema is deleted.');
    db = createDatabase(process.env.TEST_DATABASE_URL);
    await db.query('DROP SCHEMA IF EXISTS rtc_api CASCADE');
    await db.query(await readFile(new URL('../migrations/001_initial.sql', import.meta.url), 'utf8'));
  } else {
    engine = new PGlite();
    await engine.exec(await readFile(new URL('../migrations/001_initial.sql', import.meta.url), 'utf8'));
    // PGlite is single-connection PostgreSQL WASM. Its transaction API holds the
    // connection across the whole callback; HTTP tests execute actual SQL, not stubs.
    db = { query: (sql, args) => engine.query(sql, args), transaction: (work) => engine.transaction(work), close: () => engine.close() };
  }
  const keys = await generateKeyPair('ES256');
  privateKey = keys.privateKey;
  const jwk = await exportJWK(keys.publicKey);
  keyResolver = createLocalJWKSet({ keys: [{ ...jwk, kid: 'test-key', alg: 'ES256', use: 'sig' }] });
  token = await signed(); userBToken = await signed(userB);
  config = { issuer, audience, jwksUrl: `${issuer}/.well-known/jwks.json`, mediaDir, publicBaseUrl: 'https://api.rtc.example', tomtomKey: 'test-only-server-key' };
  const upstream = async (url) => {
    assert.equal(url.origin, 'https://api.tomtom.com');
    assert.equal(url.searchParams.get('key'), config.tomtomKey);
    return Response.json(url.pathname.includes('calculateRoute') ? {
      routes: [{ summary: { lengthInMeters: 4290, travelTimeInSeconds: 620 } }],
    } : { results: [{ id: 'poi-1', poi: { name: 'Postmasburg Library' }, position: { lat: -28.33, lon: 23.06 } }] });
  };
  const app = await createApp({ db, config, keyResolver, fetchImpl: upstream });
  server = app.listen(0, '127.0.0.1');
  await once(server, 'listening');
  base = `http://127.0.0.1:${server.address().port}`;
});
beforeEach(async () => {
  await db.query('TRUNCATE rtc_api.posts, rtc_api.media, rtc_api.post_votes, rtc_api.reports, rtc_api.it_queue, rtc_api.municipal_queue, rtc_api.providers, rtc_api.bookings, rtc_api.idempotency, rtc_api.rate_limits CASCADE');
});
after(async () => {
  if (server) await new Promise((resolve) => server.close(resolve));
  if (db) await db.close();
  if (mediaDir) await rm(mediaDir, { recursive: true, force: true });
});

test('empty API collections and dashboard are successful zero states', async () => {
  for (const path of ['/feed', '/timeline', '/reports', '/providers']) {
    const result = await request(path); assert.equal(result.status, 200); assert.deepEqual(result.data, []);
  }
  assert.deepEqual((await request('/dashboard')).data, { pending: 0, working: 0, resolved: 0, total: 0 });
});

test('signed JWT is required; incorrect issuer/audience/expiry and tampering are rejected', async () => {
  for (const bearer of [null, `${token.slice(0, -5)}wrong`, await signed(user, { issuer: 'https://attacker.example' }),
    await signed(user, { audience: 'wrong' })]) {
    assert.equal((await request('/feed', { bearer })).status, 401);
  }
  const expired = await new SignJWT({}).setProtectedHeader({ alg: 'ES256', kid: 'test-key' }).setSubject(user)
    .setIssuer(issuer).setAudience(audience).setIssuedAt(1).setExpirationTime(2).sign(privateKey);
  assert.equal((await request('/feed', { bearer: expired })).status, 401);
  assert.equal((await request('/feed', { bearer: await signed(user, { audience: 'wrong' }) })).status, 401);
  assert.equal((await request('/feed', { bearer: await signed(user, { issuer: 'https://attacker.example' }) })).status, 401);
});

test('multipart image persists, feed/timeline include URL, authenticated image bytes decode', async () => {
  const image = await sharp({ create: { width: 640, height: 320, channels: 3, background: '#ff8a00' } }).png().toBuffer();
  const key = randomUUID();
  const posted = await request('/posts', { method: 'POST', key, body: postForm('A community update with an image.', image) });
  assert.equal(posted.status, 201, JSON.stringify(posted.data));
  assert.match(posted.data.imageUrl, /^https:\/\/api\.rtc\.example\/media\//);
  assert.equal(posted.data.imageWidth, 640); assert.equal(posted.data.imageHeight, 320);
  assert.equal(posted.data.voteCount, 0);
  assert.deepEqual((await request('/feed')).data, [posted.data]);
  assert.deepEqual((await request('/timeline')).data, [posted.data]);
  const path = new URL(posted.data.imageUrl).pathname;
  assert.equal((await request(path, { bearer: null })).status, 401);
  const loaded = await request(path);
  assert.equal(loaded.status, 200); assert.equal(loaded.headers.get('content-type'), 'image/webp');
  assert.equal((await sharp(loaded.data).metadata()).width, 640);
  const again = await request('/posts', { method: 'POST', key, body: postForm('A community update with an image.', image) });
  assert.equal(again.headers.get('idempotency-replayed'), 'true'); assert.deepEqual(again.data, posted.data);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM rtc_api.media')).rows[0].n, 1);
  assert.deepEqual(await readdir(join(mediaDir, 'staging')), []);
});

test('corrupt bytes cannot bypass MIME validation and text-only posts have null image', async () => {
  const failed = await request('/posts', { method: 'POST', key: randomUUID(), body: postForm('This update contains fake media.', Buffer.from('<script>alert(1)</script>')) });
  assert.equal(failed.status, 415);
  const posted = await request('/posts', { method: 'POST', key: randomUUID(), body: postForm('Text only update.') });
  assert.equal(posted.status, 201); assert.equal(posted.data.imageUrl, null);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM rtc_api.posts')).rows[0].n, 1);
});

test('oversized multipart image is rejected before persistence', async () => {
  const result = await request('/posts', { method: 'POST', key: randomUUID(), body: postForm('Oversized attachment.', Buffer.alloc(10 * 1024 * 1024 + 1)) });
  assert.equal(result.status, 413);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM rtc_api.posts')).rows[0].n, 0);
});

test('timeline ownership, private reports and deleted media are isolated', async () => {
  const posted = await request('/posts', { method: 'POST', key: randomUUID(), body: postForm('A public community announcement.') });
  await request('/reports', { method: 'POST', key: randomUUID(), body: { ...validReport, category: 'App Support' } });
  assert.deepEqual((await request('/timeline', { bearer: userBToken })).data, []);
  assert.deepEqual((await request('/reports', { bearer: userBToken })).data, []);
  assert.equal((await request('/feed', { bearer: userBToken })).data.length, 1);
  await db.query('UPDATE rtc_api.posts SET deleted_at=now() WHERE id=$1', [posted.data.id]);
  assert.deepEqual((await request('/feed')).data, []);
  assert.equal((await request(`/posts/${posted.data.id}/vote`, { method: 'PUT', body: { voted: true } })).status, 404);
});

test('cursor pagination preserves same-millisecond rows without duplicates', async () => {
  for (const fraction of ['123001', '123002', '123003']) {
    await db.query(`INSERT INTO rtc_api.posts(id,author_id,body,created_at) VALUES ($1,$2,'Cursor post',$3)`, [randomUUID(), user, `2026-01-01T00:00:00.${fraction}Z`]);
  }
  const ids = [];
  let path = '/feed?limit=1';
  while (path) {
    const result = await request(path); assert.equal(result.status, 200);
    ids.push(...result.data.map((post) => post.id));
    const cursor = result.headers.get('x-next-cursor'); path = cursor ? `/feed?limit=1&before=${cursor}` : null;
  }
  assert.equal(ids.length, 3); assert.equal(new Set(ids).size, 3);
  assert.equal((await request('/feed?before=invalid!')).status, 400);
});

test('voting sets desired state and repeated requests never add a second vote', async () => {
  const created = await request('/posts', { method: 'POST', key: randomUUID(), body: postForm('Vote on this public update.') });
  const path = `/posts/${created.data.id}/vote`;
  for (let i = 0; i < 2; i++) assert.deepEqual((await request(path, { method: 'PUT', body: { voted: true } })).data,
    { postId: created.data.id, voteCount: 1, votedByMe: true });
  assert.equal((await request('/feed')).data[0].votedByMe, true);
  assert.equal((await request(path, { method: 'PUT', bearer: userBToken, body: { voted: true } })).data.voteCount, 2);
  assert.equal((await request(path, { method: 'PUT', body: { voted: false } })).data.voteCount, 1);
});

test('support and infrastructure reports enter distinct queues with real PENDING timestamps', async () => {
  const support = await request('/reports', { method: 'POST', key: randomUUID(), body: { ...validReport, category: 'App Support' } });
  const municipal = await request('/reports', { method: 'POST', key: randomUUID(), body: { ...validReport, category: 'Infrastructure' } });
  assert.equal(support.status, 201); assert.equal(municipal.status, 201);
  assert.equal(support.data.category, 'APP_SUPPORT'); assert.equal(support.data.status, 'PENDING');
  assert.ok(Date.parse(support.data.createdAt) > Date.now() - 60000);
  assert.equal((await db.query('SELECT report_id FROM rtc_api.it_queue')).rows[0].report_id, support.data.id);
  assert.equal((await db.query('SELECT report_id FROM rtc_api.municipal_queue')).rows[0].report_id, municipal.data.id);
});

test('same-user key replays and rejects different body or operation; another user is independent', async () => {
  const key = randomUUID();
  const first = await request('/reports', { method: 'POST', key, body: validReport });
  const repeat = await request('/reports', { method: 'POST', key, body: validReport });
  assert.equal(repeat.status, 201); assert.deepEqual(repeat.data, first.data);
  assert.equal((await request('/reports', { method: 'POST', key, body: { ...validReport, priority: 'LOW' } })).status, 409);
  assert.equal((await request('/posts', { method: 'POST', key, body: postForm('Different operation with the same key.') })).status, 409);
  const other = await request('/reports', { method: 'POST', key, bearer: userBToken, body: validReport });
  assert.equal(other.status, 201); assert.notEqual(other.data.id, first.data.id);
});

test('invalid report input never creates an idempotency record or queue entry', async () => {
  for (const body of [{ ...validReport, body: 'too short' }, { ...validReport, latitude: null },
    { ...validReport, priority: 'CRITICAL' }, { ...validReport, category: 'it_queue; DROP TABLE' }]) {
    assert.equal((await request('/reports', { method: 'POST', key: randomUUID(), body })).status, 400);
  }
  assert.equal((await db.query('SELECT count(*)::int AS n FROM rtc_api.idempotency')).rows[0].n, 0);
});

test('dashboard counts exactly the current user reports in all statuses', async () => {
  for (const status of ['PENDING', 'WORKING', 'RESOLVED']) {
    const created = await request('/reports', { method: 'POST', key: randomUUID(), body: validReport });
    await db.query('UPDATE rtc_api.reports SET status=$1 WHERE id=$2', [status, created.data.id]);
  }
  await request('/reports', { method: 'POST', bearer: userBToken, key: randomUUID(), body: validReport });
  assert.deepEqual((await request('/dashboard')).data, { pending: 1, working: 1, resolved: 1, total: 3 });
  assert.equal((await request('/reports')).data.length, 3);
});

test('concurrent duplicate bookings create one row and replay one response', async () => {
  const providerId = await createProvider(); const key = randomUUID();
  const body = { providerId, startsAt: new Date(Date.now() + 86400000).toISOString(), notes: 'Please call on arrival.' };
  const results = await Promise.all(Array.from({ length: 8 }, () => request('/bookings', { method: 'POST', key, body })));
  for (const result of results) { assert.equal(result.status, 201, JSON.stringify(result.data)); assert.deepEqual(result.data, results[0].data); }
  assert.equal((await db.query('SELECT count(*)::int AS n FROM rtc_api.bookings')).rows[0].n, 1);
  assert.equal((await request('/bookings', { method: 'POST', key, body: { ...body, notes: 'changed' } })).status, 409);
});

test('different idempotency keys cannot double-book the same provider and start time', async () => {
  const providerId = await createProvider();
  const body = { providerId, startsAt: new Date(Date.now() + 86400000).toISOString(), notes: '' };
  const results = await Promise.all([request('/bookings', { method: 'POST', key: randomUUID(), body }),
    request('/bookings', { method: 'POST', key: randomUUID(), bearer: userBToken, body })]);
  assert.deepEqual(results.map((r) => r.status).sort(), [201, 409]);
  assert.equal(results.find((r) => r.status === 409).data.error.code, 'SLOT_UNAVAILABLE');
  assert.equal((await db.query('SELECT count(*)::int AS n FROM rtc_api.idempotency')).rows[0].n, 1);
});

test('provider DTO uses integer cents, unknown provider and past booking are rejected', async () => {
  const providerId = await createProvider();
  assert.equal((await request('/providers')).data[0].rateCents, 35000);
  assert.equal((await request('/bookings', { method: 'POST', key: randomUUID(), body: { providerId, startsAt: '2020-01-01T00:00:00Z', notes: '' } })).status, 400);
  assert.equal((await request('/bookings', { method: 'POST', key: randomUUID(), body: { providerId: randomUUID(), startsAt: '2099-01-01T00:00:00Z', notes: '' } })).status, 404);
});

test('TomTom proxy returns route/search values and validates coordinates before contacting upstream', async () => {
  const route = await request('/locations/route?fromLat=-28.33&fromLon=23.06&toLat=-28.35&toLon=23.08');
  assert.equal(route.status, 200); assert.equal(route.data.distanceMeters, 4290); assert.equal(route.data.travelTimeSeconds, 620);
  assert.ok(Date.parse(route.data.calculatedAt));
  assert.equal((await request('/locations/route?fromLat=NaN&fromLon=23&toLat=-28&toLon=23')).status, 400);
  assert.equal((await request('/locations/route?fromLat=&fromLon=23&toLat=-28&toLon=23')).status, 400);
  assert.equal((await request('/locations/search?q=library&lat=-28.33&lon=23.06')).data[0].name, 'Postmasburg Library');
});

test('TomTom unavailable, no route, rate limit and malformed upstream are explicit errors without fake values', async () => {
  await assert.rejects(createTomTom('').route({}), { code: 'LOCATION_CONFIGURATION_REQUIRED' });
  await assert.rejects(createTomTom('key', async () => Response.json({ routes: [] })).route({}), { code: 'NO_ROUTE' });
  await assert.rejects(createTomTom('key', async () => new Response('', { status: 429 })).route({}), { code: 'LOCATION_RATE_LIMITED' });
  await assert.rejects(createTomTom('key', async () => Response.json({ routes: [{ summary: { lengthInMeters: -1 } }] })).route({}), { code: 'LOCATION_INVALID_RESPONSE' });
  await assert.rejects(createTomTom('key', async () => { throw new Error('timeout secret-key'); }).route({}), (error) => error.code === 'LOCATION_UNAVAILABLE' && !error.message.includes('secret-key'));
});

test('production configuration requires HTTPS identity and preserves issuer trailing slash', () => {
  const env = { DATABASE_URL: 'postgresql://localhost/rtc', PUBLIC_BASE_URL: 'https://api.rtc.example/', OIDC_ISSUER: 'https://issuer.example/',
    OIDC_AUDIENCE: 'rtc', OIDC_JWKS_URL: 'https://issuer.example/jwks', MEDIA_DIR: mediaDir };
  assert.equal(readConfig(env).issuer, 'https://issuer.example/');
  assert.equal(readConfig({ ...env, OIDC_ISSUER: 'https://issuer.example' }).issuer, 'https://issuer.example');
  assert.equal(readConfig(env).publicBaseUrl, 'https://api.rtc.example');
  assert.throws(() => readConfig({ ...env, OIDC_ISSUER: 'http://issuer.example' }));
  assert.throws(() => readConfig({ ...env, PUBLIC_BASE_URL: 'https://name:password@api.example' }));
});
