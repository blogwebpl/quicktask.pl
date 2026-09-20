import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser { testTask { useKarma { useChromeHeadless() } } }
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { useKarma { useChromeHeadless() } } }
    }
    
    android {
       namespace = "pl.quicktask.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain { kotlin.srcDir("src/androidJvmMain/kotlin") }
        jvmMain { kotlin.srcDir("src/androidJvmMain/kotlin") }
        named("androidHostTest") { kotlin.srcDir("src/androidJvmTest/kotlin") }
        jvmTest { kotlin.srcDir("src/androidJvmTest/kotlin") }
        jsTest { kotlin.srcDir("src/browserTest/kotlin") }
        wasmJsTest { kotlin.srcDir("src/browserTest/kotlin") }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.androidx.security.crypto)
            implementation(libs.multiplatformSettings)
            implementation(libs.ktor.client.okhttp)
            implementation(files("libs/opaque-kmp.aar"))
            implementation("net.java.dev.jna:jna:5.17.0@aar")
        }
        named("androidHostTest") {
            // Reuse the desktop OPAQUE binaries; the Android AAR only contains Android .so files.
            resources.srcDir("src/jvmMain/resources")
            resources.srcDir("src/jvmTest/resources")
            dependencies {
                // Host tests run on the desktop JVM and need JNA's host native libraries.
                runtimeOnly("net.java.dev.jna:jna:5.17.0@jar")
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatformSettingsNoArg)
            implementation(libs.cryptography.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        jsMain.dependencies {
            implementation(npm("@quicktask/browser-security", project.file("browser-security")))
            implementation(libs.wrappers.browser)
            implementation(libs.cryptography.provider.webcrypto)
            implementation(npm("@serenity-kit/opaque", "1.1.0"))
        }
        wasmJsMain.dependencies {
            implementation(npm("@quicktask/browser-security", project.file("browser-security")))
            implementation(npm("@serenity-kit/opaque", "1.1.0"))
            implementation(libs.cryptography.provider.webcrypto)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.ktor.client.okhttp)
            implementation(files("libs/opaque-kmp-classes.jar"))
            implementation("net.java.dev.jna:jna:5.17.0")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
