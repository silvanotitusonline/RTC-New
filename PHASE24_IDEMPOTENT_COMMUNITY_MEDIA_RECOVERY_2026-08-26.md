# Phase 24 — Idempotent Community Media Recovery

**Date:** 26 August 2026
**Scope:** Restored Android feature-branch reconciliation.
**Environment:** Local Android source workspace only. The approved isolated non-production Supabase project and production were not changed during this phase.

## Finding

The restored Community upload outbox writes a durable row before each bounded staged-file upload. However, if a Storage upload succeeds and the process stops before the row is recorded as `DONE`, a later retry attempted the same non-upserting upload and could fail because the exact object already existed.

The recovered non-production Storage policy permits an authenticated author to select an object only beneath their own active Community draft path. This owner-only selection capability is sufficient to make the mobile retry flow idempotent without changing object ownership, widening Storage updates, or using a service credential.

## Corrective change

`resumeCommunityUpload` now creates one authenticated bucket reference and checks the exact queued draft object path before uploading. When the object already exists, the retry marks the durable row complete and proceeds to the existing server finalization path. When it does not exist, the application continues to use bounded streaming `UploadData`, `upsert = false`, and the established media-type limits.

| Scenario | Resulting behavior |
|---|---|
| Initial upload | Object does not exist; bounded non-upserting upload proceeds. |
| Process interruption after object creation, before `DONE` state | Retry detects the same author-owned draft object; no duplicate upload is attempted. |
| Missing staged file, no stored object | Recovery fails rather than inventing content; existing WorkManager retry behavior applies. |
| Cross-user or public object access | Not enabled; access remains subject to existing Storage RLS. |

No Storage policy, bucket setting, migration, or production resource changed during this source checkpoint.

## Regression coverage and verification

A source contract now requires the exact authenticated `bucket.exists(path)` check before the existing `upsert = false` streaming upload.

| Check | Result |
|---|---|
| `python3 tools/tests/run_contract_tests.py` | 45/45 source contracts passed. |
| `testDebugUnitTest` | Passed. |
| `assembleDebug` | Passed. |

The generated debug APK is local build-verification output only and was not distributed. This work does not constitute a production-release decision.

## Remaining boundary

This correction provides idempotent object creation recovery for retained staged files. It is not a complete resumable/chunked transfer protocol, does not prove behavior on physical devices, and does not replace the pending real-session Android media integration tests. Overall release status remains **NO-GO**.
