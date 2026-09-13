# Upload and Install through GitHub

## 1. Upload source

Create a GitHub repository and upload the complete contents of this folder, including `.github`, `gradle`, `tools`, `app`, `gradlew`, and `gradlew.bat`.

## 2. Build automatically

Open **Actions → WA Link Bot - Verify and Build APK → Run workflow**.

The workflow will:

1. install Java 17 and Android SDK 35;
2. bootstrap and checksum-verify the Gradle 8.9 wrapper JAR if required;
3. run `tools/release-audit.sh`;
4. run Android unit tests;
5. run Android lint;
6. build an installable debug APK;
7. create SHA-256;
8. upload both as a GitHub Actions artifact.

## 3. Publish as a GitHub Release

After the main build is green:

```bash
git tag v7.2.0-rc1
git push origin v7.2.0-rc1
```

The release workflow checks that the tag exactly matches the Gradle `versionName`. If verification passes, GitHub Release receives:

- `WA-Al-Othmany-Link-Bot-v7.2.0-rc1.apk`
- `SHA256.txt`

## 4. Install

Download the APK from the Actions artifact or GitHub Release on the Android device, allow installation from that source when Android asks, then install it.

This RC artifact is debug-signed for direct testing. For a long-term production signing identity, configure a private release keystore outside the repository and add a dedicated signed-release workflow; never commit a production keystore/password to GitHub.
