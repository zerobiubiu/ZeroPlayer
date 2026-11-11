package com.privateplayer.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.privateplayer.domain.model.AppSettings
import com.privateplayer.domain.model.DisplayPreferences
import com.privateplayer.domain.model.GesturePreferences
import com.privateplayer.domain.model.GridDensity
import com.privateplayer.domain.model.ImportPreferences
import com.privateplayer.domain.model.PlaybackPreferences
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.domain.model.ThemeMode
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "private_player_settings")

/**
 * 基于 Preferences DataStore 的设置仓库。
 *
 * 只持久化用户偏好，不存媒体数据；读取时统一回填默认值，
 * 因此新增偏好项不需要数据迁移。
 */
@Singleton
class PreferencesSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    override fun observeSettings(): Flow<AppSettings> = store.data
        .map { prefs ->
            AppSettings(
                playback = PlaybackPreferences(
                    autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
                    rememberProgress = prefs[Keys.REMEMBER_PROGRESS] ?: true,
                    autoResume = prefs[Keys.AUTO_RESUME] ?: true,
                    defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
                    defaultAspect = prefs[Keys.DEFAULT_ASPECT].toEnum(VideoAspect.FIT),
                    seekStepSeconds = prefs[Keys.SEEK_STEP] ?: 10,
                    longPressSpeed = prefs[Keys.LONG_PRESS_SPEED] ?: 2.0f,
                ),
                subtitle = SubtitlePreferences(
                    fontSizeScale = prefs[Keys.SUBTITLE_FONT_SCALE] ?: 1.0f,
                    background = prefs[Keys.SUBTITLE_BACKGROUND].toEnum(SubtitleBackground.SEMI_TRANSPARENT),
                    textColor = prefs[Keys.SUBTITLE_TEXT_COLOR].toEnum(SubtitleTextColor.WHITE),
                    delayStepMs = prefs[Keys.SUBTITLE_DELAY_STEP] ?: 100,
                ),
                gesture = GesturePreferences(
                    brightnessGestureEnabled = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
                    volumeGestureEnabled = prefs[Keys.GESTURE_VOLUME] ?: true,
                    seekGestureEnabled = prefs[Keys.GESTURE_SEEK] ?: true,
                    longPressSpeedEnabled = prefs[Keys.GESTURE_LONG_PRESS_SPEED] ?: true,
                ),
                display = DisplayPreferences(
                    themeMode = prefs[Keys.THEME_MODE].toEnum(ThemeMode.SYSTEM),
                    dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                    gridDensity = prefs[Keys.GRID_DENSITY].toEnum(GridDensity.DEFAULT),
                ),
                import = ImportPreferences(
                    generateThumbnails = prefs[Keys.IMPORT_THUMBNAILS] ?: true,
                    duplicateDetection = prefs[Keys.IMPORT_DUPLICATE_DETECTION] ?: true,
                ),
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setGridDensity(density: GridDensity) {
        store.edit { it[Keys.GRID_DENSITY] = density.name }
    }

    override suspend fun setAutoPlayNext(enabled: Boolean) {
        store.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }

    override suspend fun setRememberProgress(enabled: Boolean) {
        store.edit { it[Keys.REMEMBER_PROGRESS] = enabled }
    }

    override suspend fun setAutoResume(enabled: Boolean) {
        store.edit { it[Keys.AUTO_RESUME] = enabled }
    }

    override suspend fun setDefaultSpeed(speed: Float) {
        store.edit { it[Keys.DEFAULT_SPEED] = speed.coerceIn(0.25f, 4.0f) }
    }

    override suspend fun setDefaultAspect(aspect: VideoAspect) {
        store.edit { it[Keys.DEFAULT_ASPECT] = aspect.name }
    }

    override suspend fun setSeekStepSeconds(seconds: Int) {
        store.edit { it[Keys.SEEK_STEP] = seconds.coerceIn(5, 60) }
    }

    override suspend fun setLongPressSpeed(speed: Float) {
        store.edit { it[Keys.LONG_PRESS_SPEED] = speed.coerceIn(1.5f, 4.0f) }
    }

    override suspend fun setSubtitleFontScale(scale: Float) {
        store.edit { it[Keys.SUBTITLE_FONT_SCALE] = scale.coerceIn(0.6f, 2.0f) }
    }

    override suspend fun setSubtitleBackground(background: SubtitleBackground) {
        store.edit { it[Keys.SUBTITLE_BACKGROUND] = background.name }
    }

    override suspend fun setSubtitleTextColor(color: SubtitleTextColor) {
        store.edit { it[Keys.SUBTITLE_TEXT_COLOR] = color.name }
    }

    override suspend fun setSubtitleDelayStep(stepMs: Int) {
        store.edit { it[Keys.SUBTITLE_DELAY_STEP] = stepMs.coerceIn(50, 1000) }
    }

    override suspend fun setBrightnessGesture(enabled: Boolean) {
        store.edit { it[Keys.GESTURE_BRIGHTNESS] = enabled }
    }

    override suspend fun setVolumeGesture(enabled: Boolean) {
        store.edit { it[Keys.GESTURE_VOLUME] = enabled }
    }

    override suspend fun setSeekGesture(enabled: Boolean) {
        store.edit { it[Keys.GESTURE_SEEK] = enabled }
    }

    override suspend fun setLongPressSpeedGesture(enabled: Boolean) {
        store.edit { it[Keys.GESTURE_LONG_PRESS_SPEED] = enabled }
    }

    override suspend fun setGenerateThumbnails(enabled: Boolean) {
        store.edit { it[Keys.IMPORT_THUMBNAILS] = enabled }
    }

    override suspend fun setDuplicateDetection(enabled: Boolean) {
        store.edit { it[Keys.IMPORT_DUPLICATE_DETECTION] = enabled }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T {
        if (this == null) return default
        return enumValues<T>().firstOrNull { it.name == this } ?: default
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRID_DENSITY = stringPreferencesKey("grid_density")

        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val REMEMBER_PROGRESS = booleanPreferencesKey("remember_progress")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
        val SEEK_STEP = intPreferencesKey("seek_step_seconds")
        val LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")

        val SUBTITLE_FONT_SCALE = floatPreferencesKey("subtitle_font_scale")
        val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
        val SUBTITLE_TEXT_COLOR = stringPreferencesKey("subtitle_text_color")
        val SUBTITLE_DELAY_STEP = intPreferencesKey("subtitle_delay_step")

        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        val GESTURE_LONG_PRESS_SPEED = booleanPreferencesKey("gesture_long_press_speed")

        val IMPORT_THUMBNAILS = booleanPreferencesKey("import_generate_thumbnails")
        val IMPORT_DUPLICATE_DETECTION = booleanPreferencesKey("import_duplicate_detection")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: PreferencesSettingsRepository): SettingsRepository
}
