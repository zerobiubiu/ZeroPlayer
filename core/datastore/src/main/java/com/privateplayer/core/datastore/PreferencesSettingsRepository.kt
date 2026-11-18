package com.privateplayer.core.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
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
import com.privateplayer.domain.model.PlaybackProgressMode
import com.privateplayer.domain.model.PlaybackSpeedLimits
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleFont
import com.privateplayer.domain.model.SubtitleFontStyle
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
import kotlin.enums.enumEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "private_player_settings")
private val playbackDefaults = PlaybackPreferences()
private val subtitleDefaults = SubtitlePreferences()

/**
 * 基于 Preferences DataStore 的设置仓库。
 *
 * 只持久化用户偏好，不存媒体数据；读取时统一回填默认值，
 * 因此新增偏好项不需要数据迁移。
 */
@Singleton
class PreferencesSettingsRepository @VisibleForTesting constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

    override fun observeSettings(): Flow<AppSettings> = store.data
        .map { prefs ->
            AppSettings(
                playback = PlaybackPreferences(
                    autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
                    progressMode = PlaybackProgressMode.fromStoredFlags(
                        record = prefs[Keys.REMEMBER_PROGRESS] ?: true,
                        resume = prefs[Keys.AUTO_RESUME] ?: true,
                    ),
                    defaultSpeed = prefs.playbackSpeed(Keys.DEFAULT_SPEED, PlaybackSpeedLimits.MIN, playbackDefaults.defaultSpeed),
                    defaultAspect = prefs[Keys.DEFAULT_ASPECT].toEnum(VideoAspect.FIT),
                    seekStepSeconds = prefs[Keys.SEEK_STEP] ?: 10,
                    longPressSpeed = prefs.playbackSpeed(Keys.LONG_PRESS_SPEED, PlaybackSpeedLimits.MIN_TEMPORARY, playbackDefaults.longPressSpeed),
                ),
                subtitle = prefs.subtitlePreferences(),
                gesture = GesturePreferences(
                    brightnessGestureEnabled = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
                    volumeGestureEnabled = prefs[Keys.GESTURE_VOLUME] ?: true,
                    seekGestureEnabled = prefs[Keys.GESTURE_SEEK] ?: true,
                    longPressSpeedEnabled = prefs[Keys.GESTURE_LONG_PRESS_SPEED] ?: true,
                    hapticsEnabled = prefs[Keys.GESTURE_HAPTICS] ?: true,
                ),
                display = DisplayPreferences(
                    themeMode = prefs[Keys.THEME_MODE].toEnum(ThemeMode.SYSTEM),
                    gridDensity = prefs[Keys.GRID_DENSITY].toEnum(GridDensity.DEFAULT),
                ),
                import = ImportPreferences(
                    generateThumbnails = prefs[Keys.IMPORT_THUMBNAILS] ?: true,
                    duplicateDetection = prefs[Keys.IMPORT_DUPLICATE_DETECTION] ?: true,
                ),
            )
        }
        .distinctUntilChanged()

    private fun Preferences.subtitlePreferences() = SubtitlePreferences(
        fontSizeScale = typedValue(Keys.SUBTITLE_FONT_SCALE)?.boundedFontScale() ?: subtitleDefaults.fontSizeScale,
        background = typedValue(Keys.SUBTITLE_BACKGROUND).toEnum(subtitleDefaults.background),
        textColor = typedValue(Keys.SUBTITLE_TEXT_COLOR).toEnum(subtitleDefaults.textColor),
        delayStepMs = typedValue(Keys.SUBTITLE_DELAY_STEP)?.boundedDelayStep() ?: subtitleDefaults.delayStepMs,
        font = typedValue(Keys.SUBTITLE_FONT).toEnum(subtitleDefaults.font),
        fontStyle = typedValue(Keys.SUBTITLE_FONT_STYLE).toEnum(subtitleDefaults.fontStyle),
        verticalOffsetPercent = typedValue(Keys.SUBTITLE_VERTICAL_OFFSET)?.boundedOffset() ?: subtitleDefaults.verticalOffsetPercent,
        overrideAss = typedValue(Keys.SUBTITLE_OVERRIDE_ASS) ?: subtitleDefaults.overrideAss,
    )

    // 同名旧键即便类型不对，也只回退这一项，不中断整个设置流。
    private inline fun <reified T : Any> Preferences.typedValue(key: Preferences.Key<T>): T? {
        val untypedKey: Preferences.Key<*> = key
        return this[untypedKey] as? T
    }

    private fun Float.boundedFontScale() = if (isFinite()) coerceIn(0.6f, 2.0f) else subtitleDefaults.fontSizeScale
    private fun Int.boundedDelayStep() = coerceIn(50, 1000)
    private fun Int.boundedOffset() = coerceIn(-10, 30)

    private fun Preferences.playbackSpeed(key: Preferences.Key<Float>, minimum: Float, fallback: Float): Float {
        val speed = typedValue(key) ?: return fallback
        return if (speed.isFinite()) speed.coerceIn(minimum, PlaybackSpeedLimits.MAX) else fallback
    }

    private suspend fun writePlaybackSpeed(key: Preferences.Key<Float>, minimum: Float, speed: Float) {
        if (!speed.isFinite()) return
        store.edit { it[key] = speed.coerceIn(minimum, PlaybackSpeedLimits.MAX) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setGridDensity(density: GridDensity) {
        store.edit { it[Keys.GRID_DENSITY] = density.name }
    }

    override suspend fun setAutoPlayNext(enabled: Boolean) {
        store.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }

    override suspend fun setPlaybackProgressMode(mode: PlaybackProgressMode) {
        // 沿用两个旧键的磁盘格式；调用方只能一次提交一种完整策略。
        store.edit {
            it[Keys.REMEMBER_PROGRESS] = mode != PlaybackProgressMode.DISABLED
            it[Keys.AUTO_RESUME] = mode == PlaybackProgressMode.RESUME
        }
    }

    override suspend fun setDefaultSpeed(speed: Float) {
        writePlaybackSpeed(Keys.DEFAULT_SPEED, PlaybackSpeedLimits.MIN, speed)
    }

    override suspend fun setDefaultAspect(aspect: VideoAspect) {
        store.edit { it[Keys.DEFAULT_ASPECT] = aspect.name }
    }

    override suspend fun setSeekStepSeconds(seconds: Int) {
        store.edit { it[Keys.SEEK_STEP] = seconds.coerceIn(5, 60) }
    }

    override suspend fun setLongPressSpeed(speed: Float) {
        writePlaybackSpeed(Keys.LONG_PRESS_SPEED, PlaybackSpeedLimits.MIN_TEMPORARY, speed)
    }

    override suspend fun setSubtitleFontScale(scale: Float) {
        store.edit { it[Keys.SUBTITLE_FONT_SCALE] = scale.boundedFontScale() }
    }

    override suspend fun setSubtitleFont(font: SubtitleFont) {
        store.edit { it[Keys.SUBTITLE_FONT] = font.name }
    }

    override suspend fun setSubtitleFontStyle(style: SubtitleFontStyle) {
        store.edit { it[Keys.SUBTITLE_FONT_STYLE] = style.name }
    }

    override suspend fun setSubtitleVerticalOffset(percent: Int) {
        store.edit { it[Keys.SUBTITLE_VERTICAL_OFFSET] = percent.boundedOffset() }
    }

    override suspend fun setSubtitleOverrideAss(enabled: Boolean) {
        store.edit { it[Keys.SUBTITLE_OVERRIDE_ASS] = enabled }
    }

    override suspend fun setSubtitleBackground(background: SubtitleBackground) {
        store.edit { it[Keys.SUBTITLE_BACKGROUND] = background.name }
    }

    override suspend fun setSubtitleTextColor(color: SubtitleTextColor) {
        store.edit { it[Keys.SUBTITLE_TEXT_COLOR] = color.name }
    }

    override suspend fun setSubtitleDelayStep(stepMs: Int) {
        store.edit { it[Keys.SUBTITLE_DELAY_STEP] = stepMs.boundedDelayStep() }
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

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        store.edit { it[Keys.GESTURE_HAPTICS] = enabled }
    }

    override suspend fun setGenerateThumbnails(enabled: Boolean) {
        store.edit { it[Keys.IMPORT_THUMBNAILS] = enabled }
    }

    override suspend fun setDuplicateDetection(enabled: Boolean) {
        store.edit { it[Keys.IMPORT_DUPLICATE_DETECTION] = enabled }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T {
        if (this == null) return default
        return enumEntries<T>().firstOrNull { it.name == this } ?: default
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
        val SUBTITLE_FONT = stringPreferencesKey("subtitle_font")
        val SUBTITLE_FONT_STYLE = stringPreferencesKey("subtitle_font_style")
        val SUBTITLE_VERTICAL_OFFSET = intPreferencesKey("subtitle_vertical_offset_percent")
        val SUBTITLE_OVERRIDE_ASS = booleanPreferencesKey("subtitle_override_ass")

        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        val GESTURE_LONG_PRESS_SPEED = booleanPreferencesKey("gesture_long_press_speed")
        val GESTURE_HAPTICS = booleanPreferencesKey("gesture_haptics")

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
