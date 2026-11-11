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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!entry.isDirectory && entry.isVideo) {
                        DropdownMenuItem(
                            text = { Text("视频详情") },
                            onClick = { menuExpanded = false; onDetails() },
                        )
                    }
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("导出") },
                            onClick = { menuExpanded = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("分享") },
                            onClick = { menuExpanded = false; onShare() },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("移动") },
                        onClick = { menuExpanded = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        },
    )
}
