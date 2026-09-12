# Phase 29 — Account-Scoped Community Upload Recovery

**Date:** 2026-08-26  
**Environment boundary:** Local Android source and local build validation only. **No Supabase migration, Storage operation, user-media access, synthetic live-session test, production change, Edge deployment, remote push, or APK distribution occurred.**

## Finding

The durable `community_upload_outbox` stored draft and media-path information but did not retain the account that created each row. The background worker selected pending rows without requiring an active matching user. On a shared device, a later signed-in account could therefore attempt recovery of another account’s staged media. Server Storage/RPC authorization should reject the cross-account operation, but relying on a server rejection is not an acceptable local persistence boundary.

## Hardening Implemented

| Layer | Change |
|---|---|
| Room schema | Added nullable `owner_user_id` and increased the database schema from v1 to v2. The explicit v1→v2 migration adds the column without destructive fallback. Legacy v1 rows remain unowned and are intentionally ineligible for automatic recovery. |
| Outbox row creation | New Community media rows store the authenticated author ID at creation. |
| DAO | Pending counts, pending recovery selection, per-draft reads, and draft deletion are all filtered by `owner_user_id`. |
| Worker | `CommunityUploadWorker` first requires an active real authenticated user. With no session it returns success without retrying or deleting anything; with a session it loads only that user’s pending rows. |
| Adapter | `resumeCommunityUpload` obtains the current authenticated owner, rejects a draft with no matching owned rows, and scopes staging cleanup/deletion to that owner. The initial author flow alone may finalize a deliberately text-only draft with an empty outbox; background recovery never enables that exception. The prior object-existence idempotency check remains intact. |
| Repository | The visible pending count is now collected only for the active real session. Recovery work is scheduled after a real session is restored or signed in, as well as after a failed Community post operation. |

> This is account isolation for durable local retry, not resumable/chunked transfer. It does not claim byte-level transfer continuation, cross-device recovery, or a user-visible progress protocol.

## Validation

| Validation | Result |
|---|---:|
| Source-contract suite | **52/52 passed** |
| `testDebugUnitTest` | **Passed** |
| `assembleDebug` | **Passed** |
| `lintDebug` | **Passed** |
| Fresh device/user-switch test | **Not performed**; requires a device/emulator, two isolated authenticated accounts, a deliberately interrupted upload, and no real user media. |

No lint baseline or suppression was added. The local debug build is not a release artifact or a production-readiness claim.

## Remaining Limits and No-Go Gates

The recovery still depends on the staged file remaining present on the device and on server-side finalization. Legacy unowned outbox rows are intentionally quarantined, not auto-deleted, and are not visible as pending work to a later account. A device-level test is still required to verify Room migration behavior, sign-out/sign-in account isolation, Worker scheduling, text-only post publication, and server-authorized finalization with synthetic media. Overall application status remains **NO-GO**.
