import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { writeAudit } from "../_shared/auth.ts"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL') || "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') || "";
// Must be set as an Edge Function secret before this function is safe to invoke:
//   supabase secrets set ACCOUNT_DELETION_SCHEDULER_SECRET=<random-value> --project-ref <ref>
// There is deliberately no default: an unset secret fails closed (503) rather than
// leaving this destructive, service-role-authenticated endpoint callable by anyone.
const SCHEDULER_SECRET = Deno.env.get('ACCOUNT_DELETION_SCHEDULER_SECRET') || "";

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: {
    autoRefreshToken: false,
    persistSession: false
  }
});

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}

// Constant-time comparison so the scheduler secret cannot be recovered via a timing side
// channel on this public HTTPS endpoint.
function timingSafeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const aBytes = enc.encode(a);
  const bBytes = enc.encode(b);
  if (aBytes.length !== bBytes.length) return false;
  let diff = 0;
  for (let i = 0; i < aBytes.length; i++) diff |= aBytes[i] ^ bBytes[i];
  return diff === 0;
}

serve(async (req) => {
  // Fail closed: only a POST carrying the correct scheduler secret may trigger a batch of
  // account deletions. Anything else is rejected before the service-role client is used
  // for anything, and before any account/deletion data is touched.
  if (req.method !== "POST") {
    return json(405, { error: "Method not allowed." });
  }
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY || !SCHEDULER_SECRET) {
    return json(503, { error: "Account deletion processing is not configured." });
  }
  const suppliedSecret = req.headers.get('x-rtc-scheduler-secret') ?? "";
  if (!suppliedSecret || !timingSafeEqual(suppliedSecret, SCHEDULER_SECRET)) {
    await writeAudit(supabase, {
      actorId: null,
      eventType: "ACCOUNT_DELETION_SWEEP_DENIED",
      result: "DENIED",
      metadata: { reason: "scheduler_secret" },
    });
    return json(401, { error: "Unauthorized account-deletion request." });
  }

  try {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const { data: pendingDeletions, error: fetchError } = await supabase
      .from('account_deletion_requests')
      .select('user_id')
      .lt('requested_at', thirtyDaysAgo.toISOString());

    if (fetchError) {
      console.error("account-deletion sweep: fetch failed", fetchError.message);
      await writeAudit(supabase, { actorId: null, eventType: "ACCOUNT_DELETION_SWEEP_FAILED", result: "FAILED", metadata: { stage: "fetch" } });
      return json(500, { error: "Account deletion sweep could not be completed." });
    }

    if (!pendingDeletions || pendingDeletions.length === 0) {
      await writeAudit(supabase, { actorId: null, eventType: "ACCOUNT_DELETION_SWEEP_IDLE", result: "ALLOWED", metadata: { processed: 0 } });
      return json(200, { message: "No pending deletions.", processed: 0 });
    }

    const results: { userId: string; status: 'deleted' | 'failed' }[] = [];
    for (const request of pendingDeletions) {
      const userId = request.user_id;

      const { error: storageError } = await supabase.storage.from('community').remove([`posts/${userId}`]);
      if (storageError) console.error("account-deletion sweep: storage cleanup failed", userId, storageError.message);

      const { error: dbError } = await supabase.from('profiles').delete().eq('id', userId);

      if (dbError) {
        console.error("account-deletion sweep: profile delete failed", userId, dbError.message);
        results.push({ userId, status: 'failed' });
      } else {
        results.push({ userId, status: 'deleted' });
      }
    }

    const deletedCount = results.filter((r) => r.status === 'deleted').length;
    const failedCount = results.length - deletedCount;
    await writeAudit(supabase, {
      actorId: null,
      eventType: "ACCOUNT_DELETION_SWEEP_COMPLETED",
      result: failedCount === 0 ? "ALLOWED" : "PARTIAL",
      metadata: { processed: results.length, deleted: deletedCount, failed: failedCount },
    });

    // No dbError/storageError text is echoed to the caller; only user ids and a status enum.
    return json(200, { processed: results });
  } catch (err) {
    console.error("account-deletion sweep: unhandled error", err instanceof Error ? err.message : err);
    await writeAudit(supabase, { actorId: null, eventType: "ACCOUNT_DELETION_SWEEP_FAILED", result: "FAILED", metadata: { stage: "unhandled" } });
    return json(500, { error: "Account deletion sweep could not be completed." });
  }
});
