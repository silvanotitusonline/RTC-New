# Phase 47 — Screen-by-Screen Visual and Functional Remediation Checklist

**Status:** Active recovery work. **No new APK will be delivered during this checklist phase.**  
**Evidence inputs:** User-supplied application walkthrough video, approved reference-screen specification, and current local Android source.

## Operating rule

Only one screen is remediated at a time. The current screen must complete its source-level interaction, visual, and regression checks before work moves to the next one. No interface is marked complete solely because it compiles; each visible control must map to a real guarded action or to truthful non-interactive status.

## Current screen: Community — in progress

| Requirement | Observed state | Required remediation | Status |
|---|---|---|---|
| Dark graphite / emerald visual hierarchy | Walkthrough displays a light, generic card layout rather than the approved Community hierarchy. | Default `SYSTEM` preference now resolves to the approved dark graphite system; explicit Light remains a user choice. | Source remediated; device check pending |
| Reaction and comment count | Walkthrough shows two reaction/comment displays per post. Source confirmed both a text summary and a numbered action row. | Removed the duplicate non-action summary; one accessible Like / Comment / Share row remains. | Source remediated; device check pending |
| Timestamps | Walkthrough displays raw ISO timestamps. | Feed cards now use the existing relative-time formatter. | Source remediated; device check pending |
| Profile avatars | Walkthrough shows initials only. | Server avatar remains first priority; development-only portraits remain guarded. Real avatar refresh depends on the pending Account photo-upload remediation. | Dependent on next screen |
| Posted images and video | Walkthrough shows no visible feed media. | Feed query maps embedded media to signed URLs and the renderer displays a media preview only when authoritative media exists. | Source path confirmed; real-feed device check pending |
| Community guideline acceptance | Accept appears not to release the post/comment flow. | Server-confirmed acceptance now closes the dialog and resumes the requested composer or comment action; cancellation clears the pending action. | Source remediated; device check pending |
| Technical errors | Raw `Unexpected JSON token` text is visible. | Parser and transport details are replaced with safe, actionable Community messages. | Source remediated; device check pending |
| Like, comment, share | Existing guarded actions are present but visual duplication makes them unclear. | One explicit Like / Comment / Share row remains; it keeps the existing guarded server action and route behavior. | Source remediated; device check pending |

## Current screen: Account and profile — in progress

| Requirement | Observed state | Required remediation | Status |
|---|---|---|---|
| Graphite / emerald profile design | Walkthrough shows a light generic profile editor. | The default system preference now uses the approved dark palette; explicit Light remains selectable. | Source remediated; device check pending |
| Gallery image selection | User receives `The selected image could not be read.` despite preview. | Replaced broad `GetContent` with the system visual-media picker and safe image guidance. | Source remediated; device check pending |
| Profile photo save | Loading can appear to hang. | Added a 45-second bounded terminal result, explicit success feedback, and preserved session/feed/detail rehydration after success. | Source remediated; authenticated device check pending |
| Placeholder activity controls | Following and similar unavailable areas are visually prominent. | Replaced three inert rows with one non-clickable truthful availability status. | Source remediated; device check pending |

## Remaining application surfaces — not started

| Screen | Reference target | Functional completion requirement | Status |
|---|---|---|---|
| Home | Dark analytic dashboard with dense snapshot, real metrics, connected directories. | Default graphite system is enforced; snapshot and Quick Access Projects/Centres now deep-link to their exact directories; Help label matches its real callback. | Source remediated; device check pending |
| Explore | Vibrant structured categories, search, progress panel, and connected categories. | Uses real published category counts and connected category rows; removed unsupported 100% progress signal. | Source remediated; device check pending |
| Support | Dark emergency boundary, truthful active cases, service-request shortcuts. | Default graphite system is enforced; Find a centre now opens the real Centres directory instead of a local placeholder. | Source remediated; device check pending |
| Welcome / Sign in / Create account | Dark branded onboarding with connected authentication actions. | Existing full-page reference flow now inherits the enforced graphite default; verify splash, navigation, verification messaging, recovery, and Back flows on a device. | Pending device validation |
| Administrator workspace | Reference-aligned guarded operations hub. | Existing guarded reference workspace now inherits the enforced graphite default; verify live role/MFA routes and source-backed values on a real authenticated device. | Pending device validation |

## Completion gates before any APK is offered

1. The Community screen must pass its source contracts and compile checks after all listed defects are remediated.
2. Account/profile image selection, terminal upload behavior, avatar refresh, and Community media rendering must have an executable local test path.
3. Every remaining screen must be assessed against its approved reference composition and its existing real routes.
4. A physical Android device or compatible KVM-enabled emulator must validate the dark-theme rendering, Community media, guideline acceptance, profile image, and navigation flow.
5. The final result remains a local debug build until release signing, backend verification, and all existing production gates are separately approved.
