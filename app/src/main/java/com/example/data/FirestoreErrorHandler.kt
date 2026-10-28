package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    CREATE, UPDATE, DELETE, LIST, GET, WRITE
}

fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String?
) {
    val currentUser = Firebase.auth.currentUser
    val providerInfo = org.json.JSONArray().apply {
        currentUser?.providerData?.forEach { profile ->
            put(JSONObject().apply {
                put("providerId", profile.providerId)
                put("displayName", profile.displayName)
                put("email", profile.email)
                put("photoUrl", profile.photoUrl?.toString())
            })
        }
    }

    val authInfo = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("isAnonymous", currentUser?.isAnonymous)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", providerInfo)
    }

    val errInfo = JSONObject().apply {
        put("error", error.message ?: error.toString())
        if (error is FirebaseFirestoreException) {
            put("code", error.code.name)
        }
        put("operationType", operationType.name.lowercase())
        put("path", path)
        put("authInfo", authInfo)
    }

    val jsonString = errInfo.toString()
    Log.e("FirestoreError", jsonString, error)
    throw RuntimeException(jsonString, error)
}
