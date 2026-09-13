from pathlib import Path

OLD = ".github/workflows/android-ci.yml"

required = (
    ":app:testDebugUnitTest",
    ":app:assembleDebug",
    "upload-artifact",
)

# Old workflow filename must never return to build tooling.
for p in Path("tools").rglob("*"):

    if not p.is_file():
        continue

    try:
        text = p.read_text()
    except (UnicodeDecodeError, OSError):
        continue

    if p.name == "workflow-audit-smoke.py":
        continue

    if OLD in text:
        raise SystemExit(
            f"stale workflow filename reference: {p}"
        )

workflows = sorted(
    list(Path(".github/workflows").glob("*.yml"))
    + list(Path(".github/workflows").glob("*.yaml"))
)

if not workflows:
    raise SystemExit("no GitHub Actions workflows exist")

valid = []

for p in workflows:

    text = p.read_text()

    if all(token in text for token in required):
        valid.append(p)

if not valid:
    raise SystemExit(
        "no Android workflow contains all required build capabilities"
    )

print(
    "WorkflowAuditSmoke: PASS -> "
    + ", ".join(str(p) for p in valid)
)
