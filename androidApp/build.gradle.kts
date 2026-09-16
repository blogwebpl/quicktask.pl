import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val versionCodeEpochSeconds = 1_767_225_600L // 2026-01-01 00:00:00 UTC
val versionCodeBase = 1190L
val generatedVersionCode = providers.gradleProperty("androidVersionCode")
    .orNull
    ?.toIntOrNull()
    ?: (versionCodeBase + System.currentTimeMillis() / 1000L - versionCodeEpochSeconds).toInt()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.splashscreen)

    debugImplementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "pl.quicktask.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.quicktask.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // A new, increasing code is generated for every build invocation.
        // It can be fixed for reproducible builds with -PandroidVersionCode=1234.
        versionCode = generatedVersionCode
        versionName = "1.0"
    }
    splits {
        abi {
            // App Bundles require a single output; Google Play handles ABI delivery.
            isEnable = false
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
    bundle {
        abi {
            enableSplit = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            ndk {
                debugSymbolLevel = "FULL"
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
