plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tawsif.aura"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tawsif.aura"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"
    }

    // Fixed keystore so every CI build has the same signature (updates install over old ones)
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { aidl = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
