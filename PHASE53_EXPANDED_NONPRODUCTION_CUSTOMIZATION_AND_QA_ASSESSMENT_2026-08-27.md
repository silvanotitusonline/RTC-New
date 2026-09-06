# RTC Community: Expanded Non-Production Customization and QA Assessment

**Author:** Manus AI
**Date:** 27 August 2026
**Scope:** The local recovered Kotlin/Jetpack Compose source and the isolated RTC Community non-production Supabase project only.
**Not performed:** Production access, production schema/data change, real-user sign-in, use of supplied credentials, Firebase upload, Firebase Test Lab execution, browser confirmation action, release build, deployment, or source-code feature modification.

## Executive conclusion

The expanded specification combines three distinct efforts: local Android QA, remote administrator configuration, and device-launcher behavior. They cannot safely be implemented as one unrestricted “customize the entire app remotely” feature. The current native application has a fixed graphite/emerald/gold Compose theme, resident-owned light/dark preference, a fixed Home section sequence, one packaged launcher icon, and a mature System Administrator/MFA/RPC security pattern. It has **no** remote theme system, typography configuration, onboarding content contract, launcher aliases, or Home-layout publication lifecycle.

The safe non-production implementation is a **constrained configuration catalogue**: published administrators may choose from compiled theme presets, bounded text-scale presets, approved non-security onboarding copy slots, a finite Home section order, and precompiled internal destinations. All global settings must be server-authorized, versioned, validated, auditable, and cached locally as a last-known-good read model. The first implementation must exclude arbitrary code, URLs, downloaded fonts/assets, uncontrolled colour pickers, silent launcher changes, and arbitrary UI definitions.

## Specification reconciliation

| Requested item | Assessment | Safe non-production interpretation |
| --- | --- | --- |
| Compile a debug APK | **Eligible when source work begins.** The recovered workspace has no usable `./gradlew`; it uses the restored Gradle 8.13 installation with the existing isolated debug runtime configuration. | Re-run the existing debug compile/build checks after meaningful source changes. Do not represent a build as device validation. |
| Firebase Test Lab browser upload, Robo crawl, account sign-in, downloads | **Not authorized or configured.** The supplied credential is sensitive and will not be used. The earlier Appetize upload permission does not authorize a Firebase upload or Google account action. | A future run needs a purpose-created isolated test account, an explicitly authorized Firebase/Google Cloud project, user confirmation before the upload/start action, and exactly one virtual Pixel 8/API 34 target with a 180-second timeout if the user still chooses that smoke-test envelope. Firebase requires non-real test credentials for custom sign-in. [1] |
| Terminal `gcloud` fallback | **Unavailable and not authorized.** No `gcloud`, Google Cloud/Firebase credentials, or billing/project authorization is configured. | Do not create an account, start a trial, enable billing, or configure a connector. |
| Diagnose all issues from Test Lab artifacts | **Blocked by absence of artifacts.** | Preserve the evidence boundary: local compilation/tests, static source review, existing historic Appetize observations, and future isolated device evidence are separate result classes. |
| Remove every duplicate/legacy XML item | **Requires targeted audit, not blanket deletion.** The application is Compose-led; there is no `res/layout` directory, and `strings.xml` currently only contains the application name. | Consolidate only demonstrated duplicate components or duplicate side effects, covered by tests. Do not delete manifest, theme, XML safety, or resource entries merely because they are XML. |
| Fix crashes/leaks/timeouts from logs | **Evidence not supplied by this specification.** | Fix only reproducible source/static issues or evidence-backed runtime failures. Do not claim all crash, Room, or network issues are resolved without logs or a device target. |
| Full free-form remote colour picker | **Too broad for the first secure release.** A free-form palette can produce inaccessible contrast, conflict with the agreed RTC visual identity, and change safety/error semantics. | Start with versioned, prevalidated, contrast-reviewed presets: `RTC_GRAPHITE_EMERALD_GOLD` as the default plus a small compile-time catalogue of approved variants. A later custom palette must validate every token pair, lock system error/success colours, preserve minimum contrast, and remain a non-production reviewed capability. |
| Global typography/font controls | **Partly feasible, but must not override accessibility or download arbitrary font files.** Current typography is a single compiled Material 3 system. | Offer bounded visual-scale presets around the baseline while respecting the device accessibility font scale. Offer only fonts packaged in the APK; no remote font URL, class name, or asset upload. |
| Splash screen editor | **Native platform limitation.** Android displays the splash before the app can authenticate or fetch a server configuration. The current splash background/logo are packaged Android resources. | The system splash background/icon remain build-time assets. A published configuration may alter only the post-launch welcome treatment after validated fetch; it cannot change the operating-system splash for an already installed app. |
| Welcome/onboarding copy editor | **Feasible only as curated content.** Current screen copy is inline Compose and controls secure account creation/sign-in flows. | Permit only bounded, localised marketing/call-to-action slots with fallback defaults. Authentication labels, password rules, error states, consent wording, and recovery paths remain fixed application security copy. |
| Remote launcher icon switcher via aliases | **Not suitable as an administrator-controlled global action.** The manifest currently has one launcher activity and no aliases. Android aliases and icon resources must be declared in the installed manifest; an alias targets an activity declared before it. [2] | If enabled later, precompile a small icon set and make switching a **resident-owned, device-local opt-in preference**. An administrator can publish a visual recommendation but must not silently alter launcher components across residents’ devices. |
| Drag/drop Home editor | **Feasible within a constrained catalogue.** The current Home is a fixed Compose `LazyColumn` with connected cases, notices, directories, drafts, and help actions. | Support reordering and visibility of precompiled Home sections only. Use internal destination enums rather than arbitrary URLs. Require a resident preview, validation, typed confirmation, audit event, version history, and immutable rollback. |
| Save global settings in Room/DataStore and/or remote JSON; reflect instantly app-wide | **Must distinguish authority from cache.** Existing Room stores local drafts/media outbox; DataStore stores resident-owned theme and reading preferences. Neither is a safe global authority. | Supabase is the authoritative versioned configuration source. DataStore caches the last valid effective configuration per device. Clients fetch at launch, foreground/refresh, and on an explicit refresh; offline clients keep last-known-good/default configuration. “Instant” cannot be guaranteed on offline devices. |
| Clean release build and production-ready declaration | **Out of scope and unsupported by evidence.** The current build deliberately fails closed for release runtime configuration and only debug configuration is isolated. | Do not run or label a production release. Use debug compilation and local test evidence only until a separate release authorization and device evidence exist. |

## Current native baseline

| Surface | Read-only finding | Implication |
| --- | --- | --- |
| Material theme | `RtcCommunityTheme` exposes fixed dark/light `ColorScheme` objects and a fixed `Typography`; `ThemePreference` is `LIGHT`, `DARK`, or `SYSTEM`. | Build a second, validated **brand appearance** layer; do not replace the resident’s personal dark/light choice. |
| Design system | The theme uses a reference-driven graphite `#0C1013`, emerald `#2EC27E`, and civic gold `#D4AF37`, plus central touch, spacing, and shape tokens. The shared minimum touch target is 48dp. | Preserve current accessibility/spacing tokens as non-configurable safety rails. |
| Local persistence | `UserPreferencesStore` is device-local DataStore for personal theme/reading mode. Room holds only local drafts and media outbox rows. | Neither should publish app-wide appearance or layout. Add a separate last-known-good cache only as a secondary read model. |
| Home | `HomeScreen` renders fixed sections: welcome, snapshot, quick access, optional draft/sync, next steps, updates, help. | Extract a deterministic renderer selected by an allowlisted, validated Home configuration. |
| Welcome/auth | The Compose welcome screen is currently a fixed brand lockup and contains sign-in/sign-up/recovery flows. | Remote content can change limited non-security copy only; state logic and secure controls remain compiled. |
| Android manifest | One launcher activity references one `ic_launcher`/`ic_launcher_round`; no activity alias is present. | A launcher variant requires a binary change with new precompiled resources and aliases; remote config cannot supply an icon. |
| Administration | The existing System Administrator flow is protected by `RouteAccessPolicy`, and the Supabase assertion helper checks authenticated identity, `SYSTEM_ADMIN`, and JWT assurance `aal2`. | Add a customizer only as another protected route and server-authorized lifecycle. |
| Backend configuration | No dedicated layout-version or remote-appearance table/RPC exists. The generic `app_content` table permits direct writes by Content Editors/System Administrators and lacks version/audience/reviewer/confirmation fields. | Create dedicated configuration tables and privileged RPCs; do not use `app_content` direct writes. |

## Constrained customization architecture

The remote configuration must contain **data, not executable behavior**. The Android app resolves each configuration value against a sealed Kotlin catalogue. Unknown values, malformed payloads, duplicate sections, unsafe destinations, invalid versioning, failed fetches, or invalid contrast checks produce the immutable default RTC appearance/layout rather than an empty or broken interface.

| Configuration domain | Initial approved catalogue | Non-negotiable protections |
| --- | --- | --- |
| Brand appearance | Prevalidated named presets only. The default remains `RTC_GRAPHITE_EMERALD_GOLD`. | Fixed error/success/safety semantic colours; no arbitrary hex/HSV entry in the first implementation; accessibility validation before a preset can publish. |
| Typography | Named scale presets, with device accessibility scaling retained. | No remote font files/URLs; no scale that breaks 48dp controls or truncates essential UI. |
| Home structure | Existing compiled sections with mandatory Help/Support reachability; validated order and visibility. | Section ID enum, maximum count, duplicate rejection, required fallback Home layout, internal destination enum only. |
| Welcome content | Bounded headline/supporting-text/CTA-copy slots with default strings and length limits. | No changes to password policy, authentication error text, consent/policy wording, secure navigation, or account recovery behavior. |
| Logo and launcher | Compile-time assets only. | No image upload, remote URL, or global alias toggle. Launcher choice is only a resident device-local opt-in if introduced later. |
| AI assistance | It may formulate a visible draft proposal from approved catalogue choices. | Cannot write configuration, publish, bypass MFA, or create a free-form URL/code value. |

The backend should use a dedicated `ui_configuration_versions` record and append-only `ui_configuration_events` table. Draft/edit/publish/revert operations must call `private.access_assert_system_admin()`, validate the payload on the server, retain revision links, require a reason, require typed confirmation for publishing/reversion, and record a structured audit event. Direct client `INSERT`, `UPDATE`, and `DELETE` must be denied. The resident-facing effective-configuration RPC should return exactly one validated published configuration and version metadata for the selected audience.

The initial audience should remain **global resident-only**, as proposed in Phase 52. Segmentation must be separately approved and confined to explicit, non-sensitive, deterministic policy keys; never inferred locality, cases, health, identity, analytics, or behaviour.

## QA strategy

The first source changes should create pure Kotlin tests for the configuration validator, default fallback, supported appearance presets, typography bounds, mandatory Home reachability, invalid payload rejection, and lifecycle transitions. The current codebase has one local math unit test and Android instrumentation tests for onboarding/Resident A that compile but have not executed on a device.

Robolectric is a suitable candidate for fast local Compose behavior checks after the Home renderer is extracted. Android’s current guidance says it can run UI/Compose tests in the local JVM source set, but it is not a device replacement and has lower fidelity for screenshots and system UI. [3] A compatible dependency must be selected and proven against the recovered Kotlin 2.3.0, AGP 8.13.2, and Compose BOM project before it is introduced; the supplied older dependency pins must not be copied blindly.

For any later Firebase smoke test, the narrow 180-second / virtual Pixel 8 API 34 configuration is accepted as an explicit **smoke-test budget**, not a claim of comprehensive coverage. Firebase documents that moderately complex Robo crawls commonly warrant 300 seconds and that Robo artifacts help investigate crashes/UI but do not replace targeted assertions. [1] The test must use a created isolated test identity, not the account credential supplied in the attachment.

## Safe delivery sequence

1. Confirm global-only audience scope.
2. Confirm the initial catalogue: theme presets, bounded typography scale presets, Home section editor, and curated Welcome copy; defer arbitrary palette, font upload, splash editing, and launcher alias switching.
3. Prepare a source-controlled local Supabase migration and Kotlin domain/validator/renderer changes for review. Do not apply the migration to any project yet.
4. Compile and run relevant local contract/unit checks after each source change. Add Robolectric only after a version-compatible proposal is reviewed.
5. If later authorized, provision only an isolated test identity and run the single virtual-device Firebase smoke test after explicit upload/start confirmation.

## Current decision required

To keep the feature secure and implementable, confirm whether the initial non-production customizer should use the proposed **constrained catalogue only**—approved theme presets, bounded type scale, curated Welcome copy, and finite Home sections with global resident scope—while deferring arbitrary colour/URL/font/asset controls and remote launcher-icon changes.

## References

[1]: https://firebase.google.com/docs/test-lab/android/robo-ux-test "Firebase Test Lab: Run a Robo test (Android)"
[2]: https://developer.android.com/guide/topics/manifest/activity-alias-element "Android Developers: activity-alias"
[3]: https://developer.android.com/training/testing/local-tests/robolectric "Android Developers: Robolectric strategies"
[4]: https://developer.android.com/develop/ui/compose/testing "Android Developers: Test your Compose layout"
