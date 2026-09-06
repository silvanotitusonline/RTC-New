# Phase 50 — Exact Remaining Device-Backed Validation Matrix

## Current Execution Status

The repaired isolated debug APK is assembled locally and has passed archive integrity checks. I attempted the approved Appetize upload immediately, but the browser file-transfer bridge returned HTTP 504 before the APK reached the provider. The old Appetize build is not a safe substitute because it predates the verified non-production configuration repair.

> **Immediate execution blocker:** a fresh, verified isolated Appetize build target does not yet exist. The official REST upload alternative requires an organization-admin Appetize API token, and no such task connector or user-supplied token is available. This is a provider-access prerequisite, not an Android source failure.

**Local recheck — 27 August 2026.** The sandbox has two configured Android virtual-device definitions but no attached `adb` device and no `/dev/kvm` acceleration device. The Android emulator’s own acceleration check reports that VT/KVM is unavailable, so neither configured local AVD can execute D01–D06 in this sandbox. This is a platform limitation, not a test-suite failure.

**Alternative-provider verification — 27 August 2026.** BrowserStack requires a BrowserStack username and access key to upload an APK and start Appium testing. Sauce Labs requires a valid account plus username/access key to upload and test. Firebase Test Lab requires an authorized Firebase/Google Cloud project to upload an APK and run an instrumentation or Robo test. None is connected or authorized for this task. Creating an account, accepting provider terms, initiating a trial, or creating access credentials was not authorized and has not been attempted.

## A. Fully Autonomous Checks Ready to Run

These checks require only the newly assembled isolated debug build to exist in Appetize. They do not accept credentials, request permissions, create records, or use production.

| ID | Device-backed check | Automation action | Pass evidence |
| --- | --- | --- | --- |
| D01 | Debug synthetic Resident A launch | Force-stop then Android command launches `MainActivity` with the debug-only Resident A intent extra. | Home displays “Welcome back, Resident A”. |
| D02 | User menu / Account route | Tap the accessible profile action. | Account title and visible Sign out action appear. |
| D03 | Resident primary navigation | Tap Community, Explore, Support, then Home. | Each destination title appears and Home returns to the Resident A greeting. |
| D04 | Back navigation from Account | Open Account then invoke Android Back. | Home becomes visible again with no crash. |
| D05 | Startup error scan | Start the repaired APK with debug log capture. | No fatal exception; environment marker confirms isolated configuration. |
| D06 | Basic visual capture | Capture native screenshots at Welcome, Home, Community, Explore, Support, and Account. | Screens are inspected for graphite/emerald/gold theme, truncation, overlap, duplicate controls, and visible escape routes. |

The current local Appetize suite already implements D01–D03. D04–D06 are the next immediate scripted additions once the verified build target is available.

## B. Checks Requiring Isolated Authenticated Fixture Data

These are necessary to validate the actual server-confirmed community experience. They can be automated only after the isolated backend contains deliberately prepared synthetic accounts and authoritative test records; production identities and content are not permitted.

| ID | Device-backed check | Isolated prerequisite | Pass evidence |
| --- | --- | --- | --- |
| D07 | Guideline acceptance and composer unblocking | Synthetic resident with a pending guideline record. | Accept once; the overlay closes and the pending post or comment composer opens. |
| D08 | Create post and feed refresh | Synthetic resident plus a permitted test draft. | Server-confirmed post appears once in the Community feed after refresh. |
| D09 | Create, update, and delete comment | Author synthetic resident and a test post. | Comment appears, then updates/deletes after server confirmation; no raw backend JSON is exposed. |
| D10 | Like toggle and count | A test post, viewer identity, and guideline acceptance. | Like state and one count treatment refresh from the authoritative RPC response. |
| D11 | Share flow | A test post. | Android share chooser opens with only the safe `rtc://community/post/{id}` URI, with no signed media URL or author data. |
| D12 | Pull-to-refresh | Published isolated directory/community records. | Swipe refresh reloads visible authoritative records without duplicate cards. |
| D13 | Community profile avatars | Synthetic authored posts/comments with server-projected avatar URLs. | Image renders for server avatars; initials only where an authoritative avatar is absent. |

## C. Checks Requiring Isolated Media Fixtures and Device Permission Interaction

| ID | Device-backed check | Isolated prerequisite | Pass evidence |
| --- | --- | --- | --- |
| D14 | Community image rendering | Published test post with a signed isolated image record. | Image is visible only for the authoritative record; image tap opens media gallery. |
| D15 | Community video playback and sound toggle | Published test post with a signed isolated video record. | Video plays on explicit action; mute/unmute control changes audio state and player releases when leaving the screen. |
| D16 | Profile media picker | Synthetic authenticated resident and a harmless local image fixture. | Android visual picker opens, cancel remains terminal, and no app crash occurs. |
| D17 | Profile photo upload and avatar refresh | D16 plus isolated storage and profile projection support. | Success/failure reaches a terminal message within the configured timeout; updated avatar rehydrates Account, Community feed, and post detail. |
| D18 | Failure recovery after lost/invalid media URI | Synthetic account plus inaccessible test URI. | Safe terminal error is shown; no infinite spinner or raw exception. |

## D. Checks Requiring Notification Service Configuration

| ID | Device-backed check | Isolated prerequisite | Pass evidence |
| --- | --- | --- | --- |
| D19 | Notification rationale and deny path | Isolated authenticated session on Android 13. | “Not now” returns to Home without blocking Account/navigation. |
| D20 | Notification grant and FCM registration | Non-production Firebase Android configuration matched to the test build; no service account in app. | Permission grant succeeds and the isolated device token registers without application error. |
| D21 | In-app alert and deep link | Isolated, authorized alert fixture and a registered test device. | Alert/inbox item opens only the authorized detail route. |

The observed Appetize artifact did not contain the Firebase client configuration, so D20–D21 are expressly **not ready** until an isolated Firebase configuration is supplied through the approved per-environment channel.

## E. Checks Requiring Protected Synthetic Roles or MFA Test State

| ID | Device-backed check | Isolated prerequisite | Pass evidence |
| --- | --- | --- | --- |
| D22 | Staff workspace routes and sign out | Debug synthetic Content Editor, Moderator, Case Staff, and System Admin sessions. | Correct landing workspace, valid back path, Account profile route, and Sign out control for every role. |
| D23 | Fail-closed resident access | Resident A synthetic session. | Direct protected-route attempt returns to the safe resident workspace. |
| D24 | Administrator MFA route gate | Isolated System Admin with test MFA status. | MFA route is available only when required; protected routes do not open before verification. |
| D25 | Access-management and audit views | Isolated System Admin, dual-control state, and synthetic audit fixtures. | Read-only data is scoped correctly; any change route requires its intended guarded confirmation. |
| D26 | Admin layout/customisation controls | Isolated System Admin and authoritative configuration fixture. | No control presents success unless the corresponding server-confirmed action exists. |

## What Can Be Performed Immediately

**D01–D06 can be performed immediately by the prepared autonomous suite once the repaired APK is uploaded as a new Appetize build.** I attempted that provider upload with the user’s approval. It did not transfer because the My Browser file bridge timed out. I will not rerun D01–D06 against the old non-isolated artifact, and I will not create or expose an Appetize API token without a separate security approval.

**D07–D26 cannot be credibly passed immediately**, because they require either authoritative isolated backend fixtures, a non-production Firebase client configuration, or protected synthetic-role/MFA state. Running them without those prerequisites would either produce false results or risk crossing the stated production boundary.

## References

[1] [Appetize REST API](https://docs.appetize.io/rest-api) — organization-admin API token and `X-API-KEY` requirement for programmatic provider access.

[2] [BrowserStack App Automate app API](https://www.browserstack.com/docs/app-automate/api-reference/appium/apps) — APK upload requires a BrowserStack username and access key.

[3] [Sauce Labs Mobile App Storage](https://docs.saucelabs.com/mobile-apps/app-storage/) — live and automated mobile testing requires a valid account plus username/access key.

[4] [Firebase Test Lab](https://firebase.google.com/docs/test-lab) — device-cloud test workflow uploads packaged app/test artifacts to an authorized Firebase/Google Cloud project.
