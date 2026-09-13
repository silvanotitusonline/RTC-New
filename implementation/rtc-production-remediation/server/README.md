# RTC Kotlin / Express remediation API

This is the complete independently runnable Express implementation requested for
RTC. The existing RTC-New application uses Supabase RPC/Storage. This package does
not redirect that application, migrate its records, deploy a new server, or replace
its existing authentication flows. A deliberate data adapter/migration and an HTTPS
endpoint are required before directing an existing RTC build to this schema.

## Runtime and installation

Node.js 24, PostgreSQL 14 or newer, a persistent writable media volume, and an OIDC
provider issuing ES256 or RS256 access tokens are required. Dependencies are exact
versions in `package-lock.json`; installation was exercised with Node 24.19.0.

```sh
npm ci
cp .env.example .env
```

Set `DATABASE_URL`, `PUBLIC_BASE_URL`, `OIDC_ISSUER`, `OIDC_AUDIENCE`,
`OIDC_JWKS_URL`, `MEDIA_DIR`, and `TOMTOM_API_KEY` in `.env`. URLs supplied to Android
must use HTTPS. Keep the issuer exactly as it appears in the access token, including
any trailing slash. A Supabase project must use asymmetric JWT signing for the JWKS
configuration shown; legacy HS256 shared-secret tokens are intentionally rejected.
No token bypass or development sign-in endpoint is supplied.

Create an empty database with a migration-owner account, then apply the schema:

```sh
npm run migrate
```

After migration, create a separate database login named `rtc_app` with a strong
password through your database's secure administration interface and grant only:

```sql
GRANT CONNECT ON DATABASE rtc_api TO rtc_app;
GRANT USAGE ON SCHEMA rtc_api TO rtc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA rtc_api TO rtc_app;
```

Set the runtime `DATABASE_URL` to that account, mount `MEDIA_DIR`, and start:

```sh
npm start
```

Terminate TLS at a trusted reverse proxy/load balancer. Set a request size limit of
11 MiB and enforce pre-authentication/IP request limits there. The API applies
PostgreSQL-backed per-user limits after authentication (180 requests/minute, 30
writes/minute, 30 map requests/minute) and at most four in-flight image uploads per
instance. It enforces request/body/file/pixel limits and upstream/database timeouts.
Enable `PG_TLS=true` and an appropriate `PG_CA_FILE` for remote database connections.
The service does not trust arbitrary proxy headers or derive image URLs from Host.

The Dockerfile builds the same application and runs it as the unprivileged `node`
user. Supply secrets as environment variables or mounted secrets at deployment.
Run migrations as a separate owner job before starting the application container.
Use shared persistent storage for all replicas; an ephemeral container filesystem
will lose images. Back up the database and media volume together.

## Routes and implementation

Except for `/healthz`, every route verifies a Bearer JWT signature, issuer, audience,
expiry, issued-at and subject against the configured JWKS. Responses use the DTOs
in `../CONTRACT.md`. Money is integer cents; dates are UTC ISO instants.

| Module | Route | Implementation |
|---|---|---|
| Photo and feed | `POST /posts` | Multer accepts `body` and optional `image`; Sharp decodes and re-encodes actual image pixels; both durable media and the image reference are created before success. |
| Photo and feed | `GET /feed`, `GET /timeline` | Returns image URL, dimensions, timestamp, count and current-user vote. Timeline is scoped to the authenticated author. Empty results are `200 []`. |
| Photo and feed | `GET /media/:id` | Authenticated WebP bytes for a non-deleted community post. Storage is never exposed through `express.static`. |
| Optimistic state | `PUT /posts/:id/vote` | Sets `{voted: true|false}`, returns authoritative count, and remains safe when repeated. |
| Reporting | `POST /reports`, `GET /reports` | Validates content/priority/coordinates; writes `APP_SUPPORT` into `it_queue` and `INFRASTRUCTURE` into `municipal_queue` in the same transaction. Reads only the current user's reports. |
| Dashboard | `GET /dashboard` | Computes pending/working/resolved/total from the same reports table and authenticated-user scope. |
| Marketplace | `GET /providers` | Reads actual active providers; returns integer cents and optional live coordinates. No fake seed records. |
| Booking | `POST /bookings` | Idempotent write plus unique provider/start time. Returns `409 SLOT_UNAVAILABLE` for a slot booked with another key. |
| Location | `GET /locations/route` | TomTom car route with current traffic; returns road length, travel time and calculation timestamp. |
| Location | `GET /locations/search` | Bounded TomTom search around current coordinates, limited to South Africa. |

The photo input permits JPEG/PNG/WebP up to 10 MiB and 25 million pixels, rejects
animation and corrupt input, corrects orientation, removes metadata and resizes
inside 2048×2048 while preserving aspect ratio. The returned WebP dimensions match
the stored bytes. Kotlin/Coil must load `imageUrl` through its authenticated OkHttp
client. `imageUrl: null` is normal for text-only posts.

Feed pagination uses `(created_at,id)` keyset comparison. `limit` defaults to 30
(maximum 100). Read `X-Next-Cursor` and send it as `before` for the next page. The
cursor preserves PostgreSQL microseconds to avoid losing entries created within
the same millisecond. New posts are inserted into the first page by the Android
repository; a refresh can reconcile current counters.

All successful posts, reports and bookings require a UUID `Idempotency-Key`.
The unique `(user_id,key)` database row serializes simultaneous callers; insertion,
business operation and saved response share one transaction. The payload hash
also includes the operation. Retrying the same key/body returns the original
status/body; changing either returns `409 IDEMPOTENCY_CONFLICT`. Keep the original
key through WorkManager retries. Do not expire idempotency rows while an offline
outbox can still contain those requests. A booked instant is validated as future
only on the first execution, so later replays still work.

Bookings currently reserve a provider at an exact `startsAt` instant. This schema
prevents duplicate starts, as requested; variable-duration overlapping appointments
require explicit availability/duration rules and an exclusion constraint before
those product semantics can be introduced.

Report status starts as `PENDING` with the server's actual creation timestamp.
Only report submission and resident reads are exposed here. Administrative queue
claiming, status changes and provider onboarding remain privileged operations in
your existing admin system and require an adapter to this standalone schema.
Private support reports are never unioned into the community feed.

TomTom credentials remain on the server. Location endpoints accept only validated
coordinates/search strings, use a fixed upstream origin, reject redirects, cap the
response body, time out after eight seconds, and never substitute fake locations or
zero distance. A missing key returns `503 LOCATION_CONFIGURATION_REQUIRED`; no road
route returns `404 NO_ROUTE`; unavailable provider data returns an explicit error.

## Verification

```sh
npm run check
npm test
```

The default suite uses actual Express HTTP requests, cryptographically signed JWTs,
Sharp image decoding, disk persistence and a real PostgreSQL engine via PGlite.
PGlite is single-connection WASM, so it does not by itself prove multi-connection
transaction races. Run the identical suite against a disposable native PostgreSQL
database to exercise those cases with the application's real `pg.Pool`:

```sh
TEST_DATABASE_URL=postgresql://test_user:test_password@127.0.0.1:5432/rtc_api_test npm test
```

The test database name must end in `_test`. This command drops its `rtc_api` schema;
never give it a production connection. TomTom responses in the test suite are
controlled upstream fixtures, and the JWT fixture key is test-only. No live TomTom
route, cloud deployment, identity-provider integration or Android execution is
claimed by these server tests.

Coverage includes upload/decode/feed/media round-trip, oversize/corrupt rejection,
JWT issuer/audience/signature/expiry failures, empty states, per-user privacy,
microsecond cursors, repeatable voting, report queue routing, dashboard counts,
idempotency replay/conflict, concurrent identical bookings, competing slot keys,
integer money and explicit TomTom failure responses.

File persistence precedes SQL commit. A crash or unknown commit outcome can leave
an unreferenced file but cannot cause cleanup to delete a potentially committed
image. Remove files older than 24 hours that remain unreferenced with:

```sh
node --env-file=.env scripts/cleanup-media.js
```

The cleanup command also removes old interrupted staging files. Apply a daily
scheduler outside the request path, and monitor volume capacity and failed requests.

## Primary documentation checked

- [Express 5 and installation](https://expressjs.com/)
- [Multer upload limits and middleware](https://expressjs.com/en/resources/middleware/multer/)
- [Sharp image input and pixel limits](https://sharp.pixelplumbing.com/api-constructor/)
- [JOSE JWT verification and JWKS](https://github.com/panva/jose)
- [TomTom Calculate Route](https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/calculate-route)
- [TomTom Fuzzy Search](https://docs.tomtom.com/search-api/documentation/search-service/fuzzy-search)
