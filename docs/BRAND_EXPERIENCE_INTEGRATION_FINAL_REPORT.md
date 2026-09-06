# Brand & Experience Integration — Final Report

## A. Baseline

| Item | Evidence |
|---|---|
| Starting `main` | `3d2c64c39cffb8384313cb7d33d425b9d3018af7` |
| Restart integration head | `cab1a0d` |
| Frozen donor checkpoint | `9d0161858b850cc056b9dd99f0e889508a0aaf12` |
| Integration branch | `integration/brand-experience-v2` |
| Pull request | `#17` — `feat(branding): integrate production Brand & Experience on current main` |
| Verified implementation head | `b9cd0aca0c1935396ab1989fbac29ef332af5739` |
| Implementation-head workflow | `Android Production Verification` run `#381`, run ID `33215677725` |

The implementation was semantically ported onto the Marketplace-, Community-, and Security-enabled `main`. No historical Brand branch was merged wholesale.

## B. PORT / DROP / PARK

### PORT

- Bounded schema-v2 appearance, typography, Welcome, Home, screen-presentation, and launcher configuration.
- Strict configuration decoding, validation, compiled defaults, schema-v1 conversion, and last-known-good fallback.
- Published Welcome copy and finite launch treatments while retaining compiled authentication behavior.
- Validated Home section ordering plus one bounded, protected image widget.
- Administration-owned Draft → Preview → Save Draft → Publish → History → Revert workflow.
- Centralized `ADMIN_BRANDING` navigation with the existing protected-route and MFA boundary.
- Finite packaged launcher aliases targeting the existing single `MainActivity`.
- Expiring signed Home asset URLs with renewal and retry behavior.
- Canonical source parity for deployed Non-Production Brand migrations.

### DROP

- A second Brand Activity or parallel navigation architecture.
- Donor changes that would overwrite current Marketplace, Community, CI, or security ownership.
- Arbitrary runtime composables, scripts, remote navigation actions, executable URLs, fonts, or class names.
- Arbitrary uploaded native launcher icons; only packaged aliases remain supported.
- Stale donor migration versions that do not match deployed Non-Production history.
- Obsolete lexical regression assertions for fixed Welcome copy, removed Google Sign-In, and direct Home section iteration.

### PARK

- Publishing a live global Brand configuration; Non-Production currently has no published or draft configuration row.
- Real device/emulator execution, accessibility validation, and full authentication/Brand workflow QA.
- Signed production APK/AAB assembly and signing-certificate evidence.
- Production Supabase promotion, which remains unauthorized.
- Repository `main` protection/rules, which remain unconfigured.

## C. Architecture

### Configuration model and runtime

`GlobalUiConfiguration` schema v2 contains finite enums and bounded data classes. Unknown fields are rejected, values are validated, unsupported versions fall back safely, and schema v1 is converted through an explicit compatibility path. `RtcConfiguredAppRoot` loads the published configuration and applies the last-known-good or compiled default when the network or payload is unavailable.

### Theme and presentation

Validated Brand seeds, packaged font-family choices, bounded typography scaling, shape presets, and density presets are converted into compiled Material 3 presentation. Configuration cannot instantiate arbitrary Material objects, code, fonts, or classes.

### Welcome and authentication

`PublicWelcomeScreen` reads the published Welcome model for approved presentation fields. CREATE, SIGN_IN, email sign-up, email sign-in, confirmation validation, password recovery, Back, and canonical Google credential callbacks remain compiled application behavior and cannot be replaced by remote commands.

### Home

`HomeLayout.validatedOrDefault` resolves invalid layouts before `renderItems()` emits a deterministic sequence containing only `HomeRenderItem.Section(HomeSection)` and the bounded `HomeRenderItem.Image(HomeImageWidget)`. The renderer contains no arbitrary external action, script, navigation, or composable DSL.

### Navigation and ownership

`MainActivity` remains the only `ComponentActivity` and is 85 lines. `ADMIN_BRANDING` is defined in canonical navigation and evaluated through `RouteAccessPolicy` and `ProtectedRoute`. Branding remains under `feature/administration/branding`; Marketplace and Community files/state owners are unchanged.

### Launcher

The manifest exposes only `.LauncherDefault`, `.LauncherGold`, `.LauncherEmerald`, and `.LauncherMonochrome`, all targeting `.MainActivity`. Only `.LauncherDefault` is initially enabled.

### Supabase boundary

UI configuration tables are RLS-enabled and RPC-only. Privileged draft, history, asset, publish, and revert operations use server-side System Administrator checks; the shared `private.access_assert_system_admin()` requires a trusted Auth user, the `SYSTEM_ADMIN` database role, and AAL2. The effective global read RPC is intentionally callable by anon/authenticated contexts and returns only the published effective configuration.

## D. Defects Found and Resolved

1. **Stale Welcome contract:** fixed-copy assertions and an obsolete prohibition on Google Sign-In conflicted with bounded published copy and the canonical Google path. The contract now validates configurable presentation and compiled authentication behavior.
2. **Stale Home renderer contract:** a literal `resolvedLayout.sections` assertion conflicted with the bounded image compiler. The contract now validates fallback, the finite render-item catalogue, and absence of executable remote behavior.
3. **Brand migration source drift:** three schema-v2 migrations existed in deployed Non-Production history but not current source. Canonical files `20260827223815`, `20260827231359`, and `20260827231722` now match deployed history; stale donor timestamps are prohibited by contract.
4. **Brand Storage policy failure:** authenticated planning failed with `permission denied for table ui_configuration_versions` because Storage RLS queried RPC-only tables directly. Migration `20260828134946_repair_ui_configuration_storage_policy_boundary.sql` replaces those subqueries with private, fixed-search-path, authenticated-only `SECURITY DEFINER` predicates without granting table SELECT.
5. **Cross-policy Storage planner failure:** after the Brand boundary was repaired, the planner reached the unrelated feedback policy and failed on revoked `private.is_content_authority()`. The same forward repair preserves the equivalent role check through the already-authorized `private.has_any_role(...)` predicate.
6. **Brand Storage bucket boundary:** migration `20260828214157_scope_ui_configuration_storage_policies_to_bucket.sql` adds an explicit `bucket_id = 'rtc-ui-assets'` guard to every Brand object policy, preventing the Brand helpers from authorizing any other Storage bucket.
7. **Draft-operation race:** the editor now serializes protected save, publish, and restore operations. Preview, history resume/restore, image upload/removal, and all configuration edits are rejected while a mutation is active; controls are visibly disabled. Publish completion retains the captured saved snapshot if refreshing the effective configuration fails.

## E. Tests

### Local source verification

- Command: `python3 tools/tests/run_contract_tests.py`
- Result: `148 / 148` passed.
- Formatting check: `git diff --check` passed.
- Local Deno/Gradle execution was unavailable because this workspace has neither Deno nor a Gradle launcher/system Gradle; GitHub Actions supplied both authoritative environments.

### Exact implementation-head CI

| Gate | Result |
|---|---|
| Source contracts | PASS — `148 / 148` |
| Shared Deno authorization | PASS |
| JVM `testDebugUnitTest` | PASS |
| Android `lintDebug` | PASS |
| Debug APK `assembleDebug` | PASS |
| Compose smoke tests | PASS — compiled, not executed on a device |
| Workflow | PASS — run `#381`, ID `33215677725`, head `b9cd0aca0c1935396ab1989fbac29ef332af5739` |

Debug verification artifact:

- Name: `rtc-community-debug-verification`
- Produced by the verified workflow; artifact identity is retained by GitHub Actions run `33215677725`.

## F. Non-Production Backend Evidence

Project: `eqwstpdjoineycrkhpht` (`RTC Community Non-Production`).

- Canonical Brand migrations are present through `20260828214157_scope_ui_configuration_storage_policies_to_bucket`.
- `ui_configuration_versions`, `ui_configuration_events`, and `ui_configuration_assets` have RLS enabled.
- `anon` and `authenticated` have no direct SELECT privilege on any of those three tables.
- Each Brand Storage policy applies only to `authenticated`, requires `bucket_id = 'rtc-ui-assets'`, and combines that constraint with its fixed-search-path private helper.
- A rollback-only authenticated/AAL2 `EXPLAIN` confirms the Brand select condition is present only for `rtc-ui-assets`; the same planner path for an unrelated bucket retains its own policies without widening the Brand helper.
- `has_table_privilege` confirmed `false` for authenticated direct SELECT on all three Brand configuration tables.
- The effective global RPC currently returns zero rows because no configuration is published; the Android runtime therefore uses its compiled safe fallback.
- Direct synthetic data mutation was not required, and no synthetic Brand rows were created.
- Security advisors continue to flag exposed-schema `SECURITY DEFINER` RPCs. For Brand these are intentional RPC boundaries: the effective read is deliberately public, and privileged functions call the shared System Administrator + AAL2 assertion. See Supabase lints [0028](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable) and [0029](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable).

## G. Release Limitations

| Release gate | Status |
|---|---|
| Emulator/device instrumentation executed | NO |
| Signed production APK produced | NO |
| Signed production AAB produced | NO |
| Complete production release secret bundle configured | NO — CI reported all seven required values absent |
| Production Supabase modified | NO |
| `main` branch protected | NO |

The signed release step was correctly skipped. A successful `assembleDebugAndroidTest` proves compilation/package generation only; it does not prove runtime instrumentation. Brand & Experience is eligible for source integration after the report-head CI and review protocol complete, but the overall application remains a production **NO-GO** until the parked external release gates are satisfied.

## Report-Head Protocol

This report records the exact implementation-head evidence above. Its commit necessarily changes the PR head. The report commit must receive a fresh full `Android Production Verification` run before PR #17 is marked ready or merged; that final exact-head disposition is recorded in the PR conversation.
