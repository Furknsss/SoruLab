package com.example.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardInkCoordinatesTest {
    private fun checkPoint(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    @Test fun zoomAndPanMapPenContactBackToOriginalPage() {
        // The page point (300,400), centered at (500,600), appears at
        // (200,100) after 2x zoom and (+100,-100) pan.
        checkPoint(Offset(300f, 400f), BoardInkCoordinates.toDocument(
            Offset(200f, 100f), Size(1000f, 1200f), 2f, Offset(100f, -100f)
        ))
    }

    @Test fun scrolledQuestionRetainsItsDocumentInkCoordinates() {
        checkPoint(Offset(150f, 880f), BoardInkCoordinates.toDocument(
            Offset(150f, 180f), Size(800f, 1000f), 1f, Offset.Zero, 700f
        ))
    }

    @Test fun oppositePanAndMaximumZoomKeepPointsAtThePenTip() {
        // Document point (400,650), scroll=300 => local (400,350),
        // then scale 4 around (500,600), pan=(-150,+200).
        checkPoint(Offset(400f, 650f), BoardInkCoordinates.toDocument(
            Offset(-50f, -200f), Size(1000f, 1200f), 4f, Offset(-150f, 200f), 300f
        ))
    }

    @Test fun unzoomedLegacyCoordinatesRemainUnchanged() {
        checkPoint(Offset(123f, 456f), BoardInkCoordinates.toDocument(
            Offset(123f, 456f), Size(800f, 1000f), 1f, Offset.Zero
        ))
    }
}
