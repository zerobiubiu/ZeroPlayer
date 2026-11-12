package com.privateplayer.core.design.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.core.design.R

/** 文件操作菜单的动作；名称与图标全应用唯一，首页行菜单与详情页更多菜单共用。 */
enum class FileAction(@StringRes val labelRes: Int, val icon: ImageVector) {
    Details(R.string.design_action_details, Icons.Outlined.Info),
    FileInfo(R.string.design_action_file_info, Icons.Outlined.Info),
    Export(R.string.design_action_export, Icons.Outlined.Upload),
    Share(R.string.design_action_share, Icons.Outlined.Share),
    Rename(R.string.design_action_rename, Icons.Outlined.Edit),
    Move(R.string.design_action_move, Icons.Outlined.DriveFileMove),
    Delete(R.string.design_action_delete, Icons.Outlined.Delete),
}

/** 菜单分组：信息 / 传输 / 管理 / 危险。组间用分隔线，不加组标题。 */
data class FileActionGroup(val actions: List<FileAction>)

/**
 * 共享的文件操作菜单定义（F-22）。
 *
 * 分组顺序固定为 信息（视频详情/文件信息）→ 传输（导出/分享）→ 管理（重命名/移动）→ 危险（删除）；
 * 删除始终位于最后一个独立分组。不同页面按 [available] 取子集：
 * 首页视频行不含“文件信息”，详情页不含“视频详情”，文件夹与非视频文件不含传输/详情项。
 */
fun fileActionMenuGroups(entry: FileTreeEntry, available: Set<FileAction>): List<FileActionGroup> {
    fun group(vararg actions: FileAction): FileActionGroup? =
        actions.filter { it in available }.takeIf { it.isNotEmpty() }?.let { FileActionGroup(it) }

    val info = when {
        entry.isDirectory -> null
        entry.isVideo -> group(FileAction.Details, FileAction.FileInfo)
        else -> group(FileAction.FileInfo)
    }
    val transfer = if (entry.isDirectory) null else group(FileAction.Export, FileAction.Share)
    val manage = group(FileAction.Rename, FileAction.Move)
    val danger = group(FileAction.Delete)
    return listOfNotNull(info, transfer, manage, danger)
}

/**
 * 渲染分组菜单内容，供 `DropdownMenu` 使用；长按与三点按钮共用同一构建器。
 * 只放可执行动作，不加组标题与逐项说明；删除用 error 色并独立成组。
 */
@Composable
fun FileActionMenuContent(
    groups: List<FileActionGroup>,
    onAction: (FileAction) -> Unit,
) {
    groups.forEachIndexed { index, group ->
        if (index > 0) HorizontalDivider()
        group.actions.forEach { action ->
            val destructive = action == FileAction.Delete
            val label = stringResource(action.labelRes)
            DropdownMenuItem(
                modifier = Modifier.testTag("文件菜单-$label"),
                leadingIcon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                text = {
                    Text(
                        text = label,
                        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { onAction(action) },
            )
        }
    }
}

/** 按条目类型计算可用动作并缓存分组；页面只需提供动作回调。 */
@Composable
fun rememberFileActionMenuGroups(
    entry: FileTreeEntry,
    available: Set<FileAction>,
): List<FileActionGroup> = remember(entry.path, entry.isDirectory, entry.isVideo, available) {
    fileActionMenuGroups(entry, available)
}
