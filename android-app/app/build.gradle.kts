import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val samanKeystoreFile = providers.gradleProperty("SAMAN_KEYSTORE_FILE").orNull
val samanKeystorePassword = providers.gradleProperty("SAMAN_KEYSTORE_PASSWORD").orNull
val samanKeyAlias = providers.gradleProperty("SAMAN_KEY_ALIAS").orNull
val samanKeyPassword = providers.gradleProperty("SAMAN_KEY_PASSWORD").orNull

val hasSamanReleaseSigning =
    !samanKeystoreFile.isNullOrBlank() &&
    !samanKeystorePassword.isNullOrBlank() &&
    !samanKeyAlias.isNullOrBlank() &&
    !samanKeyPassword.isNullOrBlank()

val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

if (releaseRequested && !hasSamanReleaseSigning) {
    throw GradleException("Release signing properties are required for release tasks")
}

// Public development identity, deliberately unrelated to production signing.
val publicDebugKeystore = layout.buildDirectory.file("debug-signing/public-debug.p12").get().asFile
publicDebugKeystore.parentFile.mkdirs()
publicDebugKeystore.writeBytes(java.util.Base64.getMimeDecoder().decode(
    rootProject.file("debug-signing/PUBLIC-DEBUG-KEYSTORE.base64").readText()
))

android {
    namespace = "com.saman.tunnel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saman.tunnel"
        minSdk = 24
        targetSdk = 35
        versionCode = 175
        versionName = "1.7.5"
        manifestPlaceholders["appLabel"] = "Saman Tunnel"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    if (hasSamanReleaseSigning) {
        signingConfigs {
            create("samanRelease") {
                storeFile = file(samanKeystoreFile!!)
                storePassword = samanKeystorePassword
                keyAlias = samanKeyAlias
                keyPassword = samanKeyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = publicDebugKeystore
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Saman Tunnel Debug"
            isDebuggable = true
        }

        release {
            manifestPlaceholders["appLabel"] = "Saman Tunnel"
            isMinifyEnabled = false

            if (hasSamanReleaseSigning) {
                signingConfig = signingConfigs.getByName("samanRelease")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "26.3.11579264"

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
