# Marketplace Security Handoff

**Repository:** `silvanotitusonline/RTC-Community-Production`  
**Optimisation branch:** `optimize/marketplace-production`  
**Scope:** Marketplace client, PostgreSQL RPCs, private media storage, route authorization and integration ownership  
**Status:** Source-controlled handoff only; this document does not authorize or perform production deployment.

## 1. Security model

Marketplace is deliberately **RPC-first**. Android code does not receive a service-role key, does not write Marketplace tables directly, and does not treat client navigation checks as an authorization boundary. The Android route policy is defense-in-depth and UX gating; PostgreSQL RPC assertions, row ownership/membership checks, lifecycle checks, storage policies and authenticated identity remain authoritative.

The security workstream's `20260828092000_marketplace_authorization_hardening.sql` is authoritative for invitation/acceptance trust-boundary hardening. This optimisation does not replace it. Marketplace-specific migrations in this branch are forward-only and execute after that security migration.

## 2. Identity and role ownership

| Boundary | Client responsibility | Backend authority / owner |
|---|---|---|
| Authenticated Marketplace access | Carry the normal Supabase authenticated session only. | Supabase Auth `auth.uid()` / `private.marketplace_actor()` and function grants. |
| Business editing | Show owner/editor workflows only from RPC-backed membership data. | `private.marketplace_assert_editor()` and active business membership rows. |
| Publication administration | Central `RouteAccessPolicy` allows `CONTENT_EDITOR` and `SYSTEM_ADMIN`. | Publication RPCs call the content-admin authorization primitive; PostgreSQL remains authoritative. |
| Review moderation | Central `RouteAccessPolicy` allows `MODERATOR` and `SYSTEM_ADMIN`. | Moderation/lifecycle RPC authorization remains authoritative. |
| Lifecycle suspension/reinstatement | UI exposes controls only to `SYSTEM_ADMIN` inside publication detail and only for supported backend-derived lifecycle states. | `marketplace_suspend_business` / `marketplace_reinstate_business` validate role and current lifecycle state. |
| Invitation creation/acceptance | Client must never grant ownership or trust invite payload role locally. | `20260828092000_marketplace_authorization_hardening.sql`: owner/manager invitation authority; only MANAGER/EDITOR; authenticated-email/token match; no OWNER grant. |
| Draft/public separation | Owner preview uses `marketplace_business_editor`, never the public detail RPC. | Revision state and `current_public_revision_id` decide what can become public. |

## 3. Android route authorization matrix

All Marketplace privileged routes are declared in `RtcRoute` and evaluated by the single `RouteAccessPolicy`. `ProtectedRoute` is used at each privileged NavGraph entry; no Marketplace-specific parallel role system exists.

| Route | Client roles | Server-side expectation |
|---|---|---|
| `community/marketplace/**` | Authenticated resident/staff session | Public catalogue RPCs still require authenticated execution where grants require it. |
| `account/marketplace/**` | Authenticated account holder | Owner/editor/member RPCs validate actor and business membership. |
| `admin/marketplace` | CONTENT_EDITOR, SYSTEM_ADMIN | Content-admin-protected queue RPC. |
| `admin/marketplace/business/{submissionId}` | CONTENT_EDITOR, SYSTEM_ADMIN | Content-admin-protected submission detail + publication decision RPCs. |
| `admin/marketplace/categories` | CONTENT_EDITOR, SYSTEM_ADMIN | No new direct-write client API is added here; any future mutation must use an explicitly reviewed admin RPC. |
| `admin/marketplace/featured` | CONTENT_EDITOR, SYSTEM_ADMIN | Same publication-admin trust boundary. |
| `admin/marketplace/analytics` | CONTENT_EDITOR, SYSTEM_ADMIN | No broader table read is introduced by the route. |
| `admin/marketplace/reviews` | MODERATOR, SYSTEM_ADMIN | Review moderation trust boundary; no publication rights implied. |

A route being visible or reachable is **not** evidence of server authorization. The server must continue rejecting unauthorized requests independently.

## 4. Discovery RPC inventory

| RPC | Android caller | Data class/use | Trust-boundary owner |
|---|---|---|---|
| `marketplace_home` | `MarketplaceDiscoveryRepository.home` | Featured, nearby, newest, top-rated, categories | Marketplace catalogue backend |
| `marketplace_search_businesses` | Legacy `search` compatibility path | Non-paginated published search | Marketplace catalogue backend |
| `marketplace_search_businesses_page` | `searchPage` | Bounded, ordered pagination; stable continuation | Marketplace catalogue backend; branch migration `20260828095000_*` |
| `marketplace_business_detail` | `detail` | Published business detail only | Marketplace catalogue backend |
| `marketplace_business_reviews` | `reviews` | Published reviews + rating aggregate | Marketplace review backend |
| `marketplace_save_business` | `save` | Authenticated saved-business toggle | Marketplace account collection backend |
| `marketplace_saved_businesses` | `savedBusinesses` | Account-scoped saved listings | Marketplace account collection backend |
| `marketplace_businesses_in_view` | `mapBusinessesInView` | Published map viewport markers | Marketplace catalogue/map backend |

### Discovery constraints

- Search radius is bounded in the client and server contract; the server remains authoritative.
- Pagination uses deterministic ordering and a stable business-ID tie-breaker.
- Public business detail resolves only the published revision.
- Draft preview never falls back to `marketplace_business_detail`.

## 5. Owner/editor RPC inventory

| RPC | Android caller | Security-sensitive behavior | Backend owner |
|---|---|---|---|
| `marketplace_my_businesses` | `myBusinesses` | Returns only memberships visible to authenticated actor | Marketplace membership backend |
| `marketplace_create_business_draft` | `createDraft` | Creates private draft; caller-owned idempotency key | Marketplace owner workflow backend |
| `marketplace_business_editor` | `editor` | Returns authenticated editor payload, including private draft structures | Marketplace owner workflow backend |
| `marketplace_save_identity` | `saveIdentity` | Identity, categories and public-contact consent payload | Marketplace owner workflow backend |
| `marketplace_upsert_location` | `saveLocation` | Draft location write; visibility remains explicit | Marketplace owner workflow backend |
| `marketplace_replace_location_hours` | `saveHours` | Atomically replaces weekly hours and special-date exceptions for selected draft location | Marketplace owner workflow backend |
| `marketplace_upsert_offering` | `saveOffering` | Structured service/product pricing and availability | Marketplace owner workflow backend |
| `marketplace_begin_media_upload` | `uploadMedia` | Allocates private object path/asset row; does not itself publish | Marketplace media backend |
| `marketplace_finalize_media_upload` | `uploadMedia` | Finalizes metadata only after private object upload | Marketplace media backend |
| `marketplace_delete_draft_media` | `deleteMedia` | Deletes/removes draft media only through authenticated owner workflow | Marketplace media backend |
| `marketplace_submit_business` | `submit` | Locks/submits the current revision for review | Marketplace owner/publication workflow backend |
| `marketplace_submission_status` | `status` | Owner-visible lifecycle/revision status | Marketplace owner workflow backend |
| `marketplace_archive_business` | `archive` | Archive mutation with backend state validation | Marketplace owner workflow backend |
| `marketplace_my_invitations` | `invitations` | Account-scoped invitation list | Marketplace membership backend |

### Owner mutation invariants

- Idempotency keys originate above the repository in the mutation tracker.
- A failed logical operation retains the same key for retry.
- A successful operation retires the key and the next logical operation receives a new key.
- Duplicate in-flight mutations are suppressed.
- Draft checkpoint recovery stores only the business identifier and editor step; private business data is reloaded from the backend.

## 6. Review RPC inventory

| RPC | Android caller | Client behavior | Backend authority |
|---|---|---|---|
| `marketplace_submit_review` | `saveReview` | Authenticated create/update review with retry-stable idempotency | Review ownership/business eligibility checks |
| `marketplace_delete_review` | `deleteReview` | Destructive UI confirmation required | Review ownership validation |
| `marketplace_vote_review_helpful` | `voteHelpful` | Toggle helpful state; reload count after mutation | Authenticated actor + unique helpful vote state |
| `marketplace_report_review` | `reportReview` | Exact structured reason codes: SPAM, HARASSMENT, HATE, PRIVACY, MISINFORMATION, OTHER | Moderation report backend |
| `marketplace_submit_owner_response` | `respond` | UI offers response only to users with an RPC-backed owned/editor business membership | Backend verifies editor authority for the review's business |
| `marketplace_my_reviews` | `myReviews` | Account-scoped review management | Authenticated review owner |

The UI's owner-response capability check is **advisory**. `marketplace_submit_owner_response` must remain the decisive authorization control.

## 7. Publication administration RPC inventory

| RPC | Android caller | Client workflow | Backend authority |
|---|---|---|---|
| `marketplace_admin_queue` | `queue` | Publication queue and metrics | Content-admin assertion |
| `marketplace_admin_submission_detail` | `detail` | Authoritative submitted revision, published revision, locations, offerings, media, verification and audit history | Content-admin assertion |
| `marketplace_assign_submission` | `assign` | Explicit assignment action | Publication workflow authorization |
| `marketplace_request_changes` | `requestChanges` | Confirmation + mandatory feedback | Content-admin assertion + submission state checks |
| `marketplace_approve_and_publish` | `publish` | Explicit confirmation before publication | Content-admin assertion + revision/submission lifecycle checks |
| `marketplace_reject_submission` | `reject` | Destructive confirmation + mandatory reason | Content-admin assertion + submission state checks |
| `marketplace_suspend_business` | `suspendBusiness` | System-admin UI only; published lifecycle only | Moderator/system backend assertion + PUBLISHED state check |
| `marketplace_reinstate_business` | `reinstateBusiness` | System-admin UI only; suspended lifecycle only | Moderator/system backend assertion + SUSPENDED state check |

After admin mutations the ViewModel reloads both the queue and any active submission detail. The UI therefore does not assume a mutation succeeded in producing a particular lifecycle state.

## 8. Private media storage boundary

**Bucket:** `rtc-marketplace-media`  
**Expected posture:** private; no public object URL is treated as a supported rendering contract.

Android media flow:

1. Image preparation executes on `Dispatchers.IO`.
2. EXIF/GPS metadata is removed by the local preparation layer.
3. `marketplace_begin_media_upload` allocates the server-approved object path.
4. Android uploads only to that path in `rtc-marketplace-media`.
5. `marketplace_finalize_media_upload` finalizes the draft asset.
6. Public/detail rendering asks the authenticated Supabase Storage client for a short-lived signed URL.
7. Signed URLs have a five-minute storage TTL and a shorter 4.5-minute in-memory cache lifetime.
8. Raw object paths remain identifiers, not public URLs.
9. Draft deletion uses the owner-authorized draft-media RPC rather than arbitrary object deletion from UI code.

### Prohibited client behavior

- No service-role key in Android resources, BuildConfig or source.
- No public-bucket assumption for Marketplace media.
- No arbitrary storage path supplied by user text.
- No table-level direct writes used as a shortcut around the RPC workflow.
- No client role check used as proof that a mutation is authorized.

## 9. Migration ownership and ordering

Current integration ordering relevant to this branch:

1. `20260828090000_explicit_rpc_only_denials.sql` — shared security workstream.
2. `20260828091000_edge_function_security_primitives.sql` — shared security workstream.
3. `20260828092000_marketplace_authorization_hardening.sql` — authoritative Marketplace invitation/acceptance hardening.
4. `20260828093000_restore_alert_scheduler_auth_chain.sql` — shared security workstream.
5. `20260828094000_marketplace_detail_hour_exceptions.sql` — Marketplace additive detail contract.
6. `20260828095000_marketplace_search_pagination.sql` — Marketplace additive paginated-search contract.

Do not renumber the security workstream migrations to fit this feature branch. If `main` advances again before integration, re-audit migration ordering and semantically port forward rather than overwriting concurrent security work.

## 10. Verification ownership

Before PR #11 leaves draft state, reviewers should require:

- Android JVM tests, lint, debug assembly and Android-test Kotlin compilation on the final head.
- Source regression contracts.
- Shared Edge authorization tests.
- Marketplace route-policy matrix tests.
- Marketplace presentation decomposition/geometry-token structural tests.
- Non-production PostgreSQL policy/pgTAP checks when `RTC_NONPROD_DATABASE_URL` is available.
- Staging Marketplace smoke data only when staging seed credentials are available.
- No production database mutation as part of feature verification.

Runtime device smoke testing should be recorded separately from compile-time Android-test verification. A missing emulator/device target must be documented as a verification gap rather than silently reported as passed.

## 11. Integration rule

PR #11 is an isolated donor/integration PR. It must remain unmerged until the security/review workstream confirms the final diff against then-current `main`. If `main` has advanced, preserve the centralized `RouteAccessPolicy`, the security hardening migrations and any newer Marketplace route-family architecture; port this branch's behavior semantically rather than resolving conflicts by wholesale replacement.
