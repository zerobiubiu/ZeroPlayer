package com.privateplayer.media

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.repository.MediaRepositoryImpl
import com.privateplayer.media.playback.PlayerMediaSessionService
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 真实系统 Intent、Room 和 WorkManager；外部源始终保持原内容。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ExternalImportInstrumentedTest {
    @Test
    fun opensVideoOnlyAfterPrivateCopyAndImportsMultipleSharedFiles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) {
            "该测试只在可丢弃模拟器运行"
        }
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val resolver = context.contentResolver
        val sources = mutableListOf<Uri>()
        val database = Room.databaseBuilder(context, PrivatePlayerDatabase::class.java, PrivatePlayerDatabase.NAME).build()
        val storage = PrivateStorage(context)
        val fileTree = FileTreeRepositoryImpl(
            database, database.mediaDao(), database.playbackHistoryDao(), database.playlistDao(),
            storage, MediaProbe(), Dispatchers.IO,
        )
        val mediaRepository = MediaRepositoryImpl(
            database.mediaDao(), database.folderDao(), storage, fileTree, Dispatchers.IO,
        )
        var activity: MainActivity? = null
        fun source(name: String, mime: String, content: ByteArray): Uri {
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "外部导入-${UUID.randomUUID()}-$name")
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }))
            sources += uri
            requireNotNull(resolver.openOutputStream(uri)).use { it.write(content) }
            return uri
        }
        suspend fun awaitCopy(uri: Uri) = withTimeout(60_000) {
            var task = database.importTaskDao().observeAll().first().firstOrNull { it.sourceUri == uri.toString() }
            while (task == null || task.state !in setOf("COMPLETED", "FAILED", "CANCELED")) {
                delay(100)
                task = database.importTaskDao().observeAll().first().firstOrNull { it.sourceUri == uri.toString() }
            }
            requireNotNull(task).also {
                assertEquals(it.errorMessage, "COMPLETED", it.state)
            }
        }
        try {
            val video = instrumentation.context.assets.open("playback-regression.mkv").use { it.readBytes() }
            val videoUri = source("影片.mkv", "video/x-matroska", video)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(videoUri, "video/x-matroska")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            assertTrue(context.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.packageName == context.packageName })
            activity = instrumentation.startActivitySync(view.setClass(context, MainActivity::class.java)) as MainActivity
            val task = awaitCopy(videoUri)
            val media = requireNotNull(database.mediaDao().findById(requireNotNull(task.mediaId)))
            assertTrue(media.relativePath.startsWith("media/"))
            val copied = File(context.filesDir, media.relativePath)
            assertArrayEquals(video, copied.readBytes())
            assertArrayEquals(video, requireNotNull(resolver.openInputStream(videoUri)).use { it.readBytes() })
            withTimeout(30_000) {
                while ((database.mediaDao().findById(media.id)?.playCount ?: 0) == 0) delay(100)
            }

            val subtitle = "1\n00:00:00,000 --> 00:00:02,000\n分享字幕\n".toByteArray()
            val subtitleUri = source("字幕.srt", "application/x-subrip", subtitle)
            val emptyUri = source("空文件.bin", "application/octet-stream", byteArrayOf())
            val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                setClass(context, MainActivity::class.java)
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(subtitleUri, emptyUri))
                clipData = ClipData.newRawUri("字幕", subtitleUri).apply { addItem(ClipData.Item(emptyUri)) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            instrumentation.runOnMainSync { requireNotNull(activity).startActivity(share) }
            val subtitleTask = awaitCopy(subtitleUri)
            val emptyTask = awaitCopy(emptyUri)
            assertTrue(subtitleTask.mediaId == null && emptyTask.mediaId == null)
            val subtitlePath = subtitleTask.targetFileName
            val emptyPath = emptyTask.targetFileName
            assertArrayEquals(subtitle, File(storage.mediaRoot, subtitlePath).readBytes())
            assertEquals(0L, File(storage.mediaRoot, emptyPath).length())
            assertArrayEquals(subtitle, requireNotNull(resolver.openInputStream(subtitleUri)).use { it.readBytes() })
            assertTrue(requireNotNull(resolver.openInputStream(emptyUri)).use { it.read() } == -1)
            instrumentation.runOnMainSync {
                assertEquals(Intent.ACTION_SEND_MULTIPLE, requireNotNull(activity).intent.action)
            }
        } finally {
            activity?.let { target ->
                instrumentation.runOnMainSync { target.finish() }
                withTimeout(10_000) {
                    var destroyed = false
                    while (!destroyed) {
                        instrumentation.runOnMainSync {
                            destroyed = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(target) == Stage.DESTROYED
                        }
                        if (!destroyed) delay(50)
                    }
                }
            }
            context.stopService(Intent(context, PlayerMediaSessionService::class.java))
            val owned = database.importTaskDao().observeAll().first().filter { task -> sources.any { it.toString() == task.sourceUri } }
            owned.filter { it.state == "COMPLETED" }.forEach { task ->
                task.mediaId?.let { mediaRepository.delete(listOf(it)) }
                    ?: File(storage.mediaRoot, task.targetFileName).delete()
                database.importTaskDao().deleteById(task.id)
            }
            sources.forEach { resolver.delete(it, null, null) }
            database.close()
        }
    }
}
