import { HttpError } from './errors.js';

export function createTomTom(apiKey, fetchImpl = fetch) {
  async function request(path, params) {
    if (!apiKey) throw new HttpError(503, 'LOCATION_CONFIGURATION_REQUIRED', 'Location routing is not configured.');
    const url = new URL(path, 'https://api.tomtom.com');
    for (const [key, value] of Object.entries({ ...params, key: apiKey })) url.searchParams.set(key, String(value));
    try {
      const response = await fetchImpl(url, { signal: AbortSignal.timeout(8000), redirect: 'error' });
      if (response.status === 429) throw new HttpError(503, 'LOCATION_RATE_LIMITED', 'The map service is busy. Try again shortly.');
      if (!response.ok) throw new HttpError(502, 'LOCATION_UPSTREAM_ERROR', 'The map service could not complete this request.');
      const chunks = [];
      let length = 0;
      for await (const chunk of response.body) {
        length += chunk.byteLength;
        if (length > 1024 * 1024) throw new Error('UPSTREAM_TOO_LARGE');
        chunks.push(chunk);
      }
      return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch (error) {
      if (error instanceof HttpError) throw error;
      throw new HttpError(502, 'LOCATION_UNAVAILABLE', 'Live location data is temporarily unavailable.');
    }
  }
  return {
    async route({ fromLat, fromLon, toLat, toLon }) {
      const data = await request(`/routing/1/calculateRoute/${fromLat},${fromLon}:${toLat},${toLon}/json`, {
        travelMode: 'car', traffic: 'true', departAt: 'now', routeRepresentation: 'summaryOnly',
      });
      const summary = data.routes?.[0]?.summary;
      if (!summary) throw new HttpError(404, 'NO_ROUTE', 'No road route is available between these locations.');
      if (!Number.isSafeInteger(summary.lengthInMeters) || summary.lengthInMeters < 0 ||
          !Number.isSafeInteger(summary.travelTimeInSeconds) || summary.travelTimeInSeconds < 0) {
        throw new HttpError(502, 'LOCATION_INVALID_RESPONSE', 'The map service returned incomplete route data.');
      }
      return { distanceMeters: summary.lengthInMeters, travelTimeSeconds: summary.travelTimeInSeconds, calculatedAt: new Date().toISOString() };
    },
    async search({ q, lat, lon }) {
      const data = await request(`/search/2/search/${encodeURIComponent(q)}.json`, { lat, lon, limit: 10, countrySet: 'ZA', language: 'en-GB', typeahead: 'false' });
      if (!Array.isArray(data.results)) throw new HttpError(502, 'LOCATION_INVALID_RESPONSE', 'The map service returned incomplete search data.');
      return data.results.filter((item) => typeof item.id === 'string' &&
        Number.isFinite(item.position?.lat) && Math.abs(item.position.lat) <= 90 &&
        Number.isFinite(item.position?.lon) && Math.abs(item.position.lon) <= 180)
        .map((item) => ({ id: item.id, name: item.poi?.name ?? item.address?.freeformAddress ?? q, latitude: item.position.lat, longitude: item.position.lon }));
    },
  };
}
