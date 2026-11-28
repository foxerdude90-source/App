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
        composeTestRule.onNodeWithTag("studio_hub_lazy_column")
            .performScrollToNode(hasTestTag("studio_tool_velocity"))
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

    @Test
    fun imageStudio_rendersCustomRatioAndCollageCells() {
        val src = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888)
        src.eraseColor(0xFF760107.toInt())
        try {
            val out = com.example.core.image.ImageStudioEngine.renderImageRatio(
                source = src,
                config = com.example.core.image.ImageRatioConfig(
                    preset = com.example.core.image.ImageRatioPreset.CUSTOM,
                    customRatioW = 5f,
                    customRatioH = 7f,
                    fitMode = com.example.core.image.RatioFitMode.FIT_BLUR,
                    paddingFraction = 0.05f,
                    cornerRadiusFraction = 0.1f,
                ),
                previewMaxEdge = 350,
            )
            assertEquals(250, out.width)
            assertEquals(350, out.height)
            out.recycle()
        } finally {
            src.recycle()
        }

        val bounds = android.graphics.RectF(20f, 20f, 1020f, 1020f)
        com.example.core.image.CollageLayout.entries.forEach { layout ->
            val cells = com.example.core.image.ImageStudioEngine.computeCollageCells(bounds, layout, gap = 16f)
            assertEquals(layout.slotCount, cells.size)
            cells.forEach { c ->
                org.junit.Assert.assertTrue(c.width() > 50f && c.height() > 50f)
                org.junit.Assert.assertTrue(c.left >= bounds.left - 0.5f && c.right <= bounds.right + 0.5f)
                org.junit.Assert.assertTrue(c.top >= bounds.top - 0.5f && c.bottom <= bounds.bottom + 0.5f)
            }
        }
    }

    @Test
    fun imageStudio_opensFromProLabAndSwitchesModes() {
        composeTestRule.setContent {
            ClipperTheme {
                ClipperApp()
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Pro Lab and open Image Ratio & Studio
        composeTestRule.onNodeWithTag("nav_tab_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_hub_lazy_column")
            .performScrollToNode(hasTestTag("studio_tool_image_studio"))
        composeTestRule.onNodeWithTag("studio_tool_image_studio").performClick()
        composeTestRule.waitForIdle()

        // Ratio Studio default mode + Custom W:H input fields
        composeTestRule.onNodeWithTag("ratio_pick_image_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("image_studio_lazy_column")
            .performScrollToNode(hasTestTag("ratio_preset_custom"))
        composeTestRule.onNodeWithTag("ratio_preset_custom").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ratio_custom_w_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ratio_custom_h_input").assertIsDisplayed()

        // Switch across Collage, Film Filters, and Palette & EXIF modes
        composeTestRule.onNodeWithTag("image_studio_lazy_column")
            .performScrollToNode(hasTestTag("img_mode_collage"))
        composeTestRule.onNodeWithTag("img_mode_collage").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collage_pick_photos_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("img_mode_filters").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("img_mode_palette").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun mapsGeoMath_parsesNominatimOsrmOverpassAndWeatherJson() {
        val nominatimJson = """
            [{"place_id":"101","lat":"37.8199","lon":"-122.4783","name":"Golden Gate Bridge","display_name":"Golden Gate Bridge, San Francisco, California, USA","type":"bridge"}]
        """.trimIndent()
        val places = com.example.core.maps.MapsGeoMath.parseNominatimSearchJson(nominatimJson)
        assertEquals(1, places.size)
        assertEquals("Golden Gate Bridge", places[0].name)
        assertEquals(37.8199, places[0].point.lat, 0.0001)

        val osrmJson = """
            {"code":"Ok","routes":[{"distance":4250.5,"duration":390.0,"geometry":{"coordinates":[[-122.4194,37.7749],[-122.4500,37.8000],[-122.4783,37.8199]]},"legs":[{"summary":"US-101 N","steps":[{"name":"Market St","distance":800.0,"duration":90.0,"maneuver":{"type":"depart","modifier":"right","location":[-122.4194,37.7749]}},{"name":"US-101 N","distance":3450.5,"duration":300.0,"maneuver":{"type":"turn","modifier":"left","location":[-122.4500,37.8000]}}]}]}]}
        """.trimIndent()
        val routes = com.example.core.maps.MapsGeoMath.parseOsrmRoutesJson(osrmJson)
        assertEquals(1, routes.size)
        assertEquals(3, routes[0].geometry.size)
        assertEquals(2, routes[0].steps.size)
        assertEquals("Turn left onto US-101 N", routes[0].steps[1].instruction)

        val overpassJson = """
            {"elements":[{"id":55,"lat":37.7760,"lon":-122.4180,"tags":{"name":"Tesla Supercharger Civic"}}]}
        """.trimIndent()
        val pois = com.example.core.maps.MapsGeoMath.parseOverpassPoisJson(
            jsonText = overpassJson,
            center = com.example.core.maps.GeoPoint(37.7749, -122.4194),
            category = com.example.core.maps.PoiCategory.EV_CHARGER,
        )
        assertEquals(1, pois.size)
        assertEquals("Tesla Supercharger Civic", pois[0].name)

        val weatherJson = """
            {"current":{"temperature_2m":19.4,"wind_speed_10m":12.5,"weather_code":1}}
        """.trimIndent()
        val weather = com.example.core.maps.MapsGeoMath.parseOpenMeteoCurrentJson(weatherJson)
        assertNotNull(weather)
        assertEquals(19.4, weather!!.temperatureC, 0.01)
        assertEquals("Partly Cloudy", weather.conditionLabel)
    }

    @Test
    fun maps3D_opensFullScreenFromProLabAndTogglesLayersAndMeasureTool() {
        composeTestRule.setContent {
            ClipperTheme {
                ClipperApp()
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Pro Lab and open full-screen Maps 3D
        composeTestRule.onNodeWithTag("nav_tab_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_hub_lazy_column")
            .performScrollToNode(hasTestTag("studio_tool_maps"))
        composeTestRule.onNodeWithTag("studio_tool_maps").performClick()
        composeTestRule.waitForIdle()

        // Verify full-screen Maps root, MapViewWrapper, search bar, top live compass bar, scale bar, and live GPS prompt are shown
        composeTestRule.onNodeWithTag("maps_fullscreen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_view_wrapper").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maps_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maps_top_compass_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maps_top_compass_bar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maps_scale_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maps_allow_location_banner").assertIsDisplayed()

        // Tap the bigger speed/heading telemetry strip to open Detailed Telemetry Sheet
        composeTestRule.onNodeWithTag("maps_telemetry_detail_trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maps_telemetry_detail_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maps_telemetry_detail_close").performClick()
        composeTestRule.waitForIdle()

        // Open Map Layers sheet and select OSM Streets & Tesla Night styles
        composeTestRule.onNodeWithTag("maps_layers_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maps_layers_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("maps_style_osm_streets").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maps_style_tesla_dark").performClick()
        composeTestRule.waitForIdle()

        // Activate Geodesic Measure tool
        composeTestRule.onNodeWithTag("maps_measure_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maps_measure_hud_card").assertIsDisplayed()

        // Exit full-screen Maps back to Pro Lab hub
        composeTestRule.onNodeWithTag("maps_exit_fullscreen_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pro_lab_update_engine_button").assertIsDisplayed()
    }
}
