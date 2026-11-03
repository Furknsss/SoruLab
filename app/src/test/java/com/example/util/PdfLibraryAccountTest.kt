package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLibraryAccountTest {
    @Test fun `offline student always has the same readable cache`() {
        assertEquals("device_yks_student_tr", PdfLibraryAccount.cacheUid(null, null))
        assertEquals("device_yks_student_tr", PdfLibraryAccount.cacheUid("", ""))
    }

    @Test fun `authenticated user does not inherit another user's guest cache`() {
        assertEquals("user_A", PdfLibraryAccount.cacheUid("user_A", null))
        assertEquals("user_B", PdfLibraryAccount.cacheUid("user_B", "old_account"))
    }

    @Test fun `previously recognized local account works when Firebase is temporarily unavailable`() {
        assertEquals("saved_account", PdfLibraryAccount.cacheUid(null, "saved_account"))
    }
}
