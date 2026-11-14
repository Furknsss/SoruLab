package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DenemeStudioScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bank(): List<CustomQuestionRecord> {
        val subject=SoruLabCatalog.allSubjects.first { it.id=="tyt_fizik" }
        return subject.topics.flatMap { topic -> (1..4).map { n ->
            CustomQuestionRecord(id=topic.id+n,examType="TYT",subjectId=subject.id,
                subjectName=subject.name,topicId=topic.id,topicName=topic.name,
                difficulty="Orta",correctOption="A",questionText="Örnek soru $n",
                aiSolutionSummary=CustomQuestionRecord.packSolutionWithMeta("A",answerKeyVerified=true))
        } }
    }

    @Test fun phoneCanCreatePhysicsExamAndStrictDifficultyDisablesStart() {
        var request: DenemeRequest?=null
        compose.setContent {
            MaterialTheme { DenemeStudioScreen(null,emptyList(),emptyList(),bank(),{},
                { request=it },{},{},{}) }
        }
        compose.onNodeWithTag("create_deneme_button").assertIsDisplayed()
        capture("deneme-phone-home")
        compose.onNodeWithTag("create_deneme_button").performClick()
        compose.onNodeWithTag("deneme_branch_selector").performScrollTo().performClick()
        compose.onNodeWithText("TYT Fizik • 7 soru").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Hazır • 7 doğrulanmış soru").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Konu dağılımını gör").performScrollTo().performClick()
        compose.onNodeWithText("Optik").performScrollTo()
        capture("deneme-phone-distribution")
        compose.onNodeWithTag("deneme_difficulty_row").performScrollTo()
        compose.onNodeWithText("Zor",useUnmergedTree=true).assertIsDisplayed().performClick()
        compose.waitForIdle()
        capture("deneme-phone-after-zor")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Soru havuzunda eksikler var").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deneme_confirm_create").assertIsNotEnabled()
        compose.onNodeWithTag("deneme_difficulty_row").performScrollTo()
        compose.onNodeWithText("Orta",useUnmergedTree=true).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Hazır • 7 doğrulanmış soru").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deneme_confirm_create").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("tyt_fizik",request?.blueprintId)
            assertEquals(DenemeDifficulty.ORTA,request?.difficulty)
        }
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletShowsSavedExamAndOpensItsExactResult() {
        val ready=DenemePlanner.build(DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik"),
            bank(),sequence=77) as DenemePlan.Ready
        val record=TestResultRecord(id="attempt",testId=ready.test.id,testTitle=ready.test.title,
            correctCount=1,wrongCount=1,emptyCount=5,answersMapJson="{\"1\":\"A\",\"2\":\"B\"}",
            testSnapshotJson=TestSnapshotCodec.encode(ready.test),durationSeconds=360)
        var reviewed: TestResultRecord?=null
        compose.setContent {
            MaterialTheme { DenemeStudioScreen(null,listOf(DenemeBlueprints.fromSnapshot(ready.test)!!),
                listOf(record),bank(),{},{},{},{reviewed=it},{}) }
        }
        capture("deneme-tablet-home")
        compose.onNodeWithText("Karnem").performClick()
        compose.onNodeWithText("0,75 NET").assertIsDisplayed()
        capture("deneme-tablet-result")
        compose.onNodeWithText("Sorularımı İncele").performClick()
        compose.runOnIdle { assertEquals(record,reviewed) }
    }

    private fun capture(name: String) {
        var captured: Bitmap? = null
        compose.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                ?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            captured = bitmap
        }
        val bitmap = requireNotNull(captured)
        val file=File("build/reports/test-layout/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
