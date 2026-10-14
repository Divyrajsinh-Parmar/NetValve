// PayProbe: the measuring instrument. A tiny separate app (separate UID, so the scheduler can
// give it its own tier) that sends one fresh HTTPS request per second and records how long
// each one took. No Compose, no Hilt, no third-party libraries on purpose: it must stay small,
// boring and quick to build. Both debug APKs are signed with the same default debug keystore.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.netvalve.payprobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.netvalve.payprobe"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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

dependencies {
    testImplementation(libs.junit4)
}
