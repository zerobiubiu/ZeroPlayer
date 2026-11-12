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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
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
import com.privateplayer.core.design.R as DesignR
import com.privateplayer.core.design.theme.Spacing
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
    val permissionDeniedText = stringResource(R.string.wifi_permission_denied)
    val clipLabel = stringResource(R.string.wifi_clip_label)
    var permissionError by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionError = null
            viewModel.startWebUpload(directory)
        } else {
            permissionError = permissionDeniedText
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
                Text(stringResource(R.string.wifi_title), style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = stringResource(
                        when (state.status) {
                            WebUploadStatus.STOPPED -> R.string.wifi_status_stopped
                            WebUploadStatus.STARTING -> R.string.wifi_status_starting
                            WebUploadStatus.RUNNING -> R.string.wifi_status_running
                            WebUploadStatus.ERROR -> R.string.wifi_status_error
                        },
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        stringResource(
                            R.string.wifi_initial_directory,
                            (if (state.status == WebUploadStatus.RUNNING || state.status == WebUploadStatus.STARTING) state.initialPath else directory).trim('/'),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.wifi_space_port, formatBytes(importState.usage.freeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.address?.takeIf { state.status == WebUploadStatus.RUNNING }?.let { address ->
                        SelectionContainer {
                            Text(address, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(clipLabel, address),
                            )
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, R.string.wifi_address_copied, Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.wifi_copy_address)) }
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

                    // F-09：无鉴权风险独立成块，紧邻启动按钮；图标 + 文字，不只靠颜色。
                    WifiRiskNotice()

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
                        }) { Text(stringResource(R.string.wifi_start)) }
                        WebUploadStatus.STARTING, WebUploadStatus.RUNNING -> OutlinedButton(onClick = viewModel::stopWebUpload) {
                            Text(stringResource(R.string.wifi_stop))
                        }
                    }
                    Text(
                        stringResource(R.string.wifi_usage_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                Text(stringResource(R.string.wifi_import_settings), style = MaterialTheme.typography.titleMedium)
            }
            item {
                ImportOption(
                    title = stringResource(DesignR.string.design_import_thumbnails),
                    checked = preferences.generateThumbnails,
                    onCheckedChange = viewModel::setGenerateThumbnails,
                )
            }
            item {
                ImportOption(
                    title = stringResource(DesignR.string.design_import_duplicates),
                    checked = preferences.duplicateDetection,
                    onCheckedChange = viewModel::setDuplicateDetection,
                )
            }
            item { HorizontalDivider() }
            item {
                Text(stringResource(R.string.wifi_uploads_title), style = MaterialTheme.typography.titleMedium)
            }
            if (state.transfers.isEmpty()) {
                item {
                    Text(
                        stringResource(
                            if (state.status == WebUploadStatus.RUNNING) R.string.wifi_uploads_waiting else R.string.wifi_uploads_hint,
                        ),
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

/**
 * 无鉴权风险提示（F-09）。
 *
 * 服务没有登录或访问令牌，同一网络内能访问地址的设备均可读写文件。
 * 该结论独立成块并放在启动按钮旁，使用警示容器色 + 图标 + 文字，
 * 浅色与深色下均可读；不使用营销或恐吓语言，只陈述事实与使用建议。
 */
@Composable
private fun WifiRiskNotice() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(Spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wifi_risk_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.wifi_risk_detail),
                    style = MaterialTheme.typography.bodySmall,
                )
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
    val stateLabel = stringResource(
        when (transfer.state) {
            WebTransferState.RECEIVING -> R.string.wifi_transfer_receiving
            WebTransferState.IMPORTING -> R.string.wifi_transfer_importing
            WebTransferState.COMPLETED -> R.string.wifi_transfer_completed
            WebTransferState.FAILED -> R.string.wifi_transfer_failed
            WebTransferState.CANCELED -> R.string.wifi_transfer_canceled
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(transfer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val size = if (transfer.state == WebTransferState.RECEIVING) {
            stringResource(R.string.wifi_transfer_size_progress, formatBytes(transfer.receivedBytes), formatBytes(transfer.sizeBytes))
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
            TextButton(onClick = onRetry) { Text(stringResource(R.string.wifi_retry_save)) }
        } else if (transfer.taskId != null && transfer.state == WebTransferState.IMPORTING) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.wifi_cancel_save)) }
        }
    }
}
