import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  enforceRateLimit,
  isUuid,
  publicErrorStatus,
  readJsonObject,
  verifyCaller,
} from "../_shared/auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const NOTIFICATION_DISPATCH_ROLES = ["SYSTEM_ADMIN", "CONTENT_EDITOR"] as const;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const headers = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  ...corsHeaders,
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (request.method !== "POST") return json(405, { error: "POST is required." });
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !SUPABASE_SERVICE_ROLE_KEY) {
    return json(503, { error: "Server configuration is incomplete." });
  }

  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  try {
    const caller = await verifyCaller(
      request,
      { supabaseUrl: SUPABASE_URL, publishableKey: SUPABASE_ANON_KEY, admin },
      NOTIFICATION_DISPATCH_ROLES,
    );
    const actorId = caller.userId;
    await enforceRateLimit(admin, "realtime_notify", actorId, 60, 60);

    const payload = await readJsonObject(request, 4_096);
    const channel = typeof payload.channel === "string" ? payload.channel.slice(0, 100) : "community";
    const event = typeof payload.event === "string" ? payload.event.slice(0, 50) : "update";
    const data = typeof payload.data === "object" && payload.data !== null ? payload.data : {};

    const { error: rpcError } = await admin.rpc("notify_realtime", {
      p_channel: channel,
      p_event: event,
      p_payload: data,
    });

    if (rpcError) {
      if (typeof payload.recipientId !== "string" || !isUuid(payload.recipientId)) {
        throw new Error("REALTIME_DISPATCH_FAILED");
      }
      const { error: insertError } = await admin.from("notification_events").insert({
        recipient_id: payload.recipientId,
        notification_type: event,
        title: typeof payload.title === "string" ? payload.title.slice(0, 120) : "Community Notification",
        body: typeof payload.body === "string" ? payload.body.slice(0, 600) : "",
        payload: data,
      });
      if (insertError) throw new Error("REALTIME_DISPATCH_FAILED");
    }

    return json(200, { success: true, channel, event });
  } catch (error) {
    const status = publicErrorStatus(error);
    return json(status, {
      error: status === 401
        ? "Authentication is required."
        : status === 403
        ? "This account is not authorised to dispatch notifications."
        : "Notification dispatch failed.",
    });
  }
});
