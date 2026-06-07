// FieldNotes — RecorderScreenTest.kt
// Authored by: testing | Implements: 10_TESTING_PLAN.md (Compose UI smoke test)
package com.fieldnotes.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class RecorderScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun twoRecordButtonsVisibleOnLaunch() {
        composeTestRule.onNodeWithText("FIELD REC").assertIsDisplayed()
        composeTestRule.onNodeWithText("VOICE NOTE").assertIsDisplayed()
    }
}
