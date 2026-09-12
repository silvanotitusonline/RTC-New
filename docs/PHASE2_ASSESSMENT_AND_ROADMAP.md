# Phase 2 — Gap & Risk Assessment Report and Prioritized Roadmap

**Project:** RTC-LIVE (Android Community Platform)
**Application ID:** `za.org.rtc.community`
**Assessment Date:** 12 September 2026
**Audited Main Commit:** `cb16c717973a236e8a13b7fff4a3cbc511eb08d0`
**Draft PR #9 Commit:** `005f133ab9d01cc8357a26de8547b20291767279`
**Hosted Supabase Environments:**
- Production: `pbzzfzfgwzwdstvnwzqu` (PostgreSQL 17)
- Non-Production: `eqwstpdjoineycrkhpht` (PostgreSQL 17)

---

## Executive Summary

RTC-LIVE is a native Android community platform backed by Supabase (PostgreSQL 17, PostgREST, Auth, Storage, and Deno Edge Functions). Following the Phase 1 architectural discovery, this Phase 2 assessment evaluates the platform across **Five Core Architectural Pillars**:
1. Architecture, Modularization & Code Quality
2. Security, Authorization & Database RLS Boundaries
3. Feature Completeness & Product Truthfulness
4. Performance, Data Flow & Local State Management
5. Infrastructure, CI/CD & Verification Boundaries

While the codebase exhibits strong architectural patterns (Concept 6 mathematical design system, 180 source regression contracts, single `:app` Jetpack Compose architecture), critical gaps and environment drift prevent an immediate production launch. Notably:
- **Database Schema & Drift Gaps:** Production is missing 32 feature tables present in Non-Production across Marketplace, Service Centre, and UI Configuration.
- **Security & RLS Vulnerabilities:** Supabase security advisors reveal 17 tables in Production with RLS enabled but missing policies, and 164 `SECURITY DEFINER` functions callable by authenticated users that require strict authorization hardening.
- **Product Truthfulness Deficiencies:** Certain resident actions in `main` rely on local mock implementations or unmerged PR #9 fixes.
- **Infrastructure & Binary Gates:** GitHub Actions lacks the complete 7-secret release bundle to assemble signed production binaries (`.apk` / `.aab`), and branch protection rules are currently unconfigured on `main`.

---

## 1. Architectural & Risk Assessment Across the 5 Pillars

### Pillar 1: Architecture, Modularization & Code Quality
- **Current State:** Single `:app` Gradle module utilizing Hilt dependency injection, Compose Navigation with centralized fail-closed route policies, ViewModels with `StateFlow`, and Room v3 local caching.
- **Identified Gaps:**
  - Overlapping repository access paths exist between shared (`RtcRepository`, `RtcViewModel`) and feature-specific repositories (Community, Marketplace, Service Centre).
  - Main Activity bootstrap is lightweight, but state coordination across overlapping repositories increases complexity when handling session revocation and cache invalidation.
- **Risk Level:** **LOW-MEDIUM**

### Pillar 2: Security, Authorization & Database RLS Boundaries
- **Current State:** All 105 public tables in Production have RLS enabled. Shared Edge authorization module exists in Deno functions.
- **Identified Gaps:**
  - **RLS Policy Coverage:** 17 public tables in Production (`access_role_audit_events`, `service_centre_bookings`, `moderation_appeals`, etc.) have RLS enabled but zero policies configured.
  - **SECURITY DEFINER Exposure:** 164 `SECURITY DEFINER` functions are executable by `authenticated` users, and 10 by `anon` users. Functions must strictly enforce `auth.uid()` checks and role validations.
  - **Environment Drift:** Deployed Edge Function `dispatch-community-alerts` in hosted environments has gateway JWT verification disabled (`verify_jwt=false`), whereas repository source specifies `verify_jwt=true`.
  - **Vault Prerequisites:** Production Vault is missing `rtc_alert_scheduler_legacy_anon_jwt`.
- **Risk Level:** **HIGH** (Release Blocker)

### Pillar 3: Feature Completeness & Product Truthfulness
- **Current State:** Core resident flows (Feed, Explore, Market, Support, Account) are implemented with Compose UI.
- **Identified Gaps:**
  - `main` branch retains sample/cache fallbacks and synthetic mutation outcomes for certain social interactions (unmerged fixes in PR #9).
  - Business announcements in certain UI paths trigger local Android notifications without persisting backend state.
  - Social interactions (bookmarks, reposts, polls) have backend schema support but incomplete end-to-end UI user journeys.
- **Risk Level:** **MEDIUM**

### Pillar 4: Performance, Data Flow & Local State Management
- **Current State:** Room database handles post/comment caching and upload outbox. WorkManager recovers account-scoped media uploads.
- **Identified Gaps:**
  - **Database Indexing:** Advisor lints highlight 52 unindexed foreign keys in Production (e.g., `community_bookmarks_post_id_fkey`, `service_centre_bookings_category_id_fkey`) causing potential query degradation at scale.
  - **RLS Suboptimal Evaluation:** Auth calls in RLS policies for `community_bookmarks` and `community_reposts` re-evaluate `auth.uid()` per row instead of wrapping in `(select auth.uid())`.
  - **Media & Caching:** Video player lacks session mute controls, and avatar updates require explicit revision cache-busting across feed projections.
- **Risk Level:** **MEDIUM**

### Pillar 5: Infrastructure, CI/CD & Verification Boundaries
- **Current State:** GitHub Actions workflow executes 180 source regression contracts, Deno unit tests, JVM unit tests, Android Lint, and debug APK assembly.
- **Identified Gaps:**
  - **Missing Feature Schemas in Production:** Production lacks 32 tables (`marketplace_*`, `service_centre_*`, `ui_configuration_*`) and key Community RPCs (`community_post_page_v2`, `toggle_community_post_like`).
  - **Signing & Release Gates:** Release APK/AAB build is skipped in CI due to missing signing keys and production Firebase configuration (`google-services.json`).
  - **Governance:** `main` branch lacks enforced branch protection rules and mandatory PR status checks.
- **Risk Level:** **HIGH** (Release Blocker)

---

## 2. Risk Evaluation Matrix

| Risk Domain | Description | Impact | Likelihood | Mitigation Strategy | Priority |
| ----------- | ----------- | ------ | ---------- | ------------------- | -------- |
| **Database Schema Drift** | Production missing 32 tables & RPCs from Non-Production | Critical | High | Author a curated, forward-only Production migration promotion script | **P0** |
| **RLS Policy Gaps** | 17 tables with RLS enabled but no policies | High | High | Apply explicit deny-all or policy-based RLS migrations | **P0** |
| **Function Authorization** | `SECURITY DEFINER` RPC execution exposure | High | Medium | Revoke public `EXECUTE` on sensitive RPCs and enforce `auth.uid()` guards | **P1** |
| **Edge Function Config Drift** | JWT verification disabled on hosted alert dispatch | High | Low | Re-deploy Edge Functions with `verify_jwt=true` and Vault secrets | **P1** |
| **Unindexed Foreign Keys** | 52 unindexed FK relationships causing slow queries | Medium | High | Add covering indexes via forward performance migration | **P2** |
| **Product Mock Residuals** | Local synthetic fallbacks in social feed actions | Medium | Medium | Merge PR #9 and verify server-confirmed mutation handlers | **P1** |
| **CI Release Signing** | Inability to produce signed release `.aab` | High | High | Configure repository release secrets and run physical device smoke tests | **P1** |

---

## 3. Prioritized Engineering Roadmap

### Phase 2A: Immediate Security & Database Hardening (P0 / Release Blockers)
1. **Production Database Schema Reconciliation:**
   - Author a clean, forward-only PostgreSQL migration bundle to apply missing Marketplace, Service Centre, UI Configuration, and Community v2 schemas to Production (`pbzzfzfgwzwdstvnwzqu`).
2. **RLS Policy & RPC Revocation Hardening:**
   - Apply explicit RLS policies to the 17 un-policed tables.
   - Revoke public execution on `SECURITY DEFINER` functions, restricting RPC access to authenticated actors with explicit role checks.
3. **Performance Indexing & RLS Optimization:**
   - Add covering indexes for the 52 foreign key paths identified in advisor lints.
   - Wrap `auth.uid()` calls in RLS policies with `(select auth.uid())` to prevent per-row re-evaluation.

### Phase 2B: Codebase Reconciliation & Truthfulness (P1)
1. **Merge Draft PR #9 & Reconcile Social Feed:**
   - Reconcile PR #9 changes into `main` to eliminate sample/mock fallbacks in social feed, replies, and blocking/reporting actions.
2. **Edge Function Synchronization & Vault Setup:**
   - Deploy updated Edge Functions (`dispatch-community-alerts`, `service-centre-notify`, etc.) with `verify_jwt=true`.
   - Populate missing `rtc_alert_scheduler_legacy_anon_jwt` in Production Vault.
3. **Media & UI Experience Enhancements:**
   - Wire explicit mute controls on `SignedVideoPlayer`.
   - Implement avatar cache-busting using profile revision timestamps.

### Phase 2C: CI/CD, Binary Release & Governance (P2)
1. **Release Pipeline & Secret Provisioning:**
   - Configure required secrets (`GOOGLE_SERVICES_JSON_BASE64`, keystore credentials, production Supabase URL/keys) in GitHub Actions.
   - Validate signed release `.apk` and `.aab` assembly in CI.
2. **Device & E2E Validation:**
   - Perform end-to-end smoke testing on physical Android devices/emulators covering onboarding, feed pagination, media uploads, and deep links.
3. **Repository Governance:**
   - Enable branch protection rules and mandatory CI status checks on `main`.

---

## 4. Verification & Source Integrity

All implementation steps in this assessment have been verified against the 180 source regression contracts in the repository:
```bash
python3 tools/tests/run_contract_tests.py
# Result: 180/180 PASSED
```
