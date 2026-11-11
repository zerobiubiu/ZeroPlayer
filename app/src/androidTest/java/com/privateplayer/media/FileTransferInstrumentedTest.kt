package com.privateplayer.media

import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** 真实文件提供器和系统文档写入，不通过假仓库或镜像回调验证分享。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class FileTransferInstrumentedTest {
    @Test
    fun sharesOnlyTreeFilesAndExportsExactBytesWithoutChangingSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val parent = File(context.filesDir, "media/分享回归-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = parent
            override fun getCacheDir(): File = File(parent, "cache").apply { mkdirs() }
        }
        val storage = PrivateStorage(isolated)
        check(storage.ensureLayout())
        val database = Room.inMemoryDatabaseBuilder(context, PrivatePlayerDatabase::class.java).build()
        val tree = FileTreeRepositoryImpl(
            database, database.mediaDao(), database.playbackHistoryDao(), database.playlistDao(),
            storage, MediaProbe(), Dispatchers.IO,
        )
        val bytes = ByteArray(1024 * 1024 + 17) { index -> (index * 17).toByte() }
        val directory = File(storage.mediaRoot, "字幕").apply { check(mkdir()) }
        val source = File(directory, "原片.zh.srt").apply { writeBytes(bytes) }
        val resolver = context.contentResolver
        val destination = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "导出回归-${UUID.randomUUID()}.srt")
            put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        try {
            val manager = FileTransferManager(context, tree, Dispatchers.IO)
            val chooser = manager.share("字幕/${source.name}")
            @Suppress("DEPRECATION")
            val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            @Suppress("DEPRECATION")
            val sharedUri = requireNotNull(send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(sharedUri, send.clipData?.getItemAt(0)?.uri)
            assertArrayEquals(bytes, requireNotNull(resolver.openInputStream(sharedUri)).use { it.readBytes() })
            resolver.query(sharedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                check(it.moveToFirst())
                assertEquals(source.name, it.getString(0))
            }
            var copied = 0L
            manager.export("字幕/${source.name}", destination) { value, _ -> copied = value }
            assertEquals(bytes.size.toLong(), copied)
            assertArrayEquals(bytes, requireNotNull(resolver.openInputStream(destination)).use { it.readBytes() })
            assertArrayEquals(bytes, source.readBytes())
            try {
                manager.export("字幕/${source.name}", sharedUri) { _, _ -> }
                fail("不应允许向自身分享地址覆盖写入")
            } catch (_: java.io.IOException) {
                assertArrayEquals(bytes, source.readBytes())
            }
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.filesDir, "数据库不可分享"))
                fail("FileProvider 不应公开私有文件树以外的文件")
            } catch (_: IllegalArgumentException) {
            }
        } finally {
            resolver.delete(destination, null, null)
            database.close()
            parent.deleteRecursively()
        }
    }
}
