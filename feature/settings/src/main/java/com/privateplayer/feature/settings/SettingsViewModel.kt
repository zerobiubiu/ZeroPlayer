package com.privateplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privateplayer.core.common.di.ApplicationScope
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.core.playback.PlaybackEngineProvider
import com.privateplayer.domain.model.AppSettings
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.StorageUsage
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.domain.model.ThemeMode
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val usage: StorageUsage = StorageUsage(0, 0, 0, 0, 0),
    val engineLabel: String = "",
    val engineDetail: String = "",
    val versionName: String = "",
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val storageRepository: StorageRepository,
    engineProvider: PlaybackEngineProvider,
    versionNameProvider: AppVersionNameProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    /**
     * 设置写入队列：所有写入按提交顺序串行执行。
     *
     * 连续修改不会互相覆盖，队列保证最后一次修改最后落盘；
     * 队列消费使用应用级作用域，离开设置页后仍在完成写入。
     */
    private val writeQueue = SettingsWriteQueue(applicationScope) { error ->
        message.value = "设置保存失败：${error.message ?: "未知错误"}"
    }

    private val engineLabel = engineProvider.backendLabel
    private val engineDetail = engineProvider.backendDetail
    private val versionName: String = versionNameProvider.versionName()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        storageRepository.observeUsage(),
        message,
    ) { settings, usage, messageValue ->
        SettingsUiState(
            settings = settings,
            usage = usage,
            engineLabel = engineLabel,
            engineDetail = engineDetail,
            versionName = versionName,
            message = messageValue,
        )
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun consumeMessage() {
        message.value = null
    }

    fun setTheme(mode: ThemeMode) = launchSetting { settingsRepository.setThemeMode(mode) }

    fun setAutoPlayNext(enabled: Boolean) = launchSetting { settingsRepository.setAutoPlayNext(enabled) }

    fun setRememberProgress(enabled: Boolean) = launchSetting { settingsRepository.setRememberProgress(enabled) }

    fun setAutoResume(enabled: Boolean) = launchSetting { settingsRepository.setAutoResume(enabled) }

    fun setDefaultSpeed(speed: Float) = launchSetting { settingsRepository.setDefaultSpeed(speed) }

    fun setDefaultAspect(aspect: VideoAspect) = launchSetting { settingsRepository.setDefaultAspect(aspect) }

    fun setSeekStep(seconds: Int) = launchSetting { settingsRepository.setSeekStepSeconds(seconds) }

    fun setLongPressSpeed(speed: Float) = launchSetting { settingsRepository.setLongPressSpeed(speed) }

    fun setSubtitleFontScale(scale: Float) = launchSetting { settingsRepository.setSubtitleFontScale(scale) }

    fun setSubtitleBackground(background: SubtitleBackground) =
        launchSetting { settingsRepository.setSubtitleBackground(background) }

    fun setSubtitleTextColor(color: SubtitleTextColor) =
        launchSetting { settingsRepository.setSubtitleTextColor(color) }

    fun setSubtitleDelayStep(stepMs: Int) = launchSetting { settingsRepository.setSubtitleDelayStep(stepMs) }

    fun setBrightnessGesture(enabled: Boolean) =
        launchSetting { settingsRepository.setBrightnessGesture(enabled) }

    fun setVolumeGesture(enabled: Boolean) = launchSetting { settingsRepository.setVolumeGesture(enabled) }

    fun setSeekGesture(enabled: Boolean) = launchSetting { settingsRepository.setSeekGesture(enabled) }

    fun setLongPressSpeedGesture(enabled: Boolean) =
        launchSetting { settingsRepository.setLongPressSpeedGesture(enabled) }

    fun setHapticsEnabled(enabled: Boolean) = launchSetting { settingsRepository.setHapticsEnabled(enabled) }

    fun setGenerateThumbnails(enabled: Boolean) = launchSetting { settingsRepository.setGenerateThumbnails(enabled) }

    fun setDuplicateDetection(enabled: Boolean) = launchSetting { settingsRepository.setDuplicateDetection(enabled) }

    fun clearThumbnailCache() {
        viewModelScope.launch {
            val outcome = storageRepository.clearThumbnailCache()
            message.value = outcome.text("缩略图缓存已清理")
        }
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch {
            val outcome = storageRepository.clearTemporaryFiles()
            message.value = outcome.text("临时文件已清理")
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        writeQueue.submit(block)
    }

    private fun OperationOutcome.text(success: String): String = when (this) {
        is OperationOutcome.Success -> success
        is OperationOutcome.Failure -> message
    }
}
