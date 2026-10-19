# ScanVault ProGuard & R8 Optimization Rules

# Keep ML Kit Barcode Scanning classes
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep interface com.google.mlkit.vision.barcode.** { *; }

# Keep Room Database models and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Gson models for Product OpenFoodFacts API
-keep class com.scanvault.app.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Google Play Services Ads
-keep class com.google.android.gms.ads.** { *; }
