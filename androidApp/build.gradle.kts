import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.File as CredentialFile
import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.github.triplet.gradle.androidpublisher.ResolutionStrategy

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("com.github.triplet.play") version "4.1.1"
}

val publishSettingsFile = rootProject.file("android-publish.properties")
val publishSettings = Properties().apply {
    if (publishSettingsFile.isFile) publishSettingsFile.inputStream().use { load(it) }
}
val signingFields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = signingFields.all { !publishSettings.getProperty(it).isNullOrBlank() }

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
    implementation(libs.google.play.app.update)
    // Play's transitive Fragment 1.0.0 is incompatible with Activity Result APIs.
    implementation(libs.androidx.fragment)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    testImplementation("junit:junit:4.13.2")

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
        // Local/offline builds use this baseline. Publishing resolves the next
        // versionCode from Google Play before building the upload artifact.
        versionCode = 1190
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
    signingConfigs {
        if (hasReleaseSigning) {
            create("playUpload") {
                storeFile = rootProject.file(publishSettings.getProperty("storeFile"))
                storePassword = publishSettings.getProperty("storePassword")
                keyAlias = publishSettings.getProperty("keyAlias")
                keyPassword = publishSettings.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("playUpload")
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

play {
    serviceAccountCredentials.set(rootProject.file(
        publishSettings.getProperty("serviceAccountFile", "play-service-account.json")
    ))
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
    resolutionStrategy.set(ResolutionStrategy.AUTO)
}

abstract class ValidatePlayUpload : DefaultTask() {
    @get:Input
    abstract val missingFields: ListProperty<String>

    @get:Input
    abstract val credentialPaths: ListProperty<String>

    @TaskAction
    fun validate() {
        check(missingFields.get().isEmpty()) {
            "Uzupełnij android-publish.properties: ${missingFields.get().joinToString()}. Nie wpisuj haseł w czacie."
        }
        credentialPaths.get().forEach { path ->
            check(CredentialFile(path).isFile) {
                "Nie znaleziono pliku klucza lub konta Google wskazanego w android-publish.properties."
            }
        }
    }
}

val validatePlayUpload = tasks.register<ValidatePlayUpload>("validatePlayUpload") {
    group = "publishing"
    description = "Checks local signing and Google Play credentials without uploading anything."
    missingFields.set((signingFields + "serviceAccountFile").filter {
        publishSettings.getProperty(it).isNullOrBlank()
    })
    credentialPaths.set(listOf("storeFile", "serviceAccountFile").mapNotNull { field ->
        publishSettings.getProperty(field)?.takeIf { it.isNotBlank() }?.let { rootProject.file(it).absolutePath }
    })
}

tasks.matching { it.name == "publishReleaseBundle" || it.name == "publishReleaseApk" }
    .configureEach { dependsOn(validatePlayUpload) }
