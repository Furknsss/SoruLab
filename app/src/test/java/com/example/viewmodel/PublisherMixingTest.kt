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
}
