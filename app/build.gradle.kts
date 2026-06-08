// FieldNotes — app/build.gradle.kts
// Authored by: lead architect | Implements: 03_SETUP_ENVIRONMENT.md (with corrections)
//
// Corrections vs spec:
//  - Compose is configured via the `kotlin.compose` plugin (Kotlin 2.0), not composeOptions.
//  - DRIVE_CLIENT_ID is read from local.properties with a plain Properties loader rather than the
//    internal `gradleLocalProperties(...)` API, which is unstable across AGP versions.
//  - externalNativeBuild (whisper.cpp) is added in the whisper integration phase; see app/src/main/cpp.

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Read local.properties (not committed) for the OAuth client id.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val driveClientId: String = localProps.getProperty("drive.client.id", "UNCONFIGURED")

// AppAuth uses Google's reverse-client-ID custom scheme as the OAuth redirect, e.g. client id
// "1234-abc.apps.googleusercontent.com" -> scheme "com.googleusercontent.apps.1234-abc". The scheme
// is needed both at build time (AppAuth's RedirectUriReceiverActivity manifest placeholder) and at
// runtime (to construct the redirect Uri), so it's derived once here.
val driveRedirectScheme: String = driveClientId
    .removeSuffix(".apps.googleusercontent.com")
    .let { prefix -> if (prefix != driveClientId) "com.googleusercontent.apps.$prefix" else "com.fieldnotes.app" }

android {
    namespace = "com.fieldnotes.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fieldnotes.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.fieldnotes.app.HiltTestRunner"

        buildConfigField("String", "DRIVE_CLIENT_ID", "\"$driveClientId\"")
        buildConfigField("String", "DRIVE_REDIRECT_SCHEME", "\"$driveRedirectScheme\"")

        // AppAuth captures the OAuth redirect via this scheme (see RedirectUriReceiverActivity).
        manifestPlaceholders["appAuthRedirectScheme"] = driveRedirectScheme

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64") // Pixel 8 = arm64; x86_64 for emulator
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Modern whisper.cpp uses GGML_* flag names. Disable x86 SIMD so the
                // x86_64 emulator build links; harmless on arm64 (those flags are x86-only).
                arguments += listOf(
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_AVX=OFF",
                    "-DGGML_AVX2=OFF",
                    "-DGGML_FMA=OFF",
                    "-DGGML_F16C=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.navigation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.google.api.client.android)
    implementation(libs.google.drive.api)
    implementation(libs.google.auth.oauth2)
    implementation(libs.appauth)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.accompanist.permissions)

    // Unit tests (JUnit 5 + MockK + Robolectric + Truth)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit4)            // Robolectric tests use the JUnit4 runner...
    testRuntimeOnly(libs.junit.vintage.engine) // ...executed via the vintage engine on the JUnit Platform
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.ext.junit)

    // Instrumented UI tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
