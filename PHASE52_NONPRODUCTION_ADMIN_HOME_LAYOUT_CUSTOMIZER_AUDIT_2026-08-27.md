# RTC Community Non-Production Android Home Layout Customizer

## Evidence-Led Audit and Safe Implementation Plan

**Author:** Manus AI
**Date:** 27 August 2026
**Scope:** Local `recovery/nonproduction-baseline` Kotlin/Jetpack Compose source and the isolated RTC Community Supabase project only.
**Excluded:** Production Supabase, production data, production credentials, release deployment, real-user login, billing, and cloud test execution.

## Executive conclusion

The requested System Administrator Home Layout Customizer is viable in the isolated non-production application, but it does **not** currently exist as an Android route, a UI state model, a Supabase layout table, or a server-authorized RPC workflow. It is therefore a controlled feature slice, not a small visual configuration change.

The recovered application already contains the correct security foundations to build it safely: an existing `SYSTEM_ADMIN` role, client-side fail-closed route checks, a server-side `private.access_assert_system_admin()` helper that requires an authenticated actor, `SYSTEM_ADMIN`, and JWT assurance level `aal2`, plus established server-authorized, typed-confirmation, audit-recording workflows. The customizer must use those foundations rather than direct Android table writes or a generic content editor.

No supplied account credential has been used. No Firebase Test Lab, Robo crawl, screenshot capture, emulator run, provider upload, production build, production claim, or database mutation was performed in this assessment.

## What is present and what is absent

| Area | Verified current state | Consequence |
| --- | --- | --- |
| Resident Home screen | `HomeScreen` is a fixed Compose `LazyColumn`: greeting, metrics, Quick Access, optional draft/sync indicators, cases, notices, and help. | The layout renderer must be refactored to resolve a **validated, finite section order** against the current fixed components. |
| Administrator workspace | `AdminWorkspace` provides MFA-aware administrator tools but no Layout Customizer tile, route, screen, or action. | Add one protected route and navigation entry only after a server-authorized capability exists. |
| Route policy | `RouteAccessPolicy` already distinguishes staff and administrator routes. For live Supabase sessions, administrator routes require verified MFA. | Add the customizer route to `adminRoutes`; do not create a parallel or weaker access check. |
| Supabase adapter | `ProductionUxRepository` uses protected RPCs for role changes, privacy analytics, operations, notices, and alerts. It has no layout fetch, draft, publish, history, or rollback method. | Add typed layout RPC wrappers only after the backend contract is defined and source-controlled. |
| Database schema | The isolated public schema has roles, RLS, audit events, operations controls, and generic `app_content`; it has no layout/version/audience/revision table. | Create dedicated layout-version and immutable-event tables instead of reusing an unconstrained generic store. |
| Generic `app_content` access | It permits direct inserts, updates, and deletes for Content Editors and System Administrators. It has JSON payload and timestamps but lacks audience, schema version, reviewer, confirmation, and rollback linkage. | It is unsuitable for system-wide Home releases. The new data must deny direct mutation from Android clients. |
| Existing server-side admin guard | `private.access_assert_system_admin()` checks signed-in identity, the administrator role, and `aal2`. | Every draft mutation, publication, and reversion RPC must call the helper as a server-side authorization boundary. |
| Current tests | The project has one local `RtcMathTest`; onboarding and Resident A tests are Android instrumentation tests that compile but have not run on a device. | Build and source contracts are useful, but device behavior and visual fidelity remain unverified. |

## Security reconciliation of the supplied specification

The specification’s embedded account credential and cloud test instruction will not be executed. Firebase’s own Test Lab guidance says custom sign-in credentials must be non-real test accounts only. A Robo crawl may generate logs, annotated screenshots, and video, but those artifacts do not replace targeted assertions or a security review. [1]

The proposed customizer must not load arbitrary Compose code, scripts, layouts, external executable links, database queries, or URLs. A remote configuration can select only **precompiled, named widgets and internal routes that are already shipped in the APK**. This prevents an administrator, an AI proposal, or a compromised payload from changing application behavior outside the reviewed native client.

The isolated Supabase security advisor currently reports the expected warning category for existing authenticated `SECURITY DEFINER` RPCs. That warning is not a license to grant broad permissions: each new customizer RPC must retain a pinned `search_path`, call the administrator assertion helper, validate input bounds, write an audit event, revoke access from `PUBLIC`, and grant execute only to `authenticated`. The customizer tables themselves must reject all direct client mutation.

## Target architecture

| Layer | Safe design | Explicitly prohibited |
| --- | --- | --- |
| Android client | A deterministic `HomeLayoutResolver` maps a server-returned, validated section identifier to an already compiled composable. Unknown, duplicate, unsupported, or malformed sections resolve to the current safe default layout. | Downloaded Compose code, reflection-based screen loading, free-form routes, raw URL handling, direct table mutation. |
| Configuration payload | Versioned JSON with a top-level schema version, a finite ordered array of section IDs, and only allowlisted flags. The first version should be capped at 3–8 sections, reject duplicates, and include mandatory support/help reachability. | Arbitrary JSON interpreted as UI behavior, raw SQL, HTML, JavaScript, class names, or unbounded custom fields. |
| Supported Home sections | `WELCOME`, `COMMUNITY_SNAPSHOT`, `QUICK_ACCESS`, `CONTINUE_DRAFT`, `PENDING_SYNC`, `NEXT_STEPS`, `LATEST_UPDATES`, and `HELP`. The server may only arrange or hide non-mandatory entries from this exact catalogue. | Removing core safety/account access, creating a new screen in configuration, or pointing to a route not compiled into the APK. |
| Actions and navigation | Each widget action is an enum resolved locally to an existing internal destination such as Support, Projects, Centres, Opportunities, Notices, Community, or Help. | Arbitrary deep links, free-form external links, or remote route strings. |
| Supabase data | A dedicated version table plus an append-only layout event table. The data model records owner, reviewer, publisher, schema version, payload fingerprint, reason, timestamps, predecessor revision, lifecycle state, and audience key. | Reusing `app_content` direct-write policies, client-side audit records, editable audit history, or storing secrets in configuration. |
| Publish authority | A `SECURITY DEFINER` RPC obtains the actor with `private.access_assert_system_admin()`, validates the draft, requires a typed confirmation, atomically supersedes the prior active version for the same audience, records the audit event, and returns the published revision ID. | Client-controlled role checks, disabled MFA, direct `update`/`insert`, blanket grants, or a service-role key in Android. |
| Client reads | Resident clients can read only the one effective, published, validated configuration applicable to them. The cached value expires safely and the application retains the fixed default layout on error. | Draft visibility to residents, sensitive segmentation data in payloads, or a blank/unusable Home screen during an outage. |
| Development adapter | A synthetic System Administrator may open a fixture-only preview and exercise the validator. Actual draft, publish, revert, and audit mutations remain disabled without a real isolated Supabase authentication session and MFA. | Allowing a debug extra to create a real server session, elevate authority, bypass MFA, or write remote configuration. |

## Recommended non-production schema and lifecycle

The source-controlled migration should introduce `home_layout_versions` and `home_layout_events`. The version record should include an opaque UUID, `audience_key`, `state`, `schema_version`, a bounded `jsonb` payload, a server-computed payload hash, `based_on_version_id`, creator/reviewer/publisher identifiers, reasoning fields with length checks, and timestamps. The event record should be append-only and carry the actor, action, version, prior-version reference, server-generated timestamp, and safe metadata. Both tables should enable RLS and use deny-by-default direct-mutation policies.

The lifecycle should be **Draft → Submitted → Approved → Published → Superseded**, with **Rejected** and **Reverted** as terminal event outcomes. Draft creation and editing require an MFA-verified System Administrator. Publishing requires the same privilege plus typed confirmation and a reason. The first non-production implementation should retain dual-administrator approval as a supported policy flag but should not silently assume a two-person workflow for every draft; the approval threshold needs an explicit product decision before it is enabled.

Resident reads should use a narrow `get_effective_home_layout()` contract that returns one published payload and its version metadata only. An administrator history contract should return bounded, redacted revision metadata and can return the payload only for a specific authorized revision. A revert must create a **new published revision based on an earlier payload**; it must never mutate historical rows.

## Initial audience recommendation

The first implementation should publish **one global resident Home layout**. It produces a clear audit trail, has a simple rollback model, and avoids sensitive or opaque profiling. If segmented layouts are needed later, add only documented, non-sensitive audience keys with an explicit policy—for example, a static application role class—not inferred location, demographics, health, case content, or browsing behavior. The selected segment must be shown in the administrator preview, publish confirmation, audit record, and resident read decision.

| Option | User impact | Security and delivery implications |
| --- | --- | --- |
| **A. Global-only initial release — recommended** | One approved Home layout applies to every resident. | Lowest complexity; one active version; straightforward rollback; no profiling or audience-resolution logic. |
| **B. Segmented release** | Different approved layouts apply to defined resident audiences. | Requires an approved audience taxonomy, deterministic server-side membership rules, per-audience fallback, preview disclosure, overlap precedence, and substantially more RLS and test coverage. |

## Native implementation sequence

1. Add sealed/domain types for layout version, lifecycle, supported section, and safe audience key. Keep the current fixed section sequence as `HomeLayout.default()`.
2. Extract the existing Home entries into a deterministic renderer that accepts only a validated `HomeLayout`; keep visual components and connected navigation callbacks intact.
3. Add `LAYOUT_CUSTOMIZER` to `RtcRoute`, `routeTitle`, `RouteAccessPolicy.adminRoutes`, the NavHost, and one MFA-aware System Administrator workspace tile.
4. Implement a customizer screen with a finite section palette, visible order, a resident preview, explicit current/published/draft states, reason and typed-confirmation fields, bounded history, and safe return paths. The view must not imply that a save occurred until the server confirms it.
5. Add source-controlled Supabase migration and RLS/RPC contracts. Do not apply the migration until the final contract is reviewed and explicitly authorized for the isolated non-production project.
6. Add repository and view-model methods for effective-layout fetch, administrator draft operations, publish, history, and revert. Disable mutating controls in `DEVELOPMENT_ADAPTER` while allowing local fixture preview.
7. Extend the source-level regression contracts for every new route, role/MFA gate, allowed section, payload fallback, direct-write denial, and absence of arbitrary URL/code behavior.

## QA and test strategy

Robolectric is an appropriate candidate for **fast local behavior checks** once the Home renderer and state are extracted from the large activity. Android’s current guidance says Robolectric can run Compose UI tests from the local `test` source set, but recommends it primarily for behavior rather than pixel-perfect assertions; it cannot replace device tests for features such as system UI and edge-to-edge behavior. [2]

I will not add the older dependency versions from the supplied specification without a compatibility check against the current Kotlin 2.3.0, AGP 8.13.2, and Compose BOM configuration. The existing dependency catalog has no Robolectric or screenshot framework. A safe next step is to first add low-risk pure Kotlin validator tests; then, if needed, introduce Robolectric with Android resources enabled and one isolated Compose behavior test. Screenshot testing should remain advisory only until it is compared against a real Android device capture.

| Test layer | Planned proof | Current status |
| --- | --- | --- |
| Pure local unit tests | Payload validator, default fallback, duplicate/unknown section rejection, audience selection, revision state transitions. | Not yet implemented. |
| Local Compose/Robolectric behavior | Home resolver displays the selected compiled sections and maintains valid internal navigation semantics. | Not configured; no dependency change has been made. |
| Android instrumentation | Protected route denial, real isolated System Administrator + MFA gate, customizer preview, publish/revert confirmation, default fallback. | Tests compile only; no device target is attached. |
| Isolated Firebase Test Lab | Targeted scripted synthetic account journey and Robo crawl artifacts for regression investigation. | Not authorized or configured; no account/password will be supplied to a run. |
| Native visual verification | Compare the resulting device screenshot with the agreed graphite/emerald/gold reference treatment. | Not executed. The separate React review console remains a synthetic aid only and is not evidence of native output. |

Firebase’s Robo test can systematically explore the UI and create log, screenshot, and video evidence, but the official guidance recommends at least 300 seconds for moderately complex apps. A three-minute run can be a narrow smoke check, not a thorough certification. [1] The isolated test route will therefore require a purpose-created synthetic non-production account or a synthetic debug entry flow and an explicitly authorized Google Cloud/Firebase test project before any cloud matrix is scheduled.

## Evidence boundary

The local recovery work has previously passed source contracts, debug compilation, unit tests, lint, and debug assembly, but those results predate this proposed feature and must be rerun after any source change. The assessment did not modify Kotlin, Gradle, SQL migrations, RLS, Supabase records, or device-cloud resources. It does **not** establish that the application is production-ready or device-validated.

## Decision needed before feature implementation

Choose the initial audience scope: **A — one global Home layout for every resident**, or **B — segmented resident layouts**, with the understanding that B requires a separately approved, privacy-safe audience taxonomy and additional server-side resolution/RLS tests.

## References

[1]: https://firebase.google.com/docs/test-lab/android/robo-ux-test "Firebase Test Lab: Run a Robo test (Android)"
[2]: https://developer.android.com/training/testing/local-tests/robolectric "Android Developers: Robolectric strategies"
[3]: https://developer.android.com/develop/ui/compose/testing "Android Developers: Test your Compose layout"
[4]: https://firebase.google.com/docs/test-lab/android/run-robo-scripts "Firebase Test Lab: Run a Robo script (Android)"
