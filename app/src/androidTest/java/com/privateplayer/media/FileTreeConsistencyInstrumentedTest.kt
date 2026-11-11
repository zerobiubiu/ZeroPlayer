package com.privateplayer.media

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.database.dao.MediaDao
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.database.entity.MediaEntity
import com.privateplayer.core.database.entity.PlaybackHistoryEntity
import com.privateplayer.core.database.entity.PlaylistEntity
import com.privateplayer.core.database.entity.PlaylistItemEntity
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.data.importer.ImportCommitJournal
import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.data.repository.ImportRepositoryImpl
import com.privateplayer.data.repository.StorageRepositoryImpl
import com.privateplayer.domain.model.OperationOutcome
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileTreeConsistencyInstrumentedTest {
    private lateinit var testRoot: File
    private lateinit var storage: PrivateStorage
    private lateinit var database: PrivatePlayerDatabase
    private lateinit var repository: FileTreeRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testRoot = File(context.cacheDir, "file-tree-test-${UUID.randomUUID()}")
        check(testRoot.mkdir())
        val files = File(testRoot, "files").apply { check(mkdir()) }
        val cache = File(testRoot, "cache").apply { check(mkdir()) }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
        storage = PrivateStorage(isolated)
        check(storage.ensureLayout())
        database = Room.inMemoryDatabaseBuilder(context, PrivatePlayerDatabase::class.java).build()
        repository = newRepository(database.mediaDao())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testRoot.isInitialized) testRoot.deleteRecursively()
    }

    @Test
    fun `真实树与旧视频显示名及字幕兼容`() = runBlocking {
        val legacy = "01234567-89ab-cdef-0123-456789abcdef.mp4"
        val video = mediaFile(legacy, "旧视频")
        mediaFile("原片名.zh-CN.srt", "字幕")
        mediaFile("其他.txt", "普通文件")
        check(File(storage.mediaRoot, "目录").mkdir())
        val id = insertMedia(legacy, "原片名", duration = 9_000L, position = 3_000L)

        val children = repository.list("")
        assertTrue(children.first().isDirectory)
        val entry = children.single { it.mediaId == id }
        assertEquals(legacy, entry.path)
        assertEquals("原片名.mp4", entry.name)
        assertEquals(9_000L, entry.durationMs)
        assertEquals(3_000L, entry.playbackPositionMs)
        assertEquals(storage.fileIdentity(video), storage.fileIdentity(repository.openForRead(legacy)))
        assertEquals("旧视频", repository.openForRead(legacy).readText())
        val subtitle = File(storage.mediaRoot, "原片名.zh-CN.srt")
        val matched = repository.matchingSubtitles(legacy).single()
        assertEquals(storage.fileIdentity(subtitle), storage.fileIdentity(File(matched)))
        assertEquals("字幕", File(matched).readText())
        assertFalse(children.single { it.path == "其他.txt" }.isVideo)
    }

    @Test
    fun `无索引视频原位建立记录且字幕不能建立视频记录`() = runBlocking {
        val video = mediaFile("未索引.mkv", "无法被平台解析但仍保留的原文件")
        val subtitle = mediaFile("未索引.srt", "字幕")
        val originalLength = video.length()
        val id = repository.ensureMedia(video.name)
        assertEquals(id, repository.ensureMedia(video.name))
        assertEquals("media/未索引.mkv", database.mediaDao().findById(id)?.relativePath)
        assertEquals(originalLength, video.length())
        assertTrue(video.isFile)
        assertFalse(repository.find(subtitle.name)!!.isVideo)
        try {
            repository.ensureMedia(subtitle.name)
            fail("字幕不应作为视频入库")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `目录和文件更名移动删除保持媒体及关联数据一致`() = runBlocking {
        val folder = File(storage.mediaRoot, "旧目录").apply { check(mkdir()) }
        val nested = File(folder, "子目录").apply { check(mkdir()) }
        File(folder, "电影.mp4").writeText("视频")
        File(nested, "资料.txt").writeText("资料")
        val id = insertMedia("旧目录/电影.mp4", "电影", duration = 9_000L, position = 3_000L)
        database.playbackHistoryDao().insert(PlaybackHistoryEntity(mediaId = id, playedAt = 1L, positionMs = 3_000L, durationMs = 9_000L))
        val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(name = "旧数据", description = null, createdAt = 1L, updatedAt = 1L))
        database.playlistDao().insertItem(PlaylistItemEntity(playlistId, id, 0, 1L))

        repository.rename("旧目录", "新目录")
        assertFalse(folder.exists())
        assertEquals("media/新目录/电影.mp4", database.mediaDao().findById(id)?.relativePath)
        assertEquals("资料", File(storage.mediaRoot, "新目录/子目录/资料.txt").readText())
        repository.rename("新目录/电影.mp4", "新片名.mkv")
        repository.move("新目录/新片名.mkv", "")
        val moved = database.mediaDao().findById(id)!!
        assertEquals("media/新片名.mkv", moved.relativePath)
        assertEquals("新片名.mkv", moved.fileName)
        assertEquals("新片名", moved.displayName)
        assertEquals(3_000L, moved.playbackPositionMs)
        assertTrue(repository.openForRead("新片名.mkv").isFile)
        repository.delete("新目录")
        assertEquals(1, database.playbackHistoryDao().findForMedia(id).size)
        repository.delete("新片名.mkv")
        assertNull(database.mediaDao().findById(id))
        assertTrue(database.playbackHistoryDao().findForMedia(id).isEmpty())
        assertTrue(database.playlistDao().observeItems(playlistId).first().isEmpty())
    }

    @Test
    fun `覆盖及逃逸被拒绝且原文件不变`() = runBlocking {
        val first = mediaFile("源.mp4", "源")
        val existing = mediaFile("目标.mp4", "目标")
        try {
            repository.rename(first.name, existing.name)
            fail("不应覆盖同名目标")
        } catch (_: IOException) {
        }
        assertEquals("源", first.readText())
        assertEquals("目标", existing.readText())
        for (path in listOf("../外部", "/etc/passwd", "a/../../外部")) {
            try {
                repository.openForRead(path)
                fail("不应允许越界路径")
            } catch (_: IllegalArgumentException) {
            }
        }
        for (name in listOf("", " ", ".", "..", "嵌套/名称", "反斜杠\\名称")) {
            try {
                repository.createDirectory("", name)
                fail("不应接受非法名称")
            } catch (_: IllegalArgumentException) {
            }
        }
        val outside = File(testRoot, "树外文件").apply { writeText("树外") }
        Files.createSymbolicLink(File(storage.mediaRoot, "链接").toPath(), outside.toPath())
        try {
            repository.openForRead("链接")
            fail("不应允许访问符号链接")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals("树外", outside.readText())
        val outsideDirectory = File(testRoot, "树外目录").apply { check(mkdir()) }
        val protectedFile = File(outsideDirectory, "保留.txt").apply { writeText("保留") }
        val withLink = File(storage.mediaRoot, "含链接").apply { check(mkdir()) }
        Files.createSymbolicLink(File(withLink, "外部").toPath(), outsideDirectory.toPath())
        repository.delete("含链接")
        assertFalse(withLink.exists())
        assertEquals("保留", protectedFile.readText())
    }

    @Test
    fun `暂存提交只进入现有目录且原子拒绝覆盖`() = runBlocking {
        val staged = stagedFile(UUID.randomUUID().toString(), "新内容")
        val folder = File(storage.mediaRoot, "目标目录").apply { check(mkdir()) }
        val existing = File(folder, "已有.mp4").apply { writeText("原内容") }
        try {
            storage.withMediaTreeLock { storage.commitTempFileLocked(staged, "media/目标目录/已有.mp4") }
            fail("不应覆盖已有文件")
        } catch (_: IOException) {
        }
        assertEquals("原内容", existing.readText())
        assertEquals("新内容", staged.readText())
        try {
            storage.withMediaTreeLock { storage.commitTempFileLocked(staged, "media/不存在的目录/新文件.mp4") }
            fail("不应创建已不存在的目录")
        } catch (_: IOException) {
        }
        assertFalse(File(storage.mediaRoot, "不存在的目录").exists())
        val identity = storage.fileIdentity(staged)
        val committed = storage.withMediaTreeLock { storage.commitTempFileLocked(staged, "media/目标目录/新文件.mp4") }
        assertFalse(staged.exists())
        assertEquals(identity, storage.fileIdentity(committed))
        assertEquals("新内容", committed.readText())
    }

    @Test
    fun `数据库失败时移动和删除均恢复文件及关系`() = runBlocking {
        val folder = File(storage.mediaRoot, "待处理").apply { check(mkdir()) }
        File(folder, "电影.mp4").writeText("视频")
        val id = insertMedia("待处理/电影.mp4", "电影", position = 10L)
        database.playbackHistoryDao().insert(PlaybackHistoryEntity(mediaId = id, playedAt = 1L, positionMs = 10L, durationMs = 100L))
        val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(name = "旧数据", description = null, createdAt = 1L, updatedAt = 1L))
        database.playlistDao().insertItem(PlaylistItemEntity(playlistId, id, 0, 1L))

        try {
            newRepository(failingDao("applyRename")).rename("待处理", "失败目标")
            fail("应传播数据库故障")
        } catch (_: IllegalStateException) {
        }
        assertTrue(File(folder, "电影.mp4").isFile)
        assertFalse(File(storage.mediaRoot, "失败目标").exists())
        assertEquals("media/待处理/电影.mp4", database.mediaDao().findById(id)?.relativePath)

        try {
            newRepository(failingDao("deleteById")).delete("待处理")
            fail("应传播数据库故障")
        } catch (_: IllegalStateException) {
        }
        assertTrue(File(folder, "电影.mp4").isFile)
        assertNotNull(database.mediaDao().findById(id))
        assertEquals(1, database.playbackHistoryDao().findForMedia(id).size)
        assertEquals(1, database.playlistDao().observeItems(playlistId).first().size)
    }

    @Test
    fun `未完成导入的已移动文件不可在恢复前移动或删除`() = runBlocking {
        val folder = File(storage.mediaRoot, "导入中").apply { check(mkdir()) }
        val taskId = UUID.randomUUID().toString()
        val staging = stagedFile(taskId, "尚未入库")
        val task = insertImport(taskId, "导入中/影片.mp4", staging.length())
        val commits = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { commits.prepare(task, staging) }
        val target = storage.withMediaTreeLock { commits.commit(prepared) }
        assertEquals(folder.canonicalFile, target.parentFile)
        try {
            repository.rename("导入中", "新目录")
            fail("不应移动尚未恢复的导入")
        } catch (_: IOException) {
        }
        try {
            repository.delete("导入中/影片.mp4")
            fail("不应删除尚未恢复的导入")
        } catch (_: IOException) {
        }
        assertTrue(target.isFile)
        assertFalse(staging.exists())
        assertEquals(prepared.identity, storage.fileIdentity(target))
        database.importTaskDao().completeWithNotice(taskId, null, null, 2L)
        storage.withMediaTreeLock { commits.finish(task, prepared) }
        repository.rename("导入中", "新目录")
        assertEquals("尚未入库", File(storage.mediaRoot, "新目录/影片.mp4").readText())
    }

    @Test
    fun `进程中断后按数据库提交结果恢复移动和删除`() = runBlocking {
        val folder = File(storage.mediaRoot, "原目录").apply { check(mkdir()) }
        File(folder, "电影.mp4").writeText("视频")
        val id = insertMedia("原目录/电影.mp4", "电影", position = 7L)
        val target = File(storage.mediaRoot, "新目录")

        var journal = writeJournal("MOVE", "原目录", "新目录", id to "media/原目录/电影.mp4")
        Files.move(folder.toPath(), target.toPath())
        repository.list("")
        assertTrue(File(folder, "电影.mp4").isFile)
        assertFalse(target.exists())
        assertFalse(journal.exists())
        assertEquals(7L, database.mediaDao().findById(id)?.playbackPositionMs)

        journal = writeJournal("MOVE", "原目录", "新目录", id to "media/原目录/电影.mp4")
        Files.move(folder.toPath(), target.toPath())
        database.mediaDao().applyRename(id, "电影", "电影.mp4", "media/新目录/电影.mp4", 2L)
        repository.list("")
        assertFalse(folder.exists())
        assertTrue(File(target, "电影.mp4").isFile)
        assertFalse(journal.exists())

        journal = writeJournal("DELETE", "新目录", "", id to "media/新目录/电影.mp4")
        Files.move(target.toPath(), File(journal, "payload").toPath())
        repository.list("")
        assertTrue(File(target, "电影.mp4").isFile)
        assertFalse(journal.exists())
        assertNotNull(database.mediaDao().findById(id))

        journal = writeJournal("DELETE", "新目录", "", id to "media/新目录/电影.mp4")
        Files.move(target.toPath(), File(journal, "payload").toPath())
        database.mediaDao().deleteById(id)
        repository.list("")
        assertFalse(target.exists())
        assertFalse(journal.exists())
        assertNull(database.mediaDao().findById(id))
    }

    @Test
    fun `删除回滚重命名后再次中断仍可恢复`() = runBlocking {
        val source = mediaFile("电影.mp4", "原始视频")
        val id = insertMedia("电影.mp4", "电影", position = 5L)
        val journal = writeJournal("DELETE", "电影.mp4", "", id to "media/电影.mp4")
        val payload = File(journal, "payload")
        Files.move(source.toPath(), payload.toPath())
        val identity = storage.fileIdentity(payload)
        Files.move(payload.toPath(), source.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        repository.list("")
        assertEquals("原始视频", source.readText())
        assertFalse(journal.exists())
        assertEquals(5L, database.mediaDao().findById(id)?.playbackPositionMs)
        assertEquals(identity, storage.fileIdentity(source))
    }

    @Test
    fun `导入提交前后中断均按持久文件身份恢复`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val staged = stagedFile(id, "完整暂存")
        val task = insertImport(id, "恢复.bin", staged.length())
        val journal = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { journal.prepare(task, staged) }

        // 模拟日志已落盘、rename 尚未发生时重建执行器。
        var recovered = ImportCommitJournal(storage)
        val beforeMove = storage.withMediaTreeLock { recovered.read(task)!! }
        assertEquals(prepared, beforeMove)
        assertEquals(prepared.identity, storage.fileIdentity(recovered.content(beforeMove)))
        assertTrue(staged.isFile)
        assertFalse(File(storage.mediaRoot, "恢复.bin").exists())

        val target = storage.withMediaTreeLock { recovered.commit(beforeMove) }
        assertFalse(staged.exists())
        // 再次重建执行器，此时原暂存路径已不存在，不能依赖两条路径同时存在。
        recovered = ImportCommitJournal(storage)
        val afterMove = storage.withMediaTreeLock { recovered.read(task)!! }
        assertEquals(prepared.identity, storage.fileIdentity(recovered.content(afterMove)))
        assertEquals("完整暂存", recovered.content(afterMove).readText())
        assertEquals(target, storage.withMediaTreeLock { recovered.commit(afterMove) })

        database.importTaskDao().completeWithNotice(id, null, null, 3L)
        // 数据库已提交、日志尚未清理时重启，只清理日志，不能再回退正式文件。
        storage.withMediaTreeLock { ImportCommitJournal(storage).finish(task, afterMove) }
        assertNull(storage.withMediaTreeLock { ImportCommitJournal(storage).read(task) })
        assertEquals("完整暂存", target.readText())
    }

    @Test
    fun `同长度同内容的既有目标也不能冒认本次提交`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val staged = stagedFile(id, "相同内容")
        val task = insertImport(id, "冲突.dat", staged.length())
        val commits = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { commits.prepare(task, staged) }
        val existing = mediaFile("冲突.dat", "相同内容")
        val existingIdentity = storage.fileIdentity(existing)
        assertFalse(existingIdentity == prepared.identity)
        try {
            storage.withMediaTreeLock { commits.commit(prepared) }
            fail("不能按文件名、长度或内容接管已有目标")
        } catch (_: IOException) {
        }
        assertEquals(existingIdentity, storage.fileIdentity(existing))
        assertEquals(prepared.identity, storage.fileIdentity(staged))
        assertNotNull(storage.withMediaTreeLock { commits.read(task) })
        repository.rename("冲突.dat", "原有内容.dat")
        val target = storage.withMediaTreeLock { commits.commit(commits.read(task)!!) }
        assertFalse(staged.exists())
        assertEquals(prepared.identity, storage.fileIdentity(target))
        assertEquals(existingIdentity, storage.fileIdentity(File(storage.mediaRoot, "原有内容.dat")))
    }

    @Test
    fun `导入数据库事务失败后原位回退且保留重试身份`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val staged = stagedFile(id, "不应丢失")
        val task = insertImport(id, "事务.mp4", staged.length())
        val commits = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { commits.prepare(task, staged) }
        storage.withMediaTreeLock {
            try {
                database.withTransaction {
                    commits.commit(prepared)
                    val mediaId = insertMedia("事务.mp4", "事务")
                    database.importTaskDao().completeWithNotice(id, mediaId, null, 3L)
                    throw IllegalStateException("模拟数据库提交失败")
                }
                fail("应传播数据库故障")
            } catch (_: IllegalStateException) {
                commits.rollback(prepared)
            }
        }
        assertEquals("COMMITTING", database.importTaskDao().findState(id))
        assertNull(database.mediaDao().findByRelativePath("media/事务.mp4"))
        assertFalse(File(storage.mediaRoot, "事务.mp4").exists())
        assertEquals(prepared.identity, storage.fileIdentity(staged))
        assertEquals("不应丢失", staged.readText())

        // 模拟重试中的新执行器，仍使用同一 inode，不再复制。
        val recovered = ImportCommitJournal(storage)
        storage.withMediaTreeLock {
            val target = recovered.commit(recovered.read(task)!!)
            assertEquals(prepared.identity, storage.fileIdentity(target))
            database.withTransaction {
                val mediaId = insertMedia("事务.mp4", "事务")
                database.importTaskDao().completeWithNotice(id, mediaId, null, 4L)
            }
            recovered.finish(task, prepared)
        }
        assertEquals("COMPLETED", database.importTaskDao().findState(id))
    }

    @Test
    fun `取消已移动导入会恢复暂存并支持重试而不删除任务`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val staged = stagedFile(id, "取消后保留")
        val task = insertImport(id, "取消.bin", staged.length())
        val commits = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { commits.prepare(task, staged) }
        storage.withMediaTreeLock { commits.commit(prepared) }
        val scheduled = mutableListOf<String>()
        val canceled = mutableListOf<String>()
        val scheduler = object : ImportWorkScheduler {
            override fun schedule(taskId: String) { scheduled += taskId }
            override fun cancel(taskId: String) { canceled += taskId }
            override suspend fun isActive(taskId: String): Boolean = false
        }
        val imports = ImportRepositoryImpl(
            database.importTaskDao(), storage, repository,
            SafDocuments(ApplicationProvider.getApplicationContext()), scheduler, Dispatchers.IO,
        )
        imports.cancel(id)
        assertEquals(listOf(id), canceled)
        assertEquals("FAILED", database.importTaskDao().findState(id))
        assertEquals(prepared.identity, storage.fileIdentity(staged))
        assertEquals("取消后保留", staged.readText())
        assertFalse(File(storage.mediaRoot, "取消.bin").exists())
        imports.removeTask(id)
        assertNotNull(database.importTaskDao().findById(id))
        assertTrue(staged.isFile)
        imports.retry(id)
        assertEquals("QUEUED", database.importTaskDao().findState(id))
        assertEquals(listOf(id), scheduled)
        assertEquals(prepared.identity, storage.fileIdentity(staged))
        assertEquals(1, database.importTaskDao().claimForImport(id, 5L))
        assertEquals(1, database.importTaskDao().beginCommit(id, 6L))
        storage.withMediaTreeLock {
            val target = commits.commit(commits.read(task)!!)
            assertEquals(prepared.identity, storage.fileIdentity(target))
            database.importTaskDao().completeWithNotice(id, null, null, 7L)
            commits.finish(task, prepared)
        }
        assertEquals("COMPLETED", database.importTaskDao().findState(id))
        assertFalse(staged.exists())
    }

    @Test
    fun `目标目录被移动或删除时保留暂存而不重新创建目录`() = runBlocking {
        repository.createDirectory("", "接收")
        val id = UUID.randomUUID().toString()
        val staged = stagedFile(id, "保留资料")
        val task = insertImport(id, "接收/资料.zip", staged.length())
        val commits = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { commits.prepare(task, staged) }
        repository.rename("接收", "已移动")
        try {
            storage.withMediaTreeLock { commits.commit(prepared) }
            fail("不能把已移动的目标目录重新创建")
        } catch (_: IOException) {
        }
        assertFalse(File(storage.mediaRoot, "接收").exists())
        assertEquals(prepared.identity, storage.fileIdentity(staged))
        repository.createDirectory("", "接收")
        repository.delete("接收")
        try {
            storage.withMediaTreeLock { commits.commit(prepared) }
            fail("不能把已删除的目标目录重新创建")
        } catch (_: IOException) {
        }
        assertTrue(staged.isFile)
        repository.createDirectory("", "接收")
        val target = storage.withMediaTreeLock { commits.commit(commits.read(task)!!) }
        assertEquals("保留资料", target.readText())
        assertTrue(File(storage.mediaRoot, "已移动").isDirectory)
    }

    @Test
    fun `旧缓存暂存移动后日志仍指向原位置且回退不会覆盖新文件`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val staged = File(storage.temporaryRoot, "旧任务.part").apply { writeText("旧版本内容") }
        val task = insertImport(id, "旧任务.bin", staged.length(), "temporary/旧任务.part")
        val commits = ImportCommitJournal(storage)
        val prepared = storage.withMediaTreeLock { commits.prepare(task, staged) }
        val target = storage.withMediaTreeLock { commits.commit(prepared) }
        assertFalse(staged.exists())
        val recovered = storage.withMediaTreeLock { ImportCommitJournal(storage).read(task)!! }
        assertEquals(staged.canonicalPath, recovered.sourcePath)
        assertEquals(prepared.identity, storage.fileIdentity(commits.content(recovered)))
        staged.writeText("后来出现的暂存")
        val otherIdentity = storage.fileIdentity(staged)
        try {
            storage.withMediaTreeLock { commits.rollback(recovered) }
            fail("回退不能覆盖原位置的新文件")
        } catch (_: IOException) {
        }
        assertEquals("后来出现的暂存", staged.readText())
        assertEquals(otherIdentity, storage.fileIdentity(staged))
        assertEquals("旧版本内容", target.readText())
        assertNotNull(storage.withMediaTreeLock { commits.read(task) })
        database.importTaskDao().completeWithNotice(id, null, null, 8L)
        storage.withMediaTreeLock { commits.finish(task, recovered) }
        assertEquals("后来出现的暂存", staged.readText())
        assertEquals("旧版本内容", target.readText())
    }

    @Test
    fun `零字节任意格式和数GB文件提交均只做重命名`() = runBlocking {
        for (name in listOf("空文件", "空资料.zip", "空字幕.srt")) {
            val id = UUID.randomUUID().toString()
            val staged = stagedFile(id, "")
            val task = insertImport(id, name, 0L)
            val commits = ImportCommitJournal(storage)
            storage.withMediaTreeLock {
                val prepared = commits.prepare(task, staged)
                val target = commits.commit(prepared)
                assertEquals(0L, target.length())
                assertEquals(prepared.identity, storage.fileIdentity(target))
                assertFalse(staged.exists())
                database.importTaskDao().completeWithNotice(id, null, null, 9L)
                commits.finish(task, prepared)
            }
        }
        val id = UUID.randomUUID().toString()
        val staged = stagedFile(id, "")
        val size = 3L * 1024L * 1024L * 1024L + 7L
        RandomAccessFile(staged, "rw").use {
            it.setLength(size)
            it.write(0x41)
            it.seek(size - 1L)
            it.write(0x5A)
            it.fd.sync()
        }
        val task = insertImport(id, "大文件.unknown", size)
        val commits = ImportCommitJournal(storage)
        storage.withMediaTreeLock {
            val prepared = commits.prepare(task, staged)
            val target = commits.commit(prepared)
            assertEquals(size, target.length())
            assertEquals(prepared.identity, storage.fileIdentity(target))
            assertFalse(staged.exists())
            RandomAccessFile(target, "r").use {
                assertEquals(0x41, it.read())
                it.seek(size - 1L)
                assertEquals(0x5A, it.read())
            }
            database.importTaskDao().completeWithNotice(id, null, null, 10L)
            commits.finish(task, prepared)
        }
    }

    @Test
    fun `提交拒绝把正式文件当暂存且拒绝不属于任务的来源`() = runBlocking {
        val original = mediaFile("原正式文件.bin", "正式内容")
        val originalIdentity = storage.fileIdentity(original)
        try {
            storage.withMediaTreeLock { storage.commitTempFileLocked(original, "media/新文件.bin") }
            fail("不能移动原有正式文件")
        } catch (_: IllegalArgumentException) {
        }
        val id = UUID.randomUUID().toString()
        val other = stagedFile(UUID.randomUUID().toString(), "其他任务")
        val task = insertImport(id, "新文件.bin", other.length())
        try {
            storage.withMediaTreeLock { ImportCommitJournal(storage).prepare(task, other) }
            fail("不能提交其他任务的暂存")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(originalIdentity, storage.fileIdentity(original))
        assertEquals("正式内容", original.readText())
        assertEquals("其他任务", other.readText())
        assertFalse(File(storage.mediaRoot, "新文件.bin").exists())
    }

    private fun stagedFile(id: String, content: String): File =
        storage.absoluteFile("web-uploads/$id.part").apply {
            check(parentFile!!.isDirectory || parentFile!!.mkdirs())
            writeText(content)
        }

    private suspend fun insertImport(
        id: String,
        target: String,
        size: Long,
        legacyPath: String? = null,
    ): ImportTaskEntity = ImportTaskEntity(
        id = id,
        sourceUri = if (legacyPath == null) "web-upload:$id" else "content://test/$id",
        displayName = target.substringAfterLast('/'),
        mimeType = null,
        sizeBytes = size,
        targetFileName = target,
        folderId = null,
        tempRelativePath = legacyPath ?: "web-uploads/$id.part",
        state = "COMMITTING",
        bytesCopied = size,
        errorMessage = null,
        mediaId = null,
        createdAt = 1L,
        updatedAt = 1L,
    ).also { database.importTaskDao().insert(it) }

    @Test
    fun `存储统计包括任意文件和字幕且不跟随符号链接`() = runBlocking {
        val video = mediaFile("影片.mp4", "视频")
        val subtitle = mediaFile("片名/影片.srt", "字幕")
        val other = mediaFile("片名/资料.txt", "普通文件")
        val outside = File(testRoot, "树外占用").apply { writeBytes(ByteArray(2048)) }
        Files.createSymbolicLink(File(storage.mediaRoot, "树外链接").toPath(), outside.toPath())
        val store = storageRepository()
        assertEquals(video.length() + subtitle.length() + other.length(), store.observeUsage().first().mediaBytes)
        val added = mediaFile("新增.vtt", "新增字幕")
        repository.notifyChanged()
        assertEquals(video.length() + subtitle.length() + other.length() + added.length(), store.observeUsage().first().mediaBytes)
    }

    @Test
    fun `清理旧缓存保留所有未完成任务和其父目录`() = runBlocking {
        val protectedFiles = listOf("QUEUED", "RUNNING", "COMMITTING", "FAILED").map { state ->
            File(storage.temporaryRoot, "$state.part").apply { writeText(state) }.also {
                insertLegacyImport(state, "temporary/${it.name}")
            }
        }
        val nested = File(storage.temporaryRoot, "子目录").apply { check(mkdir()) }
        val protectedNested = File(nested, "处理中.part").apply { writeText("保留") }
        insertLegacyImport("RUNNING", "temporary/子目录/处理中.part")
        val ready = File(nested, "${protectedNested.name}.ready").apply { writeText(protectedNested.length().toString()) }
        val nestedOrphan = File(nested, "无任务.part").apply { writeText("清理") }
        val completed = File(storage.temporaryRoot, "完成.part").apply { writeText("完成") }
        val canceled = File(storage.temporaryRoot, "取消.part").apply { writeText("取消") }
        insertLegacyImport("COMPLETED", "temporary/完成.part")
        insertLegacyImport("CANCELED", "temporary/取消.part")
        val orphan = File(storage.temporaryRoot, "孤立.part").apply { writeText("孤立") }
        val staging = storage.absoluteFile("import-staging/持久.part").apply {
            check(parentFile!!.mkdirs())
            writeText("持久暂存")
        }
        val outside = File(testRoot, "清理树外").apply { check(mkdir()) }
        val protectedOutside = File(outside, "保留.txt").apply { writeText("树外内容") }
        val link = File(storage.temporaryRoot, "树外链接")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertEquals(OperationOutcome.Success, storageRepository().clearTemporaryFiles())
        assertTrue(protectedFiles.all { it.isFile })
        assertTrue(protectedNested.isFile)
        assertTrue(ready.isFile)
        assertFalse(nestedOrphan.exists())
        assertFalse(completed.exists())
        assertFalse(canceled.exists())
        assertFalse(orphan.exists())
        assertTrue(staging.isFile)
        assertEquals("树外内容", protectedOutside.readText())
        assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun `数据库进度和外部通知均刷新当前目录`() = runBlocking {
        mediaFile("电影.mp4", "视频")
        val id = insertMedia("电影.mp4", "电影", duration = 1000L, position = 10L)
        val version = repository.observeChanges().first()
        val emissions = Channel<List<com.privateplayer.domain.repository.FileTreeEntry>>(Channel.UNLIMITED)
        val observer = launch { repository.observeDirectory("").collect { emissions.send(it) } }
        try {
            withTimeout(10_000) { emissions.receive() }
            database.mediaDao().recordProgress(id, 222L, 0L, 0, 5L)
            withTimeout(10_000) {
                while (emissions.receive().singleOrNull { it.path == "电影.mp4" }?.playbackPositionMs != 222L) Unit
            }
            assertEquals(version, repository.observeChanges().first())
            mediaFile("资料.txt", "文本")
            repository.notifyChanged()
            assertEquals(version + 1L, repository.observeChanges().first())
            withTimeout(10_000) {
                while (emissions.receive().none { it.path == "资料.txt" }) Unit
            }
        } finally {
            observer.cancelAndJoin()
            emissions.close()
        }
    }

    private fun newRepository(dao: MediaDao) = FileTreeRepositoryImpl(
        database = database,
        mediaDao = dao,
        playbackHistoryDao = database.playbackHistoryDao(),
        playlistDao = database.playlistDao(),
        storage = storage,
        probe = MediaProbe(),
        ioDispatcher = Dispatchers.IO,
    )

    private fun failingDao(methodName: String): MediaDao {
        val real = database.mediaDao()
        return Proxy.newProxyInstance(MediaDao::class.java.classLoader, arrayOf(MediaDao::class.java)) { _, method, args ->
            if (method.name == methodName) throw IllegalStateException("模拟数据库故障")
            try {
                method.invoke(real, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as MediaDao
    }

    private fun mediaFile(path: String, content: String): File = File(storage.mediaRoot, path).apply {
        parentFile!!.mkdirs()
        writeText(content)
    }

    private suspend fun insertMedia(path: String, display: String, duration: Long = 100L, position: Long = 0L): Long {
        val file = File(storage.mediaRoot, path)
        return database.mediaDao().insert(
            MediaEntity(
                displayName = display,
                fileName = file.name,
                relativePath = "media/$path",
                folderId = null,
                mimeType = "video/mp4",
                sizeBytes = file.length(),
                durationMs = duration,
                width = 0,
                height = 0,
                videoCodec = null,
                audioCodec = null,
                bitrate = null,
                audioTrackCount = 0,
                subtitleTrackCount = 0,
                fingerprint = "测试-${UUID.randomUUID()}",
                thumbnailPath = null,
                isFavorite = false,
                addedAt = 1L,
                updatedAt = 1L,
                lastPlayedAt = null,
                playbackPositionMs = position,
                playedDurationMs = 0L,
                playCount = 0,
            ),
        )
    }

    private fun storageRepository() = StorageRepositoryImpl(
        database.mediaDao(), database.importTaskDao(), repository, storage, Dispatchers.IO,
    )

    private suspend fun insertLegacyImport(state: String, tempPath: String) {
        val id = UUID.randomUUID().toString()
        database.importTaskDao().insert(
            ImportTaskEntity(
                id = id,
                sourceUri = "content://test/$id",
                displayName = "视频.mp4",
                mimeType = "video/mp4",
                sizeBytes = 1L,
                targetFileName = "$id.mp4",
                folderId = null,
                tempRelativePath = tempPath,
                state = state,
                bytesCopied = 1L,
                errorMessage = null,
                mediaId = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    /** 模拟操作日志已落盘后进程在不同事务边界中断。 */
    private fun writeJournal(operation: String, source: String, destination: String, vararg records: Pair<Long, String>): File {
        val root = File(storage.backupsRoot, ".file-tree-operations")
        check(root.isDirectory || root.mkdirs())
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir())
        FileOutputStream(File(directory, "manifest")).use { stream ->
            DataOutputStream(stream).use { output ->
                output.writeInt(1)
                output.writeUTF(operation)
                output.writeUTF(source)
                output.writeUTF(destination)
                output.writeInt(records.size)
                records.forEach { (id, path) -> output.writeLong(id); output.writeUTF(path) }
                output.flush()
                stream.fd.sync()
            }
        }
        return directory
    }
}
