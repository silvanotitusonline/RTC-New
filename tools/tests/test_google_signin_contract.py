from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/za/org/rtc/community'


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def test_google_signin_is_wired_through_existing_auth_layers():
    google_ui = SRC / 'ui/auth/RtcGoogleSignInButton.kt'
    assert google_ui.exists(), 'Google sign-in UI adapter is missing'

    ui = google_ui.read_text(encoding='utf-8')
    assert 'CredentialManager.create' in ui
    assert 'GetGoogleIdOption.Builder' in ui
    assert '.setNonce(hashedNonce)' in ui
    assert 'Continue with Google' in ui
    assert '281489677261-j6isgtjd4mqv4os6fakt2qeloogpav8p.apps.googleusercontent.com' in ui

    app = text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt')
    assert 'PublicWelcomeScreen' in app
    assert 'onGoogleCredential = viewModel::signInWithGoogleIdToken' in app

    view_model = text('app/src/main/java/za/org/rtc/community/app/RtcViewModel.kt')
    coordinator = text('app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt')
    assert 'fun signInWithGoogleIdToken(idToken: String, nonce: String)' in view_model
    assert 'authenticationCoordinator.signInWithGoogleIdToken(idToken, nonce)' in view_model
    assert 'repository.signInWithGoogleIdToken(idToken, nonce)' in coordinator

    repository = text('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    assert 'suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): Result<Unit>' in repository
    assert 'supabase.auth.signInWith(IDToken)' in repository
    assert 'provider = Google' in repository
    assert 'this.nonce = nonce' in repository


def test_google_signin_does_not_embed_confidential_oauth_material():
    app_source = '\n'.join(
        path.read_text(encoding='utf-8', errors='ignore')
        for path in (ROOT / 'app/src').rglob('*')
        if path.is_file()
    )
    assert 'GOCSPX-' not in app_source
    assert 'BEGIN PRIVATE KEY' not in app_source


def test_credential_manager_release_rules_are_present():
    rules = text('app/proguard-rules.pro')
    assert '-if class androidx.credentials.CredentialManager' in rules
    assert '-keep class androidx.credentials.playservices.**' in rules
