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

android {
    namespace = "com.saman.tunnel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saman.tunnel"
        minSdk = 24
        targetSdk = 35
        versionCode = 111
        versionName = "1.1.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    if (hasSamanReleaseSigning) {
        signingConfigs {
            create("samanRelease") {
                storeFile = file(samanKeystoreFile!!)
                storePassword = samanKeystorePassword
                keyAlias = samanKeyAlias
                keyPassword = samanKeyPassword
            }
        }
    }

    buildTypes {
        release {
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

    kotlinOptions {
        jvmTarget = "17"
    }
}
