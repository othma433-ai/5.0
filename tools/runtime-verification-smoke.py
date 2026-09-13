from pathlib import Path

verify = Path("tools/verify-core.sh").read_text()

forbidden = (
    "runtime/BotRuntime.kt",
    "RuntimeControlSmoke.kt",
)

for token in forbidden:
    if token in verify:
        raise SystemExit(
            f"non-hermetic runtime verification returned: {token}"
        )

required = (
    "runtime/ThroughputMeter.kt",
    "ThroughputMeterSmoke.kt",
)

for token in required:
    if token not in verify:
        raise SystemExit(
            f"pure runtime smoke test missing: {token}"
        )

test = Path(
    "app/src/test/java/com/waalothmany/linkbot/"
    "runtime/BotRuntimeTest.kt"
)

if not test.exists():
    raise SystemExit(
        "Gradle BotRuntimeTest.kt is missing"
    )

print("RuntimeVerificationSmoke: PASS")
