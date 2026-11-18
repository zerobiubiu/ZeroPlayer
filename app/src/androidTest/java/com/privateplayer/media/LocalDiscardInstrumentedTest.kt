package com.privateplayer.media

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.data.importer.ImportCommitJournal
import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.data.repository.ImportRepositoryImpl
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDiscardInstrumentedTest {
    private lateinit var root: File
    private lateinit var storage: PrivateStorage
    private lateinit var database: PrivatePlayerDatabase
    private lateinit var tree: FileTreeRepositoryImpl
    private lateinit var imports: ImportRepositoryImpl
    private lateinit var commits: ImportCommitJournal
    private val tasks get() = database.importTaskDao()
    private val payload = "import contents\n".toByteArray()
    private val scheduler = object : ImportWorkScheduler {
        private val scheduled = mutableSetOf<String>()
        override fun schedule(taskId: String) { scheduled.add(taskId) }
        override fun cancel(taskId: String) { scheduled.remove(taskId) }
        override suspend fun isActive(taskId: String): Boolean = taskId in scheduled
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = Files.createTempDirectory(context.cacheDir.toPath(), "local-discard-").toFile()
        val files = File(root, "files").apply { check(mkdir()) }
        val cache = File(root, "cache").apply { check(mkdir()) }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
        storage = PrivateStorage(isolated)
        check(storage.ensureLayout())
        database = Room.inMemoryDatabaseBuilder(context, PrivatePlayerDatabase::class.java).build()
        tree = FileTreeRepositoryImpl(
            database, database.mediaDao(), database.playbackHistoryDao(), database.playlistDao(),
            storage, MediaProbe(), Dispatchers.IO,
        )
        imports = ImportRepositoryImpl(
            taskDao = tasks, mediaDao = database.mediaDao(), storage = storage, fileTree = tree,
            safDocuments = SafDocuments(isolated), scheduler = scheduler, ioDispatcher = Dispatchers.IO,
        )
        commits = ImportCommitJournal(storage)
    }

    @After
    fun tearDown() {
        try {
            if (::database.isInitialized) database.close()
        } finally {
            if (::root.isInitialized) check(root.deleteRecursively())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun cancelKeepsPartialCopyForRetryAndDiscardLeavesExternalSourceUntouched() = runBlocking {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "local-discard-${UUID.randomUUID()}.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { it.write(payload) }
            val result = imports.enqueue(listOf(uri.toString()))
            assertTrue(result.rejected.toString(), result.rejected.isEmpty())
            val row = requireNotNull(tasks.findById(result.acceptedTaskIds.single()))
            assertEquals(uri.toString(), row.sourceUri)
            assertEquals(1, tasks.claimForImport(row.id, 1L))
            val prefix = payload.copyOf(payload.size / 2)
            val part = writePart(row, prefix)
            val permanent = storage.resolveMediaFile("keep.dat").apply { writeBytes(payload) }
            val identity = storage.fileIdentity(permanent)

            imports.cancel(row.id)
            val stopped = requireNotNull(tasks.findById(row.id))
            assertEquals("FAILED", stopped.state)
            assertEquals(prefix.size.toLong(), stopped.bytesCopied)
            assertFalse(scheduler.isActive(row.id))
            expectFailure<IOException> { imports.removeTask(row.id) }
            assertEquals(stopped, tasks.findById(row.id))
            assertArrayEquals(prefix, part.readBytes())

            imports.retry(row.id)
            assertEquals("QUEUED", tasks.findState(row.id))
            assertTrue(scheduler.isActive(row.id))
            assertArrayEquals(prefix, part.readBytes())
            imports.cancel(row.id)
            imports.discardLocalTask(row.id)

            assertCleared(row)
            assertEquals(identity, storage.fileIdentity(permanent))
            assertArrayEquals(payload, permanent.readBytes())
            assertArrayEquals(payload, requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() })
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun discardRollsBackOwnedRenameAndClearsOnlyItsCheckpoint() = runBlocking {
        val row = task("interrupted.bin", "COMMITTING")
        tasks.insert(row)
        val prepared = prepare(row)
        val target = publish(prepared)
        assertEquals(prepared.identity, storage.fileIdentity(target))
        assertFalse(part(row).exists())
        assertNull(database.mediaDao().findByRelativePath(storage.mediaRelativePath(row.targetFileName)))
        // 停在取消状态已落库、尚未回退 rename 的窗口，不调用会立即补做回退的 cancel。
        assertEquals(1, tasks.cancelUnlessFinished(row.id, 2L))

        val other = task("neighbor.bin")
        tasks.insert(other)
        val otherPrepared = prepare(other)
        imports.discardLocalTask(row.id)

        assertCleared(row)
        assertFalse(target.exists())
        assertEquals(other, tasks.findById(other.id))
        assertEquals(otherPrepared, journal(other))
        assertEquals(otherPrepared.identity, storage.fileIdentity(part(other)))
        assertArrayEquals(payload, part(other).readBytes())
        assertEquals(payload.size.toString(), ready(other).readText())
    }

    @Test
    fun discardRejectsForeignTargetEvenWithIdenticalContents() = runBlocking {
        val row = task("contested.bin")
        tasks.insert(row)
        val prepared = prepare(row)
        val foreign = storage.resolveMediaFile(row.targetFileName).apply { writeBytes(payload) }
        val foreignIdentity = storage.fileIdentity(foreign)
        assertNotEquals(prepared.identity, foreignIdentity)

        expectFailure<IOException> { imports.discardLocalTask(row.id) }

        assertEquals(row, tasks.findById(row.id))
        assertEquals(prepared, journal(row))
        assertEquals(prepared.identity, storage.fileIdentity(part(row)))
        assertArrayEquals(payload, part(row).readBytes())
        assertEquals(payload.size.toString(), ready(row).readText())
        assertEquals(foreignIdentity, storage.fileIdentity(foreign))
        assertArrayEquals(payload, foreign.readBytes())
    }

    @Test
    fun discardRejectsIndexedTargetEvenWhenItsIdentityMatches() = runBlocking {
        val row = task("indexed.mp4")
        val prepared = prepare(row)
        val target = publish(prepared)
        // 先建立真实媒体索引，再放入待恢复任务；ensureMedia 不应绕过未完成导入保护。
        val mediaId = tree.ensureMedia(row.targetFileName)
        val indexed = requireNotNull(database.mediaDao().findById(mediaId))
        tasks.insert(row)

        expectFailure<IOException> { imports.discardLocalTask(row.id) }

        assertEquals(indexed, database.mediaDao().findById(mediaId))
        assertEquals(row, tasks.findById(row.id))
        assertEquals(prepared, journal(row))
        assertFalse(part(row).exists())
        assertEquals(payload.size.toString(), ready(row).readText())
        assertEquals(prepared.identity, storage.fileIdentity(target))
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun activeTasksAndRetainedWebUploadsCannotBeDiscardedOrRemoved() = runBlocking {
        for (state in listOf("QUEUED", "RUNNING", "COMMITTING")) {
            val row = task("$state.bin", state)
            tasks.insert(row)
            scheduler.schedule(row.id)
            val part = writePart(row, payload)
            expectFailure<IllegalArgumentException> { imports.discardLocalTask(row.id) }
            expectFailure<IllegalArgumentException> { imports.removeTask(row.id) }
            assertTrue(scheduler.isActive(row.id))
            assertEquals(row, tasks.findById(row.id))
            assertArrayEquals(payload, part.readBytes())
        }

        val web = task("received.bin", web = true)
        tasks.insert(web)
        val received = writePart(web, payload)
        expectFailure<IllegalArgumentException> { imports.discardLocalTask(web.id) }
        expectFailure<IOException> { imports.removeTask(web.id) }
        assertEquals(web, tasks.findById(web.id))
        assertArrayEquals(payload, received.readBytes())
    }

    @Test
    fun completedImportAllowsRecordRemovalButNotDiscard() = runBlocking {
        val row = task("saved.bin", "COMPLETED")
        tasks.insert(row)
        val prepared = prepare(row)
        val target = publish(prepared)

        expectFailure<IllegalArgumentException> { imports.discardLocalTask(row.id) }
        assertEquals(row, tasks.findById(row.id))
        assertEquals(prepared, journal(row))
        imports.removeTask(row.id)

        assertCleared(row)
        assertEquals(prepared.identity, storage.fileIdentity(target))
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun markerTypeConflictKeepsRecordAndDiscardCanBeRetried() = runBlocking {
        val row = task("retry-cleanup.bin")
        tasks.insert(row)
        prepare(row)
        val marker = ready(row)
        check(marker.delete() && marker.mkdir())
        val child = File(marker, "keep.txt").apply { writeText("不是完成标记") }

        expectFailure<IOException> { imports.discardLocalTask(row.id) }
        assertEquals(row, tasks.findById(row.id))
        assertEquals("不是完成标记", child.readText())
        check(child.delete() && marker.delete())
        imports.discardLocalTask(row.id)

        assertCleared(row)
        assertFalse(storage.resolveMediaFile(row.targetFileName).exists())
    }

    @Test
    fun legacyStagingStillClaimedByAnotherTaskCannotBeDiscarded() = runBlocking {
        val path = "temporary/shared.part"
        val first = task("first.bin").copy(tempRelativePath = path)
        val second = task("second.bin").copy(tempRelativePath = path)
        tasks.insert(first)
        tasks.insert(second)
        val shared = storage.absoluteCacheFile(path).apply { writeBytes(payload) }
        expectFailure<IOException> { imports.discardLocalTask(first.id) }
        assertArrayEquals(payload, shared.readBytes())
        assertEquals(first, tasks.findById(first.id))
        assertEquals(second, tasks.findById(second.id))
    }

    private fun task(target: String, state: String = "FAILED", web: Boolean = false): ImportTaskEntity {
        val id = UUID.randomUUID().toString()
        val directory = if (web) "web-uploads" else "import-staging"
        return ImportTaskEntity(
            id = id, sourceUri = if (web) "web-upload:$id" else "content://test/$id",
            displayName = target, mimeType = null, sizeBytes = payload.size.toLong(), targetFileName = target,
            folderId = null, tempRelativePath = "$directory/$id.part", state = state,
            bytesCopied = payload.size.toLong(), errorMessage = null, mediaId = null, createdAt = 1L, updatedAt = 1L,
        )
    }

    private fun part(row: ImportTaskEntity): File = storage.absoluteFile(row.tempRelativePath)
    private fun ready(row: ImportTaskEntity): File = storage.absoluteFile("${row.tempRelativePath}.ready")

    private fun writePart(row: ImportTaskEntity, bytes: ByteArray): File = part(row).apply {
        val directory = requireNotNull(parentFile)
        check(directory.isDirectory || directory.mkdirs())
        writeBytes(bytes)
    }

    private suspend fun prepare(row: ImportTaskEntity): ImportCommitJournal.Prepared {
        val part = writePart(row, payload)
        ready(row).writeText(part.length().toString())
        return storage.withMediaTreeLock { commits.prepare(row, part) }
    }

    private suspend fun publish(prepared: ImportCommitJournal.Prepared): File =
        storage.withMediaTreeLock { commits.commit(prepared) }

    private suspend fun journal(row: ImportTaskEntity): ImportCommitJournal.Prepared? =
        storage.withMediaTreeLock { commits.read(row) }

    private suspend fun assertCleared(row: ImportTaskEntity) {
        assertNull(tasks.findById(row.id))
        assertNull(journal(row))
        assertFalse(part(row).exists())
        assertFalse(ready(row).exists())
    }

    private inline fun <reified E : Exception> expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            if (error !is E) throw error
            return
        }
        fail("应抛出 ${E::class.java.simpleName}")
    }
}
