package com.privateplayer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.components.AppTopBar
import com.privateplayer.core.design.components.InfoRow
import com.privateplayer.core.design.components.SettingSection
import com.privateplayer.core.design.components.SwitchRow
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.domain.model.ThemeMode
import com.privateplayer.domain.model.VideoAspect
import kotlin.math.roundToInt

/** 设置二级页面；一级只显示分组入口与当前摘要。 */
enum class SettingsSection(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
) {
    PLAYBACK("playback", "播放", "续播、倍速与画面比例", Icons.Outlined.PlayCircle),
    SUBTITLE("subtitle", "字幕", "字号、颜色与延迟", Icons.Outlined.ClosedCaption),
    GESTURE("gesture", "手势", "亮度、音量、进度与震动", Icons.Outlined.TouchApp),
    DISPLAY("display", "显示", "主题模式", Icons.Outlined.Palette),
    IMPORT("import", "导入", "缩略图与重复检测", Icons.Outlined.FileDownload),
    STORAGE("storage", "存储", "用量统计与缓存清理", Icons.Outlined.Storage),
    ABOUT("about", "关于", "版本、播放引擎与许可", Icons.Outlined.Info),
    ;

    companion object {
        fun fromId(id: String?): SettingsSection? = entries.firstOrNull { it.id == id }
    }
}

/** 一级设置页：按类型分组的入口列表。 */
@Composable
fun SettingsHubScreen(
    onOpenSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "设置", subtitle = "按类型进入二级页面调整")
            LazyColumn(
                contentPadding = PaddingValues(bottom = Spacing.contentBottom),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SettingSection(title = "偏好设置") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            SettingsSection.entries.forEach { section ->
                                SettingsEntryRow(
                                    section = section,
                                    detail = sectionDetail(section, state),
                                    onClick = { onOpenSection(section) },
                                )
                            }
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
}

@Composable
private fun SettingsEntryRow(
    section: SettingsSection,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.card),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.padding(horizontal = Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = section.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "进入${section.title}设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 二级设置页：只保留该类型的选项。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = section.title, subtitle = section.summary, onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.contentBottom),
            ) {
                when (section) {
                    SettingsSection.PLAYBACK -> PlaybackSettings(state, viewModel)
                    SettingsSection.SUBTITLE -> SubtitleSettings(state, viewModel)
                    SettingsSection.GESTURE -> GestureSettings(state, viewModel)
                    SettingsSection.DISPLAY -> DisplaySettings(state, viewModel)
                    SettingsSection.IMPORT -> ImportSettings(state, viewModel)
                    SettingsSection.STORAGE -> StorageSettings(state, viewModel)
                    SettingsSection.ABOUT -> AboutSettings(state)
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
}

@Composable
private fun PlaybackSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val playback = state.settings.playback
    SettingSection(title = "播放行为") {
        Column {
            SwitchRow(
                title = "自动播放下一个视频",
                description = "播放结束后按当前顺序继续播放",
                checked = playback.autoPlayNext,
                onCheckedChange = viewModel::setAutoPlayNext,
            )
            SwitchRow(
                title = "记忆播放进度",
                description = "关闭后不写入断点，仅保留播放历史",
                checked = playback.rememberProgress,
                onCheckedChange = viewModel::setRememberProgress,
            )
            SwitchRow(
                title = "打开时自动续播",
                description = "从上次位置继续播放",
                checked = playback.autoResume,
                onCheckedChange = viewModel::setAutoResume,
            )
        }
    }
    SettingSection(title = "播放参数") {
        Column {
            NumericSettingRow(
                title = "默认倍速",
                initialValue = playback.defaultSpeed.toString(),
                unit = "×", minimum = 0.25, maximum = 4.0,
                rangeLabel = "0.25～4 倍", integerOnly = false,
                onSave = { viewModel.setDefaultSpeed(it.toFloat()) },
            )
            SettingsDropdownRow(
                title = "默认画面比例",
                value = playback.defaultAspect.label,
                options = VideoAspect.entries.map { it to it.label },
                onSelect = viewModel::setDefaultAspect,
            )
            NumericSettingRow(
                title = "双击跳转时长",
                initialValue = playback.seekStepSeconds.toString(),
                unit = "秒", minimum = 5.0, maximum = 60.0,
                rangeLabel = "5～60 的整数秒", integerOnly = true,
                onSave = { viewModel.setSeekStep(it.toInt()) },
            )
            NumericSettingRow(
                title = "长按临时倍速",
                initialValue = playback.longPressSpeed.toString(),
                unit = "×", minimum = 1.5, maximum = 4.0,
                rangeLabel = "1.5～4 倍", integerOnly = false,
                onSave = { viewModel.setLongPressSpeed(it.toFloat()) },
            )
        }
    }
}

@Composable
private fun SubtitleSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val subtitle = state.settings.subtitle
    SettingSection(title = "字幕样式") {
        Column {
            NumericSettingRow(
                title = "字幕字号",
                initialValue = (subtitle.fontSizeScale * 100).roundToInt().toString(),
                unit = "%", minimum = 60.0, maximum = 200.0,
                rangeLabel = "60～200 的整数百分比", integerOnly = true,
                onSave = { viewModel.setSubtitleFontScale((it / 100).toFloat()) },
            )
            SettingsDropdownRow(
                title = "字幕背景",
                value = subtitle.background.label,
                options = SubtitleBackground.entries.map { it to it.label },
                onSelect = viewModel::setSubtitleBackground,
            )
            SettingsDropdownRow(
                title = "字幕颜色",
                value = subtitle.textColor.label,
                options = SubtitleTextColor.entries.map { it to it.label },
                onSelect = viewModel::setSubtitleTextColor,
            )
            Text(
                text = "字号、颜色和背景用于普通文本字幕；ASS 等自带样式的字幕保留原排版。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            )
        }
    }
    SettingSection(title = "字幕同步") {
        Column {
            NumericSettingRow(
                title = "延迟调节步长",
                initialValue = subtitle.delayStepMs.toString(),
                unit = "ms", minimum = 50.0, maximum = 1000.0,
                rangeLabel = "50～1000 的整数毫秒", integerOnly = true,
                onSave = { viewModel.setSubtitleDelayStep(it.toInt()) },
            )
            Text(
                text = "播放器会记住每个视频上次选中的字幕，下次打开自动恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun GestureSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val gesture = state.settings.gesture
    SettingSection(title = "手势开关") {
        Column {
            SwitchRow(
                title = "左侧上下滑动调节亮度",
                description = null,
                checked = gesture.brightnessGestureEnabled,
                onCheckedChange = viewModel::setBrightnessGesture,
            )
            SwitchRow(
                title = "右侧上下滑动调节音量",
                description = null,
                checked = gesture.volumeGestureEnabled,
                onCheckedChange = viewModel::setVolumeGesture,
            )
            SwitchRow(
                title = "横向滑动调节进度",
                description = "滑动越快跳幅越大，慢滑保持逐秒微调",
                checked = gesture.seekGestureEnabled,
                onCheckedChange = viewModel::setSeekGesture,
            )
            SwitchRow(
                title = "长按临时倍速",
                description = null,
                checked = gesture.longPressSpeedEnabled,
                onCheckedChange = viewModel::setLongPressSpeedGesture,
            )
        }
    }
    SettingSection(title = "反馈") {
        Column {
            SwitchRow(
                title = "滑动进度震动反馈",
                description = "部分设备没有线性马达或震感生硬，可关闭",
                checked = gesture.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
            )
        }
    }
}

@Composable
private fun DisplaySettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingSection(title = "主题") {
        Column {
            SettingsDropdownRow(
                title = "主题模式",
                value = state.settings.display.themeMode.label,
                options = ThemeMode.entries.map { it to it.label },
                onSelect = viewModel::setTheme,
            )
            Text(
                text = "使用应用自带的配色，不跟随系统动态取色，保证深色与浅色下的对比度一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ImportSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val import = state.settings.import
    SettingSection(title = "导入") {
        Column {
            SwitchRow(
                title = "导入时生成缩略图",
                description = "失败不影响导入，只影响列表封面",
                checked = import.generateThumbnails,
                onCheckedChange = viewModel::setGenerateThumbnails,
            )
            SwitchRow(
                title = "检测疑似重复视频",
                description = "按文件大小和首段内容提示，仍会保留副本",
                checked = import.duplicateDetection,
                onCheckedChange = viewModel::setDuplicateDetection,
            )
        }
    }
}

@Composable
private fun StorageSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingSection(title = "存储管理") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(Radius.card),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                InfoRow("文件", "${state.usage.mediaCount} 个视频 · ${formatBytes(state.usage.mediaBytes)}")
                InfoRow("缩略图缓存", formatBytes(state.usage.thumbnailBytes))
                InfoRow("临时文件", formatBytes(state.usage.temporaryBytes))
                InfoRow("可用空间", formatBytes(state.usage.freeBytes))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    OutlinedButton(onClick = viewModel::clearThumbnailCache) {
                        Text("清理缩略图")
                    }
                    OutlinedButton(onClick = viewModel::clearTemporaryFiles) {
                        Text("清理临时文件")
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "清理缓存不会删除文件或待恢复的导入暂存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun AboutSettings(state: SettingsUiState) {
    SettingSection(title = "版本信息") {
        Column {
            InfoRow("版本", state.versionName)
            InfoRow("播放引擎", state.engineLabel)
            InfoRow("引擎状态", state.engineDetail)
        }
    }
    SettingSection(title = "说明") {
        Column {
            Text(
                text = "本软件部分基于 FreeType 团队的工作。第三方许可随安装包保存在 assets/third-party-licenses；对外分发前须遵守各组件条款。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
            Text(
                text = "ZeroPlayer · 本地优先。视频保存在应用私有存储，不自动扫描系统文件；网页上传仅在手动开启时接收局域网文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
        }
    }
}

/** 一级入口的右侧摘要，进入二级页面前就能看到关键状态。 */
private fun sectionDetail(section: SettingsSection, state: SettingsUiState): String = when (section) {
    SettingsSection.PLAYBACK -> buildList {
        add(if (state.settings.playback.autoResume) "自动续播" else "从头播放")
        add("${state.settings.playback.defaultSpeed}×")
        add(state.settings.playback.defaultAspect.label)
    }.joinToString(" · ")
    SettingsSection.SUBTITLE -> buildList {
        add("字号 ${(state.settings.subtitle.fontSizeScale * 100).roundToInt()}%")
        add(state.settings.subtitle.textColor.label)
        add("步长 ${state.settings.subtitle.delayStepMs}ms")
    }.joinToString(" · ")
    SettingsSection.GESTURE -> buildList {
        if (state.settings.gesture.seekGestureEnabled) add("滑动调速") else add("滑动已关闭")
        add(if (state.settings.gesture.hapticsEnabled) "震动开" else "震动关")
    }.joinToString(" · ")
    SettingsSection.DISPLAY -> state.settings.display.themeMode.label
    SettingsSection.IMPORT -> buildList {
        add(if (state.settings.import.generateThumbnails) "生成缩略图" else "不生成缩略图")
        add(if (state.settings.import.duplicateDetection) "查重开" else "查重关")
    }.joinToString(" · ")
    SettingsSection.STORAGE -> "${state.usage.mediaCount} 个视频 · ${formatBytes(state.usage.mediaBytes)}"
    SettingsSection.ABOUT -> "版本 ${state.versionName} · ${state.engineLabel}"
}

private val VideoAspect.label: String
    get() = when (this) {
        VideoAspect.FIT -> "适应屏幕"
        VideoAspect.FILL -> "拉伸填充"
        VideoAspect.CROP -> "裁剪填满"
        VideoAspect.RATIO_16_9 -> "16:9"
        VideoAspect.RATIO_4_3 -> "4:3"
    }

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }

private val SubtitleBackground.label: String
    get() = when (this) {
        SubtitleBackground.NONE -> "无背景"
        SubtitleBackground.SEMI_TRANSPARENT -> "半透明"
        SubtitleBackground.SOLID -> "纯色"
    }

private val SubtitleTextColor.label: String
    get() = when (this) {
        SubtitleTextColor.WHITE -> "白色"
        SubtitleTextColor.YELLOW -> "黄色"
        SubtitleTextColor.CYAN -> "青色"
    }
