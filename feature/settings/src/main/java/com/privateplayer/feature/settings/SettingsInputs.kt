package com.privateplayer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.core.design.R as DesignR

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
 * 数值设置行（F-26）：紧凑行（标题 + 当前值 + 单位，56–64dp），点击打开编辑对话框。
 *
 * 保存语义与 F-37 一致：对话框内键盘完成或确认时写入合法值；非法值不写入并提示范围；
 * 范围提示仅在聚焦或出错时显示（F-29）；取消/关闭对话框不写入。
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
    var editing by remember { mutableStateOf(false) }
    var lastCommitted by remember(title) { mutableStateOf<Double?>(null) }
    val currentInitial by rememberUpdatedState(initialValue)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val savedValue = remember(currentInitial, minimum, maximum, integerOnly) {
        parseSettingNumber(currentInitial, minimum, maximum, integerOnly)
    }

    /** 写入合法值；与已保存值或上次写入值相同时跳过，避免重复落盘。 */
    fun commit(value: Double) {
        if (value == savedValue || value == lastCommitted) return
        lastCommitted = value
        currentOnCommit(value)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable { editing = true }
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md)
            .testTag("setting-$title"),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = "$initialValue $unit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("setting-value-$title"),
        )
    }

    if (editing) {
        NumericEditDialog(
            title = title,
            initialValue = initialValue,
            unit = unit,
            minimum = minimum,
            maximum = maximum,
            rangeLabel = rangeLabel,
            integerOnly = integerOnly,
            savedValue = savedValue,
            lastCommitted = lastCommitted,
            onCommit = ::commit,
            onDismiss = { editing = false },
        )
    }
}

/**
 * 数值编辑对话框：预填当前值并全选；范围提示仅在聚焦或出错时显示（F-29）。
 * 键盘完成或确认写入合法值；非法值不写入并提示；取消/关闭不写入。
 */
@Composable
private fun NumericEditDialog(
    title: String,
    initialValue: String,
    unit: String,
    minimum: Double,
    maximum: Double,
    rangeLabel: String,
    integerOnly: Boolean,
    savedValue: Double?,
    lastCommitted: Double?,
    onCommit: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(initialValue, TextRange(0, initialValue.length)))
    }
    var focused by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var focusRequested by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val rangeHint = stringResource(R.string.settings_numeric_range_hint, rangeLabel)
    val invalidInputText = stringResource(R.string.settings_numeric_invalid_input, rangeLabel)

    fun tryCommit(): Boolean {
        return when (val edit = resolveNumericEdit(fieldValue.text, savedValue, lastCommitted, minimum, maximum, integerOnly)) {
            is NumericEdit.Commit -> { onCommit(edit.value); true }
            is NumericEdit.Unchanged -> true
            is NumericEdit.Reject -> { errorText = invalidInputText; false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_numeric_edit_value, title)) },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it; errorText = null },
                label = { Text(title) },
                suffix = { Text(unit) },
                singleLine = true,
                isError = errorText != null,
                supportingText = if (focused || errorText != null) {
                    {
                        Text(
                            text = errorText ?: rangeHint,
                            color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (tryCommit()) onDismiss() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned {
                        // 对话框窗口可聚焦后再请求焦点，避免 LaunchedEffect 早于窗口就绪。
                        if (!focusRequested) {
                            focusRequested = true
                            runCatching { focusRequester.requestFocus() }
                        }
                    }
                    .onFocusChanged { focused = it.isFocused }
                    .testTag("setting-edit-$title"),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (tryCommit()) onDismiss() }) {
                Text(stringResource(R.string.settings_numeric_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(DesignR.string.design_cancel)) } },
    )
}
