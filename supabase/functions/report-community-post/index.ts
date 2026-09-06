import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  APP_ROLES,
  SecurityError,
  boundedText,
  enforceRateLimit,
  isUuid,
  readJsonObject,
  verifyCaller,
  writeAudit,
} from "../_shared/auth.ts";

const H = {
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const CONCURRENCY = 6;
const ATTEMPTS = 3;

type SA = { project_id: string; client_email: string; private_key: string; token_uri?: string };
const sleep = (n: number) => new Promise((resolve) => setTimeout(resolve, n));
function json(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: H }); }
function b64(v: ArrayBuffer | string) {
  const b = typeof v === "string" ? new TextEncoder().encode(v) : new Uint8Array(v);
  let s = "";
  b.forEach((x) => s += String.fromCharCode(x));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
function pem(p: string) {
  const x = p.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(/\s/g, "");
  const b = atob(x);
  const o = new Uint8Array(b.length);
  for (let i = 0; i < b.length; i++) o[i] = b.charCodeAt(i);
  return o.buffer;
}

async function token(a: SA) {
  const now = Math.floor(Date.now() / 1000);
  const uri = a.token_uri ?? "https://oauth2.googleapis.com/token";
  const h = b64(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const c = b64(JSON.stringify({ iss: a.client_email, scope: SCOPE, aud: uri, iat: now, exp: now + 3600 }));
  const u = `${h}.${c}`;
  const k = await crypto.subtle.importKey("pkcs8", pem(a.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign({ name: "RSASSA-PKCS1-v1_5" }, k, new TextEncoder().encode(u));
  const r = await fetch(uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${u}.${b64(sig)}`,
    }),
  });
  if (!r.ok) throw new Error("FCM_TOKEN_FAILED");
  const z = await r.json() as { access_token?: string };
  if (!z.access_token) throw new Error("FCM_TOKEN_FAILED");
  return z.access_token;
}

async function fp(t: string) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function classify(s: number, b: any) {
  const code = b?.error?.details?.find?.((x: any) => x?.errorCode)?.errorCode ?? b?.error?.status ?? `HTTP_${s}`;
  return {
    code: String(code),
    stale: s === 404 || s === 410 || code === "UNREGISTERED",
    retry: s === 429 || s >= 500 || code === "UNAVAILABLE" || code === "INTERNAL",
  };
}

async function pool<T, R>(a: T[], f: (x: T) => Promise<R>) {
  const o: R[] = [];
  let c = 0;
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, a.length) }, async () => {
    while (c < a.length) {
      const i = c++;
      o[i] = await f(a[i]);
    }
  }));
  return o;
}

async function pushOne(
  service: ReturnType<typeof createClient>,
  bearer: string,
  a: SA,
  reportId: string,
  postId: string,
  reason: string,
  d: any,
) {
  const fingerprint = await fp(d.fcm_token);
  const { data: existing } = await service.from("notification_delivery_attempts")
    .select("id,state,attempt_count,next_retry_at")
    .eq("source_type", "MODERATION_REPORT")
    .eq("source_id", reportId)
    .eq("token_fingerprint", fingerprint)
    .maybeSingle();
  if (existing?.state === "ACCEPTED" || existing?.state === "PERMANENT_FAILURE") return existing.state;

  const { data: attempt, error } = await service.from("notification_delivery_attempts").upsert({
    source_type: "MODERATION_REPORT",
    source_id: reportId,
    device_registration_id: d.id,
    token_fingerprint: fingerprint,
    state: "PENDING",
    next_retry_at: null,
  }, { onConflict: "source_type,source_id,token_fingerprint" }).select("id,attempt_count").single();
  if (error || !attempt) return "RETRY_PENDING";

  let httpStatus = 0;
  let lastCode = "UNKNOWN";
  for (let i = 0; i < ATTEMPTS; i++) {
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${a.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: d.fcm_token,
          notification: { title: "New Community report", body: "A Community post needs moderation review." },
          data: { route: "moderation_report", report_id: reportId, post_id: postId, reason_code: reason },
          android: { priority: "high" },
        },
      }),
    });
    if (r.ok) {
      await service.from("notification_delivery_attempts").update({
        state: "ACCEPTED",
        attempt_count: (attempt.attempt_count ?? 0) + i + 1,
        last_http_status: r.status,
        accepted_at: new Date().toISOString(),
        last_error_code: null,
        next_retry_at: null,
      }).eq("id", attempt.id);
      return "ACCEPTED";
    }

    let body: any = {};
    try { body = await r.json(); } catch { /* no response body */ }
    const cl = classify(r.status, body);
    httpStatus = r.status;
    lastCode = cl.code;
    if (cl.stale) {
      await service.from("device_registrations").delete().eq("id", d.id);
      await service.from("notification_delivery_attempts").update({
        state: "PERMANENT_FAILURE",
        attempt_count: (attempt.attempt_count ?? 0) + i + 1,
        last_http_status: httpStatus,
        last_error_code: lastCode,
        permanently_failed_at: new Date().toISOString(),
      }).eq("id", attempt.id);
      return "PERMANENT_FAILURE";
    }
    if (!cl.retry) {
      await service.from("notification_delivery_attempts").update({
        state: "PERMANENT_FAILURE",
        attempt_count: (attempt.attempt_count ?? 0) + i + 1,
        last_http_status: httpStatus,
        last_error_code: lastCode,
        permanently_failed_at: new Date().toISOString(),
      }).eq("id", attempt.id);
      return "PERMANENT_FAILURE";
    }
    if (i < ATTEMPTS - 1) await sleep(250 * Math.pow(2, i) + Math.floor(Math.random() * 150));
  }

  await service.from("notification_delivery_attempts").update({
    state: "RETRY_PENDING",
    attempt_count: (attempt.attempt_count ?? 0) + ATTEMPTS,
    last_http_status: httpStatus,
    last_error_code: lastCode,
    next_retry_at: new Date(Date.now() + 5 * 60_000).toISOString(),
  }).eq("id", attempt.id);
  return "RETRY_PENDING";
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: H });
  if (req.method !== "POST") return json({ error: "Use POST for Community reports." }, 405);

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
  if (!url || !anon || !serviceKey) return json({ error: "The report service is not configured." }, 503);

  const service = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  let caller;
  try {
    caller = await verifyCaller(req, { supabaseUrl: url, publishableKey: anon, admin: service }, APP_ROLES);
  } catch {
    await writeAudit(service, { actorId: null, eventType: "COMMUNITY_POST_REPORT_DENIED", result: "DENIED", metadata: { reason: "authentication_or_role" } });
    return json({ error: "A signed-in authorised account is required." }, 401);
  }

  let input: Record<string, unknown>;
  try {
    input = await readJsonObject(req, 8_192);
  } catch {
    await writeAudit(service, { actorId: caller.userId, eventType: "COMMUNITY_POST_REPORT_DENIED", result: "DENIED", metadata: { reason: "invalid_json" } });
    return json({ error: "The report details are invalid." }, 400);
  }

  const postId = isUuid(input.postId) ? input.postId : null;
  const reasonCode = boundedText(input.reasonCode, 40);
  const detail = typeof input.detail === "string" ? input.detail.trim() : "";
  if (!postId || !reasonCode || detail.length > 2000) {
    await writeAudit(service, { actorId: caller.userId, eventType: "COMMUNITY_POST_REPORT_DENIED", result: "DENIED", metadata: { reason: "invalid_input" } });
    return json({ error: "Choose a valid post and report reason." }, 400);
  }

  try {
    await enforceRateLimit(service, "community_post_report", caller.userId, 5, 300);
  } catch (error) {
    const rateLimited = error instanceof SecurityError && error.message === "RATE_LIMITED";
    await writeAudit(service, {
      actorId: caller.userId,
      eventType: "COMMUNITY_POST_REPORT_DENIED",
      result: rateLimited ? "DENIED" : "FAILED",
      entityType: "COMMUNITY_POST",
      entityId: postId,
      metadata: { reason: rateLimited ? "rate_limited" : "rate_limit_unavailable" },
    });
    return json({ error: rateLimited ? "Too many reports. Try again later." : "The report service is temporarily unavailable." }, rateLimited ? 429 : 503);
  }

  const authorization = req.headers.get("Authorization")!;
  const userClient = createClient(url, anon, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: reportId, error: reportError } = await userClient.rpc("report_community_post", {
    p_post_id: postId,
    p_reason_code: reasonCode,
    p_detail: detail,
  });
  if (reportError || typeof reportId !== "string") {
    await writeAudit(service, { actorId: caller.userId, eventType: "COMMUNITY_POST_REPORT_DENIED", result: "DENIED", entityType: "COMMUNITY_POST", entityId: postId, metadata: { reason: "rpc_rejected" } });
    return json({ error: "The report could not be submitted." }, 400);
  }

  try {
    const credentials = await service.rpc("get_firebase_fcm_service_account");
    if (credentials.error || typeof credentials.data !== "string" || !credentials.data.trim()) throw new Error("NO_FCM");
    const account = JSON.parse(credentials.data) as SA;
    const { data: roles } = await service.from("user_roles").select("user_id").in("role", ["MODERATOR", "SYSTEM_ADMIN"]);
    const ids = [...new Set((roles ?? []).map((x: any) => x.user_id))];
    const { data: devices } = ids.length
      ? await service.from("device_registrations").select("id,fcm_token").in("user_id", ids)
      : { data: [] as any[] };
    const unique = [...new Map((devices ?? []).map((d: any) => [d.fcm_token, d])).values()];
    if (!unique.length) {
      await writeAudit(service, { actorId: caller.userId, eventType: "COMMUNITY_POST_REPORTED", result: "ALLOWED", entityType: "COMMUNITY_POST", entityId: postId, metadata: { reportId, eligibleDevices: 0 } });
      return json({ reportId, push: { eligibleDevices: 0, accepted: 0, retryPending: 0, permanentFailures: 0 } });
    }

    const bearer = await token(account);
    const results = await pool(unique, (device) => pushOne(service, bearer, account, reportId, postId, reasonCode, device));
    await writeAudit(service, { actorId: caller.userId, eventType: "COMMUNITY_POST_REPORTED", result: "ALLOWED", entityType: "COMMUNITY_POST", entityId: postId, metadata: { reportId, eligibleDevices: results.length } });
    return json({
      reportId,
      push: {
        eligibleDevices: results.length,
        accepted: results.filter((x) => x === "ACCEPTED").length,
        retryPending: results.filter((x) => x === "RETRY_PENDING").length,
        permanentFailures: results.filter((x) => x === "PERMANENT_FAILURE").length,
      },
    });
  } catch (error) {
    await writeAudit(service, { actorId: caller.userId, eventType: "COMMUNITY_POST_REPORT_NOTIFICATION_FAILED", result: "FAILED", entityType: "COMMUNITY_POST", entityId: postId, metadata: { reportId, code: error instanceof Error ? error.message : "UNKNOWN" } });
    return json({ reportId, push: { deferred: true } }, 202);
  }
});
