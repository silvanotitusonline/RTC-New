# ADR 0001: RPC-first security boundary

**Status:** Accepted  
**Date:** 2026-08 / reiterated 2026-09-17

## Context

RTC Community handles civic reports, evidence, support cases, marketplace ownership, and staff workflows. Direct PostgREST table access is too coarse for these rules.

## Decision

- Prefer SECURITY DEFINER RPCs (and Edge Functions for cross-cutting / external work) as the client-facing API.
- Enable RLS on all public tables; use explicit deny policies where the only legitimate path is RPC/service-role.
- Keep intentional public-read RPCs callable by `anon` and document them.

## Consequences

- Stronger invariants and auditability.
- Larger surface of authenticated SECURITY DEFINER functions (accepted WARN advisors).
- Clients must not treat UI role checks as authoritative.
