// Apache-2.0 vendored official PaddleOCR Android SDK, pinned revision dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf
plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.paddle.ocr"
    compileSdk = 36
    defaultConfig {
        // Preserve existing SoruLab minSdk=24, but invoke Paddle only on API>=26.
        // Upstream Android demo supports API>=26.
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")
    implementation("com.quickbirdstudios:opencv:4.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.18.0")
}
val fetchPaddleModels by tasks.registering(Exec::class) {
    description = "Download public official PP-OCRv6_small ONNX assets before Android packaging"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/prepare_ppocrv6_models.py")
}
tasks.named("preBuild") { dependsOn(fetchPaddleModels) }
