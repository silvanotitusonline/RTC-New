# RTC live TomTom integration

This change connects the installed application's native Kotlin/Compose map routes
to RTC's existing Supabase backend. It does not depend on the separate Express
implementation package.

## Runtime configuration

Production project: `pbzzfzfgwzwdstvnwzqu` (RTC Community Production).
Both supplied credentials are provisioned into encrypted Supabase Vault entries:

| Vault name | Purpose | Client exposure |
| --- | --- | --- |
| `TOMTOM_API_KEY` | Server-side TomTom search, reverse geocoding and routing | Never returned to Android |
| `TOMTOM_MAPS_SDK_KEY` | Native map display and optional traffic layers | Returned to signed-in users for the native SDK |

The service-only `rtc_tomtom_credentials_v1()` function selects these two named
entries. Anonymous and authenticated database clients have no execution grant.
The `tomtom-location` Edge Function validates each user token through Supabase
Auth, applies the shared database-backed rate limit, and returns no-store responses.
Its configuration action returns only the mobile map key. No supplied key is
stored in source control, Gradle properties, BuildConfig or a bundled asset.
Runtime delivery avoids embedding keys in source; it cannot make a client SDK key
inaccessible to someone controlling the device.

## Application behavior

- Marketplace map plots actual published PUBLIC business locations, with bounded
  concurrent loading and a short-lived cache. Businesses without published coordinates
  are identified as unavailable for mapping; coordinates are never invented.
- Business and linked service-provider directions open RTC's native map. Private
  provider base locations remain private.
- Civic map uses the existing sanitized `public_latitude`/`public_longitude` fields.
  It does not read owner/staff-only exact location details.
- The report composer can select a searched place or current position, retain it with
  the draft, or return to manual entry. Submission sends the actual coordinates to
  the canonical report RPC and waits for its server-issued ID; an RPC failure cannot
  produce a fabricated verified report. Nullable SQL arguments are sent explicitly.
- Foreground location accepts precise or approximate permission, rejects stale fixes,
  stops on background, and clears location/search/route state when the account changes.
- Search and reverse geocoding use live TomTom results. Routing supports driving,
  walking and cycling with distance, ETA, route geometry and textual instructions.
- Driving calculations request current traffic. The optional native traffic overlay
  has a separate opt-in for the location-probe sharing required by the SDK.
- The map supports marker selection, pan/zoom, recentering, route fitting and
  accessible list selection, and preserves TomTom attribution.
- Empty results, permission denial, unavailable routes and network failures remain
  explicit states; sample businesses/coordinates are no longer substituted by the
  affected discovery repositories.

This provides route planning and textual directions. It does not claim background
turn-by-turn voice navigation or offline downloaded maps.

## SDK and build

The app uses TomTom 2.5.3 Standard **Compose Complete**. Its native artifacts are
publicly downloadable; Extended Maven credentials are needed for the separate
View-based API, not this Compose renderer. Android NDK 26.3.11579264 and documented
64-bit ABIs are configured. Unsupported OpenGL ES devices receive an explicit error.
The SDK starts with telemetry off.

The existing Android CI builds the actual application. The TomTom workflow checks
the Edge Function and its focused behavior/security tests. `tomtom_security_test.sql`
verifies the Vault access boundary without returning any credential values.

## Verification

Direct live checks with the supplied keys returned HTTP 200 for Postmasburg search,
driving-route calculation and native map tile retrieval. Vault access checks confirmed
the keys exist and anonymous/resident direct reads are denied. Full application build,
deployed endpoint and device verification results are recorded in the integration PR.

The deployed endpoint also passed authenticated configuration, search, reverse-geocode,
driving, walking and cycling checks. Anonymous access returned 401, invalid coordinates
returned 400, and direct resident access to the credential RPC returned 403. No response
included the server-side key. Device rendering remains a separate acceptance check;
this execution environment has no Android emulator or ADB device.

Primary documentation:

- [TomTom project setup](https://docs.tomtom.com/navigation/android/getting-started/project-setup)
- [TomTom Standard Compose and Extended View APIs](https://docs.tomtom.com/navigation/android/getting-started/low-end-configuration)
- [TomTom SDK initialization and consent](https://docs.tomtom.com/navigation/android/getting-started/initializing-and-configuring-the-sdk)
- [TomTom Compose traffic](https://docs.tomtom.com/navigation/android/guides/map-display/map-display-for-compose/traffic)
- [Supabase Vault](https://supabase.com/docs/guides/database/vault)
