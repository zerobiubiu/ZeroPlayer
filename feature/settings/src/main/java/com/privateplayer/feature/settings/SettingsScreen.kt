package com.privateplayer.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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

/** 设置页：所有开关与选项都直接写入 DataStore，并在页面重进后保持。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings = state.settings


    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "设置", subtitle = "选项即时生效，数值修改后保存")

            LazyColumn(
                contentPadding = PaddingValues(bottom = Spacing.contentBottom),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SettingSection(title = "播放") {
                        Column {
                            SwitchRow(
                                title = "自动播放下一个视频",
                                description = "播放结束后按当前顺序继续播放",
                                checked = settings.playback.autoPlayNext,
                                onCheckedChange = viewModel::setAutoPlayNext,
                            )
                            SwitchRow(
                                title = "记忆播放进度",
                                description = "关闭后不写入断点，仅保留播放历史",
                                checked = settings.playback.rememberProgress,
                                onCheckedChange = viewModel::setRememberProgress,
                            )
                            SwitchRow(
                                title = "打开时自动续播",
                                description = "从上次位置继续播放",
                                checked = settings.playback.autoResume,
                                onCheckedChange = viewModel::setAutoResume,
                            )
                            NumericSettingRow(
                                title = "默认倍速",
                                initialValue = settings.playback.defaultSpeed.toString(),
                                unit = "×", minimum = 0.25, maximum = 4.0,
                                rangeLabel = "0.25～4 倍", integerOnly = false,
                                onSave = { viewModel.setDefaultSpeed(it.toFloat()) },
                            )
                            SettingsDropdownRow(
                                title = "默认画面比例",
                                value = settings.playback.defaultAspect.label,
                                options = VideoAspect.entries.map { it to it.label },
                                onSelect = viewModel::setDefaultAspect,
                            )
                            NumericSettingRow(
                                title = "双击跳转时长",
                                initialValue = settings.playback.seekStepSeconds.toString(),
                                unit = "秒", minimum = 5.0, maximum = 60.0,
                                rangeLabel = "5～60 的整数秒", integerOnly = true,
                                onSave = { viewModel.setSeekStep(it.toInt()) },
                            )
                            NumericSettingRow(
                                title = "长按临时倍速",
                                initialValue = settings.playback.longPressSpeed.toString(),
                                unit = "×", minimum = 1.5, maximum = 4.0,
                                rangeLabel = "1.5～4 倍", integerOnly = false,
                                onSave = { viewModel.setLongPressSpeed(it.toFloat()) },
                            )
                        }
                    }
                }

                item {
                    SettingSection(title = "字幕") {
                        Column {
                            NumericSettingRow(
                                title = "字幕字号",
                                initialValue = (settings.subtitle.fontSizeScale * 100).roundToInt().toString(),
                                unit = "%", minimum = 60.0, maximum = 200.0,
                                rangeLabel = "60～200 的整数百分比", integerOnly = true,
                                onSave = { viewModel.setSubtitleFontScale((it / 100).toFloat()) },
                            )
                            SettingsDropdownRow(
                                title = "字幕背景",
                                value = settings.subtitle.background.label,
                                options = SubtitleBackground.entries.map { it to it.label },
                                onSelect = viewModel::setSubtitleBackground,
                            )
                            SettingsDropdownRow(
                                title = "字幕颜色",
                                value = settings.subtitle.textColor.label,
                                options = SubtitleTextColor.entries.map { it to it.label },
                                onSelect = viewModel::setSubtitleTextColor,
                            )
                            Text(
                                text = "字号、颜色和背景用于普通文本字幕；ASS 等自带样式的字幕保留原排版。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
                            )
                            NumericSettingRow(
                                title = "延迟调节步长",
                                initialValue = settings.subtitle.delayStepMs.toString(),
                                unit = "ms", minimum = 50.0, maximum = 1000.0,
                                rangeLabel = "50～1000 的整数毫秒", integerOnly = true,
                                onSave = { viewModel.setSubtitleDelayStep(it.toInt()) },
                            )
                        }
                    }
                }

                item {
                    SettingSection(title = "手势") {
                        Column {
                            SwitchRow(
                                title = "左侧上下滑动调节亮度",
                                description = null,
                                checked = settings.gesture.brightnessGestureEnabled,
                                onCheckedChange = viewModel::setBrightnessGesture,
                            )
                            SwitchRow(
                                title = "右侧上下滑动调节音量",
                                description = null,
                                checked = settings.gesture.volumeGestureEnabled,
                                onCheckedChange = viewModel::setVolumeGesture,
                            )
                            SwitchRow(
                                title = "横向滑动调节进度",
                                description = null,
                                checked = settings.gesture.seekGestureEnabled,
                                onCheckedChange = viewModel::setSeekGesture,
                            )
                            SwitchRow(
                                title = "长按临时倍速",
                                description = null,
                                checked = settings.gesture.longPressSpeedEnabled,
                                onCheckedChange = viewModel::setLongPressSpeedGesture,
                            )
                        }
                    }
                }

                item {
                    SettingSection(title = "显示") {
                        Column {
                            SettingsDropdownRow(
                                title = "主题",
                                value = settings.display.themeMode.label,
                                options = ThemeMode.entries.map { it to it.label },
                                onSelect = viewModel::setTheme,
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                SwitchRow(
                                    title = "动态取色",
                                    description = "使用系统壁纸生成配色（Android 12+）",
                                    checked = settings.display.dynamicColor,
                                    onCheckedChange = viewModel::setDynamicColor,
                                )
                            }
                        }
                    }
                }

                item {
                    SettingSection(title = "导入") {
                        Column {
                            SwitchRow(
                                title = "导入时生成缩略图",
                                description = "失败不影响导入，只影响列表封面",
                                checked = settings.import.generateThumbnails,
                                onCheckedChange = viewModel::setGenerateThumbnails,
                            )
                            SwitchRow(
                                title = "检测疑似重复视频",
                                description = "按文件大小和首段内容提示，仍会保留副本",
                                checked = settings.import.duplicateDetection,
                                onCheckedChange = viewModel::setDuplicateDetection,
                            )
                        }
                    }
                }

                item {
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

                item {
                    SettingSection(title = "关于") {
                        Column {
                            InfoRow("版本", state.versionName)
                            InfoRow("播放引擎", state.engineLabel)
                            InfoRow("引擎状态", state.engineDetail)
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

