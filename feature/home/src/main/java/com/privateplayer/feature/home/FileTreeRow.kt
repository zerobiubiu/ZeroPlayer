package com.privateplayer.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.FileActionMenuContent
import com.privateplayer.core.design.components.rememberFileActionMenuGroups
import com.privateplayer.domain.repository.FileTreeEntry

/** 长按和行尾按钮打开同一菜单，保证触屏与辅助设备均可操作。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTreeRow(
    entry: FileTreeEntry,
    enabled: Boolean,
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
        modifier = Modifier.fillMaxWidth().combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = if (entry.isDirectory) "打开目录" else if (entry.isVideo) "播放视频" else "打开文件",
            onLongClickLabel = "打开操作菜单",
            onClick = onOpen,
            onLongClick = { menuExpanded = true },
        ),
        leadingContent = {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(if (entry.isDirectory) "目录" else "文件 · ${formatBytes(entry.sizeBytes)}")
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "${entry.name}的操作菜单")
                }
                // 首页行菜单取共享分组的子集：视频行不含“文件信息”（行菜单里已有“视频详情”入口），
                // 目录与非视频文件不含传输/详情项；长按与三点按钮共用同一构建器。
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
        },
    )
}
