package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCropRecoveryPolicyTest {
    @Test fun `recover a dropped right column without inventing question numbers`() {
        val source = (1..5).toSet()
        assertTrue(PdfCropRecoveryPolicy.shouldRetry(source, setOf(1, 2, 3), true))
        assertTrue(PdfCropRecoveryPolicy.preferRecovery(
            source, listOf(1, 2, 3), listOf(1, 2, 3, 4, 5)
        ))
    }

    @Test fun `scanned pages without trusted printed numbers are not forced`() {
        assertFalse(PdfCropRecoveryPolicy.shouldRetry(emptySet(), setOf(1), true))
        assertFalse(PdfCropRecoveryPolicy.shouldRetry(setOf(1, 2), setOf(1), false))
    }

    @Test fun `duplicated and unsupported labels can never replace valid crops`() {
        assertFalse(PdfCropRecoveryPolicy.preferRecovery(
            setOf(1, 2, 3), listOf(1, 2), listOf(1, 1, 3)
        ))
        assertFalse(PdfCropRecoveryPolicy.preferRecovery(
            setOf(1, 2, 3), listOf(1, 2), listOf(1, 2, 9)
        ))
        assertFalse(PdfCropRecoveryPolicy.preferRecovery(
            setOf(1, 2, 3), listOf(1, 2), listOf(2, 3)
        ))
    }

    @Test fun `already complete page is never cropped twice`() {
        assertFalse(PdfCropRecoveryPolicy.shouldRetry(setOf(4, 5), setOf(4, 5), true))
        assertFalse(PdfCropRecoveryPolicy.preferRecovery(
            setOf(4, 5), listOf(4, 5), listOf(4, 5)
        ))
    }
}
