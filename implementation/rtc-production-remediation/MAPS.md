# Native maps and current location

The supplied native map uses **TomTom SDK 2.5.3**, `MapView`, and the Standard
renderer. The current TomTom documentation makes `MapView` available in the
**Extended** flavor, which requires TomTom entitlement and repository credentials.
An API key alone does not provide access to that flavor. The native module is
separated from the core Android library so the other remediation code can compile
independently. There is no WebView, static image, fake coordinate, or distance fallback.

## Dependencies and access

Use `com.tomtom.sdk.maps:map-display-standard:2.5.3` from
`https://repositories.tomtom.com/artifactory/maven`, with
`missingDimensionStrategy("tomtom-sdk-version", "extended")`. Do not include a
Premium renderer in the same app variant. The documented platform requirements
are minimum SDK 26, compile SDK 35 or higher, NDK major version 26, ARM64/x86_64
ABIs, and OpenGL ES 3.0 for Standard rendering.

For location, use `com.google.android.gms:play-services-location:21.4.0` and the
manifest permissions `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`. The
library does not request background location. The hosting Activity supplies its
TomTom map-display key to `LiveMapScreen`; the Node server's routing key stays on
the server. Use distinct appropriately restricted keys for these two purposes.

The public Maven POM and Gradle metadata for `map-display-standard:2.5.3` returned
HTTP 200 during verification. The metadata identifies
`map-display-standard-android-extended:2.5.3` as its Extended implementation.
Requests for that implementation's POM and AAR returned **HTTP 401** without
repository credentials. Native SDK compilation and device rendering therefore
remain a required integration gate after entitlement is supplied.

## Connect an actual destination

Create `LocationViewModel` with `LocationViewModel.Factory(LiveLocationSource(context), api)`.
Scope this ViewModel to the relevant Navigation entry. Pass a `MapTarget` created
from an incident's or provider's stored ID, title and coordinates into
`LiveMapScreen(apiKey, target, viewModel)`. If latitude or longitude is absent,
pass `null`; never substitute another town or a hard-coded coordinate.

`LocationControls` requests both foreground permissions together after the user
presses **Use my location**, supports approximate-only access, and rechecks after
returning from Settings. `LiveLocationSource` emits explicit permission, device
settings, searching, unavailable and fresh-fix states. It rejects fixes older than
60 seconds using monotonic elapsed time, expires an old fix even when callbacks
stop, and removes location updates and its provider receiver when collection ends.
`collectAsStateWithLifecycle` plus zero-timeout `WhileSubscribed` stops collection
when the screen is no longer visible.

`LocationViewModel` calls the authenticated `RtcApi.route` endpoint. A newer
destination/fix cancels the previous Retrofit request. A 750 ms debounce and
coordinate/time comparison reduce repeated requests. It displays the returned
road distance with units and an approximate-origin label where relevant. A denied
permission, disabled location, offline connection, stale fix or missing route
produces an unavailable state; none is rendered as zero kilometres.

`RtcTomTomMap` owns its native `MapView` through a lifecycle observer. It forwards
create/start/resume/pause/stop/destroy, saves the SDK Bundle through Compose's
saveable state, cancels map initialization on disposal, and reconstructs destination
and origin markers from current application data after restoration. A new
destination recenters the map; regular location updates preserve manual pan/zoom.
The SDK's own attribution remains visible.

The UI shows fresh distance/duration data; the supplied contract does not return
route geometry, so this module does not claim to render a turn-by-turn route line.
`GET /locations/search` and `RtcApi.search` provide live POI results for a search
screen or destination selector.

## Verification still requiring a device and keys

1. Resolve and compile the native Extended module with authorized Maven credentials.
2. Supply an SDK-enabled map key and a server routing/search key; verify actual map tiles.
3. Exercise precise, approximate, denied and revoked permission; switch GPS off/on.
4. Rotate, background and reopen the screen; check camera restoration and no duplicate pins.
5. Select two real destinations rapidly and confirm only the latest road distance appears.
6. Disconnect/reconnect, verify stale location expiry, and check TalkBack at large font scale.

## Primary documentation

- [TomTom project setup and flavor requirements](https://docs.tomtom.com/navigation/android/getting-started/project-setup)
- [TomTom Standard renderer and current MapView dependency](https://docs.tomtom.com/navigation/android/getting-started/low-end-configuration)
- [TomTom 2.5.3 MapView lifecycle API](https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/2.5.3/maps/map-display-common/com.tomtom.sdk.map.display.ui/-map-view/index.html)
- [TomTom 2.5.3 MapOptions](https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/2.5.3/maps/map-display-common/com.tomtom.sdk.map.display/-map-options/index.html)
- [TomTom marker configuration](https://docs.tomtom.com/navigation/android/guides/map-display/map-display-for-views/markers)
- [Google Play services dependencies](https://developers.google.com/android/guides/setup)
- [Android foreground and approximate location permission](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [Android location update lifecycle](https://developer.android.com/develop/sensors-and-location/location/request-updates)
