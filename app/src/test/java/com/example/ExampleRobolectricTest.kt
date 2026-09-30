package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Water Reminder", appName)
  }
}
