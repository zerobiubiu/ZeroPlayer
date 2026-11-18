package com.privateplayer.data.webupload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 用户显式启动的临时局域网服务；不在后台自行重启。 */
@AndroidEntryPoint
class WebUploadService : Service() {
    @Inject lateinit var controller: WebUploadServiceController
    private var servingGeneration = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val generation = intent?.getLongExtra(EXTRA_GENERATION, -1L) ?: -1L
        if (intent?.action == ACTION_STOP) {
            if (controller.stopFromNotification(generation) || !controller.isActive(servingGeneration)) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (!controller.isActive(generation)) {
            if (!controller.isActive(servingGeneration)) stopSelf(startId)
            return START_NOT_STICKY
        }
        servingGeneration = generation
        val directory = intent?.getStringExtra(EXTRA_DIRECTORY).orEmpty()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "网页上传服务", NotificationManager.IMPORTANCE_LOW),
            )
            val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            val stopIntent = Intent(this, WebUploadService::class.java).setAction(ACTION_STOP)
                .setData(Uri.fromParts("webupload", generation.toString(), null))
                .putExtra(EXTRA_GENERATION, generation)
            val stop = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("网页文件服务")
                .setContentText("局域网内无需登录即可读写和删除文件。")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "停止", stop)
                .apply { openApp?.let(::setContentIntent) }
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            controller.startListening(generation, directory) {
                controller.withoutActiveSession { stopSelf() }
            }
        } catch (_: Exception) {
            controller.fail(generation, "无法启动前台服务，请检查系统权限与后台限制")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.fail(servingGeneration, "系统已结束长时间运行的文件服务，请在需要时重新启动")
        stopSelf(startId)
    }

    override fun onDestroy() {
        controller.onServiceStopped(servingGeneration)
        stopForeground(STOP_FOREGROUND_REMOVE)
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
