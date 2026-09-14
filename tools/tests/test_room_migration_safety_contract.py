from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP_MODULE = ROOT / "app/src/main/java/za/org/rtc/community/di/AppModule.kt"


def test_production_room_database_never_falls_back_to_destructive_migration():
    source = APP_MODULE.read_text(encoding="utf-8")
    assert ".fallbackToDestructiveMigration(" not in source, (
        "RtcDatabase stores resident drafts/session/cache state and must fail closed when a "
        "migration is missing rather than wiping local data. Add an explicit Room migration."
    )
