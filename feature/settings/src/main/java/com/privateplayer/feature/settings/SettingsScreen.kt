package com.privateplayer.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.components.AppTopBar
import com.privateplayer.core.design.components.InfoRow
import com.privateplayer.core.design.components.SettingSection
import com.privateplayer.core.design.components.SettingsDropdownRow
import com.privateplayer.core.design.components.SubtitleAppearanceActions
import com.privateplayer.core.design.components.SubtitleAppearanceControls
import com.privateplayer.core.design.components.SwitchRow
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.model.PlaybackProgressMode
import com.privateplayer.domain.model.PlaybackSpeedLimits
import com.privateplayer.core.design.R as DesignR
import com.privateplayer.domain.model.ThemeMode
import com.privateplayer.domain.model.VideoAspect
import kotlin.math.roundToInt

/** 设置按观看、文件和应用分组；路由 ID 不依赖可见标题。 */
enum class SettingsSection(
    val id: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    PLAYBACK("playback", R.string.settings_section_playback, Icons.Outlined.PlayCircle),
    SUBTITLE("subtitle", R.string.settings_section_subtitle, Icons.Outlined.ClosedCaption),
    GESTURE("gesture", R.string.settings_section_gesture, Icons.Outlined.TouchApp),
    IMPORT("import", R.string.settings_section_import, Icons.Outlined.FileDownload),
    STORAGE("storage", R.string.settings_section_storage, Icons.Outlined.Storage),
    APPEARANCE("appearance", R.string.settings_section_appearance, Icons.Outlined.Palette),
    ABOUT("about", R.string.settings_section_about, Icons.Outlined.Info),
    ;

    companion object {
        internal val groups = listOf(
            R.string.settings_group_watch to listOf(PLAYBACK, SUBTITLE, GESTURE),
            R.string.settings_group_files to listOf(IMPORT, STORAGE),
            R.string.settings_group_app to listOf(APPEARANCE, ABOUT),
        )

        fun fromId(id: String?): SettingsSection? = when (id) {
            "display" -> APPEARANCE
            "data" -> STORAGE
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
            SnackbarHost(snackbarHostState)
            LazyColumn(
                contentPadding = PaddingValues(bottom = Spacing.contentBottom),
                modifier = Modifier.fillMaxSize(),
            ) {
                SettingsSection.groups.forEach { (heading, sections) ->
                    item(key = heading) {
                        SettingSection(title = stringResource(heading)) {
                            sections.forEach { section ->
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

    }
}

@Composable
private fun SettingsEntryRow(
    section: SettingsSection,
    detail: String?,
    onClick: () -> Unit,
) {
    val title = stringResource(section.titleRes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.settings_enter_section, title), onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onBack = onBack,
            )
            SnackbarHost(snackbarHostState)
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
                    SettingsSection.APPEARANCE -> DisplaySettings(state, viewModel)
                    SettingsSection.IMPORT -> ImportSettings(state, viewModel)
                    SettingsSection.STORAGE -> StorageSettings(state, viewModel)
                    SettingsSection.ABOUT -> AboutSettings(state)
                }
            }
        }

    }
}

@Composable
private fun PlaybackSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val playback = state.settings.playback
    SettingSection(title = stringResource(R.string.settings_playback_behavior)) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_auto_play_next),
                description = null,
                checked = playback.autoPlayNext,
                onCheckedChange = viewModel::setAutoPlayNext,
            )
            SettingsDropdownRow(
                title = stringResource(R.string.settings_progress_mode),
                value = playback.progressMode,
                options = PlaybackProgressMode.entries,
                labelRes = PlaybackProgressMode::labelRes,
                onSelect = viewModel::setPlaybackProgressMode,
            )
        }
    }
    SettingSection(title = stringResource(R.string.settings_playback_params)) {
        Column {
            NumericSettingRow(
                title = stringResource(R.string.settings_default_speed),
                initialValue = playback.defaultSpeed.toString(),
                unit = stringResource(R.string.settings_unit_times),
                minimum = PlaybackSpeedLimits.MIN.toDouble(), maximum = PlaybackSpeedLimits.MAX.toDouble(),
                rangeLabel = stringResource(R.string.settings_range_speed, PlaybackSpeedLimits.MIN.toString(), PlaybackSpeedLimits.MAX.toInt()), integerOnly = false,
                onCommit = { viewModel.setDefaultSpeed(it.toFloat()) },
            )
            SettingsDropdownRow(
                title = stringResource(R.string.settings_default_aspect),
                value = playback.defaultAspect,
                options = VideoAspect.entries,
                labelRes = VideoAspect::labelRes,
                onSelect = viewModel::setDefaultAspect,
            )
        }
    }
}

@Composable
private fun SubtitleSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val subtitle = state.settings.subtitle
    SettingSection(title = stringResource(R.string.settings_subtitle_style)) {
        SubtitleAppearanceControls(subtitle, remember(viewModel) {
            SubtitleAppearanceActions(viewModel::setSubtitleFontScale, viewModel::setSubtitleFont, viewModel::setSubtitleFontStyle,
                viewModel::setSubtitleTextColor, viewModel::setSubtitleBackground, viewModel::setSubtitleVerticalOffset, viewModel::setSubtitleOverrideAss)
        }) {
            NumericSettingRow(
                title = stringResource(DesignR.string.design_subtitle_size),
                initialValue = (subtitle.fontSizeScale * 100).roundToInt().toString(),
                unit = stringResource(R.string.settings_unit_percent),
                minimum = 60.0, maximum = 200.0,
                rangeLabel = stringResource(R.string.settings_range_percent_60_200), integerOnly = true,
                onCommit = { viewModel.setSubtitleFontScale((it / 100).toFloat()) },
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
        }
    }
}

@Composable
private fun GestureSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val gesture = state.settings.gesture
    val playback = state.settings.playback
    SettingSection(title = stringResource(R.string.settings_gesture_double_tap)) {
            NumericSettingRow(
                title = stringResource(R.string.settings_seek_step),
                initialValue = playback.seekStepSeconds.toString(),
                unit = stringResource(R.string.settings_unit_seconds),
                minimum = 5.0, maximum = 60.0,
                rangeLabel = stringResource(R.string.settings_range_seconds_5_60), integerOnly = true,
                onCommit = { viewModel.setSeekStep(it.toInt()) },
            )
        SettingsNote(stringResource(R.string.settings_gesture_double_tap_zones))
    }
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
        }
    }
    SettingSection(title = stringResource(R.string.settings_gesture_hold)) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_gesture_long_press),
                description = null,
                checked = gesture.longPressSpeedEnabled,
                onCheckedChange = viewModel::setLongPressSpeedGesture,
            )
            NumericSettingRow(
                title = stringResource(R.string.settings_long_press_speed),
                initialValue = playback.longPressSpeed.toString(),
                unit = stringResource(R.string.settings_unit_times),
                minimum = PlaybackSpeedLimits.MIN_TEMPORARY.toDouble(), maximum = PlaybackSpeedLimits.MAX.toDouble(),
                rangeLabel = stringResource(R.string.settings_range_speed, PlaybackSpeedLimits.MIN_TEMPORARY.toString(), PlaybackSpeedLimits.MAX.toInt()), integerOnly = false,
                onCommit = { viewModel.setLongPressSpeed(it.toFloat()) },
            )
        }
    }
    SettingSection(title = stringResource(R.string.settings_gesture_feedback)) {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_gesture_haptics),
                description = null,
                checked = gesture.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
            )
        }
    }
}

@Composable
private fun DisplaySettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsDropdownRow(
        title = stringResource(R.string.settings_theme_mode),
        value = state.settings.display.themeMode,
        options = ThemeMode.entries,
        labelRes = ThemeMode::labelRes,
        onSelect = viewModel::setTheme,
    )
}

@Composable
private fun ImportSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val import = state.settings.import
    SettingSection(title = stringResource(R.string.settings_import_group)) {
        Column {
            SwitchRow(
                title = stringResource(DesignR.string.design_import_thumbnails),
                description = null,
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
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            InfoRow(
                stringResource(R.string.settings_storage_files),
                stringResource(R.string.settings_detail_video_usage_format, state.usage.mediaCount, formatBytes(state.usage.mediaBytes)),
            )
            InfoRow(stringResource(R.string.settings_storage_thumbnail_cache), formatBytes(state.usage.thumbnailBytes))
            InfoRow(stringResource(R.string.settings_storage_temp_files), formatBytes(state.usage.temporaryBytes))
            InfoRow(stringResource(R.string.settings_storage_free), formatBytes(state.usage.freeBytes))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
            SettingsNote(stringResource(R.string.settings_clear_note))
        }
    }
}

@Composable
private fun AboutSettings(state: SettingsUiState) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf<String?>(null) }
    val loadFailedText = stringResource(R.string.settings_about_licenses_load_failed)

    SettingSection(title = stringResource(R.string.settings_about_version_info)) {
        Column {
            InfoRow(stringResource(R.string.settings_about_version), state.versionName)
            InfoRow(stringResource(R.string.settings_about_engine), state.engineLabel)
        }
    }
    SettingSection(title = stringResource(R.string.settings_about_privacy)) {
        Column {
            SettingsNote(stringResource(R.string.settings_about_product_note))
            SettingsNote(stringResource(R.string.settings_about_license_note))
            TextButton(
                onClick = {
                    licenseText = runCatching {
                        context.assets.open("third-party-licenses/NOTICE.txt").bufferedReader().use { it.readText() } +
                            "\n\n" +
                            context.assets.open("third-party-licenses/LICENSE.libmpv-android").bufferedReader().use { it.readText() }
                    }.getOrElse { loadFailedText }
                    showLicenses = true
                },
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            ) { Text(stringResource(R.string.settings_about_licenses)) }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_about_licenses_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = licenseText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(DesignR.string.design_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

/** 一级入口的右侧摘要，进入二级页面前就能看到关键状态。 */
@Composable
private fun sectionDetail(section: SettingsSection, state: SettingsUiState): String? = when (section) {
    SettingsSection.PLAYBACK -> listOf(
        stringResource(state.settings.playback.progressMode.labelRes),
        stringResource(R.string.settings_detail_speed_format, state.settings.playback.defaultSpeed.toString()),
    ).joinToString(" · ")
    SettingsSection.SUBTITLE -> stringResource(R.string.settings_detail_font_scale_format, (state.settings.subtitle.fontSizeScale * 100).roundToInt())
    SettingsSection.GESTURE -> listOf(
        stringResource(if (state.settings.gesture.seekGestureEnabled) R.string.settings_detail_seek_on else R.string.settings_detail_seek_off),
        stringResource(if (state.settings.gesture.hapticsEnabled) R.string.settings_detail_haptics_on else R.string.settings_detail_haptics_off),
    ).joinToString(" · ")
    SettingsSection.APPEARANCE -> stringResource(state.settings.display.themeMode.labelRes)
    SettingsSection.IMPORT -> null
    SettingsSection.STORAGE -> stringResource(
        R.string.settings_detail_video_usage_format, state.usage.mediaCount, formatBytes(state.usage.mediaBytes),
    )
    SettingsSection.ABOUT -> stringResource(R.string.settings_detail_version_format, state.versionName, state.engineLabel)
}
