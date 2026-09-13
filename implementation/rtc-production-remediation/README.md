# RTC Android and Express implementation

This package supplies the six requested modules as complete Kotlin/Compose and
Node/Express source with one matching API contract. It is **not yet connected to
the installed RTC app**. The verified RTC-New checkout uses Supabase RPCs and
Storage, not an Express backend. Existing application navigation, live records,
Daily Post services and the full RTC logo are unchanged by this package.

Use this package for the requested Express architecture, or port its corresponding
fixes through the existing Supabase repositories. Switching production traffic
requires selecting that architecture, mapping existing records/permissions and
performing device acceptance. A successful library build is not proof that the
installed app's reported defects have been fixed.

## Implementation index

All paths below are relative to this directory. Android package:
`za.org.rtc.remediation`.

| Module | Exact Android implementation | Exact server implementation |
| --- | --- | --- |
| 1. Photos/feed | `media/PhotoPreparation.kt`, `network/RtcApi.kt`, `data/RtcRepository.kt`, `ui/CommunityScreens.kt`, `ui/RtcComponents.kt` | `server/src/media.js`, POST `/posts`, GET `/feed`, GET `/timeline`, GET `/media/:id` in `server/src/app.js` |
| 2. Maps/location | `location/LiveLocationSource.kt`, `location/LocationViewModel.kt`, `location/LocationControls.kt`; native `android/tomtom/.../maps/RtcTomTomMap.kt` and `LiveMapScreen.kt` | `server/src/tomtom.js`, authenticated `/locations/route` and `/locations/search` |
| 3. Layout/states | `ui/ReportFormScreen.kt`, `ui/RtcComponents.kt`, `ui/CommunityScreens.kt`, `presentation/PresentationRules.kt` | Empty reads return HTTP 200 with `[]`; money is integer cents; nullable image URLs |
| 4. Forms/navigation | `presentation/RtcViewModels.kt`, `ui/ReportFormScreen.kt`, `ui/RtcRemediationScreen.kt`, `ui/BookingDialog.kt` | `server/src/validation.js`; server revalidates every input |
| 5. Optimism/drafts/chart | `data/RtcRepository.kt`, `presentation/RtcViewModels.kt`, `presentation/PresentationRules.kt` | PUT `/posts/:id/vote`, `/reports`, `/dashboard` |
| 6. Offline/idempotency/routing | `data/RtcDatabase.kt`, `work/OutboxWork.kt`, `work/RetryPolicy.kt`, `network/Network.kt` | `server/src/database.js`, POST `/bookings` and `/reports`, `server/migrations/001_initial.sql` |

Core Android source files are under
`android/src/main/java/za/org/rtc/remediation/`. No RecyclerView, XML screen or
Facebook Shimmer dependency is needed: this implementation uses Compose, Coil
and a Compose skeleton animation. Manifest/FileProvider XML is included.

## Module 1: complete photo lifecycle

`PhotoPreparation.prepare` reads the `content://` stream while its grant is valid,
checks decoded dimensions/type/size, and copies the full photo to app-private
persistent storage. Camera capture uses `TakePicture` and a FileProvider URI,
not the small thumbnail from `TakePicturePreview`. The outbox owns a separate
immutable file copy so changing the draft cannot corrupt a queued upload.

`RtcApi.createPost` declares Retrofit `@Multipart` with body and an optional
`MultipartBody.Part` named **image**. The worker constructs this same field name.
Multer uses `upload.single('image')`, bounded fields/files/bytes, and Sharp decodes
and re-encodes actual image bytes rather than trusting the client MIME header.
The server stores the durable filename and dimensions with the post and returns
an authenticated absolute `imageUrl` in both feed and timeline projections.

Coil receives the repository's authenticated ImageLoader. A null URL renders no
image; an image error shows recovery UI. Remote photos reserve a stable aspect
ratio and use `ContentScale.Crop`. This presentation crop affects community/provider
photos only; it does not alter RTC's full original branding artwork. Cached post
JSON preserves the media URL and dimensions rather than dropping them on reload.

Local server media must live on a persistent volume. Multiple server replicas
must share that volume or use an equivalent durable object-store adapter; an
ephemeral container filesystem is unsuitable. `server/README.md` covers setup,
cleanup and permissions.

## Module 2: native maps and road distance

The `location` package requests foreground fine/coarse permission when the user
chooses **Use my location**, accepts approximate location, rejects stale fixes,
observes location settings and removes updates when collection stops. The
ViewModel cancels stale routing calls and debounces newer coordinates/destinations.
Distance comes from TomTom road-routing `lengthInMeters`, never straight-line
geometry masquerading as a driving distance. Missing location, coordinates,
network, API configuration or route is displayed as unavailable.

Native `MapView` is in the optional `:tomtom` module. **TomTom SDK 2.5.3 MapView
requires Extended entitlement and Maven credentials**; the actual Extended artifact
returned HTTP 401 during access verification. Core compilation excludes this
module. Enable it only with authorized access:

```bash
gradle -p android -PincludeTomTom=true :tomtom:assembleDebug
```

Supply `TOMTOM_MAVEN_USERNAME` and `TOMTOM_MAVEN_PASSWORD` through the environment,
and pass the SDK-enabled map key to `LiveMapScreen`. The routing/search key is
`TOMTOM_API_KEY` on the server. See [MAPS.md](MAPS.md) for exact dependencies,
lifecycle ownership, target binding and primary documentation.

## Module 3: keyboard, images, formatting and empty states

Report fields scroll in a weighted Column while the submit row stays outside that
scrolling region. `imePadding()` applies once to the form, with Scaffold insets
consumed by the host. On the containing Activity retain:

```xml
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="adjustResize" />
```

Merge this attribute into the existing activity declaration; keep its intent
filters and other attributes. Call `enableEdgeToEdge()` in that Activity and keep
inset handling in Compose. Do not add a second XML IME listener to this layout.

Provider images use a fixed aspect ratio with `ContentScale.Crop`. Badges live
outside the clipped image layer. Distances use one line with ellipsis. The
presentation mapper formats integer `rateCents` as ZAR using
`NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA"))`; it never
prints a binary floating-point price. Empty collections are `LoadState.Content`
and display the empty-state component. Only actual failures become error states.
Skeletons reserve the same primary image/text geometry while loading.

## Module 4: validation and protected navigation

The minimum is **20 characters**, not a 20-character maximum. Both platforms count
Unicode code points in trimmed text; the form shows minimum and maximum counters.
The submit button reflects validation and in-flight state, and field errors remain
inline. The category is required, priority has a selected checkmark, and coordinate
pairs are validated together on both client and server.

Report, post and booking ViewModels use `SavedStateHandle`. The screen mounts them
under one Activity or parent NavGraph owner, keyed by account. System back, toolbar
back and tab navigation call the same unsaved-changes guard with keep/discard
options. Blocking saves cannot be dismissed halfway through a handoff. Booking
notes/time retain their request key on retry; a changed normalized request gets a
new key. Clear the account's ViewModel owner at logout.

## Module 5: immediate state and consistent totals

Voting is a separate, minimum-48dp click target outside the content detail click
region. The repository applies the desired vote immediately through a StateFlow
overlay, serializes concurrent taps for that post and rolls the overlay back on
failure. The backend sets `{voted:true|false}` instead of toggling, so repeating an
ambiguous request cannot add another vote.

Queued posts/reports appear immediately as **Queued locally** with their payload
preview. They are distinct from API-created records. On success, one Room
transaction writes the returned server DTO and removes the queue entry; the feed
or activity list updates without manual refresh. Only actual server `createdAt`
values appear on delivered report timelines. New server reports default to PENDING.

The dashboard ring and status rows use the same summary derived from the same
`reports` StateFlow. Total equals pending + working + resolved. Total zero renders
a neutral grey ring and zero counts, with no division by zero or green success ring.

## Module 6: durable work and request identity

Every post/report is inserted into Room before delivery is attempted. WorkManager
uses connected-network constraints, exponential backoff, a durable per-account
queue and a recovery lease. A persistent submission UUID survives retries and
process recreation. A connectivity interceptor is only an advisory early failure;
IOException after an apparently healthy network also retains the saved request.

HTTP 401 waits for the same account to sign in. HTTP 408/429/5xx and network errors
retry; terminal validation/conflict errors stay visible for review. Automatic
attempts are bounded. A periodic recovery job covers interrupted scheduling or a
worker process death. Coroutine cancellation is propagated; committed media is not
deleted merely because the caller was cancelled. API requests stay bound to their
initiating account and do not follow mutation redirects. Image loaders/cache paths
are account-isolated.

Post/report/booking idempotency uses a unique database `(user_id,key)` record,
a normalized request hash, and a cached status/body committed in the **same SQL
transaction** as the operation. A duplicate transaction waits for the winner and
replays it; a changed payload returns 409. A separate unique provider/start-time
constraint blocks double booking with different keys.

Report category normalization maps App Support to APP_SUPPORT/`it_queue` and
Infrastructure to INFRASTRUCTURE/`municipal_queue`. The insertion of report and
queue entry is atomic. These are private resident reports, not community-feed posts.

## Host wiring

The Android project is a library, so it can be reviewed and compiled without
changing RTC's existing entry point:

```bash
gradle -p android testDebugUnitTest lintDebug assembleDebug
```

Use the included `SdkSessionProvider` to bridge validated sessions from the host's
auth SDK. It stores only the current in-memory access token; the existing SDK owns
refresh, persistence and sign-out. `RtcClient` owns Room, Retrofit, the repository,
media preparation and its WorkerFactory. Construct it once in the Application,
assign it before calling `start()`, and delegate WorkManager to its factory.

The following wiring function has concrete inputs from the hosting app:

```kotlin
fun createRtcClient(
    application: android.app.Application,
    deployedHttpsApiUrl: String,
    sessions: za.org.rtc.remediation.network.SdkSessionProvider,
): za.org.rtc.remediation.RtcClient =
    za.org.rtc.remediation.RtcClient(application, deployedHttpsApiUrl, sessions)
```

For RTC's existing Hilt WorkManager configuration, preserve both factories:

```kotlin
fun rtcWorkConfiguration(
    client: za.org.rtc.remediation.RtcClient,
    existingFactory: androidx.work.WorkerFactory,
): androidx.work.Configuration {
    val factories = androidx.work.DelegatingWorkerFactory().apply {
        addFactory(client.workerFactory)
        addFactory(existingFactory)
    }
    return androidx.work.Configuration.Builder().setWorkerFactory(factories).build()
}
```

Return that configuration from the existing `Configuration.Provider`. Preserve
RTC's existing removal of the default WorkManager initializer. Call `client.start()`
once from `Application.onCreate()` after assigning `client`; this prevents a
WorkManager/client initialization cycle. Forward each authenticated SDK session
with `sessions.updateFromSdk(userId, accessToken)` and call `sessions.clear()` on
sign-out. Never pass a Supabase service-role key into Android.

Mount `RtcRemediationScreen` under the chosen Activity/NavGraph owner. Its required
arguments include repository, authenticated account ID, `client.photos::prepare`,
exit handling and a real provider-directions destination. Pass
`client.photos::cameraDestination` for full-resolution camera capture. The supplied
FileProvider XML grants only the private `rtc-outbox-media` directory.

The host must wire its actual incident/provider map destination and report-location
selection to the included `LocationViewModel`; no fictitious navigation callback
or placeholder location is supplied. Existing Supabase sessions are compatible
with the server only when their configured issuer/audience/JWKS use the supported
RS256/ES256 signature algorithms. Do not bypass verification for legacy tokens.

## Verification and remaining acceptance

The Node suite runs real Express HTTP, signed test JWTs, Sharp decoding and SQL
constraints against PGlite locally. With `TEST_DATABASE_URL`, the same tests use
native PostgreSQL pooled connections; CI supplies PostgreSQL 17 for concurrency
validation. TomTom upstream-response fixtures are explicitly mocked and do not
constitute a live TomTom test. Android tests cover presentation invariants,
location freshness/distance and retry classification; Kotlin compilation and lint
run independently of the restricted native TomTom artifact.

Before declaring the app production-ready, complete the Express/Supabase data and
permission mapping, supply hosting/identity/TomTom configuration, compile the native
SDK, then test camera/gallery uploads, keyboard at large text, rotation, tab drafts,
account switching, app-kill during upload, offline recovery and actual route distances
on a device. The package contains no production data migration or live deployment.

## Source references

- [Express Multer: multipart fields, limits and errors](https://expressjs.com/en/resources/middleware/multer/)
- [Android: offline-first data layer and persistent work](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Android: WorkManager constraints and backoff](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Android: Compose window insets](https://developer.android.com/develop/ui/compose/system/insets-ui)
- [TomTom: Standard renderer and Extended MapView](https://docs.tomtom.com/navigation/android/getting-started/low-end-configuration)
