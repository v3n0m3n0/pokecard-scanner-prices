package com.example

import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GreetingScreenshotTest {
  @Test
  fun captureGreeting() {
    captureRoboImage {
      Text("Hello World")
    }
  }
}
