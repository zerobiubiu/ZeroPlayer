package com.privateplayer.feature.settings

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
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
import com.privateplayer.core.design.R as DesignR
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.domain.model.ThemeMode
import com.privateplayer.domain.model.VideoAspect
import kotlin.math.roundToInt

/** 设置二级页面；一级按任务分组（播放与字幕 / 手势与反馈 / 外观 / 数据与存储 / 关于）。文案全部来自模块资源。 */
enum class SettingsSection(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector,
) {
    PLAYBACK("playback", R.string.settings_section_playback, R.string.settings_section_playback_summary, Icons.Outlined.PlayCircle),
    GESTURE("gesture", R.string.settings_section_gesture, R.string.settings_section_gesture_summary, Icons.Outlined.TouchApp),
    APPEARANCE("appearance", R.string.settings_section_appearance, R.string.settings_section_appearance_summary, Icons.Outlined.Palette),
    DATA("data", R.string.settings_section_data, R.string.settings_section_data_summary, Icons.Outlined.Storage),
    ABOUT("about", R.string.settings_section_about, R.string.settings_section_about_summary, Icons.Outlined.Info),
    ;

    companion object {
        /** 旧路由兼容：display→appearance，import/storage→data，subtitle→playback。 */
        fun fromId(id: String?): SettingsSection? = when (id) {
            "display" -> APPEARANCE
            "import", "storage" -> DATA
            "subtitle" -> PLAYBACK
            else -> entries.firstOrNull { it.id == id }
        }
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
    val messageText = state.messageRes?.let { stringResource(it, *state.messageArgs) }

    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = stringResource(R.string.settings_title))
            LazyColumn(
                contentPadding = PaddingValues(bottom = Spacing.contentBottom),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SettingSection(title = stringResource(R.string.settings_group_preferences)) {
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
    val title = stringResource(section.titleRes)
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
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.settings_enter_section, title),
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
    val messageText = state.messageRes?.let { stringResource(it, *state.messageArgs) }

    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(section.titleRes),
                subtitle = stringResource(section.summaryRes),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.contentBottom),
            ) {
                when (section) {
                    SettingsSection.PLAYBACK -> {
                        PlaybackSettings(state, viewModel)
                        SubtitleSettings(state, viewModel)
                    }
                    SettingsSection.GESTURE -> GestureSettings(state, viewModel)
                    SettingsSection.APPEARANCE -> DisplaySettings(state, viewModel)
                    SettingsSection.DATA -> {
                        ImportSettings(state, viewModel)
                        StorageSettings(state, viewModel)
                    }
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
    SettingSection(title = stringResource(R.string.settings_playback_behavior)) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_auto_play_next),
                description = stringResource(R.string.settings_auto_play_next_desc),
                checked = playback.autoPlayNext,
                onCheckedChange = viewModel::setAutoPlayNext,
            )
            SwitchRow(
                title = stringResource(R.string.settings_remember_progress),
                description = stringResource(R.string.settings_remember_progress_desc),
                checked = playback.rememberProgress,
                onCheckedChange = viewModel::setRememberProgress,
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_resume),
                description = stringResource(R.string.settings_auto_resume_desc),
                checked = playback.autoResume,
                onCheckedChange = viewModel::setAutoResume,
            )
        }
    }
    SettingSection(title = stringResource(R.string.settings_playback_params)) {
        Column {
            NumericSettingRow(
                title = stringResource(R.string.settings_default_speed),
                initialValue = playback.defaultSpeed.toString(),
                unit = stringResource(R.string.settings_unit_times),
                minimum = 0.25, maximum = 4.0,
                rangeLabel = stringResource(R.string.settings_range_speed_025_4), integerOnly = false,
                onCommit = { viewModel.setDefaultSpeed(it.toFloat()) },
            )
            SettingsDropdownRow(
                title = stringResource(R.string.settings_default_aspect),
                value = stringResource(playback.defaultAspect.labelRes),
                options = VideoAspect.entries.map { it to stringResource(it.labelRes) },
                onSelect = viewModel::setDefaultAspect,
            )
            NumericSettingRow(
                title = stringResource(R.string.settings_seek_step),
                initialValue = playback.seekStepSeconds.toString(),
                unit = stringResource(R.string.settings_unit_seconds),
                minimum = 5.0, maximum = 60.0,
                rangeLabel = stringResource(R.string.settings_range_seconds_5_60), integerOnly = true,
                onCommit = { viewModel.setSeekStep(it.toInt()) },
            )
            NumericSettingRow(
                title = stringResource(R.string.settings_long_press_speed),
                initialValue = playback.longPressSpeed.toString(),
                unit = stringResource(R.string.settings_unit_times),
                minimum = 1.5, maximum = 4.0,
                rangeLabel = stringResource(R.string.settings_range_speed_15_4), integerOnly = false,
                onCommit = { viewModel.setLongPressSpeed(it.toFloat()) },
            )
        }
    }
}

@Composable
private fun SubtitleSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val subtitle = state.settings.subtitle
    SettingSection(title = stringResource(R.string.settings_subtitle_style)) {
        Column {
            NumericSettingRow(
                title = stringResource(R.string.settings_subtitle_font_scale),
                initialValue = (subtitle.fontSizeScale * 100).roundToInt().toString(),
                unit = stringResource(R.string.settings_unit_percent),
                minimum = 60.0, maximum = 200.0,
                rangeLabel = stringResource(R.string.settings_range_percent_60_200), integerOnly = true,
                onCommit = { viewModel.setSubtitleFontScale((it / 100).toFloat()) },
            )
            SettingsDropdownRow(
                title = stringResource(R.string.settings_subtitle_background),
                value = stringResource(subtitle.background.labelRes),
                options = SubtitleBackground.entries.map { it to stringResource(it.labelRes) },
                onSelect = viewModel::setSubtitleBackground,
            )
            SettingsDropdownRow(
                title = stringResource(R.string.settings_subtitle_text_color),
                value = stringResource(subtitle.textColor.labelRes),
                options = SubtitleTextColor.entries.map { it to stringResource(it.labelRes) },
                onSelect = viewModel::setSubtitleTextColor,
            )
            Text(
                text = stringResource(R.string.settings_subtitle_style_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
            )
        }
    }
    SettingSection(title = stringResource(R.string.settings_subtitle_sync)) {
        Column {
            NumericSettingRow(
                title = stringResource(R.string.settings_subtitle_delay_step),
                initialValue = subtitle.delayStepMs.toString(),
                unit = stringResource(R.string.settings_unit_ms),
                minimum = 50.0, maximum = 1000.0,
                rangeLabel = stringResource(R.string.settings_range_ms_50_1000), integerOnly = true,
                onCommit = { viewModel.setSubtitleDelayStep(it.toInt()) },
            )
            Text(
                text = stringResource(R.string.settings_subtitle_memory_note),
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
    SettingSection(title = stringResource(R.string.settings_gesture_switches)) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_gesture_brightness),
                description = null,
                checked = gesture.brightnessGestureEnabled,
                onCheckedChange = viewModel::setBrightnessGesture,
            )
            SwitchRow(
                title = stringResource(R.string.settings_gesture_volume),
                description = null,
                checked = gesture.volumeGestureEnabled,
                onCheckedChange = viewModel::setVolumeGesture,
            )
            SwitchRow(
                title = stringResource(R.string.settings_gesture_seek),
                description = stringResource(R.string.settings_gesture_seek_desc),
                checked = gesture.seekGestureEnabled,
                onCheckedChange = viewModel::setSeekGesture,
            )
            SwitchRow(
                title = stringResource(R.string.settings_gesture_long_press),
                description = null,
                checked = gesture.longPressSpeedEnabled,
                onCheckedChange = viewModel::setLongPressSpeedGesture,
            )
        }
    }
    SettingSection(title = stringResource(R.string.settings_gesture_feedback)) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_gesture_haptics),
                description = stringResource(R.string.settings_gesture_haptics_desc),
                checked = gesture.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
            )
        }
    }
}

@Composable
private fun DisplaySettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingSection(title = stringResource(R.string.settings_theme)) {
        Column {
            SettingsDropdownRow(
                title = stringResource(R.string.settings_theme_mode),
                value = stringResource(state.settings.display.themeMode.labelRes),
                options = ThemeMode.entries.map { it to stringResource(it.labelRes) },
                onSelect = viewModel::setTheme,
            )
            Text(
                text = stringResource(R.string.settings_theme_note),
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
    SettingSection(title = stringResource(R.string.settings_import_group)) {
        Column {
            SwitchRow(
                title = stringResource(DesignR.string.design_import_thumbnails),
                description = stringResource(DesignR.string.design_import_thumbnails_desc),
                checked = import.generateThumbnails,
                onCheckedChange = viewModel::setGenerateThumbnails,
            )
            SwitchRow(
                title = stringResource(DesignR.string.design_import_duplicates),
                description = stringResource(DesignR.string.design_import_duplicates_desc),
                checked = import.duplicateDetection,
                onCheckedChange = viewModel::setDuplicateDetection,
            )
        }
    }
}

@Composable
private fun StorageSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingSection(title = stringResource(R.string.settings_storage_management)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(Radius.card),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                InfoRow(
                    stringResource(R.string.settings_storage_files),
                    stringResource(R.string.settings_detail_video_usage_format, state.usage.mediaCount, formatBytes(state.usage.mediaBytes)),
                )
                InfoRow(stringResource(R.string.settings_storage_thumbnail_cache), formatBytes(state.usage.thumbnailBytes))
                InfoRow(stringResource(R.string.settings_storage_temp_files), formatBytes(state.usage.temporaryBytes))
                InfoRow(stringResource(R.string.settings_storage_free), formatBytes(state.usage.freeBytes))
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
                        Text(stringResource(R.string.settings_clear_thumbnails))
                    }
                    OutlinedButton(onClick = viewModel::clearTemporaryFiles) {
                        Text(stringResource(R.string.settings_clear_temp))
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.settings_clear_note),
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
    SettingSection(title = stringResource(R.string.settings_about_version_info)) {
        Column {
            InfoRow(stringResource(R.string.settings_about_version), state.versionName)
            InfoRow(stringResource(R.string.settings_about_engine), state.engineLabel)
            InfoRow(stringResource(R.string.settings_about_engine_status), state.engineDetail)
        }
    }
    SettingSection(title = stringResource(R.string.settings_about_notes)) {
        Column {
            Text(
                text = stringResource(R.string.settings_about_license_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
            Text(
                text = stringResource(R.string.settings_about_product_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
        }
    }
}

/** 一级入口的右侧摘要，进入二级页面前就能看到关键状态。 */
@Composable
private fun sectionDetail(section: SettingsSection, state: SettingsUiState): String = when (section) {
    SettingsSection.PLAYBACK -> listOf(
        stringResource(if (state.settings.playback.autoResume) R.string.settings_detail_auto_resume else R.string.settings_detail_from_start),
        stringResource(R.string.settings_detail_speed_format, state.settings.playback.defaultSpeed.toString()),
        stringResource(R.string.settings_detail_font_scale_format, (state.settings.subtitle.fontSizeScale * 100).roundToInt()),
    ).joinToString(" · ")
    SettingsSection.GESTURE -> listOf(
        stringResource(if (state.settings.gesture.seekGestureEnabled) R.string.settings_detail_seek_on else R.string.settings_detail_seek_off),
        stringResource(if (state.settings.gesture.hapticsEnabled) R.string.settings_detail_haptics_on else R.string.settings_detail_haptics_off),
    ).joinToString(" · ")
    SettingsSection.APPEARANCE -> stringResource(state.settings.display.themeMode.labelRes)
    SettingsSection.DATA -> stringResource(
        R.string.settings_detail_video_usage_format, state.usage.mediaCount, formatBytes(state.usage.mediaBytes),
    )
    SettingsSection.ABOUT -> stringResource(R.string.settings_detail_version_format, state.versionName, state.engineLabel)
}
