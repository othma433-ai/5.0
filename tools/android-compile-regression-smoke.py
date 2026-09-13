from pathlib import Path

service = Path(
    "app/src/main/java/com/waalothmany/linkbot/"
    "automation/WaAccessibilityService.kt"
).read_text()

ui = Path(
    "app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt"
).read_text()

if "normalizePreview(row.preview)" in service:
    if "private fun normalizePreview" not in service:
        raise SystemExit(
            "normalizePreview is called but not defined"
        )

bad = "import androidx.compose.foundation.layout.weight"

if bad in ui:
    raise SystemExit(
        "invalid Compose weight import returned"
    )

if "Modifier.weight(" not in ui:
    raise SystemExit(
        "expected scoped Modifier.weight usage is missing"
    )

print("AndroidCompileRegressionSmoke: PASS")
