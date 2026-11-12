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
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.privateplayer.core.design.components.AppTopBar
import com.privateplayer.core.design.components.DeleteDialog
import com.privateplayer.core.design.components.MoveDialog
import com.privateplayer.core.design.components.NameDialog
import com.privateplayer.core.design.components.ErrorState
import com.privateplayer.core.design.components.LoadingState
import com.privateplayer.core.design.theme.Spacing
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
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val messageText = pendingMessage?.let { stringResource(it.labelRes()) }

    BackHandler(enabled = state.currentDirectory.isNotEmpty()) { viewModel.navigateUp() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> pendingMessage = message }
    }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            pendingMessage = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HomeTopBar(
                currentDirectory = state.currentDirectory,
                enabled = !state.isWorking,
                onBack = viewModel::navigateUp,
                onNavigate = viewModel::openDirectory,
                onCreateDirectory = { dialog = HomeDialog.CreateDirectory(state.currentDirectory) },
                onLocalImport = { onOpenLocalImport(state.currentDirectory) },
                onWifiImport = { onOpenWifiImport(state.currentDirectory) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
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
                    ErrorState(message = stringResource(state.errorRes!!), onRetry = viewModel::retry)
                }

                state.entries.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = if (state.isWorking) null else stringResource(R.string.home_new_folder),
                        onAction = if (state.isWorking) null else ({ dialog = HomeDialog.CreateDirectory(state.currentDirectory) }),
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(state.entries, key = FileTreeEntry::path) { entry ->
                        FileTreeRow(
                            entry = entry,
                            enabled = !state.isWorking,
                            onOpen = {
                                when {
                                    entry.isDirectory -> viewModel.openDirectory(entry.path)
                                    entry.isVideo -> viewModel.resolveVideo(entry, onPlayMedia)
                                    else -> viewModel.showUnsupportedFileMessage()
                                }
                            },
                            onDetails = { viewModel.resolveVideo(entry, onOpenMedia) },
                            onRename = { dialog = HomeDialog.Rename(entry) },
                            onMove = { dialog = HomeDialog.Move(entry) },
                            onDelete = { dialog = HomeDialog.Delete(entry) },
                            onExport = { onExportFile(entry.path, entry.name) },
                            onShare = { onShareFile(entry.path) },
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
                viewModel.rename(active.entry, name)
            },
        )

        is HomeDialog.Move -> MoveDialog(
            entry = active.entry,
            loadDirectories = viewModel::directoriesForMove,
            onDismiss = { dialog = null },
            onConfirm = { destination ->
                dialog = null
                viewModel.move(active.entry, destination)
            },
        )

        is HomeDialog.Delete -> DeleteDialog(
            entry = active.entry,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                viewModel.delete(active.entry)
            },
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
