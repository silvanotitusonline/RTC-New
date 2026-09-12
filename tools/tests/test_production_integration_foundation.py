from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRADLE = (ROOT / "app/build.gradle.kts").read_text()
WORKFLOW = (ROOT / ".github/workflows/android-ci.yml").read_text()
GITIGNORE = (ROOT / ".gitignore").read_text()


def _workflow_step(start_name: str, end_name: str) -> str:
    start = WORKFLOW.index(f"- name: {start_name}")
    end = WORKFLOW.index(f"- name: {end_name}", start)
    return WORKFLOW[start:end]


def test_debug_source_verification_does_not_require_live_supabase_secrets():
    step = _workflow_step(
        "Restore isolated debug runtime for verification",
        "Set up Gradle",
    )
    assert "RTC_NONPROD_SUPABASE_URL" in step
    assert "RTC_NONPROD_SUPABASE_PUBLISHABLE_KEY" in step
    assert "https://rtc-ci.invalid" in step
    assert "sb_publishable_ci_placeholder" in step
    assert "exit 1" not in step


def test_release_runtime_is_explicit_ignored_and_fail_closed():
    assert "release.runtime.properties" in GRADLE
    assert "supabase.production.url" in GRADLE
    assert "supabase.production.publishableKey" in GRADLE
    assert "releaseRequested" in GRADLE
    assert "Release builds require signing.properties" in GRADLE
    assert "RTC_ANDROID_KEYSTORE_PATH" in GRADLE
    assert "release.runtime.properties" in GITIGNORE
    assert "SERVICE_ROLE" not in GRADLE


def test_ci_release_candidate_requires_complete_secret_bundle():
    for secret_name in (
        "RTC_PROD_SUPABASE_URL",
        "RTC_PROD_SUPABASE_PUBLISHABLE_KEY",
        "GOOGLE_SERVICES_JSON_BASE64",
        "RTC_ANDROID_KEYSTORE_BASE64",
        "RTC_ANDROID_KEYSTORE_PASSWORD",
        "RTC_ANDROID_KEY_ALIAS",
        "RTC_ANDROID_KEY_PASSWORD",
    ):
        assert secret_name in WORKFLOW
    assert "release.runtime.properties" in WORKFLOW
    assert "rtc-ci-release.keystore" in WORKFLOW
    assert 'export RTC_ANDROID_KEYSTORE_PATH="$GITHUB_WORKSPACE/rtc-ci-release.keystore"' in WORKFLOW
    assert "assembleRelease bundleRelease" in WORKFLOW


def test_ci_cleans_reconstructed_runtime_and_signing_material():
    assert "Cleanup reconstructed CI credentials" in WORKFLOW
    cleanup = WORKFLOW[WORKFLOW.index("- name: Cleanup reconstructed CI credentials") :]
    for sensitive_path in (
        "runtime.local.properties",
        "release.runtime.properties",
        "signing.properties",
        "rtc-ci-release.keystore",
        "app/google-services.json",
    ):
        assert sensitive_path in cleanup
