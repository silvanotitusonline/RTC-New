from collections import defaultdict
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "supabase" / "migrations"
CANONICAL_VERSION = re.compile(r"^(\d{14})_")


def test_canonical_supabase_migration_versions_are_unique():
    by_version: dict[str, list[str]] = defaultdict(list)
    for path in sorted(MIGRATIONS.glob("*.sql")):
        match = CANONICAL_VERSION.match(path.name)
        if match:
            by_version[match.group(1)].append(path.name)

    duplicates = {
        version: names
        for version, names in by_version.items()
        if len(names) > 1
    }

    assert not duplicates, (
        "Canonical Supabase migration versions must be unique; duplicate versions break "
        f"clean migration replay/history reconciliation: {duplicates}"
    )
