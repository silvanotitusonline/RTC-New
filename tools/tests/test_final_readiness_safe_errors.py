import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/java/za/org/rtc/community/app"
SERVICE_VMS = ROOT / "app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/viewmodel"
MARKETPLACE_LOAD = ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceLoadState.kt"


def test_service_centre_and_marketplace_never_publish_raw_backend_error_messages():
    safe = (APP / "SafeUiError.kt").read_text(encoding="utf-8")
    assert "fun serviceCentre(" in safe
    assert "fun marketplace(" in safe

    for path in SERVICE_VMS.glob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for raw in ("failure.message", "error.message", "exceptionOrNull()?.message"):
            assert raw not in text, f"{path.name} exposes raw backend text via {raw}"
        assert "SafeUiError.serviceCentre" in text, f"{path.name} must use the shared Service Centre safe-error boundary"

    marketplace = MARKETPLACE_LOAD.read_text(encoding="utf-8")
    assert "message?.takeIf" not in marketplace
    assert "SafeUiError.marketplace(this" in marketplace


def test_root_live_content_boundary_does_not_expose_repository_error_detail():
    view_model = (APP / "RtcViewModel.kt").read_text(encoding="utf-8")

    assert not re.search(
        r"val liveContentMessage = repository\.liveContentMessage\s*\n\s*val accessManagedAccount",
        view_model,
    )
    assert ".map(::sanitizeLiveContentMessage)" in view_model
    assert "sanitizeLiveContentMessage" in view_model
    assert "Live community information could not be refreshed. Check your connection and try again." in view_model
    assert "Community conversation could not be loaded. Check your connection and try again." in view_model
