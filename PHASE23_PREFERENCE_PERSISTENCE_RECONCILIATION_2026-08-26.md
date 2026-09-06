# Phase 23 — Server-Confirmed Experience and Notification Preference Reconciliation

**Date:** 26 August 2026
**Scope:** Restored Android feature-branch reconciliation.
**Environment:** Local Android source workspace only. The approved isolated non-production Supabase project and production were not changed during this phase.

## Finding

The restored source used a generic account-preferences upsert for reading mode, theme, and support notifications. It updated local session state before any server confirmation and did not hydrate those settings from their authoritative owner-scoped rows. The ordinary community-alert preference used a separate table but also returned no confirmed value after save.

## Corrective change

The generic bulk preference path was removed. The authenticated adapter now uses separate contracts for the three distinct storage boundaries:

| Preference | Authoritative storage | Save result |
|---|---|---|
| Reading mode and theme | `account_preferences` | Saves the pair, re-reads it, and verifies both persisted values. |
| Support notifications | `account_preferences.support_notifications` | Saves then re-reads the owner’s boolean. |
| Ordinary community alerts | `community_alert_preferences.ordinary_alerts_enabled` | Saves then re-reads the owner’s boolean. |

Authenticated session hydration now loads persisted profile, experience, support-notification, and ordinary-alert values. The repository updates in-memory session state and local reading/theme preferences only after a successful confirmed result. The development identity adapter remains a local simulation path only and grants no staff or administrator authority.

## Regression coverage and verification

A new source contract requires the dedicated adapter methods, hydration reads, repository read-back calls, and absence of the generic `saveAccountPreferences` path.

| Check | Result |
|---|---|
| `python3 tools/tests/run_contract_tests.py` | 44/44 source contracts passed. |
| `testDebugUnitTest` | Passed after correction of a stale removed-payload import. |
| `assembleDebug` | Passed. |

The generated debug APK is local build-verification output only. It was not distributed and does not represent a production-release approval.

## Remaining boundary

These checks validate source behavior and local compilation, not device interaction or a fresh real-session persistence run from this restored workspace. Real-session Android integration, durable upload recovery, Case Staff assignment workflow reconciliation, and release gates remain outstanding. Overall release status remains **NO-GO**.
