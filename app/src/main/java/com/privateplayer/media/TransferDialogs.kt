package com.privateplayer.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.feature.importer.ImportUiState

@Composable
internal fun ImportTasksDialog(
    state: ImportUiState,
    notices: List<String>,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val tasks = state.activeTasks + state.finishedTasks
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_import_tasks_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val allNotices = notices + state.notices
                if (allNotices.isNotEmpty()) {
                    item { Text(allNotices.joinToString("\n"), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }
                if (state.enqueuing) item { Text(stringResource(R.string.app_import_creating)) }
                if (tasks.isEmpty() && !state.enqueuing) item { Text(stringResource(R.string.app_import_empty)) }
                items(tasks, key = ImportTask::id) { task ->
                    ImportTaskRow(task, onCancel, onRetry, onRemove)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_import_close)) } },
    )
}

@Composable
private fun ImportTaskRow(
    task: ImportTask,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(task.displayName, style = MaterialTheme.typography.titleSmall)
        Text(
            when (task.state) {
                ImportTaskState.QUEUED -> stringResource(R.string.app_task_queued)
                ImportTaskState.RUNNING -> stringResource(R.string.app_task_running, formatBytes(task.bytesCopied))
                ImportTaskState.COMMITTING -> stringResource(R.string.app_task_committing)
                ImportTaskState.COMPLETED -> stringResource(R.string.app_task_completed)
                ImportTaskState.CANCELED -> stringResource(R.string.app_task_canceled)
                ImportTaskState.FAILED -> stringResource(R.string.app_task_failed)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        task.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (task.state == ImportTaskState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (task.state.isActive) {
            val progress = task.progress
            if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        Row {
            if (task.state.isActive) TextButton(onClick = { onCancel(task.id) }) { Text(stringResource(R.string.app_task_cancel)) }
            if (task.state == ImportTaskState.FAILED) TextButton(onClick = { onRetry(task.id) }) { Text(stringResource(R.string.app_task_retry)) }
            if (!task.state.isActive) TextButton(onClick = { onRemove(task.id) }) { Text(stringResource(R.string.app_task_remove)) }
        }
    }
}

@Composable
internal fun ExportProgressDialog(state: FileTransferState, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.app_exporting_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_exporting_progress, formatBytes(state.copied), formatBytes(state.total)))
                if (state.total > 0L) {
                    LinearProgressIndicator(
                        progress = { (state.copied.toFloat() / state.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.app_export_cancel)) } },
    )
}
