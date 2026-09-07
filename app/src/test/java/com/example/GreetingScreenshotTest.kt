package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.AlarmItem
import com.example.ui.components.AlarmCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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

  @Test
  fun greeting_screenshot() {
    val sampleAlarm = AlarmItem(
      id = 1,
      hour = 7,
      minute = 30,
      label = "صبح بخیر",
      isEnabled = true,
      daysOfWeek = listOf(6, 7, 1, 2, 3),
      volume = 85,
      isGradualVolume = true,
      isSmartMuteEnabled = true
    )
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AlarmCard(
          alarm = sampleAlarm,
          onToggle = {},
          onClick = {},
          onDelete = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

