package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddCityDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun city_field_takes_focus_and_opens_the_keyboard() {
        var shown = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() {
                shown++
            }

            override fun hide() = Unit
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                MaterialTheme {
                    AddCityDialog(
                        input = "",
                        error = null,
                        onInputChange = {},
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(1, shown)
    }
}
