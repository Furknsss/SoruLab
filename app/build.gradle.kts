import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.time.Duration
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

run {
  val debugKeystoreFile = file("${rootDir}/debug.keystore")
  val debugKeystoreB64File = file("${rootDir}/debug.keystore.base64")
  if (!debugKeystoreFile.exists() && debugKeystoreB64File.exists()) {
    runCatching {
      val cleanB64 = debugKeystoreB64File.readText(Charsets.UTF_8).replace("\\s+".toRegex(), "")
      debugKeystoreFile.writeBytes(Base64.getDecoder().decode(cleanB64))
    }
  }

  val googleServicesFile = file("google-services.json")
  val googleServicesB64File = file("${rootDir}/google-services.json.base64")
  if ((!googleServicesFile.exists() || googleServicesFile.length() < 50L) && googleServicesB64File.exists()) {
    runCatching {
      val cleanB64 = googleServicesB64File.readText(Charsets.UTF_8).replace("\\s+".toRegex(), "")
      googleServicesFile.writeBytes(Base64.getDecoder().decode(cleanB64))
    }
  }

  val uploadKeystoreFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
  if (!uploadKeystoreFile.exists() && debugKeystoreFile.exists()) {
    runCatching {
      val srcPass = "android".toCharArray()
      val destPass = (System.getenv("STORE_PASSWORD") ?: "android").toCharArray()
      val destKeyPass = (System.getenv("KEY_PASSWORD") ?: "android").toCharArray()
      val srcKs = KeyStore.getInstance(KeyStore.getDefaultType())
      FileInputStream(debugKeystoreFile).use { srcKs.load(it, srcPass) }
      val key = srcKs.getKey("androiddebugkey", srcPass)
      val chain = srcKs.getCertificateChain("androiddebugkey")
      if (key != null && chain != null) {
        val destKs = KeyStore.getInstance(KeyStore.getDefaultType())
        destKs.load(null, destPass)
        destKs.setKeyEntry("upload", key, destKeyPass, chain)
        FileOutputStream(uploadKeystoreFile).use { destKs.store(it, destPass) }
      }
    }
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.sorulab.ykslab"
    minSdk = 24
    targetSdk = 36
    versionCode = 14
    versionName = "1.1.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}
