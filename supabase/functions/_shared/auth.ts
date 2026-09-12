import { createClient } from "npm:@supabase/supabase-js@2";

export const APP_ROLES = [
  "RESIDENT",
  "CASE_STAFF",
  "CONTENT_EDITOR",
  "MODERATOR",
  "EVIDENCE_REVIEWER",
  "SYSTEM_ADMIN",
] as const;

export type AppRole = typeof APP_ROLES[number];
export type SecurityCode =
  | "AUTH_REQUIRED"
  | "ROLE_REQUIRED"
  | "INVALID_REQUEST"
  | "RATE_LIMITED"
  | "SERVICE_UNAVAILABLE";

export class SecurityError extends Error {
  readonly status: number;
  readonly publicMessage: string;

  constructor(code: SecurityCode, status?: number, publicMessage?: string) {
    super(code);
    this.name = "SecurityError";
    this.status = status ?? (
      code === "AUTH_REQUIRED" ? 401 :
      code === "ROLE_REQUIRED" ? 403 :
      code === "INVALID_REQUEST" ? 400 :
      code === "RATE_LIMITED" ? 429 : 503
    );
    this.publicMessage = publicMessage ?? (
      code === "AUTH_REQUIRED" ? "Authentication is required." :
      code === "ROLE_REQUIRED" ? "This account is not authorised for this action." :
      code === "INVALID_REQUEST" ? "The request is invalid." :
      code === "RATE_LIMITED" ? "Too many requests. Try again shortly." :
      "The protected service is temporarily unavailable."
    );
  }
}

export { SecurityError as AuthError };

export type EdgeAdminClient = {
  from: (table: string) => any;
  rpc: (name: string, args?: Record<string, unknown>) => Promise<{ data: any; error: any }>;
  [key: string]: any;
};

export type EdgeAuthConfig = {
  supabaseUrl: string;
  publishableKey: string;
  admin: EdgeAdminClient;
  createUserClient?: (authorization: string) => any;
};

export type VerifiedCaller = {
  userId: string;
  role: AppRole;
};

function bearerAuthorization(request: Request): { authorization: string; token: string } {
  const authorization = request.headers.get("Authorization")?.trim() ?? "";
  if (!authorization.startsWith("Bearer ")) throw new SecurityError("AUTH_REQUIRED");
  const token = authorization.slice(7).trim();
  if (!token || token.length > 8192) throw new SecurityError("AUTH_REQUIRED");
  return { authorization, token };
}

export async function authenticateCaller(
  request: Request,
  config: EdgeAuthConfig,
): Promise<{ userId: string }> {
  const { authorization, token } = bearerAuthorization(request);
  if (!config.supabaseUrl || !config.publishableKey) throw new SecurityError("SERVICE_UNAVAILABLE");

  const userClient = config.createUserClient
    ? config.createUserClient(authorization)
    : createClient(config.supabaseUrl, config.publishableKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });

  const { data, error } = await userClient.auth.getUser(token);
  if (error || !data?.user?.id || !isUuid(data.user.id)) throw new SecurityError("AUTH_REQUIRED");
  return { userId: data.user.id };
}

export async function authorizeCaller(
  request: Request,
  config: EdgeAuthConfig,
  allowedRoles: readonly string[],
): Promise<VerifiedCaller> {
  const { userId } = await authenticateCaller(request, config);
  if (!allowedRoles.length) throw new SecurityError("ROLE_REQUIRED");

  const { data, error } = await config.admin
    .from("user_roles")
    .select("role")
    .eq("user_id", userId)
    .limit(20);
  if (error) throw new SecurityError("SERVICE_UNAVAILABLE");

  const rows = Array.isArray(data) ? data : [];
  const role = allowedRoles.find((candidate) => rows.some((row) => row?.role === candidate));
  if (!role || !(APP_ROLES as readonly string[]).includes(role)) throw new SecurityError("ROLE_REQUIRED");
  return { userId, role: role as AppRole };
}

export async function verifyCaller(
  request: Request,
  config: EdgeAuthConfig,
  allowedRoles: readonly string[],
): Promise<VerifiedCaller> {
  return await authorizeCaller(request, config, allowedRoles);
}

export function boundedText(value: unknown, maximum: number, minimum = 1): string | null {
  if (typeof value !== "string" || !Number.isInteger(maximum) || maximum < minimum || minimum < 0) return null;
  const trimmed = value.trim();
  return trimmed.length >= minimum && trimmed.length <= maximum ? trimmed : null;
}

export function isUuid(value: unknown): value is string {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

export function jsonObject(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

export async function readJsonObject(request: Request, maximumBytes = 65_536): Promise<Record<string, unknown>> {
  if (!Number.isInteger(maximumBytes) || maximumBytes < 2 || maximumBytes > 1_048_576) {
    throw new SecurityError("INVALID_REQUEST");
  }
  const contentLength = request.headers.get("content-length");
  if (contentLength !== null) {
    const declaredLength = Number(contentLength);
    if (!Number.isFinite(declaredLength) || declaredLength < 0 || declaredLength > maximumBytes) {
      throw new SecurityError("INVALID_REQUEST");
    }
  }

  const source = await request.text();
  if (new TextEncoder().encode(source).byteLength > maximumBytes) throw new SecurityError("INVALID_REQUEST");
  try {
    const parsed = JSON.parse(source);
    const object = jsonObject(parsed);
    if (!object) throw new SecurityError("INVALID_REQUEST");
    return object;
  } catch (error) {
    if (error instanceof SecurityError) throw error;
    throw new SecurityError("INVALID_REQUEST");
  }
}

export async function enforceRateLimit(
  admin: EdgeAdminClient,
  scope: string,
  actorId: string,
  maxRequests: number,
  windowSeconds: number,
): Promise<void> {
  if (
    !boundedText(scope, 80) ||
    !isUuid(actorId) ||
    !Number.isInteger(maxRequests) || maxRequests < 1 || maxRequests > 120 ||
    !Number.isInteger(windowSeconds) || windowSeconds < 1 || windowSeconds > 3600
  ) {
    throw new SecurityError("INVALID_REQUEST");
  }
  const { data, error } = await admin.rpc("claim_edge_function_rate_limit", {
    p_scope: scope,
    p_actor_id: actorId,
    p_max_requests: maxRequests,
    p_window_seconds: windowSeconds,
  });
  if (error) throw new SecurityError("SERVICE_UNAVAILABLE");
  if (data !== true) throw new SecurityError("RATE_LIMITED");
}

export type AuditEvent = {
  actorId: string | null;
  eventType: string;
  result: string;
  entityType?: string | null;
  entityId?: string | null;
  metadata?: Record<string, unknown>;
};

export async function writeAudit(admin: EdgeAdminClient, event: AuditEvent): Promise<boolean> {
  const eventType = boundedText(event.eventType, 120);
  const result = boundedText(event.result, 80);
  if (!eventType || !result) return false;
  const entityId = event.entityId && isUuid(event.entityId) ? event.entityId : null;
  const actorId = event.actorId && isUuid(event.actorId) ? event.actorId : null;
  const metadata = event.metadata && jsonObject(event.metadata) ? event.metadata : {};
  const { error } = await admin.from("audit_events").insert({
    actor_id: actorId,
    event_type: eventType,
    entity_type: boundedText(event.entityType, 80) ?? null,
    entity_id: entityId,
    result,
    metadata,
    source: "supabase-edge-function",
  });
  return !error;
}

export async function verifySchedulerCaller(request: Request, admin: EdgeAdminClient): Promise<void> {
  const secret = boundedText(request.headers.get("x-rtc-alert-dispatch-secret"), 512);
  if (!secret) throw new SecurityError("AUTH_REQUIRED");
  const { data, error } = await admin.rpc("assert_rtc_alert_dispatch_secret", { p_secret: secret });
  if (error || data !== true) throw new SecurityError("AUTH_REQUIRED");
}

export function publicError(error: unknown): { status: number; body: { error: string; errorCode: string } } {
  if (error instanceof SecurityError) {
    return { status: error.status, body: { error: error.publicMessage, errorCode: error.message } };
  }
  return {
    status: 500,
    body: { error: "The protected service is unavailable.", errorCode: "SERVICE_ERROR" },
  };
}

export function publicErrorStatus(error: unknown): number {
  return publicError(error).status;
}
