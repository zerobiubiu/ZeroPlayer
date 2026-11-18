package com.privateplayer.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.R as DesignR
import com.privateplayer.core.design.components.ImmersiveSheetWindowEffect
import com.privateplayer.core.design.components.SubtitleAppearanceActions
import com.privateplayer.core.design.components.SubtitleAppearanceControls
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.model.SubtitlePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleSheet(
    state: PlayerUiState,
    onSelectTrack: (String?) -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onResetDelay: () -> Unit,
    onAppearance: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        ImmersiveSheetWindowEffect()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_subtitle), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAppearance) { Text(stringResource(R.string.player_edit_subtitle_appearance)) }
            }
            if (state.subtitleTracks.isNotEmpty()) {
                SelectionRow(stringResource(R.string.player_subtitle_off), state.selectedSubtitleTrackId == null, onClick = { onSelectTrack(null) })
            }
            state.subtitleTracks.forEach { track ->
                SelectionRow(track.externalFileName?.substringAfterLast('/')?.substringAfterLast('\\') ?: track.label,
                    track.id == state.selectedSubtitleTrackId, maxLines = Int.MAX_VALUE, onClick = { onSelectTrack(track.id) })
            }
            if (state.subtitleTracks.isEmpty()) {
                Text(stringResource(R.string.player_subtitle_none_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.player_subtitle_delay), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.player_subtitle_delay_value, state.subtitleDelayMs), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val step = state.subtitlePrefs.delayStepMs
                    TextButton(onClick = { onAdjustDelay(-step.toLong()) }) { Text(stringResource(R.string.player_subtitle_delay_minus, step)) }
                    TextButton(onClick = { onAdjustDelay(step.toLong()) }) { Text(stringResource(R.string.player_subtitle_delay_plus, step)) }
                    TextButton(onClick = onResetDelay) { Text(stringResource(R.string.player_subtitle_delay_reset)) }
                }
            }
        }
    }
}

/** 从顶部调外观，画面下方的真实字幕不被面板或遮罩盖住。 */
@Composable
internal fun PlayerSubtitleAppearancePanel(
    preferences: SubtitlePreferences, actions: SubtitleAppearanceActions, onBack: () -> Unit, onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(DesignR.string.design_subtitle_appearance)
    BackHandler(onBack = onBack)
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .padding(Spacing.sm), contentAlignment = Alignment.TopCenter) {
        Surface(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight * 0.5f).semantics { paneTitle = title },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(DesignR.string.design_back)) }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDone) { Text(stringResource(R.string.player_appearance_done)) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.sm)) {
                    SubtitleAppearanceControls(preferences, actions)
                }
            }
        }
    }
}
