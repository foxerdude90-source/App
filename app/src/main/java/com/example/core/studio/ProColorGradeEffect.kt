package com.example.core.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ColorLutPreset(
    val label: String,
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val vignette: Float,
    val tealOrange: Float,
) {
    NATURAL("Natural", 0f, 1.0f, 1.0f, 0f, 0f, 0f),
    TEAL_ORANGE("Teal & Orange", 0.05f, 1.15f, 1.12f, 0.08f, 0.28f, 0.65f),
    NOIR_BW("Noir B&W", 0.04f, 1.32f, 0.0f, 0f, 0.42f, 0f),
    GOLDEN_HOUR("Golden Hour", 0.12f, 1.08f, 1.18f, 0.35f, 0.20f, 0.18f),
    CYBER_NEON("Cyber Neon", -0.02f, 1.24f, 1.38f, -0.25f, 0.35f, 0.45f),
    MATTE_FILM("Matte Film", 0.06f, 0.90f, 0.86f, 0.14f, 0.30f, 0.20f),
    VIVID_HDR("Vivid Pop", 0.08f, 1.18f, 1.32f, 0.05f, 0.10f, 0.12f),
}

data class ColorGradeParams(
    val exposure: Float = 0f,        // -1.0 .. +1.0 EV
    val contrast: Float = 1.0f,      // 0.5 .. 1.8
    val saturation: Float = 1.0f,    // 0.0 .. 2.0
    val temperature: Float = 0f,     // -0.6 .. +0.6 (cool .. warm)
    val vignette: Float = 0f,        // 0.0 .. 0.85
    val tealOrange: Float = 0f,      // 0.0 .. 1.0 split-tone
)

/**
 * Custom OpenGL ES Media3 GlEffect for GPU Cinematic LUT & Color Grading.
 */
@UnstableApi
class ColorGradeGlEffect(
    private val params: ColorGradeParams,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ColorGradeShaderProgram(useHdr, params)
    }
}

@UnstableApi
private class ColorGradeShaderProgram(
    useHdr: Boolean,
    private val params: ColorGradeParams,
) : BaseGlShaderProgram(useHdr, 1) {

    private val glProgram: GlProgram

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
        val identity = GlUtil.create4x4IdentityMatrix()
        glProgram.setFloatsUniform("uTransformationMatrix", identity)
        glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatUniform("uExposure", params.exposure.coerceIn(-1f, 1f))
            glProgram.setFloatUniform("uContrast", params.contrast.coerceIn(0.5f, 1.8f))
            glProgram.setFloatUniform("uSaturation", params.saturation.coerceIn(0f, 2f))
            glProgram.setFloatUniform("uTemperature", params.temperature.coerceIn(-0.6f, 0.6f))
            glProgram.setFloatUniform("uVignette", params.vignette.coerceIn(0f, 0.85f))
            glProgram.setFloatUniform("uTealOrange", params.tealOrange.coerceIn(0f, 1f))
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vec4 texCoord = uTexTransformationMatrix * vec4(aFramePosition.xy * 0.5 + 0.5, 0.0, 1.0);
                vTexCoord = texCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexSampler;
            uniform float uExposure;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uTemperature;
            uniform float uVignette;
            uniform float uTealOrange;

            void main() {
                vec3 c = texture2D(uTexSampler, vTexCoord).rgb;

                // 1. Exposure (EV stop scaling)
                c = c * pow(2.0, uExposure);

                // 2. White-balance temperature shift
                c.r += uTemperature * 0.14;
                c.b -= uTemperature * 0.14;
                c = clamp(c, 0.0, 1.0);

                // 3. Contrast around perceptual midpoint
                c = clamp((c - 0.5) * uContrast + 0.5, 0.0, 1.0);

                // 4. Split-tone Cinema Teal (shadows) & Warm Orange (highlights)
                if (uTealOrange > 0.01) {
                    float lum = dot(c, vec3(0.299, 0.587, 0.114));
                    float shadowMask = clamp(1.0 - lum * 1.6, 0.0, 1.0);
                    float highMask = clamp((lum - 0.45) * 1.8, 0.0, 1.0);
                    vec3 teal = vec3(0.0, 0.11, 0.15) * shadowMask * uTealOrange;
                    vec3 orange = vec3(0.14, 0.06, -0.05) * highMask * uTealOrange;
                    c = clamp(c + teal + orange, 0.0, 1.0);
                }

                // 5. Saturation
                float gray = dot(c, vec3(0.299, 0.587, 0.114));
                c = clamp(mix(vec3(gray), c, uSaturation), 0.0, 1.0);

                // 6. Optical radial vignette
                if (uVignette > 0.01) {
                    vec2 p = vTexCoord - vec2(0.5);
                    float dist = length(p) * 1.4142;
                    float vig = smoothstep(1.05, 0.28, dist);
                    c *= mix(1.0, vig, uVignette);
                }

                gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
            }
        """
    }
}

/**
 * Matching CPU preview function for live real-frame Color Grading preview.
 */
object ColorGradePreviewMath {
    fun applyColorGradeCpu(source: Bitmap, params: ColorGradeParams): Bitmap {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val dst = IntArray(w * h)

        val expMul = 2.0.pow(params.exposure.coerceIn(-1f, 1f).toDouble()).toFloat()
        val contrast = params.contrast.coerceIn(0.5f, 1.8f)
        val sat = params.saturation.coerceIn(0f, 2f)
        val temp = params.temperature.coerceIn(-0.6f, 0.6f)
        val vigAmt = params.vignette.coerceIn(0f, 0.85f)
        val split = params.tealOrange.coerceIn(0f, 1f)

        for (y in 0 until h) {
            val py = (y.toFloat() / (h - 1).coerceAtLeast(1)) - 0.5f
            val row = y * w
            for (x in 0 until w) {
                val px = (x.toFloat() / (w - 1).coerceAtLeast(1)) - 0.5f
                val c = src[row + x]
                var r = (((c ushr 16) and 0xFF) / 255f) * expMul + temp * 0.14f
                var g = (((c ushr 8) and 0xFF) / 255f) * expMul
                var b = ((c and 0xFF) / 255f) * expMul - temp * 0.14f

                r = ((r.coerceIn(0f, 1f) - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                g = ((g.coerceIn(0f, 1f) - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                b = ((b.coerceIn(0f, 1f) - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)

                if (split > 0.01f) {
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    val shadowMask = (1f - lum * 1.6f).coerceIn(0f, 1f) * split
                    val highMask = ((lum - 0.45f) * 1.8f).coerceIn(0f, 1f) * split
                    r = (r + 0.14f * highMask).coerceIn(0f, 1f)
                    g = (g + 0.11f * shadowMask + 0.06f * highMask).coerceIn(0f, 1f)
                    b = (b + 0.15f * shadowMask - 0.05f * highMask).coerceIn(0f, 1f)
                }

                val gray = 0.299f * r + 0.587f * g + 0.114f * b
                r = (gray + (r - gray) * sat).coerceIn(0f, 1f)
                g = (gray + (g - gray) * sat).coerceIn(0f, 1f)
                b = (gray + (b - gray) * sat).coerceIn(0f, 1f)

                if (vigAmt > 0.01f) {
                    val dist = hypot(px, py) * 1.4142f
                    val t = ((dist - 0.28f) / (1.05f - 0.28f)).coerceIn(0f, 1f)
                    val vig = 1f - t * t * (3f - 2f * t)
                    val mul = 1f + (vig - 1f) * vigAmt
                    r *= mul
                    g *= mul
                    b *= mul
                }

                dst[row + x] = Color.argb(
                    255,
                    (r * 255f).roundToInt().coerceIn(0, 255),
                    (g * 255f).roundToInt().coerceIn(0, 255),
                    (b * 255f).roundToInt().coerceIn(0, 255),
                )
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(dst, 0, w, 0, 0, w, h)
        return out
    }
}
