package app.wishtube.ui

import androidx.compose.material.icons.Icons
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import app.wishtube.sources.SourceId

@RunWith(AndroidJUnit4::class)
class FeedUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testEmptyState() {
        composeRule.setContent {
            EmptyState(
                icon = Icons.Outlined.VideoLibrary,
                title = "Nothing here yet",
                body = "Pull down to refresh or check your enabled sources in Settings."
            )
        }
        composeRule.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }

    @Test
    fun testErrorState() {
        composeRule.setContent {
            FailureState(
                errors = mapOf(SourceId.PEERTUBE to "Could not reach"),
                nav = null,
                onRetry = {}
            )
        }
        composeRule.onNodeWithText("PeerTube could not be reached.").assertIsDisplayed()
    }
}
