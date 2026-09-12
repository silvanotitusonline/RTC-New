# Remediation External Reference Notes — 2026-08-26

## Supabase Documentation Reviewed

| Source | Relevant finding |
|---|---|
| [Supabase Changelog](https://supabase.com/changelog.md) | The current changelog was reviewed before remediation design. The April 2026 Data API change reinforces that table exposure must be intentional; it does not alter the existing rule that grants and RLS must be designed together. |
| [Securing your API](https://supabase.com/docs/guides/api/securing-your-api) | Data API reachability is governed by explicit PostgreSQL grants, while RLS governs accessible rows. The source advises bundling grants with RLS in the same migration, enabling RLS on exposed tables/views, and restricting function execution to appropriate roles. |
| [Event Triggers](https://supabase.com/docs/guides/database/postgres/event-triggers) | Event triggers are DDL-level constructs and are not the design needed for row-level profile synchronization. The avatar fix should use a normal row trigger attached to the canonical profile table, not a DDL event trigger. |

## Remediation Implication

The avatar synchronization mechanism must be an ordinary, narrowly scoped row trigger/function that is not callable through the public Data API. The reaction mutation must be an authenticated-only, server-validated operation with explicit grants/RLS rather than direct privileged client access. No service-role credential belongs in the Android app.
