package com.privateplayer.media

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.database.DatabaseModule
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.database.entity.FolderEntity
import com.privateplayer.core.database.entity.PlaybackHistoryEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleDelayMigrationInstrumentedTest {
    @Test
    fun migratesFromVersion1ThroughVersion2() = runBlocking { verifyMigrationFrom(1) }

    @Test
    fun migratesFromVersion2KeepingSubtitleSelections() = runBlocking { verifyMigrationFrom(2) }

    private suspend fun verifyMigrationFrom(oldVersion: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "subtitle-delay-v$oldVersion-${UUID.randomUUID()}.db"
        val asset = "${PrivatePlayerDatabase::class.java.name}/$oldVersion.json"
        val schema = instrumentation.context.assets.open(asset).bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.transaction {
                    createHistoricalSchema(schema)
                    insertHistoricalRows(oldVersion)
                    version = schema.getInt("version")
                }
            }
            val migrated = DatabaseModule.openDatabase(context, name)
            try {
                val dao = migrated.mediaDao()
                val first = requireNotNull(dao.findById(1L))
                val second = requireNotNull(dao.findById(2L))
                assertEquals("media/first.mkv", first.relativePath)
                assertEquals("media/second.mkv", second.relativePath)
                assertEquals(2_000L, first.playbackPositionMs)
                assertTrue(first.isFavorite)
                assertEquals(if (oldVersion == 2) "2" else null, first.selectedSubtitleTrackId)
                assertEquals(if (oldVersion == 2) "off" else null, second.selectedSubtitleTrackId)
                assertEquals(0L, first.subtitleDelayMs)
                assertEquals(0L, second.subtitleDelayMs)
                assertEquals(FolderEntity(7L, "旧目录", 11L, 4), migrated.folderDao().findById(7L))
                assertEquals(
                    PlaybackHistoryEntity(9L, first.id, 35L, 2_000L, 50_000L),
                    migrated.playbackHistoryDao().findForMedia(first.id).single(),
                )

                dao.setSubtitleDelayMs(first.id, -350L)
                assertEquals(first.copy(subtitleDelayMs = -350L), dao.findById(first.id))
                assertEquals(second, dao.findById(second.id))

                dao.setSubtitleDelayMs(second.id, 750L)
                dao.setSubtitleDelayMs(first.id, 0L)
                assertEquals(first, dao.findById(first.id))
                assertEquals(second.copy(subtitleDelayMs = 750L), dao.findById(second.id))
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    /** 直接使用 KSP 导出的旧表、索引和 Room 标识；不从当前实体反推历史结构。 */
    private fun SQLiteDatabase.createHistoricalSchema(schema: JSONObject) {
        val tables = schema.getJSONArray("entities")
        for (i in 0 until tables.length()) {
            val entity = tables.getJSONObject(i)
            val tableName = entity.getString("tableName")
            fun create(definition: JSONObject) =
                execSQL(definition.getString("createSql").replace("\${TABLE_NAME}", tableName))
            create(entity)
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) create(indices.getJSONObject(j))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) execSQL(setup.getString(i))
    }

    private fun SQLiteDatabase.insertHistoricalRows(oldVersion: Int) {
        execSQL("INSERT INTO folders (id, name, createdAt, sortOrder) VALUES (7, '旧目录', 11, 4)")
        val insertMedia = """
            INSERT INTO media (id, displayName, fileName, relativePath, folderId, sizeBytes, durationMs,
                width, height, audioTrackCount, subtitleTrackCount, fingerprint, isFavorite, addedAt,
                updatedAt, lastPlayedAt, playbackPositionMs, playedDurationMs, playCount)
            VALUES (?, ?, ?, ?, 7, 1234, 50000, 1280, 720, 1, 2, ?, 1, 11, 22, 35, 2000, 2500, 3)
        """.trimIndent()
        execSQL(insertMedia, arrayOf(1L, "第一片", "first.mkv", "media/first.mkv", "fingerprint-1"))
        execSQL(insertMedia, arrayOf(2L, "第二片", "second.mkv", "media/second.mkv", "fingerprint-2"))
        if (oldVersion == 2) {
            execSQL("UPDATE media SET selectedSubtitleTrackId = '2' WHERE id = 1")
            execSQL("UPDATE media SET selectedSubtitleTrackId = 'off' WHERE id = 2")
        }
        execSQL("INSERT INTO playback_history (id, mediaId, playedAt, positionMs, durationMs) VALUES (9, 1, 35, 2000, 50000)")
    }
}
