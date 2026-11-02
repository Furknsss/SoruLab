package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalQuestionCacheIOTest {
    @Test
    fun `atomic write replaces previous complete JSON without partial state`() {
        val dir = createTempDir(prefix = "sorulab_cache_test_")
        try {
            val cache = File(dir, "questions.json")
            LocalQuestionCacheIO.writeJson(cache, "[{\"id\":\"q1\"}]")
            assertEquals("[{\"id\":\"q1\"}]", LocalQuestionCacheIO.readJson(cache))
            LocalQuestionCacheIO.writeJson(cache, "[{\"id\":\"q1\"},{\"id\":\"q2\"}]")
            assertEquals("[{\"id\":\"q1\"},{\"id\":\"q2\"}]",
                LocalQuestionCacheIO.readJson(cache))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `invalid primary JSON falls back to valid backup`() {
        assertEquals("[{\"id\":\"backup\"}]", LocalQuestionCacheIO.firstValidJson(
            listOf("[{\"id\":", "[{\"id\":\"backup\"}]", "[]")
        ))
        assertNull(LocalQuestionCacheIO.firstValidJson(listOf("{", "", null)))
    }
}
