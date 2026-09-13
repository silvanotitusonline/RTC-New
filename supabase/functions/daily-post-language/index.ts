import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { geminiTranslation, synthesize, GEMINI_MODEL } from "./providers.ts";
import { APP_ROLES, enforceRateLimit, isUuid, readJsonObject, verifyCaller, writeAudit, type EdgeAdminClient } from "../_shared/auth.ts";

const URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const LANG = /^[a-z]{2,3}(-[A-Z]{2})?$/;
const headers = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "Vary": "Authorization" };

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}
function safeLanguage(value: unknown): string | null {
  return typeof value === "string" && LANG.test(value) ? value : null;
}
async function ensureTranslation(admin: EdgeAdminClient, post: any, targetLanguage: string) {
  const cached = await admin.from("daily_post_translations")
    .select("post_id,revision,target_language,translated_headline,translated_excerpt,translated_blocks,audio_storage_path")
    .eq("post_id", post.id).eq("revision", post.revision).eq("target_language", targetLanguage).maybeSingle();
  if (cached.error) throw new Error("TRANSLATION_CACHE_UNAVAILABLE");
  if (cached.data) return cached.data;
  const translated = await geminiTranslation(post, targetLanguage);
  const inserted = await admin.from("daily_post_translations").insert({
    post_id: post.id,
    revision: post.revision,
    target_language: targetLanguage,
    translated_headline: translated.headline,
    translated_excerpt: translated.excerpt,
    translated_blocks: translated.blocks,
    provider: `gemini:${GEMINI_MODEL}`,
  }).select("post_id,revision,target_language,translated_headline,translated_excerpt,translated_blocks,audio_storage_path").single();
  if (inserted.error || !inserted.data) throw new Error("TRANSLATION_CACHE_UNAVAILABLE");
  return inserted.data;
}

function object(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function narrationText(headline: string, excerpt: string, blocks: unknown[]): string {
  const body = blocks.map((raw) => object(raw)?.text).filter((value): value is string => typeof value === "string" && value.trim().length > 0).join("\n\n");
  return [headline, excerpt, body].filter(Boolean).join("\n\n").slice(0, 12_000);
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "POST is required." });
  if (!URL || !ANON || !SERVICE) return json(503, { error: "Server configuration is incomplete." });
  const admin = createClient(URL, SERVICE, { auth: { persistSession: false, autoRefreshToken: false } }) as unknown as EdgeAdminClient;
  let actorId: string | null = null;
  try {
    const caller = await verifyCaller(req, { supabaseUrl: URL, publishableKey: ANON, admin }, APP_ROLES);
    actorId = caller.userId;
    await enforceRateLimit(admin, "daily_post_language", actorId, 20, 60);
    const input = await readJsonObject(req, 8_192);
    const action = input.action === "translate" || input.action === "narrate" ? input.action : null;
    const postId = isUuid(input.postId) ? input.postId : null;
    const targetLanguage = safeLanguage(input.targetLanguage);
    if (!action || !postId || (action === "translate" && !targetLanguage)) return json(400, { error: "Invalid language request." });

    const postResult = await admin.from("daily_posts").select("id,state,revision,canonical_language,headline,excerpt,content_blocks")
      .eq("id", postId).eq("state", "PUBLISHED").maybeSingle();
    if (postResult.error || !postResult.data) return json(404, { error: "Publication not found." });
    const post = postResult.data;

    if (action === "translate") {
      if (targetLanguage === post.canonical_language) {
        return json(200, { postId, revision: post.revision, targetLanguage, headline: post.headline, excerpt: post.excerpt, blocks: post.content_blocks, audioUrl: null });
      }
      const translated = await ensureTranslation(admin, post, targetLanguage!);
      await writeAudit(admin, { actorId, eventType: "DAILY_POST_TRANSLATED", result: "ALLOWED", entityType: "DAILY_POST", entityId: postId, metadata: { targetLanguage } });
      return json(200, { postId, revision: post.revision, targetLanguage, headline: translated.translated_headline, excerpt: translated.translated_excerpt, blocks: translated.translated_blocks, audioUrl: null });
    }

    const language = targetLanguage ?? post.canonical_language;
    let headline = post.headline as string;
    let excerpt = post.excerpt as string;
    let blocks = post.content_blocks as unknown[];
    let translation: any = null;
    if (language !== post.canonical_language) {
      translation = await ensureTranslation(admin, post, language);
      headline = translation.translated_headline;
      excerpt = translation.translated_excerpt;
      blocks = translation.translated_blocks;
    }
    const path = `${post.id}/${post.revision}/${language}.mp3`;
    const existing = await admin.storage.from("daily-post-ai-audio").createSignedUrl(path, 3600);
    if (!existing.error && existing.data?.signedUrl) return json(200, { audioUrl: existing.data.signedUrl });

    const audio = await synthesize(admin, language, narrationText(headline, excerpt, blocks));
    const upload = await admin.storage.from("daily-post-ai-audio").upload(path, audio, { contentType: "audio/mpeg", upsert: true });
    if (upload.error) throw new Error("TTS_STORAGE_UNAVAILABLE");
    if (translation) await admin.from("daily_post_translations").update({ audio_storage_path: path })
      .eq("post_id", post.id).eq("revision", post.revision).eq("target_language", language);
    const signed = await admin.storage.from("daily-post-ai-audio").createSignedUrl(path, 3600);
    if (signed.error || !signed.data?.signedUrl) throw new Error("TTS_STORAGE_UNAVAILABLE");
    await writeAudit(admin, { actorId, eventType: "DAILY_POST_NARRATED", result: "ALLOWED", entityType: "DAILY_POST", entityId: postId, metadata: { language } });
    return json(200, { audioUrl: signed.data.signedUrl });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, { actorId, eventType: "DAILY_POST_LANGUAGE_FAILED", result: code.includes("RATE") ? "DENIED" : "FAILED", metadata: { code } });
    return json(code === "AUTH_REQUIRED" ? 401 : code === "ROLE_REQUIRED" ? 403 : code === "RATE_LIMITED" ? 429 : 503, { error: "Translation or narration is currently unavailable." });
  }
});
