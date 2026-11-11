package com.privateplayer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Spacing

/** 文本枚举设置使用锚定行的 Material 3 下拉菜单，不显示勾选清单。 */
@Composable
internal fun <T> SettingsDropdownRow(
    title: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "展开${title}选项", tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(min = 180.dp)) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private val INTEGER_PATTERN = Regex("^[0-9]+$")
private val DECIMAL_PATTERN = Regex("^[0-9]+(?:\\.[0-9]+)?$")

/** 输入只接受有限数字及指定范围，避免被数据层静默截断。 */
internal fun parseSettingNumber(raw: String, minimum: Double, maximum: Double, integerOnly: Boolean): Double? {
    val text = raw.trim().replace(',', '.')
    val pattern = if (integerOnly) INTEGER_PATTERN else DECIMAL_PATTERN
    if (!pattern.matches(text)) return null
    val number = text.toDoubleOrNull() ?: return null
    return number.takeIf { it.isFinite() && it >= minimum && it <= maximum }
}

/** 数值可直接在设置页输入；保存前校验范围与小数格式。 */
@Composable
internal fun NumericSettingRow(
    title: String,
    initialValue: String,
    unit: String,
    minimum: Double,
    maximum: Double,
    rangeLabel: String,
    integerOnly: Boolean,
    onSave: (Double) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    var edited by remember(title, initialValue) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val parsed = remember(value, minimum, maximum, integerOnly) {
        parseSettingNumber(value, minimum, maximum, integerOnly)
    }
    val save: () -> Unit = {
        parsed?.let {
            onSave(it)
            edited = false
            focusManager.clearFocus()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; edited = true },
            label = { Text(title) },
            suffix = { Text(unit) },
            singleLine = true,
            isError = edited && parsed == null,
            supportingText = { Text(if (edited && parsed == null) "请输入$rangeLabel" else "允许范围：$rangeLabel") },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = edited && parsed != null,
            onClick = save,
            modifier = Modifier.semantics { contentDescription = "保存$title" },
        ) { Text("保存") }
    }
}
