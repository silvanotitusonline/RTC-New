from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)

workspace_path = Path("app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspace.kt")
workspace = workspace_path.read_text()
workspace = replace_once(
    workspace,
    "import androidx.compose.foundation.layout.fillMaxWidth\n",
    "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.lazy.items\n",
    "LazyListScope items import",
)
workspace_path.write_text(workspace)

components_path = Path("app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt")
components = components_path.read_text()
components = replace_once(
    components,
    "import androidx.compose.material3.ExposedDropdownMenu\n",
    "",
    "remove invalid ExposedDropdownMenu import",
)
components_path.write_text(components)

print("Admin compile follow-up applied.")
