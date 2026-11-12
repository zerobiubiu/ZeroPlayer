package com.privateplayer.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.FileActionMenuContent
import com.privateplayer.core.design.components.rememberFileActionMenuGroups
import com.privateplayer.domain.repository.FileTreeEntry

/**
 * 文件行（F-28/F-41）：普通态单击打开、长按进入多选；多选态单击/长按切换选中。
 * 行尾三点不再常驻（降低噪声），操作菜单经读屏自定义操作打开，触屏经详情页更多菜单管理。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTreeRow(
    entry: FileTreeEntry,
    enabled: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    var menuExpanded by remember(entry.path) { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.home_row_menu)
    // 首页行菜单取共享分组的子集：视频行不含“文件信息”，文件夹与非视频文件不含传输/详情项。
    val available = buildSet {
        if (!entry.isDirectory && entry.isVideo) add(FileAction.Details)
        if (!entry.isDirectory) {
            add(FileAction.Export)
            add(FileAction.Share)
        }
        add(FileAction.Rename)
        add(FileAction.Move)
        add(FileAction.Delete)
    }
    val groups = rememberFileActionMenuGroups(entry, available)
    Box {
        ListItem(
            colors = if (selected) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier.fillMaxWidth()
                .semantics {
                    // F-28：隐藏行尾三点后，读屏仍可通过自定义操作打开菜单。
                    customActions = listOf(CustomAccessibilityAction(menuLabel, { menuExpanded = true; true }))
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
                        if (selectionMode) R.string.home_row_toggle_select else R.string.home_row_menu,
                    ),
                    onClick = onOpen,
                    onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
                ),
            leadingContent = {
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        // F-28：菜单不再以行尾三点常驻显示，由读屏自定义操作触发；降低列表右侧噪声。
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            FileActionMenuContent(groups) { action ->
                menuExpanded = false
                when (action) {
                    FileAction.Details -> onDetails()
                    FileAction.Export -> onExport()
                    FileAction.Share -> onShare()
                    FileAction.Rename -> onRename()
                    FileAction.Move -> onMove()
                    FileAction.Delete -> onDelete()
                    FileAction.FileInfo -> Unit
                }
            }
        }
    }
}
