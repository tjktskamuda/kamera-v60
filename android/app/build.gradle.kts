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

    // Kunci penanda tangan tetap agar versi baru bisa dipasang menimpa versi lama.
    signingConfigs {
        create("release") {
            storeFile = file("kamera-v60.jks")
            storePassword = "o3P4GU1XTNTm3i6y04o1Pyg5"
            keyAlias = "kamerav60"
            keyPassword = "o3P4GU1XTNTm3i6y04o1Pyg5"
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
