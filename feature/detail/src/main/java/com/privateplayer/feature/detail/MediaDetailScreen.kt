package com.privateplayer.feature.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.common.format.formatDateTime
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.core.common.format.formatPercent
import com.privateplayer.core.design.components.AppTopBar
import com.privateplayer.core.design.components.ConfirmDialog
import com.privateplayer.core.design.components.DeleteDialog
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.FileActionMenuContent
import com.privateplayer.core.design.components.InfoRow
import com.privateplayer.core.design.components.LoadingState
import com.privateplayer.core.design.components.MediaThumbnail
import com.privateplayer.core.design.components.MetaChip
import com.privateplayer.core.design.components.MoveDialog
import com.privateplayer.core.design.components.NameDialog
import com.privateplayer.core.design.components.SectionHeader
import com.privateplayer.core.design.components.rememberFileActionMenuGroups
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.model.SubtitleFile
import com.privateplayer.domain.repository.FileTreeEntry

/** 视频详情：文件信息、播放、外挂字幕管理与文件操作（F-21）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    onExportFile: (path: String, name: String) -> Unit,
    onShareFile: (path: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var subtitleToDelete by remember { mutableStateOf<SubtitleFile?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var fileDialog by remember { mutableStateOf<DetailFileDialog?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importSubtitle(uri.toString())
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.isLoading, state.media) {
        if (!state.isLoading && state.media == null) onBack()
    }

    val media = state.media
    if (state.isLoading) {
        LoadingState(modifier = modifier.padding(top = 120.dp))
        return
    }
    if (media == null) {
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "视频详情",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.setShowFileInfo(true) }) {
                        Icon(Icons.Outlined.Info, contentDescription = "文件信息")
                    }
                    Box {
                        IconButton(
                            onClick = { moreMenuExpanded = true },
                            enabled = !state.isWorking,
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        // 详情页取共享分组菜单的子集：不含“视频详情”（已在本页），
                        // “文件信息”保留在顶栏，菜单内不再重复。
                        val entry = viewModel.treeEntry()
                        if (entry != null) {
                            val groups = rememberFileActionMenuGroups(
                                entry,
                                setOf(FileAction.Export, FileAction.Share, FileAction.Rename, FileAction.Move, FileAction.Delete),
                            )
                            DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                                FileActionMenuContent(groups) { action ->
                                    moreMenuExpanded = false
                                    when (action) {
                                        FileAction.Export -> onExportFile(entry.path, entry.name)
                                        FileAction.Share -> onShareFile(entry.path)
                                        FileAction.Rename -> fileDialog = DetailFileDialog.Rename(entry)
                                        FileAction.Move -> fileDialog = DetailFileDialog.Move(entry)
                                        FileAction.Delete -> fileDialog = DetailFileDialog.Delete(entry)
                                        FileAction.Details, FileAction.FileInfo -> Unit
                                    }
                                }
                            }
                        }
                    }
                },
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = Spacing.contentBottom),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenHorizontal),
                    ) {
                        MediaThumbnail(
                            thumbnailFile = state.thumbnailFile,
                            fallbackKey = media.id,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            cornerRadius = Radius.card,
                            durationText = formatDuration(media.durationMs),
                        )
                        Button(
                            onClick = { onPlay(media.id) },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .height(48.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("播放")
                        }
                        if (media.hasProgress) {
                            LinearProgressIndicator(
                                progress = { media.progress },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(4.dp),
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenHorizontal)
                            .padding(top = Spacing.lg),
                    ) {
                        Text(
                            text = media.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            MetaChip(media.resolutionLabel)
                            MetaChip(formatDuration(media.durationMs))
                            MetaChip(formatBytes(media.sizeBytes))
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = if (media.hasProgress) {
                                "观看进度 ${formatPercent(media.progress)} · 上次播放 ${formatDateTime(media.lastPlayedAt)}"
                            } else {
                                "暂无播放进度"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(Spacing.lg))
                    SectionHeader(title = "视频信息")
                }
                item {
                    Column {
                        InfoRow("文件名", media.fileName)
                        InfoRow("大小", formatBytes(media.sizeBytes))
                        InfoRow("时长", formatDuration(media.durationMs))
                        InfoRow(
                            label = "分辨率",
                            value = if (media.width > 0) "${media.width}×${media.height}" else "未知",
                        )
                        InfoRow("视频编码", media.videoCodec ?: "未知")
                        InfoRow("音频编码", media.audioCodec ?: "未知")
                        InfoRow(
                            label = "码率",
                            value = media.bitrate?.let { "$it bps" } ?: "未知",
                        )
                        InfoRow("音轨数量（导入探测）", "${media.audioTrackCount}")
                        InfoRow(
                            "内嵌字幕轨（导入探测）",
                            if (media.subtitleTrackCount == 0) "未识别，以播放器为准" else "${media.subtitleTrackCount}",
                        )
                        InfoRow("导入时间", formatDateTime(media.addedAt))
                        InfoRow("最近播放", formatDateTime(media.lastPlayedAt))
                        InfoRow("播放次数", "${media.playCount}")
                        state.fileInfo?.let { info ->
                            InfoRow("文件状态", if (info.exists) "正常" else "文件缺失")
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(Spacing.lg))
                    SectionHeader(
                        title = "外挂字幕 (${state.subtitles.size + state.sameDirectorySubtitles.size})",
                        actionLabel = "添加",
                        onAction = { subtitlePicker.launch(arrayOf("*/*")) },
                    )
                }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                    ) {
                        state.sameDirectorySubtitleError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
                        if (state.subtitles.isEmpty() && state.sameDirectorySubtitles.isEmpty() &&
                            state.sameDirectorySubtitleError == null
                        ) {
                            Text(
                                text = "还没有外挂字幕。同目录同名字幕会自动匹配，也可添加 srt / ass / ssa / vtt 字幕副本。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
                        state.sameDirectorySubtitles.forEach { subtitle ->
                            SubtitleRow(subtitle = subtitle, source = "同目录字幕 · 在首页管理")
                        }
                        state.subtitles.forEach { subtitle ->
                            SubtitleRow(
                                subtitle = subtitle,
                                source = "已添加的私有副本",
                                onDelete = { subtitleToDelete = subtitle },
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(Spacing.xl))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(Radius.card),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenHorizontal),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.lg)) {
                            Text(
                                text = "私有存储说明",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "该视频保存在应用私有目录，其他应用无法直接访问；删除应用数据会同时删除视频。原始导入文件从未被修改。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.contentBottom),
        )
    }


    subtitleToDelete?.let { subtitle ->
        ConfirmDialog(
            title = "删除字幕",
            message = "将删除应用私有目录中的字幕副本「${subtitle.name}」，不影响视频或原始字幕文件。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteSubtitle(subtitle)
                subtitleToDelete = null
            },
            onDismiss = { subtitleToDelete = null },
        )
    }

    if (state.showFileInfo) {
        FileInfoSheet(
            title = media.displayName,
            rows = buildList {
                add("相对路径" to media.relativePath)
                add("绝对路径" to (state.fileInfo?.absolutePath ?: "—"))
                add("文件存在" to if (state.fileInfo?.exists == true) "是" else "否")
                add("磁盘大小" to formatBytes(state.fileInfo?.sizeBytes ?: 0L))
                add("MIME" to (media.mimeType ?: "未知"))
                add("缩略图" to (media.thumbnailPath ?: "未生成"))
            },
            onDismiss = { viewModel.setShowFileInfo(false) },
        )
    }

    when (val active = fileDialog) {
        is DetailFileDialog.Rename -> NameDialog(
            title = "重命名",
            initialName = active.entry.name,
            originalName = active.entry.path.substringAfterLast('/'),
            confirmLabel = "保存",
            onDismiss = { fileDialog = null },
            onConfirm = { name ->
                fileDialog = null
                viewModel.rename(name)
            },
        )

        is DetailFileDialog.Move -> MoveDialog(
            entry = active.entry,
            loadDirectories = viewModel::directoriesForMove,
            onDismiss = { fileDialog = null },
            onConfirm = { destination ->
                fileDialog = null
                viewModel.move(destination)
            },
        )

        is DetailFileDialog.Delete -> DeleteDialog(
            entry = active.entry,
            onDismiss = { fileDialog = null },
            onConfirm = {
                fileDialog = null
                viewModel.delete()
            },
        )

        null -> Unit
    }
}

/** 详情页文件操作对话框状态；对话框本体与首页共用 core:design 实现。 */
private sealed interface DetailFileDialog {
    data class Rename(val entry: FileTreeEntry) : DetailFileDialog
    data class Move(val entry: FileTreeEntry) : DetailFileDialog
    data class Delete(val entry: FileTreeEntry) : DetailFileDialog
}

@Composable
private fun SubtitleRow(
    subtitle: SubtitleFile,
    source: String,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Icon(
            Icons.Outlined.Subtitles,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subtitle.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = "删除字幕 ${subtitle.name}" },
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoSheet(
    title: String,
    rows: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
            Text(
                text = "文件信息",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
            Spacer(Modifier.height(Spacing.md))
            rows.forEach { (label, value) ->
                InfoRow(label = label, value = value)
            }
        }
    }
}
