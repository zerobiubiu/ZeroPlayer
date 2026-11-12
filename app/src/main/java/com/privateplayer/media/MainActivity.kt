package com.privateplayer.media

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.privateplayer.feature.importer.ImportViewModel
import com.privateplayer.feature.player.PlayerVolumeKeyHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 宿主。
 *
 * 所有页面均由 Compose + Navigation 承载；播放器是独立的全屏路由，
 * 以便完全控制沉浸式状态栏、屏幕方向与常亮。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), PlayerVolumeKeyHost {

    private val externalIntents: ExternalIntentViewModel by viewModels()
    private val fileTransfers: FileTransferViewModel by viewModels()
    private val imports: ImportViewModel by viewModels()
    private var playerVolumeKeyHandler: ((KeyEvent) -> Boolean)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // 前台导入通知被拒绝不影响导入本身，只是不可见；此处无需额外处理。
            if (!granted) Unit
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (externalIntents.mustKeepActivity() || fileTransfers.state.value.exporting ||
                    imports.uiState.value.enqueuing || imports.uiState.value.activeTasks.isNotEmpty()
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.app_transfer_busy_toast,
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        requestNotificationPermissionIfNeeded()
        val externalAction = intent?.action in setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
        if (savedInstanceState == null && externalAction) receiveExternalIntent(intent)
        else externalIntents.restorePending()
        setContent {
            PrivatePlayerRoot(externalIntents, fileTransfers, imports)
        }
    }

    override fun setPlayerVolumeKeyHandler(handler: ((KeyEvent) -> Boolean)?) {
        playerVolumeKeyHandler = handler
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            playerVolumeKeyHandler?.invoke(event) == true
        ) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            playerVolumeKeyHandler?.invoke(event) == true
        ) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveExternalIntent(intent)
    }

    private fun receiveExternalIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_VIEW) intent.data?.let(uris::add)
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            // 某些分享应用只填 EXTRA_STREAM，另一些只填 ClipData，也可能同时填写。
            @Suppress("DEPRECATION")
            val stream = runCatching { intent.extras?.get(Intent.EXTRA_STREAM) }.getOrNull()
            when (stream) {
                is Uri -> uris += stream
                is ArrayList<*> -> stream.forEach { if (it is Uri) uris.add(it) }
                is Array<*> -> stream.forEach { if (it is Uri) uris.add(it) }
            }
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
        }
        val distinct = uris.distinct().let { if (action == Intent.ACTION_VIEW) it.take(1) else it }
        externalIntents.receive(distinct.map(Uri::toString), intent.type, action == Intent.ACTION_VIEW)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
