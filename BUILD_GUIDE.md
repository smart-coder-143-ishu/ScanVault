# ScanVault Android Application — Build & Deployment Guide

## 1. Platform & Environment Architecture

> **Notice Regarding AI Studio Cloud Environment:**  
> The Google AI Studio preview sandbox operates in a Linux container with Node.js and web runtime tools. It does not contain the Android SDK, OpenJDK 17, or Gradle CLI binaries natively installed inside this cloud sandbox.  
> Therefore, this project provides:
> 1. A **complete, production-ready Native Android project** (located in `/android/`) with full Kotlin source files, Jetpack Compose UI, CameraX, Google ML Kit Barcode Scanning, Room Database, ViewModels, and Gradle build files.
> 2. An **interactive Native Android Web Simulator & Project Explorer** in `/src/` running on the Vite preview server, enabling immediate testing of the user interface, camera scanner, gallery decoder, barcode generator, history database, and Open Food Facts lookups directly in your browser.

---

## 2. Requirements to Build on Local Machine

- **Operating System:** macOS, Windows 10/11, or Linux
- **IDE:** Android Studio Iguana (2023.2.1), Jellyfish (2023.3.1), Koala (2024.1.1) or later
- **JDK:** OpenJDK 17 (bundled automatically with modern Android Studio)
- **Android SDK:**
  - `compileSdk`: 34 (Android 14)
  - `minSdk`: 24 (Android 7.0 Nougat — covering 96%+ of active Android devices)
  - `targetSdk`: 34

---

## 3. Opening in Android Studio

1. Clone or download the repository to your computer.
2. Launch **Android Studio**.
3. Select **File > Open...** and navigate to the `android/` directory.
4. Android Studio will detect the Gradle files and initiate project sync automatically.
5. Connect an Android device with USB debugging enabled or launch an Android Virtual Device (AVD) running Android 11+ (API 30+ recommended for CameraX preview).
6. Click **Run > Run 'app'** (or press Shift+F10).

---

## 4. Command Line Build Commands

Open a terminal inside the `/android` directory:

```bash
# Run unit tests (verifies checksums, URL safety analyzer, Wi-Fi parser)
./gradlew test

# Build debug APK for testing
./gradlew assembleDebug
# Output APK: app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected device
./gradlew installDebug

# Build release Android App Bundle (AAB) for Google Play Store upload
./gradlew bundleRelease
# Output AAB: app/build/outputs/bundle/release/app-release.aab
```

---

## 5. Generating a Signed Release Bundle for Google Play

### Step 1: Generate Release Keystore
Run keytool from your terminal:
```bash
keytool -genkey -v -keystore scanvault-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias scanvault
```

### Step 2: Configure `app/build.gradle.kts`
Add your release signing configuration:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("scanvault-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "your_store_password"
            keyAlias = "scanvault"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "your_key_password"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### Step 3: Build AAB
```bash
./gradlew bundleRelease
```
Upload the resulting `app-release.aab` directly to Google Play Console under **Production > Releases**.

---

## 6. Configuring Google AdMob for Production

The codebase currently configures official Google AdMob test IDs:
- Sample App ID: `ca-app-pub-3940256099942544~3347511713` (in `AndroidManifest.xml`)
- Sample Banner Ad Unit: `ca-app-pub-3940256099942544/6300978111` (in `AdBannerView.kt`)

### Production Switch Checklist:
1. Log in to [Google AdMob Console](https://admob.google.com/).
2. Create an Android App and copy your **App ID**. Replace the `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" ... />` value in `AndroidManifest.xml`.
3. Create an **Adaptive Banner Ad Unit** and copy the **Ad Unit ID**.
4. In `com/scanvault/app/admob/AdBannerView.kt`, update `TEST_BANNER_AD_UNIT_ID` to your production unit ID and set `ENABLE_LIVE_ADS = true`.
5. Integrate Google UMP (User Messaging Platform) SDK if publishing to users in the European Economic Area (EEA) and the UK for GDPR consent compliance.
