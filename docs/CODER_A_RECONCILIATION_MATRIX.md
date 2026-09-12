# Coder A Phase A — Reconciliation Matrix

Date: 2026-08-30
Working branch: `integration/compose-mvp-v1`
Created from: `origin/integration/resident-modernisation-v1` @ `4c0494eb2fb110a2be6919208e2683c51ca87081`

## Current remote heads (fetched 2026-08-30 17:35 SAST)

| Ref | SHA | Notes |
|---|---|---|
| `main` | `57511f61` | Resident-modernisation *contract* foundation only. Four-icon shell. Public Reports card is not wired. |
| `integration/resident-modernisation-v1` | `4c0494eb` | Canonical foundation. Public Reports Android slice + `publicReportRoutes()`. Events/Inbox packages present. Feature flags default **off**. |
| `integration/resident-modernisation-wiring-v1` PR #28 | `dd7cd483` | Auth + five-destination policy + Home/Explore/Services/Account/Inbox wiring. `reportsHostAvailable = false`. |
| `integration/resident-modernisation-v1-ui` PR #29 | `e85068b3` | Five-icon NavHost + Admin Events + duplicate Public Reports client. Conflicts with current integration base. |
| `feature/public-reports-android-v1` PR #27 | `b013d2db` | Superseded. Domain-only remnant. Do not merge. |
| `testable/pr29-reconciled-ui` | `4c0494eb` | Identical to integration base. Not a completed reconciliation. |
| `integration/compose-mvp-v1` | this branch | Coder A integration owner branch. |

PR #28 and PR #29 both still declare base `86a0f2b`. The integration base has since moved (`bfc9fe45` Public Reports Android slice, `4c0494eb` NavHost placement). Wholesale merge of either PR is forbidden and would conflict.

## Capability matrix

| Capability | Integration base `4c0494eb` | PR #28 | PR #29 | Final source |
|---|---|---|---|---|
| Authentication email/password | Existing coordinator + repository | Keep | Do not overwrite | Integration + PR #28 OAuth only |
| Google OAuth | Credential-manager / ID-token path on older wiring | **Browser OAuth** `signInWith(Google)` + `rtc://community` Auth scheme | Not authoritative | **PR #28** |
| Session restore / sign-out | Present | Preserve | Do not overwrite | Integration |
| Password recovery deep link | `MainActivity` recovery query | Present | Routes recovery to Account Settings | Integration recovery + later settings route |
| Five-icon resident bar | Still Home/Community/Explore/Support | Policy object, flag-gated | Hard-wired five icons | **PR #28 policy**, enable only after smoke |
| Public Reports RPC client | `SupabasePublicReportRepository` + `PublicReportRpcContract` | Not present | Thinner duplicate remote client | **Integration base. Discard PR #29 client.** |
| Public Reports routes | `publicReportRoutes()` + Community hub callback | `reportsHostAvailable = false` | Own feed/composer/detail screens | **Integration routes.** Wire PR #28 boundary to this host. |
| Home metrics / composer / events | Cards exist in package; HomeScreen on base does not mount modernisation contract | Mounts modernisation contract, metrics empty until reports host | Mounts metrics via separate ViewModel + duplicate models | **PR #28 contract + integration repository dashboard()** |
| Events resident | Package + screen | Route + locality apply button | Route | **PR #28 route + integration repository** |
| Admin Events | Not in NavHost/Admin Workspace | Absent | AdminEventsScreen + ADMIN_EVENTS route | **PR #29 only this slice** |
| Inbox | Package + screen | Typed tab + allowlisted resolver | Notification gateway rewrite | **PR #28 resolver** |
| Services hub | Service Centre + Marketplace routes exist | Services destination + Public Reports card | Services destination | **PR #28 hub + integration Service Centre** |
| Account hub / settings / support / provider | Single AccountScreen | Flag-gated split routes | Separate AccountHubScreen | **PR #28 split**, keep existing AccountScreen visuals |
| Feature flags | All `releaseDefaultEnabled = false` | Same fail-closed defaults | Implicitly on | **Keep fail-closed.** Integration override is a later explicit commit. |
| CI debug runtime | Isolated placeholders on some histories | Connected non-prod URL + publishable key for installable APK | Not authoritative | **PR #28 CI step** |
| Source contracts | Present | Updated for Google browser OAuth + route content file | Different NavHost assumptions | Port PR #28 test updates with the files they describe |

## Interpretation vs Blueprint

- Blueprint wants Public Reports reachable. Integration already registers `RtcRoute.PUBLIC_REPORTS` from Community hub. The device bug on `main` is missing this wiring, not missing RPCs.
- Blueprint wants five destinations. PR #28 implements them behind `RESIDENT_NAVIGATION_V2` defaulting to off. That is the correct release posture.
- Blueprint wants dashboard metrics to use typed filters. PR #28 Home cards navigate with `CommunityRouteOptions`. Integration `civic_report_dashboard_v1` is the data source. Do not invent counts in UI.
- Anonymous reports must keep `auth.uid()` privately. Live RPCs already do this. Do not change SQL in this branch unless a proven gap appears in Non-Production.
- Do not enable feature flags until auth + route smoke on a debug APK.

## Explicitly discarded

- PR #27 wholesale.
- PR #29 `data/remote/SupabasePublicReportsRepository`.
- PR #29 hard-wired four-to-five icon swap that bypasses feature flags.
- Merging PR #28 or #29 history onto this branch.
