import groovy.json.JsonSlurper
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
    versionCode = 19
    versionName = "1.2.0"

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

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR }

val verifyFirebaseConfiguration by tasks.registering {
  val googleServicesFile = layout.projectDirectory.file("google-services.json")
  inputs.file(googleServicesFile)
  inputs.file(rootProject.layout.projectDirectory.file(".firebaserc"))
  inputs.file(rootProject.layout.projectDirectory.file("firebase.json"))
  inputs.file(layout.projectDirectory.file("src/main/res/values/firebase_applet_config.xml"))

  doLast {
    val configFile = googleServicesFile.asFile
    check(configFile.isFile) {
      "app/google-services.json is missing. Download the Android config for ${android.defaultConfig.applicationId} from Firebase Console."
    }

    val config = JsonSlurper().parse(configFile) as? Map<*, *>
      ?: error("app/google-services.json is not a valid JSON object.")
    val projectInfo = config["project_info"] as? Map<*, *>
      ?: error("Firebase config is missing project_info.")
    check(projectInfo["project_id"] == "gen-lang-client-0143749618" &&
      !projectInfo["project_number"].toString().isBlank()
    ) { "Firebase config must belong to project gen-lang-client-0143749618 and include its project number." }
    val firebaseCliConfig = JsonSlurper().parse(rootProject.file("firebase.json")) as? Map<*, *>
      ?: error("firebase.json is not a valid JSON object.")
    val androidDatabaseId = Regex("""<string name="firestore_database_id"[^>]*>([^<]+)</string>""")
      .find(layout.projectDirectory.file("src/main/res/values/firebase_applet_config.xml").asFile.readText())
      ?.groupValues
      ?.get(1)
      ?: error("Android Firestore database ID is missing.")
    val configuredDatabaseIds = (firebaseCliConfig["firestore"] as? List<*>)
      .orEmpty()
      .mapNotNull { (it as? Map<*, *>)?.get("database")?.toString() }
    check(configuredDatabaseIds.contains(androidDatabaseId)) {
      "firebase.json must target the same Firestore database ID as the Android app."
    }

    val firebaseCliProject = JsonSlurper().parse(rootProject.file(".firebaserc")) as? Map<*, *>
      ?: error(".firebaserc is not a valid JSON object.")
    val defaultProject = (firebaseCliProject["projects"] as? Map<*, *>)?.get("default")
    check(defaultProject == projectInfo["project_id"]) {
      ".firebaserc default project must match the Firebase Android config project."
    }

    val clients = config["client"] as? List<*>
      ?: error("Firebase config has no Android client.")
    val appClient = clients.firstNotNullOfOrNull { entry ->
      val client = entry as? Map<*, *> ?: return@firstNotNullOfOrNull null
      val clientInfo = client["client_info"] as? Map<*, *> ?: return@firstNotNullOfOrNull null
      val androidInfo = clientInfo["android_client_info"] as? Map<*, *> ?: return@firstNotNullOfOrNull null
      client.takeIf { androidInfo["package_name"] == android.defaultConfig.applicationId }
    } ?: error("Firebase config does not contain ${android.defaultConfig.applicationId}.")

    val hasApiKey = (appClient["api_key"] as? List<*>)
      .orEmpty()
      .any { entry ->
        (entry as? Map<*, *>)?.get("current_key")?.toString()?.isNotBlank() == true
      }
    check(hasApiKey) { "Firebase Android client is missing its API key." }

    val oauthClients = (appClient["oauth_client"] as? List<*>).orEmpty()
    val hasWebOAuthClient = oauthClients.any { entry ->
      val client = entry as? Map<*, *> ?: return@any false
      client["client_type"].toString() == "3" &&
        client["client_id"].toString().endsWith(".apps.googleusercontent.com")
    }
    check(hasWebOAuthClient) {
      "Google Sign-In is not configured: Firebase config must include a Web OAuth client (client_type 3)."
    }

    val hasAndroidOAuthClient = oauthClients.any { entry ->
      val client = entry as? Map<*, *> ?: return@any false
      val androidInfo = client["android_info"] as? Map<*, *> ?: return@any false
      client["client_type"].toString() == "1" &&
        androidInfo["package_name"] == android.defaultConfig.applicationId &&
        androidInfo["certificate_hash"]?.toString()?.isNotBlank() == true
    }
    check(hasAndroidOAuthClient) {
      "Google Sign-In is not configured: register this Android app and its signing certificate SHA-1 in Firebase Console."
    }
  }
}

tasks.named("preBuild").configure {
  dependsOn(verifyFirebaseConfiguration)
}

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
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
