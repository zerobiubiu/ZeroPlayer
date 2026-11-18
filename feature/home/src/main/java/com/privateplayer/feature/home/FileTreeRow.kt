package com.privateplayer.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.FileActionMenuContent
import com.privateplayer.core.design.components.rememberFileActionMenuGroups
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.feature.home.fileicons.materialFileIcon

/**
 * 文件行（F-28/F-41）：普通态单击打开、长按进入多选；多选态单击/长按切换选中。
 * 单项操作从选择顶栏的“更多”进入；普通态也保留读屏的直接菜单动作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTreeRow(
    entry: FileTreeEntry,
    enabled: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onAction: (FileTreeEntry, FileAction) -> Unit,
) {
    var menuEntry by remember { mutableStateOf<FileTreeEntry?>(null) }
    val menuLabel = stringResource(R.string.home_row_menu)
    Box {
        ListItem(
            colors = if (selected) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier.fillMaxWidth()
                .semantics {
                    if (selectionMode) {
                        toggleableState = ToggleableState(selected)
                    } else if (enabled) {
                        customActions = listOf(CustomAccessibilityAction(menuLabel) { menuEntry = entry; true })
                    }
                }
                .combinedClickable(
                    enabled = enabled,
                    role = if (selectionMode) Role.Checkbox else Role.Button,
                    onClickLabel = stringResource(
                        if (selectionMode) R.string.home_row_toggle_select
                        else if (entry.isDirectory) R.string.home_row_open_folder
                        else if (entry.isVideo) R.string.home_row_play_video
                        else R.string.home_row_open_file,
                    ),
                    onLongClickLabel = stringResource(
                        if (selectionMode) R.string.home_row_toggle_select else R.string.home_row_select,
                    ),
                    onClick = if (selectionMode) onToggleSelect else onOpen,
                    onLongClick = onToggleSelect,
                ),
            leadingContent = {
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (entry.isDirectory) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val surface = MaterialTheme.colorScheme.surface
                    val drawable = remember(entry, surface) {
                        materialFileIcon(entry.name, entry.path, entry.isVideo, light = surface.luminance() > 0.5f)
                    }
                    Image(
                        painter = painterResource(drawable),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            headlineContent = {
                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = if (entry.isDirectory) null else {
                { Text(stringResource(R.string.home_file_row_format, formatBytes(entry.sizeBytes))) }
            },
            trailingContent = null,
        )
        HomeFileMenu(menuEntry, onDismiss = { menuEntry = null }, onAction = onAction)
    }
}

// 具体文件类型的可用项由共享 fileActionMenuGroups 筛选，首页不重复定义分类规则。
private val homeActions = FileAction.entries.toSet() - FileAction.FileInfo
private val homeOverflowActions = homeActions - FileAction.Rename

@Composable
internal fun HomeFileMenu(
    entry: FileTreeEntry?,
    renameInToolbar: Boolean = false,
    onDismiss: () -> Unit,
    onAction: (FileTreeEntry, FileAction) -> Unit,
) {
    if (entry == null) return
    val groups = rememberFileActionMenuGroups(entry, if (renameInToolbar) homeOverflowActions else homeActions)
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        FileActionMenuContent(groups) { action ->
            onDismiss()
            onAction(entry, action)
        }
    }
}
