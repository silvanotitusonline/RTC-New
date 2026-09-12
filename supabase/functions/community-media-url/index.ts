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
const BUCKET = "rtc-community-media";
const URL_TTL_SECONDS = 3600;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const headers = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Vary": "Authorization",
  ...corsHeaders,
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}

async function viewerMaySeeAuthor(
  admin: ReturnType<typeof createClient>,
  viewer: string,
  author: string,
): Promise<boolean> {
  const { data, error } = await admin
    .from("community_blocks")
    .select("blocker_id")
    .or(`and(blocker_id.eq.${viewer},blocked_id.eq.${author}),and(blocker_id.eq.${author},blocked_id.eq.${viewer})`)
    .limit(1);
  if (error || (data?.length ?? 0) > 0) return false;
  const muted = await admin
    .from("community_mutes")
    .select("muter_id")
    .eq("muter_id", viewer)
    .eq("muted_id", author)
    .limit(1);
  return !muted.error && (muted.data?.length ?? 0) === 0;
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
  let actorId: string | null = null;
  let mediaId: string | null = null;
  try {
    const caller = await verifyCaller(request, { supabaseUrl: SUPABASE_URL, publishableKey: SUPABASE_ANON_KEY, admin }, APP_ROLES);
    actorId = caller.userId;
    await enforceRateLimit(admin, "community_media_url", actorId, 60, 60);
    const payload = await readJsonObject(request, 2_048);
    mediaId = isUuid(payload.mediaId) ? payload.mediaId : null;
    if (!mediaId) throw new AuthError("INVALID_REQUEST");

    const { data: media, error } = await admin
      .from("community_post_media")
      .select("storage_path,post_id,community_posts!inner(author_id,state,deleted_at)")
      .eq("id", mediaId)
      .maybeSingle();
    if (error || !media) {
      await writeAudit(admin, { actorId, eventType: "COMMUNITY_MEDIA_URL_DENIED", result: "DENIED", entityType: "COMMUNITY_MEDIA", entityId: mediaId, metadata: { reason: "media_not_found" } });
      return json(404, { error: "Media is unavailable." });
    }

    const post = Array.isArray(media.community_posts) ? media.community_posts[0] : media.community_posts;
    if (!post || !["PUBLISHED", "LOCKED"].includes(post.state) || post.deleted_at || !(await viewerMaySeeAuthor(admin, actorId, post.author_id))) {
      await writeAudit(admin, { actorId, eventType: "COMMUNITY_MEDIA_URL_DENIED", result: "DENIED", entityType: "COMMUNITY_MEDIA", entityId: mediaId, metadata: { reason: "visibility" } });
      return json(404, { error: "Media is unavailable." });
    }

    const { data: signed, error: signedError } = await admin.storage.from(BUCKET).createSignedUrl(media.storage_path, URL_TTL_SECONDS);
    if (signedError || !signed?.signedUrl) throw new Error("SIGNED_URL_UNAVAILABLE");
    await writeAudit(admin, { actorId, eventType: "COMMUNITY_MEDIA_URL_ISSUED", result: "ALLOWED", entityType: "COMMUNITY_MEDIA", entityId: mediaId, metadata: { ttlSeconds: URL_TTL_SECONDS } });
    return json(200, { url: signed.signedUrl, expiresAt: new Date(Date.now() + URL_TTL_SECONDS * 1000).toISOString() });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, { actorId, eventType: "COMMUNITY_MEDIA_URL_DENIED", result: error instanceof AuthError ? "DENIED" : "FAILED", entityType: "COMMUNITY_MEDIA", entityId: mediaId, metadata: { code } });
    const status = publicErrorStatus(error);
    return json(status, { error: status === 401 ? "A signed-in Community account is required." : status === 403 ? "This account is not authorised to access Community media." : status === 429 ? "Too many media URL requests. Try again shortly." : status === 400 ? "A valid mediaId is required." : "Media could not be prepared." });
  }
});
