package com.privateplayer.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.privateplayer.core.design.R
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleFont
import com.privateplayer.domain.model.SubtitleFontStyle
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** 设置与播放器使用同一组外观操作；每次只更改用户编辑的一个属性。 */
class SubtitleAppearanceActions(
    val fontScale: (Float) -> Unit,
    val font: (SubtitleFont) -> Unit,
    val fontStyle: (SubtitleFontStyle) -> Unit,
    val textColor: (SubtitleTextColor) -> Unit,
    val background: (SubtitleBackground) -> Unit,
    val verticalOffset: (Int) -> Unit,
    val overrideAss: (Boolean) -> Unit,
)

@Composable
fun SubtitleAppearanceControls(
    prefs: SubtitlePreferences,
    actions: SubtitleAppearanceActions,
    fontSizeRow: @Composable () -> Unit = {
        SubtitlePercentRow(stringResource(R.string.design_subtitle_size), (prefs.fontSizeScale * 100).roundToInt(),
            60..200, 100, { stringResource(R.string.design_subtitle_percent, it) }, { actions.fontScale(it / 100f) })
    },
) {
    Column {
        fontSizeRow()
        SettingsDropdownRow(stringResource(R.string.design_subtitle_font), prefs.font, SubtitleFont.entries, SubtitleFont::labelRes, actions.font)
        SettingsDropdownRow(stringResource(R.string.design_subtitle_font_style), prefs.fontStyle, SubtitleFontStyle.entries, SubtitleFontStyle::labelRes, actions.fontStyle)
        SettingsDropdownRow(stringResource(R.string.design_subtitle_text_color), prefs.textColor, SubtitleTextColor.entries, SubtitleTextColor::labelRes, actions.textColor)
        SettingsDropdownRow(stringResource(R.string.design_subtitle_background), prefs.background, SubtitleBackground.entries, SubtitleBackground::labelRes, actions.background)
        SubtitlePercentRow(stringResource(R.string.design_subtitle_position), prefs.verticalOffsetPercent, -10..30, 0,
            label = { percent -> when {
                percent > 0 -> stringResource(R.string.design_subtitle_position_up, percent)
                percent < 0 -> stringResource(R.string.design_subtitle_position_down, abs(percent))
                else -> stringResource(R.string.design_subtitle_position_default)
            } }, onCommit = actions.verticalOffset)
        SwitchRow(stringResource(R.string.design_subtitle_override_ass), stringResource(R.string.design_subtitle_format_note),
            prefs.overrideAss, actions.overrideAss)
    }
}

/** 拖动时实时显示候选值，松手即应用；重置键恢复基线，不重置别的属性。 */
@Composable
private fun SubtitlePercentRow(
    title: String, value: Int, range: IntRange, default: Int,
    label: @Composable (Int) -> String, onCommit: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(value.toFloat()) }
    val shown = if (dragging) draft.roundToInt() else value
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(label(shown), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { onCommit(default) }, enabled = shown != default) { Text(stringResource(R.string.design_subtitle_reset)) }
        }
        Slider(value = if (dragging) draft else value.toFloat(), valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChange = { draft = it; dragging = true },
            onValueChangeFinished = { dragging = false; if (draft.roundToInt() != value) onCommit(draft.roundToInt()) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
        )
    }
}
