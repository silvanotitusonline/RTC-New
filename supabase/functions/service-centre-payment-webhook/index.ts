import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const YOCO_WEBHOOK_SECRET = Deno.env.get("YOCO_WEBHOOK_SECRET") ?? "";
const INTERNAL_NOTIFY_SECRET = Deno.env.get("SERVICE_CENTRE_INTERNAL_NOTIFY_SECRET") ?? "";
const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };
function json(status: number, body: Record<string, unknown>) { return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS }); }
function decodeBase64(value: string): Uint8Array { const binary = atob(value); return Uint8Array.from(binary, c => c.charCodeAt(0)); }
function encodeBase64(value: ArrayBuffer): string { const bytes = new Uint8Array(value); let binary = ""; for (const byte of bytes) binary += String.fromCharCode(byte); return btoa(binary); }
function constantTimeEqual(a: string, b: string): boolean { if (!a || a.length !== b.length) return false; let mismatch = 0; for (let i = 0; i < a.length; i++) mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i); return mismatch === 0; }

async function verifySignature(rawBody: string, request: Request): Promise<boolean> {
  const webhookId = request.headers.get("webhook-id")?.trim() ?? "";
  const timestamp = request.headers.get("webhook-timestamp")?.trim() ?? "";
  const signatures = request.headers.get("webhook-signature")?.trim() ?? "";
  const seconds = Number(timestamp);
  if (!webhookId || !Number.isFinite(seconds) || Math.abs(Date.now() / 1000 - seconds) > 180 || !signatures || !YOCO_WEBHOOK_SECRET.startsWith("whsec_")) return false;
  const secretBytes = decodeBase64(YOCO_WEBHOOK_SECRET.slice("whsec_".length));
  const key = await crypto.subtle.importKey("raw", secretBytes, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signedContent = `${webhookId}.${timestamp}.${rawBody}`;
  const expected = encodeBase64(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(signedContent)));
  return signatures.split(" ").some(candidate => {
    const [version, signature] = candidate.split(",", 2);
    return version === "v1" && constantTimeEqual(signature ?? "", expected);
  });
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json(405, { error: "POST is required." });
  if (!URL || !SERVICE || !YOCO_WEBHOOK_SECRET) return json(503, { error: "Webhook configuration is incomplete." });
  const rawBody = await request.text();
  let authentic = false;
  try { authentic = await verifySignature(rawBody, request); } catch { authentic = false; }
  if (!authentic) return json(403, { error: "Invalid webhook signature." });

  let event: any;
  try { event = JSON.parse(rawBody); } catch { return json(400, { error: "Invalid webhook payload." }); }
  if (event?.type !== "payment.succeeded") return json(200, { received: true, ignored: true });

  const payment = event?.payload;
  const checkoutId = typeof payment?.metadata?.checkoutId === "string" ? payment.metadata.checkoutId.trim() : "";
  const paymentId = typeof payment?.id === "string" ? payment.id.trim() : "";
  const amountCents = typeof payment?.amount === "number" ? payment.amount : Number(payment?.amount);
  const currencyCode = typeof payment?.currency === "string" ? payment.currency.trim().toUpperCase() : "";
  if (!checkoutId || !paymentId || !Number.isInteger(amountCents) || amountCents <= 0 || currencyCode !== "ZAR") return json(400, { error: "Payment payload is incomplete." });

  const admin = createClient(URL, SERVICE, { auth: { persistSession: false, autoRefreshToken: false } });
  const confirmed = await admin.rpc("service_centre_confirm_commitment_payment", {
    p_external_checkout_id: checkoutId,
    p_external_payment_id: paymentId,
    p_amount_cents: amountCents,
    p_currency_code: currencyCode,
  });
  if (confirmed.error || !confirmed.data) return json(409, { error: confirmed.error?.message ?? "Payment could not be reconciled." });
  const bookingId = typeof confirmed.data.bookingId === "string" ? confirmed.data.bookingId : "";

  if (bookingId && INTERNAL_NOTIFY_SECRET) {
    try {
      await fetch(`${URL}/functions/v1/service-centre-notify`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-service-centre-internal-secret": INTERNAL_NOTIFY_SECRET },
        body: JSON.stringify({ bookingId, eventType: "SERVICE_BOOKING_CONFIRMED" }),
      });
    } catch {
      // Payment confirmation remains authoritative even if FCM delivery is temporarily unavailable.
    }
  }
  return json(200, { received: true, bookingId, status: "CONFIRMED" });
});
