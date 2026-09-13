import { geminiTranslation, synthesize } from "../daily-post-language/providers.ts";
import type { EdgeAdminClient } from "../_shared/auth.ts";

type Check = { status: "passed" | "blocked"; code?: string; bytes?: number };

// Only invoked after the scheduler's server-secret check. It never publishes a
// post, claims a broadcast job, or sends a notification to a resident.
export async function checkLanguageProviders(admin: EdgeAdminClient) {
  let translation: Check;
  let narration: Check;
  try {
    await geminiTranslation({
      headline: "Community update", excerpt: "The community centre is open today.",
      content_blocks: [],
    }, "af");
    translation = { status: "passed" };
  } catch (error) {
    translation = { status: "blocked", code: safeCode(error) };
  }
  const path = `readiness/${crypto.randomUUID()}/en-ZA.mp3`;
  let uploaded = false;
  try {
    const audio = await synthesize(admin, "en-ZA", "RTC service verification.");
    if (audio.length < 100) throw new Error("TTS_EMPTY_AUDIO");
    const result = await admin.storage.from("daily-post-ai-audio").upload(path, audio, { contentType: "audio/mpeg" });
    if (result.error) throw new Error("TTS_STORAGE_UNAVAILABLE");
    uploaded = true;
    const signed = await admin.storage.from("daily-post-ai-audio").createSignedUrl(path, 60);
    if (signed.error || !signed.data?.signedUrl) throw new Error("TTS_SIGNING_UNAVAILABLE");
    const response = await fetch(signed.data.signedUrl);
    if (!response.ok || (await response.arrayBuffer()).byteLength !== audio.length) throw new Error("TTS_PLAYBACK_FETCH_FAILED");
    narration = { status: "passed", bytes: audio.length };
  } catch (error) {
    narration = { status: "blocked", code: safeCode(error) };
  } finally {
    if (uploaded) {
      const cleanup = await admin.storage.from("daily-post-ai-audio").remove([path]);
      if (cleanup.error) narration = { status: "blocked", code: "TTS_PROBE_CLEANUP_FAILED" };
    }
  }
  return { translation, narration };
}

function safeCode(error: unknown): string {
  const message = error instanceof Error ? error.message : "PROVIDER_UNAVAILABLE";
  return /^[A-Z0-9_]{3,80}$/.test(message) ? message : "PROVIDER_UNAVAILABLE";
}
