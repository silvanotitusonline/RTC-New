export type Point = { latitude: number; longitude: number };
type ObjectValue = Record<string, unknown>;

export class LocationError extends Error {
  constructor(readonly status: number, readonly code: string, readonly publicMessage: string) {
    super(code);
  }
}

const invalid = () => new LocationError(400, "INVALID_LOCATION_REQUEST", "Check the location and try again.");
const upstream = () => new LocationError(502, "MAP_PROVIDER_UNAVAILABLE", "The map service is temporarily unavailable. Try again.");
const object = (v: unknown): ObjectValue | null => typeof v === "object" && v !== null && !Array.isArray(v) ? v as ObjectValue : null;
const list = (v: unknown): unknown[] => Array.isArray(v) ? v : [];
const string = (v: unknown): string => typeof v === "string" ? v : "";

export function point(value: unknown): Point {
  const p = object(value);
  if (!p || typeof p.latitude !== "number" || typeof p.longitude !== "number" ||
    !Number.isFinite(p.latitude) || !Number.isFinite(p.longitude) ||
    Math.abs(p.latitude) > 90 || Math.abs(p.longitude) > 180) throw invalid();
  return { latitude: p.latitude, longitude: p.longitude };
}

function upstreamPoint(value: unknown): Point {
  const p = object(value);
  try { return point({ latitude: p?.lat ?? p?.latitude, longitude: p?.lon ?? p?.longitude }); }
  catch { throw upstream(); }
}

function natural(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) throw upstream();
  return value;
}

async function readBounded(response: Response): Promise<ObjectValue> {
  const maximum = 2_000_000;
  if (Number(response.headers.get("content-length") ?? 0) > maximum) {
    await response.body?.cancel(); throw upstream();
  }
  const reader = response.body?.getReader();
  if (!reader) throw upstream();
  let bytes = 0;
  const chunks: Uint8Array[] = [];
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > maximum) { await reader.cancel(); throw upstream(); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const all = new Uint8Array(bytes);
  let offset = 0;
  for (const chunk of chunks) { all.set(chunk, offset); offset += chunk.byteLength; }
  try {
    const result = object(JSON.parse(new TextDecoder().decode(all)));
    if (!result) throw upstream();
    return result;
  } catch { throw upstream(); }
}

export type ProviderOptions = { fetcher?: typeof fetch; now?: () => Date };

export async function queryTomTom(
  input: ObjectValue,
  apiKey: string,
  options: ProviderOptions = {},
): Promise<ObjectValue> {
  if (!apiKey) throw new LocationError(503, "MAP_NOT_CONFIGURED", "Maps are not configured yet.");
  let path: string;
  const params = new URLSearchParams({ key: apiKey });
  const action = input.action;
  let mode = "car";
  if (action === "search") {
    if (typeof input.query !== "string") throw invalid();
    const query = input.query.trim();
    if (query.length < 2 || query.length > 160) throw invalid();
    path = `/search/2/search/${encodeURIComponent(query)}.json`;
    params.set("countrySet", "ZA"); params.set("limit", "8"); params.set("language", "en-GB");
    if (input.origin != null) {
      const origin = point(input.origin);
      params.set("lat", String(origin.latitude)); params.set("lon", String(origin.longitude));
    }
  } else if (action === "reverse") {
    const p = point(input.position);
    path = `/search/2/reverseGeocode/${p.latitude},${p.longitude}.json`;
    params.set("language", "en-GB");
  } else if (action === "route") {
    const origin = point(input.origin); const destination = point(input.destination);
    if (input.travelMode != null && !["car", "pedestrian", "bicycle"].includes(String(input.travelMode))) throw invalid();
    mode = input.travelMode == null ? "car" : String(input.travelMode);
    path = `/routing/1/calculateRoute/${origin.latitude},${origin.longitude}:${destination.latitude},${destination.longitude}/json`;
    params.set("travelMode", mode); params.set("traffic", mode === "car" ? "true" : "false");
    params.set("routeType", "fastest"); params.set("routeRepresentation", "polyline");
    params.set("instructionsType", "text"); params.set("language", "en-GB");
  } else throw invalid();

  // Fixed origin and redirect rejection prevent turning an authenticated proxy into SSRF.
  let response: Response;
  try {
    response = await (options.fetcher ?? fetch)(`https://api.tomtom.com${path}?${params}`, {
      signal: AbortSignal.timeout(12_000), redirect: "error", headers: { Accept: "application/json" },
    });
  } catch { throw upstream(); }
  if (response.status === 429) {
    await response.body?.cancel();
    throw new LocationError(429, "MAP_RATE_LIMITED", "Map requests are busy. Try again shortly.");
  }
  if (response.status === 401 || response.status === 403) {
    await response.body?.cancel();
    throw new LocationError(503, "MAP_NOT_CONFIGURED", "The map service needs configuration. Please contact support.");
  }
  if (action === "route" && response.status === 404) {
    await response.body?.cancel();
    throw new LocationError(404, "NO_ROUTE", "No route is available for these locations and travel mode.");
  }
  const body = await readBounded(response);
  if (!response.ok) {
    const description = string(body.errorText) + string(object(body.detailedError)?.message) + string(object(body.error)?.description);
    if (action === "route" && (response.status === 404 || /NO_ROUTE_FOUND|no route|cannot be routed/i.test(description))) {
      throw new LocationError(404, "NO_ROUTE", "No route is available for these locations and travel mode.");
    }
    throw upstream();
  }
  if (action === "route") {
    const route = object(list(body.routes)[0]);
    if (!route) throw new LocationError(404, "NO_ROUTE", "No route is available for these locations and travel mode.");
    const summary = object(route.summary);
    const rawPoints = list(route.legs).flatMap(leg => list(object(leg)?.points)).map(upstreamPoint);
    if (!summary || rawPoints.length < 2) throw upstream();
    // Retain both endpoints and sample long geometries evenly to bound mobile rendering cost.
    const points = rawPoints.length <= 4_000 ? rawPoints : Array.from({ length: 4_000 }, (_, i) => rawPoints[Math.round(i * (rawPoints.length - 1) / 3_999)]);
    const instructions = list(object(route.guidance)?.instructions).map(raw => {
      const instruction = object(raw);
      const message = string(instruction?.message).trim();
      if (!message) return null;
      return { message, routeOffsetInMeters: natural(instruction?.routeOffsetInMeters) };
    }).filter(Boolean);
    return {
      distanceMeters: natural(summary.lengthInMeters), durationSeconds: natural(summary.travelTimeInSeconds),
      trafficDelaySeconds: natural(summary.trafficDelayInSeconds ?? 0), points, instructions,
      calculatedAt: (options.now ?? (() => new Date()))().toISOString(), travelMode: mode,
    };
  }
  if (action === "reverse") {
    if (!Array.isArray(body.addresses)) throw upstream();
    return { results: body.addresses.slice(0, 8).map(raw => {
      const item = object(raw); const address = object(item?.address);
      let position: Point;
      if (item?.position && typeof item.position === "object") position = upstreamPoint(item.position);
      else {
        const values = string(item?.position).split(",");
        if (values.length !== 2 || values.some(value => !value.trim())) throw upstream();
        position = upstreamPoint({ latitude: Number(values[0]), longitude: Number(values[1]) });
      }
      const label = string(address?.freeformAddress);
      return { id: `${position.latitude},${position.longitude}`, title: label, address: label, position };
    }) };
  }
  if (!Array.isArray(body.results)) throw upstream();
  return { results: body.results.slice(0, 8).map(raw => {
    const item = object(raw); const address = object(item?.address);
    const label = string(address?.freeformAddress);
    return { id: string(item?.id), title: string(object(item?.poi)?.name) || label, address: label, position: upstreamPoint(item?.position) };
  }) };
}
