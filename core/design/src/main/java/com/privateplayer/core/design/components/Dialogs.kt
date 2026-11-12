package com.privateplayer.core.design.components

import android.view.View
import android.view.Window
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.privateplayer.core.design.theme.Spacing

/** 二次确认对话框；删除类操作强制走这里。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 单行文本输入对话框（重命名、新建文件夹、新建播放列表）。 */
@Composable
fun InputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onConfirm(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 底部操作面板中一项的语义类型。
 *
 * 面板里混合了单选、可执行动作、二级导航和纯状态展示，
 * 统一用“都带勾选图标”表达会把不同语义混为一谈。
 */
enum class SheetActionKind {
    /** 单选项：仅当前选中项显示勾选标记，并带可选择语义。 */
    SingleChoice,

    /** 可执行动作：执行后即离开面板，不显示任何选中标记。 */
    Action,

    /** 二级导航：打开另一个面板或页面，不显示选中标记。 */
    Navigation,

    /** 状态展示：只说明当前状态，不可点击，不显示选中标记。 */
    Status,
}

/** 底部操作面板中的一项。 */
data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val supportingText: String? = null,
    /** 语义类型：决定是否显示勾选标记与读屏播报。 */
    val kind: SheetActionKind = SheetActionKind.Action,
    /** 单选项是否处于选中状态；仅 [SheetActionKind.SingleChoice] 有效。 */
    val selected: Boolean = false,
) {
    /** 只有单选项在选中时才显示勾选标记。 */
    val showsSelectionMark: Boolean
        get() = kind == SheetActionKind.SingleChoice && selected
}

/**
 * 通用底部操作面板。
 *
 * 面板运行在独立的对话框窗口里；immersive 为真时该窗口也隐藏系统栏，
 * 避免系统状态栏压在播放器画面上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    immersive: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (immersive) ImmersiveSheetWindowEffect()
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                )
            }
            actions.forEach { action ->
                SheetActionRow(action)
            }
        }
    }
}

/**
 * 让面板自己的窗口保持沉浸式。
 *
 * 面板内容运行在独立的对话框窗口中；在 Activity 窗口上隐藏系统栏对它无效，
 * 必须拿到面板自己的窗口再隐藏。
 *
 * 面板内容的 `LocalView` 是对话框根视图的子视图，因此沿父视图链向上查找
 * 实现 [DialogWindowProvider] 的根视图。
 *
 * 供不使用 [ActionSheet] 的自定义面板复用。
 */
@Composable
fun ImmersiveSheetWindowEffect() {
    val view = LocalView.current
    val window = remember(view) { view.findDialogWindow() }
    DisposableEffect(window) {
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** 沿父视图链找到承载当前内容的对话框窗口；不在对话框中时返回 null。 */
private fun View.findDialogWindow(): Window? {
    var current: View? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent as? View
    }
    return null
}

/**
 * 面板中的一行。
 *
 * 按语义类型区分表达，避免所有行都长得像可勾选项：
 * - 单选项：当前项显示勾选并用容器色高亮，读屏播报“已选中”；
 * - 导航项：行尾显示箭头，表示会打开下一级面板；
 * - 动作项：只有图标与文字，不带任何选中标记；
 * - 状态项：不可点击，只陈述当前状态。
 */
@Composable
private fun SheetActionRow(action: SheetAction) {
    val isSelected = action.showsSelectionMark
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        action.destructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = when {
        action.destructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val leadingTint = when {
        action.destructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val interactionModifier = when (action.kind) {
        // 状态项只陈述事实，不注册点击语义，读屏不会把它当成可操作项。
        SheetActionKind.Status -> Modifier
        SheetActionKind.SingleChoice -> Modifier.selectable(
            selected = isSelected,
            enabled = action.enabled,
            role = Role.RadioButton,
            onClick = action.onClick,
        )

        else -> Modifier.clickable(enabled = action.enabled, onClick = action.onClick)
    }

    ListItem(
        headlineContent = { Text(text = action.label, color = contentColor) },
        supportingContent = action.supportingText?.let { text ->
            { Text(text = text, style = MaterialTheme.typography.bodySmall, color = supportingColor) }
        },
        leadingContent = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = leadingTint,
            )
        },
        trailingContent = when {
            isSelected -> {
                {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = leadingTint,
                    )
                }
            }

            action.kind == SheetActionKind.Navigation -> {
                {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(horizontal = Spacing.sm),
    )
}

/** 单选筛选标签行。 */
@Composable
fun FilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(labels) { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/** 设置页开关行。 */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 设置页可点击行（打开选择面板或对话框）。 */
@Composable
fun ClickableSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 关键操作主按钮，用于空状态与详情页。 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text)
    }
}
