from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SAFE_UI_ERROR = ROOT / "app/src/main/java/za/org/rtc/community/app/SafeUiError.kt"
AUTH_ERROR_DIALOG = ROOT / "app/src/main/java/za/org/rtc/community/ui/auth/AuthenticationErrorDialog.kt"
GOOGLE_SIGN_IN = ROOT / "app/src/main/java/za/org/rtc/community/ui/auth/RtcGoogleSignInButton.kt"


def test_generic_safe_ui_error_never_appends_raw_exception_details():
    source = SAFE_UI_ERROR.read_text()

    assert 'else -> fallback' in source
    assert 'error.javaClass.simpleName' not in source
    assert '"$fallback (' not in source


def test_authentication_error_dialog_does_not_render_technical_logs():
    source = AUTH_ERROR_DIALOG.read_text()

    assert "rawExceptionMessage" not in source
    assert "Technical Logs:" not in source


def test_google_sign_in_logs_diagnostics_without_rendering_raw_exception_copy():
    source = GOOGLE_SIGN_IN.read_text()

    assert "AuthDiagnosticLogger.logError(errorType, rawExceptionMessage)" in source
    assert "errorMessageText = e.localizedMessage" not in source
    assert "rawExceptionMessage = rawExceptionMessage" not in source
