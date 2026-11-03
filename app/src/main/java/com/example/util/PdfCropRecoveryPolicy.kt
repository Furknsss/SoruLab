package com.example.util

/**
 * Publisher-independent safety gate for an optional second crop pass using
 * strongly ordered native-PDF printed question anchors.
 *
 * Never replace a crop with more *unverified* blocks. The fallback must add
 * previously missing, correctly numbered printed questions and retain all
 * source-confirmed numbers that the primary crop already found.
 */
internal object PdfCropRecoveryPolicy {
    fun shouldRetry(
        sourceNumbers: Set<Int>,
        firstPassNumbers: Set<Int>,
        sourceHasOptions: Boolean
    ): Boolean = sourceHasOptions && sourceNumbers.size >= 2 &&
        (sourceNumbers - firstPassNumbers).isNotEmpty()

    fun preferRecovery(
        sourceNumbers: Set<Int>,
        firstPassNumbers: List<Int>,
        recoveredNumbers: List<Int>
    ): Boolean {
        if (sourceNumbers.isEmpty() || recoveredNumbers.isEmpty()) return false
        if (recoveredNumbers.size != recoveredNumbers.toSet().size) return false
        val first = firstPassNumbers.toSet().intersect(sourceNumbers)
        val recovered = recoveredNumbers.toSet()
        return recovered.all { it in sourceNumbers } &&
            recovered.containsAll(first) &&
            recovered.size > first.size
    }
}
