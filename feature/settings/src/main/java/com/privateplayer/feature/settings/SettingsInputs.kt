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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.settings_expand_options, title), tint = MaterialTheme.colorScheme.primary)
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

/** 一次数值编辑的处置结果。 */
internal sealed interface NumericEdit {
    /** 合法且与已保存值不同：需要写入。 */
    data class Commit(val value: Double) : NumericEdit

    /** 非法输入：不写入，由界面提示并恢复。 */
    data object Reject : NumericEdit

    /** 与已保存值或上次写入值相同：无需写入。 */
    data object Unchanged : NumericEdit
}

/**
 * 依据输入文本、仓库中的已保存值与本次会话上次写入值决定处置方式。
 *
 * - 非法输入一律拒绝，不落库；
 * - 与已保存值或上次写入值相同不再重复写；
 * - 其余情况提交新值。
 */
internal fun resolveNumericEdit(
    text: String,
    savedValue: Double?,
    lastWrittenValue: Double?,
    minimum: Double,
    maximum: Double,
    integerOnly: Boolean,
): NumericEdit {
    val parsed = parseSettingNumber(text, minimum, maximum, integerOnly) ?: return NumericEdit.Reject
    if (parsed == savedValue || parsed == lastWrittenValue) return NumericEdit.Unchanged
    return NumericEdit.Commit(parsed)
}

/**
 * 数值设置行：输入完成即生效，与同页开关使用同一保存语义。
 *
 * - 键盘完成或失焦时写入合法值，不再提供逐行“保存”按钮；
 * - 非法输入不写入，键盘完成时提示范围，失焦时恢复为已保存值；
 * - 离开页面时若仍有未提交的合法输入补写一次，保证最后一次修改落盘。
 */
@Composable
internal fun NumericSettingRow(
    title: String,
    initialValue: String,
    unit: String,
    minimum: Double,
    maximum: Double,
    rangeLabel: String,
    integerOnly: Boolean,
    onCommit: (Double) -> Unit,
) {
    var text by remember(title) { mutableStateOf(initialValue) }
    var focused by remember(title) { mutableStateOf(false) }
    var errorText by remember(title) { mutableStateOf<String?>(null) }
    var notice by remember(title) { mutableStateOf<String?>(null) }
    var lastCommitted by remember(title) { mutableStateOf<Double?>(null) }
    val currentInitial by rememberUpdatedState(initialValue)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val focusManager = LocalFocusManager.current
    val rangeHint = stringResource(R.string.settings_numeric_range_hint, rangeLabel)
    val invalidInputText = stringResource(R.string.settings_numeric_invalid_input, rangeLabel)
    val savedValue = remember(currentInitial, minimum, maximum, integerOnly) {
        parseSettingNumber(currentInitial, minimum, maximum, integerOnly)
    }
    val restoredNotice = stringResource(R.string.settings_numeric_restored, currentInitial)

    /** 写入合法值；与已保存值或上次写入值相同时跳过，避免重复落盘。 */
    fun commit(value: Double) {
        if (value == savedValue || value == lastCommitted) return
        lastCommitted = value
        currentOnCommit(value)
    }

    LaunchedEffect(initialValue) {
        if (!focused) text = initialValue
    }

    DisposableEffect(title) {
        onDispose {
            val pending = parseSettingNumber(text, minimum, maximum, integerOnly)
            if (pending != null) commit(pending)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            errorText = null
            notice = null
        },
        label = { Text(title) },
        suffix = { Text(unit) },
        singleLine = true,
        isError = errorText != null,
        supportingText = {
            val hint = errorText ?: notice ?: rangeHint
            Text(
                text = hint,
                color = if (errorText != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                when (val edit = resolveNumericEdit(text, savedValue, lastCommitted, minimum, maximum, integerOnly)) {
                    is NumericEdit.Commit -> {
                        commit(edit.value)
                        focusManager.clearFocus()
                    }

                    is NumericEdit.Unchanged -> focusManager.clearFocus()
                    is NumericEdit.Reject -> errorText = invalidInputText
                }
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs)
            .testTag("setting-$title")
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) return@onFocusChanged
                when (val edit = resolveNumericEdit(text, savedValue, lastCommitted, minimum, maximum, integerOnly)) {
                    is NumericEdit.Commit -> commit(edit.value)
                    is NumericEdit.Unchanged -> Unit
                    is NumericEdit.Reject -> {
                        text = currentInitial
                        errorText = null
                        notice = restoredNotice
                    }
                }
            },
    )
}
