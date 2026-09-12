import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { GoogleAuth } from "npm:google-auth-library";
import { APP_ROLES, enforceRateLimit, isUuid, readJsonObject, verifyCaller, writeAudit, type EdgeAdminClient } from "../_shared/auth.ts";

const URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-2.5-flash";
const GOOGLE_SERVICE_ACCOUNT_JSON = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "";
const LANG = /^[a-z]{2,3}(-[A-Z]{2})?$/;
const headers = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "Vary": "Authorization" };

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}
function safeLanguage(value: unknown): string | null {
  return typeof value === "string" && LANG.test(value) ? value : null;
}
function object(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : null;
}
function bytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

async function geminiTranslation(post: any, targetLanguage: string) {
  if (!GEMINI_KEY) throw new Error("AI_CONFIGURATION_UNAVAILABLE");
  const prompt = [
    "Translate the following RTC Community publication accurately and neutrally.",
    `Target locale: ${targetLanguage}.`,
    "Return JSON only with keys headline, excerpt, blocks.",
    "For blocks: preserve each id, type, mediaIds, quotedPostId, actionLabel and actionUrl exactly. Translate only human-readable text and actionLabel. Do not add facts, commentary, summaries, or claims.",
    JSON.stringify({ headline: post.headline, excerpt: post.excerpt, blocks: post.content_blocks }),
  ].join("\n");
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(GEMINI_MODEL)}:generateContent?key=${encodeURIComponent(GEMINI_KEY)}`;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig: { responseMimeType: "application/json", temperature: 0, maxOutputTokens: 8192 },
    }),
  });
  if (!response.ok) throw new Error("AI_TRANSLATION_UNAVAILABLE");
  const result = await response.json();
  const text = result?.candidates?.[0]?.content?.parts?.map((part: { text?: string }) => part.text ?? "").join("") ?? "";
  if (new TextEncoder().encode(text).byteLength > 196_608) throw new Error("AI_TRANSLATION_INVALID");
  const parsed = object(JSON.parse(text));
  if (!parsed || typeof parsed.headline !== "string" || parsed.headline.length > 240 || typeof parsed.excerpt !== "string" || parsed.excerpt.length > 900 || !Array.isArray(parsed.blocks) || parsed.blocks.length > 40) {
    throw new Error("AI_TRANSLATION_INVALID");
  }
  const sourceBlocks = Array.isArray(post.content_blocks) ? post.content_blocks : [];
  if (parsed.blocks.length !== sourceBlocks.length) throw new Error("AI_TRANSLATION_INVALID");
  for (let i = 0; i < sourceBlocks.length; i++) {
    const source = object(sourceBlocks[i]);
    const translated = object(parsed.blocks[i]);
    if (!source || !translated || source.id !== translated.id || source.type !== translated.type) throw new Error("AI_TRANSLATION_INVALID");
  }
  return parsed as { headline: string; excerpt: string; blocks: unknown[] };
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

async function googleAccessToken() {
  if (!GOOGLE_SERVICE_ACCOUNT_JSON) throw new Error("TTS_CONFIGURATION_UNAVAILABLE");
  let credentials: Record<string, unknown>;
  try { credentials = JSON.parse(GOOGLE_SERVICE_ACCOUNT_JSON); } catch { throw new Error("TTS_CONFIGURATION_UNAVAILABLE"); }
  const auth = new GoogleAuth({ credentials, scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  if (!token.token) throw new Error("TTS_CONFIGURATION_UNAVAILABLE");
  return token.token;
}

function narrationText(headline: string, excerpt: string, blocks: unknown[]): string {
  const body = blocks.map((raw) => object(raw)?.text).filter((value): value is string => typeof value === "string" && value.trim().length > 0).join("\n\n");
  return [headline, excerpt, body].filter(Boolean).join("\n\n").slice(0, 12_000);
}

async function synthesize(languageCode: string, text: string): Promise<Uint8Array> {
  const bearer = await googleAccessToken();
  const response = await fetch("https://texttospeech.googleapis.com/v1/text:synthesize", {
    method: "POST",
    headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
    body: JSON.stringify({ input: { text }, voice: { languageCode }, audioConfig: { audioEncoding: "MP3", speakingRate: 1.0 } }),
  });
  if (!response.ok) throw new Error("TTS_UNAVAILABLE");
  const payload = await response.json();
  if (typeof payload?.audioContent !== "string" || payload.audioContent.length > 16_000_000) throw new Error("TTS_INVALID_OUTPUT");
  return bytes(payload.audioContent);
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

    const audio = await synthesize(language, narrationText(headline, excerpt, blocks));
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
