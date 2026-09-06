# RTC Community — Final Production Readiness Report

**Assessment date:** 29 August 2026  
**Repository:** `silvanotitusonline/RTC-Community-Production`  
**Release branch:** `release/final-production-readiness`  
**Pull request:** #21 — `release: final production-readiness hardening and GO/NO-GO`  
**Validated application head before this report-only commit:** `ff2636cccb1a6febd4584b76dda6ea78794eaecb`  
**Main baseline:** `85856ec711af84280dec8e3b76bee0b2938193f2`  
**Production Supabase:** `pbzzfzfgwzwdstvnwzqu`  
**Non-Production Supabase:** `eqwstpdjoineycrkhpht`

> This report supersedes `docs/PRODUCTION_READINESS_REPORT.md` for release decision purposes. The earlier report remains in source control as historical evidence.

## Executive decision

### Overall classification: CONDITIONAL GO FOR CONTROLLED RELEASE PROMOTION

The current Android/application source is a strong, independently verified release candidate. The final-readiness branch has passed the full source contract, Edge authorization, JVM, Android Lint, debug APK and Compose/AndroidTest compilation gates on an exact source head.

However, **this is not a GO for immediate public Production launch**.

Immediate launch remains **NO-GO** until all of the following external and backend gates are cleared:

1. Production Supabase is reconciled through a separately authorized, curated, forward-only promotion.
2. The current Production Edge Functions and their database security dependencies are brought to the verified current contract.
3. Production storage, scheduler/Vault prerequisites and Service Centre payment/notification secrets are configured and verified.
4. A signed release APK/AAB is built with the complete Production runtime, Firebase and signing bundle.
5. The signed candidate is installed and exercised on a real Android device/emulator, including lifecycle, deep links, notifications, payment return paths and accessibility smoke.
6. The final merged `main` head is re-verified after PR integration.

No Production Supabase mutation was performed during this final-readiness assessment.

---

## 1. Final verified Android/source baseline

Authoritative source head before this report-only documentation commit:

`ff2636cccb1a6febd4584b76dda6ea78794eaecb`

Authoritative verification:

- GitHub Actions workflow: **Android Production Verification**
- Run: **#435 / `33228681556`**
- Source regression contracts: **173/173 passed**
- Shared Edge authorization tests: **14/14 passed**
- JVM unit tests: **GREEN**
- Android Lint: **GREEN**
- Debug APK assembly: **GREEN**
- Compose/AndroidTest APK compilation: **GREEN**
- Debug verification artifact upload: **GREEN**
- Reconstructed CI credential cleanup: **GREEN**
- Release-candidate gate: executed and correctly skipped because the complete Production runtime/Firebase/signing secret bundle was not configured

Debug verification artifact:

- Name: `rtc-community-debug-verification`
- Artifact ID: `9707966917`
- Size: `40,191,704` bytes
- SHA-256: `665a3acc0b995ffaa068b69e094ca8000680917deca425c4505a6fc6b2e7a290`

This artifact is a **debug verification artifact**, not a signed Production release.

The CI workflow was also migrated from Node-20-targeted JavaScript actions to current Node-24-capable action majors. The prior Node-20, deprecated setup-java and punycode action-runtime warnings are absent from run #435.

---

## 2. Final-readiness phase outcomes

### Phase 1 — Root application coordinator decomposition: GREEN

The oversized root `RtcViewModel` was decomposed into bounded coordinators and shared UI/error state while retaining a compatibility facade. The single-Activity architecture was preserved.

### Phase 2 — Navigation and authorization single-source: GREEN

Unknown `admin/*` destinations now fail closed. Known dynamic protected routes are evaluated explicitly before the fallback denial.

### Phase 3 — Safe error / resilience convergence: GREEN

Resident-facing Community, Marketplace and Service Centre error boundaries no longer surface raw backend exception detail. Top-level Community refresh failures are sanitized.

### Phase 4 — Authentication and account lifecycle: GREEN

Sign-out now clears account/staff scoped local state even when remote sign-out fails. SDK `sessionStatus` is observed so `NotAuthenticated` and unrecoverable refresh failure fail closed, while valid storage/refresh authentication can rehydrate the Supabase session.

### Phase 5 — Resident feature reliability: GREEN

Resident refresh failure is visibly surfaced with retry/dismiss behavior; stale data is no longer labelled as live; saved-draft deletion requires explicit confirmation; Service Centre detail loading reports a truthful loading state; Marketplace save failures surface visible feedback without destroying loaded detail.

### Phase 6 — Offline/reconnection and account isolation: GREEN

Local drafts are account-scoped through Room v3 instead of being globally keyed by draft area. Legacy unowned drafts are deliberately not attributed to the next account. Existing authenticated-owner WorkManager upload recovery remains connectivity-constrained and owner-checked.

Authoritative Phase 6 checkpoint:

- Head: `be14e92a8ed1c90441add8cd38d0ead2da56835c`
- CI #433 / `33228151778`
- 172/172 source contracts
- 14/14 Edge authorization tests
- JVM/Lint/APK/Compose GREEN
- Artifact `9707680677`
- SHA-256 `f6f80927fb43b4636ed397259492958f149fb6f9f0f6248f6e684088a0c3fddf`

### Phase 7 — Performance / release tooling: GREEN

No release-blocking main-thread pattern requiring speculative app churn was found. The measured CI/runtime maintenance defect was addressed by upgrading the five Node-20-targeted JavaScript actions while preserving all build, secret, cleanup and artifact gates.

Authoritative Phase 7 checkpoint is the final source baseline run #435 described above.

### Phase 8 — Repository/governance cleanup: COMPLETE WITH NAMED LIMITATION

The following historical donor PRs were disposition-noted and closed **unmerged**:

- #18 Service Centre donor
- #14 temporary Community reconciliation
- #11 Marketplace donor
- #8 Supabase security v2 donor
- #7 Brand optimisation donor
- #4 historical Supabase hardening donor
- #3 historical Brand/customizer donor
- #1 Concept 6 donor

Their source branches were retained as audit/recovery evidence rather than destructively deleted.

Before closing #1, the current branch was checked for the valuable Concept 6 behavior: the mathematical design-system documentation and live `RtcMath` source remain present with the centralized Golden Ratio/Fibonacci/π relationships and 600/840dp adaptive breakpoints.

After cleanup, PR #21 is the only open PR.

#### Governance limitation

`main` currently reports `protected:false`, with required status-check enforcement off. Repository rulesets are unavailable on the current private-repository plan surface, and branch-protection detail/write enforcement is unavailable through the connected GitHub integration.

Therefore the Android verification workflow is **operationally required by this release process but not currently enforceable by GitHub as a mandatory merge gate**. This is a release-control limitation, not an application-code defect.

---

## 3. Phase 9 — Release binary/device gate

### Status: EXTERNALLY GATED — NOT GREEN

What is verified:

- Release tasks are fail-closed when Production runtime/signing values are missing.
- Release minification and resource shrinking remain enabled.
- Debug Android compilation and package assembly are verified by CI.
- A source-controlled Appetize QA harness exists for a non-production synthetic resident session.

What is **not** verified:

- No signed release APK/AAB was produced by run #435.
- No real Android device/emulator/Appetize execution was available in this assessment environment.
- AndroidTest/Compose tests were compiled, not executed on-device.
- Production Firebase runtime behavior was not exercised.
- Install/launch/process-death/lifecycle restoration was not exercised on hardware/emulator.
- Notification cold/warm deep links were not executed end-to-end on device.
- Service Centre hosted-payment browser return and webhook-confirmation UX were not exercised on device.
- TalkBack, font scaling, orientation/window size, light/dark/system and accessibility smoke were not executed on device.

The existing Appetize runner also requires an externally uploaded build ID (`RTC_APPETIZE_BUILD_ID`), which was not available through the connected tools.

This gate must remain explicit. AndroidTest compilation must never be represented as real device execution.

---

## 4. Phase 10 — Production Supabase read-only reconciliation

### Status: READ-ONLY RECONCILIATION COMPLETE — PROMOTION NOT EXECUTED

The Production and Non-Production projects were compared directly using read-only migration, schema, privilege, function, storage, scheduler/Vault, Edge Function and security-advisor queries.

No Production migration, Edge deployment, secret write, scheduler mutation, storage mutation or data write was performed.

### 4.1 Migration histories cannot be replayed wholesale

Production ends at the August 25 release-candidate hardening series.

Non-Production was subsequently reconstructed and received Brand/UI, Marketplace, Supabase-security v2, Community pagination/like and Service Centre changes through August 28.

The repository migration set contains:

- reconstruction/synthetic entries;
- explicitly non-production-only Brand/UI foundation work;
- Marketplace migrations explicitly held to non-production pending owner authorization;
- later forward hardening/repair migrations.

Therefore **Non-Production migration history must not be replayed wholesale into Production**.

A new Production-specific forward reconciliation must be authored against actual Production state.

### 4.2 Decisive Production schema gap

Fresh Production database inspection found **zero** current feature tables in these families:

- `marketplace_*`
- `service_centre_*`
- `ui_configuration_*`

Non-Production contains **32** such tables:

- 24 Marketplace
- 5 Service Centre
- 3 UI configuration

All 32 use RLS. The five Service Centre core tables additionally use forced RLS.

Direct-table privilege verification in Non-Production is clean:

| Family | Tables | anon direct DML | authenticated direct DML |
|---|---:|---:|---:|
| Marketplace | 24 | 0 | 0 |
| Service Centre | 5 | 0 | 0 |
| UI configuration | 3 | 0 | 0 |

The current application source therefore **cannot be publicly launched against the current Production database**, because required schemas/RPCs do not exist there.

### 4.3 RPC / trust-boundary verification in Non-Production

| Family | Functions | SECURITY DEFINER | Missing pinned search_path | anon executable | authenticated executable |
|---|---:|---:|---:|---:|---:|
| Marketplace | 56 | 56 | 0 | 0 | 45 |
| Service Centre | 19 | 19 | 0 | 0 | 17 |
| UI configuration | 10 | 10 | 0 | 1 | 10 |

The single anonymous UI execution path is the intentional public effective-theme read.

Supabase advisor warnings about authenticated users being able to execute SECURITY DEFINER RPCs must therefore be interpreted with their server-side authorization contracts. They are not equivalent to direct-table exposure.

For Production's existing `RLS enabled/no policy` INFO entries, a direct privilege query confirmed all 13 affected existing tables deny SELECT and writes to both `anon` and `authenticated`. Those tables are intentional RPC-only boundaries.

### 4.4 Community Production gap

Production currently lacks:

- `community_post_page_v2(timestamp with time zone, uuid, integer)`
- `toggle_community_post_like(uuid)`

Non-Production has both in their expected forms. The v2 page function is SECURITY INVOKER, pins `search_path=public, pg_temp`, denies anon execute and grants authenticated execute.

### 4.5 Edge Function Production drift

Production currently has only the original six Edge Functions.

Non-Production has those six plus:

- `service-centre-payment-create`
- `service-centre-payment-webhook`
- `service-centre-notify`

All six shared deployed source hashes differ between Production and Non-Production.

The most important security drift is `dispatch-community-alerts`:

- Production: older standalone implementation, `verify_jwt=false`;
- Non-Production/current repository contract: shared `_shared/auth.ts` scheduler guard/audit, `verify_jwt=true`.

Current repository `supabase/config.toml` expects `verify_jwt=true` for all six original RTC functions.

### 4.6 Edge security primitive dependency

Production is missing:

- `public.edge_function_rate_limits`
- `public.claim_edge_function_rate_limit(...)`

Non-Production contains both in the current hardened form. Direct client access to the rate-limit table is denied, and the claim RPC is service-role-only with bounded inputs.

The database primitive must be promoted before deploying Edge Functions that depend on `enforceRateLimit`.

### 4.7 Alert scheduler/Vault prerequisite

Both projects contain `rtc-community-alert-schedule`.

The hardened scheduler requires four Vault names:

1. `rtc_alert_dispatch_secret`
2. `rtc_alert_scheduler_project_url`
3. `rtc_alert_scheduler_publishable_key`
4. `rtc_alert_scheduler_legacy_anon_jwt`

Production currently has the first three but is missing `rtc_alert_scheduler_legacy_anon_jwt`.

Non-Production has all four.

The hardened scheduler resolves the values from Vault at execution time; it does not embed secret material in cron SQL/source.

### 4.8 Storage gap

Production currently has none of the current Marketplace/UI buckets.

Non-Production has:

- `rtc-marketplace-media` — private — 10 MiB limit
- `rtc-marketplace-verification` — private — 10 MiB limit
- `rtc-ui-assets` — private — 8 MiB limit

The buckets and final scoped policies must be included in Production reconciliation.

### 4.9 Brand/UI promotion cannot reuse non-production history blindly

The existing UI-configuration foundation migration explicitly states it is **non-production Phase 1 only**.

The UI v2 migration likewise states that Production deployment is out of scope and assumes the Phase 1 foundation already exists.

Production currently has no UI-configuration tables.

Therefore Production needs a clean Production-specific foundation representing the intended final v2 schema, grants and storage policies, rather than replaying or relabelling the non-production files.

### 4.10 Marketplace before Service Centre

The Marketplace foundation is also explicitly held to Non-Production until an authorized Production promotion.

Service Centre then references Marketplace categories, businesses and offerings. Production dependency order is therefore:

**Marketplace → Service Centre**

### 4.11 Service Centre Production runtime prerequisites

Production Service Centre activation requires, at minimum:

- standard Supabase URL/publishable/service-role runtime values;
- `YOCO_SECRET_KEY`;
- HTTPS `SERVICE_CENTRE_PAYMENT_RETURN_URL`;
- `YOCO_WEBHOOK_SECRET`;
- a shared `SERVICE_CENTRE_INTERNAL_NOTIFY_SECRET` for webhook → notification handoff;
- Firebase messaging credentials via `GOOGLE_SERVICE_ACCOUNT_JSON` or the guarded database credential RPC;
- a valid Firebase project identifier from `GOOGLE_CLOUD_PROJECT_ID` or the service-account credential.

The payment webhook intentionally runs without Supabase JWT verification because it authenticates Yoco's HMAC webhook signature.

`service-centre-notify` intentionally supports a non-JWT internal path for payment confirmation, but the internal secret is restricted to the `SERVICE_BOOKING_CONFIRMED` event. Other caller-driven events authenticate the Supabase user and apply the shared Edge rate limit.

### 4.12 Accepted Supabase plan-tier limitation

Leaked-password protection remains disabled. This was previously owner-accepted because the current Supabase plan does not expose that capability.

This remains a named security exception but is **not** the decisive reason the current release cannot launch. Backend parity, signed binary and device execution are the decisive gates.

---

## 5. Required Production promotion procedure

**Do not execute this procedure without a separate explicit Production authorization.**

### Gate A — Author the forward reconciliation

Create a new Production-specific migration/reconciliation bundle derived from actual Production state. It should produce the verified final schema without replaying synthetic/non-production history.

The bundle must include, in dependency-safe order:

1. final Production UI-configuration foundation/v2 schema and private asset bucket/policies;
2. final Marketplace schema, account collections, storage and RPC contracts;
3. explicit RPC-only denial hardening;
4. Marketplace authorization/search/replay hardening;
5. Edge rate-limit/security primitives;
6. current Community pagination/like parity;
7. Service Centre schema/RPCs after Marketplace dependencies exist;
8. final storage-policy repairs/scoping.

### Gate B — Provision Production-only prerequisites

Provision/validate without exposing secret material:

- missing `rtc_alert_scheduler_legacy_anon_jwt` in Vault;
- Yoco checkout secret;
- Yoco webhook secret;
- Service Centre internal notification secret;
- HTTPS payment return URL;
- Firebase messaging credential/project configuration;
- Android Production runtime/Firebase/signing CI bundle.

### Gate C — Rehearse first

Rehearse the exact Production-specific forward bundle against an isolated environment representing current Production schema.

Do not use Non-Production migration timestamps as the authoritative Production replay plan.

### Gate D — Explicit owner authorization

Only after reconciliation and rehearsal evidence exists should the owner explicitly authorize mutation of `pbzzfzfgwzwdstvnwzqu`.

### Gate E — Database promotion

Apply only the reviewed forward bundle in its dependency order.

Then verify:

- migration history;
- table/RLS/forced-RLS state;
- direct anon/authenticated grants;
- SECURITY DEFINER and pinned search paths;
- storage buckets and policies;
- scheduler job and Vault prerequisite names;
- security advisors;
- relevant authenticated/non-authenticated RPC behavior.

### Gate F — Edge deployment

After database dependencies exist, deploy:

- the current six shared RTC Edge Functions;
- `service-centre-payment-create`;
- `service-centre-payment-webhook`;
- `service-centre-notify`.

Preserve the current intended JWT/custom-auth configuration from `supabase/config.toml`.

### Gate G — Signed binary and device release candidate

With the complete Production runtime/Firebase/signing bundle:

1. assemble signed release APK and AAB;
2. preserve minification/resource shrinking;
3. install the signed APK on an Android device/emulator;
4. execute resident, protected workspace and account lifecycle smoke;
5. test notification cold/warm deep links;
6. test Marketplace and Service Centre navigation;
7. test Yoco checkout return and webhook-confirmed booking state;
8. test media playback/upload recovery;
9. test process/lifecycle restoration;
10. test TalkBack, font scaling and responsive/orientation behavior.

### Gate H — Merge and post-merge verification

Merge PR #21 only after the applicable release blockers are accepted/cleared.

Then require a **fresh workflow on the resulting `main` head** before any release tag/public deployment decision.

---

## 6. Governance and operational conditions

Until branch protection can be enabled/enforced, the release owner must operationally require:

- no direct unverified merge to `main`;
- exact-head Android Production Verification before merge;
- fresh `main` verification after merge;
- no Production Supabase mutation outside an explicitly authorized promotion step;
- no secret material committed to source;
- no use of historical donor branches as migration/deployment sources.

Historical donor branches remain audit/recovery references only.

---

## 7. Final GO / NO-GO matrix

| Area | Status | Decision |
|---|---|---|
| Application architecture | GREEN | GO |
| Route authorization / trust boundary | GREEN | GO |
| Safe error handling | GREEN | GO |
| Authentication/account lifecycle | GREEN | GO |
| Resident reliability | GREEN | GO |
| Offline/account isolation | GREEN | GO |
| Android source contracts | 173/173 GREEN | GO |
| Shared Edge auth tests | 14/14 GREEN | GO |
| JVM / Android Lint | GREEN | GO |
| Debug APK / AndroidTest compile | GREEN | GO for source verification only |
| Release tooling | GREEN | GO |
| Repository donor cleanup | COMPLETE | GO |
| GitHub enforced main protection | NOT AVAILABLE | CONDITIONAL / governance limitation |
| Signed release APK/AAB | NOT PRODUCED | NO-GO for public launch |
| Real device/emulator execution | NOT EXECUTED | NO-GO for public launch |
| Production Marketplace schema | MISSING | NO-GO for public launch |
| Production UI configuration schema | MISSING | NO-GO for public launch |
| Production Service Centre schema | MISSING | NO-GO for public launch |
| Production Community v2/like RPC parity | MISSING | NO-GO for public launch |
| Production Edge Function source/auth parity | STALE | NO-GO for public launch |
| Production Edge rate-limit primitive | MISSING | NO-GO for public launch |
| Production Marketplace/UI storage | MISSING | NO-GO for public launch |
| Hardened scheduler legacy JWT Vault prerequisite | MISSING | NO-GO for hardened alert path |
| Service Centre Yoco/Firebase Production runtime | NOT YET VERIFIED | NO-GO for Service Centre activation |
| Leaked-password protection | ACCEPTED PLAN EXCEPTION | CONDITIONAL |

---

## 8. Final release decision

### CONDITIONAL GO — controlled promotion preparation may proceed

The current repository/source is sufficiently hardened and verified to move into a **controlled Production promotion rehearsal**.

### NO-GO — immediate public Production launch

Do **not** publish the current Android build as the Production release and do **not** point the current full feature set at the existing Production Supabase environment yet.

The immediate launch blockers are concrete, observable and external to the already-verified source baseline:

1. required Production backend feature schemas/RPCs/storage are absent;
2. current Edge Function security/source parity is not deployed to Production;
3. required scheduler and Service Centre Production prerequisites are incomplete;
4. no signed release APK/AAB has been produced;
5. no real device/emulator release smoke has been executed;
6. GitHub cannot currently enforce the verified workflow as a protected `main` merge requirement.

Once those conditions are cleared through the gated procedure above, issue a new release decision from fresh Production/backend, signed-binary, device and post-merge evidence.

---

## 9. Evidence location

Detailed phase-by-phase evidence, RED→GREEN checkpoints, CI run IDs, artifact digests, donor PR dispositions, Phase 9 device-gate findings and Phase 10 read-only Production reconciliation are recorded in PR #21.

The exact CI result for this **report-only commit** must also be recorded on PR #21 after it completes; the report itself intentionally does not create a second documentation-only commit merely to embed its own resulting SHA/run number.
