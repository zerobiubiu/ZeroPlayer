package com.privateplayer.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.design.components.EmptyState
import com.privateplayer.core.design.components.ConfirmDialog
import com.privateplayer.core.design.components.SelectionTopBar
import com.privateplayer.core.design.components.AppTopBar
import com.privateplayer.core.design.components.DeleteDialog
import com.privateplayer.core.design.components.MoveDialog
import com.privateplayer.core.design.components.NameDialog
import com.privateplayer.core.design.components.ErrorState
import com.privateplayer.core.design.components.FileAction
import com.privateplayer.core.design.components.LoadingState
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.core.design.R as DesignR
import com.privateplayer.domain.repository.FileTreeEntry
import kotlinx.coroutines.flow.collect

/** 首页与网页共享私有文件树，播放进度等信息由数据层维护。 */
@Composable
fun HomeScreen(
    onOpenMedia: (Long) -> Unit,
    onPlayMedia: (Long) -> Unit,
    onOpenLocalImport: (String) -> Unit,
    onOpenWifiImport: (String) -> Unit,
    onExportFile: (path: String, name: String) -> Unit,
    onShareFile: (path: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<HomeMessage?>(null) }
    var dialog by remember(state.currentDirectory) { mutableStateOf<HomeDialog?>(null) }
    val selection = remember(state.currentDirectory) { mutableStateMapOf<String, FileTreeEntry>() }
    val selectionMode = selection.isNotEmpty()
    val messageText = pendingMessage?.let { message ->
        val summary = stringResource(message.labelRes(), *message.formatArgs())
        if (message is HomeMessage.BatchDone && message.result.changedTargets.isNotEmpty()) {
            stringResource(R.string.home_batch_changed_summary, summary, stringResource(R.string.home_delete_target_changed))
        } else summary
    }
    val failuresLabel = stringResource(R.string.home_batch_failures)

    fun onFileAction(entry: FileTreeEntry, action: FileAction) {
        when (action) {
            FileAction.Details -> viewModel.resolveVideo(entry, onOpenMedia)
            FileAction.Export -> onExportFile(entry.path, entry.name)
            FileAction.Share -> onShareFile(entry.path)
            FileAction.Rename -> dialog = HomeDialog.Rename(entry)
            FileAction.Move -> dialog = HomeDialog.Move(entry)
            FileAction.Delete -> dialog = HomeDialog.Delete(entry)
            FileAction.FileInfo -> error("Unexpected home menu action: $action")
        }
    }

    fun handleBatchResult(result: BatchResult) {
        val failures = result.failed.mapTo(mutableSetOf()) { it.first }
        selection.keys.retainAll(failures)
        pendingMessage = HomeMessage.BatchDone(result)
    }

    BackHandler(enabled = selectionMode || state.currentDirectory.isNotEmpty()) {
        if (selectionMode) selection.clear() else viewModel.navigateUp()
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> pendingMessage = message }
    }
    LaunchedEffect(pendingMessage, messageText) {
        messageText?.let {
            val failures = (pendingMessage as? HomeMessage.BatchDone)?.result?.failed.orEmpty()
            val result = snackbarHostState.showSnackbar(it, actionLabel = failuresLabel.takeIf { failures.isNotEmpty() }, withDismissAction = failures.isNotEmpty())
            if (result == SnackbarResult.ActionPerformed && failures.isNotEmpty()) dialog = HomeDialog.BatchFailures(failures)
            pendingMessage = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selectionMode) {
                val single = selection.values.singleOrNull()
                var menuEntry by remember { mutableStateOf<FileTreeEntry?>(null) }
                SelectionTopBar(
                    selectedCount = selection.size,
                    onClose = selection::clear,
                    actions = {
                        IconButton(onClick = {
                            state.entries.forEach { entry -> selection.putIfAbsent(entry.path, entry) }
                        }, enabled = !state.isWorking) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(R.string.home_select_all))
                        }
                        if (single != null) {
                            IconButton(onClick = { dialog = HomeDialog.Rename(single) }, enabled = !state.isWorking) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(DesignR.string.design_action_rename))
                            }
                            Box {
                                IconButton(onClick = { menuEntry = single }, enabled = !state.isWorking) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.home_more))
                                }
                                HomeFileMenu(menuEntry, renameInToolbar = true, onDismiss = { menuEntry = null }, onAction = ::onFileAction)
                            }
                        } else {
                            IconButton(onClick = { dialog = HomeDialog.BatchMove(selection.values.toList()) }, enabled = !state.isWorking) {
                                Icon(Icons.Outlined.DriveFileMove, contentDescription = stringResource(R.string.home_batch_move))
                            }
                            IconButton(onClick = { dialog = HomeDialog.BatchDelete(selection.values.toList()) }, enabled = !state.isWorking) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.home_batch_delete))
                            }
                        }
                    },
                )
            } else {
                HomeTopBar(
                    currentDirectory = state.currentDirectory,
                    enabled = !state.isWorking,
                    onBack = viewModel::navigateUp,
                    onNavigate = viewModel::openDirectory,
                    onCreateDirectory = { dialog = HomeDialog.CreateDirectory(state.currentDirectory) },
                    onLocalImport = { onOpenLocalImport(state.currentDirectory) },
                    onWifiImport = { onOpenWifiImport(state.currentDirectory) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            SnackbarHost(snackbarHostState)
            when {
                state.isLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) { LoadingState(label = stringResource(R.string.home_loading)) }

                state.errorRes != null -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorState(
                        title = stringResource(state.errorRes!!),
                        reason = state.errorReason,
                        onRetry = viewModel::retry,
                        secondaryActionLabel = if (state.currentDirectory.isNotEmpty()) {
                            stringResource(DesignR.string.design_back_parent)
                        } else null,
                        onSecondaryAction = if (state.currentDirectory.isNotEmpty()) viewModel::navigateUp else null,
                    )
                }

                state.entries.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.home_empty_title),
                        actionLabel = if (state.isWorking) null else stringResource(R.string.home_import_files),
                        onAction = if (state.isWorking) null else ({ onOpenLocalImport(state.currentDirectory) }),
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(state.entries, key = FileTreeEntry::path) { entry ->
                        val selected = selection[entry.path]?.let {
                            it.identity == entry.identity && it.isDirectory == entry.isDirectory
                        } == true
                        FileTreeRow(
                            entry = entry,
                            enabled = !state.isWorking,
                            selectionMode = selectionMode,
                            selected = selected,
                            onToggleSelect = {
                                if (selected) selection.remove(entry.path) else selection[entry.path] = entry
                            },
                            onOpen = {
                                when {
                                    entry.isDirectory -> viewModel.openDirectory(entry.path)
                                    entry.isVideo -> viewModel.resolveVideo(entry, onPlayMedia)
                                    else -> viewModel.showUnsupportedFileMessage(entry)
                                }
                            },
                            onAction = ::onFileAction,
                        )
                    }
                }
            }
        }
    }

    when (val active = dialog) {
        is HomeDialog.CreateDirectory -> NameDialog(
            title = stringResource(R.string.home_dialog_create_title),
            initialName = "",
            confirmLabel = stringResource(R.string.home_dialog_create_confirm),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                viewModel.createDirectory(active.parent, name)
            },
        )

        is HomeDialog.Rename -> NameDialog(
            title = stringResource(R.string.home_dialog_rename_title),
            initialName = active.entry.name,
            originalName = active.entry.path.substringAfterLast('/'),
            confirmLabel = stringResource(R.string.home_dialog_rename_confirm),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                selection.clear()
                viewModel.rename(active.entry, name)
            },
        )

        is HomeDialog.Move -> MoveDialog(
            entry = active.entry,
            loadDirectories = viewModel::directoriesForMove,
            onDismiss = { dialog = null },
            onConfirm = { destination ->
                dialog = null
                selection.clear()
                viewModel.move(active.entry, destination)
            },
        )

        is HomeDialog.Delete -> DeleteDialog(
            entry = active.entry,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                selection.clear()
                viewModel.delete(active.entry)
            },
        )

        is HomeDialog.BatchDelete -> ConfirmDialog(
            title = stringResource(R.string.home_batch_delete_title, active.entries.size),
            message = stringResource(R.string.home_batch_delete_message),
            confirmLabel = stringResource(R.string.home_batch_delete),
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.deleteBatch(active.entries, ::handleBatchResult)
            },
            onDismiss = { dialog = null },
        )

        is HomeDialog.BatchMove -> MoveDialog(
            entry = active.entries.first(),
            title = stringResource(R.string.home_batch_move_title, active.entries.size),
            loadDirectories = viewModel::directoriesForMove,
            onDismiss = { dialog = null },
            onConfirm = { destination ->
                dialog = null
                viewModel.moveBatch(active.entries, destination, ::handleBatchResult)
            },
        )

        is HomeDialog.BatchFailures -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(failuresLabel) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(active.failures) { (path, reason) ->
                        Column {
                            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall)
                            Text(reason.ifBlank { stringResource(R.string.home_batch_retry) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.home_close)) } },
        )

        null -> Unit
    }
}

/**
 * 首页顶栏（F-30）：复用 AppTopBar，标题为当前文件夹名（根目录为“文件”），
 * 副标题为祖先路径面包屑（F-01 去重、F-24 紧凑且可点 ≥48dp）。
 */
@Composable
private fun HomeTopBar(
    currentDirectory: String,
    enabled: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onCreateDirectory: () -> Unit,
    onLocalImport: () -> Unit,
    onWifiImport: () -> Unit,
) {
    var importMenuExpanded by remember { mutableStateOf(false) }
    val rootLabel = stringResource(R.string.home_title)
    val title = homeTitle(currentDirectory, rootLabel)
    AppTopBar(
        title = title,
        onBack = if (currentDirectory.isNotEmpty()) onBack else null,
        titleContent = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (currentDirectory.isNotEmpty()) {
                    DirectoryBreadcrumbs(path = currentDirectory, onNavigate = onNavigate)
                }
            }
        },
        actions = {
            IconButton(onClick = onCreateDirectory, enabled = enabled) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.home_new_folder))
            }
            Box {
                IconButton(onClick = { importMenuExpanded = true }, enabled = enabled) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_import_files))
                }
                DropdownMenu(expanded = importMenuExpanded, onDismissRequest = { importMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_import_local)) },
                        onClick = { importMenuExpanded = false; onLocalImport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_import_wifi)) },
                        onClick = { importMenuExpanded = false; onWifiImport() },
                    )
                }
            }
        },
    )
}

/**
 * 祖先路径面包屑（F-24）：紧凑文本行，祖先可点击且热区 ≥48dp；
 * 不含当前文件夹（当前文件夹已是标题，F-01 去重）。根段为“文件”。
 */
@Composable
private fun DirectoryBreadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val rootLabel = stringResource(R.string.home_title)
    val segments = remember(path, rootLabel) { breadcrumbSegments(path, rootLabel) }
    val scroll = rememberScrollState()
    LaunchedEffect(path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        segments.forEachIndexed { index, (name, directory) ->
            if (index > 0) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { onNavigate(directory) }
                    .padding(horizontal = Spacing.xs),
            )
        }
    }
}
