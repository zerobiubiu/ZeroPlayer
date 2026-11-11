package com.privateplayer.data.webupload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 用户显式启动的临时局域网服务；不在后台自行重启。 */
@AndroidEntryPoint
class WebUploadService : Service() {
    @Inject lateinit var controller: WebUploadServiceController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val generation = intent?.getLongExtra(EXTRA_GENERATION, -1L) ?: -1L
        if (generation < 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        val directory = intent?.getStringExtra(EXTRA_DIRECTORY).orEmpty()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "网页上传服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "网页上传开启期间显示服务状态"
            },
        )
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WebUploadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("网页上传已开启")
            .setContentText("仅供可信局域网访问；可在应用中查看地址")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "停止", stop)
            .apply { openApp?.let(::setContentIntent) }
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            controller.startListening(generation, directory) { stopSelf() }
        } catch (error: Exception) {
            controller.onStartFailure(generation, "无法启动前台服务：${error.message ?: "系统限制"}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.onServiceStopped()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GENERATION = "generation"
        const val EXTRA_DIRECTORY = "directory"
        private const val ACTION_STOP = "com.privateplayer.media.STOP_WEB_UPLOAD"
        private const val CHANNEL_ID = "web_upload_service"
        private const val NOTIFICATION_ID = 4212
    }
}
