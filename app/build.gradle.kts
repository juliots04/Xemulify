plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.xemulify"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.xemulify"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            // ABIs típicas de TV boxes (arm) + x86_64 para el emulador de Android Studio.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            // Extrae el core .so a nativeLibraryDir para poder cargarlo por ruta absoluta.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.glide)
    // Motor de emulación (embebe el core libretro de PS1).
    implementation(libs.libretrodroid)
    // Códigos QR para vinculación rápida del mando
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
}