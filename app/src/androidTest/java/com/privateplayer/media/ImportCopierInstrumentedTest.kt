package com.privateplayer.media

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.storage.ImportCopier
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实文档 URI 和私有暂存验证跨块续传及来源不变。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ImportCopierInstrumentedTest {
    @Test
    fun resumesVerifiedPrefixAndRejectsChangedPrefix() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val resolver = context.contentResolver
        val source = ByteArray(3 * 64 * 1024 + 123) { index -> (index * 31).toByte() }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "续传回归-${UUID.randomUUID()}.bin")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        val temporary = File(context.cacheDir, "resume-test-${UUID.randomUUID()}.part")
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use { it.write(source) }
            val offset = 64 * 1024 + 17
            temporary.writeBytes(source.copyOfRange(0, offset))
            val copier = ImportCopier(SafDocuments(context), PrivateStorage(context))
            val copied = copier.copy(uri, temporary, offset.toLong()) { _, _ -> }
            assertTrue(copied == source.size.toLong())
            assertArrayEquals(source, temporary.readBytes())
            assertArrayEquals(source, requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() })

            val changed = source.copyOfRange(0, offset).apply { this[offset - 1] = (this[offset - 1].toInt() xor 1).toByte() }
            temporary.writeBytes(changed)
            try {
                copier.copy(uri, temporary, offset.toLong()) { _, _ -> }
                fail("已变化的暂存前缀不应续传")
            } catch (_: IOException) {
                assertArrayEquals(changed, temporary.readBytes())
            }
            assertArrayEquals(source, requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() })
        } finally {
            temporary.delete()
            resolver.delete(uri, null, null)
        }
    }
}
