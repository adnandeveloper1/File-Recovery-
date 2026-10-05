package com.nexappra.filerecovery

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nexappra.filerecovery.domain.model.PremiumRequiredException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PremiumAccessFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun debugPreviewUnlocksToolsAndCanBeRevokedWithoutPurchase() {
        compose.onNodeWithText("Get Plus").performClick()
        compose.onNodeWithContentDescription("Test Premium features").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.activity.billing.state.value.isDebugPreview }
        compose.runOnIdle { compose.activity.billing.requirePremium() }
        compose.onNodeWithText("TEST ACCESS · DEBUG BUILD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Test Premium features").performScrollTo().performClick()
        compose.waitUntil(5_000) { !compose.activity.billing.state.value.isDebugPreview }
        compose.runOnIdle {
            // The default local build has no configured verifier or real purchase.
            if (!compose.activity.billing.state.value.configured) {
                try {
                    compose.activity.billing.requirePremium()
                    fail("Turning off test access must restore the Premium gate")
                } catch (_: PremiumRequiredException) { }
            }
        }
    }
}
