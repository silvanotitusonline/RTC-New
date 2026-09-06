from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/java/za/org/rtc/community/app"


def test_root_rtc_viewmodel_is_a_bounded_compatibility_facade():
    """Root state may remain source-compatible, but cross-cutting implementation belongs in cohesive coordinators."""
    view_model = (APP / "RtcViewModel.kt").read_text(encoding="utf-8")

    assert len(view_model.splitlines()) <= 520, "RtcViewModel must be a bounded application facade, not an 800+ line coordinator"
    assert (APP / "RtcAuthenticationCoordinator.kt").exists()
    assert (APP / "RtcAdministrationCoordinator.kt").exists()
    assert (APP / "RtcResidentCoordinator.kt").exists()
    assert (APP / "SafeUiError.kt").exists()

    assert "FirebaseApp" not in view_model
    assert "FirebaseMessaging" not in view_model
    assert "communitySafeErrorMessage" not in view_model
    assert "profilePhotoSafeErrorMessage" not in view_model
