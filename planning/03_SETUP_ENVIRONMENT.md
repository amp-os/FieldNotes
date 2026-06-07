# FieldNotes — Environment Setup

## Android project initialisation

Create a new Android project with these parameters:
- **Template:** Empty Activity (Compose)
- **Name:** FieldNotes
- **Package:** `com.fieldnotes.app`
- **Save location:** `./FieldNotes`
- **Language:** Kotlin
- **Min SDK:** API 26 (Android 8.0)

Use Android Studio's project wizard or create via the command line using the Android Gradle plugin directly. If using CLI:

```bash
# Install Android Studio command-line tools if not present
# Then scaffold:
mkdir FieldNotes && cd FieldNotes
# Use gradle init or clone a minimal template — see below for full build.gradle
```

## Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

## `gradle/libs.versions.toml` (version catalog)

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
ksp = "2.0.0-1.0.21"
compose-bom = "2024.06.00"
hilt = "2.51.1"
room = "2.6.1"
work = "2.9.0"
glance = "1.0.0"
coroutines = "1.8.1"
google-api-client = "2.5.0"
google-drive = "v3-rev20240521-2.0.0"
google-auth = "1.23.0"

[libraries]
# Kotlin
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { module = "androidx.navigation:navigation-compose", version = "2.7.7" }

# Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

# WorkManager
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
hilt-work = { module = "androidx.hilt:hilt-work", version = "1.2.0" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version = "1.2.0" }

# Glance (widgets)
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }

# Google Drive
google-api-client-android = { module = "com.google.api-client:google-api-client-android", version.ref = "google-api-client" }
google-drive-api = { module = "com.google.apis:google-api-services-drive", version.ref = "google-drive" }
google-auth-oauth2 = { module = "com.google.auth:google-auth-library-oauth2-http", version.ref = "google-auth" }

# JSON
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.7.0" }

# Misc
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version = "1.1.1" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version = "2.8.2" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

## `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fieldnotes.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fieldnotes.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Inject OAuth client ID from local.properties (set by user — see 11_GOOGLE_CLOUD_SETUP.md)
        val localProps = com.android.build.gradle.internal.cxx.configure.gradleLocalProperties(rootDir, providers)
        buildConfigField("String", "DRIVE_CLIENT_ID", 
            "\"${localProps.getProperty("drive.client.id", "UNCONFIGURED")}\"")
        
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")  // Pixel 8 = arm64; x86_64 for emulator
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DWHISPER_NO_AVX=ON",
                    "-DWHISPER_NO_AVX2=ON",
                    "-DWHISPER_NO_FMA=ON"
                )
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Required for Google API client
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.google.api.client.android)
    implementation(libs.google.drive.api)
    implementation(libs.google.auth.oauth2)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
}
```

## `app/src/main/cpp/CMakeLists.txt`

whisper.cpp is integrated as a Git submodule:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("fieldnotes")

# whisper.cpp is cloned to app/src/main/cpp/whisper.cpp/
add_subdirectory(whisper.cpp)

# JNI bridge
add_library(
    fieldnotes-jni
    SHARED
    whisper_jni.cpp
)

target_link_libraries(
    fieldnotes-jni
    whisper
    android
    log
)
```

## Submodule setup (run once)

```bash
cd app/src/main/cpp
git submodule add https://github.com/ggerganov/whisper.cpp
git submodule update --init --recursive
```

## AndroidManifest.xml permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<!-- Storage — not needed on API 29+ with scoped storage -->
```

## Emulator setup for CI testing

Create an AVD that mirrors the Pixel 8 as closely as possible:
```bash
# Using avdmanager
avdmanager create avd \
  --name "Pixel8_API35" \
  --package "system-images;android-35;google_apis_playstore;x86_64" \
  --device "pixel_8"

# Start emulator headless
emulator -avd Pixel8_API35 -no-window -no-audio &

# Wait for boot
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
```

> Note: whisper.cpp JNI will not work on x86_64 emulator without the `WHISPER_NO_AVX` flags above — ensure these are set.

## `local.properties` entries required from human setup

```properties
sdk.dir=/path/to/Android/Sdk
drive.client.id=YOUR_OAUTH_CLIENT_ID_HERE.apps.googleusercontent.com
```

See `11_GOOGLE_CLOUD_SETUP.md` for how to obtain `drive.client.id`.

## Verify build

```bash
./gradlew assembleDebug
# Expected: BUILD SUCCESSFUL
# APK at: app/build/outputs/apk/debug/app-debug.apk
```
