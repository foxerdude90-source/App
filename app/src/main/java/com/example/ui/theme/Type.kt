package com.example.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

data class CaptionFontOption(
    val id: String,
    val displayName: String,
    val styleTag: String,
    @FontRes val fontResId: Int,
    val fontFamily: FontFamily,
)

val CaptionFontCatalog: List<CaptionFontOption> = listOf(
    CaptionFontOption(
        id = "montserrat",
        displayName = "Montserrat",
        styleTag = "Modern Sans",
        fontResId = R.font.montserrat,
        fontFamily = FontFamily(Font(R.font.montserrat, FontWeight.Bold)),
    ),
    CaptionFontOption(
        id = "bebas_neue",
        displayName = "Bebas Neue",
        styleTag = "Tall Headline",
        fontResId = R.font.bebas_neue,
        fontFamily = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal)),
    ),
    CaptionFontOption(
        id = "playfair_display",
        displayName = "Playfair Display",
        styleTag = "Editorial Serif",
        fontResId = R.font.playfair_display,
        fontFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Bold)),
    ),
    CaptionFontOption(
        id = "oswald",
        displayName = "Oswald",
        styleTag = "Condensed",
        fontResId = R.font.oswald,
        fontFamily = FontFamily(Font(R.font.oswald, FontWeight.Bold)),
    ),
    CaptionFontOption(
        id = "poppins",
        displayName = "Poppins",
        styleTag = "Geometric",
        fontResId = R.font.poppins,
        fontFamily = FontFamily(Font(R.font.poppins, FontWeight.SemiBold)),
    ),
    CaptionFontOption(
        id = "roboto_mono",
        displayName = "Roboto Mono",
        styleTag = "Tech Code",
        fontResId = R.font.roboto_mono,
        fontFamily = FontFamily(Font(R.font.roboto_mono, FontWeight.Medium)),
    ),
    CaptionFontOption(
        id = "pacifico",
        displayName = "Pacifico",
        styleTag = "Hand Script",
        fontResId = R.font.pacifico,
        fontFamily = FontFamily(Font(R.font.pacifico, FontWeight.Normal)),
    ),
    CaptionFontOption(
        id = "anton",
        displayName = "Anton",
        styleTag = "Heavy Impact",
        fontResId = R.font.anton,
        fontFamily = FontFamily(Font(R.font.anton, FontWeight.Normal)),
    ),
    CaptionFontOption(
        id = "lora",
        displayName = "Lora",
        styleTag = "Warm Story",
        fontResId = R.font.lora,
        fontFamily = FontFamily(Font(R.font.lora, FontWeight.SemiBold)),
    ),
    CaptionFontOption(
        id = "bangers",
        displayName = "Bangers",
        styleTag = "Dynamic Pop",
        fontResId = R.font.bangers,
        fontFamily = FontFamily(Font(R.font.bangers, FontWeight.Normal)),
    ),
)

val ClipperTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)
