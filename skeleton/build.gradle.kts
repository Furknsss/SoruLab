plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.denemelab.preview"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    defaultConfig {
        applicationId = "com.denemelab.preview"
        minSdk = 24
        targetSdk = 32
        versionCode = 1
        versionName = "0.1.0-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
