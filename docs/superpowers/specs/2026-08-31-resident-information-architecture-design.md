# Resident Information Architecture Design

**Date:** 2026-08-31  
**Approved by:** Product owner  
**Foundation:** `integration/resident-navigation-v2-chrome` at `8c84c3c25b4590f9f4d3150c15a3b37e9baf6794`  
**Delivery branch:** `integration/resident-information-architecture-v1`

## Goal

Refine the resident-facing Home, Community, Explore, and Market destinations so that Public Reports are the source of Home community metrics, Community contains only its two feeds, Explore contains only official notices plus projects and opportunities, and the fourth primary tab opens the Marketplace directly.

## Approved report semantics

All four metrics are calculated only from publicly available reports whose `verified_at` is not null.

| Scope | Definition |
|---|---|
| Verified | Every verified public report |
| Active | Verified reports with status `IN_PROGRESS` |
| Resolved | Verified reports with status `COMPLETED` or `CLOSED` |
| Unresolved | Verified reports whose status is neither `COMPLETED` nor `CLOSED` |

`Active` is intentionally a subset of `Unresolved`. `Resolved` and `Unresolved` partition the verified total. Reports remain subject to the existing public-visibility window and public projection; private reporter identity, exact location, and evidence storage paths must never enter these responses.

## Architecture decision

Use an additive Public Reports v2 read contract while retaining the existing `SupabasePublicReportRepository` and all v1 create, detail, vote, comment, evidence, owner, and admin RPCs. Add `civic_report_dashboard_v2()` for the four exact counts and `civic_report_page_v2(...)` for cursor-paginated feed scopes `VERIFIED`, `ACTIVE`, `RESOLVED`, and `UNRESOLVED`. Do not reinterpret the existing v1 dashboard fields or v1 status buckets because older APKs may still rely on their current meanings.

The migration must be deployed before an APK that requires the v2 reads is promoted. Repository implementation does not itself authorize deployment to Supabase Production.

## Home

Replace the project/service `LiveDashboardMetrics` card in the `COMMUNITY_SNAPSHOT` slot with a Public Reports snapshot containing exactly four actionable metrics: Verified, Active, Resolved, and Unresolved. Selecting a metric navigates to Community, selects Public Reports, and applies the identical scope.

Place a composer card immediately below the snapshot. It contains one text input and two explicit actions:

- **Community Post:** save the entered text as a Community draft and open the complete Community composer.
- **Public Report:** seed the entered text into the Public Report `What’s happening?` field and open the complete Public Report composer.

Neither action publishes directly. Existing guidelines, media, category, urgency, location, evidence/no-evidence, identity, authentication, and validation requirements remain authoritative.

The Home snapshot must have loading, retry, and unavailable states. It must never present stale project metrics as Public Report data.

## Community

The Community primary destination contains exactly two top-level sections, labelled **Community Feed** and **Public Reports**. Remove the Marketplace button and Marketplace choice card from Community.

The Community Feed is rendered directly rather than behind another choice card. The Public Reports feed uses the same visual rhythm and content density as Community posts, while retaining report-specific title, verification, urgency, lifecycle status, evidence count, thumbs-up, thumbs-down, comment count, detail navigation, and pagination.

Public Reports exposes the four primary scopes in this order: Verified, Active, Resolved, Unresolved. Urgency, category, and sorting remain available as secondary filters. The default scope is Verified, so unverified reports are not shown in this resident dashboard.

Legacy Community Feed and Public Reports routes remain valid for existing deep links. Route parsing must fail closed for unknown scope values.

## Explore

The Explore root contains only two cards:

1. **Community Notices**, showing the current published count and navigating to the notices directory.
2. **Projects and Opportunities**, containing separate actionable rows and counts for projects and opportunities.

Remove Centres, Help Centre, Community conversations, and the four-value directory overview from the Explore root. Existing detail/deep-link routes may remain for compatibility and search results.

## Market

The fourth resident primary tab is labelled **Market**, uses a storefront icon, and has the top-bar title **Market**. Selecting it renders the existing `MarketplaceHomeRoute` immediately. The Service Centre / Marketplace selection hub is not shown.

Keep the internal `services` route as a compatibility alias during this release, but make its content the Marketplace home. Existing Service Centre booking and provider routes remain available for historical bookings and explicit deep links; they are not a competing primary-tab choice.

## State and navigation

- Keep the five primary destinations: Home, Community, Explore, Market, Account.
- Keep Search and Notifications adjacent in the resident top bar.
- Keep Profile and accessibility controls under Account as established by the Navigation V2 foundation.
- Store Home composer prefills in back-stack `SavedStateHandle`/existing draft state, not in URL query strings or logs.
- Consume prefills once and do not overwrite text the user has already entered.
- Preserve cursor pagination and deterministic refresh generation handling.

## Supabase security and rollout

- Both v2 functions use the existing public projection and 24-month visibility boundary.
- The dashboard returns counts only; it cannot return private fields.
- The page RPC returns the same safe public row type as v1.
- Preserve anonymous read access only where the v1 public feed already permits it.
- Revoke default/public execution before granting the intended `anon` and `authenticated` roles.
- Add SQL tests for verified-only counts, every scope, unverified exclusion, public expiry, cursor paging, and grants.
- Apply the migration before promoting the client; roll back the client independently by continuing to support v1.

## Accessibility and failure behaviour

- Every tab, scope, metric, and composer action has an explicit content description or visible label.
- Touch targets remain at least 48 dp.
- Loading and empty states identify the selected Public Report scope.
- A failed dashboard refresh retains any prior successful snapshot and offers retry.
- A failed feed filter request cannot mix rows from the previous scope.
- Offline and authentication failures use the existing safe UI error mapping and reveal no backend details.

## Verification gates

1. SQL migration and pgTAP/RLS/RPC tests.
2. Kotlin unit tests for scope wires, mappings, dashboard state, navigation policy, and composer prefill consumption.
3. Compose tests for the exact Home metrics, two Community sections, two Explore cards, and Market direct entry.
4. Existing Python source/contract suite.
5. `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
6. Emulator install/launch plus Home → scoped Public Reports, Community switching, Explore, Market, and Account smoke checks when device infrastructure is available.
7. Authentication and live Supabase smoke after the additive migration is explicitly approved and deployed.

## Non-goals

- No Supabase Production deployment without separate approval.
- No merge of PR #33 or this stacked branch during implementation.
- No changes to Public Report mutation authorization, evidence privacy, admin verification authority, signing, or credential gates.
- No client-side full-table counting or pagination shortcuts.
- No reintroduction of Marketplace inside Community or Support as a primary tab.
