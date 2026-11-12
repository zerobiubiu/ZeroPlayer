package com.privateplayer.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.repository.FileTreeEntry
import kotlinx.coroutines.CancellationException

/** 名称校验（F-21/F-22 共用）：只陈述用户能操作的规则，不出现“空字符”这类术语。 */
fun nameError(name: String): String? = when {
    name.isBlank() -> "请输入名称"
    name == "." || name == ".." -> "名称不能是 . 或 .."
    name.any { it == '/' || it == '\\' || it == '\u0000' } -> "名称不能包含 / 或 \\"
    else -> null
}

/** 是否改变真实文件名；旧文件的可读显示名可能与磁盘文件名不同。 */
fun canConfirmName(name: String, originalName: String?): Boolean =
    nameError(name) == null && (originalName == null || name != originalName)

/** 单行名称对话框：重命名与新建目录共用；非法名称禁用确认并给出行内提示。 */
@Composable
fun NameDialog(
    title: String,
    initialName: String,
    originalName: String? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var edited by remember(initialName) { mutableStateOf(false) }
    val error = nameError(name)
    val canConfirm = canConfirmName(name, originalName)
    val confirm = { if (canConfirm) onConfirm(name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; edited = true },
                modifier = Modifier.fillMaxWidth().testTag("名称输入"),
                label = { Text("名称") },
                singleLine = true,
                isError = edited && error != null,
                supportingText = if (edited && error != null) ({ Text(error) }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canConfirm, modifier = Modifier.testTag("名称确认")) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 删除确认：点名对象、说明不可撤销，红色删除按钮，单次确认。 */
@Composable
fun DeleteDialog(entry: FileTreeEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        title = if (entry.isDirectory) "删除目录？" else "删除文件？",
        message = if (entry.isDirectory) {
            "将永久删除“${entry.name}”及其中的全部内容，此操作无法撤销。"
        } else {
            "将永久删除“${entry.name}”，此操作无法撤销。"
        },
        confirmLabel = "删除",
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

private sealed interface MoveListing {
    val path: String
    data class Loading(override val path: String) : MoveListing
    data class Success(override val path: String, val directories: List<FileTreeEntry>) : MoveListing
    data class Failure(override val path: String) : MoveListing
}

/** 移动目标选择器：只能进入真实存在的目录，确认按钮在目标与原目录相同时禁用。 */
@Composable
fun MoveDialog(
    entry: FileTreeEntry,
    loadDirectories: suspend (String, FileTreeEntry) -> List<FileTreeEntry>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val originalParent = remember(entry.path) { entry.path.substringBeforeLast('/', "") }
    var destination by remember(entry.path) { mutableStateOf(originalParent) }
    var refresh by remember(entry.path) { mutableIntStateOf(0) }
    var listing by remember(entry.path) { mutableStateOf<MoveListing>(MoveListing.Loading(originalParent)) }

    LaunchedEffect(destination, refresh) {
        val path = destination
        listing = MoveListing.Loading(path)
        listing = try {
            MoveListing.Success(path, loadDirectories(path, entry))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            MoveListing.Failure(path)
        }
    }
    val currentListing = listing.takeIf { it.path == destination } ?: MoveListing.Loading(destination)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动“${entry.name}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("选择目标目录", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (destination.isEmpty()) "当前位置：文件" else "当前位置：文件/$destination",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (destination.isNotEmpty()) {
                    TextButton(onClick = { destination = destination.substringBeforeLast('/', "") }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                        Text("返回上级目录", modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    when (currentListing) {
                        is MoveListing.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(Spacing.md))
                            Text("正在读取目录…", modifier = Modifier.padding(Spacing.md))
                        }

                        is MoveListing.Failure -> {
                            Text("无法读取此目录，请重试。", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { refresh++ }) { Text("重试") }
                        }

                        is MoveListing.Success -> {
                            if (currentListing.directories.isEmpty()) {
                                Text("没有可进入的子目录", modifier = Modifier.padding(vertical = Spacing.md))
                            }
                            currentListing.directories.forEach { directory ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                                        .clickable { destination = directory.path }
                                        .padding(vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(directory.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(destination) },
                enabled = currentListing is MoveListing.Success && destination != originalParent,
                modifier = Modifier.testTag("移动确认"),
            ) { Text("移动到此处") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
