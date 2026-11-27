package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.captions.CaptionBitmapRenderer
import com.example.core.captions.CaptionStyle
import com.example.ui.ClipperApp
import com.example.ui.theme.CaptionFontCatalog
import com.example.ui.theme.ClipperTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appName_isClipper() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Clipper", context.getString(R.string.app_name))
    }

    @Test
    fun captionCatalog_hasTenBundledOfflineFontsThatRender() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(10, CaptionFontCatalog.size)

        CaptionFontCatalog.forEach { font ->
            val typeface = CaptionBitmapRenderer.loadCaptionTypeface(context, font.id)
            assertNotNull("Font ${font.displayName} failed to load", typeface)
        }

        val card = CaptionBitmapRenderer.renderStandaloneCardBitmap(
            context = context,
            text = "Clipper *Studio* Test",
            style = CaptionStyle(fontId = CaptionFontCatalog.first().id),
            cardWidthPx = 400,
            cardHeightPx = 500,
        )
        assertEquals(400, card.width)
        assertEquals(500, card.height)
        card.recycle()
    }

    @Test
    fun dashboard_rendersAndSwitchesAcrossAllFiveWorkspaces() {
        composeTestRule.setContent {
            ClipperTheme {
                ClipperApp()
            }
        }
        composeTestRule.waitForIdle()

        // Default Media Downloader workspace
        composeTestRule.onNodeWithTag("fetch_mode_single").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_mode_batch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_url_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_cookies_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_lazy_column")
            .performScrollToNode(hasTestTag("fetch_inspect_button"))
        composeTestRule.onNodeWithTag("fetch_inspect_button").assertIsDisplayed()

        // Switch to GPU Upscale workspace
        composeTestRule.onNodeWithTag("nav_tab_upscale").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("upscale_pick_video_button").assertIsDisplayed()

        // Switch to Cutout workspace
        composeTestRule.onNodeWithTag("nav_tab_cutout").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cutout_pick_image_button").assertIsDisplayed()

        // Switch to Voice Studio workspace
        composeTestRule.onNodeWithTag("nav_tab_voice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_cat_all").assertIsDisplayed()

        // Switch to Pro Media Lab workspace
        composeTestRule.onNodeWithTag("nav_tab_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pro_lab_update_engine_button").assertIsDisplayed()
    }

    @Test
    fun batchQueue_addsMultipleUrlsAndDisplaysGlobalProgress() {
        composeTestRule.setContent {
            ClipperTheme {
                ClipperApp()
            }
        }
        composeTestRule.waitForIdle()

        // Switch downloader to Batch Queue mode
        composeTestRule.onNodeWithTag("fetch_mode_batch").performClick()
        composeTestRule.waitForIdle()

        // Enter multiple URLs and enqueue them
        composeTestRule.onNodeWithTag("batch_urls_input")
            .performTextInput("https://example.com/clip1.mp4\nhttps://example.com/clip2.mp4")
        composeTestRule.onNodeWithTag("fetch_lazy_column")
            .performScrollToNode(hasTestTag("batch_add_button"))
        composeTestRule.onNodeWithTag("batch_add_button").performClick()
        composeTestRule.waitForIdle()

        // Global progress card and queue controls should appear
        composeTestRule.onNodeWithTag("fetch_lazy_column")
            .performScrollToNode(hasTestTag("batch_global_progress_bar"))
        composeTestRule.onNodeWithTag("batch_global_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_lazy_column")
            .performScrollToNode(hasTestTag("batch_start_button"))
        composeTestRule.onNodeWithTag("batch_start_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_lazy_column")
            .performScrollToNode(hasTestTag("batch_item_row_0"))
        composeTestRule.onNodeWithTag("batch_item_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fetch_lazy_column")
            .performScrollToNode(hasTestTag("batch_item_row_1"))
        composeTestRule.onNodeWithTag("batch_item_row_1").assertIsDisplayed()
    }

    @Test
    fun velocityStudio_opensFromProLabAndSwitchesAutoAndCustomCurveModes() {
        composeTestRule.setContent {
            ClipperTheme {
                ClipperApp()
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Pro Lab and open Velocity Studio
        composeTestRule.onNodeWithTag("nav_tab_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_tool_velocity").performClick()
        composeTestRule.waitForIdle()

        // Verify media attach controls and Auto Velocity mode
        composeTestRule.onNodeWithTag("velocity_pick_video_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("velocity_pick_music_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("velocity_mode_auto").assertIsDisplayed()
        composeTestRule.onNodeWithTag("velocity_mode_curve").assertIsDisplayed()
        composeTestRule.onNodeWithTag("velocity_lazy_column")
            .performScrollToNode(hasTestTag("velocity_curve_canvas"))
        composeTestRule.onNodeWithTag("velocity_curve_canvas").assertIsDisplayed()

        // Switch to Custom Curve (CapCut-style) mode and verify presets + add point control
        composeTestRule.onNodeWithTag("velocity_mode_curve").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("velocity_lazy_column")
            .performScrollToNode(hasTestTag("velocity_preset_hero"))
        composeTestRule.onNodeWithTag("velocity_preset_hero").performClick()
        composeTestRule.onNodeWithTag("velocity_lazy_column")
            .performScrollToNode(hasTestTag("velocity_add_point_button"))
        composeTestRule.onNodeWithTag("velocity_add_point_button").performClick()
        composeTestRule.onNodeWithTag("velocity_lazy_column")
            .performScrollToNode(hasTestTag("velocity_export_button"))
        composeTestRule.onNodeWithTag("velocity_export_button").assertIsDisplayed()
    }
}



