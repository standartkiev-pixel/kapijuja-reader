plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kapijuja.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kapijuja.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.1.8"

        // Experimental Silero/PyTorch build: the current test device and modern
        // Android phones are arm64. Keeping one ABI avoids multiplying the
        // large native runtime inside this test APK. Revisit with ABI splits later.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}


dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.pytorch:pytorch_android:2.1.0")
}
