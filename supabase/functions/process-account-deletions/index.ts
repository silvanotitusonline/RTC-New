import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { writeAudit } from "../_shared/auth.ts"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL') || ""
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') || ""
const SCHEDULER_SECRET = Deno.env.get('ACCOUNT_DELETION_SCHEDULER_SECRET') || ""
const MAX_BATCH = 25
const STORAGE_LIST_LIMIT = 100

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false },
})

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  })
}

function timingSafeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder()
  const aBytes = enc.encode(a)
  const bBytes = enc.encode(b)
  if (aBytes.length !== bBytes.length) return false
  let diff = 0
  for (let i = 0; i < aBytes.length; i++) diff |= aBytes[i] ^ bBytes[i]
  return diff === 0
}

type DeletionRequest = { id: string; requester_id: string }

async function removeUserStorage(userId: string): Promise<void> {
  // All user-owned object policies use the user id as the first path segment.
  // Listing is deliberately bounded; the request remains failed if a bucket
  // cannot be fully enumerated, so the account is never deleted prematurely.
  for (const bucket of ['rtc-profile-media', 'rtc-community-media', 'rtc-feedback-media']) {
    const paths: string[] = []
    const pending = [userId]
    while (pending.length > 0) {
      const prefix = pending.pop()!
      const { data: objects, error: listError } = await supabase.storage
        .from(bucket)
        .list(prefix, { limit: STORAGE_LIST_LIMIT, offset: 0 })
      if (listError) throw new Error(`storage list failed for ${bucket}`)
      if (!objects || objects.length >= STORAGE_LIST_LIMIT) {
        throw new Error(`storage cleanup requires manual review for ${bucket}`)
      }
      for (const object of objects) {
        if (!object.name) continue
        const path = `${prefix}/${object.name}`
        if (object.id === null || object.metadata === null) pending.push(path)
        else paths.push(path)
      }
    }
    if (paths.length > 0) {
      const { error: removeError } = await supabase.storage.from(bucket).remove(paths)
      if (removeError) throw new Error(`storage remove failed for ${bucket}`)
    }
  }
}

async function markFailed(requestId: string): Promise<void> {
  await supabase
    .from('account_deletion_requests')
    .update({ state: 'failed', audit_note: 'Automated deletion sweep failed; retry or review required.' })
    .eq('id', requestId)
    .in('state', ['processing', 'submitted'])
}

serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed." })
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY || !SCHEDULER_SECRET) {
    return json(503, { error: "Account deletion processing is not configured." })
  }
  const suppliedSecret = req.headers.get('x-rtc-scheduler-secret') ?? ""
  if (!suppliedSecret || !timingSafeEqual(suppliedSecret, SCHEDULER_SECRET)) {
    await writeAudit(supabase, { actorId: null, eventType: "ACCOUNT_DELETION_SWEEP_DENIED", result: "DENIED", metadata: { reason: "scheduler_secret" } })
    return json(401, { error: "Unauthorized account-deletion request." })
  }

  try {
    const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString()
    const { data: candidates, error: fetchError } = await supabase
      .from('account_deletion_requests')
      .select('id, requester_id')
      .eq('state', 'submitted')
      .lt('requested_at', cutoff)
      .order('requested_at', { ascending: true })
      .limit(MAX_BATCH)

    if (fetchError) throw new Error('candidate fetch failed')
    if (!candidates || candidates.length === 0) {
      await writeAudit(supabase, { actorId: null, eventType: "ACCOUNT_DELETION_SWEEP_IDLE", result: "ALLOWED", metadata: { processed: 0 } })
      return json(200, { message: "No pending deletions.", processed: 0 })
    }

    const results: { requestId: string; status: 'deleted' | 'failed' | 'skipped' }[] = []
    for (const request of candidates as DeletionRequest[]) {
      // Conditional update is the claim step: concurrent sweep invocations cannot
      // process the same request unless the first invocation has already failed.
      const { data: claimed, error: claimError } = await supabase
        .from('account_deletion_requests')
        .update({ state: 'processing', audit_note: 'Claimed by automated deletion sweep.' })
        .eq('id', request.id)
        .eq('state', 'submitted')
        .select('id, requester_id')
        .maybeSingle()
      if (claimError) throw new Error('deletion claim failed')
      if (!claimed) {
        results.push({ requestId: request.id, status: 'skipped' })
        continue
      }

      try {
        await removeUserStorage(request.requester_id)
        const { error: authDeleteError } = await supabase.auth.admin.deleteUser(request.requester_id)
        if (authDeleteError) throw new Error('auth deletion failed')
        // The request row is configured to cascade with auth.users deletion.
        results.push({ requestId: request.id, status: 'deleted' })
      } catch (error) {
        console.error('account-deletion sweep: request failed', request.id, error instanceof Error ? error.message : error)
        await markFailed(request.id)
        results.push({ requestId: request.id, status: 'failed' })
      }
    }

    const deletedCount = results.filter((r) => r.status === 'deleted').length
    const failedCount = results.filter((r) => r.status === 'failed').length
    await writeAudit(supabase, {
      actorId: null,
      eventType: "ACCOUNT_DELETION_SWEEP_COMPLETED",
      result: failedCount === 0 ? "ALLOWED" : "PARTIAL",
      metadata: { processed: results.length, deleted: deletedCount, failed: failedCount, skipped: results.length - deletedCount - failedCount },
    })
    return json(failedCount === 0 ? 200 : 207, { processed: results })
  } catch (error) {
    console.error("account-deletion sweep: unhandled error", error instanceof Error ? error.message : error)
    await writeAudit(supabase, { actorId: null, eventType: "ACCOUNT_DELETION_SWEEP_FAILED", result: "FAILED", metadata: { stage: "unhandled" } })
    return json(500, { error: "Account deletion sweep could not be completed." })
  }
})
