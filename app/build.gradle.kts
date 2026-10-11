plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.scarface.reverse"
    compileSdk = 35
    defaultConfig { applicationId = "com.scarface.reverse"; minSdk = 26; targetSdk = 34; versionCode = 7; versionName = "0.5.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
