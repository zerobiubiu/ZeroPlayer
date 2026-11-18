package com.privateplayer.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.R
import com.privateplayer.core.design.theme.Spacing

/** 整行接收点击，弹窗只锚定在末端值和图标的实际边界。 */
@Composable
fun SettingValueRow(
    title: String,
    value: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    role: Role = Role.Button,
    popup: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth().testTag("setting-$title")
            .clickable(role = role, onClickLabel = actionLabel, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // 外层给长文本保留换行空间；内层紧包内容，不能把占满一列的容器当作弹窗锚点。
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        value, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f, fill = false).testTag("setting-value-$title"),
                    )
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("setting-affordance-$title"))
                }
                popup()
            }
        }
    }
}

@Composable
fun <T> SettingsDropdownRow(
    title: String,
    value: T,
    options: List<T>,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingValueRow(title, stringResource(labelRes(value)), Icons.Filled.ArrowDropDown,
        actionLabel = stringResource(R.string.design_expand_options, title),
        onClick = { expanded = true }, role = Role.DropdownList,
    ) {
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isCurrent = option == value
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes(option))) },
                    modifier = Modifier.semantics { selected = isCurrent },
                    trailingIcon = {
                        if (isCurrent) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        expanded = false
                        if (!isCurrent) onSelect(option)
                    },
                )
            }
        }
    }
}
