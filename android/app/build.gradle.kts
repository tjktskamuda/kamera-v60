plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "id.tjktskamuda.kamerav60"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.tjktskamuda.kamerav60"
        minSdk = 29
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Kunci penanda tangan TIDAK disimpan di repositori. GitHub Actions menuliskannya dari
    // secret KAMERA_V60_KEYSTORE ke file sementara saat build (lihat .github/workflows/apk.yml).
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KAMERA_KEYSTORE_FILE") ?: "release.p12")
            storeType = "PKCS12"
            storePassword = System.getenv("KAMERA_KEYSTORE_PASSWORD") ?: "kamerav60"
            keyAlias = "kamerav60"
            keyPassword = System.getenv("KAMERA_KEYSTORE_PASSWORD") ?: "kamerav60"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}
