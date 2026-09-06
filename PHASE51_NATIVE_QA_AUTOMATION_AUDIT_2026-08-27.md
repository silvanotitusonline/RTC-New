# RTC Community Native Android QA Automation and UI/UX Audit

**Scope.** This audit applies only to the local `recovery/nonproduction-baseline` branch. It does not query, modify, deploy to, or authenticate against production. It evaluates the user-provided native QA workflow against the test infrastructure actually available on 27 August 2026.

> **Verification rule.** A successful build, static contract, or browser simulation is not evidence that the Android APK completed the same action on a device. This report labels each result accordingly.

## A. Build and Test Execution Summary

| Activity | Result | Evidence / qualification |
| --- | --- | --- |
| Source-contract suite | **Passed: 79 / 79** | `uv run --no-project tools/tests/run_contract_tests.py` completed successfully. The checks include non-production debug configuration, release-gated Resident A automation, protected-route behavior, media, profile, notification, and Community contracts. |
| Debug unit-test task | **Passed** | `:app:testDebugUnitTest` completed successfully in the constrained Gradle run. |
| Android lint | **Passed** | `:app:lintDebug` completed successfully. |
| Debug APK assembly | **Passed** | `:app:assembleDebug` completed successfully. Gradle reported 62 actionable tasks, 1 executed and 61 up-to-date, in **9 seconds**. |
| APK location | **Produced locally** | `app/build/outputs/apk/debug/app-debug.apk`; it has not been uploaded, distributed, or substituted for the prior Appetize artifact. |
| APK size | **41,014,202 bytes** | Local file-stat evidence. |
| APK SHA-256 | **`929cafe5eeba681912b3b57e02f1ed87b007e7c7476db9aba0b3f8973433e531`** | Local integrity evidence; no signing key or sensitive runtime value was exposed. |
| APK archive integrity | **Passed** | ZIP integrity check completed successfully. |
| Automated device crawl | **Not executed — 0 screens** | There is no local Android device, `adb`, `/dev/kvm`, Firebase Test Lab setup, or authorized alternative cloud device target. |
| Headless Android snapshot suite | **Not available** | The build contains no Robolectric, Roborazzi, Paparazzi, or equivalent snapshot dependency or test configuration. |

The source has no executable `./gradlew` wrapper in the retained workspace, so the provided build instruction was adapted to the restored Gradle 8.13 installation. This is a repository completeness concern for future reproducible developer and CI builds; it did not block the local recovery build.

## B. Functional and Runtime Findings

| Severity | Finding | Evidence | Relevant Kotlin / build area | Recommendation |
| --- | --- | --- | --- | --- |
| High — release gate | A fresh isolated Android device target is unavailable. | No `adb` executable or attached target, no KVM acceleration, no installed/authenticated `gcloud`, and no configured Firebase/Google Cloud device-test integration. | `app/src/androidTest/.../DebugResidentMenuAutomationTest.kt`; `qa/appetize/` | Run the existing synthetic Resident A instrumentation suite and autonomous ADB navigation suite on an authorized non-production device cloud before release. Do not accept browser-console simulation as a substitute. |
| High — integration gate | The prior Appetize build reported that Firebase default options were unavailable and Firebase initialization was unsuccessful. | Historical Appetize debug log from the old artifact; no fatal process exception was captured. The current rebuilt APK was not device-run. | `app/build.gradle.kts`, Firebase Messaging initialization, `AndroidManifest.xml` | Provide the normal public Firebase Android client configuration only through the debug/non-production build configuration; validate notification permission, token registration, and alert deep links on an isolated device. Never put a Firebase service-account credential in the APK. |
| Medium — performance gate | Startup frame-jank warnings were observed in the prior Appetize session. | Historical Appetize log recorded 60/72 skipped-frame warnings during startup. This is not yet reproduced on the rebuilt isolated APK. | `MainActivity.kt`, `RtcViewModel.kt`, `RtcRepository.kt`, startup DI/WorkManager initialization | Capture a cold-start trace on a fresh isolated device. Defer non-critical feed hydration, image work, and synchronous initialization from first composition; report a measured result, not an estimate. |
| Medium — test coverage | The resident Account navigation test compiles but has not executed on Android hardware or a cloud emulator. | `:app:compileDebugAndroidTestKotlin` previously passed; no device target is present. | `DebugResidentMenuAutomationTest.kt`, `MainActivity.kt` | Execute `DebugResidentMenuAutomationTest` first on one isolated Pixel-class target; preserve its debug-only intent and verify release exclusion. |
| Low — build reproducibility | The Gradle wrapper is missing from the retained local source. | `./gradlew` is not executable in the workspace; restored Gradle was used. | Repository root `gradlew`/`gradle/wrapper/` | Restore and source-control the wrapper JAR, properties, script, and wrapper validation checks from an authoritative archive before external developer hand-off. |

No fatal crash, ANR, uncaught Kotlin exception, Room migration failure, or live network failure was observed in the **current local execution**, because none of the current checks launched the rebuilt APK on a device. The earlier Appetize observations remain limited to native launch, onboarding round trip, and an authenticated Home render from an artifact that predates the verified non-production configuration repair.

## C. Visual and Android UX Audit

The current native source contracts confirm the intended graphite, emerald, and gold system; transparent RTC launcher/splash assets; real onboarding return paths; four resident primary destinations; a single Community action/count treatment; readable relative timestamps; guarded Community guidelines; and protected administrator routing. These are **source-level** results, not screenshot-parity findings.

| Audit area requested | Evidence now | Required device evidence before an Android verdict |
| --- | --- | --- |
| Truncated text, padding, contrast, dynamic theme | No current native screenshot or snapshot. | Portrait and large-font screenshots of Welcome, sign-in, Create account, Home, Explore, Community, Account, Support, and Administrator surfaces. |
| Tap targets and navigation | Source routes and test selectors exist; native test compiled. | Instrumented test and/or Robo crawl results with touch-target inspection. |
| Empty, loading, and error states | Source controls exist for safe display paths. | Isolated device captures with controlled offline/slow-loading and authorized error fixtures. |
| Snackbars, permission rationale, system bars, edge-to-edge | Prior Appetize session showed the in-app notification rationale; no rebuilt-device result. | Fresh-device permission decline/grant test and visual evidence at API 33+. |
| Community media, video sound, profile-photo flow | Source contracts passed. | Isolated signed image/video fixture, mute/play testing, picker selection, bounded upload, and post-refresh evidence. |

The separate browser review console is useful for reviewing layout intent, connected synthetic interactions, fixture queues, annotations, and mock captures. It cannot evaluate Android Compose measurement, system bars, permissions, actual media decoding, touch dispatch, jank, notifications, or server-authorized paths.

## D. Production Readiness and Recommendations

The recovery branch is suitable for continued **non-production validation**, but it is not ready for a production-readiness statement. The missing fresh device evidence is substantive because it covers launch performance, Firebase/notifications, protected navigation, Community media, and profile synchronization.

| Priority | Recommendation | Why it matters | Completion evidence |
| --- | --- | --- | --- |
| 1 | Establish one authorized isolated device-cloud target and run both a three-minute Robo crawl and targeted instrumentation test. | A Robo test explores app paths and records installation/crash results, screenshots, activity maps, and video; instrumentation provides deterministic assertions. [1] [2] | Result matrix link, `logcat`, screenshots, video, crawl graph, and targeted test result for the rebuilt isolated APK. |
| 2 | Repair the non-production Firebase client setup and test notification behavior. | The prior artifact could not initialize the default Firebase application. The app must prove device-side permission, token, and deep-link behavior without exposing privileged credentials. | Fresh isolated device logs show successful client initialization; decline and grant cases documented; no service account in app source. |
| 3 | Turn the debug Resident A route into a formal cloud instrumentation target. | It reaches the user menu without real-user credentials or backend mutations, making D01–D06 deterministic and safe. | Passing class-level instrumentation execution on the rebuilt isolated APK and proof that release builds exclude the entry point. |
| 4 | Add Compose visual screenshot testing only after selecting a supported tool and target strategy. | The project currently has no headless Compose screenshot framework. A new dependency should be introduced deliberately, with baselines reviewed as design evidence—not as a security bypass. | Source-controlled visual tests for major screens, goldens reviewed, and device-cloud comparison retained for final validation. |
| 5 | Improve cold-start observability before micro-optimizing. | The observed skipped frames may come from emulator graphics, startup dependency work, or rendering; changing code without a trace risks ineffective work. | Measured cold/warm startup traces and an evidence-backed reduction in main-thread work. |

For Material 3 polish, keep transitions understated and respect reduced-motion preferences; preserve a predictable system-bar treatment; review edge-to-edge insets on the real targets; and ensure all actionable controls retain at least the Android-recommended 48dp touch target. These are implementation quality recommendations, not statements that the current UI violates them.

## Device-Cloud Decision Record

Firebase Test Lab supports Robo and instrumentation tests, but its command-line route requires a Google Cloud SDK installation, authenticated Firebase project, and a configured project. Its results include test execution artifacts, and its documentation cautions that test-account credentials must not correspond to real users. [1] The current session has no `gcloud` binary and no Firebase/Google Cloud integration, so the cloud command in the attached workflow was **not run**. No account, token, billing setting, bucket, API, or test matrix was created.

## Final Status

The local recovery branch builds cleanly and passes its available automated quality gates. The requested headless/device QA cannot yet be completed in this environment because the necessary safe device target is absent. The immediate next evidence-producing action is an authorized isolated device-cloud execution of the already-built APK and test APKs; only then should runtime logs, screen count, crawl graph, video, screenshots, Android visual defects, and end-to-end protected flows be adjudicated.

## References

[1] [Firebase Test Lab — Start testing with the gcloud CLI](https://firebase.google.com/docs/test-lab/android/command-line)

[2] [Google Cloud CLI — `gcloud firebase test android run`](https://docs.cloud.google.com/sdk/gcloud/reference/firebase/test/android/run)

[3] [Firebase Test Lab overview](https://firebase.google.com/docs/test-lab)
