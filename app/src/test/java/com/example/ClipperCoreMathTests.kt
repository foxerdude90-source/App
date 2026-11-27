package com.example

import com.example.core.captions.CaptionCue
import com.example.core.captions.RichTextTokenizer
import com.example.core.captions.SubtitleCodec
import com.example.core.cutout.BackgroundRemovalEngine
import com.example.core.cutout.LassoMode
import com.example.core.cutout.NormalizedPoint
import com.example.core.media.DetectedMediaKind
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.ImageCompressorEngine
import com.example.core.media.MediaStoreSaver
import com.example.core.media.UrlValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipperCoreMathTests {

    @Test
    fun urlValidation_acceptsDirectOpenMediaLinks() {
        val res = DirectMediaFetcher.validateUrl("https://cdn.example.org/videos/demo_clip.mp4")
        assertTrue(res is UrlValidationResult.Valid)
    }

    @Test
    fun urlValidation_rejectsPrivateNetworkAndUnsafeSchemes() {
        assertTrue(DirectMediaFetcher.validateUrl("file:///etc/passwd") is UrlValidationResult.Rejected)
        assertTrue(DirectMediaFetcher.validateUrl("http://127.0.0.1/video.mp4") is UrlValidationResult.Rejected)
        assertTrue(DirectMediaFetcher.validateUrl("http://localhost:8080/test.mp4") is UrlValidationResult.Rejected)
        assertTrue(DirectMediaFetcher.validateUrl("http://192.168.1.5/file.jpg") is UrlValidationResult.Rejected)
    }

    @Test
    fun urlValidation_rejectsRestrictedThirdPartyPlatforms() {
        assertTrue(DirectMediaFetcher.validateUrl("https://www.youtube.com/watch?v=abc") is UrlValidationResult.Rejected)
        assertTrue(DirectMediaFetcher.validateUrl("https://www.instagram.com/reel/xyz") is UrlValidationResult.Rejected)
        assertTrue(DirectMediaFetcher.validateUrl("https://www.tiktok.com/@user/video/123") is UrlValidationResult.Rejected)
    }

    @Test
    fun mimeClassification_detectsVideoImageAndAudio() {
        assertEquals(DetectedMediaKind.VIDEO, DirectMediaFetcher.classifyMimeType("video/mp4", "https://a.com/x"))
        assertEquals(DetectedMediaKind.IMAGE, DirectMediaFetcher.classifyMimeType("application/octet-stream", "https://a.com/pic.png"))
        assertEquals(DetectedMediaKind.AUDIO, DirectMediaFetcher.classifyMimeType("audio/mpeg", "https://a.com/track.mp3"))
    }

    @Test
    fun openGraphExtractor_readsDirectVideoTag() {
        val html = """<html><head><meta property="og:video:secure_url" content="https://cdn.example.org/open.mp4" /></head></html>"""
        assertEquals("https://cdn.example.org/open.mp4", DirectMediaFetcher.extractOpenGraphMediaUrl(html))
    }

    @Test
    fun cielabDeltaE_behavesMathematically() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val cyan = 0xFF06B6D4.toInt()

        assertEquals(0f, BackgroundRemovalEngine.deltaE(white, white), 0.001f)
        assertEquals(
            BackgroundRemovalEngine.deltaE(white, cyan),
            BackgroundRemovalEngine.deltaE(cyan, white),
            0.001f,
        )
        assertTrue(BackgroundRemovalEngine.deltaE(white, black) > 95f)
    }

    @Test
    fun smartObjectSegmentation_removesBorderBackgroundAndPreservesCenterSubject() {
        val w = 24
        val h = 24
        val bg = 0xFFFFFFFF.toInt()
        val fg = 0xFF0F172A.toInt()
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 8..15 && y in 8..15) fg else bg
        }

        val mask = BackgroundRemovalEngine.computeSmartObjectMaskFromPixels(pixels, w, h, sensitivity = 20f)
        // Corner pixel should be transparent
        assertTrue((mask[0].toInt() and 0xFF) < 20)
        // Center subject pixel should remain opaque
        assertTrue((mask[12 * w + 12].toInt() and 0xFF) > 220)
    }

    @Test
    fun lassoSelection_keepInsideAndCutInsideAreExact() {
        val w = 20
        val h = 20
        val fullMask = BackgroundRemovalEngine.createFullOpaqueMask(w, h)
        val centerSquare = listOf(
            NormalizedPoint(0.25f, 0.25f),
            NormalizedPoint(0.75f, 0.25f),
            NormalizedPoint(0.75f, 0.75f),
            NormalizedPoint(0.25f, 0.75f),
        )

        val kept = BackgroundRemovalEngine.applyLassoSelection(
            currentMask = fullMask,
            width = w,
            height = h,
            polygon = centerSquare,
            mode = LassoMode.KEEP_INSIDE,
            featherRadius = 0,
        )
        assertEquals(255, kept[10 * w + 10].toInt() and 0xFF)
        assertEquals(0, kept[0].toInt() and 0xFF)

        val cut = BackgroundRemovalEngine.applyLassoSelection(
            currentMask = fullMask,
            width = w,
            height = h,
            polygon = centerSquare,
            mode = LassoMode.CUT_INSIDE,
            featherRadius = 0,
        )
        assertEquals(0, cut[10 * w + 10].toInt() and 0xFF)
        assertEquals(255, cut[0].toInt() and 0xFF)
    }

    @Test
    fun magicWand_erasesOnlyConnectedMatchingRegion() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            if (x < 6) 0xFF06B6D4.toInt() else 0xFF1E293B.toInt()
        }
        val mask = BackgroundRemovalEngine.createFullOpaqueMask(w, h)
        val result = BackgroundRemovalEngine.applyMagicWandFloodFill(
            pixels = pixels,
            currentMask = mask,
            width = w,
            height = h,
            seedNormX = 0.1f,
            seedNormY = 0.5f,
            tolerance = 20f,
        )
        assertEquals(0, result[8 * w + 1].toInt() and 0xFF)
        assertEquals(255, result[8 * w + 12].toInt() and 0xFF)
    }

    @Test
    fun brushStroke_erasesAndRestoresAlphaCorrectly() {
        val w = 30
        val h = 30
        val full = BackgroundRemovalEngine.createFullOpaqueMask(w, h)
        val stroke = listOf(NormalizedPoint(0.5f, 0.5f))

        val erased = BackgroundRemovalEngine.applyBrushStroke(full, w, h, stroke, radiusFraction = 0.2f, erase = true)
        assertEquals(0, erased[15 * w + 15].toInt() and 0xFF)

        val restored = BackgroundRemovalEngine.applyBrushStroke(erased, w, h, stroke, radiusFraction = 0.2f, erase = false)
        assertEquals(255, restored[15 * w + 15].toInt() and 0xFF)
    }

    @Test
    fun subtitleCodec_srtRoundTripAndAutoDistribute() {
        val cues = listOf(
            CaptionCue(1L, 0L, 2500L, "Hello *Clipper* studio"),
            CaptionCue(2L, 2500L, 5200L, "Offline captions ready"),
        )
        val srt = SubtitleCodec.serializeToSrt(cues)
        assertTrue(srt.contains("00:00:00,000 --> 00:00:02,500"))
        assertFalse(srt.contains("*"))

        val parsed = SubtitleCodec.parseSubtitles(srt)
        assertEquals(2, parsed.size)
        assertEquals("Hello Clipper studio", parsed[0].text)
        assertEquals(2500L, parsed[1].startMs)
        assertEquals(5200L, parsed[1].endMs)

        val auto = SubtitleCodec.autoDistributeScript(
            script = "One two three four five six seven eight nine ten",
            totalDurationMs = 10_000L,
            wordsPerCue = 5,
        )
        assertEquals(2, auto.size)
        assertEquals(0L, auto.first().startMs)
        assertEquals(10_000L, auto.last().endMs)
    }

    @Test
    fun richTextTokenizer_parsesHighlightedSpans() {
        val spans = RichTextTokenizer.parseWords("Make *bold* clips *fast*", uppercase = false)
        assertEquals(4, spans.size)
        assertFalse(spans[0].isHighlighted)
        assertTrue(spans[1].isHighlighted)
        assertEquals("bold", spans[1].text)
        assertTrue(spans[3].isHighlighted)
        assertEquals("fast", spans[3].text)
    }

    @Test
    fun formattingAndSamplingUtilities_areAccurate() {
        assertEquals("512 B", MediaStoreSaver.formatByteSize(512L))
        assertEquals("1.00 MB", MediaStoreSaver.formatByteSize(1024L * 1024L))
        assertEquals("01:05", MediaStoreSaver.formatDurationMs(65_000L))
        assertEquals(60, MediaStoreSaver.computeSavingsPercent(1000L, 400L))
        assertEquals(4, ImageCompressorEngine.computeInSampleSize(4000, 3000, 1000))
    }
}
