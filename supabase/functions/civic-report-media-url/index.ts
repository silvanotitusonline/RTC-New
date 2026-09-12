import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  APP_ROLES,
  AuthError,
  enforceRateLimit,
  isUuid,
  publicErrorStatus,
  readJsonObject,
  verifyCaller,
  writeAudit,
} from "../_shared/auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const BUCKET = "civic-report-evidence";
const URL_TTL_SECONDS = 300;
const headers = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Vary": "Authorization",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { error: "POST is required." });
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !SUPABASE_SERVICE_ROLE_KEY) {
    return json(503, { error: "Server configuration is incomplete." });
  }

  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  let actorId: string | null = null;
  let evidenceId: string | null = null;
  try {
    const caller = await verifyCaller(
      request,
      { supabaseUrl: SUPABASE_URL, publishableKey: SUPABASE_ANON_KEY, admin },
      APP_ROLES,
    );
    actorId = caller.userId;
    await enforceRateLimit(admin, "civic_report_media_url", actorId, 60, 60);
    const payload = await readJsonObject(request, 2_048);
    evidenceId = isUuid(payload.evidenceId) ? payload.evidenceId : null;
    if (!evidenceId) throw new AuthError("INVALID_REQUEST");

    const { data: evidence, error } = await admin
      .from("civic_report_evidence")
      .select("id, report_id, storage_path, state")
      .eq("id", evidenceId)
      .maybeSingle();
    if (error || !evidence || evidence.state !== "FINALIZED") {
      await writeAudit(admin, {
        actorId,
        eventType: "CIVIC_REPORT_MEDIA_URL_DENIED",
        result: "DENIED",
        entityType: "CIVIC_REPORT_EVIDENCE",
        entityId: evidenceId,
        metadata: { reason: "evidence_not_found" },
      });
      return json(404, { error: "Evidence is unavailable." });
    }

    const { data: visible, error: visibleError } = await admin
      .from("civic_reports_public")
      .select("id")
      .eq("id", evidence.report_id)
      .maybeSingle();
    if (visibleError || !visible) {
      await writeAudit(admin, {
        actorId,
        eventType: "CIVIC_REPORT_MEDIA_URL_DENIED",
        result: "DENIED",
        entityType: "CIVIC_REPORT_EVIDENCE",
        entityId: evidenceId,
        metadata: { reason: "report_not_public" },
      });
      return json(404, { error: "Evidence is unavailable." });
    }

    const { data: signed, error: signedError } = await admin.storage
      .from(BUCKET)
      .createSignedUrl(evidence.storage_path, URL_TTL_SECONDS);
    if (signedError || !signed?.signedUrl) throw new Error("SIGNED_URL_UNAVAILABLE");

    await writeAudit(admin, {
      actorId,
      eventType: "CIVIC_REPORT_MEDIA_URL_ISSUED",
      result: "ALLOWED",
      entityType: "CIVIC_REPORT_EVIDENCE",
      entityId: evidenceId,
      metadata: { ttlSeconds: URL_TTL_SECONDS },
    });
    return json(200, {
      url: signed.signedUrl,
      expiresAt: new Date(Date.now() + URL_TTL_SECONDS * 1000).toISOString(),
    });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, {
      actorId,
      eventType: "CIVIC_REPORT_MEDIA_URL_DENIED",
      result: error instanceof AuthError ? "DENIED" : "FAILED",
      entityType: "CIVIC_REPORT_EVIDENCE",
      entityId: evidenceId,
      metadata: { code },
    });
    const status = publicErrorStatus(error);
    return json(status, {
      error:
        status === 401
          ? "A signed-in account is required."
          : status === 403
            ? "This account is not authorised to access Public Report evidence."
            : status === 429
              ? "Too many evidence URL requests. Try again shortly."
              : status === 400
                ? "A valid evidenceId is required."
                : "Evidence could not be prepared.",
    });
  }
});
