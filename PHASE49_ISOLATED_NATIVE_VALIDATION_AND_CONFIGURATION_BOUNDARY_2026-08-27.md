# Phase 49 — Isolated Native Validation and Configuration Boundary

**Scope.** This record covers only the local `recovery/nonproduction-baseline` workspace and the test-only Android debug path. No production database, deployment, role, or configuration was changed. No new APK was uploaded, delivered, or represented as release-ready.

## 1. Summary

The original Appetize validation route established that the previously assembled debug APK launches its real native Compose interface and that the public onboarding round trip works. During the attempt to reach the authenticated resident menu, however, configuration inspection established that the uploaded artifact had not consumed the ignored non-production runtime properties. Validation on that artifact was therefore stopped and its authenticated result is excluded from all non-production test claims.

The local debug build is now fail-closed and can only resolve Supabase configuration from explicitly supplied, ignored non-production runtime properties. A debug-only intent entry has also been added so a future device automation run can open the existing synthetic **Resident A** workspace without credentials, registration, notification permission, or backend mutation. The source contracts, debug compile, instrumented-test compile, JVM test task, and Android lint task all completed successfully.

An independent Appetize Playwright runner is now also prepared locally. It controls the Android session through the provider’s ADB command channel rather than relying on the streamed canvas, so no user touch input, sign-in, or permission prompt is required during the resident navigation check. Its two test cases were discovered successfully in local test collection; actual execution remains intentionally deferred until a freshly uploaded, verified non-production debug build ID is available.

> **No production-readiness conclusion is justified by this phase.** The new instrumented resident-menu test compiled successfully, but no Android device was attached to the sandbox to execute it. The currently uploaded Appetize artifact predates the boundary repair and must not be used as evidence for the repaired configuration.

## 2. Executed Native Appetize Observations

| Check | Evidence observed in the real Android session | Outcome | Boundary interpretation |
| --- | --- | --- | --- |
| Launch | Initial black/loading state advanced to the native graphite Welcome screen. | Passed for the uploaded artifact. | This demonstrates native rendering only; it does not validate the repaired debug configuration. |
| Welcome → Create account | The Create account screen rendered the graphite onboarding form with name, email, password, confirmation, password guidance, Create account, Sign in, and Back controls. | Passed without entering data. | Safe public-screen visual and navigation observation. |
| Create account → Back | The in-app Back control restored the Welcome screen. | Passed. | No account registration or backend mutation occurred. |
| Authenticated Home | An authenticated Home screen was observed with the user dashboard, resident bottom navigation, and an app-owned notification rationale. | Observed only. | Excluded from isolated validation because the uploaded artifact was later found not to be wired to the isolated runtime properties. |
| Crash check | The exposed Appetize debug log contained no fatal exception during the observed launch and onboarding sequence. | No fatal crash observed. | This is not a complete functional test. |

## 3. Findings from the Uploaded Artifact

| Finding | Impact | Disposition |
| --- | --- | --- |
| `runtime.local.properties` contained the non-production keys but was not read by the Android Gradle build. | The assembled debug BuildConfig did not resolve to the isolated backend. | Repaired locally; the uploaded artifact is no longer a valid test target. |
| Firebase initialization reported missing default options and unsuccessful initialization. | FCM registration and push-alert delivery are unavailable in that Appetize artifact. | Remains open. No Firebase service-account material was added to the Android app. |
| Startup log reported skipped frames and long renderer durations. | Startup jank remains a performance finding. | Remains open for profiling on a valid device target. |
| Appetize streamed canvas did not provide stable programmatic access to in-app Compose controls in this browser session. | Browser-host controls could not replace real Android UI automation. | Superseded by the compiled Android instrumented test path. |

## 4. Configuration Boundary Repair

The local `app/build.gradle.kts` now reads the ignored `runtime.local.properties` only for **debug** Supabase fields. Debug configuration fails at build configuration time when either required non-production property is absent. The former source-controlled production endpoint and publishable key are no longer debug defaults. The release variant retains no implicit production fallback.

The generated debug BuildConfig was checked without printing any configuration values. It contained the expected isolated non-production project marker and did not contain the prior production project marker. The local runtime file is already excluded from Git by `.gitignore`.

## 5. Automated Resident-Menu Route

`MainActivity` now accepts one explicit extra only in a debug build: `za.org.rtc.community.DEBUG_SESSION_ROLE=RESIDENT_A`. The activity calls the pre-existing `beginDevelopmentResidentSession()` adapter only when both conditions are met. The adapter already enforces `BuildConfig.DEBUG`, creates no Supabase session, accepts no credentials, and selects no privileged role.

`DebugResidentMenuAutomationTest` launches the activity with that debug-only extra and asserts this resident journey on an Android target:

| Automated assertion | Intended native result |
| --- | --- |
| Synthetic intent launch | Resident A opens at Home. |
| Home assertion | “Welcome back, Resident A” is visible. |
| Profile action | The accessible “Open account or work queue” control is activated. |
| Account assertion | The Account screen and visible Sign out action are displayed. |

This test source has compiled successfully. It has **not** run because `adb devices` reported no attached Android target in the sandbox. It remains ready for an Android device runner or a properly automated device-cloud execution channel.

## 6. Completed Automated Checks

| Check | Result |
| --- | --- |
| Source regression contracts | **79 / 79 passed**. |
| `:app:compileDebugKotlin` | **Passed** after the debug-boundary repair and after the debug intent was added. |
| `:app:compileDebugAndroidTestKotlin` | **Passed** for the new resident-menu instrumentation test. |
| `:app:testDebugUnitTest` | **Passed**. |
| `:app:lintDebug` | **Passed**; report generated locally under `app/build/reports/`. |
| Working-tree hygiene | `git diff --check` passed; no values from the ignored runtime file were added to source control. |
| Appetize automation test discovery | **Passed**; two autonomous resident navigation tests were collected without starting a device session. |

## 7. Not Yet Verified End-to-End

The following flows have not been certified in this phase and must not be reported as working end-to-end:

| Flow | Why it remains unverified |
| --- | --- |
| Actual execution of the resident Account-menu instrumented test | No Android device target is attached to the sandbox. |
| Actual execution of the autonomous Appetize resident-menu suite | The only currently uploaded Appetize build predates the verified non-production configuration repair. |
| Community post creation, comments, Likes, and shares | These require a valid isolated authenticated device session and authoritative server response. |
| One-time guideline acceptance followed by posting/commenting | Requires the same valid isolated authenticated device run. |
| Authoritative Community image/video rendering and video mute/unmute | Requires safely uploaded, authoritative isolated content. |
| Profile photo picker, upload, terminal result, and avatar-projection refresh | Requires a valid isolated authenticated device run and a selected local image. |
| FCM/in-app alert registration and notification display | Firebase client configuration is absent in the observed artifact; no service-account credential belongs in the device app. |
| Staff/admin protected routes, MFA, access-management changes, analytics, and operations actions | These are intentionally protected and need dedicated synthetic role/device tests. |

## 8. Current Disposition

The uploaded Appetize artifact is retained as an observation-only record and is not used for further validation. The local source now has an enforceable non-production debug configuration path and a repeatable test entry to resident Account navigation. A replacement test artifact has **not** been assembled or uploaded, because the user requested no further APK delivery until credible visual and functional validation is complete.

**Post-record update — 27 August 2026.** A replacement isolated debug artifact was subsequently assembled and passed local archive-integrity checks. The user approved uploading it to the existing Appetize application for autonomous testing. The provider upload dialog was reached, but the browser file-transfer bridge returned HTTP 504 before transfer completed. Official Appetize REST upload requires an organization-admin API token, and this task has no configured connector or user-supplied token. The new artifact was not uploaded and the existing pre-repair artifact remains excluded from automated execution.
