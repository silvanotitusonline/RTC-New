# RTC Administration Dashboard and Supabase Production Audit

**Audit date:** 18 September 2026  
**Repository:** `silvanotitusonline/RTC-New`  
**Supabase project:** `RTC Community Production` (`pbzzfzfgwzwdstvnwzqu`, `eu-west-1`)  
**Scope:** Administration workspace, staff and system-administrator tools, their operational workflows, Supabase authorization and data contracts, production queue correctness, realtime invalidation, performance signals, and validation controls.

## Executive conclusion

The administration area is a credible protected staff workspace rather than a collection of placeholder screens. It has useful separation between overview, work queue, moderation, notices and events, access management, privacy analytics, system health, operational controls, administrative activity, branding, and guarded AI assistance. The client also follows several strong safety patterns: protected routes, server-side role checks, MFA gating for sensitive administrator tools, reason and typed-confirmation requirements for consequential actions, bounded summary RPCs, immutable audit concepts, and a payload-free realtime invalidation design.

However, the dashboard is **not yet reliable enough to be treated as an operational source of truth**. The most important defect is a production-state mismatch in the headline summary. The summary RPC treats `RESOLVED`, `CLOSED`, `ARCHIVED`, and `CANCELLED` as terminal, but production civic reports currently include `COMPLETED` and `DUPLICATE`. Those states are consequently counted as pending. The production snapshot contains two `COMPLETED` reports and one `DUPLICATE` report, so the dashboard can overstate actionable civic-report work by at least three records in the inspected dataset.

The second major concern is database hardening and migration governance. Supabase security advisors report 25 RLS-enabled tables with no policies and 179 authenticated-callable `SECURITY DEFINER` functions. Some of these may be intentionally RPC-only or service-owned, but the current volume makes accidental privilege exposure and incomplete authorization review difficult to exclude. The performance advisor also reports 47 unindexed foreign keys and 111 unused indexes. These are not all administration defects, but they directly affect queue growth, audit history, role management, and the cost of maintaining a large operational schema.

The recommended posture is **stabilize correctness first, then reduce operational ambiguity, then harden and measure**. Do not expand the dashboard’s feature count until its queue definitions, freshness guarantees, migration lineage, and critical-action observability are made authoritative.

## Findings at a glance

| ID | Severity | Area | Finding | Recommended disposition |
| --- | --- | --- | --- | --- |
| F-01 | Critical | Dashboard correctness | Terminal civic-report states are incomplete in the summary RPC. `COMPLETED` and `DUPLICATE` are counted as pending. | Correct the state contract, add a database-backed regression test, and reconcile the summary with the admin queue query. |
| F-02 | High | Production security | Supabase reports 25 RLS-enabled tables without policies and 179 authenticated-callable `SECURITY DEFINER` functions. | Create an allowlist and review every exposed function/table; revoke by default where no client contract exists. |
| F-03 | High | Freshness and realtime | The invalidation table has zero rows in production despite four triggers. The implementation has no periodic fallback refresh and marks the stream live after receiving an event, not after confirming a successful authoritative refresh. | Add bounded fallback refresh, refresh-health telemetry, and an integration test that proves write → invalidation → reread. |
| F-04 | High | Migration governance | Production has a substantially larger and differently versioned migration history than the current repository migration set. | Establish one canonical migration lineage and a CI drift check before further production changes. |
| F-05 | Medium | Operational usability | The workspace is dense and routes many actions through a large reference directory. Several actions require staff to know account IDs or move to another screen instead of offering contextual lookup and detail views. | Add an action-oriented queue landing page, contextual detail drawers/screens, better filters, and staff lookup controls. |
| F-06 | Medium | Workflow completeness | The overview counts civic reports, notices, events, and work items, but the broader administration surface also governs community moderation, appeals, support cases, daily posts, marketplace reviews, UI configuration, alerts, and account lifecycle. | Define an explicit coverage matrix and distinguish “pending operational workload” from “system health” and “content governance.” |
| F-07 | Medium | Performance hygiene | 47 unindexed foreign keys and 111 unused indexes indicate accumulated schema/index debt. | Prioritize indexes for admin queues and audit/event paths, then remove or defer unused indexes only after workload validation. |
| F-08 | Medium | Validation gap | Focused Python test execution could not run because `pytest` is not installed locally. Android and contract validation was started but not available as a completed result within the audit window. | Make the required test toolchain reproducible and publish a clear pass/fail dashboard for admin contracts. |
| F-09 | Low | UI semantics | The UI uses cards with click handlers and minimum sizing, but several icons have null content descriptions and some action affordances are visually similar despite different consequences. | Improve semantics, destructive-action hierarchy, loading states, keyboard/focus behavior, and screen-reader descriptions. |

## What is working well

### The security model is layered

The workspace checks staff membership before rendering. System-administrator destinations are additionally redirected to MFA when the live session is not verified. Sensitive backend operations use server-side assertions rather than trusting the client role alone. The access-management copy correctly describes exact verified-account lookup, dual-administrator approval, fresh-session requirements, and immutable history.

Operational controls also require an operational reason, an audit note, an expiry, an impact preview, and typed confirmation. The AI surface is explicitly proposal-only and states that it cannot publish, delete, assign roles, or apply production configuration directly. These are good controls for reducing accidental high-impact actions.

### The dashboard summary is bounded by design

The production summary RPC returns only aggregate counts. It does not expose whole privileged tables to the dashboard. The realtime path is also designed as an invalidation signal: a staff client receives a notification and rereads the guarded aggregate RPC. This is safer than broadcasting operational row payloads to staff clients.

### The workspace has useful operational grouping

The overview presents live queue counts, urgent work, work-queue filters, frontend preview links, and protected administration tools. The work queue includes claim, release, ready-for-review, and reassignment operations. Notices follow a draft, submit, independent review, publish or schedule, and retire lifecycle. Moderation presents report and appeal paths instead of only a single destructive action.

### The repository has strong architectural intent

The code is split into administration workspace, dashboard state, access, moderation/content, operations, and AI files. The GitHub workflows include source regression contracts, Edge authorization tests, JVM unit tests, lint, debug builds, Android smoke-test compilation, and a disposable local Supabase reset plus database test job. The architecture is therefore capable of supporting a stronger audit and release gate without a wholesale rewrite.

## Detailed assessment

## 1. Operational correctness

### F-01 — Summary counts do not match production terminal states

The summary function in `supabase/migrations/20260917220000_production_admin_dashboard_summary.sql` excludes only `RESOLVED`, `CLOSED`, `ARCHIVED`, and `CANCELLED` for civic reports. The production data inspection found the following report states:

| State | Count | Expected treatment |
| --- | ---: | --- |
| `ACKNOWLEDGED` | 2 | Pending |
| `CLOSED` | 1 | Terminal |
| `COMPLETED` | 1 | Terminal, but currently counted |
| `DUPLICATE` | 1 | Terminal, but currently counted |
| `IN_PROGRESS` | 1 | Pending |
| `SUBMITTED` | 2 | Pending |

This is not merely a label problem. The summary drives the red pending badge, the Reports tile, and the total pending task count. An administrator can therefore prioritize work based on a false queue size.

**Remediation.** Define a single database-level state classification for each governed queue. The safest short-term fix is to update the summary RPC to exclude every terminal state currently accepted by the civic-report state machine, including `COMPLETED` and `DUPLICATE`. The safer long-term design is a canonical `is_open` or `queue_bucket` function/view used by both the summary RPC and the paginated admin queue RPC. Add a pgTAP test that seeds one row for every state and asserts the summary count.

### F-03 — Realtime freshness is not proven end to end

Production inspection confirmed that the summary RPC exists, four invalidation triggers exist, and the invalidation table is included in the database setup. The production invalidation table currently contains zero rows. That does not prove the triggers are broken because the table may have been created after the existing records were written; it does mean the audit found no evidence that the current production dashboard has processed a post-deployment invalidation event.

The client stream sets the status to `LIVE` as soon as an invalidation event reaches the flow. The authoritative reread happens in the subsequent collector. If the reread fails, the state can still briefly describe live updates while showing stale counts. There is also no periodic fallback refresh while the subscription remains connected. Realtime subscriptions can silently become stale without a transport error, so a dashboard that governs production work should not rely solely on the stream.

**Remediation.** Keep the invalidation approach, but change the state machine to `CONNECTING`, `LIVE_AND_CONFIRMED`, `STALE`, and `DISCONNECTED`. Mark the stream healthy only after the RPC reread succeeds. Add a low-frequency fallback refresh, such as every 2–5 minutes while the screen is active, with exponential backoff on repeated failures. Display the age of the last successful authoritative read, not only the age of the last realtime event. Add an integration test that performs a safe test-environment write, observes an invalidation row, rereads the summary, and verifies the count transition.

### F-04 — Production and repository migration lineage needs reconciliation

The repository contains 65 migration files in the inspected checkout. Production reports 130 migration entries and includes versions and names that do not correspond one-for-one with the current repository filenames. Production also shows multiple dashboard-summary entries under different migration versions. This may reflect a previous migration packaging or promotion process, but it prevents a reviewer from treating the checked-in migration directory as a complete, deterministic description of production.

**Remediation.** Choose one canonical migration history. Export or otherwise reconcile the production schema into a reviewed baseline, preserve the historical lineage in a documented archive, and make future releases apply only migrations present in version control. Add CI that compares the migration set used to build the disposable database with the migration set intended for production. The release process should fail on an unexpected production-only migration unless it is explicitly recorded as an approved compatibility migration.

## 2. Administration feature coverage

The workspace covers a broad set of functions, but its overview is not yet a complete control-plane map. The main dashboard directly exposes:

- operational work queue and assignment;
- moderation and appeals;
- official notice editorial workflow;
- community events;
- access management;
- privacy analytics;
- system health and delivery indicators;
- operational controls and incidents;
- administrative activity;
- branding and experience;
- guarded AI proposals; and
- links back to major resident-facing surfaces.

The production schema and source also contain additional governed domains, including support cases, daily posts, marketplace submissions and review reports, UI configuration versions, community alerts, account deletion, role changes, notification delivery, and audit events. The overview’s aggregate summary only counts four sources: civic reports, official notices, community events, and operational work items. This is acceptable if intentionally defined as a moderation summary, but the current “administration dashboard” framing risks implying broader coverage than the headline metrics actually provide.

**Recommendation.** Publish a coverage matrix with these columns: governed feature, admin surface, queue source, read RPC, mutation RPC, role allowed, MFA required, audit event, realtime signal, pagination, and test contract. Any feature without a corresponding row should be marked either “not administered here” or “planned.” The dashboard should then separate three concepts: **actionable workload**, **system health**, and **governance/configuration**.

## 3. Usability and efficiency

The current UI is careful and reasonably accessible, but it is optimized for completeness rather than speed of expert work.

The overview contains a frontend-preview strip, tab navigation, live counts, protected tool tiles, urgent work, and a long tool directory. This creates a large vertical surface before an administrator reaches the exact task they need. The tool directory is useful as a map, but it is not a fast command surface. There is no visible global search, saved filter, date/age filter, SLA filter, or “needs my decision” mode in the reviewed workspace.

The work queue provides useful filters for all, urgent, mine, and unassigned. Reassignment asks for a raw staff account ID, which is a high-friction and error-prone interaction. It also shifts authorization discovery to the operator instead of offering a searchable list of eligible staff. Moderation uses one shared reason field above the entire report and appeal list. That can make it easy to apply a stale reason to the wrong item, especially when a user scrolls through a dense list.

The notice workflow is clear but also uses a shared review-note field for multiple cards. The system should bind a note to the specific action dialog or selected record. Destructive actions such as remove, lock, reject, retire, and operational pause deserve confirmation dialogs with the target, impact, reason, and resulting state shown together.

**Recommended interaction changes.**

1. Make the first screen an action-oriented “Needs attention” queue with age, priority, SLA, assignment, and role filters.
2. Keep the current overview as a secondary “Operations overview” tab.
3. Replace account-ID reassignment with a searchable, role-filtered staff picker.
4. Move moderation and editorial reasons into per-item dialogs or selected-item panels.
5. Add consistent “last authoritative refresh,” “source,” and “stale” indicators to every operational count.
6. Add pagination or lazy loading to every unbounded operational list and display the number of loaded versus total records where available.
7. Add a compact keyboard/focus order and screen-reader labels for tool tiles, refresh controls, status badges, and destructive actions.
8. Provide direct links from every aggregate metric to a pre-filtered queue that uses the same classification logic as the metric.

## 4. Supabase security and authorization

### F-02 — RLS and `SECURITY DEFINER` exposure require an allowlist review

The production Supabase security advisor reported:

- **25** tables with RLS enabled but no policies;
- **10** anon-callable `SECURITY DEFINER` functions;
- **179** authenticated-callable `SECURITY DEFINER` functions; and
- **1** warning for leaked-password protection not being enabled.

The audit also verified that sampled administration RPCs do not execute for `anon` but are executable by `authenticated`, with the function body expected to enforce staff or administrator authorization. That pattern can be valid, but it creates a broad attack surface: every authenticated user can invoke the function endpoint and rely on the function’s internal assertion. A missing assertion, overly broad helper, unsafe search path, or overly permissive input validator becomes a privilege boundary defect.

The RLS-without-policy findings include access-control tables, privacy-analytics tables, operational-control tables, operational-work tables, moderation appeals, notifications, and service-centre tables. Some are likely intentionally RPC-only and therefore deny direct access. They should still be explicitly classified so that an advisor warning is not confused with an unreviewed omission.

**Remediation.** Create an authorization manifest for every exposed table and function. The manifest should state whether direct table access is allowed, whether only a specific RPC is allowed, which roles may execute it, whether MFA or a fresh session is required, and which audit event is emitted. Revoke `EXECUTE` from `authenticated` for functions that are not part of the client contract. Move internal-only functions into a non-exposed schema where practical. For every remaining `SECURITY DEFINER` function, verify `SET search_path`, explicit actor checks, bounded inputs, target binding, idempotency, and audit behavior. Enable compromised-password protection.

### Role governance is conceptually strong but needs operational evidence

The access-management screen describes two-person approval for administrator changes, exact verified-account lookup, and fresh-session checks. The production schema includes role-change requests, access-session controls, and role audit events. The next audit should test these as adversarial workflows, not only inspect source strings: a resident calling each RPC, a moderator attempting system-admin changes, an administrator approving their own request, a stale session acting after invalidation, and a replayed mutation request.

## 5. Performance and scalability

### F-07 — Advisor output shows significant schema/index debt

Supabase reports 47 foreign keys without covering indexes and 111 unused indexes. The unused-index report includes administration-adjacent objects such as operational work items, audit events, official notice reviews, access-role tables, civic report indexes, community events, notification delivery, and UI configuration. Unused indexes are not automatically safe to remove because the production project may have low current volume or the feature may be newly introduced. Conversely, unindexed foreign keys can materially increase update/delete costs and audit-history joins as the system grows.

The current queue tables do have useful indexes, including state/updated indexes for community events, source uniqueness for work items, visibility and owner indexes for operational work, and several civic-report indexes. The issue is consistency and evidence, not absence of all indexing.

**Remediation.** Measure the actual admin query plans with representative row volumes in a non-production clone. Add covering indexes for the most frequent filters and joins first, especially queue state, assignee, due time, audit entity, and role-request target. Review duplicate indexes such as overlapping actor indexes. Remove unused indexes only through a migration after confirming no scheduled job or infrequent administrative workflow depends on them. Track p95 RPC latency and row counts for every administration read.

## 6. Reliability and testability

The repository’s CI design is a strength. It runs source contracts, Edge authorization tests, JVM tests, lint, debug assembly, Android test compilation, and local Supabase reset/database tests. In this audit, the source-contract suite passed **193/193**. The JVM/Gradle validation could not start because the sandbox has no configured Android SDK (`ANDROID_HOME`/`local.properties`). The focused Python test command also could not run because `pytest` is not installed. These are validation-environment limitations rather than evidence that the source contracts fail, but they should be made reproducible for contributors and release checks.

The administration surface needs a smaller, explicit regression suite with these minimum cases:

- every civic-report state is classified correctly by summary and queue;
- summary counts and queue rows agree for the same filters;
- a terminal transition removes an item from the pending summary;
- a realtime invalidation triggers a successful authoritative reread;
- a failed reread produces a stale state and does not claim freshness;
- staff, moderator, content editor, case staff, and system administrator role boundaries are enforced;
- unverified MFA cannot access protected administration tools;
- role changes require the correct second administrator and cannot self-approve;
- every destructive action requires a non-empty reason and creates an audit record;
- repeated mutation requests are idempotent;
- paginated lists do not load entire tables; and
- the empty, loading, error, stale, and success states are all visible and actionable.

## Prioritized remediation roadmap

### Phase 0: Correctness containment

Immediately correct the civic-report terminal-state classification. Add a regression test that fails on `COMPLETED` and `DUPLICATE`. Temporarily change the dashboard label to “Summary of configured queues” if the team cannot guarantee complete feature coverage. Add a visible authoritative-refresh timestamp and stale warning.

### Phase 1: Production trust

Prove the realtime write-to-reread path in a disposable environment and then in a controlled production smoke test that does not mutate business data. Add fallback refresh and a confirmed-live state. Reconcile the summary RPC with the queue RPC so every metric opens a list governed by the same state predicate.

### Phase 2: Security closure

Create the function/table authorization manifest. Review and classify all 25 policyless RLS tables. Review the 179 authenticated-callable `SECURITY DEFINER` functions by exposure and role. Revoke unused execution grants, enable password-leak protection, and add adversarial authorization tests.

### Phase 3: Operator efficiency

Reorder the workspace around “Needs attention.” Add global search, SLA/age filters, saved views, contextual detail panels, per-item decision dialogs, searchable staff reassignment, bulk-safe operations where justified, and direct links from every metric to a matching queue.

### Phase 4: Governance and scale

Reconcile migration history and add schema drift gates. Tune indexes based on actual query plans. Add service-level metrics for RPC latency, realtime age, queue age, failed mutations, and audit-write failures. Establish a monthly advisor review and a release gate that blocks new administration functions without authorization, audit, and regression contracts.

## Suggested target operating model

A robust administration dashboard should make five facts immediately visible for every queue: **what requires action, who owns it, how old it is, what will happen when the operator acts, and whether the displayed state is fresh and authoritative**. The current RTC workspace has the beginnings of all five, but they are distributed across separate screens and are not yet consistently bound to one queue contract.

The target should be a single authoritative queue model with role-filtered projections. Summary cards, badges, tabs, and detail screens should all derive from the same server-side classification. Every mutation should return the resulting entity state and audit identifier. Every realtime event should be treated as an invalidation request rather than as truth. Every protected operation should have one explicit authorization path, one idempotency path, and one observable audit result.

## Evidence and limitations

The audit inspected the checked-out repository, administration source files, Supabase production table metadata, production migration history, Supabase security and performance advisors, sampled function grants, queue-state distributions, administration indexes, and available CI/test definitions. It did not submit destructive production mutations, alter production schema, change grants, or publish application changes.

Production counts and advisor findings are point-in-time observations from 18 September 2026. The zero-row invalidation table is not, by itself, proof that the triggers are defective; it is evidence that the audit could not confirm a live event has been recorded since the table’s creation. The advisor warnings also require classification: some may be intentional RPC-only boundaries or low-volume indexes. They should not be dismissed without an explicit allowlist.

## References

[1]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspace.kt "RTC administration workspace implementation"

[2]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/feature/administration/AdminDashboardViewModel.kt "RTC administration dashboard state and realtime implementation"

[3]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/feature/administration/AdministrationAccess.kt "RTC access management and privacy analytics screens"

[4]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/feature/administration/AdministrationContentModeration.kt "RTC moderation and editorial administration screens"

[5]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/feature/administration/AdministrationOperations.kt "RTC operations, health, activity, and controls screens"

[6]: https://github.com/silvanotitusonline/RTC-New/blob/main/supabase/migrations/20260917220000_production_admin_dashboard_summary.sql "RTC production administration summary migration"

[7]: https://github.com/silvanotitusonline/RTC-New/blob/main/supabase/migrations/20260918010000_admin_dashboard_realtime_invalidation.sql "RTC administration dashboard realtime invalidation migration"

[8]: https://github.com/silvanotitusonline/RTC-New/blob/main/.github/workflows/android-ci.yml "RTC Android CI workflow"

[9]: https://github.com/silvanotitusonline/RTC-New/blob/main/.github/workflows/supabase-local-ci.yml "RTC local Supabase verification workflow"

[10]: https://supabase.com/docs/guides/database/database-linter "Supabase database advisor and linter documentation"

[11]: https://supabase.com/docs/guides/database/postgres/row-level-security "Supabase row-level security documentation"

[12]: https://supabase.com/docs/guides/database/functions "Supabase database functions documentation"

[13]: https://supabase.com/docs/guides/realtime/postgres-changes "Supabase Postgres Changes realtime documentation"

[14]: https://supabase.com/docs/guides/auth/password-security "Supabase password security documentation"
