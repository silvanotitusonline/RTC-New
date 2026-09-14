from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "android-release.yml"


def test_production_release_workflow_is_manual_fail_closed_and_complete():
    text = WORKFLOW.read_text(encoding="utf-8")

    assert "workflow_dispatch:" in text
    assert "pull_request:" not in text
    assert "\n  push:" not in text
    assert "refs/heads/main" in text
    assert "Production release is fail-closed" in text
    assert "exit 1" in text

    for secret in (
        "RTC_PROD_SUPABASE_URL",
        "RTC_PROD_SUPABASE_PUBLISHABLE_KEY",
        "GOOGLE_SERVICES_JSON_BASE64",
        "RTC_ANDROID_KEYSTORE_BASE64",
        "RTC_ANDROID_KEYSTORE_PASSWORD",
        "RTC_ANDROID_KEY_ALIAS",
        "RTC_ANDROID_KEY_PASSWORD",
    ):
        assert f"secrets.{secret}" in text
        assert secret in text

    for verification in (
        "python3 tools/tests/run_contract_tests.py",
        "deno test supabase/functions/_shared/auth_test.ts",
        "testDebugUnitTest",
        "lintDebug",
        "assembleDebugAndroidTest",
    ):
        assert verification in text

    for release_task in ("assemblePhoneBeta", "assembleRelease", "bundleRelease"):
        assert release_task in text

    assert "za.org.rtc.community" in text
    assert "app/build/outputs/apk/phoneBeta/*.apk" in text
    assert "app/build/outputs/apk/release/*.apk" in text
    assert "app/build/outputs/bundle/release/*.aab" in text
    assert "sha256sum -- * > SHA256SUMS.txt" in text
    assert "if-no-files-found: error" in text
    assert "retention-days: 30" in text
    assert "Cleanup reconstructed credentials" in text
    cleanup = text.split("- name: Cleanup reconstructed credentials", 1)[1]
    assert "if: always()" in cleanup
    for sensitive_file in (
        "runtime.local.properties",
        "release.runtime.properties",
        "signing.properties",
        "rtc-ci-release.keystore",
        "app/google-services.json",
    ):
        assert sensitive_file in cleanup
