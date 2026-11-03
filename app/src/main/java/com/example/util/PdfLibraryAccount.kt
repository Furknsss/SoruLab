package com.example.util

/**
 * Every imported book needs a stable local owner, even if Google/Firebase
 * login has not succeeded yet. Never use a nullable owner for cache hydration.
 */
internal object PdfLibraryAccount {
    const val GUEST_CACHE_UID = "device_yks_student_tr"

    fun cacheUid(firebaseUid: String?, rememberedUid: String?): String =
        firebaseUid?.takeIf { it.isNotBlank() }
            ?: rememberedUid?.takeIf { it.isNotBlank() }
            ?: GUEST_CACHE_UID
}
