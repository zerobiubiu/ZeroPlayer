package com.privateplayer.feature.importer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.domain.repository.WebTransferRecord
import com.privateplayer.domain.repository.WebTransferState
import com.privateplayer.domain.repository.WebUploadStatus

/** 临时文件服务的控制面板；关闭面板不会中断仍在运行的服务。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiImportSheet(
    directory: String,
    onDismiss: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.webUploadState.collectAsStateWithLifecycle()
    val importState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.importPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionError by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionError = null
            viewModel.startWebUpload(directory)
        } else {
            permissionError = "未获得局域网权限，请在系统设置中允许后重试。"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("通过 Wi-Fi 导入", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = when (state.status) {
                        WebUploadStatus.STOPPED -> "服务已停止"
                        WebUploadStatus.STARTING -> "正在启动服务"
                        WebUploadStatus.RUNNING -> "服务运行中"
                        WebUploadStatus.ERROR -> "服务启动失败"
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        "初始目录：/${(if (state.status == WebUploadStatus.RUNNING || state.status == WebUploadStatus.STARTING) state.initialPath else directory).trim('/')}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "可用空间 ${formatBytes(importState.usage.freeBytes)} · 端口 8765",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.address?.takeIf { state.status == WebUploadStatus.RUNNING }?.let { address ->
                        SelectionContainer {
                            Text(address, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi 导入地址", address))
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, "地址已复制", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("复制地址") }
                    }
                    val message = permissionError ?: state.message
                    if (!message.isNullOrBlank()) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (permissionError != null || state.status == WebUploadStatus.ERROR) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    when (state.status) {
                        WebUploadStatus.STOPPED, WebUploadStatus.ERROR -> Button(onClick = {
                            permissionError = null
                            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            } else {
                                viewModel.startWebUpload(directory)
                            }
                        }) { Text("启动服务") }
                        WebUploadStatus.STARTING, WebUploadStatus.RUNNING -> OutlinedButton(onClick = viewModel::stopWebUpload) {
                            Text("停止服务")
                        }
                    }
                    Text(
                        "在同一网络的浏览器中打开地址，可上传、下载和管理文件。服务没有登录或访问令牌，能访问该地址的设备均可读写文件；传输完成后请停止服务。关闭此面板不会停止服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                Text("导入设置", style = MaterialTheme.typography.titleMedium)
            }
            item {
                ImportOption(
                    title = "生成视频缩略图",
                    checked = preferences.generateThumbnails,
                    onCheckedChange = viewModel::setGenerateThumbnails,
                )
            }
            item {
                ImportOption(
                    title = "检测重复视频",
                    checked = preferences.duplicateDetection,
                    onCheckedChange = viewModel::setDuplicateDetection,
                )
            }
            item { HorizontalDivider() }
            item {
                Text("本次上传", style = MaterialTheme.typography.titleMedium)
            }
            if (state.transfers.isEmpty()) {
                item {
                    Text(
                        if (state.status == WebUploadStatus.RUNNING) "等待上传文件" else "启动服务后显示本次上传记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.transfers, key = { it.id }) { transfer ->
                    TransferRow(
                        transfer = transfer,
                        onRetry = { transfer.taskId?.let(viewModel::retry) },
                        onCancel = { transfer.taskId?.let(viewModel::cancel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportOption(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun TransferRow(transfer: WebTransferRecord, onRetry: () -> Unit, onCancel: () -> Unit) {
    val stateLabel = when (transfer.state) {
        WebTransferState.RECEIVING -> "接收中"
        WebTransferState.IMPORTING -> "正在保存"
        WebTransferState.COMPLETED -> "已完成"
        WebTransferState.FAILED -> "失败"
        WebTransferState.CANCELED -> "已取消"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(transfer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val size = if (transfer.state == WebTransferState.RECEIVING) {
            "${formatBytes(transfer.receivedBytes)} / ${formatBytes(transfer.sizeBytes)}"
        } else {
            formatBytes(transfer.sizeBytes)
        }
        Text(
            listOfNotNull(stateLabel, size, transfer.message?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (transfer.state == WebTransferState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (transfer.state == WebTransferState.RECEIVING && transfer.sizeBytes > 0L) {
            LinearProgressIndicator(
                progress = { (transfer.receivedBytes.toDouble() / transfer.sizeBytes).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (transfer.state == WebTransferState.IMPORTING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (transfer.taskId != null && transfer.state == WebTransferState.FAILED) {
            TextButton(onClick = onRetry) { Text("重试保存") }
        } else if (transfer.taskId != null && transfer.state == WebTransferState.IMPORTING) {
            TextButton(onClick = onCancel) { Text("取消保存") }
        }
    }
}
