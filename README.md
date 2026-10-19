# ScanVault — Smart QR & Barcode Scanner for Android

ScanVault is a native Android application built with modern Android development standards:
- **Language:** Kotlin 1.9
- **UI Framework:** Jetpack Compose & Material 3
- **Camera Pipeline:** Android CameraX (1.3.3)
- **Scanning Engine:** Google ML Kit Barcode Scanning
- **Local Persistence:** Room Database with Kotlin Coroutines & Flow
- **Architecture:** MVVM (Model-View-ViewModel) + Clean Repository pattern
- **Network & Lookups:** OkHttp + Open Food Facts community product database
- **Code Generator:** Offline ZXing engine generating Bitmaps for QR, Code 128, EAN-13, and UPC
- **Monetization:** Google Mobile Ads (AdMob) with adaptive non-intrusive banner placement

## Project Structure
```
android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── BUILD_GUIDE.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/ (strings.xml, colors.xml, themes.xml)
        │   │   └── xml/ (data_extraction_rules.xml, backup_rules.xml)
        │   └── java/com/scanvault/app/
        │       ├── ScanVaultApplication.kt
        │       ├── MainActivity.kt
        │       ├── admob/
        │       │   └── AdBannerView.kt
        │       ├── data/
        │       │   ├── local/ (ScanRecordEntity.kt, ScanDao.kt, ScanDatabase.kt)
        │       │   ├── model/ (BarcodeFormatType.kt, ScanContent.kt)
        │       │   ├── parser/ (BarcodePayloadParser.kt)
        │       │   └── repository/ (ScanRepository.kt, ProductLookupRepository.kt)
        │       ├── generator/
        │       │   └── BarcodeGenerator.kt
        │       ├── scanner/
        │       │   ├── BarcodeAnalyzer.kt
        │       │   └── CameraPreview.kt
        │       └── ui/
        │           ├── navigation/ (Screen.kt, NavGraph.kt)
        │           ├── screens/
        │           │   ├── home/HomeScreen.kt
        │           │   ├── scanner/ScannerScreen.kt
        │           │   ├── result/ScanResultScreen.kt
        │           │   ├── history/HistoryScreen.kt
        │           │   ├── bulk/BulkScanScreen.kt
        │           │   ├── tools/GeneratorScreen.kt
        │           │   ├── product/ProductDetailsScreen.kt
        │           │   └── settings/SettingsScreen.kt
        │           └── theme/ (Color.kt, Type.kt, Theme.kt)
        └── test/java/com/scanvault/app/
            └── BarcodePayloadParserTest.kt
```
