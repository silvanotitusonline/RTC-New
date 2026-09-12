# Phase 21 — Unsupported Alert-Retry Workflow Removal

**Date:** 26 August 2026
**Scope:** Restored Android feature-branch reconciliation.
**Environment:** Local Android source workspace only. The isolated non-production Supabase project and production were not changed during this phase.

## Finding

The restored feature archive contained an end-to-end Android workflow for `ops_retry_failed_community_alert`: an Operations UI panel, a ViewModel wrapper, a repository pass-through, and an authenticated Supabase RPC wrapper.

The retained non-production real-session contract runner explicitly proves that this RPC must remain unavailable. The recovered non-production backend therefore cannot truthfully perform the action. Keeping the UI would allow an Operations user to request an action that has no supported delivery contract and could create a false-success expectation.

## Corrective change

The unsupported workflow was removed from all Android layers:

| Layer | Removal |
|---|---|
| Compose Operations UI | Deleted the failed-alert retry section and its local retry-reason state. |
| ViewModel | Deleted `retryFailedAlertDelivery`. |
| Repository | Deleted the refresh-after-retry pass-through. |
| Supabase adapter | Deleted the RPC wrapper for `ops_retry_failed_community_alert`. |
| Source regression suite | Added a fail-closed assertion that the Android UI, ViewModel, repository, and adapter contain neither the retry action nor its RPC name. |

Supported Operations controls, incident handling, alert dashboard visibility, and guarded AI integration were not changed.

## Verification

| Check | Result |
|---|---|
| `python3 tools/tests/run_contract_tests.py` | 42/42 source contracts passed. |
| `testDebugUnitTest` | Passed. |
| `assembleDebug` | Passed. |

The debug APK remains a local build-verification artifact only. It was not distributed and does not constitute a production-release claim.

## Remaining boundary

The retained non-production source migrations still record the backend’s negative contract. The older archived migration that originally defined the retry function is not reapplied to the approved isolated project. A separate migration-history reconciliation is required before any fresh-environment bootstrap claim can be made. Overall release status remains **NO-GO**.
