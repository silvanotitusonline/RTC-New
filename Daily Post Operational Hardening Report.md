# Daily Post Operational Hardening Report

## Scope

This package extends the Daily Post comments and moderation surface with operational telemetry, production-safe queue controls, count-drift detection, Realtime fallback handling, security inventory tooling, and an explicit release gate.

## Implemented

The Android client now records structured duration and outcome metrics for comment pagination, creation, deletion, moderation, and reporting. Outcomes distinguish success, ordinary errors, timeouts, and authorization failures. A moderator-only health RPC exposes call volume, p50, p95, p99, timeout rate, authorization-failure rate, and error rate.

Production now contains report-table indexes for reporter and reviewer access, plus an actor/timestamp index for Daily Post audit events. The audit-event index is justified by the moderator history and operational review paths; current production row counts are near zero, so the index should be re-evaluated after workload growth rather than removed solely because its scan count is currently low.

The moderator backend now includes cursor-based open-report queue access, a detail queue with joined comment context, deduplicated report upsert behavior inherited from the report RPC, status transitions with reviewer identity and audit events, and aggregation by comment. Direct report-table access remains blocked.

A service-role-only count-drift check compares `daily_posts.comment_count` with `VISIBLE` comments. It records unresolved drift events and is scheduled every five minutes through `pg_cron`. The monitor records drift rather than silently repairing counts.

The Daily Post Realtime client now uses a post-scoped channel, filters actions by `post_id` for SDK compatibility, cancels collection on route teardown, and exposes an explicit unavailable state. The detail screen displays a warning and manual refresh fallback when the stream closes or fails.

The repository includes a guarded non-production load/query-plan runner. It refuses the production project ref, runs bounded `EXPLAIN (ANALYZE, BUFFERS)` checks, verifies the drift function, and optionally runs bounded `pgbench` traffic when explicitly enabled.

A release-gate workflow and local script now run the 217 source contracts, RPC authorization manifest verification, SQL artifact checks, shell syntax checks, and whitespace validation. Android CI remains the build/test gate.

## Production observations

The production SECURITY DEFINER inventory reconciles to **181 functions with authenticated execution**: 129 authenticated-user candidates, 42 moderator/administrator candidates, and 10 intentionally public functions. A further 34 SECURITY DEFINER functions are service-role-only and are not part of the authenticated total. No grants were revoked automatically; the checked-in inventory query produces review candidates so obsolete functions can be removed only after ownership confirmation.

Production Daily Post report and audit tables currently contain no live rows, and their scan counts are therefore not representative of future workload. No unused index was removed. The review query remains read-only and ranks candidates by scan count, relation size, row estimates, and foreign-key coverage.

## Verification

The local release gate passes with **217/217 source contracts**. Production verification confirmed the count-drift cron job exists at `*/5 * * * *` and the operational health migration was applied successfully.

The final Android workflow is running against the hardening commit `802048ea23440a7020c64c8ac85c4d1f6f0414d7`. Its terminal conclusion must be checked before release; any compiler or smoke-test failure should be fixed and rerun.

## Remaining controlled work

Representative p50/p95/p99 values require live traffic because current `pg_stat_statements` entries are migration/validation observations. The non-production load runner is ready but requires a staging database URL and a staging Daily Post UUID. The report queue UI can consume the new moderator RPCs, but the backend controls and audit history are already in production.
