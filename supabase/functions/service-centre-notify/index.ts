import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { GoogleAuth } from "npm:google-auth-library";
import { authenticateCaller, enforceRateLimit, isUuid, readJsonObject } from "../_shared/auth.ts";

const URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const PROJECT = Deno.env.get("GOOGLE_CLOUD_PROJECT_ID") ?? "";
const EVENTS = new Set([
  "SERVICE_BOOKING_NEW",
  "SERVICE_BOOKING_ACCEPTED",
  "SERVICE_BOOKING_DECLINED",
  "SERVICE_BOOKING_MESSAGE",
  "SERVICE_BOOKING_COMPLETED",
  "SERVICE_BOOKING_CANCELLED",
]);
const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "Vary": "Authorization" };
function json(status: number, body: Record<string, unknown>) { return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS }); }
function userClient(authorization: string) { return createClient(URL, ANON, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false, autoRefreshToken: false } }); }
async function firebaseCredential(admin: ReturnType<typeof createClient>) { const env = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? ""; if (env) return JSON.parse(env); const { data, error } = await admin.rpc("get_firebase_fcm_service_account"); if (error || typeof data !== "string" || !data.trim()) throw new Error("FCM_CREDENTIALS_UNAVAILABLE"); return JSON.parse(data); }
async function firebaseAccess(admin: ReturnType<typeof createClient>) { const credentials = await firebaseCredential(admin); const auth = new GoogleAuth({ credentials, scopes: ["https://www.googleapis.com/auth/firebase.messaging"] }); const client = await auth.getClient(); const token = await client.getAccessToken(); if (!token.token) throw new Error("FCM_CREDENTIALS_UNAVAILABLE"); return { token: token.token, project: PROJECT || credentials.project_id }; }

async function deliver(admin: ReturnType<typeof createClient>, context: any) {
  const recipient = typeof context?.recipientUserId === "string" ? context.recipientUserId : "";
  const title = typeof context?.title === "string" ? context.title.slice(0, 120) : "Service booking";
  const body = typeof context?.body === "string" ? context.body.slice(0, 600) : "Your booking has been updated.";
  if (!isUuid(recipient)) throw new Error("RECIPIENT_INVALID");
  const rawPayload = context?.payload && typeof context.payload === "object" ? context.payload as Record<string, unknown> : {};
  const payload = Object.fromEntries(Object.entries(rawPayload).slice(0, 32).map(([key, value]) => [key.slice(0, 64), String(value ?? "").slice(0, 1024)]));
  payload.title = title;
  payload.body = body;

  const eventType = String(payload.notification_type ?? "SERVICE_BOOKING");
  const bookingId = String(payload.booking_id ?? "");
  const messageId = String(payload.message_id ?? "");
  const { data: existing } = await admin.from("notification_events").select("id")
    .eq("recipient_id", recipient).eq("notification_type", "FCM")
    .contains("payload", { notification_type: eventType, booking_id: bookingId, message_id: messageId })
    .order("created_at", { ascending: false }).limit(1).maybeSingle();
  let notificationEventId = existing?.id as string | undefined;
  if (!notificationEventId) {
    const inserted = await admin.from("notification_events").insert({ recipient_id: recipient, notification_type: "FCM", title, body, payload }).select("id").single();
    if (inserted.error || !inserted.data) throw new Error("EVENT_PERSISTENCE_FAILED");
    notificationEventId = inserted.data.id;
  }

  const devices = await admin.from("device_registrations").select("id,fcm_token").eq("user_id", recipient).limit(100);
  if (devices.error) throw new Error("DEVICE_LOOKUP_FAILED");
  if (!(devices.data ?? []).length) return { notificationEventId, delivered: 0 };
  const access = await firebaseAccess(admin);
  let delivered = 0;
  for (const device of devices.data ?? []) {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${access.project}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access.token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token: device.fcm_token, data: payload, android: { priority: "HIGH" } } }),
    });
    if (response.ok) delivered += 1;
    else if (response.status === 404 || response.status === 410) await admin.from("device_registrations").delete().eq("id", device.id);
  }
  if (delivered > 0) await admin.from("notification_events").update({ delivered_at: new Date().toISOString() }).eq("id", notificationEventId);
  return { notificationEventId, delivered };
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json(405, { error: "POST is required." });
  if (!URL || !ANON || !SERVICE) return json(503, { error: "Notification service configuration is incomplete." });
  const admin = createClient(URL, SERVICE, { auth: { persistSession: false, autoRefreshToken: false } });
  try {
    const input = await readJsonObject(request, 4096);
    const bookingId = isUuid(input.bookingId) ? input.bookingId : null;
    const eventType = typeof input.eventType === "string" && EVENTS.has(input.eventType) ? input.eventType : null;
    const messageId = input.messageId == null ? null : (isUuid(input.messageId) ? input.messageId : null);
    if (!bookingId || !eventType || (input.messageId != null && !messageId)) return json(400, { error: "A valid bookingId and eventType are required." });

    const { userId } = await authenticateCaller(request, { supabaseUrl: URL, publishableKey: ANON, admin });
    await enforceRateLimit(admin, "service_centre_notify", userId, 30, 60);
    const client = userClient(request.headers.get("Authorization") ?? "");
    const response = await client.rpc("service_centre_notification_context", {
      p_booking_id: bookingId,
      p_event_type: eventType,
      p_message_id: messageId,
    });
    if (response.error || !response.data) return json(403, { error: response.error?.message ?? "Notification is not permitted." });

    const result = await deliver(admin, response.data);
    return json(200, result);
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    return json(code === "AUTH_REQUIRED" ? 401 : code === "RATE_LIMITED" ? 429 : code === "FCM_CREDENTIALS_UNAVAILABLE" ? 503 : 500, { error: "Booking notification could not be delivered." });
  }
});
