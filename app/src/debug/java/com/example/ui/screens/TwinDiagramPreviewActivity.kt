package com.example.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import com.example.viewmodel.TwinRightTriangleTemplate

/**
 * DEBUG ONLY. Test the actual Compose figure renderer with one computed
 * right-triangle source/twin pair, never a mock LLM image.
 */
class TwinDiagramPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val original = YksQuestion(
            id = "twin-preview-source-345",
            number = 1,
            topicHeader = "Dik Üçgenler",
            stemTop = "ABC dik üçgeninde A açısı 90° ve AB=3 cm, AC=4 cm olduğuna göre BC kaç cm'dir?",
            stemBottom = "",
            options = mapOf("A" to "3", "B" to "4", "C" to "5", "D" to "6", "E" to "7"),
            correctOption = "C",
            stepByStepSolution = "Pisagor: BC=5",
            answerKeyVerified = true,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE
        )
        val twin = TwinRightTriangleTemplate.generate(original)
            ?: error("Preview source failed to produce its verified twin")
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF7F9FC))
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("SoruLab • İkiz Soru Görsel Testi", fontWeight = FontWeight.Bold)
                    Text("KAYNAK: " + original.stemTop)
                    Text("YENİ İKİZ: " + twin.questionStem)
                    TwinDiagramCanvas(twin.diagramJson)
                    Text("Cevap seçenekleri", fontWeight = FontWeight.Bold)
                    twin.options.forEach { (letter, value) ->
                        Text(letter + ") " + value, modifier = Modifier.fillMaxWidth())
                    }
                    Text("Yeniden hesaplanan sonuç: " +
                        twin.options.getValue(twin.correctOption))
                    Text(twin.solutionExplanation)
                }
            }
        }
    }
}
