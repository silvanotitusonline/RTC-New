import { createLocationHandler } from "./index.ts";
import { LocationError, point, queryTomTom } from "./provider.ts";
import { SecurityError } from "../_shared/auth.ts";

function assert(value: unknown, message = "Assertion failed"): asserts value { if (!value) throw new Error(message); }
function equal(actual: unknown, expected: unknown) { assert(JSON.stringify(actual) === JSON.stringify(expected), `${JSON.stringify(actual)} != ${JSON.stringify(expected)}`); }
async function rejects(fn: () => unknown, status: number) {
  try { await fn(); throw new Error("Expected rejection"); }
  catch (error) { assert(error instanceof LocationError); equal(error.status, status); }
}
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json" } });
const origin = { latitude: -28.332649, longitude: 23.062371 };
const destination = { latitude: -28.3, longitude: 23.1 };
const routeResponse = { routes: [{ summary: { lengthInMeters: 6789, travelTimeInSeconds: 511, trafficDelayInSeconds: 14 }, legs: [{ points: [{ latitude: -28.332649, longitude: 23.062371 }, { latitude: -28.3, longitude: 23.1 }] }], guidance: { instructions: [{ message: "Turn left", routeOffsetInMeters: 110 }] } }] };

Deno.test("coordinate validation rejects coercions, non-finite and out-of-range values", async () => {
  equal(point(origin), origin);
  for (const value of [null, [], {}, { latitude: "0", longitude: 0 }, { latitude: NaN, longitude: 0 }, { latitude: 0, longitude: Infinity }, { latitude: 91, longitude: 0 }]) {
    await rejects(() => point(value), 400);
  }
});

Deno.test("search encodes user text under a fixed upstream origin and returns actual coordinates", async () => {
  let called = "";
  const result = await queryTomTom({ action: "search", query: "Clinic/?key=other&", origin }, "server-test-key", {
    fetcher: async (url, init) => {
      called = String(url); equal(init?.redirect, "error");
      return json({ results: [{ id: "real-id", poi: { name: "Clinic" }, address: { freeformAddress: "Postmasburg" }, position: { lat: -28.3, lon: 23.1 } }] });
    },
  });
  const url = new URL(called); equal(url.origin, "https://api.tomtom.com"); equal(url.searchParams.get("key"), "server-test-key");
  equal(url.searchParams.get("countrySet"), "ZA"); equal(url.searchParams.get("lat"), String(origin.latitude));
  equal(result, { results: [{ id: "real-id", title: "Clinic", address: "Postmasburg", position: destination }] });
});

Deno.test("empty search is a successful empty collection", async () => {
  equal(await queryTomTom({ action: "search", query: "Unlisted place" }, "key", { fetcher: async () => json({ results: [] }) }), { results: [] });
});

Deno.test("car routes preserve provider distance, time, geometry and guidance", async () => {
  const result = await queryTomTom({ action: "route", origin, destination, travelMode: "car" }, "key", {
    fetcher: async url => {
      equal(new URL(String(url)).searchParams.get("traffic"), "true");
      return json(routeResponse);
    }, now: () => new Date("2026-09-13T00:00:00Z"),
  });
  equal(result.distanceMeters, 6789); equal(result.durationSeconds, 511); equal(result.trafficDelaySeconds, 14);
  equal(result.points, [origin, destination]); equal(result.instructions, [{ message: "Turn left", routeOffsetInMeters: 110 }]);
  equal(result.calculatedAt, "2026-09-13T00:00:00.000Z");
});

Deno.test("walking and cycling explicitly disable live car traffic", async () => {
  for (const travelMode of ["pedestrian", "bicycle"]) {
    await queryTomTom({ action: "route", origin, destination, travelMode }, "key", { fetcher: async url => {
      const p = new URL(String(url)).searchParams; equal(p.get("travelMode"), travelMode); equal(p.get("traffic"), "false"); return json(routeResponse);
    } });
  }
});

Deno.test("invalid query and route mode do not contact the provider", async () => {
  let calls = 0;
  const fetcher = async () => { calls++; return json({}); };
  for (const input of [{ action: "search", query: "a" }, { action: "route", origin, destination, travelMode: "jet" }, { action: "fetch", url: "http://localhost" }]) {
    await rejects(() => queryTomTom(input, "key", { fetcher }), 400);
  }
  equal(calls, 0);
});

Deno.test("reverse geocoding preserves published address and coordinates", async () => {
  const result = await queryTomTom({ action: "reverse", position: origin }, "key", { fetcher: async () => json({ addresses: [{ address: { freeformAddress: "Main Road, Postmasburg" }, position: "-28.3,23.1" }] }) });
  equal(result, { results: [{ id: "-28.3,23.1", title: "Main Road, Postmasburg", address: "Main Road, Postmasburg", position: destination }] });
});

Deno.test("upstream rate limit, denied credentials, no route and malformed response are explicit", async () => {
  for (const [status, expected, body] of [[429,429,{}],[403,503,{}],[400,404,{errorText:"NO_ROUTE_FOUND"}],[200,502,{routes:[{summary:{},legs:[]}]}]] as const) {
    await rejects(() => queryTomTom({ action: "route", origin, destination }, "key", { fetcher: async () => json(body, status) }), expected);
  }
  await rejects(() => queryTomTom({ action: "route", origin, destination }, "key", { fetcher: async () => { throw new TypeError("contains secret URL"); } }), 502);
});

Deno.test("oversized upstream bodies are cancelled and rejected", async () => {
  await rejects(() => queryTomTom({ action: "search", query: "Clinic" }, "key", { fetcher: async () => new Response("", { headers: { "content-length": "2000001" } }) }), 502);
});

Deno.test("anonymous request cannot access credentials or upstream", async () => {
  let credentialsRead = false;
  const handler = createLocationHandler({ authenticate: async () => { throw new SecurityError("AUTH_REQUIRED"); }, rateLimit: async () => {}, credentials: async () => { credentialsRead = true; return {}; } });
  const response = await handler(new Request("https://rtc.test", { method: "POST", body: '{"action":"config"}' }));
  equal(response.status, 401); equal(credentialsRead, false);
});

Deno.test("configuration returns only native key and never the routing key", async () => {
  const handler = createLocationHandler({ authenticate: async () => ({ userId: "resident" }), rateLimit: async () => {}, credentials: async () => ({ TOMTOM_API_KEY: "private-server-key", TOMTOM_MAPS_SDK_KEY: "mobile-map-key" }) });
  const response = await handler(new Request("https://rtc.test", { method: "POST", body: '{"action":"config"}' }));
  equal(response.status, 200); equal(response.headers.get("cache-control"), "no-store");
  const body = await response.text(); assert(!body.includes("private-server-key")); equal(JSON.parse(body).mapKey, "mobile-map-key");
});

Deno.test("rate limit prevents credential retrieval and request dispatch", async () => {
  let credentialsRead = false;
  const handler = createLocationHandler({ authenticate: async () => ({ userId: "resident" }), rateLimit: async () => { throw new SecurityError("RATE_LIMITED"); }, credentials: async () => { credentialsRead = true; return {}; } });
  const response = await handler(new Request("https://rtc.test", { method: "POST", body: '{"action":"config"}' }));
  equal(response.status, 429); equal(credentialsRead, false);
});
