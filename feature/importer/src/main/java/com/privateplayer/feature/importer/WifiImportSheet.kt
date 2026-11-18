package com.privateplayer.feature.importer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.design.R as DesignR
import com.privateplayer.core.design.components.SwitchRow
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.repository.WebTransferRecord
import com.privateplayer.domain.repository.WebTransferState
import com.privateplayer.domain.repository.WebUploadEndpoint
import com.privateplayer.domain.repository.WebUploadNetwork
import com.privateplayer.domain.repository.WebUploadStatus
import kotlinx.coroutines.delay

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
    val active = state.status == WebUploadStatus.STARTING || state.status == WebUploadStatus.RUNNING
    val endpoints = if (state.status == WebUploadStatus.RUNNING) state.endpoints else emptyList()
    val networkCounts = remember(endpoints) { endpoints.groupingBy { it.kind }.eachCount() }
    val inset = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal)

    // 恢复入口取决于系统授权，而不是上一次请求的回调；重开面板或进程后仍可去设置。
    var hasPermission by remember(context) { mutableStateOf(context.hasLocalNetworkPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasLocalNetworkPermission()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.startWebUpload(directory)
    }

    val clipLabel = stringResource(R.string.wifi_clip_label)
    var copiedUrl by remember(state.status, endpoints) { mutableStateOf<String?>(null) }
    var copyCount by remember { mutableIntStateOf(0) }
    val feedbackTimeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = 2_000L, containsText = true,
    ) ?: 2_000L
    LaunchedEffect(copiedUrl, copyCount) {
        if (copiedUrl != null) {
            delay(feedbackTimeout)
            copiedUrl = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Column(modifier = inset, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.wifi_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (active) {
                        // 停止入口不跟着网卡数量下移；启动过程中同样可以取消监听。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceStatus(state.status, Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                copiedUrl = null
                                viewModel.stopWebUpload()
                            }) { Text(stringResource(R.string.wifi_stop)) }
                        }
                        WifiRiskNotice()
                    } else {
                        ServiceStatus(state.status)
                        TransferDestination(directory, importState.usage.freeBytes)
                        WifiRiskNotice()
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 37 && !context.hasLocalNetworkPermission()) {
                                permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            } else {
                                viewModel.startWebUpload(directory)
                            }
                        }) { Text(stringResource(R.string.wifi_start)) }
                        if (!hasPermission) PermissionRecovery()
                    }
                    state.message?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.status == WebUploadStatus.ERROR) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            if (endpoints.isNotEmpty()) {
                item { SectionTitle(R.string.wifi_browser_addresses, inset) }
                items(endpoints, key = { it.url }) { endpoint ->
                    EndpointRow(
                        endpoint = endpoint,
                        showInterface = networkCounts.getValue(endpoint.kind) > 1,
                        copied = copiedUrl == endpoint.url,
                        modifier = inset,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, endpoint.url))
                            copiedUrl = endpoint.url
                            copyCount++
                        },
                    )
                }
            }
            if (active) {
                item {
                    Column(modifier = inset, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TransferDestination(state.initialPath, importState.usage.freeBytes)
                        Text(
                            stringResource(R.string.wifi_usage_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column {
                    HorizontalDivider(modifier = inset)
                    SectionTitle(R.string.wifi_import_settings, inset.padding(top = Spacing.md))
                    SwitchRow(
                        title = stringResource(DesignR.string.design_import_thumbnails),
                        description = null,
                        checked = preferences.generateThumbnails,
                        onCheckedChange = viewModel::setGenerateThumbnails,
                    )
                    SwitchRow(
                        title = stringResource(DesignR.string.design_import_duplicates),
                        description = null,
                        checked = preferences.duplicateDetection,
                        onCheckedChange = viewModel::setDuplicateDetection,
                    )
                }
            }
            if (state.transfers.isNotEmpty()) {
                item { SectionTitle(R.string.wifi_uploads_title, inset) }
                items(state.transfers, key = { it.id }) { transfer ->
                    TransferRow(
                        transfer = transfer,
                        modifier = inset,
                        onRetry = { transfer.taskId?.let(viewModel::retry) },
                        onCancel = { transfer.taskId?.let(viewModel::cancel) },
                    )
                }
            } else if (state.status == WebUploadStatus.RUNNING) {
                item {
                    Text(
                        stringResource(R.string.wifi_uploads_waiting),
                        modifier = inset,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Context.hasLocalNetworkPermission(): Boolean =
    Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_LOCAL_NETWORK,
    ) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionRecovery() {
    val context = LocalContext.current
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            stringResource(if (settingsUnavailable) R.string.wifi_permission_manual else R.string.wifi_permission_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (!settingsUnavailable) {
            TextButton(onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    settingsUnavailable = true
                } catch (_: SecurityException) {
                    settingsUnavailable = true
                }
            }) { Text(stringResource(R.string.wifi_go_settings)) }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int, modifier: Modifier) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

@Composable
private fun ServiceStatus(status: WebUploadStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        WebUploadStatus.STOPPED -> R.string.wifi_status_stopped
        WebUploadStatus.STARTING -> R.string.wifi_status_starting
        WebUploadStatus.RUNNING -> R.string.wifi_status_running
        WebUploadStatus.ERROR -> R.string.wifi_status_error
    }
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun TransferDestination(path: String, freeBytes: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.wifi_initial_directory, path.trim('/')), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.wifi_space_port, formatBytes(freeBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** F-09：风险紧邻启停，文字与图标共同表达，不依赖警示色。 */
@Composable
private fun WifiRiskNotice() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
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
private fun EndpointRow(
    endpoint: WebUploadEndpoint,
    showInterface: Boolean,
    copied: Boolean,
    modifier: Modifier,
    onCopy: () -> Unit,
) {
    val kind = stringResource(
        when (endpoint.kind) {
            WebUploadNetwork.WIFI -> R.string.wifi_network_wifi
            WebUploadNetwork.ETHERNET -> R.string.wifi_network_ethernet
            WebUploadNetwork.HOTSPOT -> R.string.wifi_network_hotspot
        },
    )
    val label = if (showInterface && endpoint.interfaceName.isNotBlank()) {
        stringResource(R.string.wifi_network_interface, kind, endpoint.interfaceName)
    } else kind
    val action = stringResource(R.string.wifi_copy_network_address, label, endpoint.url)
    val copiedLabel = stringResource(R.string.wifi_address_copied)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(endpoint.url, style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(
            onClick = onCopy,
            modifier = Modifier.semantics {
                contentDescription = action
                if (copied) {
                    stateDescription = copiedLabel
                    liveRegion = LiveRegionMode.Polite
                }
            },
        ) { Text(if (copied) copiedLabel else stringResource(R.string.wifi_copy_address)) }
    }
}

@Composable
private fun TransferRow(transfer: WebTransferRecord, modifier: Modifier, onRetry: () -> Unit, onCancel: () -> Unit) {
    val stateLabel = stringResource(
        when (transfer.state) {
            WebTransferState.RECEIVING -> R.string.wifi_transfer_receiving
            WebTransferState.IMPORTING -> R.string.wifi_transfer_importing
            WebTransferState.COMPLETED -> R.string.wifi_transfer_completed
            WebTransferState.FAILED -> R.string.wifi_transfer_failed
            WebTransferState.CANCELED -> R.string.wifi_transfer_canceled
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = modifier) {
        Text(transfer.name, style = MaterialTheme.typography.bodyLarge)
        val size = if (transfer.state == WebTransferState.RECEIVING) {
            stringResource(R.string.wifi_transfer_size_progress, formatBytes(transfer.receivedBytes), formatBytes(transfer.sizeBytes))
        } else {
            formatBytes(transfer.sizeBytes)
        }
        val failure = transfer.failureKind
        val reason = if (failure != null) failure.diagnostic(transfer.message) ?: stringResource(failure.messageRes) else transfer.message
        Text(
            if (reason.isNullOrBlank()) "$stateLabel · $size" else "$stateLabel · $size · $reason",
            style = MaterialTheme.typography.bodySmall,
            color = if (transfer.state == WebTransferState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (transfer.state == WebTransferState.RECEIVING) Modifier
                else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (transfer.state == WebTransferState.RECEIVING && transfer.sizeBytes > 0L) {
            LinearProgressIndicator(
                progress = { (transfer.receivedBytes.toDouble() / transfer.sizeBytes).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (transfer.state == WebTransferState.IMPORTING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (failure?.canRetry == true) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.wifi_retry_save)) }
        } else if (transfer.taskId != null && transfer.state == WebTransferState.IMPORTING) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.wifi_cancel_save)) }
        }
    }
}
