package com.example.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class PublisherMixingTest {
    @Test
    fun interleaveByGroup_distributesPublishersAcrossThePool() {
        val mixed = interleaveByGroup(
            listOf("3D-1", "3D-2", "Bilgi Sarmal-1", "Karekök-1")
        ) { it.substringBefore('-') }

        assertEquals(
            listOf("3D-1", "Bilgi Sarmal-1", "Karekök-1", "3D-2"),
            mixed
        )
    }

    @Test
    fun interleaveByGroup_handlesUnequalPublisherCountsSmoothly() {
        val questions = listOf(
            "3D-1", "3D-2", "3D-3",
            "Apotemi-1",
            "Orijinal-1", "Orijinal-2"
        )
        val mixed = interleaveByGroup(questions) { it.substringBefore('-') }

        assertEquals(
            listOf("3D-1", "Apotemi-1", "Orijinal-1", "3D-2", "Orijinal-2", "3D-3"),
            mixed
        )
    }

    @Test
    fun interleaveByGroup_singlePublisherPreservesOrder() {
        val questions = listOf("3D-1", "3D-2", "3D-3")
        val mixed = interleaveByGroup(questions) { it.substringBefore('-') }
        assertEquals(questions, mixed)
    }

    @Test
    fun interleaveByGroup_emptyListReturnsEmpty() {
        val mixed = interleaveByGroup(emptyList<String>()) { it }
        assertEquals(emptyList<String>(), mixed)
    }
}
