# Coder A — RPC architecture, PR #29 conflicts, testable build

Date: 2026-08-30
Working branch: `integration/compose-mvp-v1`
Foundation: `integration/resident-modernisation-v1` @ `4c0494eb`
PR #29 head: `e85068b3` (draft, `mergeable_state: dirty`)
PR #28 head: `dd7cd483`

## Decision: do not merge PR #29 wholesale

GitHub reports PR #29 as dirty against `integration/resident-modernisation-v1`. The PR was opened from `86a0f2b`. The integration base then received:

- `bfc9fe45` Public Reports Android slice (`SupabasePublicReportRepository` + `publicReportRoutes()`)
- `4c0494eb` NavHost placement next to Marketplace

Wholesale merge would replace that client with PR #29's thinner duplicate and fight the current NavHost. Owner rules forbid accept-ours / accept-theirs across whole files.

The testable build is this integration branch, not a GitHub merge of #29.

## RPC client architecture (keep)

Canonical Android client on the foundation and on this branch:

`app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt`

Bound by `PublicReportModule` to `PublicReportRepository`.

Contract object: `PublicReportRpcContract`

| Client method | RPC / view | Notes |
|---|---|---|
| `categories()` | view `civic_report_categories_public` | sort_order |
| `page()` | `civic_report_page_v1` | cursor `p_cursor_created_at` + `p_cursor_id`, max 50 |
| `get()` | `civic_report_get_v1` | `p_report_id` |
| `timeline()` | view `civic_report_status_history_public` | |
| `comments()` | `civic_report_comment_page_v1` | cursor pagination |
| `addComment()` | `civic_report_add_comment_v1` | client request id |
| `setVote()` | `civic_report_set_vote_v1` | direction -1 / 0 / 1 |
| `create()` | `civic_report_create_v1` | owner is `auth.uid()` even when identity is ANONYMOUS |
| `uploadEvidenceBytes()` | storage bucket `civic-report-evidence` | owner-scoped path |
| `finalizeEvidence()` | `civic_report_finalize_evidence_v1` | |
| `dashboard()` | `civic_report_dashboard_v1` | Home metric source |
| `myPage()` | `civic_report_my_page_v1` | |
| `withdraw()` | `civic_report_withdraw_v1` | |
| `ownerPrivateDetails()` | `civic_report_owner_private_details_v1` | never shown on public cards |
| `adminPage()` | `admin_civic_report_page_v1` | staff |
| `adminSetVerification()` | `admin_civic_report_set_verification_v1` | EVIDENCE_REVIEWER / SYSTEM_ADMIN |
| `adminTransition()` | `admin_civic_report_transition_v1` | CASE_STAFF / EVIDENCE_REVIEWER / SYSTEM_ADMIN |

Mapping is explicit (`PublicReportJsonMappers`) so RPC JSON never becomes a Compose model. Failures map `CIVIC_REPORT_*` codes in `PublicReportFailure`.

## PR #29 client (discard)

`feature/publicreports/data/remote/SupabasePublicReportsRepository.kt` plus flattened `PublicReportsRepository` / `PublicReportsViewModel` / `PublicReportsScreens`.

Gaps versus the integration client:

- No cursor pagination after the first page
- No `myPage`, `withdraw`, `timeline`, `ownerPrivateDetails`
- No `PublicReportFailure` code mapping
- Decodes RPC rows directly into `@Serializable` UI models
- Would bind a second Hilt repository for the same RPCs

Same RPC names, worse client. Discarded.

## Overlapping files and retained behaviour

| File | Integration | PR #29 | Retained |
|---|---|---|---|
| Public Reports data/domain | Full RPC client | Duplicate thinner client | Integration |
| `PublicReportNavGraph.kt` | `publicReportRoutes()` | Inline composables + different models | Integration routes |
| `RtcCommunityNavGraph.kt` COMMUNITY | Hub card → overlay | Switch + PR #29 screens | Switch hosting **integration** `PublicReportsScreen` |
| `RtcNavigation.kt` | Four primary routes + public-report overlays | Hard five-icon + extra routes | Add EVENTS / ADMIN_EVENTS / SERVICES constants; keep four-icon primary set until flag |
| `RouteAccessPolicy.kt` | `admin/` fail-closed | ADMIN_EVENTS allowlist | Port ADMIN_EVENTS allowlist only |
| Admin Events | ViewModel recovered earlier | Screen + workspace row | Screen + route + workspace row |
| Auth / OAuth | Integration + later PR #28 port | Not authoritative | Do not take PR #29 auth |
| Feature flags | All off | Implicitly on | Remain off |

## What this testable build wires

- Community tab: Discussions / Public Reports switch
- Public Reports feed / composer / detail / votes / comments / evidence / admin still use `SupabasePublicReportRepository`
- Resident Events overlay `events`
- Staff Events calendar `admin/events` behind Content Editor / System Administrator
- Marketplace remains reachable from the Community switch header
- `ResidentModernisationFeatureFlags` stay fail-closed
- Five-icon `ResidentNavigationPolicy` is present; chrome still uses the four-icon set until `RESIDENT_NAVIGATION_V2` is enabled after device smoke

## Verification not claimed

No Gradle or emulator in this session. Required later from repo CI:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:compileDebugAndroidTestKotlin`
