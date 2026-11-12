package com.privateplayer.media

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privateplayer.core.design.theme.PrivatePlayerTheme
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.model.ThemeMode
import com.privateplayer.feature.detail.MediaDetailScreen
import com.privateplayer.feature.home.HomeScreen
import com.privateplayer.feature.importer.ImportViewModel
import com.privateplayer.feature.importer.WifiImportSheet
import com.privateplayer.feature.player.PlayerScreen
import com.privateplayer.feature.settings.SettingsHubScreen
import com.privateplayer.feature.settings.SettingsSection
import com.privateplayer.feature.settings.SettingsSectionScreen

/** 首页和设置是唯一的一级导航，详情及播放器是二级页面。 */
@Composable
fun PrivatePlayerRoot(
    externalIntents: ExternalIntentViewModel,
    fileTransfers: FileTransferViewModel,
    imports: ImportViewModel,
) {
    val appViewModel: AppViewModel = hiltViewModel()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val importState by imports.uiState.collectAsStateWithLifecycle()
    val showImports by externalIntents.showImports.collectAsStateWithLifecycle()
    val externalNotices by externalIntents.notices.collectAsStateWithLifecycle()
    val readyMediaId by externalIntents.readyMediaId.collectAsStateWithLifecycle()
    val transfer by fileTransfers.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var localDirectory by rememberSaveable { mutableStateOf("") }
    var wifiDirectory by rememberSaveable { mutableStateOf<String?>(null) }
    // 回调中使用的文案在组合期解析，保证配置变化（语言/深色）后取到最新值。
    val exportBusyText = stringResource(R.string.app_export_busy)
    val noDocumentAppText = stringResource(R.string.app_no_document_app)
    val noShareAppText = stringResource(R.string.app_no_share_app)
    val noPickerText = stringResource(R.string.app_no_picker)
    val exportPickerFailedFormat = stringResource(R.string.app_export_picker_failed)
    val shareUiFailedFormat = stringResource(R.string.app_share_ui_failed)
    val pickerFailedFormat = stringResource(R.string.app_picker_failed)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            externalIntents.showImportTasks()
            imports.enqueue(uris.map { it.toString() }, localDirectory)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileTransfers.exportTo(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }

    // 导出走唯一的 SAF 路径：首页与详情页共用同一回调与启动器，不重复实现。
    val exportFile: (path: String, name: String) -> Unit = { path, name ->
        if (transfer.exporting) {
            fileTransfers.reportError(exportBusyText)
        } else {
            fileTransfers.prepareExport(path)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeForName(name)
                putExtra(Intent.EXTRA_TITLE, name)
            }
            try {
                exporter.launch(intent)
            } catch (error: Exception) {
                fileTransfers.exportTo(null)
                fileTransfers.reportError(exportPickerFailedFormat.format(error.message ?: noDocumentAppText))
            }
        }
    }

    val darkTheme = when (settings.display.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    PrivatePlayerTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val currentRoute = entry?.destination?.route
        val showBottomBar = currentRoute == Routes.HOME || currentRoute == Routes.SETTINGS

        LaunchedEffect(readyMediaId) {
            readyMediaId?.let { mediaId ->
                externalIntents.dismissImportTasks()
                navController.navigate(Routes.player(mediaId)) { launchSingleTop = true }
                externalIntents.consumeReadyMedia()
            }
        }
        LaunchedEffect(transfer.shareIntent) {
            transfer.shareIntent?.let { intent ->
                try {
                    context.startActivity(intent)
                } catch (error: Exception) {
                    fileTransfers.reportError(shareUiFailedFormat.format(error.message ?: noShareAppText))
                } finally {
                    fileTransfers.consumeShare()
                }
            }
        }

        Scaffold(
            bottomBar = {
                if (showBottomBar) RootBottomBar(navController, currentRoute)
            },
            floatingActionButton = {
                val hasTasks = importState.activeTasks.isNotEmpty() ||
                    importState.finishedTasks.any { it.state == ImportTaskState.FAILED }
                if (currentRoute == Routes.HOME && hasTasks && !showImports) {
                    ExtendedFloatingActionButton(
                        onClick = externalIntents::showImportTasks,
                        icon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
                        text = { Text(stringResource(R.string.app_import_tasks)) },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = if (showBottomBar) Modifier.padding(padding).consumeWindowInsets(padding) else Modifier,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenMedia = { navController.navigate(Routes.detail(it)) },
                        onPlayMedia = { navController.navigate(Routes.player(it)) },
                        onOpenLocalImport = { directory ->
                            localDirectory = directory
                            try {
                                picker.launch(arrayOf("*/*"))
                            } catch (error: Exception) {
                                fileTransfers.reportError(pickerFailedFormat.format(error.message ?: noPickerText))
                            }
                        },
                        onOpenWifiImport = { directory -> wifiDirectory = directory },
                        onExportFile = exportFile,
                        onShareFile = fileTransfers::share,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsHubScreen(
                        onOpenSection = { section ->
                            navController.navigate(Routes.settingsSection(section.id)) { launchSingleTop = true }
                        },
                    )
                }
                composable(
                    route = Routes.SETTINGS_SECTION,
                    arguments = listOf(navArgument("section") { type = NavType.StringType }),
                ) { entry ->
                    val section = SettingsSection.fromId(entry.arguments?.getString("section"))
                        ?: SettingsSection.PLAYBACK
                    SettingsSectionScreen(section = section, onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
                ) {
                    MediaDetailScreen(
                        onBack = { navController.popBackStack() },
                        onPlay = { navController.navigate(Routes.player(it)) },
                        onExportFile = exportFile,
                        onShareFile = fileTransfers::share,
                    )
                }
                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                    ),
                ) { PlayerScreen(onBack = { navController.popBackStack() }) }
            }
        }

        wifiDirectory?.let { directory ->
            WifiImportSheet(directory = directory, onDismiss = { wifiDirectory = null }, viewModel = imports)
        }
        if (showImports) {
            ImportTasksDialog(
                state = importState,
                notices = externalNotices,
                onDismiss = {
                    externalIntents.dismissImportTasks()
                    imports.dismissNotices()
                },
                onCancel = { id -> externalIntents.forgetTask(id); imports.cancel(id) },
                onRetry = imports::retry,
                onRemove = { id -> externalIntents.forgetTask(id); imports.remove(id) },
            )
        }
        if (transfer.exporting) ExportProgressDialog(transfer, fileTransfers::cancelExport)
        else transfer.message?.let { message ->
            AlertDialog(
                onDismissRequest = fileTransfers::dismissMessage,
                title = { Text(stringResource(R.string.app_transfer_dialog_title)) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = fileTransfers::dismissMessage) { Text(stringResource(R.string.app_transfer_dismiss)) } },
            )
        }
    }
}

@Composable
private fun RootBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.HOME,
            onClick = { navController.navigateToTopLevel(Routes.HOME) },
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.app_nav_home)) },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { navController.navigateToTopLevel(Routes.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.app_nav_settings)) },
        )
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
