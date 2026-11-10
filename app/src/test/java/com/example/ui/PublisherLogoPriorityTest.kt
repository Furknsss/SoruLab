package com.example.ui.screens

import com.example.data.YksPublisherInfo
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PublisherLogoPriorityTest {
    private fun publisher(id: String, path: String) = YksPublisherInfo(
        id = id,
        name = if (id == "oks") "OKS" else "345 Yayınları",
        shortCode = if (id == "oks") "OKS" else "345",
        primaryColorHex = 0xFF102B5FL, secondaryColorHex = 0xFF172554L,
        customLogoPath = path
    )

    @Test fun manuallyAssignedBookPublisherOverridesObsoleteZipLogo() {
        val chosen = File.createTempFile("chosen-345", ".png")
        val stale = File.createTempFile("stale-oks", ".png")
        try {
            assertEquals(chosen.absolutePath,
                preferredPublisherLogoPath(chosen.absolutePath, stale.absolutePath))
            assertEquals(chosen.absolutePath,
                preferredPublisherLogoPath("file://" + chosen.absolutePath, stale.absolutePath))
        } finally {
            chosen.delete()
            stale.delete()
        }
    }

    @Test fun oldLogoIsOnlyFallbackIfStudentHasNoStoredArtwork() {
        val stale = File.createTempFile("stale-zip", ".png")
        try {
            assertEquals("",
                preferredPublisherLogoPath("/no/such/selected.png", stale.absolutePath))
            assertEquals(stale.absolutePath,
                preferredPublisherLogoPath("", stale.absolutePath))
            assertEquals("", preferredPublisherLogoPath("", "/no/such/image.png"))
        } finally {
            stale.delete()
        }
    }

    @Test fun explicitlySelectedPublisherWinsZipSuppliedPublisherIdentity() {
        val pilihan = listOf(publisher("345", "/books/345.png"),
            publisher("oks", "/books/oks.png"))
        assertEquals("345", publisherForZipImport("OKS", "345", pilihan).id)
        assertEquals("oks", publisherForZipImport("OKS", null, pilihan).id)
        assertEquals("345", publisherForZipImport("345 Yayınları", null, pilihan).id)
    }
}
