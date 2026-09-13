import { GoogleAuth } from "npm:google-auth-library";
import type { EdgeAdminClient } from "../_shared/auth.ts";
const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
export const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-2.5-flash";
const GOOGLE_SERVICE_ACCOUNT_JSON = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "";

function object(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : null;
}
function bytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

export async function geminiTranslation(post: any, targetLanguage: string) {
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

async function googleAccessToken(admin: EdgeAdminClient) {
  let configured = GOOGLE_SERVICE_ACCOUNT_JSON;
  if (!configured) {
    const stored = await admin.rpc("get_firebase_fcm_service_account");
    if (!stored.error && typeof stored.data === "string") configured = stored.data;
  }
  if (!configured) throw new Error("TTS_CONFIGURATION_UNAVAILABLE");
  let credentials: Record<string, unknown>;
  try { credentials = JSON.parse(configured); } catch { throw new Error("TTS_CONFIGURATION_UNAVAILABLE"); }
  const auth = new GoogleAuth({ credentials, scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  if (!token.token) throw new Error("TTS_CONFIGURATION_UNAVAILABLE");
  return token.token;
}

export async function synthesize(admin: EdgeAdminClient, languageCode: string, text: string): Promise<Uint8Array> {
  const bearer = await googleAccessToken(admin);
  const response = await fetch("https://texttospeech.googleapis.com/v1/text:synthesize", {
    method: "POST",
    headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
    body: JSON.stringify({ input: { text }, voice: { languageCode }, audioConfig: { audioEncoding: "MP3", speakingRate: 1.0 } }),
  });
  if (!response.ok) {
    const failure = await response.json().catch(() => ({}));
    const reason = failure?.error?.details?.find((entry: any) => typeof entry?.reason === "string")?.reason;
    const safeReason = typeof reason === "string" && /^[A-Z_]{1,64}$/.test(reason) ? reason : "UNAVAILABLE";
    throw new Error(`TTS_HTTP_${response.status}_${safeReason}`);
  }
  const payload = await response.json();
  if (typeof payload?.audioContent !== "string" || payload.audioContent.length > 16_000_000) throw new Error("TTS_INVALID_OUTPUT");
  return bytes(payload.audioContent);
}
