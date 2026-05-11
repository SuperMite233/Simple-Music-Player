plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.supermite.smp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.supermite.smp"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "beta1.0.3"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
