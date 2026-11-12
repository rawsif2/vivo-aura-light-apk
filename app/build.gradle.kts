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
        versionCode = 5
        versionName = "3.0.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
