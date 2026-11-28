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
    fun openGraphExtractor_handlesReversedAttrsEntitiesAndRelativeUrls() {
        val html = """<html><head><meta content="/media/clip.mp4?a=1&amp;b=2" property="og:video" /></head></html>"""
        assertEquals(
            "https://cdn.example.org/media/clip.mp4?a=1&b=2",
            DirectMediaFetcher.extractOpenGraphMediaUrl(html, baseUrl = "https://cdn.example.org/watch/123"),
        )
    }

    @Test
    fun urlValidation_rejectsPrivateIpv6AndUnencodedQueryPasses() {
        assertTrue(DirectMediaFetcher.validateUrl("http://[::1]/clip.mp4") is UrlValidationResult.Rejected)
        assertTrue(DirectMediaFetcher.validateUrl("https://cdn.example.org/clip.mp4?tag=a|b") is UrlValidationResult.Valid)
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

    @Test
    fun youtubeDlQualityParser_deduplicatesResolutionsAndBuildsAudioMuxSelectors() {
        val raw = listOf(
            com.example.core.media.YoutubeDlMediaEngine.RawStreamDescriptor(
                formatId = "137",
                ext = "mp4",
                vcodec = "avc1.640028",
                acodec = "none",
                width = 1920,
                height = 1080,
                fps = 30,
                fileSizeBytes = 42_000_000L,
            ),
            com.example.core.media.YoutubeDlMediaEngine.RawStreamDescriptor(
                formatId = "22",
                ext = "mp4",
                vcodec = "avc1.64001F",
                acodec = "mp4a.40.2",
                width = 1280,
                height = 720,
                fps = 30,
                fileSizeBytes = 18_000_000L,
            ),
            com.example.core.media.YoutubeDlMediaEngine.RawStreamDescriptor(
                formatId = "140",
                ext = "m4a",
                vcodec = "none",
                acodec = "mp4a.40.2",
                width = 0,
                height = 0,
                fps = 0,
                fileSizeBytes = 4_200_000L,
                formatNote = "128k",
            ),
        )
        val options = com.example.core.media.YoutubeDlMediaEngine.buildQualityOptions(raw, hasFfmpeg = true)
        assertTrue(options.any { it.id == "auto_best_mp4" })
        assertTrue(options.any { it.id == "auto_best_audio" && it.extractAudioOnly })
        val fhd = options.first { it.heightPx == 1080 }
        assertTrue(fhd.requiresAudioMux)
        assertTrue(fhd.formatSelector.contains("137+bestaudio"))
        val hd = options.first { it.heightPx == 720 }
        assertFalse(hd.requiresAudioMux)
        assertEquals("22", hd.formatSelector)
    }

    @Test
    fun videoUpscaler_computesEvenAlignedTargetDimensions() {
        val (w2x, h2x) = com.example.core.upscale.VideoUpscalerEngine.computeTargetDimensions(
            srcWidth = 640,
            srcHeight = 360,
            preset = com.example.core.upscale.UpscaleTargetPreset.SCALE_2X,
        )
        assertEquals(1280, w2x)
        assertEquals(720, h2x)
        assertEquals(0, w2x % 2)
        assertEquals(0, h2x % 2)
    }

    @Test
    fun proAudioDsp_softLimiterPreventsClippingAboveUnity() {
        val linear = com.example.core.studio.ProAudioDspProcessor.dbToLinearGain(18f)
        assertTrue(linear > 7.5f)
        val limited = com.example.core.studio.ProAudioDspProcessor.processSample(
            normalizedSample = 0.9f,
            linearGain = linear,
            envelope = 1.0f,
            softLimiter = true,
        )
        assertTrue(limited <= 1.0f && limited > 0.85f)
    }

    @Test
    fun voiceStudio_catalogHas16VoicesAndPitchTrackerDetectsSineF0() {
        assertEquals(16, com.example.core.voice.VoicePresetCatalog.builtInVoices.size)

        val sr = 22050
        val targetHz = 140.0
        val pcm = FloatArray(sr * 2) { i ->
            (0.45 * kotlin.math.sin(2.0 * kotlin.math.PI * targetHz * i / sr)).toFloat()
        }
        val (profile, analysis) = com.example.core.voice.VoiceStudioDspEngine.analyzeVoiceSamplePcm(
            samples = pcm,
            sampleRate = sr,
            voiceName = "Test Sine Clone",
            sourceLabel = "UnitTest",
        )
        assertTrue(profile.isCloned)
        assertEquals(targetHz.toFloat(), analysis.fundamentalHz, 6.0f)

        val clauses = com.example.core.voice.VoiceStudioDspEngine.splitScriptIntoClauses(
            script = "Hello world. Welcome back, ready to build!",
            phrasing = com.example.core.voice.PhrasingStyle.NATURAL,
        )
        assertEquals(2, clauses.size)
    }

    @Test
    fun urlSanitizer_stripsTrackingParamsWhilePreservingVideoIds() {
        val ytRaw = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123XYZ&t=42&utm_source=share"
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42",
            com.example.core.media.YoutubeDlMediaEngine.sanitizeMediaUrl(ytRaw),
        )

        val igRaw = "https://www.instagram.com/reel/C8xyz123/?igsh=MW9hZ2&fqnt=1"
        assertEquals(
            "https://www.instagram.com/reel/C8xyz123/",
            com.example.core.media.YoutubeDlMediaEngine.sanitizeMediaUrl(igRaw),
        )
    }

    @Test
    fun cookieManager_formatsNetscapeCookiesAndDetectsAuthErrors() {
        val netscape = com.example.core.media.CookieSessionManager.convertRawCookiesToNetscape(
            url = "https://www.instagram.com/accounts/login/",
            rawCookieHeader = "sessionid=abc123; csrftoken=xyz789",
            expirationEpochSec = 1893456000L,
        )
        assertTrue(netscape.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(netscape.contains(".instagram.com\tTRUE\t/\tTRUE\t1893456000\tsessionid\tabc123"))
        assertTrue(netscape.contains(".instagram.com\tTRUE\t/\tTRUE\t1893456000\tcsrftoken\txyz789"))

        assertTrue(com.example.core.media.CookieSessionManager.isAuthOrRateLimitError("ERROR: Login required to view this reel"))
        assertTrue(com.example.core.media.CookieSessionManager.isAuthOrRateLimitError("HTTP Error 429: Too Many Requests rate-limit reached"))
        assertFalse(com.example.core.media.CookieSessionManager.isAuthOrRateLimitError("Unable to resolve host"))
    }

    @Test
    fun voiceStudioDspChain_isCleanAndWsolaPitchShiftPreservesSmoothness() {
        val sr = 24000
        val sine = FloatArray(sr) { i ->
            (0.35 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * i / sr)).toFloat()
        }
        val voice = com.example.core.voice.VoicePresetCatalog.builtInVoices.first()
        val mastered = com.example.core.voice.VoiceStudioDspEngine.applyVoiceStudioDspChain(
            input = sine,
            sampleRate = sr,
            profile = voice,
            params = com.example.core.voice.VoiceGenerationParams(),
        )
        assertEquals(sine.size, mastered.size)
        // Verify zero NaN, bounded amplitude, and smooth sample-to-sample derivative (no crackle/clicks)
        var maxDelta = 0f
        for (i in 1 until mastered.size) {
            assertFalse(mastered[i].isNaN())
            assertTrue(kotlin.math.abs(mastered[i]) <= 0.96f)
            val d = kotlin.math.abs(mastered[i] - mastered[i - 1])
            if (d > maxDelta) maxDelta = d
        }
        assertTrue("Expected smooth crackle-free signal, got maxDelta=$maxDelta", maxDelta < 0.15f)

        val shifted = com.example.core.voice.VoiceStudioDspEngine.pitchShiftWsola(sine, sr, semitones = 3.0f)
        assertEquals(sine.size, shifted.size)
        assertTrue(shifted.all { !it.isNaN() && kotlin.math.abs(it) <= 1.2f })
    }

    @Test
    fun batchQueueHelper_parsesSanitizesDeduplicatesAndComputesGlobalProgress() {
        val rawInput = """
            https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=track123
            https://www.instagram.com/reel/C8xyz123/?igsh=MW9hZ2&fqnt=1
            https://www.youtube.com/watch?v=dQw4w9WgXcQ&utm_source=copy
            not_a_url
        """.trimIndent()

        val parsed = com.example.core.media.BatchQueueHelper.parseMultipleUrls(rawInput)
        assertEquals(2, parsed.size)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", parsed[0])
        assertEquals("https://www.instagram.com/reel/C8xyz123/", parsed[1])

        val items = listOf(
            com.example.core.media.BatchQueueItem(
                id = "1",
                rawUrl = parsed[0],
                sanitizedUrl = parsed[0],
                status = com.example.core.media.BatchItemStatus.COMPLETED,
                progress = 1f,
            ),
            com.example.core.media.BatchQueueItem(
                id = "2",
                rawUrl = parsed[1],
                sanitizedUrl = parsed[1],
                status = com.example.core.media.BatchItemStatus.DOWNLOADING,
                progress = 0.5f,
            ),
            com.example.core.media.BatchQueueItem(
                id = "3",
                rawUrl = "https://example.org/video3.mp4",
                sanitizedUrl = "https://example.org/video3.mp4",
                status = com.example.core.media.BatchItemStatus.QUEUED,
                progress = 0f,
            ),
            com.example.core.media.BatchQueueItem(
                id = "4",
                rawUrl = "https://example.org/video4.mp4",
                sanitizedUrl = "https://example.org/video4.mp4",
                status = com.example.core.media.BatchItemStatus.FAILED,
                progress = 0f,
            ),
        )

        val summary = com.example.core.media.BatchQueueHelper.computeSummary(items)
        assertEquals(4, summary.totalCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.failedCount)
        assertEquals(1, summary.queuedCount)
        assertEquals(2, summary.activeIndex)
        // Item 1 (1.0) + Item 2 (0.12 + 0.88 * 0.5 = 0.56) + Item 3 (0.0) + Item 4 (1.0) = 2.56 / 4 = 0.64
        assertEquals(0.64f, summary.globalProgress, 0.005f)
    }

    @Test
    fun velocityCurveMath_monotoneHermiteSplineIsSmoothAndTimeIntegralIsExact() {
        // Exact integral check: constant 2.0x speed halves duration; 0.5x doubles duration
        val flat2x = listOf(
            com.example.core.velocity.VelocityKeyframe("a", 0f, 2.0f),
            com.example.core.velocity.VelocityKeyframe("b", 1f, 2.0f),
        )
        val lut2x = com.example.core.velocity.VelocityCurveMath.buildTimeRemapLut(flat2x, 10_000L)
        assertEquals(5_000L, lut2x.totalOutputDurationMs)

        val flatHalf = listOf(
            com.example.core.velocity.VelocityKeyframe("a", 0f, 0.5f),
            com.example.core.velocity.VelocityKeyframe("b", 1f, 0.5f),
        )
        val lutHalf = com.example.core.velocity.VelocityCurveMath.buildTimeRemapLut(flatHalf, 10_000L)
        assertEquals(20_000L, lutHalf.totalOutputDurationMs)

        // Monotone spline zero-overshoot & exact pass-through check
        val hero = com.example.core.velocity.VelocityCurveMath.presetKeyframes(
            com.example.core.velocity.VelocityCurvePreset.HERO,
        )
        val minSpd = hero.minOf { it.speed }
        val maxSpd = hero.maxOf { it.speed }
        val sampled = com.example.core.velocity.VelocityCurveMath.sampleCurveForDisplay(hero, samples = 120)
        assertTrue(sampled.all { it >= minSpd - 0.01f && it <= maxSpd + 0.01f })
        assertEquals(0.25f, com.example.core.velocity.VelocityCurveMath.evaluateSplineSpeed(hero, 0.42f), 0.01f)

        // Auto velocity beat-synced curve stays gently paced within slow-mo and fast bounds
        val beats = listOf(
            com.example.core.velocity.DetectedBeat(1500L, 0.25f, 0.95f),
            com.example.core.velocity.DetectedBeat(3000L, 0.50f, 1.00f),
            com.example.core.velocity.DetectedBeat(4500L, 0.75f, 0.90f),
        )
        val settings = com.example.core.velocity.AutoVelocitySettings(
            isAutoTuning = false,
            frequency = com.example.core.velocity.AutoVelocityFrequency.EVERY_BEAT,
            smoothness = 0.80f,
            maxSlowMoSpeed = 0.30f,
            maxFastSpeed = 2.60f,
        )
        val autoCurve = com.example.core.velocity.VelocityCurveMath.generateAutoVelocityCurve(beats, settings, bpm = 120f)
        assertTrue(autoCurve.size >= 11)
        assertTrue(autoCurve.all { it.speed in 0.29f..2.61f })
    }

    @Test
    fun beatDetectionEngine_detects120BpmKickPulsesFromPcm() {
        val sr = 22050
        val durationSec = 6
        val pcm = FloatArray(sr * durationSec)
        // Synthesize crisp 120 BPM kick-drum pulses (1 beat every 0.50 seconds)
        for (beatIdx in 0 until 12) {
            val startSample = (beatIdx * 0.5 * sr).toInt()
            val burstLen = (0.09 * sr).toInt()
            for (i in 0 until burstLen) {
                val idx = startSample + i
                if (idx >= pcm.size) break
                val env = kotlin.math.exp(-i.toDouble() / (0.025 * sr))
                val freq = 130.0 - 70.0 * (i.toDouble() / burstLen)
                pcm[idx] = (0.85 * env * kotlin.math.sin(2.0 * kotlin.math.PI * freq * i / sr)).toFloat()
            }
        }

        val analysis = com.example.core.velocity.BeatDetectionEngine.analyzePcmForBeats(
            samples = pcm,
            sampleRate = sr,
            durationMs = durationSec * 1000L,
        )
        assertEquals(96, analysis.waveformBars.size)
        assertTrue("Expected at least 6 detected beats, got ${analysis.beats.size}", analysis.beats.size >= 6)
        assertEquals(120f, analysis.bpm, 6.0f)
    }

    @Test
    fun imageStudio_ratioDimensionsAndPaletteQuantizerAreExact() {
        val (w169, h169) = com.example.core.image.ImageStudioEngine.computeRatioOutputDimensions(
            srcWidth = 4000,
            srcHeight = 3000,
            ratioW = 16f,
            ratioH = 9f,
            resolution = com.example.core.image.RatioOutputResolution.HD_1080,
        )
        assertEquals(1080, w169)
        assertEquals(608, h169)
        assertEquals(0, w169 % 2)
        assertEquals(0, h169 % 2)

        val (w57, h57) = com.example.core.image.ImageStudioEngine.computeRatioOutputDimensions(
            srcWidth = 1000,
            srcHeight = 1000,
            ratioW = 5f,
            ratioH = 7f,
            resolution = com.example.core.image.RatioOutputResolution.QHD_2048,
        )
        assertEquals(2048, h57)
        assertEquals(1462, w57)

        val crimson = 0xFF760107.toInt()
        val cyan = 0xFF06B6D4.toInt()
        val pixels = IntArray(400) { idx -> if (idx < 300) crimson else cyan }
        val swatches = com.example.core.image.ImageStudioEngine.extractDominantPaletteFromPixels(pixels, swatchCount = 2)
        assertEquals(2, swatches.size)
        assertEquals("#760107", swatches[0].hex)
        assertEquals(75, swatches[0].sharePercent)
        assertEquals("#06B6D4", swatches[1].hex)
        assertEquals(25, swatches[1].sharePercent)
    }

    @Test
    fun mapsGeoMath_haversineAreaRouteSnapAndFormattingAreExact() {
        val sf = com.example.core.maps.GeoPoint(37.7749, -122.4194)
        val oakland = com.example.core.maps.GeoPoint(37.8044, -122.2712)
        val distMeters = com.example.core.maps.MapsGeoMath.haversineMeters(sf, oakland)
        assertTrue("Expected ~13.4 km between SF and Oakland, got $distMeters", distMeters in 13_000.0..14_000.0)

        // Shortest-angle wraparound across 0°/360° boundary (350° -> 10° midpoint must be 0°)
        assertEquals(0f, com.example.core.maps.MapsGeoMath.interpolateAngleDegrees(350f, 10f, 0.5f), 0.1f)
        assertEquals(355f, com.example.core.maps.MapsGeoMath.interpolateAngleDegrees(350f, 10f, 0.25f), 0.1f)

        // Spherical polygon area for a ~1km x ~1km square near the equator
        val degPerKm = 1.0 / 111.195
        val square = listOf(
            com.example.core.maps.GeoPoint(0.0, 0.0),
            com.example.core.maps.GeoPoint(0.0, degPerKm),
            com.example.core.maps.GeoPoint(degPerKm, degPerKm),
            com.example.core.maps.GeoPoint(degPerKm, 0.0),
        )
        val areaSqM = com.example.core.maps.MapsGeoMath.sphericalPolygonAreaSqMeters(square)
        assertEquals(1_000_000.0, areaSqM, 15_000.0)

        // Route snapping and off-route detection
        val route = listOf(
            com.example.core.maps.GeoPoint(37.7700, -122.4200),
            com.example.core.maps.GeoPoint(37.7800, -122.4200),
            com.example.core.maps.GeoPoint(37.7800, -122.4100),
        )
        val onRouteMatch = com.example.core.maps.MapsGeoMath.matchPointToRoute(
            position = com.example.core.maps.GeoPoint(37.7750, -122.42005),
            routeGeometry = route,
            offRouteThresholdMeters = 55.0,
        )
        assertFalse(onRouteMatch.isOffRoute)
        assertTrue(onRouteMatch.distanceFromRouteMeters < 10.0)
        assertTrue(onRouteMatch.remainingDistanceMeters > 1000.0)

        val offRouteMatch = com.example.core.maps.MapsGeoMath.matchPointToRoute(
            position = com.example.core.maps.GeoPoint(37.7750, -122.4250),
            routeGeometry = route,
            offRouteThresholdMeters = 55.0,
        )
        assertTrue(offRouteMatch.isOffRoute)
        assertTrue(offRouteMatch.distanceFromRouteMeters > 400.0)

        // Direct coordinate parsing & DMS/unit formatting
        val parsedCoord = com.example.core.maps.MapsGeoMath.tryParseDirectCoordinates("37.7749, -122.4194")
        assertEquals(37.7749, parsedCoord!!.lat, 0.00001)
        assertEquals(-122.4194, parsedCoord.lng, 0.00001)
        assertEquals(null, com.example.core.maps.MapsGeoMath.tryParseDirectCoordinates("Golden Gate Bridge"))

        val dms = com.example.core.maps.MapsGeoMath.formatCoordinatesDms(sf)
        assertTrue(dms.contains("37°46'") && dms.contains("N") && dms.contains("122°25'") && dms.contains("W"))
        assertEquals("2.5 km", com.example.core.maps.MapsGeoMath.formatDistance(2500.0, useImperial = false))
        assertEquals("1.0 mi", com.example.core.maps.MapsGeoMath.formatDistance(1609.344, useImperial = true))
        assertEquals("1.50 ha", com.example.core.maps.MapsGeoMath.formatArea(15_000.0, useImperial = false))
        assertEquals(72 to "km/h", com.example.core.maps.MapsGeoMath.formatSpeed(20f, useImperial = false))
        assertEquals("NE", com.example.core.maps.MapsGeoMath.compassCardinal(45f))
        assertEquals("Turn right onto Market St", com.example.core.maps.MapsGeoMath.buildHumanInstruction("turn", "right", "Market St"))
    }
}





