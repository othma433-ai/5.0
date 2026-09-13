# Gradle Wrapper Bootstrap

This source bundle intentionally bootstraps `gradle-wrapper.jar` from the official Gradle 8.9 distribution endpoint when the JAR is absent. The bootstrap script verifies SHA-256 before installing the JAR, and `gradle-wrapper.properties` also pins the Gradle 8.9 binary distribution SHA-256.

Run:

```bash
./tools/bootstrap-gradle-wrapper.sh
./gradlew --version
```

The bootstrap fails closed if the download or checksum verification fails.
