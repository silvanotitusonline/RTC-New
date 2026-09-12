import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { GoogleAuth } from "npm:google-auth-library";
import {
  SecurityError,
  boundedText,
  isUuid,
  readJsonObject,
  verifyCaller,
  writeAudit,
} from "../_shared/auth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-2.5-flash";
const GOOGLE_SERVICE_ACCOUNT_JSON = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "";
const GOOGLE_CLOUD_PROJECT_ID = Deno.env.get("GOOGLE_CLOUD_PROJECT_ID") ?? "gen-lang-client-0507599237";
const RECAPTCHA_SITE_KEY = Deno.env.get("RECAPTCHA_SITE_KEY") ?? "6LeC44UtAAAAAAgm0aKcyO8b3xoW3SpPFLY2IXe7";
const ANDROID_PACKAGE = "za.org.rtc.community";
const MIN_RECAPTCHA_SCORE = 0.5;
const ACTION_COMMAND = "rtc_admin_ai_command";
const ACTION_CONFIRM = "rtc_admin_ai_confirm";
const MAX_COMMANDS_PER_MINUTE = 10;
const EXPIRY_MINUTES = 15;
const MAX_REQUEST_BYTES = 49_152;

const ENTITY_TYPES = [
  "home_content", "explore_content", "notices", "community_notices", "community_moderation",
  "events", "projects", "centres", "opportunities", "resources", "faqs", "contacts", "dashboard_metrics",
] as const;
type EntityType = typeof ENTITY_TYPES[number];
type AiRole = "CONTENT_EDITOR" | "MODERATOR" | "SYSTEM_ADMIN";

const headers = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Vary": "Authorization",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isEntityType(value: unknown): value is EntityType {
  return typeof value === "string" && (ENTITY_TYPES as readonly string[]).includes(value);
}

function allowedScopeForRole(role: AiRole): EntityType[] {
  if (role === "CONTENT_EDITOR") return ["notices", "community_notices", "events", "projects", "centres", "opportunities", "resources", "faqs"];
  if (role === "MODERATOR") return ["community_moderation"];
  return [...ENTITY_TYPES];
}

async function recaptchaAccessToken(): Promise<string> {
  if (!GOOGLE_SERVICE_ACCOUNT_JSON) throw new Error("RECAPTCHA_CREDENTIALS_UNAVAILABLE");
  let credentials: Record<string, unknown>;
  try {
    credentials = JSON.parse(GOOGLE_SERVICE_ACCOUNT_JSON) as Record<string, unknown>;
  } catch {
    throw new Error("RECAPTCHA_CREDENTIALS_UNAVAILABLE");
  }
  const auth = new GoogleAuth({ credentials, scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  if (!token.token) throw new Error("RECAPTCHA_CREDENTIALS_UNAVAILABLE");
  return token.token;
}

async function verifyRecaptcha(token: string, expectedAction: string): Promise<void> {
  if (token.length < 1 || token.length > 16_384) throw new Error("RECAPTCHA_REJECTED");
  const accessToken = await recaptchaAccessToken();
  const response = await fetch(
    `https://recaptchaenterprise.googleapis.com/v1/projects/${GOOGLE_CLOUD_PROJECT_ID}/assessments`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ event: { token, siteKey: RECAPTCHA_SITE_KEY, expectedAction } }),
    },
  );
  if (!response.ok) throw new Error("RECAPTCHA_UNAVAILABLE");
  const assessment = await response.json();
  const properties = assessment?.tokenProperties;
  const score = assessment?.riskAnalysis?.score;
  if (
    properties?.valid !== true ||
    properties?.action !== expectedAction ||
    properties?.androidPackageName !== ANDROID_PACKAGE ||
    typeof score !== "number" || score < MIN_RECAPTCHA_SCORE
  ) throw new Error("RECAPTCHA_REJECTED");
}

function modelPrompt(command: string, scope: EntityType[], context: unknown[]) {
  return [
    "You are RTC Community's guarded content-management proposal generator.",
    "Return JSON only. Never execute a change. Never recommend deletion, publishing, archiving, restoration, role assignment, configuration change, or user-data mutation.",
    "The JSON schema is: {kind:'PROPOSAL'|'READ_ONLY',summary:string,changes:[{entityType:string,entityId:string|null,operation:'CREATE_DRAFT'|'PATCH_DRAFT'|'CREATE_CORRECTION',changes:object,reason:string}]}",
    `Allowed entity types: ${scope.join(", ")}. Allowed operations: CREATE_DRAFT, PATCH_DRAFT, CREATE_CORRECTION.`,
    "For PATCH_DRAFT and CREATE_CORRECTION, entityId must be one of the verified context IDs. If a request cannot safely produce a draft, use kind READ_ONLY and an empty changes array.",
    `Administrator request: ${command}`,
    `Verified public context: ${JSON.stringify(context)}`,
  ].join("\n");
}

async function generateProposal(command: string, scope: EntityType[], context: unknown[]) {
  if (!GEMINI_API_KEY) throw new Error("AI_CONFIGURATION_UNAVAILABLE");
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(GEMINI_MODEL)}:generateContent?key=${encodeURIComponent(GEMINI_API_KEY)}`;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: modelPrompt(command, scope, context) }] }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.1, maxOutputTokens: 4096 },
    }),
  });
  if (!response.ok) throw new Error("AI_UNAVAILABLE");
  const result = await response.json();
  const text = result?.candidates?.[0]?.content?.parts?.map((part: { text?: string }) => part.text ?? "").join("") ?? "";
  if (new TextEncoder().encode(text).byteLength > 65_536) throw new Error("AI_INVALID_OUTPUT");
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new Error("AI_INVALID_OUTPUT");
  }
}

function validateProposal(raw: unknown, scope: EntityType[], contextRows: Array<{ id: string; entity_type: string; updated_at: string }>) {
  if (!isObject(raw) || (raw.kind !== "PROPOSAL" && raw.kind !== "READ_ONLY")) throw new Error("AI_INVALID_OUTPUT");
  const summary = boundedText(raw.summary, 3000, 0) ?? "";
  const rawChanges = Array.isArray(raw.changes) ? raw.changes : [];
  if (rawChanges.length > 20) throw new Error("AI_INVALID_OUTPUT");
  const known = new Map(contextRows.map((row) => [row.id, row]));
  const changes = rawChanges.map((change) => {
    if (!isObject(change) || !isEntityType(change.entityType) || !scope.includes(change.entityType)) throw new Error("AI_INVALID_OUTPUT");
    const operation = change.operation;
    if (operation !== "CREATE_DRAFT" && operation !== "PATCH_DRAFT" && operation !== "CREATE_CORRECTION") throw new Error("AI_INVALID_OUTPUT");
    const entityId = change.entityId === null ? null : (isUuid(change.entityId) ? change.entityId : null);
    if ((operation === "PATCH_DRAFT" || operation === "CREATE_CORRECTION") && (!entityId || !known.has(entityId))) throw new Error("AI_INVALID_OUTPUT");
    if (!isObject(change.changes) || new TextEncoder().encode(JSON.stringify(change.changes)).byteLength > 16_384) throw new Error("AI_INVALID_OUTPUT");
    const reason = boundedText(change.reason, 2000);
    if (!reason) throw new Error("AI_INVALID_OUTPUT");
    return { entityType: change.entityType, entityId, operation, changes: change.changes, reason };
  });
  if (raw.kind === "READ_ONLY" && changes.length !== 0) throw new Error("AI_INVALID_OUTPUT");
  const targetVersions = changes
    .filter((change) => change.entityId !== null)
    .map((change) => ({ entityId: change.entityId, updatedAt: known.get(change.entityId!)?.updated_at ?? null }));
  return { kind: raw.kind, summary, changes, targetVersions };
}

async function handleCommand(admin: ReturnType<typeof createClient>, actorId: string, actorRole: AiRole, input: Record<string, unknown>) {
  const command = boundedText(input.command, 12_000);
  const recaptchaToken = boundedText(input.recaptchaToken, 16_384);
  if (!command || !recaptchaToken) return json(400, { error: "A command and fresh human-verification token are required." });

  const roleScope = allowedScopeForRole(actorRole);
  const rawScope = Array.isArray(input.scope) ? input.scope : roleScope;
  if (rawScope.length > ENTITY_TYPES.length) return json(400, { error: "The requested RTC AI scope is invalid." });
  const requestedScope = rawScope.filter(isEntityType);
  const scope = Array.from(new Set(requestedScope.filter((value) => roleScope.includes(value))));
  if (scope.length === 0) return json(403, { error: "This request includes content outside your RTC AI permission boundary." });

  try {
    await verifyRecaptcha(recaptchaToken, ACTION_COMMAND);
    const { data: allowed, error: limitError } = await admin.rpc("claim_ai_rate_limit", {
      p_actor_id: actorId,
      p_max_requests: MAX_COMMANDS_PER_MINUTE,
    });
    if (limitError) throw new Error("RATE_LIMIT_UNAVAILABLE");
    if (allowed !== true) return json(429, { error: "RTC Admin AI rate limit reached. Try again shortly." });

    const { data: contextRows, error: contextError } = await admin
      .from("app_content")
      .select("id,entity_type,slug,title,summary,data,updated_at")
      .in("entity_type", scope)
      .eq("state", "PUBLISHED")
      .limit(20);
    if (contextError) throw new Error("CONTEXT_UNAVAILABLE");

    const proposal = validateProposal(await generateProposal(command, scope, contextRows ?? []), scope, contextRows ?? []);
    const expiresAt = new Date(Date.now() + EXPIRY_MINUTES * 60_000).toISOString();
    const state = proposal.kind === "PROPOSAL" ? "PENDING_CONFIRMATION" : "COMPLETED_READ_ONLY";
    const { data: action, error: insertError } = await admin
      .from("ai_actions")
      .insert({
        actor_id: actorId,
        command,
        scope,
        proposal: { kind: proposal.kind, summary: proposal.summary, changes: proposal.changes },
        target_versions: proposal.targetVersions,
        state,
        expires_at: expiresAt,
      })
      .select("id")
      .single();
    if (insertError || !action) throw new Error("PERSISTENCE_UNAVAILABLE");

    await writeAudit(admin, {
      actorId,
      eventType: proposal.kind === "PROPOSAL" ? "AI_PROPOSAL_CREATED" : "AI_READ_COMPLETED",
      result: "ALLOWED",
      entityType: "AI_ACTION",
      entityId: action.id,
      metadata: { entityCount: proposal.changes.length },
    });
    return json(200, {
      proposalId: action.id,
      proposal: { kind: proposal.kind, summary: proposal.summary, changes: proposal.changes },
      expiresAt,
    });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, { actorId, eventType: "AI_COMMAND_FAILED", result: "FAILED", metadata: { code } });
    const status = code === "RECAPTCHA_REJECTED" ? 403 : 503;
    return json(status, {
      error: code === "RECAPTCHA_REJECTED"
        ? "Human verification was not accepted."
        : "RTC Admin AI could not prepare a proposal. No content was changed.",
    });
  }
}

async function handleConfirm(admin: ReturnType<typeof createClient>, actorId: string, input: Record<string, unknown>) {
  const proposalId = isUuid(input.proposalId) ? input.proposalId : null;
  const recaptchaToken = boundedText(input.recaptchaToken, 16_384);
  if (!proposalId || !recaptchaToken) return json(400, { error: "A proposal ID and fresh human-verification token are required." });

  try {
    await verifyRecaptcha(recaptchaToken, ACTION_CONFIRM);
    const { data, error } = await admin.rpc("confirm_ai_proposal", { p_action_id: proposalId, p_actor_id: actorId });
    if (error || !isObject(data)) throw new Error("CONFIRMATION_UNAVAILABLE");
    if (data.ok !== true) {
      const code = typeof data.code === "string" ? data.code : "denied";
      await writeAudit(admin, { actorId, eventType: "AI_CONFIRMATION_DENIED", result: "DENIED", entityType: "AI_ACTION", entityId: proposalId, metadata: { code } });
      return json(code === "not_found" ? 404 : code === "permission_denied" ? 403 : 412, {
        error: code === "not_found"
          ? "The proposal is no longer available."
          : code === "permission_denied"
          ? "This account cannot confirm that proposal."
          : "The proposal can no longer be confirmed.",
      });
    }

    await writeAudit(admin, { actorId, eventType: "AI_PROPOSAL_CONFIRMED", result: "ALLOWED", entityType: "AI_ACTION", entityId: proposalId, metadata: { draftsCreated: data.draftsCreated ?? 0 } });
    return json(200, { draftsCreated: data.draftsCreated ?? 0 });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, { actorId, eventType: "AI_CONFIRMATION_FAILED", result: "FAILED", entityType: "AI_ACTION", entityId: proposalId, metadata: { code } });
    const status = code === "RECAPTCHA_REJECTED" ? 403 : 503;
    return json(status, {
      error: code === "RECAPTCHA_REJECTED"
        ? "Human verification was not accepted."
        : "The proposal could not be confirmed. No content was changed.",
    });
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "POST is required." });
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !SUPABASE_SERVICE_ROLE_KEY) {
    return json(503, { error: "Server configuration is incomplete." });
  }

  const admin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  let actorId: string | null = null;
  try {
    const input = await readJsonObject(req, MAX_REQUEST_BYTES);
    const verified = await verifyCaller(
      req,
      { supabaseUrl: SUPABASE_URL, publishableKey: SUPABASE_ANON_KEY, admin },
      ["CONTENT_EDITOR", "MODERATOR", "SYSTEM_ADMIN"],
    );
    actorId = verified.userId;
    const actorRole = verified.role as AiRole;

    if (input.operation === "command") return await handleCommand(admin, actorId, actorRole, input);
    if (input.operation === "confirm") return await handleConfirm(admin, actorId, input);
    return json(400, { error: "operation must be command or confirm." });
  } catch (error) {
    const code = error instanceof Error ? error.message : "UNKNOWN";
    await writeAudit(admin, {
      actorId,
      eventType: "AI_REQUEST_DENIED",
      result: error instanceof SecurityError ? "DENIED" : "FAILED",
      metadata: { code },
    });
    return json(
      code === "AUTH_REQUIRED" ? 401 : code === "ROLE_REQUIRED" ? 403 : code === "INVALID_REQUEST" ? 400 : 500,
      {
        error: code === "AUTH_REQUIRED"
          ? "Sign in before using RTC AI."
          : code === "ROLE_REQUIRED"
          ? "This account is not authorised to use RTC AI."
          : code === "INVALID_REQUEST"
          ? "The RTC AI request is invalid."
          : "The protected service is unavailable.",
      },
    );
  }
});
