package com.privateplayer.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleFont
import com.privateplayer.domain.model.SubtitleFontStyle
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitlePreferencesDataStoreInstrumentedTest {
    @get:Rule
    val temporary: TemporaryFolder = TemporaryFolder.builder()
        .parentFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        .assureDeletion()
        .build()

    private val defaults = SubtitlePreferences(
        fontSizeScale = 1f,
        background = SubtitleBackground.SEMI_TRANSPARENT,
        textColor = SubtitleTextColor.WHITE,
        delayStepMs = 100,
        font = SubtitleFont.DEFAULT,
        fontStyle = SubtitleFontStyle.NORMAL,
        verticalOffsetPercent = 0,
        overrideAss = false,
    )

    private val scaleKey = floatPreferencesKey("subtitle_font_scale")
    private val backgroundKey = stringPreferencesKey("subtitle_background")
    private val colorKey = stringPreferencesKey("subtitle_text_color")
    private val stepKey = intPreferencesKey("subtitle_delay_step")
    private val offsetKey = intPreferencesKey("subtitle_vertical_offset_percent")

    @Test
    fun observesEachStyleChangeAndRestoresItFromDisk() = runBlocking {
        val expected = SubtitlePreferences(
            fontSizeScale = 1.5f,
            background = SubtitleBackground.NAVY,
            textColor = SubtitleTextColor.CYAN,
            delayStepMs = 250,
            font = SubtitleFont.MONOSPACE,
            fontStyle = SubtitleFontStyle.BOLD_ITALIC,
            verticalOffsetPercent = 16,
            overrideAss = true,
        )
        assertEquals(defaults, SubtitlePreferences())
        withStore { store ->
            val repository = PreferencesSettingsRepository(store)
            withTimeout(10_000L) {
                val changes = Channel<SubtitlePreferences>()
                val observer = launch { repository.observeSettings().collect { changes.send(it.subtitle) } }
                try {
                    assertEquals(defaults, changes.receive())
                    repository.setSubtitleFontScale(expected.fontSizeScale)
                    assertEquals(expected.fontSizeScale, changes.receive().fontSizeScale, 0f)
                    repository.setSubtitleFont(expected.font)
                    assertEquals(expected.font, changes.receive().font)
                    repository.setSubtitleFontStyle(expected.fontStyle)
                    assertEquals(expected.fontStyle, changes.receive().fontStyle)
                    repository.setSubtitleTextColor(expected.textColor)
                    assertEquals(expected.textColor, changes.receive().textColor)
                    repository.setSubtitleBackground(expected.background)
                    assertEquals(expected.background, changes.receive().background)
                    repository.setSubtitleVerticalOffset(expected.verticalOffsetPercent)
                    assertEquals(expected.verticalOffsetPercent, changes.receive().verticalOffsetPercent)
                    repository.setSubtitleDelayStep(expected.delayStepMs)
                    assertEquals(expected.delayStepMs, changes.receive().delayStepMs)
                    repository.setSubtitleOverrideAss(expected.overrideAss)
                    assertEquals(expected, changes.receive())
                } finally {
                    observer.cancelAndJoin()
                }
            }
        }
        withStore { reopened ->
            val repository = PreferencesSettingsRepository(reopened)
            assertEquals(expected, repository.observeSettings().first().subtitle)
            repository.setSubtitleOverrideAss(false)
            assertEquals(expected.copy(overrideAss = false), repository.observeSettings().first().subtitle)
        }
    }

    @Test
    fun restoresLegacyKeysWithoutChangingTheirMeaning() = runBlocking {
        // 只写旧版本已有的四个键，不经过新仓库的 setter。
        withStore { legacy ->
            legacy.edit {
                it[scaleKey] = 1.25f
                it[backgroundKey] = "SOLID"
                it[colorKey] = "YELLOW"
                it[stepKey] = 350
            }
        }
        withStore { reopened ->
            val expected = defaults.copy(
                fontSizeScale = 1.25f,
                background = SubtitleBackground.SOLID,
                textColor = SubtitleTextColor.YELLOW,
                delayStepMs = 350,
            )
            assertEquals(expected, PreferencesSettingsRepository(reopened).observeSettings().first().subtitle)
        }
    }

    @Test
    fun malformedKeysFallBackIndependentlyAndNumbersStayInRange() = runBlocking {
        withStore { store ->
            store.edit {
                it[scaleKey] = Float.NaN
                it[backgroundKey] = "UNKNOWN_BACKGROUND"
                it[colorKey] = "YELLOW"
                it[intPreferencesKey("subtitle_font")] = 7
                it[stringPreferencesKey("subtitle_font_style")] = "UNKNOWN_STYLE"
                it[stringPreferencesKey("subtitle_override_ass")] = "true"
                it[offsetKey] = Int.MAX_VALUE
                it[stepKey] = Int.MIN_VALUE
            }
        }
        withStore { reopened ->
            val repository = PreferencesSettingsRepository(reopened)
            val expected = defaults.copy(textColor = SubtitleTextColor.YELLOW, verticalOffsetPercent = 30, delayStepMs = 50)
            assertEquals(expected, repository.observeSettings().first().subtitle)
            reopened.edit {
                it[scaleKey] = 9f
                it[offsetKey] = Int.MIN_VALUE
                it[stepKey] = Int.MAX_VALUE
            }
            assertEquals(
                expected.copy(fontSizeScale = 2f, verticalOffsetPercent = -10, delayStepMs = 1000),
                repository.observeSettings().first().subtitle,
            )
        }
    }

    @Test
    fun numericSettersPersistOnlySafeValues() = runBlocking {
        withStore { store ->
            val repository = PreferencesSettingsRepository(store)
            repository.setSubtitleFontScale(-1f)
            repository.setSubtitleVerticalOffset(Int.MAX_VALUE)
            repository.setSubtitleDelayStep(Int.MIN_VALUE)
            // 直接读原始键，避免读路径的钳制掩盖了错误写入。
            val raw = store.data.first()
            assertEquals(0.6f, requireNotNull(raw[scaleKey]), 0f)
            assertEquals(30, raw[offsetKey])
            assertEquals(50, raw[stepKey])
            repository.setSubtitleFontScale(Float.POSITIVE_INFINITY)
            repository.setSubtitleVerticalOffset(Int.MIN_VALUE)
            repository.setSubtitleDelayStep(Int.MAX_VALUE)
        }
        withStore { reopened ->
            val raw = reopened.data.first()
            assertEquals(1f, requireNotNull(raw[scaleKey]), 0f)
            assertEquals(-10, raw[offsetKey])
            assertEquals(1000, raw[stepKey])
        }
    }

    @Test
    fun defaultAndLongPressSpeedPersistTheirOwnBounds() = runBlocking {
        val defaultKey = floatPreferencesKey("default_speed")
        val longPressKey = floatPreferencesKey("long_press_speed")
        withStore { store ->
            val repository = PreferencesSettingsRepository(store)
            val initial = repository.observeSettings().first().playback
            assertEquals(1f, initial.defaultSpeed, 0f)
            assertEquals(2f, initial.longPressSpeed, 0f)

            suspend fun writeAndExpect(speed: Float, held: Float, expectedSpeed: Float, expectedHeld: Float) {
                repository.setDefaultSpeed(speed)
                repository.setLongPressSpeed(held)
                val raw = store.data.first()
                assertEquals(expectedSpeed, requireNotNull(raw[defaultKey]), 0f)
                assertEquals(expectedHeld, requireNotNull(raw[longPressKey]), 0f)
            }
            writeAndExpect(0.25f, 1.5f, 0.25f, 1.5f)
            writeAndExpect(8f, 8f, 8f, 8f)
            writeAndExpect(20f, 0f, 8f, 1.5f)
            writeAndExpect(-1f, 20f, 0.25f, 8f)
            writeAndExpect(Float.NaN, Float.POSITIVE_INFINITY, 0.25f, 8f)
        }
        withStore { reopened ->
            val repository = PreferencesSettingsRepository(reopened)
            suspend fun expect(speed: Float, held: Float) {
                val playback = repository.observeSettings().first().playback
                assertEquals(speed, playback.defaultSpeed, 0f)
                assertEquals(held, playback.longPressSpeed, 0f)
            }
            expect(0.25f, 8f)
            reopened.edit {
                it[defaultKey] = 20f
                it[longPressKey] = 0f
            }
            expect(8f, 1.5f)
            reopened.edit {
                it[defaultKey] = Float.NEGATIVE_INFINITY
                it[longPressKey] = Float.NaN
            }
            expect(1f, 2f)
        }
    }

    // 每次打开同一个临时文件，但使用独立的存储实例和作用域，不接触应用设置。
    private suspend fun withStore(block: suspend (DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
                File(temporary.root, "subtitles.preferences_pb")
            }
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }
}
