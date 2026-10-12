# Raksha360 — Android Release & Distribution Guide

This document describes the reproducible Android release workflow, keystore stability, signing verification, and distribution options for Raksha360.

---

## 1. Application Metadata & Identifiers

| Parameter | Value |
|---|---|
| **Application ID** | `com.aistudio.naarikavach.safe7x` |
| **Version Name** | `1.0` |
| **Version Code** | `1` |
| **Minimum SDK** | `24` (Android 7.0 Nougat or newer) |
| **Target SDK** | `36` (Android 16 preview / latest platform) |
| **Namespace** | `com.example` |
| **Output Artifact** | `distribution/Raksha360-v1.0-release.apk` |

---

## 2. Release Signing & Key Stability

### Keystore Policy
To ensure that users can install future updates without uninstalling the app (which would erase local databases, voice profiles, and encryption keys), **one stable release signing key** is used.

- **Keystore Path**: `keystores/raksha360-release.jks`
- **Key Alias**: `upload`
- **Algorithm**: RSA 2048-bit, self-signed for 25+ years (valid through 2051+)
- **Security Safeguard**: Keystore files (`*.jks`, `*.keystore`), `.env.release`, and passwords are listed in `.gitignore` and must **never** be committed or pushed to public version control.

### Credentials Configuration
Signing credentials can be supplied through `.env.release` (recommended for local builds) or via process environment variables:

```ini
# .env.release (git-ignored)
KEYSTORE_PATH=keystores/raksha360-release.jks
STORE_PASSWORD=<your-secure-store-password>
KEY_ALIAS=upload
KEY_PASSWORD=<your-secure-key-password>
```

---

## 3. Automated Release Build Script

A release script is provided at `scripts/build_android_release.ps1`:

```powershell
# Run from repository root
powershell -ExecutionPolicy Bypass -File .\scripts\build_android_release.ps1
```

### What the script performs:
1. Validates that Java Development Kit (JDK 17+) and Android SDK are located.
2. Reads `.env.release` or environment variables for keystore credentials.
3. Validates or initializes the release keystore.
4. Executes `./gradlew assembleRelease` using the Gradle daemon.
5. Verifies the generated APK at `app/build/outputs/apk/release/app-release.apk`.
6. Checks the signature using `apksigner.bat` (verifies v1 JAR, v2 APK signature scheme, and v3 scheme).
7. Calculates the SHA-256 cryptographic checksum.
8. Copies the release artifact to `distribution/Raksha360-v1.0-release.apk`.
9. Injects the computed SHA-256 hash into `distribution/index.html`.

---

## 4. HTTPS Distribution Options

### Option A: Static HTTPS Hosting (Direct APK Sideloading)
You can deploy the contents of the `distribution/` folder (`index.html` + `Raksha360-v1.0-release.apk`) to any static HTTPS host:
- **Cloudflare Pages / Workers**: Free HTTPS, global CDN caching.
- **GitHub Releases / GitHub Pages**: Attach the APK to a GitHub Release and link from `distribution/index.html`.
- **AWS S3 + CloudFront / Firebase Hosting**: Low-latency HTTPS object storage.

#### What Android users experience during APK sideloading:
1. User taps download in Chrome or mobile browser.
2. Android prompts: *"File might be harmful. Do you want to download Raksha360-v1.0-release.apk anyway?"* &rarr; Tap **Download anyway**.
3. User opens the downloaded file.
4. If this is the first sideload from that browser, Android displays: *"For your security, your phone is not allowed to install unknown apps from this source."* &rarr; User taps **Settings** and enables **Allow from this source**.
5. Tap **Install**.
6. Subsequent updates signed with the same `raksha360-release.jks` key can be installed as seamless updates without uninstalling.

### Option B: Google Play Console (Internal / Closed Testing)
If you have access to a Google Play Developer account:
1. Generate an Android App Bundle (AAB):
   ```powershell
   .\gradlew.bat bundleRelease
   ```
2. The output bundle will be created at `app/build/outputs/bundle/release/app-release.aab`.
3. In Google Play Console, create an application under `com.aistudio.naarikavach.safe7x`.
4. Upload `app-release.aab` to the **Internal Testing** track.
5. Add internal testers' email addresses. Testers receive a direct Play Store opt-in link and get automatic background updates.

### Option C: Firebase App Distribution
If Firebase is configured in your project (`google-services.json` present):
1. Distribute directly via Gradle:
   ```powershell
   .\gradlew.bat appDistributionUploadRelease
   ```
2. Testers receive an invite email and use the Firebase App Tester app to install builds.

---

## 5. Security & Privacy Guarantees

- **No Credential Leakage**: No signing passwords, keystore binaries, or backend `.env` tokens are embedded in client binaries or HTML.
- **Offline Safety Guarantee**: Voice keyword detection operates completely on-device through the embedded Vosk acoustic model.
- **Last-Known Location Safeguard**: When a device loses connection or powers off, control room records are explicitly stamped with `LAST KNOWN LOCATION · NOT LIVE`.
- **Public Directory Independence**: The 37 Nagpur police stations are directory references only; internal dispatches remain internal control-room tasks.
