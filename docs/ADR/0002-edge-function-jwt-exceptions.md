# ADR 0002: Edge Function JWT exceptions

**Status:** Accepted  
**Date:** 2026-09-17

## Context

Some workloads are schedulers, public static pages, or legacy webhook stubs. User JWT verification is either impossible or meaningless for these cases.

## Decision

Default `verify_jwt: true`. Exceptions require:
1. Documentation in `docs/EDGE_FUNCTION_JWT_POLICY.md`.
2. Alternative authentication (shared secret RPC, or no sensitive capability).
3. No acceptance of caller-supplied account IDs for privileged work.

## Consequences

Clear audit trail of why certain functions are public at the platform JWT layer while still being hardened inside the function body.
