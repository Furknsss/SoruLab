package com.example.util

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal class PdfVisionAccessException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Reject unavailable attestation before copying or scanning a whole book. */
internal suspend fun requirePdfVisionAccess(context: Context) {
    if (!ensureFirebaseInitialized(context)) {
        throw PdfVisionAccessException(
            "Görsel analiz bağlantısı kurulamadı. Uygulamanın Firebase yapılandırması kontrol edilmeli."
        )
    }
    try {
        val result = withTimeout(30_000L) {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }
        check(result.token.isNotBlank()) { "Empty App Check attestation" }
    } catch (error: TimeoutCancellationException) {
        throw PdfVisionAccessException(
            "Görsel analiz bağlantısı zaman aşımına uğradı. İnternet bağlantınızı kontrol edip yeniden deneyin.",
            error
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Never display SDK exception messages: they can contain credentials.
        throw PdfVisionAccessException(
            "Görsel analiz için uygulama doğrulaması başarısız. Firebase App Check kaydı kontrol edilmeli. " +
                "Kitap işlenmedi; mevcut kitaplarınız değişmedi.",
            error
        )
    }
}
