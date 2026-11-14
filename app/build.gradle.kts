import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

// Secrets live in the git-ignored local.properties (e.g. ORS_API_KEY=...)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// Free OpenRouteService key for road distance, drive time and route lines.
val orsApiKey: String = localProperties.getProperty("ORS_API_KEY")
    ?: System.getenv("ORS_API_KEY")
    ?: ""

// Optional dev-login account for debug builds (DEV_LOGIN_EMAIL / DEV_LOGIN_PASSWORD).
// Never compiled into release builds.
val devLoginEmail: String = localProperties.getProperty("DEV_LOGIN_EMAIL") ?: ""
val devLoginPassword: String = localProperties.getProperty("DEV_LOGIN_PASSWORD") ?: ""

android {
    namespace = "com.dhruvk.parkover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dhruvk.parkover"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ORS_API_KEY", "\"$orsApiKey\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_LOGIN_EMAIL", "\"$devLoginEmail\"")
            buildConfigField("String", "DEV_LOGIN_PASSWORD", "\"$devLoginPassword\"")
        }
        release {
            buildConfigField("String", "DEV_LOGIN_EMAIL", "\"\"")
            buildConfigField("String", "DEV_LOGIN_PASSWORD", "\"\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // android.util.Log etc. return defaults in JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.annotation)

    // Lifecycle
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Maps: MapLibre with OpenFreeMap tiles (no Google billing needed)
    implementation(libs.maplibre.android)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)

    // Location Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Image Loading
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Lottie
    implementation(libs.lottie.compose)

    // QR Code
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Testing: unit (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.org.json) // real org.json; the Android stub returns nulls on the JVM

    // Testing: on device (integration + E2E)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
