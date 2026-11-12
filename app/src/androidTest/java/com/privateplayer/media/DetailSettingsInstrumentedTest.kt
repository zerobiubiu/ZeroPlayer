package com.privateplayer.media

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
import com.privateplayer.core.storage.LocalSubtitleStore
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.core.storage.StorageMediaFileLocator
import com.privateplayer.domain.model.LibraryQuery
import com.privateplayer.domain.model.LibraryStats
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.StorageUsage
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.repository.StorageRepository
import com.privateplayer.domain.usecase.NoOpPlaybackStopper
import com.privateplayer.feature.detail.DetailRouteArgs
import com.privateplayer.feature.detail.MediaDetailScreen
import com.privateplayer.feature.detail.MediaDetailViewModel
import com.privateplayer.feature.settings.AppVersionNameProvider
import com.privateplayer.feature.settings.SettingsHubScreen
import com.privateplayer.feature.settings.SettingsSection
import com.privateplayer.feature.settings.SettingsSectionScreen
import com.privateplayer.feature.settings.SettingsViewModel
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailSettingsInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun detailShowsReadOnlyInformationPlaysAndManagesRealSubtitles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val storage = PrivateStorage(context)
        val mediaId = -(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1L)
        val videoFile = File(storage.mediaRoot, "详情回归-${UUID.randomUUID()}.mkv")
        videoFile.parentFile!!.mkdirs()
        videoFile.writeBytes(byteArrayOf(1, 2, 3))
        val siblingSubtitle = File(storage.mediaRoot, "${videoFile.nameWithoutExtension}.zh.srt")
            .apply { writeText("同目录字幕") }
        val fileTree = DetailFileTreeRepository(videoFile.name, siblingSubtitle)
        val subtitle = storage.saveSubtitle(
            mediaId,
            "回归字幕.srt",
            ByteArrayInputStream("1\n00:00:00,000 --> 00:00:01,000\n字幕\n".toByteArray()),
        )
        val media = Media(
            id = mediaId,
            displayName = "详情回归视频",
            fileName = videoFile.name,
            relativePath = "media/${videoFile.name}",
            folderId = 7L,
            folderName = "旧逻辑文件夹",
            mimeType = "video/x-matroska",
            sizeBytes = videoFile.length(),
            durationMs = 60_000L,
            width = 1920,
            height = 1080,
            videoCodec = "H.264",
            audioCodec = "AAC",
            bitrate = 1_000_000L,
            audioTrackCount = 2,
            subtitleTrackCount = 1,
            thumbnailPath = null,
            isFavorite = true,
            addedAt = 1_700_000_000_000L,
            lastPlayedAt = 1_700_000_001_000L,
            playbackPositionMs = 30_000L,
            playedDurationMs = 30_000L,
            playCount = 1,
        )
        val viewModel = MediaDetailViewModel(
            SavedStateHandle(mapOf(DetailRouteArgs.ARG_MEDIA_ID to mediaId.toString())),
            DetailMediaRepository(media),
            fileTree,
            LocalSubtitleStore(storage, SafDocuments(context)),
            StorageMediaFileLocator(storage),
            NoOpPlaybackStopper,
            context,
            Dispatchers.IO,
        )
        val playedId = AtomicLong()
        try {
            compose.setContent {
                MaterialTheme {
                    MediaDetailScreen(
                        onBack = {},
                        onPlay = { playedId.set(it) },
                        onExportFile = { _, _ -> },
                        onShareFile = {},
                        viewModel = viewModel,
                    )
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(media.displayName).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("播放").performClick()
            assertEquals(mediaId, playedId.get())
            compose.onNodeWithContentDescription("取消收藏").assertDoesNotExist()
            compose.onNodeWithContentDescription("收藏").assertDoesNotExist()
            compose.onNodeWithText("旧逻辑文件夹", substring = true).assertDoesNotExist()
            compose.onNodeWithText("重命名").assertDoesNotExist()
            compose.onNodeWithText("移动").assertDoesNotExist()
            compose.onNodeWithText("加入列表").assertDoesNotExist()
            compose.onNodeWithText("观看进度 50%", substring = true).assertExists()

            compose.onNodeWithContentDescription("文件信息").performClick()
            compose.onNodeWithText("相对路径").assertIsDisplayed()
            pressBack()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("音轨数量（导入探测）"))
            compose.onNodeWithText("视频编码").assertExists()
            compose.onNodeWithText("音频编码").assertExists()
            compose.onNodeWithText("音轨数量（导入探测）").assertExists()
            compose.onNodeWithText("内嵌字幕轨（导入探测）").assertExists()
            compose.waitUntil(10_000) {
                viewModel.uiState.value.subtitles.size == 1 &&
                    viewModel.uiState.value.sameDirectorySubtitles.size == 1
            }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("外挂字幕 (2)"))
            compose.onNodeWithText("添加").assertIsDisplayed()
            compose.onNodeWithText("同目录字幕 · 在首页管理").assertExists()
            compose.onNodeWithText("已添加的私有副本").assertExists()
            compose.onNodeWithContentDescription("删除字幕 ${siblingSubtitle.name}").assertDoesNotExist()
            compose.onNodeWithContentDescription("删除字幕 回归字幕.srt").performClick()
            compose.onNode(hasText("删除") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { !subtitle.exists() && viewModel.uiState.value.subtitles.isEmpty() }
            assertTrue(siblingSubtitle.isFile)
            compose.onNodeWithText("外挂字幕 (1)").assertExists()
            siblingSubtitle.delete()
            fileTree.notifyChanged()
            compose.waitUntil(10_000) { viewModel.uiState.value.sameDirectorySubtitles.isEmpty() }
            compose.onNodeWithText("还没有外挂字幕。", substring = true).assertExists()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sourceName = "字幕回归-${UUID.randomUUID()}.srt"
                val contents = "1\n00:00:00,000 --> 00:00:01,000\n导入字幕\n"
                val resolver = context.contentResolver
                val source = requireNotNull(
                    resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                        },
                    ),
                )
                try {
                    requireNotNull(resolver.openOutputStream(source)).use { it.write(contents.toByteArray()) }
                    compose.runOnIdle { viewModel.importSubtitle(source.toString()) }
                    compose.waitUntil(10_000) { viewModel.uiState.value.subtitles.size == 1 }
                    val imported = storage.listSubtitles(mediaId).single()
                    assertEquals(contents, imported.readText())
                    compose.onNode(hasScrollAction()).performScrollToNode(hasText("外挂字幕 (1)"))
                    compose.onNodeWithContentDescription("删除字幕 ${imported.name}").performClick()
                    compose.onNode(hasText("删除") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
                    compose.waitUntil(10_000) { !imported.exists() && viewModel.uiState.value.subtitles.isEmpty() }
                    assertEquals(contents, requireNotNull(resolver.openInputStream(source)).bufferedReader().use { it.readText() })
                } finally {
                    resolver.delete(source, null, null)
                }
            }
        } finally {
            storage.deleteSubtitleDir(mediaId)
            siblingSubtitle.delete()
            videoFile.delete()
        }
    }

    @Test
    fun settingsShowsFileStorageWithBinaryUnitsAndNoObsoleteEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val viewModel = SettingsViewModel(
            PreferencesSettingsRepository(context),
            object : StorageRepository {
                override fun observeUsage(): Flow<StorageUsage> = flowOf(
                    StorageUsage(
                        mediaBytes = 1536L * 1024 * 1024,
                        thumbnailBytes = 2L * 1024 * 1024,
                        temporaryBytes = 1024,
                        freeBytes = 2L * 1024 * 1024 * 1024,
                        mediaCount = 3,
                    ),
                )

                override suspend fun clearThumbnailCache(): OperationOutcome = error("本测试不清理缓存")
                override suspend fun clearTemporaryFiles(): OperationOutcome = error("本测试不清理缓存")
            },
            object : PlaybackEngineProvider {
                override val backendLabel = "测试引擎"
                override val backendDetail = "测试状态"
                override fun create(): PlaybackEngine = error("设置页不应创建播放引擎")
            },
            AppVersionNameProvider { "1.0.1" },
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            Dispatchers.IO,
        )
        val selectedSection = mutableStateOf<SettingsSection?>(null)
        compose.setContent {
            MaterialTheme {
                val section = selectedSection.value
                if (section == null) {
                    SettingsHubScreen(
                        onOpenSection = { selectedSection.value = it },
                        viewModel = viewModel,
                    )
                } else {
                    SettingsSectionScreen(
                        section = section,
                        onBack = { selectedSection.value = null },
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(10_000) { viewModel.uiState.value.usage.mediaCount == 3 }
        compose.onAllNodesWithText("媒体库", substring = true).assertCountEquals(0)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("外观"))
        compose.onNodeWithText("外观").performClick()
        compose.onNode(hasText("主题模式") and hasClickAction()).assertExists()
        compose.onNodeWithText("跟随系统").assertExists()
        compose.onAllNodesWithText("网格密度", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("媒体库", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("返回").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("数据与存储"))
        compose.onNodeWithText("数据与存储").performClick()
        compose.onNodeWithText("文件").assertExists()
        compose.onNodeWithText("3 个视频 · 1.5 GiB").assertExists()
        compose.onNodeWithText("2.0 MiB").assertExists()
        compose.onNodeWithText("2.0 GiB").assertExists()
        compose.onAllNodesWithText("媒体库", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("返回").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("关于"))
        compose.onNodeWithText("关于").performClick()
        compose.onNodeWithText("播放引擎").assertExists()
        compose.onNodeWithText("测试引擎").assertExists()
        compose.onAllNodesWithText("MPV 集成验证", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("播放列表", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("MPV 集成验证").assertCountEquals(0)
        compose.onAllNodesWithText("媒体库", substring = true).assertCountEquals(0)
    }

    @Test
    fun settingsNumericRowsSaveImmediatelyWithoutSaveButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repository = PreferencesSettingsRepository(context)
        val viewModel = SettingsViewModel(
            repository,
            object : StorageRepository {
                override fun observeUsage(): Flow<StorageUsage> = flowOf(StorageUsage(0, 0, 0, 0, 0))

                override suspend fun clearThumbnailCache(): OperationOutcome = error("本测试不清理缓存")
                override suspend fun clearTemporaryFiles(): OperationOutcome = error("本测试不清理缓存")
            },
            object : PlaybackEngineProvider {
                override val backendLabel = "测试引擎"
                override val backendDetail = "测试状态"
                override fun create(): PlaybackEngine = error("设置页不应创建播放引擎")
            },
            AppVersionNameProvider { "1.0.1" },
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            Dispatchers.IO,
        )
        val selectedSection = mutableStateOf<SettingsSection?>(null)
        compose.setContent {
            MaterialTheme {
                val section = selectedSection.value
                if (section == null) {
                    SettingsHubScreen(
                        onOpenSection = { selectedSection.value = it },
                        viewModel = viewModel,
                    )
                } else {
                    SettingsSectionScreen(
                        section = section,
                        onBack = { selectedSection.value = null },
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitUntil(10_000) { viewModel.uiState.value.settings.playback.defaultSpeed > 0f }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("播放与字幕"))
        compose.onNodeWithText("播放与字幕").performClick()

        // 页面不再有逐行“保存”按钮
        compose.onAllNodesWithText("保存", substring = true).assertCountEquals(0)

        // 紧凑行：默认不显示范围提示（F-29）
        compose.onNodeWithText("允许范围：0.25～4 倍").assertDoesNotExist()

        // 点击紧凑行打开编辑对话框，预填当前值
        compose.onNodeWithTag("setting-默认倍速").performClick()
        compose.onNodeWithText("编辑默认倍速").assertExists()
        // 聚焦后显示范围提示（F-29）；requestFocus 异步，等待焦点生效
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("允许范围：0.25～4 倍").fetchSemanticsNodes().isNotEmpty()
        }

        // 先归一到一个确定的基线值，后续断言不依赖设备上残留的旧设置
        compose.onNodeWithTag("setting-edit-默认倍速").performTextReplacement("1")
        compose.onNodeWithTag("setting-edit-默认倍速").performImeAction()
        compose.waitUntil(10_000) { viewModel.uiState.value.settings.playback.defaultSpeed == 1.0f }

        // 非法值不写入并给出提示，对话框不关闭
        compose.onNodeWithTag("setting-默认倍速").performClick()
        compose.onNodeWithTag("setting-edit-默认倍速").performTextReplacement("9")
        compose.onNodeWithTag("setting-edit-默认倍速").performImeAction()
        compose.onNodeWithText("请输入0.25～4 倍").assertExists()
        assertEquals(1.0f, viewModel.uiState.value.settings.playback.defaultSpeed, 0.0f)

        // 合法值键盘完成即写入，无需点击保存（对话框仍开着，直接改值）
        compose.onNodeWithTag("setting-edit-默认倍速").performTextReplacement("2.5")
        compose.onNodeWithTag("setting-edit-默认倍速").performImeAction()
        compose.waitUntil(10_000) { viewModel.uiState.value.settings.playback.defaultSpeed == 2.5f }

        // 快速连续修改，最后一次生效
        compose.onNodeWithTag("setting-默认倍速").performClick()
        compose.onNodeWithTag("setting-edit-默认倍速").performTextReplacement("3")
        compose.onNodeWithTag("setting-edit-默认倍速").performImeAction()
        compose.waitUntil(10_000) { viewModel.uiState.value.settings.playback.defaultSpeed == 3.0f }

        // 返回一级再进入，值保留
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("播放与字幕"))
        compose.onNodeWithText("播放与字幕").performClick()
        compose.waitUntil(10_000) { viewModel.uiState.value.settings.playback.defaultSpeed == 3.0f }
        compose.onNodeWithTag("setting-value-默认倍速", useUnmergedTree = true)
            .assert(SemanticsMatcher("值含 3.0") { node ->
                node.config.getOrElseNullable(SemanticsProperties.Text) { null }
                    ?.any { it.text.contains("3.0") } == true
            })
    }
}

private class DetailMediaRepository(private val media: Media) : MediaRepository {
    override fun observeById(id: Long): Flow<Media?> = flowOf(media.takeIf { it.id == id })
    override fun observeRecentlyAdded(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeContinueWatching(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeFavorites(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeRecentlyPlayed(limit: Int): Flow<List<Media>> = error("详情页不应调用")
    override fun observeLibrary(query: LibraryQuery): Flow<List<Media>> = error("详情页不应调用")
    override fun observeStats(): Flow<LibraryStats> = error("详情页不应调用")
    override suspend fun findById(id: Long): Media? = error("详情页不应调用")
    override suspend fun setFavorite(id: Long, favorite: Boolean): OperationOutcome = error("详情页不应调用")
    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean): OperationOutcome = error("详情页不应调用")
    override suspend fun rename(id: Long, newDisplayName: String): OperationOutcome = error("详情页不应调用")
    override suspend fun moveToFolder(ids: List<Long>, folderId: Long?): OperationOutcome = error("详情页不应调用")
    override suspend fun delete(ids: List<Long>): OperationOutcome = error("详情页不应调用")
    override suspend fun recordPlayback(mediaId: Long, positionMs: Long, watchedMs: Long, playIncrement: Int) =
        error("详情页不应调用")

    override suspend fun setSelectedSubtitleTrack(mediaId: Long, trackId: String?) =
        error("详情页不应调用")
}

private class DetailFileTreeRepository(
    private val videoName: String,
    private val siblingSubtitle: File,
) : FileTreeRepository {
    private val changes = MutableStateFlow(0)

    override fun observeChanges(): Flow<Long> = changes.map(Int::toLong)

    override fun observeDirectory(path: String): Flow<List<FileTreeEntry>> {
        check(path.isEmpty())
        return changes.map { emptyList() }
    }

    override suspend fun matchingSubtitles(path: String): List<String> {
        check(path == videoName)
        return if (siblingSubtitle.isFile) listOf(siblingSubtitle.absolutePath) else emptyList()
    }

    override fun notifyChanged() {
        changes.value++
    }

    override suspend fun list(path: String): List<FileTreeEntry> = error("详情页不应调用")
    override suspend fun find(path: String): FileTreeEntry? = error("详情页不应调用")
    override suspend fun createDirectory(parent: String, name: String): FileTreeEntry = error("详情页不应调用")
    override suspend fun rename(path: String, name: String): FileTreeEntry = error("详情页不应调用")
    override suspend fun move(path: String, destination: String): FileTreeEntry = error("详情页不应调用")
    override suspend fun delete(path: String): Unit = error("详情页不应调用")
    override suspend fun openForRead(path: String): File = error("详情页不应调用")
    override suspend fun requireDirectory(path: String): File = error("详情页不应调用")
    override suspend fun ensureMedia(path: String): Long = error("详情页不应调用")
}
