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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.FileActionMenuContent
import com.privateplayer.core.design.components.rememberFileActionMenuGroups
import com.privateplayer.domain.repository.FileTreeEntry

/** 长按进入多选或打开菜单；多选态单击切换选中，普通态单击打开。 */
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
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.fillMaxWidth().combinedClickable(
            enabled = enabled,
            role = if (selectionMode) Role.Checkbox else Role.Button,
            onClickLabel = stringResource(
                if (selectionMode) R.string.home_row_toggle_select
                else if (entry.isDirectory) R.string.home_row_open_folder
                else if (entry.isVideo) R.string.home_row_play_video
                else R.string.home_row_open_file,
            ),
            onLongClickLabel = stringResource(if (selectionMode) R.string.home_row_toggle_select else R.string.home_row_menu),
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
        trailingContent = if (selectionMode) null else {
            {
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.home_row_menu_description, entry.name))
                    }
                    // 首页行菜单取共享分组的子集：视频行不含“文件信息”（行菜单里已有“视频详情”入口），
                    // 文件夹与非视频文件不含传输/详情项；长按与三点按钮共用同一构建器。
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
        },
    )
}
