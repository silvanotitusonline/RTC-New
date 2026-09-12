# RTC Community Production Integration Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple source verification from live non-production credentials while making release builds require explicit production runtime, Firebase, and signing material.

**Architecture:** Keep all feature code untouched. Harden only Gradle release configuration, GitHub Actions credential materialization, ignore rules, source contracts, and integration documentation. Debug verification may use non-routable placeholders; release tasks fail closed.

**Tech Stack:** Android Gradle Kotlin DSL, GitHub Actions, Python source-contract tests, Firebase Android configuration, Supabase Kotlin BuildConfig runtime values.

**Spec:** `docs/superpowers/specs/2026-08-28-production-integration-foundation-design.md`

## Global Constraints

- Current post-PR-#6 `main` architecture is authoritative.
- Do not modify Marketplace, Community, Supabase migrations/functions, or Brand/Experience feature implementation.
- Do not add secrets to source control.
- Do not add a Supabase service-role key to Android configuration.
- Debug/source verification must run without live Supabase credentials.
- Release tasks must require explicit production runtime configuration and signing material.
- Keep all existing PR #6 structural regression contracts passing.

---

### Task 1: Add release-boundary source contracts

**Files:**
- Create: `tools/tests/test_production_integration_foundation.py`
- Read: `.github/workflows/android-ci.yml`
- Read: `app/build.gradle.kts`
- Read: `.gitignore`

**Interfaces:**
- Consumes: repository source files as plain text.
- Produces: source contracts automatically discovered by `tools/tests/run_contract_tests.py`.

- [ ] **Step 1: Add failing contracts for the desired boundary**

The tests must assert that:

```python
assert 'release.runtime.properties' in gradle
assert 'supabase.production.url' in gradle
assert 'supabase.production.publishableKey' in gradle
assert 'releaseRequested' in gradle
assert 'Release builds require signing.properties' in gradle
assert 'release.runtime.properties' in gitignore
assert 'RTC_PROD_SUPABASE_URL' in workflow
assert 'RTC_PROD_SUPABASE_PUBLISHABLE_KEY' in workflow
assert 'RTC_ANDROID_KEYSTORE_BASE64' in workflow
assert 'RTC_ANDROID_KEYSTORE_PASSWORD' in workflow
assert 'RTC_ANDROID_KEY_ALIAS' in workflow
assert 'RTC_ANDROID_KEY_PASSWORD' in workflow
assert 'https://rtc-ci.invalid' in workflow
assert 'exit 1' not in the debug-runtime missing-secret branch
assert 'SERVICE_ROLE' not in gradle
```

- [ ] **Step 2: Confirm the new contracts fail against the starting branch state**

Run:

```bash
python3 tools/tests/run_contract_tests.py
```

Expected: the newly added production-integration contracts fail because release runtime/signing hardening is not implemented yet.

- [ ] **Step 3: Commit the contract-only change**

```bash
git add tools/tests/test_production_integration_foundation.py
git commit -m "test(release): define production integration boundary"
```

---

### Task 2: Harden Gradle release runtime and signing configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`
- Test: `tools/tests/test_production_integration_foundation.py`

**Interfaces:**
- Consumes: `release.runtime.properties` and `signing.properties` when a release task is requested.
- Produces: release `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_PUBLISHABLE_KEY`; fail-closed release task configuration.

- [ ] **Step 1: Add the ignored release runtime file**

Append `release.runtime.properties` to the runtime/signing ignore section in `.gitignore`.

- [ ] **Step 2: Add release-task detection**

In `app/build.gradle.kts`, define:

```kotlin
val releaseRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}
```

- [ ] **Step 3: Load release runtime properties**

Add a `Properties` loader for root `release.runtime.properties` and a helper that returns an empty string during non-release configuration but throws during a requested release task when a required production property is absent.

Required keys:

```text
supabase.production.url
supabase.production.publishableKey
```

- [ ] **Step 4: Make release signing fail closed**

When `releaseRequested` is true, require all existing signing properties:

```text
storeFile
storePassword
keyAlias
keyPassword
```

with a clear error message containing `Release builds require signing.properties`.

Do not require signing during debug-only Gradle configuration.

- [ ] **Step 5: Inject release BuildConfig values**

Inside `buildTypes.release`, override the blank defaults with the validated production URL and publishable key using the existing quoting helper.

- [ ] **Step 6: Run source contracts**

```bash
python3 tools/tests/run_contract_tests.py
```

Expected: production-integration contracts pass and all pre-existing source contracts remain green.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts .gitignore
git commit -m "build(release): require explicit runtime and signing config"
```

---

### Task 3: Separate source verification from connected/release credentials in CI

**Files:**
- Modify: `.github/workflows/android-ci.yml`
- Test: `tools/tests/test_production_integration_foundation.py`

**Interfaces:**
- Consumes: optional non-production debug secrets and optional complete release secret bundle.
- Produces: always-runnable source verification plus conditional signed release-candidate assembly.

- [ ] **Step 1: Make debug verification secret-independent**

In `Restore isolated debug runtime for verification`, when either non-production secret is missing, always use:

```text
https://rtc-ci.invalid
sb_publishable_ci_placeholder
```

for compile/test verification and emit a warning. Do not exit before source tests on push events.

- [ ] **Step 2: Define the complete release secret bundle**

The release-candidate step must consume:

```text
RTC_PROD_SUPABASE_URL
RTC_PROD_SUPABASE_PUBLISHABLE_KEY
GOOGLE_SERVICES_JSON_BASE64
RTC_ANDROID_KEYSTORE_BASE64
RTC_ANDROID_KEYSTORE_PASSWORD
RTC_ANDROID_KEY_ALIAS
RTC_ANDROID_KEY_PASSWORD
```

- [ ] **Step 3: Skip release assembly when the bundle is incomplete**

Check every required value. If any is absent, log a clear message and exit that release step successfully without producing release artifacts. Source verification remains authoritative for the normal CI run.

- [ ] **Step 4: Materialize ignored release files when complete**

Create:

`release.runtime.properties`

```properties
supabase.production.url=<secret>
supabase.production.publishableKey=<secret>
```

Decode the keystore into an ignored file, then create `signing.properties` pointing to it with the four expected signing properties.

Firebase config continues to be decoded into ignored `app/google-services.json`.

Use `umask 077` before writing credentials.

- [ ] **Step 5: Build the release candidate**

Run:

```bash
gradle --no-daemon --stacktrace assembleRelease bundleRelease
```

Only after the complete secret bundle has been materialized.

- [ ] **Step 6: Clean reconstructed sensitive files**

Add an `if: always()` cleanup step that removes:

```text
runtime.local.properties
release.runtime.properties
signing.properties
CI keystore file
app/google-services.json
```

- [ ] **Step 7: Run source contracts**

```bash
python3 tools/tests/run_contract_tests.py
```

Expected: all source contracts pass.

- [ ] **Step 8: Commit**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: decouple source verification from release credentials"
```

---

### Task 4: Full verification and integration handoff

**Files:**
- Create: `PRODUCTION_INTEGRATION_FOUNDATION_REPORT.md`

**Interfaces:**
- Consumes: final branch source and CI run evidence.
- Produces: integration-manager handoff with exact remaining repository settings work.

- [ ] **Step 1: Run the full source/Android gate**

```bash
python3 tools/tests/run_contract_tests.py
gradle --no-daemon testDebugUnitTest
gradle --no-daemon lintDebug
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

- [ ] **Step 2: Push branch and inspect GitHub Actions**

Verify the branch CI reaches the source contracts, JVM tests, lint, debug assembly and AndroidTest assembly even when non-production runtime secrets are unavailable.

- [ ] **Step 3: Record evidence**

`PRODUCTION_INTEGRATION_FOUNDATION_REPORT.md` must include:

- starting `main` SHA;
- final branch SHA;
- source-contract count/result;
- JVM/lint/debug/AndroidTest build result;
- CI run ID and conclusion;
- release candidate status;
- exact required GitHub secrets for a signed release;
- explicit note that production deployment was not performed;
- explicit note that branch protection/ruleset still requires repository-settings action if it remains disabled;
- compatibility notes for the four parallel AI branches.

- [ ] **Step 4: Commit the report**

```bash
git add PRODUCTION_INTEGRATION_FOUNDATION_REPORT.md
git commit -m "docs: record production integration foundation verification"
```
