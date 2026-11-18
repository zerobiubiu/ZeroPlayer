package com.privateplayer.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.domain.R as DomainR
import com.privateplayer.feature.settings.R as SettingsR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun settingsTabOpensSectionsAfterLaunchAndReentry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        fun action(@StringRes label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction())

        ActivityScenario.launch(MainActivity::class.java).use {
            val home = action(R.string.app_nav_home)
            val settings = action(R.string.app_nav_settings)
            val playback = action(SettingsR.string.settings_section_playback)
            home.assertIsSelected()
            settings.performClick()
            settings.assertIsSelected()
            playback.assertIsDisplayed().performClick()
            action(SettingsR.string.settings_progress_mode).assertIsDisplayed().performClick()
            val recordOnly = context.getString(DomainR.string.option_progress_record_only)
            compose.onNode(hasText(recordOnly) and hasAnyAncestor(isPopup())).assertIsDisplayed()
            pressBack()
            compose.onNode(isPopup()).assertDoesNotExist()
            pressBack()
            playback.assertIsDisplayed()

            home.performClick()
            home.assertIsSelected()
            settings.performClick()
            action(SettingsR.string.settings_section_subtitle).assertIsDisplayed().performClick()
            compose.onNodeWithText(context.getString(SettingsR.string.settings_subtitle_style)).assertIsDisplayed()
            pressBack()
            settings.assertIsSelected()
            playback.assertIsDisplayed()
        }
    }
}
