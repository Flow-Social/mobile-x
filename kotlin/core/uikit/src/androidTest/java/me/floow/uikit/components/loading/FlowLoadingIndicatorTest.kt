package me.floow.uikit.components.loading

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowLoadingIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun indicator_isDisplayedWithUnifiedSize() {
        composeTestRule.setContent {
            FlowLoadingIndicator()
        }

        composeTestRule.onNodeWithTag(FLOW_LOADING_INDICATOR_TAG)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(FlowLoadingIndicatorDefaults.Size)
            .assertHeightIsEqualTo(FlowLoadingIndicatorDefaults.Size)
    }

    @Test
    fun indicator_ignoresExternalSizeOverrides() {
        composeTestRule.setContent {
            FlowLoadingIndicator(
                modifier = Modifier.size(12.dp)
            )
        }

        composeTestRule.onNodeWithTag(FLOW_LOADING_INDICATOR_TAG)
            .assertWidthIsEqualTo(FlowLoadingIndicatorDefaults.Size)
            .assertHeightIsEqualTo(FlowLoadingIndicatorDefaults.Size)
    }
}
