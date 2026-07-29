package com.worxbend.zephyr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.worxbend.zephyr.settings.AppSettings
import com.worxbend.zephyr.settings.ThemePreference
import com.worxbend.zephyr.settings.UiDensity
import kotlin.test.Test

class ZephyrUiPrimitivesTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun searchFieldPublishesUserInput() = runComposeUiTest {
        setContent {
            ZephyrTheme(darkTheme = false) {
                var query by remember { mutableStateOf("") }
                Column {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search installed candidates",
                        modifier = Modifier.testTag("candidate-search"),
                    )
                    Text(query, modifier = Modifier.testTag("search-query"))
                }
            }
        }

        onNodeWithTag("candidate-search").performClick()
        onNodeWithTag("candidate-search").performTextInput("gradle")

        onNodeWithTag("search-query").assertTextEquals("gradle")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsScreenPublishesAppearanceChanges() = runComposeUiTest {
        setContent {
            ZephyrTheme(darkTheme = false) {
                var settings by remember { mutableStateOf(AppSettings()) }
                Box {
                    SettingsScreen(
                        settings = settings,
                        onSettingsChange = { transform -> settings = transform(settings) },
                    )
                    Text(
                        "${settings.themePreference}:${settings.uiDensity}",
                        modifier = Modifier.testTag("appearance-settings"),
                    )
                }
            }
        }

        onNodeWithText("Dark").performClick()
        onNodeWithText("Comfortable").performClick()

        onNodeWithTag("appearance-settings").assertTextEquals(
            "${ThemePreference.Dark}:${UiDensity.Comfortable}",
        )
    }
}
