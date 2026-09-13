from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
CI = (ROOT / ".github" / "workflows" / "android-ci.yml").read_text(encoding="utf-8")


def test_phone_beta_build_uses_production_runtime_without_release_keystore():
    assert 'create("phoneBeta")' in BUILD
    assert 'taskName.contains("PhoneBeta", ignoreCase = true)' in BUILD
    assert 'signingConfig = signingConfigs.getByName("debug")' in BUILD
    assert 'releaseRuntimeValue("supabase.production.url", "RTC_PROD_SUPABASE_URL")' in BUILD
    assert '"supabase.production.publishableKey"' in BUILD
    assert '"RTC_PROD_SUPABASE_PUBLISHABLE_KEY"' in BUILD


def test_phone_beta_requires_firebase_client_configuration_but_not_admin_credentials():
    assert 'productionConnectedBuildRequested' in BUILD
    assert 'PhoneBeta builds require app/google-services.json' in BUILD
    assert 'firebase-adminsdk' not in BUILD.lower()
    assert 'SUPABASE_SERVICE_ROLE_KEY' not in BUILD


def test_ci_builds_and_uploads_phone_beta_only_with_complete_client_bundle():
    assert 'Assemble production-connected phone beta when complete client configuration exists' in CI
    assert 'RTC_PROD_SUPABASE_URL' in CI
    assert 'RTC_PROD_SUPABASE_PUBLISHABLE_KEY' in CI
    assert 'GOOGLE_SERVICES_JSON_BASE64' in CI
    assert 'gradle --no-daemon --stacktrace assemblePhoneBeta' in CI
    assert 'name: rtc-community-phone-beta' in CI
    assert 'app/build/outputs/apk/phoneBeta/*.apk' in CI


def test_release_signing_remains_a_separate_fail_closed_gate():
    assert 'RTC_ANDROID_KEYSTORE_BASE64' in CI
    assert 'RTC_ANDROID_KEYSTORE_PASSWORD' in CI
    assert 'RTC_ANDROID_KEY_ALIAS' in CI
    assert 'RTC_ANDROID_KEY_PASSWORD' in CI
    assert 'gradle --no-daemon --stacktrace assembleRelease bundleRelease' in CI
