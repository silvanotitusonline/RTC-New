import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { GoogleAuth } from "npm:google-auth-library";
import { boundedText, writeAudit, type EdgeAdminClient } from "../_shared/auth.ts";
import { checkLanguageProviders } from "./readiness.ts";

const URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const PROJECT = Deno.env.get("GOOGLE_CLOUD_PROJECT_ID") ?? "";
const MAX_JOBS = 20;
const CONCURRENCY = 12;
const headers = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };

function json(status: number, body: Record<string, unknown>) { return new Response(JSON.stringify(body), { status, headers }); }
async function fingerprint(token: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
async function verifyDailyPostSchedulerCaller(req: Request, admin: EdgeAdminClient) {
  const secret = boundedText(req.headers.get("x-rtc-daily-post-scheduler-secret"), 512);
  if (!secret) throw new Error("AUTH_REQUIRED");
  const result = await admin.rpc("assert_daily_post_scheduler_secret", { p_secret: secret });
  if (result.error || result.data !== true) throw new Error("AUTH_REQUIRED");
}
async function credentials(admin: EdgeAdminClient) {
  const fromEnv = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "";
  if (fromEnv) return JSON.parse(fromEnv);
  const result = await admin.rpc("get_firebase_fcm_service_account");
  if (result.error || typeof result.data !== "string" || !result.data.trim()) throw new Error("FCM_CREDENTIALS_UNAVAILABLE");
  return JSON.parse(result.data);
}
async function accessToken(admin: EdgeAdminClient) {
  const creds = await credentials(admin);
  const auth = new GoogleAuth({ credentials: creds, scopes: ["https://www.googleapis.com/auth/firebase.messaging"] });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  if (!token.token) throw new Error("FCM_CREDENTIALS_UNAVAILABLE");
  return { token: token.token, project: PROJECT || creds.project_id };
}
async function pool<T>(items: T[], fn: (item: T) => Promise<void>) {
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, items.length) }, async () => {
    while (cursor < items.length) await fn(items[cursor++]);
  }));
}
async function allDevices(admin: EdgeAdminClient) {
  const devices: Array<{ id: string; user_id: string; fcm_token: string }> = [];
  let from = 0;
  for (;;) {
    const result = await admin.from("device_registrations").select("id,user_id,fcm_token").range(from, from + 999);
    if (result.error) throw new Error("DEVICE_LOOKUP_FAILED");
    const page = Array.isArray(result.data) ? result.data : [];
    devices.push(...page.filter((d: any) => typeof d?.fcm_token === "string" && d.fcm_token.length > 20));
    if (page.length < 1000) return devices;
    from += 1000;
    if (from >= 50_000) return devices;
  }
}
async function sendDevice(
  admin: EdgeAdminClient, bearer: string, project: string,
  job: any, post: any, device: { id: string; user_id: string; fcm_token: string },
) {
  const fp = await fingerprint(device.fcm_token);
  const existing = await admin.from("notification_delivery_attempts")
    .select("id,state").eq("source_type", "DAILY_POST_JOB").eq("source_id", job.job_id).eq("token_fingerprint", fp).maybeSingle();
  if (existing.error) throw new Error("ATTEMPT_LOOKUP_FAILED");
  if (existing.data?.state === "ACCEPTED" || existing.data?.state === "PERMANENT_FAILURE") return false;
  const attempt = await admin.from("notification_delivery_attempts").upsert({
    source_type: "DAILY_POST_JOB", source_id: job.job_id, device_registration_id: device.id,
    token_fingerprint: fp, state: "PENDING",
  }, { onConflict: "source_type,source_id,token_fingerprint" }).select("id,attempt_count").single();
  if (attempt.error || !attempt.data) throw new Error("ATTEMPT_PERSISTENCE_FAILED");
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message: {
      token: device.fcm_token,
      notification: { title: post.publicationType === "BREAKING" ? `BREAKING: ${post.headline}` : post.headline, body: post.excerpt || "Open The Daily Post for the full story." },
      data: {
        type: "DAILY_POST",
        notification_type: "DAILY_POST",
        post_id: post.postId,
        destination: "resident_explore",
        tab: "daily_post",
        idempotency_key: job.dispatch_key,
      },
      android: { priority: post.publicationType === "BREAKING" ? "HIGH" : "NORMAL" },
    } }),
  });
  if (response.ok) {
    const saved = await admin.from("notification_delivery_attempts").update({ state: "ACCEPTED", accepted_at: new Date().toISOString(), last_http_status: response.status, attempt_count: (attempt.data.attempt_count ?? 0) + 1 }).eq("id", attempt.data.id);
    if (saved.error) throw new Error("ATTEMPT_PERSISTENCE_FAILED");
    return true;
  }
  let payload: any = {};
  try { payload = await response.json(); } catch { /* no-op */ }
  const code = String(payload?.error?.details?.find((entry: any) => typeof entry?.errorCode === "string")?.errorCode ?? payload?.error?.status ?? `HTTP_${response.status}`);
  const stale = code === "UNREGISTERED";
  await admin.from("notification_delivery_attempts").update({
    state: stale ? "PERMANENT_FAILURE" : "RETRY_PENDING",
    permanently_failed_at: stale ? new Date().toISOString() : null,
    next_retry_at: stale ? null : new Date(Date.now() + 5 * 60_000).toISOString(),
    last_http_status: response.status, last_error_code: code, attempt_count: (attempt.data.attempt_count ?? 0) + 1,
  }).eq("id", attempt.data.id);
  if (stale) await admin.from("device_registrations").delete().eq("id", device.id);
  if (!stale) throw new Error(`FCM_RETRY:${code}`);
  return false;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "POST is required." });
  if (!URL || !SERVICE) return json(503, { error: "Server configuration is incomplete." });
  const admin = createClient(URL, SERVICE, { auth: { persistSession: false, autoRefreshToken: false } }) as unknown as EdgeAdminClient;
  try {
    await verifyDailyPostSchedulerCaller(req, admin);
    // Explicit, server-only diagnostic mode: validate FCM requests without
    // delivery and exercise the actual translation, TTS, and audio-storage path.
    const body = await req.text();
    if (new TextEncoder().encode(body).byteLength > 1024) return json(400, { error: "Request too large." });
    const input = body.trim() ? JSON.parse(body) : {};
    if (input?.action === "check") {
      const devices = await allDevices(admin);
      let push: Record<string, unknown>;
      try {
        const auth = await accessToken(admin);
        let valid = 0;
        const failures: string[] = [];
        for (const device of devices) {
          const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(auth.project)}/messages:send`, {
            method: "POST", headers: { Authorization: `Bearer ${auth.token}`, "Content-Type": "application/json" },
            body: JSON.stringify({ validate_only: true, message: { token: device.fcm_token, data: { type: "RTC_READINESS_CHECK" } } }),
          });
          if (response.ok) valid++;
          else {
            const failure = await response.json().catch(() => ({}));
            const reason = failure?.error?.details?.find((entry: any) => typeof entry?.errorCode === "string")?.errorCode;
            const safeReason = typeof reason === "string" && /^[A-Z_]{1,64}$/.test(reason) ? reason : "UNAVAILABLE";
            failures.push(`HTTP_${response.status}_${safeReason}`);
          }
        }
        push = { status: devices.length > 0 && valid === devices.length ? "passed" : "blocked", mode: "validate_only", registeredDevices: devices.length, validatedDevices: valid, failures, deliveryConfirmed: false };
      } catch {
        push = { status: "blocked", code: "FCM_CREDENTIALS_OR_API_UNAVAILABLE", deliveryConfirmed: false };
      }
      const languages = await checkLanguageProviders(admin);
      return json(200, { checkedAt: new Date().toISOString(), push, ...languages });
    }
    const claimed = await admin.rpc("daily_post_claim_due_jobs_v1", { p_limit: MAX_JOBS });
    if (claimed.error) throw new Error("JOB_CLAIM_FAILED");
    const jobs = Array.isArray(claimed.data) ? claimed.data : [];
    let completed = 0, failed = 0, pushed = 0;
    for (const job of jobs) {
      try {
        const executed = await admin.rpc("daily_post_execute_job_v1", { p_job_id: job.job_id });
        if (executed.error || !executed.data) throw new Error("JOB_EXECUTION_FAILED");
        const post = executed.data;
        if (job.push_enabled === true) {
          const devices = await allDevices(admin);
          if (devices.length > 0) {
            const auth = await accessToken(admin);
            const errors: string[] = [];
            await pool(devices, async (device) => {
              try { if (await sendDevice(admin, auth.token, auth.project, job, post, device)) pushed++; }
              catch (error) { errors.push(error instanceof Error ? error.message : "FCM_FAILED"); }
            });
            if (errors.length > 0) throw new Error(`FCM_PARTIAL_FAILURE:${errors.length}`);
          }
        }
        await admin.rpc("daily_post_complete_job_v1", { p_job_id: job.job_id, p_success: true, p_error: null });
        completed++;
      } catch (error) {
        const message = error instanceof Error ? error.message : "UNKNOWN";
        await admin.rpc("daily_post_complete_job_v1", { p_job_id: job.job_id, p_success: false, p_error: message });
        await writeAudit(admin, { actorId: null, eventType: "DAILY_POST_JOB_FAILED", result: "FAILED", entityType: "DAILY_POST", entityId: job.post_id, metadata: { jobId: job.job_id, code: message } });
        failed++;
      }
    }
    return json(200, { claimed: jobs.length, completed, failed, pushed });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    return json(code === "AUTH_REQUIRED" ? 401 : 500, { error: "Daily Post scheduler could not complete.", code });
  }
});
