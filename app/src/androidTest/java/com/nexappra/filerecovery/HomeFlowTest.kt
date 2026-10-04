package com.nexappra.filerecovery

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nexappra.filerecovery.core.designsystem.theme.FileRecoveryTheme
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.presentation.home.*
import org.junit.*
import org.junit.Assert.*

class HomeFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun onlyVisualCategoriesAreOfferedAndPhotosKeepTheirCategory() {
        var selected: RecoveryCategory? = null
        compose.setContent { FileRecoveryTheme(false) { HomeScreen(HomeUiState(), { selected = it }, {}, {}, {}) } }
        compose.onNodeWithText("Photos").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(RecoveryCategory.Photos, selected)
        compose.onNodeWithText("Videos").assertIsDisplayed()
        compose.onNodeWithText("Audio").assertDoesNotExist()
        compose.onNodeWithText("Documents").assertDoesNotExist()
    }
}
