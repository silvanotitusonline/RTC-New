# ADR 0003: High-volume table growth strategy

**Status:** Accepted (forward-looking)  
**Date:** 2026-09-17

## Context

Tables that will grow quickly: `messages`, `notification_delivery_attempts`, `audit_events` / domain audit tables, community reactions, marketplace audit events.

## Decision

1. **Near term:** Indexes on FKs and hot filters (applied 2026-09-17). Monitor row counts and p95 query latency.
2. **Medium term:** Time-based partitioning (monthly) for `notification_delivery_attempts` and large audit tables once sustained volume justifies the operational cost.
3. **Retention:**
   - Notification delivery attempts: keep accepted/permanent-failure rows for operational debugging window (e.g. 90 days), then archive or purge.
   - Messages: retain for account lifetime; hard-delete on account deletion pipeline.
   - Audit events: longer retention for compliance; anonymise actor identifiers on account deletion where schema allows.
4. **Archival mechanism:** Prefer scheduled Edge Function / SQL job that moves cold partitions to cheaper storage or deletes expired rows under explicit policy, with audit of the archival run itself.

## Consequences

Avoid premature partitioning complexity while ensuring a clear path when metrics demand it.
