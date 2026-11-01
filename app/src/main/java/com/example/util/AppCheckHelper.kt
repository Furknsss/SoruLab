package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val FALLBACK_WEB_CLIENT_ID =
    "656943524635-kg7aottftmb82gj95fkde001dbptbh0b.apps.googleusercontent.com"

fun getGoogleWebClientId(context: Context): String {
    val pkgResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (pkgResId != 0) {
        val value = runCatching { context.getString(pkgResId).trim() }.getOrDefault("")
        if (value.isNotBlank() && value.endsWith(".apps.googleusercontent.com")) {
            return value
        }
    }
    val namespaceResId = context.resources.getIdentifier("default_web_client_id", "string", "com.example")
    if (namespaceResId != 0) {
        val value = runCatching { context.getString(namespaceResId).trim() }.getOrDefault("")
        if (value.isNotBlank() && value.endsWith(".apps.googleusercontent.com")) {
            return value
        }
    }
    val fallbackFromStrings = runCatching {
        context.getString(R.string.sorulab_web_client_id).trim()
    }.getOrDefault("")
    return fallbackFromStrings.ifBlank { FALLBACK_WEB_CLIENT_ID }
}

private fun buildFallbackFirebaseOptions(context: Context): FirebaseOptions? {
    return try {
        val apiKeyB64 = runCatching { context.getString(R.string.sorulab_firebase_api_key_b64).trim() }
            .getOrDefault("QUl6YVN5RG95QjBHc3hQY1EtWENlaUFJYll0ZWJtVGhDeXhrNndr")
        val decodedApiKey = String(Base64.decode(apiKeyB64, Base64.DEFAULT), Charsets.UTF_8).trim()
        val projectId = runCatching { context.getString(R.string.sorulab_firebase_project_id).trim() }
            .getOrDefault("gen-lang-client-0143749618")
        val applicationId = runCatching { context.getString(R.string.sorulab_firebase_app_id).trim() }
            .getOrDefault("1:656943524635:android:8ff497c04d59f36e5e01be")
        val gcmSenderId = runCatching { context.getString(R.string.sorulab_firebase_sender_id).trim() }
            .getOrDefault("656943524635")
        val storageBucket = runCatching { context.getString(R.string.sorulab_firebase_storage_bucket).trim() }
            .getOrDefault("gen-lang-client-0143749618.firebasestorage.app")

        if (decodedApiKey.isBlank() || projectId.isBlank() || applicationId.isBlank()) {
            null
        } else {
            FirebaseOptions.Builder()
                .setApiKey(decodedApiKey)
                .setProjectId(projectId)
                .setApplicationId(applicationId)
                .setGcmSenderId(gcmSenderId)
                .setStorageBucket(storageBucket)
                .build()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Fallback FirebaseOptions oluşturulamadı: ${e.message}")
        null
    }
}

fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isEmpty()) {
            runCatching { FirebaseApp.initializeApp(appCtx) }
        }
        var defaultApp = FirebaseApp.getApps(appCtx)
            .firstOrNull { it.name == "[DEFAULT]" }

        val currentApiKey = defaultApp?.options?.apiKey
        if (defaultApp == null || currentApiKey.isNullOrBlank() || currentApiKey.contains("dummy", ignoreCase = true)) {
            val fallbackOptions = buildFallbackFirebaseOptions(appCtx)
            if (fallbackOptions != null) {
                defaultApp = runCatching {
                    if (defaultApp == null) {
                        FirebaseApp.initializeApp(appCtx, fallbackOptions)
                    } else {
                        defaultApp
                    }
                }.getOrNull()
            }
        }

        val resolvedApp = defaultApp ?: FirebaseApp.getApps(appCtx).firstOrNull { it.name == "[DEFAULT]" }
            ?: return false
        val apiKey = resolvedApp.options.apiKey
        if (apiKey.isNullOrBlank() || apiKey.contains("dummy", ignoreCase = true)) {
            Log.e(TAG, "Firebase yapılandırmasında geçerli bir API anahtarı bulunamadı.")
            return false
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Firebase başlatılamadı (${e.javaClass.simpleName}).")
        false
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (!ensureFirebaseInitialized(context)) {
            Log.e(TAG, "App Check kurulamadı: Firebase yapılandırması eksik veya geçersiz.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .apply()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val siteKey = runCatching { context.getString(R.string.recaptcha_site_key).trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
                    if (resId != 0) context.getString(resId).trim() else ""
                }
            if (siteKey.isNotBlank()) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            } else {
                Log.e(TAG, "recaptcha_site_key resource string is blank or missing.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Firebase App Check kurulamadı (${e.javaClass.simpleName}).")
    }
}
