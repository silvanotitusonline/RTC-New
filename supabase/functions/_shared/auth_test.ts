import { assertEquals, assertRejects } from "jsr:@std/assert";
import {
  SecurityError,
  authenticateCaller,
  authorizeCaller,
  boundedText,
  enforceRateLimit,
  publicError,
  readJsonObject,
  verifySchedulerCaller,
} from "./auth.ts";

const userId = "00000000-0000-4000-8000-000000000001";

function requestWith(token?: string): Request {
  return new Request("https://example.test/functions/v1/test", {
    method: "POST",
    headers: token ? { Authorization: token } : {},
    body: "{}",
  });
}

function userClient(user: { id: string } | null, error: unknown = null) {
  return {
    auth: { getUser: async () => ({ data: { user }, error }) },
  } as any;
}

function adminClient(options: {
  roles?: string[];
  roleError?: unknown;
  rateLimit?: boolean;
  rateError?: unknown;
  schedulerAllowed?: boolean;
} = {}) {
  return {
    from: () => ({
      select: () => ({
        eq: () => ({
          limit: async () => ({ data: (options.roles ?? []).map((role) => ({ role })), error: options.roleError ?? null }),
        }),
      }),
    }),
    rpc: async (name: string) => {
      if (name === "claim_edge_function_rate_limit") {
        return { data: options.rateLimit ?? true, error: options.rateError ?? null };
      }
      if (name === "assert_rtc_alert_dispatch_secret") {
        return { data: options.schedulerAllowed ?? false, error: null };
      }
      return { data: null, error: { message: "unexpected rpc" } };
    },
  } as any;
}

function config(
  roles: string[] = ["RESIDENT"],
  user: { id: string } | null = { id: userId },
  userError: unknown = null,
) {
  return {
    supabaseUrl: "https://example.test",
    publishableKey: "sb_publishable_test",
    admin: adminClient({ roles }),
    createUserClient: () => userClient(user, userError),
  };
}

Deno.test("shared auth rejects a missing bearer JWT", async () => {
  await assertRejects(() => authenticateCaller(requestWith(), config()), SecurityError, "AUTH_REQUIRED");
});

Deno.test("shared auth rejects a malformed authorization scheme", async () => {
  await assertRejects(
    () => authenticateCaller(requestWith("Basic abc"), config()),
    SecurityError,
    "AUTH_REQUIRED",
  );
});

Deno.test("shared auth rejects an expired or invalid user JWT", async () => {
  await assertRejects(
    () => authenticateCaller(requestWith("Bearer expired.jwt.token"), config(["RESIDENT"], null, { message: "expired" })),
    SecurityError,
    "AUTH_REQUIRED",
  );
});

Deno.test("shared role authorization rejects a valid caller with the wrong server-side role", async () => {
  await assertRejects(
    () => authorizeCaller(requestWith("Bearer valid.jwt.token"), config(["RESIDENT"]), ["SYSTEM_ADMIN"]),
    SecurityError,
    "ROLE_REQUIRED",
  );
});

Deno.test("shared role authorization accepts the server-side allowed role", async () => {
  const caller = await authorizeCaller(
    requestWith("Bearer valid.jwt.token"),
    config(["SYSTEM_ADMIN"]),
    ["SYSTEM_ADMIN"],
  );
  assertEquals(caller, { userId, role: "SYSTEM_ADMIN" });
});

Deno.test("shared role vocabulary preserves evidence reviewer authority", async () => {
  const caller = await authorizeCaller(
    requestWith("Bearer valid.jwt.token"),
    config(["EVIDENCE_REVIEWER"]),
    ["EVIDENCE_REVIEWER"],
  );
  assertEquals(caller, { userId, role: "EVIDENCE_REVIEWER" });
});

Deno.test("bounded text fails closed on blank and oversized values", () => {
  assertEquals(boundedText("  ", 10), null);
  assertEquals(boundedText("abcdefghijk", 10), null);
  assertEquals(boundedText("  valid  ", 10), "valid");
});

Deno.test("bounded JSON rejects actual body bytes beyond the limit", async () => {
  const request = new Request("https://example.test", {
    method: "POST",
    body: JSON.stringify({ value: "x".repeat(200) }),
  });
  await assertRejects(() => readJsonObject(request, 64), SecurityError, "INVALID_REQUEST");
});

Deno.test("bounded JSON accepts a small object", async () => {
  const request = new Request("https://example.test", { method: "POST", body: '{"ok":true}' });
  assertEquals(await readJsonObject(request, 64), { ok: true });
});

Deno.test("database rate-limit denial maps to RATE_LIMITED", async () => {
  await assertRejects(
    () => enforceRateLimit(adminClient({ rateLimit: false }), "community_media_url", userId, 10, 60),
    SecurityError,
    "RATE_LIMITED",
  );
});

Deno.test("database rate-limit infrastructure errors fail closed without masquerading as quota exhaustion", async () => {
  await assertRejects(
    () => enforceRateLimit(adminClient({ rateError: { message: "db down" } }), "community_media_url", userId, 10, 60),
    SecurityError,
    "SERVICE_UNAVAILABLE",
  );
});

Deno.test("database rate-limit parameter bounds match the SQL primitive", async () => {
  await assertRejects(
    () => enforceRateLimit(adminClient(), "community_media_url", userId, 121, 60),
    SecurityError,
    "INVALID_REQUEST",
  );
  await assertRejects(
    () => enforceRateLimit(adminClient(), "community_media_url", userId, 10, 3601),
    SecurityError,
    "INVALID_REQUEST",
  );
});

Deno.test("public error mapping never exposes unknown internal messages", () => {
  const mapped = publicError(new Error("database password leaked here"));
  assertEquals(mapped.status, 500);
  assertEquals(mapped.body, { error: "The protected service is unavailable.", errorCode: "SERVICE_ERROR" });
});

Deno.test("scheduler guard requires the independent machine secret", async () => {
  const missing = new Request("https://example.test", { method: "POST" });
  await assertRejects(() => verifySchedulerCaller(missing, adminClient()), SecurityError, "AUTH_REQUIRED");

  const wrong = new Request("https://example.test", {
    method: "POST",
    headers: { "x-rtc-alert-dispatch-secret": "not-the-secret" },
  });
  await assertRejects(() => verifySchedulerCaller(wrong, adminClient({ schedulerAllowed: false })), SecurityError, "AUTH_REQUIRED");

  const correct = new Request("https://example.test", {
    method: "POST",
    headers: { "x-rtc-alert-dispatch-secret": "machine-secret" },
  });
  await verifySchedulerCaller(correct, adminClient({ schedulerAllowed: true }));
});
