package com.privateplayer.media.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.CustomCommandPendingIntentBuilder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.PlaybackPendingIntentBuilder
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.session.MpvMedia3Player
import com.privateplayer.feature.player.PlaybackCoordinator
import com.privateplayer.feature.player.PlaybackServiceStarter
import com.privateplayer.media.MainActivity
import com.privateplayer.media.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import java.io.FileDescriptor
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 单一 libmpv 的系统媒体会话；通知内容与媒体按钮由 Media3 原生提供者生成。 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlayerMediaSessionService : MediaSessionService() {
    @Inject lateinit var coordinator: PlaybackCoordinator

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:playback")
            .apply { setReferenceCounted(false) }
    }
    private var lastWakeLockRenewedAt = 0L
    private val notificationLock = Any()
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val notificationProvider by lazy { DefaultMediaNotificationProvider.Builder(this).build() }
    private val sessionActivity by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private var player: MpvMedia3Player? = null
    private var playbackEngine: PlaybackEngine? = null
    private var mediaSession: MediaSession? = null
    private var acquired = false
    private var destroyed = false
    private var focusPreparation = false
    private var inForeground = false
    private var foregroundAttempt = 0L
    private var notificationFailed = false
    private var notificationRevision = 0L
    private val dismissalKey = UUID.randomUUID().toString()

    private val actionFactory = object : MediaNotification.ActionFactory {
        override fun createMediaAction(
            mediaSession: MediaSession, icon: IconCompat, title: CharSequence, command: Int,
        ): NotificationCompat.Action = NotificationCompat.Action(
            icon, title, createMediaActionPendingIntent(mediaSession, command),
        )

        override fun createMediaActionPendingIntent(mediaSession: MediaSession, command: Int): PendingIntent =
            PlaybackPendingIntentBuilder(this@PlayerMediaSessionService, command, PlayerMediaSessionService::class.java)
                .setSessionId(mediaSession.id)
                .setStartAsForegroundService(!mediaSession.player.playWhenReady)
                .build()

        override fun createNotificationDismissalIntent(mediaSession: MediaSession): PendingIntent =
            PendingIntent.getService(
                this@PlayerMediaSessionService,
                0,
                Intent(this@PlayerMediaSessionService, PlayerMediaSessionService::class.java)
                    .setAction(ACTION_DISMISS_NOTIFICATION)
                    .putExtra(EXTRA_DISMISS_KEY, dismissalKey),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        override fun createCustomAction(
            mediaSession: MediaSession, icon: IconCompat, title: CharSequence, customAction: String, extras: Bundle,
        ): NotificationCompat.Action = NotificationCompat.Action(
            icon,
            title,
            CustomCommandPendingIntentBuilder(
                this@PlayerMediaSessionService,
                PlayerMediaSessionService::class.java,
                SessionCommand(customAction, extras),
            ).setSessionId(mediaSession.id).build(),
        )

        override fun createCustomActionFromCustomCommandButton(
            mediaSession: MediaSession, customCommandButton: CommandButton,
        ): NotificationCompat.Action {
            val command = requireNotNull(customCommandButton.sessionCommand)
            return createCustomAction(
                mediaSession,
                IconCompat.createWithResource(this@PlayerMediaSessionService, customCommandButton.iconResId),
                customCommandButton.displayName,
                command.customAction,
                command.customExtras,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val lease = coordinator.acquire(this, ::prepareForAudioFocus, ::onAudioFocusAbandoned)
            acquired = true
            playbackEngine = lease.engine
            serviceScope.launch {
                lease.engine.snapshot.collect { snapshot ->
                    val needsCpu = requiresPlaybackWakeLock(snapshot)
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (needsCpu && (!wakeLock.isHeld || now - lastWakeLockRenewedAt >= 5 * 60_000L)) {
                            wakeLock.acquire(10 * 60_000L)
                            lastWakeLockRenewedAt = now
                        }
                        if (!needsCpu && wakeLock.isHeld) wakeLock.release()
                    } catch (error: RuntimeException) {
                        coordinator.reportError("无法保持后台播放：${error.message ?: "电源锁不可用"}")
                    }
                }
            }
            val bridge = MpvMedia3Player(
                engine = lease.engine,
                media = lease.media,
                executeForMedia = lease.executeForMedia,
                registerCommand = lease.registerCommand,
                isCommandCurrent = lease.isCommandCurrent,
                registerSpeedRequest = lease.registerSpeedRequest,
                applySpeedRequest = lease.applySpeedRequest,
                discardSpeedRequest = lease.discardSpeedRequest,
            )
            player = bridge
            val session = MediaSession.Builder(this, bridge)
                .setSessionActivity(sessionActivity)
                .setCallback(object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult =
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                            .setAvailableSessionCommands(SessionCommands.EMPTY)
                            .setAvailablePlayerCommands(SessionPlayerCommands.supported)
                            .build()
                })
                .build()
            coordinator.claim(this, ::prepareForAudioFocus, ::onAudioFocusAbandoned)
            mediaSession = session
            // 应用内直接控制引擎，不等待外部控制器连接也必须注册通知观察者。
            addSession(session)
        } catch (error: Exception) {
            coordinator.reportError("创建媒体会话失败：${error.message ?: "未知错误"}")
            stopSelf()
        } catch (error: LinkageError) {
            coordinator.reportError("创建媒体会话失败：${error.message ?: "组件不可用"}")
            stopSelf()
        }
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        super.dump(fd, writer, args)
        writer.println("ZeroPlayer 单引擎播放状态：${coordinator.diagnosticState()}")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_DISMISS_NOTIFICATION && intent.getStringExtra(EXTRA_DISMISS_KEY) == dismissalKey) {
            val snapshot = playbackEngine?.snapshot?.value
            if (snapshot == null || !requiresPlaybackWakeLock(snapshot)) stopSelf(startId)
        }
        return result
    }

    /** API 35+ 只有顶层应用或前台服务能取得焦点；先占前台，再请求焦点。 */
    private fun prepareForAudioFocus(): Boolean = synchronized(notificationLock) {
        if (destroyed) return@synchronized false
        focusPreparation = true
        notificationFailed = false
        val attempt = ++foregroundAttempt
        try {
            if (!inForeground) {
                startPlaybackForeground(initialNotification())
                inForeground = true
            }
            true
        } catch (error: RuntimeException) {
            focusPreparation = false
            notificationFailed = true
            mainHandler.post {
                val stillFailed = synchronized(notificationLock) {
                    !destroyed && notificationFailed && foregroundAttempt == attempt
                }
                if (stillFailed) coordinator.onForegroundServiceFailure(this, error.message ?: error.javaClass.simpleName)
            }
            false
        }
    }

    private fun onAudioFocusAbandoned() {
        val canRefresh = synchronized(notificationLock) {
            focusPreparation = false
            !notificationFailed && !destroyed
        }
        if (canRefresh) mainHandler.post { mediaSession?.let { refreshNotification(it) } }
    }

    private fun initialNotification(): Notification {
        notificationManager.createNotificationChannel(
            NotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID, "媒体播放", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(sessionActivity)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .build()
    }

    private fun startPlaybackForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, notification)
        }
    }

    /** 统一管理前台资格，避免默认实现把申请音频焦点期间的前台状态撤销。 */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        refreshNotification(session)
    }

    private fun refreshNotification(session: MediaSession) {
        if (synchronized(notificationLock) { destroyed || notificationFailed }) return
        val revision = ++notificationRevision
        if (session.player.mediaItemCount == 0 && !synchronized(notificationLock) { focusPreparation }) {
            synchronized(notificationLock) {
                if (!focusPreparation) {
                    if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
                    else notificationManager.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
                    inForeground = false
                }
            }
            return
        }
        try {
            val notification = notificationProvider.createNotification(
                session,
                session.mediaButtonPreferences,
                actionFactory,
            ) { updated ->
                if (revision == notificationRevision && !synchronized(notificationLock) { destroyed || notificationFailed }) {
                    notificationManager.notify(updated.notificationId, updated.notification)
                }
            }
            synchronized(notificationLock) {
                if (destroyed) return
                val playing = session.player.playWhenReady && session.player.playbackState != Player.STATE_ENDED &&
                    session.player.playbackState != Player.STATE_IDLE
                if (focusPreparation || playing) {
                    if (inForeground) {
                        notificationManager.notify(notification.notificationId, notification.notification)
                    } else {
                        startPlaybackForeground(notification.notification)
                        inForeground = true
                    }
                } else {
                    if (inForeground) stopForeground(STOP_FOREGROUND_DETACH)
                    inForeground = false
                    notificationManager.notify(notification.notificationId, notification.notification)
                }
            }
        } catch (error: RuntimeException) {
            synchronized(notificationLock) {
                focusPreparation = false
                notificationFailed = true
            }
            coordinator.onForegroundServiceFailure(this, error.message ?: error.javaClass.simpleName)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val snapshot = playbackEngine?.snapshot?.value
        if (snapshot == null || !requiresPlaybackWakeLock(snapshot)) stopSelf()
    }

    override fun onDestroy() {
        synchronized(notificationLock) {
            destroyed = true
            focusPreparation = false
            foregroundAttempt++
        }
        notificationRevision++
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        playbackEngine = null
        if (acquired) {
            acquired = false
            coordinator.release(this)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        super.onDestroy()
    }
}

internal fun requiresPlaybackWakeLock(snapshot: PlaybackSnapshot): Boolean =
    snapshot.status == PlaybackStatus.PLAYING ||
        (snapshot.playWhenReady &&
            (snapshot.status == PlaybackStatus.LOADING || snapshot.status == PlaybackStatus.PAUSED))

private const val ACTION_DISMISS_NOTIFICATION = "com.privateplayer.media.playback.DISMISS_NOTIFICATION"
private const val EXTRA_DISMISS_KEY = "dismiss_key"

@OptIn(UnstableApi::class)
private object SessionPlayerCommands {
    val supported = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_RELEASE,
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        )
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackServiceModule {
    @Provides
    @Singleton
    fun providePlaybackServiceStarter(@ApplicationContext context: Context): PlaybackServiceStarter =
        PlaybackServiceStarter {
            if (context.startService(Intent(context, PlayerMediaSessionService::class.java)) == null) {
                "系统未能创建播放服务"
            } else {
                null
            }
        }
}
