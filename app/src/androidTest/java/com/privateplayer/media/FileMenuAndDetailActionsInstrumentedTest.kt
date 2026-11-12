package com.privateplayer.media

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.longClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.performTouchInput
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.FileActionMenuContent
import com.privateplayer.core.design.components.fileActionMenuGroups
import com.privateplayer.core.storage.LocalSubtitleStore
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.core.storage.StorageMediaFileLocator
import com.privateplayer.domain.model.LibraryQuery
import com.privateplayer.domain.model.LibraryStats
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.usecase.NoOpPlaybackStopper
import com.privateplayer.feature.detail.DetailRouteArgs
import com.privateplayer.feature.detail.MediaDetailScreen
import com.privateplayer.feature.detail.MediaDetailViewModel
import com.privateplayer.feature.home.HomeScreen
import com.privateplayer.feature.home.HomeViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 文件菜单分组（F-22）与详情页文件操作（F-21）的 UI 验收。
 *
 * 菜单分组直接驱动生产的 [fileActionMenuGroups]/[FileActionMenuContent]；
 * 首页行菜单与详情页更多菜单经真实页面渲染验证同一套分组、确认与校验。
 */
@RunWith(AndroidJUnit4::class)
class FileMenuAndDetailActionsInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private fun videoEntry(name: String = "影片.mp4", path: String = name) = FileTreeEntry(
        name = name, path = path, isDirectory = false, sizeBytes = 10, modifiedAt = 0,
        mediaId = 1L, isVideo = true,
    )

    // ------------------------------------------------------------ F-22 菜单分组

    @Test
    fun sharedMenuGroupsVideoSubsetInOrderWithDeleteSeparated() {
        val clicked = mutableListOf<FileAction>()
        val groups = fileActionMenuGroups(
            videoEntry(),
            FileAction.entries.toSet() - FileAction.FileInfo,
        )
        compose.setContent {
            MaterialTheme {
                // DropdownMenuItem 只在展开的菜单弹出层内渲染；用常开菜单承载生产构建器。
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    FileActionMenuContent(groups) { clicked += it }
                }
            }
        }
        // 顺序：视频详情 → 导出/分享 → 重命名/移动 → 删除
        val labels = listOf("视频详情", "导出", "分享", "重命名", "移动", "删除")
        labels.forEach { compose.onNodeWithTag("文件菜单-$it").assertIsDisplayed() }
        compose.onAllNodesWithText("文件信息").assertCountEquals(0)

        // 逐项点击验证回调与分组位置（删除独立最后）
        compose.onNodeWithTag("文件菜单-删除").performClick()
        compose.waitUntil(5_000) { clicked == listOf(FileAction.Delete) }
        compose.onNodeWithTag("文件菜单-重命名").performClick()
        compose.waitUntil(5_000) { clicked == listOf(FileAction.Delete, FileAction.Rename) }
    }

    @Test
    fun homeRowMenuShowsGroupedActionsAndDeleteOpensConfirm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val storage = PrivateStorage(context)
        val video = File(storage.mediaRoot, "菜单回归-${UUID.randomUUID()}.mp4").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val tree = MenuFileTreeRepository(video)
        val viewModel = HomeViewModel(tree, NoOpPlaybackStopper, Dispatchers.IO)
        try {
            compose.setContent {
                MaterialTheme {
                    HomeScreen(
                        onOpenMedia = {},
                        onPlayMedia = {},
                        onOpenLocalImport = {},
                        onOpenWifiImport = {},
                        onExportFile = { _, _ -> },
                        onShareFile = {},
                        viewModel = viewModel,
                    )
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(video.name).fetchSemanticsNodes().isNotEmpty()
            }
            // 三点按钮打开菜单（与长按共用同一构建器）
            compose.onNodeWithContentDescription("${video.name}的操作菜单").performClick()
            listOf("视频详情", "导出", "分享", "重命名", "移动", "删除").forEach {
                compose.onNodeWithTag("文件菜单-$it").assertIsDisplayed()
            }
            compose.onAllNodesWithText("文件信息").assertCountEquals(0)

            // 删除独立分组且二次确认：点名文件、说明不可撤销
            compose.onNodeWithTag("文件菜单-删除").performClick()
            compose.onNodeWithText("删除文件？").assertIsDisplayed()
            compose.onNodeWithText("将永久删除“${video.name}”，此操作无法撤销。").assertIsDisplayed()
            // 取消不删除
            compose.onNode(hasText("取消") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("删除文件？").fetchSemanticsNodes().isEmpty()
            }
            assertTrue(video.isFile)

            // F-41：长按进入多选（不再打开菜单），顶栏显示已选数量
            compose.onNodeWithText(video.name).performTouchInput { longClick() }
            compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
            // 多选态批量删除：二次确认后删除
            compose.onNodeWithContentDescription("删除").performClick()
            compose.onNodeWithText("删除 1 项？").assertIsDisplayed()
            compose.onNode(hasText("删除") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { !video.exists() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(video.name).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            video.delete()
        }
    }

    // ------------------------------------------------------------ F-21 详情页操作

    private fun detailFixture(): DetailFixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val storage = PrivateStorage(context)
        val mediaId = -(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1L)
        val video = File(storage.mediaRoot, "详情操作-${UUID.randomUUID()}.mp4").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val media = Media(
            id = mediaId,
            displayName = video.nameWithoutExtension,
            fileName = video.name,
            relativePath = "media/${video.name}",
            folderId = null,
            folderName = null,
            mimeType = "video/mp4",
            sizeBytes = video.length(),
            durationMs = 60_000L,
            width = 1280,
            height = 720,
            videoCodec = "H.264",
            audioCodec = "AAC",
            bitrate = 1_000_000L,
            audioTrackCount = 1,
            subtitleTrackCount = 0,
            thumbnailPath = null,
            isFavorite = false,
            addedAt = 1_700_000_000_000L,
            lastPlayedAt = null,
            playbackPositionMs = 0L,
            playedDurationMs = 0L,
            playCount = 0,
        )
        val tree = DetailActionFileTreeRepository(video)
        val mediaRepository = MutableMediaRepository(media)
        // 真实实现里文件操作与媒体记录同事务更新；替身用回调模拟同一同步。
        tree.onChanged = { path, deleted ->
            if (deleted) mediaRepository.remove()
            else mediaRepository.update {
                it.copy(
                    fileName = path.substringAfterLast('/'),
                    displayName = path.substringAfterLast('/').substringBeforeLast('.'),
                    relativePath = "media/$path",
                )
            }
        }
        val viewModel = MediaDetailViewModel(
            SavedStateHandle(mapOf(DetailRouteArgs.ARG_MEDIA_ID to mediaId.toString())),
            mediaRepository,
            tree,
            LocalSubtitleStore(storage, SafDocuments(context)),
            StorageMediaFileLocator(storage),
            NoOpPlaybackStopper,
            context,
            Dispatchers.IO,
        )
        return DetailFixture(storage, video, media, tree, viewModel)
    }

    private data class DetailFixture(
        val storage: PrivateStorage,
        val video: File,
        val media: Media,
        val tree: DetailActionFileTreeRepository,
        val viewModel: MediaDetailViewModel,
    )

    private fun showDetail(fixture: DetailFixture, onBack: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                MediaDetailScreen(
                    onBack = onBack,
                    onPlay = {},
                    onExportFile = { _, _ -> },
                    onShareFile = {},
                    viewModel = fixture.viewModel,
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(fixture.media.displayName).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDetailMoreMenu() {
        compose.onNodeWithContentDescription("更多").performClick()
    }

    @Test
    fun detailMoreMenuShowsFiveOperationsWithoutDetailsEntry() {
        val fixture = detailFixture()
        try {
            showDetail(fixture)
            // 文件信息保留在顶栏
            compose.onNodeWithContentDescription("文件信息").assertIsDisplayed()
            openDetailMoreMenu()
            listOf("导出", "分享", "重命名", "移动", "删除").forEach {
                compose.onNodeWithTag("文件菜单-$it").assertIsDisplayed()
            }
            // 菜单内不含“视频详情/文件信息”（顶栏标题与图标描述不在菜单弹出层内）
            compose.onNodeWithTag("文件菜单-视频详情").assertDoesNotExist()
            compose.onNodeWithTag("文件菜单-文件信息").assertDoesNotExist()
        } finally {
            fixture.video.delete()
        }
    }

    @Test
    fun detailRenameValidatesAndSyncsMedia() {
        val fixture = detailFixture()
        try {
            showDetail(fixture)
            openDetailMoreMenu()
            compose.onNodeWithTag("文件菜单-重命名").performClick()
            compose.onNodeWithText("重命名").assertIsDisplayed()

            // 非法字符：行内提示 + 确认禁用
            compose.onNodeWithTag("名称输入").performTextReplacement("坏/名字.mp4")
            compose.onNodeWithText("名称不能包含 / 或 \\").assertIsDisplayed()
            compose.onNodeWithTag("名称确认").assertIsNotEnabled()

            // 与原名相同：确认禁用
            compose.onNodeWithTag("名称输入").performTextReplacement(fixture.video.name)
            compose.onNodeWithTag("名称确认").assertIsNotEnabled()

            // 合法新名：确认后媒体记录与磁盘同步
            val newName = "已改名-${UUID.randomUUID()}.mp4"
            compose.onNodeWithTag("名称输入").performTextReplacement(newName)
            compose.onNodeWithTag("名称确认").assertIsEnabled()
            compose.onNodeWithTag("名称确认").performClick()
            compose.waitUntil(10_000) {
                File(fixture.storage.mediaRoot, newName).isFile && !fixture.video.exists()
            }
            compose.waitUntil(10_000) {
                fixture.viewModel.uiState.value.media?.fileName == newName
            }
            compose.onNodeWithText("已重命名").assertExists()
        } finally {
            fixture.storage.mediaRoot.listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun detailDeleteConfirmsThenRemovesFileAndAutoBacks() {
        val fixture = detailFixture()
        val backed = AtomicReference(false)
        try {
            showDetail(fixture) { backed.set(true) }
            openDetailMoreMenu()
            compose.onNodeWithTag("文件菜单-删除").performClick()
            compose.onNodeWithText("删除文件？").assertIsDisplayed()
            compose.onNodeWithText("将永久删除“${fixture.video.name}”，此操作无法撤销。").assertIsDisplayed()

            // 取消保留文件
            compose.onNode(hasText("取消") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("删除文件？").fetchSemanticsNodes().isEmpty()
            }
            assertTrue(fixture.video.isFile)

            // 确认后删除并自动返回
            openDetailMoreMenu()
            compose.onNodeWithTag("文件菜单-删除").performClick()
            compose.onNode(hasText("删除") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { !fixture.video.exists() }
            compose.waitUntil(10_000) { backed.get() }
        } finally {
            fixture.video.delete()
        }
    }

    @Test
    fun detailMovePickerListsDirectoriesAndMovesFile() {
        val fixture = detailFixture()
        val target = File(fixture.storage.mediaRoot, "目标目录").apply { check(mkdir()) }
        try {
            showDetail(fixture)
            openDetailMoreMenu()
            compose.onNodeWithTag("文件菜单-移动").performClick()
            compose.onNodeWithText("移动“${fixture.video.name}”").assertIsDisplayed()
            // 初始目标与原目录相同：确认禁用
            compose.onNodeWithTag("移动确认").assertIsNotEnabled()
            // 进入目标目录后确认可用
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("目标目录").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("目标目录").performClick()
            compose.waitUntil(10_000) {
                !compose.onNodeWithTag("移动确认").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
            }
            compose.onNodeWithTag("移动确认").assertIsEnabled()
            compose.onNodeWithTag("移动确认").performClick()
            compose.waitUntil(10_000) { File(target, fixture.video.name).isFile && !fixture.video.exists() }
            compose.waitUntil(10_000) {
                fixture.viewModel.uiState.value.media?.relativePath == "media/目标目录/${fixture.video.name}"
            }
            compose.onNodeWithText("已移动").assertExists()
        } finally {
            target.deleteRecursively()
            fixture.video.delete()
        }
    }

    @Test
    fun detailExportAndShareUseRootCallbacks() {
        val fixture = detailFixture()
        val exported = AtomicReference<Pair<String, String>?>(null)
        val shared = AtomicReference<String?>(null)
        try {
            compose.setContent {
                MaterialTheme {
                    MediaDetailScreen(
                        onBack = {},
                        onPlay = {},
                        onExportFile = { path, name -> exported.set(path to name) },
                        onShareFile = { shared.set(it) },
                        viewModel = fixture.viewModel,
                    )
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(fixture.media.displayName).fetchSemanticsNodes().isNotEmpty()
            }
            openDetailMoreMenu()
            compose.onNodeWithTag("文件菜单-导出").performClick()
            compose.waitUntil(5_000) { exported.get() != null }
            assertEquals(fixture.video.name, exported.get()!!.second)
            assertEquals(fixture.video.name, exported.get()!!.first)

            openDetailMoreMenu()
            compose.onNodeWithTag("文件菜单-分享").performClick()
            compose.waitUntil(5_000) { shared.get() != null }
            assertEquals(fixture.video.name, shared.get())
        } finally {
            fixture.video.delete()
        }
    }
}

/** 首页菜单测试用文件树：真实磁盘列举 + 内存变更通知。 */
private class MenuFileTreeRepository(private val video: File) : FileTreeRepository {
    private val changes = MutableStateFlow(0L)

    override fun observeChanges(): Flow<Long> = changes
    override fun observeDirectory(path: String): Flow<List<FileTreeEntry>> = changes.map { list(path) }

    override suspend fun list(path: String): List<FileTreeEntry> {
        check(path.isEmpty())
        return if (video.isFile) {
            listOf(
                FileTreeEntry(
                    name = video.name, path = video.name, isDirectory = false,
                    sizeBytes = video.length(), modifiedAt = video.lastModified(), isVideo = true,
                ),
            )
        } else {
            emptyList()
        }
    }

    override suspend fun find(path: String): FileTreeEntry? = list("").firstOrNull { it.path == path }
    override suspend fun createDirectory(parent: String, name: String): FileTreeEntry = error("本测试不新建目录")
    override suspend fun rename(path: String, name: String): FileTreeEntry = error("本测试不重命名")
    override suspend fun move(path: String, destination: String): FileTreeEntry = error("本测试不移动")
    override suspend fun delete(path: String) {
        check(video.delete()) { "删除失败" }
        notifyChanged()
    }
    override suspend fun openForRead(path: String): File = error("本测试不读取文件")
    override suspend fun requireDirectory(path: String): File = error("本测试不访问目录")
    override suspend fun ensureMedia(path: String): Long = error("本测试不入库")
    override suspend fun matchingSubtitles(path: String): List<String> = emptyList()
    override fun notifyChanged() { changes.value++ }
}

/** 详情页操作用文件树：真实磁盘 rename/move/delete + 变更通知。 */
private class DetailActionFileTreeRepository(private val video: File) : FileTreeRepository {
    private val changes = MutableStateFlow(0L)
    private val root get() = video.parentFile!!

    /** 文件变更后同步媒体记录（真实实现里两者同事务）；path 为新路径，deleted 表示已删除。 */
    var onChanged: ((path: String, deleted: Boolean) -> Unit)? = null

    override fun observeChanges(): Flow<Long> = changes
    override fun observeDirectory(path: String): Flow<List<FileTreeEntry>> = changes.map { list(path) }

    override suspend fun list(path: String): List<FileTreeEntry> {
        val directory = if (path.isEmpty()) root else File(root, path)
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles().orEmpty().map { child ->
            FileTreeEntry(
                name = child.name,
                path = if (path.isEmpty()) child.name else "$path/${child.name}",
                isDirectory = child.isDirectory,
                sizeBytes = if (child.isDirectory) 0 else child.length(),
                modifiedAt = child.lastModified(),
                isVideo = child.isFile && child.extension.equals("mp4", ignoreCase = true),
            )
        }.sortedBy { it.name }
    }

    override suspend fun find(path: String): FileTreeEntry? {
        val file = File(root, path)
        if (!file.exists()) return null
        return FileTreeEntry(
            name = file.name, path = path, isDirectory = file.isDirectory,
            sizeBytes = file.length(), modifiedAt = file.lastModified(),
        )
    }

    override suspend fun createDirectory(parent: String, name: String): FileTreeEntry = error("本测试不新建目录")

    override suspend fun rename(path: String, name: String): FileTreeEntry {
        val source = File(root, path)
        val target = File(source.parentFile, name)
        check(source.renameTo(target)) { "重命名失败" }
        val newPath = if (path.contains('/')) "${path.substringBeforeLast('/')}/$name" else name
        onChanged?.invoke(newPath, false)
        notifyChanged()
        return find(newPath)!!
    }

    override suspend fun move(path: String, destination: String): FileTreeEntry {
        val source = File(root, path)
        val target = File(File(root, destination), source.name)
        check(source.renameTo(target)) { "移动失败" }
        val newPath = if (destination.isEmpty()) source.name else "$destination/${source.name}"
        onChanged?.invoke(newPath, false)
        notifyChanged()
        return find(newPath)!!
    }

    override suspend fun delete(path: String) {
        check(File(root, path).delete()) { "删除失败" }
        onChanged?.invoke(path, true)
        notifyChanged()
    }

    override suspend fun openForRead(path: String): File = error("本测试不读取文件")
    override suspend fun requireDirectory(path: String): File = error("本测试不访问目录")
    override suspend fun ensureMedia(path: String): Long = error("本测试不入库")
    override suspend fun matchingSubtitles(path: String): List<String> = emptyList()
    override fun notifyChanged() { changes.value++ }
}

/** 可观察的媒体仓库：rename/move/delete 后由文件树变更驱动记录更新。 */
private class MutableMediaRepository(initial: Media) : MediaRepository {
    private val media = MutableStateFlow<Media?>(initial)

    fun update(transform: (Media) -> Media) {
        media.value = media.value?.let(transform)
    }

    fun remove() {
        media.value = null
    }

    override fun observeById(id: Long): Flow<Media?> = media.map { it?.takeIf { m -> m.id == id } }
    override fun observeRecentlyAdded(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeContinueWatching(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeFavorites(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeRecentlyPlayed(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeLibrary(query: LibraryQuery): Flow<List<Media>> = error("详情页不应调用")
    override fun observeStats(): Flow<LibraryStats> = error("详情页不应调用")
    override suspend fun findById(id: Long): Media? = media.value?.takeIf { it.id == id }
    override suspend fun setFavorite(id: Long, favorite: Boolean): OperationOutcome = error("详情页不应调用")
    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean): OperationOutcome = error("详情页不应调用")
    override suspend fun rename(id: Long, newDisplayName: String): OperationOutcome = error("详情页不应调用")
    override suspend fun moveToFolder(ids: List<Long>, folderId: Long?): OperationOutcome = error("详情页不应调用")
    override suspend fun delete(ids: List<Long>): OperationOutcome = error("详情页不应调用")
    override suspend fun recordPlayback(mediaId: Long, positionMs: Long, watchedMs: Long, playIncrement: Int) =
        error("详情页不应调用")
    override suspend fun setSelectedSubtitleTrack(mediaId: Long, trackId: String?) = error("详情页不应调用")
}
