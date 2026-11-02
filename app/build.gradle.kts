import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.world303.timewallpaper"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.world303.timewallpaper"
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"
    }

    signingConfigs {
        // Local builds sign with the real keystore; CI (no keystore checked in) produces an
        // unsigned release APK that gets signed afterwards with apksigner.
        if (rootProject.file(localProps.getProperty("TW_STORE_FILE", "keystore/timewallpaper.jks")).exists()) {
            create("release") {
                storeFile = rootProject.file(localProps.getProperty("TW_STORE_FILE", "keystore/timewallpaper.jks"))
                storePassword = localProps.getProperty("TW_STORE_PASSWORD", "")
                keyAlias = localProps.getProperty("TW_KEY_ALIAS", "timewallpaper")
                keyPassword = localProps.getProperty("TW_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
