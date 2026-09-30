package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.material3.Text
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.ui.theme.WaterReminderTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val config = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .setExecutor(SynchronousExecutor())
        .build()
    try {
      WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    } catch (_: IllegalStateException) {
      // Already initialized
    }
  }

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { WaterReminderTheme { Text("Robolectric") } }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
