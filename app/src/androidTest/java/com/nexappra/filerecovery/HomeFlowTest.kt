package com.nexappra.filerecovery

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import com.nexappra.filerecovery.core.designsystem.theme.FileRecoveryTheme
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.presentation.home.*
import com.nexappra.filerecovery.presentation.scan.DeepScanCategoryPicker
import org.junit.*
import org.junit.Assert.*

class HomeFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedMediaCategoryIsPreserved() {
        var selected: RecoveryCategory? = null
        compose.setContent { FileRecoveryTheme(false) { HomeScreen(HomeUiState(), { selected = it }, {}, {}, {}) } }
        compose.onNodeWithText("Photos").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(RecoveryCategory.Photos, selected)
        compose.onNodeWithText("Videos").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(RecoveryCategory.Videos, selected)
        compose.onNodeWithText("Audio").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(RecoveryCategory.Audio, selected)
        compose.onNodeWithText("Documents").assertDoesNotExist()
    }
    @Test fun deepScanOffersAllThreeSelectableCategories() {
        compose.setContent {
            var selected by remember { mutableStateOf(RecoveryCategory.Photos) }
            FileRecoveryTheme(false) { DeepScanCategoryPicker(selected) { selected = it } }
        }
        compose.onNodeWithText("Photos").assertIsSelected()
        compose.onNodeWithText("Audio").performClick().assertIsSelected()
        compose.onNodeWithText("Photos").assertIsNotSelected()
        compose.onNodeWithText("Videos").performClick().assertIsSelected()
        compose.onNodeWithText("Audio").assertIsNotSelected()
    }
}
