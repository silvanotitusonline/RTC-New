# Phase 22 — Server-Confirmed Profile Persistence Reconciliation

**Date:** 26 August 2026
**Scope:** Restored Android feature-branch reconciliation.
**Environment:** Local Android source workspace only. The approved isolated non-production Supabase project and production were not changed during this phase.

## Finding

The restored feature source exposed an editable handle field and delegated profile updates to the archived `update_community_profile` RPC. It also rebuilt the authenticated display name from Auth metadata during session hydration rather than reading the user’s persisted profile. This differed from the recovered non-production contract, where `profiles.display_name` and `account_preferences.bio` / `account_preferences.interests` are owner-scoped persisted fields and an email-derived handle is not editable.

## Corrective change

The restored Android adapter now has an authenticated owner-only profile contract:

| Boundary | Implemented behavior |
|---|---|
| Read | Loads the current user’s display name from `profiles` and bio/interests from `account_preferences`. |
| Write | Validates display name, bio, and normalized interests; updates only the authenticated owner’s rows. |
| Confirmation | Re-reads persisted profile data and rejects a save if any saved field cannot be confirmed. |
| Session hydration | Uses the persisted profile values where available while retaining the handle derived from the verified email. |
| UI | Makes the account handle read-only and removes it from the profile save callback. |
| Development adapter | Retains local synthetic-state simulation without granting privileged authority or changing the handle. |

The archived direct profile RPC is no longer called by Android source. No database policy, migration, key, or production resource changed.

## Regression coverage and verification

A new source contract asserts that the direct profile RPC is absent, the adapter performs server-confirmed save/read-back, hydration uses persisted profile data, and the UI labels the account handle as non-editable.

| Check | Result |
|---|---|
| `python3 tools/tests/run_contract_tests.py` | 43/43 source contracts passed. |
| `testDebugUnitTest` | Passed. |
| `assembleDebug` | Passed. |

The generated debug APK remains a local build-verification artifact only. It was not distributed and does not constitute a release decision.

## Remaining boundary

This checkpoint reconciles profile-field persistence and handle honesty. Experience preferences, notification settings, assigned Case Staff workflow reconciliation, and Android real-session integration tests remain separate work. Overall release status remains **NO-GO**.
