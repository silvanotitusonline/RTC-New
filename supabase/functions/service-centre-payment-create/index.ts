import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { authenticateCaller, enforceRateLimit, isUuid, publicError, readJsonObject } from "../_shared/auth.ts";

const URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const YOCO_SECRET_KEY = Deno.env.get("YOCO_SECRET_KEY") ?? "";
const RETURN_URL = Deno.env.get("SERVICE_CENTRE_PAYMENT_RETURN_URL")?.trim() ?? "";
const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function userClient(authorization: string) {
  return createClient(URL, ANON, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json(405, { error: "POST is required." });
  if (!URL || !ANON || !SERVICE || !YOCO_SECRET_KEY) return json(503, { error: "Payment service configuration is incomplete." });

  const admin = createClient(URL, SERVICE, { auth: { persistSession: false, autoRefreshToken: false } });
  try {
    const { userId } = await authenticateCaller(request, { supabaseUrl: URL, publishableKey: ANON, admin });
    await enforceRateLimit(admin, "service_centre_payment_create", userId, 8, 60);
    const input = await readJsonObject(request, 4096);
    const bookingId = isUuid(input.bookingId) ? input.bookingId : null;
    const idempotencyKey = isUuid(input.idempotencyKey) ? input.idempotencyKey : null;
    if (!bookingId || !idempotencyKey) return json(400, { error: "bookingId and idempotencyKey are required." });

    const authorization = request.headers.get("Authorization") ?? "";
    const client = userClient(authorization);
    const prepared = await client.rpc("service_centre_prepare_commitment_payment", {
      p_booking_id: bookingId,
      p_idempotency_key: idempotencyKey,
    });
    if (prepared.error || !prepared.data || typeof prepared.data !== "object") {
      return json(409, { error: prepared.error?.message ?? "This booking is not ready for payment." });
    }

    const payment = prepared.data as Record<string, unknown>;
    const paymentId = typeof payment.paymentId === "string" ? payment.paymentId : "";
    const amountCents = typeof payment.amountCents === "number" ? payment.amountCents : Number(payment.amountCents);
    const currencyCode = payment.currencyCode === "ZAR" ? "ZAR" : "";
    const authoritativeIdempotencyKey = typeof payment.idempotencyKey === "string" ? payment.idempotencyKey : idempotencyKey;
    if (!isUuid(paymentId) || !Number.isInteger(amountCents) || amountCents < 200 || currencyCode !== "ZAR") {
      return json(409, { error: "The server-side commitment fee is invalid." });
    }

    const checkoutBody: Record<string, unknown> = {
      amount: amountCents,
      currency: currencyCode,
      metadata: { bookingId, paymentId, purpose: "RTC_SERVICE_CENTRE_COMMITMENT_FEE" },
    };
    if (RETURN_URL.startsWith("https://")) {
      const separator = RETURN_URL.includes("?") ? "&" : "?";
      checkoutBody.successUrl = `${RETURN_URL}${separator}bookingId=${encodeURIComponent(bookingId)}&status=success`;
      checkoutBody.cancelUrl = `${RETURN_URL}${separator}bookingId=${encodeURIComponent(bookingId)}&status=cancelled`;
      checkoutBody.failureUrl = `${RETURN_URL}${separator}bookingId=${encodeURIComponent(bookingId)}&status=failed`;
    }

    const checkoutResponse = await fetch("https://payments.yoco.com/api/checkouts", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${YOCO_SECRET_KEY}`,
        "Content-Type": "application/json",
        "Idempotency-Key": authoritativeIdempotencyKey,
      },
      body: JSON.stringify(checkoutBody),
    });
    let checkout: Record<string, unknown> = {};
    try { checkout = await checkoutResponse.json(); } catch { /* handled below */ }
    if (!checkoutResponse.ok) {
      return json(checkoutResponse.status >= 500 ? 502 : 409, { error: "Yoco could not create the hosted checkout." });
    }
    const externalCheckoutId = typeof checkout.id === "string" ? checkout.id.trim() : "";
    const redirectUrl = typeof checkout.redirectUrl === "string" ? checkout.redirectUrl.trim() : "";
    if (!externalCheckoutId || !redirectUrl.startsWith("https://")) return json(502, { error: "Yoco returned an incomplete checkout." });

    const attached = await admin.rpc("service_centre_attach_commitment_checkout", {
      p_payment_id: paymentId,
      p_external_checkout_id: externalCheckoutId,
    });
    if (attached.error) return json(500, { error: "Checkout was created but could not be attached to the booking." });

    return json(200, {
      paymentId,
      bookingId,
      redirectUrl,
      amount: String(payment.amount ?? (amountCents / 100).toFixed(2)),
      currencyCode,
    });
  } catch (error) {
    const safe = publicError(error);
    return json(safe.status, safe.body);
  }
});
