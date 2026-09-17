from release_contract_context import ROOT


def read(path: str) -> str:
    return (ROOT / path).read_text()


def test_android_ci_uses_node24_capable_current_action_majors_without_weakening_release_gates():
    workflow = read('.github/workflows/android-ci.yml')

    assert 'uses: actions/checkout@v6' in workflow
    assert 'uses: actions/setup-java@v5' in workflow
    assert 'uses: android-actions/setup-android@v4' in workflow
    assert 'uses: gradle/actions/setup-gradle@v6' in workflow
    assert workflow.count('uses: actions/upload-artifact@v6') == 2
    assert 'uses: denoland/setup-deno@v2' in workflow

    for obsolete in [
        'actions/checkout@v4',
        'actions/setup-java@v4',
        'android-actions/setup-android@v3',
        'gradle/actions/setup-gradle@v4',
        'actions/upload-artifact@v4',
    ]:
        assert obsolete not in workflow

    assert 'cache-read-only: ${{ github.event_name == \'pull_request\' }}' in workflow
    assert 'python3 tools/tests/run_contract_tests.py' in workflow
    assert 'deno test supabase/functions/_shared/auth_test.ts' in workflow
    assert './gradlew --no-daemon --stacktrace testDebugUnitTest' in workflow
    assert './gradlew --no-daemon --stacktrace lintDebug' in workflow
    assert './gradlew --no-daemon --stacktrace assembleDebugAndroidTest' in workflow
    assert 'Release-candidate build skipped because the complete release secret bundle is not configured' in workflow
    assert './gradlew --no-daemon --stacktrace assembleRelease bundleRelease' in workflow
    assert 'rm -f \\' in workflow
    assert 'rtc-ci-release.keystore' in workflow
    assert 'app/google-services.json' in workflow
