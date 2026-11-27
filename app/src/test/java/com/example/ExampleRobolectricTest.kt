package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
@Config(sdk = [35])
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
        composeTestRule.onNodeWithTag("fetch_url_input").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("studio_tool_trimmer").assertIsDisplayed()
    }
}

