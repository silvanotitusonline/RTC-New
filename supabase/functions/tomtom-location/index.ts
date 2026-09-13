import { createClient } from "npm:@supabase/supabase-js@2.99.2";
import { authenticateCaller, enforceRateLimit, publicError, SecurityError, type EdgeAdminClient } from "../_shared/auth.ts";
import { LocationError, queryTomTom } from "./provider.ts";

const json = (status: number, data: unknown) => new Response(JSON.stringify(data), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" },
});

export type LocationDependencies = {
  authenticate: (request: Request) => Promise<{ userId: string }>;
  rateLimit: (userId: string) => Promise<void>;
  credentials: () => Promise<Record<string, string>>;
  fetcher?: typeof fetch;
};

async function readLocationInput(request: Request): Promise<Record<string, unknown>> {
  const maximum = 4_096;
  const declared = Number(request.headers.get("content-length") ?? 0);
  if (!Number.isFinite(declared) || declared < 0 || declared > maximum) throw new SecurityError("INVALID_REQUEST");
  const reader = request.body?.getReader();
  if (!reader) throw new SecurityError("INVALID_REQUEST");
  const bytes = new Uint8Array(maximum);
  let length = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      if (length + value.byteLength > maximum) {
        await reader.cancel(); throw new SecurityError("INVALID_REQUEST");
      }
      bytes.set(value, length); length += value.byteLength;
    }
  } finally { reader.releaseLock(); }
  try {
    const value = JSON.parse(new TextDecoder().decode(bytes.subarray(0, length)));
    if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error();
    return value;
  } catch { throw new SecurityError("INVALID_REQUEST"); }
}

export function createLocationHandler(deps: LocationDependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST") return json(405, { error: "POST is required.", errorCode: "METHOD_NOT_ALLOWED" });
    try {
      const caller = await deps.authenticate(request);
      await deps.rateLimit(caller.userId);
      const input = await readLocationInput(request);
      if (!["config", "route", "search", "reverse"].includes(String(input.action))) throw new SecurityError("INVALID_REQUEST");
      const keys = await deps.credentials();
      if (input.action === "config") {
        if (!keys.TOMTOM_MAPS_SDK_KEY) throw new LocationError(503, "MAP_NOT_CONFIGURED", "Maps are not configured yet.");
        // A map SDK key is necessarily delivered to the native map renderer. The service key is never returned.
        return json(200, { mapKey: keys.TOMTOM_MAPS_SDK_KEY, defaultCenter: { latitude: -28.332649, longitude: 23.062371 }, attribution: "© TomTom" });
      }
      return json(200, await queryTomTom(input, keys.TOMTOM_API_KEY ?? "", { fetcher: deps.fetcher }));
    } catch (error) {
      if (error instanceof LocationError) return json(error.status, { error: error.publicMessage, errorCode: error.code });
      const safe = publicError(error);
      return json(safe.status, safe.body);
    }
  };
}

if (import.meta.main) {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } });
  Deno.serve(createLocationHandler({
    authenticate: req => authenticateCaller(req, { supabaseUrl: url, publishableKey: anon, admin: admin as unknown as EdgeAdminClient }),
    rateLimit: userId => enforceRateLimit(admin as unknown as EdgeAdminClient, "tomtom_location", userId, 60, 60),
    credentials: async () => {
      const { data, error } = await admin.rpc("rtc_tomtom_credentials_v1");
      if (error || !data || typeof data !== "object") throw new SecurityError("SERVICE_UNAVAILABLE");
      return data as Record<string, string>;
    },
  }));
}
