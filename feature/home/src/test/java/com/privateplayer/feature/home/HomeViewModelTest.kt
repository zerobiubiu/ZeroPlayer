package com.privateplayer.feature.home

import com.privateplayer.core.design.components.canConfirmName
import com.privateplayer.domain.repository.FileTreeEntry
import java.io.File
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.usecase.NoOpPlaybackStopper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `旧文件可以保存显示名作为真实名称`() {
        assertTrue(canConfirmName("旅行.mp4", "旧文件标识.mp4"))
        assertFalse(canConfirmName("旧文件标识.mp4", "旧文件标识.mp4"))
        assertTrue(canConfirmName("旅行.mp4", null))
        assertFalse(canConfirmName("..", null))
    }

    @Test
    fun `目录按类型排序且可以进入多层并返回上级`() = runTest(dispatcher) {
        val repository = FakeFileTreeRepository().apply {
            directories[""] = listOf(file("z.txt"), directory("片段"), file("A.mp4", video = true), directory("Archives"))
            directories["片段"] = listOf(directory("片段/2026"))
            directories["片段/2026"] = listOf(file("片段/2026/电影.mp4", video = true))
        }
        val model = HomeViewModel(repository, NoOpPlaybackStopper, dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        runCurrent()

        assertEquals(listOf("Archives", "片段", "A.mp4", "z.txt"), model.uiState.value.entries.map { it.name })
        model.openDirectory("片段")
        runCurrent()
        assertEquals("片段", model.uiState.value.currentDirectory)
        model.openDirectory("片段/2026")
        runCurrent()
        assertEquals("电影.mp4", model.uiState.value.entries.single().name)
        model.navigateUp()
        runCurrent()
        assertEquals("片段", model.uiState.value.currentDirectory)
        model.navigateUp()
        runCurrent()
        assertEquals("", model.uiState.value.currentDirectory)
        repository.directories[""] = listOf(file("网页导入.mp4", video = true))
        repository.notifyChanged()
        runCurrent()
        assertEquals(listOf("网页导入.mp4"), model.uiState.value.entries.map { it.name })
    }

    @Test
    fun `视频按需创建记录并复用已有记录 普通文件不播放`() = runTest(dispatcher) {
        val repository = FakeFileTreeRepository()
        val model = HomeViewModel(repository, NoOpPlaybackStopper, dispatcher)
        val played = mutableListOf<Long>()
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.messages.collect { messages += it } }

        model.resolveVideo(file("新片.mp4", video = true)) { played += it }
        runCurrent()
        model.resolveVideo(file("已有.mp4", video = true, mediaId = 23L)) { played += it }
        runCurrent()
        model.resolveVideo(file("说明.txt")) { played += it }
        runCurrent()

        assertEquals(listOf(91L, 23L), played)
        assertEquals(listOf("新片.mp4"), repository.ensuredPaths)
        assertTrue(messages.single().contains("不支持播放"))

        repository.failOperations = true
        model.resolveVideo(file("已删除.mp4", video = true)) { played += it }
        runCurrent()
        assertEquals(listOf(91L, 23L), played)
        assertTrue(messages.last().contains("无法打开视频"))
    }

    @Test
    fun `移动目标仅包含目录且排除自身`() = runTest(dispatcher) {
        val source = directory("原目录")
        val repository = FakeFileTreeRepository().apply {
            directories[""] = listOf(source, directory("目标"), file("文本.txt"))
        }
        val model = HomeViewModel(repository, NoOpPlaybackStopper, dispatcher)

        assertEquals(listOf("目标"), model.directoriesForMove("", source).map { it.name })
    }

    @Test
    fun `当前目录被删除时提示返回上级且不显示为空目录`() = runTest(dispatcher) {
        val repository = FakeFileTreeRepository().apply {
            directories[""] = listOf(directory("旧目录"))
            directories["旧目录"] = listOf(file("旧目录/视频.mp4", video = true))
        }
        val model = HomeViewModel(repository, NoOpPlaybackStopper, dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        runCurrent()
        model.openDirectory("旧目录")
        runCurrent()
        assertEquals(1, model.uiState.value.entries.size)

        repository.directories.remove("旧目录")
        repository.directories[""] = emptyList()
        repository.notifyChanged()
        runCurrent()
        assertTrue(model.uiState.value.error.orEmpty().contains("返回上级目录"))
        assertFalse(model.uiState.value.isLoading)
        model.navigateUp()
        runCurrent()
        assertEquals("", model.uiState.value.currentDirectory)
        assertEquals(null, model.uiState.value.error)
        assertTrue(model.uiState.value.entries.isEmpty())
    }

    @Test
    fun `读取失败可重试 操作失败有反馈`() = runTest(dispatcher) {
        val repository = FakeFileTreeRepository().apply { failedDirectories += "" }
        val model = HomeViewModel(repository, NoOpPlaybackStopper, dispatcher)
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.messages.collect { messages += it } }
        runCurrent()
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isLoading)

        repository.failedDirectories.clear()
        repository.directories[""] = listOf(file("a.txt"))
        model.retry()
        runCurrent()
        assertEquals(listOf("a.txt"), model.uiState.value.entries.map { it.name })
        assertEquals(null, model.uiState.value.error)

        repository.failOperations = true
        model.delete(file("a.txt"))
        runCurrent()
        assertTrue(messages.single().contains("删除失败"))
        assertFalse(model.uiState.value.isWorking)
    }
}

private fun directory(path: String) = FileTreeEntry(
    name = path.substringAfterLast('/'), path = path, isDirectory = true, sizeBytes = 0, modifiedAt = 0,
)

private fun file(path: String, video: Boolean = false, mediaId: Long? = null) = FileTreeEntry(
    name = path.substringAfterLast('/'), path = path, isDirectory = false, sizeBytes = 1, modifiedAt = 0,
    isVideo = video, mediaId = mediaId,
)

private class FakeFileTreeRepository : FileTreeRepository {
    val directories = mutableMapOf<String, List<FileTreeEntry>>("" to emptyList())
    val failedDirectories = mutableSetOf<String>()
    val ensuredPaths = mutableListOf<String>()
    var failOperations = false
    private val changes = MutableStateFlow(0L)

    override fun observeChanges(): Flow<Long> = changes
    override fun observeDirectory(path: String): Flow<List<FileTreeEntry>> = changes.map { list(path) }
    override suspend fun list(path: String): List<FileTreeEntry> {
        if (path in failedDirectories) error("读取失败")
        return directories[path] ?: error("目录不存在")
    }
    override suspend fun find(path: String): FileTreeEntry? = directories.values.flatten().find { it.path == path }
    override suspend fun createDirectory(parent: String, name: String): FileTreeEntry {
        if (failOperations) error("创建失败")
        return directory(if (parent.isEmpty()) name else "$parent/$name")
    }
    override suspend fun rename(path: String, name: String): FileTreeEntry {
        if (failOperations) error("重命名失败")
        return directory(name)
    }
    override suspend fun move(path: String, destination: String): FileTreeEntry {
        if (failOperations) error("移动失败")
        return directory(path)
    }
    override suspend fun delete(path: String) {
        if (failOperations) error("删除失败")
    }
    override suspend fun ensureMedia(path: String): Long {
        if (failOperations) error("视频不可用")
        ensuredPaths += path
        return 91L
    }
    override suspend fun openForRead(path: String): File = error("测试不读取文件")
    override suspend fun requireDirectory(path: String): File = error("测试不直接访问目录")
    override suspend fun matchingSubtitles(path: String): List<String> = emptyList()
    override fun notifyChanged() { changes.value++ }
}
