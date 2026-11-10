package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Transactional, per-account index for large offline question libraries.
 * One row per question avoids rewriting a multi-megabyte JSON array after every import.
 * Images remain as separate files in internal storage; this index is not a cloud backup.
 *
 * A missing initialization marker means a legacy JSON migration is still needed.
 * After a successful transaction (including an intentionally empty library) the
 * SQLite store becomes authoritative, so deletions cannot resurrect old caches.
 */
internal class QuestionLibrarySqliteStore(context: Context, userId: String) : java.io.Closeable {
    private val ownerUid = userId
    private val safeUid = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, "sorulab_library_${safeUid}.db", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE questions (id TEXT PRIMARY KEY NOT NULL, book_id TEXT NOT NULL, topic_id TEXT NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX questions_book_idx ON questions(book_id)")
            db.execSQL("CREATE INDEX questions_topic_idx ON questions(topic_id)")
            db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Never drop an imported question library during schema upgrades.
            error("Unsupported library database schema: $oldVersion → $newVersion")
        }
    }.apply { setWriteAheadLoggingEnabled(true) }

    override fun close() = helper.close()

    fun read(): List<CustomQuestionRecord>? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT value FROM metadata WHERE key = 'initialized' LIMIT 1", null).use {
            if (!it.moveToFirst()) return null
        }
        val output = ArrayList<CustomQuestionRecord>()
        db.rawQuery("SELECT payload FROM questions ORDER BY rowid ASC", null).use { rows ->
            while (rows.moveToNext()) {
                val record = decode(JSONObject(rows.getString(0)))
                check(record.userId.isBlank() || record.userId == ownerUid) {
                    "Soru kaydı başka kullanıcıya ait. Hesaplar karıştırılamaz."
                }
                output.add(if (record.userId.isBlank()) record.copy(userId = ownerUid) else record)
            }
        }
        return output
    }

    fun count(): Int {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM questions", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun save(records: List<CustomQuestionRecord>, replace: Boolean) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            if (replace) db.delete("questions", null, null)
            val stmt = db.compileStatement(
                "INSERT OR REPLACE INTO questions (id, book_id, topic_id, payload) VALUES (?, ?, ?, ?)"
            )
            stmt.use { insert ->
                for ((index, record) in records.withIndex()) {
                    insert.clearBindings()
                    insert.bindString(1, record.id.ifBlank { "_legacy_$index" })
                    insert.bindString(2, record.bookId)
                    insert.bindString(3, record.topicId)
                    insert.bindString(4, encode(if (record.userId.isBlank())
                        record.copy(userId = ownerUid) else record).toString())
                    insert.executeInsert()
                }
            }
            db.execSQL("INSERT OR REPLACE INTO metadata(key, value) VALUES('initialized', '1')")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun encode(rec: CustomQuestionRecord): JSONObject = JSONObject().apply {
        put("id", rec.id)
        put("userId", rec.userId)
        put("examType", rec.examType)
        put("subjectId", rec.subjectId)
        put("subjectName", rec.subjectName)
        put("topicId", rec.topicId)
        put("topicName", rec.topicName)
        put("difficulty", rec.difficulty)
        put("correctOption", rec.correctOption)
        put("questionText", rec.questionText)
        put("learningOutcome", rec.learningOutcome)
        put("questionType", rec.questionType)
        put("publisherTag", rec.publisherTag)
        put("publisherId", rec.publisherId)
        put("sourceBookTitle", rec.sourceBookTitle)
        put("aiSolutionSummary", rec.aiSolutionSummary)
        put("cropAspectRatio", rec.cropAspectRatio)
        put("bookId", rec.bookId)
        put("imageBase64", rec.imageBase64)
        put("pdfFilePath", rec.pdfFilePath)
        put("pageIndex", rec.pageIndex)
        put("questionNumberInTest", rec.questionNumberInTest)
        put("normLeft", rec.normLeft.toDouble())
        put("normTop", rec.normTop.toDouble())
        put("normRight", rec.normRight.toDouble())
        put("normBottom", rec.normBottom.toDouble())
    }

    private fun decode(obj: JSONObject): CustomQuestionRecord = CustomQuestionRecord(
        id = obj.optString("id"),
        userId = obj.optString("userId"),
        examType = obj.optString("examType", "TYT"),
        subjectId = obj.optString("subjectId"),
        subjectName = obj.optString("subjectName"),
        topicId = obj.optString("topicId"),
        topicName = obj.optString("topicName"),
        difficulty = obj.optString("difficulty", "Orta"),
        correctOption = obj.optString("correctOption"),
        questionText = obj.optString("questionText"),
        learningOutcome = obj.optString("learningOutcome"),
        questionType = obj.optString("questionType"),
        publisherTag = obj.optString("publisherTag", "Kaynak Belirtilmedi"),
        publisherId = obj.optString("publisherId"),
        sourceBookTitle = obj.optString("sourceBookTitle"),
        aiSolutionSummary = obj.optString("aiSolutionSummary"),
        cropAspectRatio = obj.optDouble("cropAspectRatio", 1.25),
        bookId = obj.optString("bookId"),
        imageBase64 = obj.optString("imageBase64"),
        pdfFilePath = obj.optString("pdfFilePath"),
        pageIndex = obj.optInt("pageIndex", -1),
        questionNumberInTest = obj.optInt("questionNumberInTest", 0),
        normLeft = obj.optDouble("normLeft").toFloat(),
        normTop = obj.optDouble("normTop").toFloat(),
        normRight = obj.optDouble("normRight", 1.0).toFloat(),
        normBottom = obj.optDouble("normBottom", 1.0).toFloat()
    )
}
