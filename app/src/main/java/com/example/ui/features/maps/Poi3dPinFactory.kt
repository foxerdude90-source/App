package com.example.ui.features.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.core.maps.PoiPinIconType
import org.maplibre.android.maps.Style

/**
 * Generates upright, 3D-sculpted, pointed teardrop map marker bitmaps with grounded contact
 * shadows, glossy spherical shading, and category-specific white vector symbols for every
 * Material Finder category (Coffee, Restaurants, ATMs, Petrol, Hospitals, Metro, Shopping, etc.).
 */
internal object Poi3dPinFactory {

    fun registerAllCategoryPins(style: Style) {
        PoiPinIconType.entries.forEach { pinType ->
            if (style.getImage(pinType.pinImageId) == null) {
                style.addImage(pinType.pinImageId, create3dCategoryPinBitmap(pinType))
            }
        }
    }

    fun create3dCategoryPinBitmap(pinType: PoiPinIconType): Bitmap {
        val w = 92
        val h = 114
        val cx = w / 2f
        val headCy = 37f
        val headR = 27f
        val tipY = 105f

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val accentColor = Color.parseColor(pinType.accentHex)
        val darkColor = Color.parseColor(pinType.darkHex)
        val lightHighlight = blendWithWhite(accentColor, 0.34f)

        // 1. Grounded elliptical contact shadow right beneath the pointed tip
        paint.shader = RadialGradient(
            cx, tipY + 1f, 20f,
            intArrayOf(
                Color.argb(120, 15, 23, 42),
                Color.argb(40, 15, 23, 42),
                Color.argb(0, 15, 23, 42),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(RectF(cx - 18f, tipY - 4f, cx + 18f, tipY + 6.5f), paint)
        paint.shader = null

        // 2. Symmetrical upright 3D pointed teardrop contour
        val pinPath = Path().apply {
            moveTo(cx, tipY)
            cubicTo(
                cx - 5f, tipY - 21f,
                cx - headR, headCy + 21f,
                cx - headR, headCy,
            )
            arcTo(
                RectF(cx - headR, headCy - headR, cx + headR, headCy + headR),
                180f,
                180f,
                false,
            )
            cubicTo(
                cx + headR, headCy + 21f,
                cx + 5f, tipY - 21f,
                cx, tipY,
            )
            close()
        }

        // Crisp pure-white outer border halo
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        canvas.drawPath(pinPath, paint)

        // 3D spherical radial fill (lit top-left, deep shaded bottom-right & pointed neck)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - 10f, headCy - 12f, 56f,
            intArrayOf(lightHighlight, accentColor, darkColor),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(pinPath, paint)
        paint.shader = null

        // Subtle dark outer contour rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = darkColor
        canvas.drawPath(pinPath, paint)

        // 3. Soft 3D specular gloss arc on upper-left dome
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            cx - 18f, headCy - 22f, cx, headCy - 6f,
            Color.argb(115, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(RectF(cx - 19f, headCy - 23f, cx + 2f, headCy - 9f), paint)
        paint.shader = null

        // 4. Category-specific crisp white vector logo inside the 3D pin head
        drawCategoryVectorLogo(canvas, pinType, cx, headCy)

        return bmp
    }

    private fun drawCategoryVectorLogo(
        canvas: Canvas,
        type: PoiPinIconType,
        cx: Float,
        cy: Float,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (type) {
            PoiPinIconType.COFFEE -> {
                // Cup body + handle + saucer
                canvas.drawRoundRect(RectF(cx - 11f, cy - 8f, cx + 7f, cy + 7f), 3f, 5f, fill)
                canvas.drawArc(RectF(cx + 4f, cy - 5f, cx + 12f, cy + 3f), -90f, 180f, false, stroke)
                canvas.drawLine(cx - 13f, cy + 10.5f, cx + 10f, cy + 10.5f, stroke)
            }
            PoiPinIconType.RESTAURANT -> {
                // Fork (left) + Knife (right)
                canvas.drawLine(cx - 6f, cy - 11f, cx - 6f, cy + 11f, stroke)
                canvas.drawLine(cx - 9.5f, cy - 11f, cx - 9.5f, cy - 3f, stroke)
                canvas.drawLine(cx - 2.5f, cy - 11f, cx - 2.5f, cy - 3f, stroke)
                canvas.drawLine(cx - 9.5f, cy - 3f, cx - 2.5f, cy - 3f, stroke)
                val knife = Path().apply {
                    moveTo(cx + 6f, cy - 11f)
                    quadTo(cx + 11f, cy - 5f, cx + 7f, cy + 1f)
                    lineTo(cx + 7f, cy + 11f)
                    lineTo(cx + 4.5f, cy + 11f)
                    lineTo(cx + 4.5f, cy - 11f)
                    close()
                }
                canvas.drawPath(knife, fill)
            }
            PoiPinIconType.BAR -> {
                // Cocktail glass
                val glass = Path().apply {
                    moveTo(cx - 11f, cy - 10f)
                    lineTo(cx + 11f, cy - 10f)
                    lineTo(cx, cy + 1f)
                    close()
                }
                canvas.drawPath(glass, fill)
                canvas.drawLine(cx, cy + 1f, cx, cy + 10f, stroke)
                canvas.drawLine(cx - 6f, cy + 10f, cx + 6f, cy + 10f, stroke)
            }
            PoiPinIconType.ATM -> {
                // Banknote / ATM card with currency emblem
                canvas.drawRoundRect(RectF(cx - 12f, cy - 8.5f, cx + 12f, cy + 8.5f), 3f, 3f, stroke)
                canvas.drawLine(cx - 12f, cy - 3f, cx + 12f, cy - 3f, stroke)
                canvas.drawCircle(cx, cy + 3f, 2.8f, fill)
            }
            PoiPinIconType.FUEL -> {
                // Gas pump body + screen + hose
                canvas.drawRoundRect(RectF(cx - 10f, cy - 10f, cx + 4f, cy + 10f), 2.5f, 2.5f, fill)
                val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(type.accentHex) }
                canvas.drawRoundRect(RectF(cx - 7.5f, cy - 7.5f, cx + 1.5f, cy - 1.5f), 1.5f, 1.5f, screenPaint)
                val hose = Path().apply {
                    moveTo(cx + 4f, cy - 4f)
                    lineTo(cx + 9.5f, cy - 4f)
                    lineTo(cx + 9.5f, cy + 6f)
                    lineTo(cx + 6.5f, cy + 6f)
                }
                canvas.drawPath(hose, stroke)
            }
            PoiPinIconType.EV -> {
                // High-voltage EV lightning bolt
                val bolt = Path().apply {
                    moveTo(cx + 2f, cy - 12f)
                    lineTo(cx - 9f, cy + 1f)
                    lineTo(cx - 1f, cy + 1f)
                    lineTo(cx - 3f, cy + 12f)
                    lineTo(cx + 9f, cy - 2f)
                    lineTo(cx + 1f, cy - 2f)
                    close()
                }
                canvas.drawPath(bolt, fill)
            }
            PoiPinIconType.HOSPITAL -> {
                // Crisp medical cross
                canvas.drawRoundRect(RectF(cx - 4f, cy - 11f, cx + 4f, cy + 11f), 2f, 2f, fill)
                canvas.drawRoundRect(RectF(cx - 11f, cy - 4f, cx + 11f, cy + 4f), 2f, 2f, fill)
            }
            PoiPinIconType.PHARMACY -> {
                // Pharmacy bottle / mortar with cross
                canvas.drawRoundRect(RectF(cx - 9f, cy - 6f, cx + 9f, cy + 10f), 3.5f, 3.5f, fill)
                canvas.drawRoundRect(RectF(cx - 5f, cy - 11f, cx + 5f, cy - 6f), 2f, 2f, fill)
                val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(type.accentHex) }
                canvas.drawRect(RectF(cx - 2f, cy - 3f, cx + 2f, cy + 7f), cut)
                canvas.drawRect(RectF(cx - 5f, cy, cx + 5f, cy + 4f), cut)
            }
            PoiPinIconType.PARKING -> {
                val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 26f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("P", cx, cy + 9f, txt)
            }
            PoiPinIconType.PARK -> {
                // Layered pine tree
                val tree = Path().apply {
                    moveTo(cx, cy - 12f)
                    lineTo(cx + 9f, cy - 1f)
                    lineTo(cx + 4f, cy - 1f)
                    lineTo(cx + 11f, cy + 7f)
                    lineTo(cx - 11f, cy + 7f)
                    lineTo(cx - 4f, cy - 1f)
                    lineTo(cx - 9f, cy - 1f)
                    close()
                }
                canvas.drawPath(tree, fill)
                canvas.drawRect(RectF(cx - 2.2f, cy + 7f, cx + 2.2f, cy + 12f), fill)
            }
            PoiPinIconType.GYM -> {
                // Dumbbell
                canvas.drawLine(cx - 10f, cy, cx + 10f, cy, stroke)
                canvas.drawRoundRect(RectF(cx - 12f, cy - 7f, cx - 7.5f, cy + 7f), 2f, 2f, fill)
                canvas.drawRoundRect(RectF(cx + 7.5f, cy - 7f, cx + 12f, cy + 7f), 2f, 2f, fill)
            }
            PoiPinIconType.HOTEL -> {
                // Bed silhouette
                canvas.drawLine(cx - 12f, cy + 7f, cx - 12f, cy - 6f, stroke)
                canvas.drawLine(cx - 12f, cy + 4f, cx + 12f, cy + 4f, stroke)
                canvas.drawLine(cx + 12f, cy + 7f, cx + 12f, cy - 1f, stroke)
                canvas.drawCircle(cx - 6.5f, cy - 2.5f, 3f, fill)
                canvas.drawRoundRect(RectF(cx - 2f, cy - 4f, cx + 11f, cy + 3f), 3f, 3f, fill)
            }
            PoiPinIconType.GROCERY -> {
                // Shopping cart
                val cart = Path().apply {
                    moveTo(cx - 12f, cy - 8f)
                    lineTo(cx - 8f, cy - 8f)
                    lineTo(cx - 5f, cy + 4f)
                    lineTo(cx + 9f, cy + 4f)
                    lineTo(cx + 11f, cy - 4f)
                    lineTo(cx - 6.5f, cy - 4f)
                }
                canvas.drawPath(cart, stroke)
                canvas.drawCircle(cx - 3.5f, cy + 9f, 2.3f, fill)
                canvas.drawCircle(cx + 7.5f, cy + 9f, 2.3f, fill)
            }
            PoiPinIconType.SHOPPING -> {
                // Shopping bag
                canvas.drawRoundRect(RectF(cx - 10f, cy - 5f, cx + 10f, cy + 10f), 3f, 3f, fill)
                canvas.drawArc(RectF(cx - 5.5f, cy - 11f, cx + 5.5f, cy - 1f), 180f, 180f, false, stroke)
            }
            PoiPinIconType.BUS -> {
                // Front bus silhouette
                canvas.drawRoundRect(RectF(cx - 9.5f, cy - 10f, cx + 9.5f, cy + 7.5f), 3.5f, 3.5f, fill)
                val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(type.accentHex) }
                canvas.drawRoundRect(RectF(cx - 7f, cy - 6.5f, cx + 7f, cy + 1f), 1.5f, 1.5f, cut)
                canvas.drawCircle(cx - 5.5f, cy + 4.5f, 1.8f, cut)
                canvas.drawCircle(cx + 5.5f, cy + 4.5f, 1.8f, cut)
                canvas.drawLine(cx - 6.5f, cy + 7.5f, cx - 6.5f, cy + 10.5f, stroke)
                canvas.drawLine(cx + 6.5f, cy + 7.5f, cx + 6.5f, cy + 10.5f, stroke)
            }
            PoiPinIconType.METRO -> {
                // Metro train front + rails
                canvas.drawRoundRect(RectF(cx - 8.5f, cy - 10.5f, cx + 8.5f, cy + 6f), 5f, 5f, fill)
                val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(type.accentHex) }
                canvas.drawRoundRect(RectF(cx - 6f, cy - 6.5f, cx + 6f, cy), 2f, 2f, cut)
                canvas.drawCircle(cx - 4.5f, cy + 3.2f, 1.6f, cut)
                canvas.drawCircle(cx + 4.5f, cy + 3.2f, 1.6f, cut)
                canvas.drawLine(cx - 5.5f, cy + 6.5f, cx - 9f, cy + 11f, stroke)
                canvas.drawLine(cx + 5.5f, cy + 6.5f, cx + 9f, cy + 11f, stroke)
            }
            PoiPinIconType.CINEMA -> {
                // Clapperboard
                canvas.drawRoundRect(RectF(cx - 10.5f, cy - 6f, cx + 10.5f, cy + 9f), 2.5f, 2.5f, fill)
                canvas.drawLine(cx - 10f, cy - 9f, cx + 10f, cy - 9f, stroke)
                val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(type.accentHex); strokeWidth = 2.2f }
                canvas.drawLine(cx - 4f, cy - 6f, cx, cy - 1f, cut)
                canvas.drawLine(cx + 3f, cy - 6f, cx + 7f, cy - 1f, cut)
            }
            PoiPinIconType.CULTURE -> {
                // Classical museum pediment & columns
                val roof = Path().apply {
                    moveTo(cx, cy - 11f)
                    lineTo(cx + 12f, cy - 4f)
                    lineTo(cx - 12f, cy - 4f)
                    close()
                }
                canvas.drawPath(roof, fill)
                canvas.drawRect(RectF(cx - 9f, cy - 2f, cx - 6f, cy + 7f), fill)
                canvas.drawRect(RectF(cx - 1.5f, cy - 2f, cx + 1.5f, cy + 7f), fill)
                canvas.drawRect(RectF(cx + 6f, cy - 2f, cx + 9f, cy + 7f), fill)
                canvas.drawRect(RectF(cx - 11.5f, cy + 7f, cx + 11.5f, cy + 10f), fill)
            }
            PoiPinIconType.CAR_SERVICE -> {
                // Car silhouette
                val car = Path().apply {
                    moveTo(cx - 8f, cy - 7f)
                    lineTo(cx + 8f, cy - 7f)
                    lineTo(cx + 11f, cy)
                    lineTo(cx + 11f, cy + 7f)
                    lineTo(cx - 11f, cy + 7f)
                    lineTo(cx - 11f, cy)
                    close()
                }
                canvas.drawPath(car, fill)
                val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(type.accentHex) }
                canvas.drawRect(RectF(cx - 7f, cy - 4.5f, cx + 7f, cy - 0.5f), cut)
                canvas.drawCircle(cx - 6.5f, cy + 3.5f, 1.8f, cut)
                canvas.drawCircle(cx + 6.5f, cy + 3.5f, 1.8f, cut)
            }
            PoiPinIconType.POST -> {
                // Envelope
                canvas.drawRoundRect(RectF(cx - 11f, cy - 7.5f, cx + 11f, cy + 7.5f), 2.5f, 2.5f, stroke)
                val flap = Path().apply {
                    moveTo(cx - 10.5f, cy - 6.5f)
                    lineTo(cx, cy + 1.5f)
                    lineTo(cx + 10.5f, cy - 6.5f)
                }
                canvas.drawPath(flap, stroke)
            }
            PoiPinIconType.SERVICE_GENERIC -> {
                // Crisp storefront / star diamond emblem
                val star = Path().apply {
                    moveTo(cx, cy - 11f)
                    lineTo(cx + 3.5f, cy - 3.5f)
                    lineTo(cx + 11f, cy)
                    lineTo(cx + 3.5f, cy + 3.5f)
                    lineTo(cx, cy + 11f)
                    lineTo(cx - 3.5f, cy + 3.5f)
                    lineTo(cx - 11f, cy)
                    lineTo(cx - 3.5f, cy - 3.5f)
                    close()
                }
                canvas.drawPath(star, fill)
            }
        }
    }

    private fun blendWithWhite(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
