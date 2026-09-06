import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  APP_ROLES,
  AuthError,
  enforceRateLimit,
  publicErrorStatus,
  readJsonObject,
  verifyCaller,
  writeAudit,
} from "../_shared/auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const RECENT_REAUTH_WINDOW_MS = 15 * 60 * 1000;
const headers = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "Vary": "Authorization" };

function response(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}

function recentEnough(lastSignInAt: string | null | undefined): boolean {
  if (!lastSignInAt) return false;
  const timestamp = Date.parse(lastSignInAt);
  return Number.isFinite(timestamp) && Date.now() - timestamp <= RECENT_REAUTH_WINDOW_MS;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return response(405, { error: "POST is required." });
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !SUPABASE_SERVICE_ROLE_KEY) {
    return response(503, { error: "Server configuration is incomplete." });
  }

  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  let actorId: string | null = null;
  try {
    const caller = await verifyCaller(req, { supabaseUrl: SUPABASE_URL, publishableKey: SUPABASE_ANON_KEY, admin }, APP_ROLES);
    actorId = caller.userId;
    const input = await readJsonObject(req, 2_048);
    if (input.operation !== "request_account_deletion") throw new AuthError("INVALID_REQUEST");
    await enforceRateLimit(admin, "privacy_request", caller.userId, 3, 3600);

    const { data: userResult, error: userError } = await admin.auth.admin.getUserById(caller.userId);
    if (userError || !userResult.user) throw new Error("USER_LOOKUP_FAILED");
    if (!recentEnough(userResult.user.last_sign_in_at)) {
      await writeAudit(admin, { actorId, eventType: "ACCOUNT_DELETION_REAUTH_REQUIRED", result: "DENIED" });
      return response(412, { error: "Reauthentication is required. Sign out and sign in again, then submit the deletion request within 15 minutes." });
    }

    const { data: request, error: requestError } = await admin
      .from("account_deletion_requests")
      .insert({ requester_id: caller.userId, state: "submitted", reauthenticated_at: new Date().toISOString() })
      .select("id, state, requested_at")
      .single();
    if (requestError || !request) throw new Error("REQUEST_PERSISTENCE_FAILED");
    await writeAudit(admin, { actorId, eventType: "ACCOUNT_DELETION_REQUEST_SUBMITTED", result: "ALLOWED", entityType: "ACCOUNT_DELETION_REQUEST", entityId: request.id });
    return response(202, { requestId: request.id, state: request.state, submittedAt: request.requested_at, message: "Your request has been submitted. Your account and content have not been deleted yet." });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, { actorId, eventType: "ACCOUNT_DELETION_REQUEST_DENIED", result: error instanceof AuthError ? "DENIED" : "FAILED", metadata: { code } });
    const status = publicErrorStatus(error);
    return response(status, { error: status === 401 ? "Sign in before requesting account deletion." : status === 403 ? "This account is not authorised to request deletion." : status === 429 ? "Too many deletion requests. Try again later." : status === 400 ? "The deletion request is invalid." : "The deletion-request service is unavailable. No deletion was performed." });
  }
});
