# Coder B interface contract

Base commit for parallel UI work: `4c0494eb2fb110a2be6919208e2683c51ca87081`
Coder A integration branch: `integration/compose-mvp-v1`

Rebase onto the latest `integration/compose-mvp-v1` boundary commit before final wiring. Do not edit Coder A files listed below.

## Files Coder B may edit

- `app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/**` visual layout only
- `app/src/main/java/za/org/rtc/community/feature/events/presentation/CommunityEventsScreen.kt` layout only
- `app/src/main/java/za/org/rtc/community/feature/home/HomeResidentModernisationCards.kt` layout only
- `app/src/main/java/za/org/rtc/community/feature/inbox/presentation/ResidentInboxScreen.kt` layout only
- `app/src/main/java/za/org/rtc/community/ui/theme/**` tokens already owned by design system
- Compose previews colocated with the screens above

## Files Coder B must not edit

- `MainActivity.kt`
- `ui/navigation/RtcCommunityApp.kt`
- `ui/navigation/RtcCommunityNavGraph.kt`
- `ui/navigation/ResidentModernisationRouteContent.kt`
- `ui/navigation/ResidentNavigationPolicy.kt`
- `ui/navigation/ResidentSurfacePolicy.kt`
- `ui/navigation/RtcNavigationMetadata.kt`
- `ui/navigation/RtcNavigationChrome.kt`
- `navigation/RtcNavigation.kt`
- `navigation/RouteAccessPolicy.kt`
- `navigation/ResidentModernisationRoutes.kt`
- `app/RtcViewModel.kt` and coordinators
- `data/RtcRepository.kt`
- `feature/publicreports/data/**`
- `feature/publicreports/domain/**`
- `di/**`
- `supabase/**`
- `.github/workflows/**`
- Gradle files

## Route contracts (already defined)

Resident primary (flag on):

1. `resident_home`
2. `resident_community` / `community?section=&focusPostId=&bucket=&urgency=&verified=&category=&sort=`
3. `resident_explore`
4. `services`
5. `account`

Overlays:

- `public-report/new`
- `public-report/{reportId}`
- `events`
- `inbox?tab=updates|messages`
- `account/settings`
- `account/support`
- `account/provider`
- `admin/events` (staff)

Public Report filter wires: `ALL|ACTIVE|COMPLETED|INACTIVE`, urgency enum, verified bool, category slug, sort `LATEST|HOT|MOST_SUPPORTED|MOST_DISCUSSED|HIGHEST_URGENCY|OLDEST`.

## UI state / action boundary

Public Reports screens must keep this shape even if names already differ:

```kotlin
@Composable
fun PublicReportsScreen(
    state: /* existing PublicReportsUiState or PublicReport feed state */,
    onAction: (/* existing action type */) -> Unit,
    onReportSelected: (reportId: String) -> Unit,
    onCreateReport: () -> Unit,
)
```

Rules:

- Immutable state in.
- Explicit events out.
- Navigation only through callbacks supplied by the NavHost.
- No Supabase, Room, Storage, or Edge Function calls from composables.
- No NavController in ViewModels.

## Remaining UI work for Coder B

- Report card density, evidence thumbnails, filter sheet polish
- Event card local vs wider-area treatment
- Empty / loading / error presentation
- Large-font and TalkBack pass on report/event/inbox cards
- Previews for feed, detail, composer, events list

Do not invent report counts. Dashboard numbers come from `civic_report_dashboard_v1` through the repository.
