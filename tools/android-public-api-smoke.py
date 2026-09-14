from pathlib import Path

targets = [
    Path("app/src/main/java/com/waalothmany/linkbot/data/AppDatabase.kt"),
    Path("app/src/main/java/com/waalothmany/linkbot/runtime/engine/standard/StandardAndroidEngine.kt"),
    Path("app/src/main/java/com/waalothmany/linkbot/whatsapp/WhatsAppInstanceDetector.kt"),
]
for p in targets:
    assert "UserHandle.myUserId()" not in p.read_text(), f"hidden/non-public API remains: {p}"

root = Path("app/src/main/java/com/waalothmany/linkbot/runtime/engine/root/RootEngine.kt").read_text()
assert "): EngineExecutionResult = try {" not in root, "RootEngine execute still uses invalid expression body"

identity = Path("app/src/main/java/com/waalothmany/linkbot/runtime/AndroidUserIdentity.kt").read_text()
assert "Process.myUid() / PER_USER_RANGE" in identity
print("AndroidPublicApiSmoke: PASS")
