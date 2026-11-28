package com.example.ui.features.maps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TurnLeft
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.maps.MapStylePreset
import com.example.core.maps.MapsGeoMath
import com.example.core.maps.PlaceResult
import com.example.core.maps.SavedPlaceBookmark
import com.example.core.maps.TravelProfile
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun travelProfileIcon(profile: TravelProfile): ImageVector = when (profile) {
    TravelProfile.DRIVING -> Icons.Outlined.DirectionsCar
    TravelProfile.BUS -> Icons.Outlined.DirectionsBus
    TravelProfile.METRO -> Icons.Outlined.DirectionsSubway
    TravelProfile.TWO_WHEELER -> Icons.Outlined.TwoWheeler
    TravelProfile.CYCLING -> Icons.Outlined.DirectionsBike
    TravelProfile.WALKING -> Icons.Outlined.DirectionsWalk
}

/**
 * Live Top Compass Tape Bar displaying N / NE / E / SE / S / SW / W / NW sliding smoothly with
 * the user's real hardware compass heading. Tap anywhere on the strip to snap the map North-Up.
 */
@Composable
internal fun TopCompassTapeBar(
    headingDeg: Float,
    mapBearingDeg: Double,
    isDarkHud: Boolean,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedHeading = ((headingDeg % 360f) + 360f) % 360f
    val cardinal = MapsGeoMath.compassCardinal(normalizedHeading)
    val headingInt = normalizedHeading.roundToInt() % 360
    val isMapRotated = abs(((mapBearingDeg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0) > 2.0

    val bg = if (isDarkHud) Color(0xE80B1120) else Color(0xF5FFFFFF)
    val border = if (isDarkHud) Color(0x5538BDF8) else StudioBorder
    val primaryText = if (isDarkHud) StudioWhite else InkObsidian
    val mutedText = if (isDarkHud) Color(0xFF94A3B8) else InkSlate
    val tickColor = if (isDarkHud) Color(0x66CBD5E1) else Color(0x66475569)
    val northCrimson = Color(0xFFEF4444)

    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = remember(primaryText) {
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            color = primaryText,
        )
    }
    val northStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            color = Color(0xFFEF4444),
        )
    }
    val intercardinalStyle = remember(mutedText) {
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = mutedText,
        )
    }

    Surface(
        onClick = onResetNorth,
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.9.dp, border),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag("maps_top_compass_bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Sliding Compass Tape Canvas (N / NE / E / SE / S / SW / W / NW)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val w = size.width
                val h = size.height
                val midX = w / 2f
                val halfSpanDeg = 68f

                // Center lubber indicator triangle at top edge
                val lubber = Path().apply {
                    moveTo(midX, 9.5f)
                    lineTo(midX - 5.5f, 1.5f)
                    lineTo(midX + 5.5f, 1.5f)
                    close()
                }
                drawPath(lubber, color = northCrimson)

                // Draw tick marks & cardinal/intercardinal labels every 15 degrees
                for (deg in 0 until 360 step 15) {
                    val delta = ((deg - normalizedHeading + 540f) % 360f) - 180f
                    if (abs(delta) > halfSpanDeg) continue
                    val x = midX + (delta / halfSpanDeg) * (w / 2f)
                    val edgeFade = (1f - (abs(delta) / halfSpanDeg)).coerceIn(0.18f, 1f)

                    val label = when (deg) {
                        0 -> "N"
                        45 -> "NE"
                        90 -> "E"
                        135 -> "SE"
                        180 -> "S"
                        225 -> "SW"
                        270 -> "W"
                        315 -> "NW"
                        else -> null
                    }

                    if (label != null) {
                        val isNorth = deg == 0
                        val isCardinal = deg % 90 == 0
                        val tickTop = if (isCardinal) h - 6.5f else h - 5f
                        drawLine(
                            color = (if (isNorth) northCrimson else primaryText).copy(alpha = edgeFade),
                            start = Offset(x, tickTop),
                            end = Offset(x, h - 1.5f),
                            strokeWidth = if (isNorth) 2.5f else 1.8f,
                            cap = StrokeCap.Round,
                        )
                        val baseStyle = when {
                            isNorth -> northStyle
                            isCardinal -> cardinalStyle
                            else -> intercardinalStyle
                        }
                        val measured = textMeasurer.measure(
                            text = label,
                            style = baseStyle.copy(color = baseStyle.color.copy(alpha = edgeFade)),
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = x - measured.size.width / 2f,
                                y = (h - measured.size.height) / 2f + 1.5f,
                            ),
                        )
                    } else {
                        drawLine(
                            color = tickColor.copy(alpha = edgeFade * 0.85f),
                            start = Offset(x, h - 4.5f),
                            end = Offset(x, h - 1.5f),
                            strokeWidth = 1.25f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            // Live Cardinal + Degrees Pill (and North-reset cue if map is rotated)
            Surface(
                color = if (isMapRotated) northCrimson.copy(alpha = 0.16f) else ClipperCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%s %03d°", cardinal, headingInt),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                    ),
                    color = if (isMapRotated) northCrimson else if (isDarkHud) ClipperCyan else ClipperIndigo,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
internal fun TeslaTurnByTurnBanner(
    state: MapsUiState,
    onToggleVoice: () -> Unit,
    onStopNavigation: () -> Unit,
) {
    val step = state.activeRoute?.steps?.getOrNull(state.activeStepIndex)
    val nextStep = state.activeRoute?.steps?.getOrNull(state.activeStepIndex + 1)
    val mod = step?.modifier.orEmpty().lowercase(Locale.US)
    val turnIcon = when {
        mod.contains("left") -> Icons.Outlined.TurnLeft
        mod.contains("right") -> Icons.Outlined.TurnRight
        else -> Icons.Outlined.Navigation
    }

    Surface(
        color = Color(0xF00B1120),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.25.dp, ClipperCyan.copy(alpha = 0.65f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_turn_banner"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClipperCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(turnIcon, contentDescription = null, tint = ClipperCyan, modifier = Modifier.size(30.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step?.let { MapsGeoMath.formatDistance(it.distanceMeters, state.useImperialUnits) } ?: "Following route",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                        ),
                        color = ClipperCyan,
                    )
                    Text(
                        text = step?.instruction ?: "Proceed along highlighted corridor",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        color = StudioWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleVoice) {
                    Icon(
                        imageVector = if (state.voiceGuidanceEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                        contentDescription = "Toggle voice guidance",
                        tint = if (state.voiceGuidanceEnabled) ClipperEmerald else Color(0xFF94A3B8),
                    )
                }
                IconButton(onClick = onStopNavigation, modifier = Modifier.testTag("maps_stop_nav_button")) {
                    Icon(Icons.Outlined.Close, contentDescription = "Stop navigation", tint = ClipperRose)
                }
            }
            if (nextStep != null) {
                Text(
                    text = "Then: ${nextStep.instruction}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TeslaDriveCockpitHud(
    state: MapsUiState,
    onSwitchCamera: () -> Unit,
    onStopNav: () -> Unit,
    onExitTeslaMode: () -> Unit,
    onOpenTelemetryDetail: () -> Unit,
) {
    val (speedVal, speedUnit) = MapsGeoMath.formatSpeed(state.liveLocation?.speedMps ?: 0f, state.useImperialUnits)
    val heading = state.effectiveHeadingDeg
    val cardinal = MapsGeoMath.compassCardinal(heading)

    Surface(
        color = Color(0xEB0B1120),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.25.dp, ClipperCyan.copy(alpha = 0.5f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_tesla_cockpit_hud"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Speedometer Cluster (tappable for full telemetry details)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenTelemetryDetail)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = speedVal.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                    ),
                    color = StudioWhite,
                )
                Text(
                    text = speedUnit,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono, fontSize = 13.sp),
                    color = ClipperCyan,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }

            // Center Telemetry (tappable for detailed breakdown)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenTelemetryDetail)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (state.isNavigating) {
                    Text(
                        text = MapsGeoMath.formatDurationSeconds(state.remainingDurationSeconds),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = ClipperEmerald,
                    )
                    Text(
                        text = "${MapsGeoMath.formatDistance(state.remainingDistanceMeters, state.useImperialUnits)} left ▸",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono, fontSize = 13.sp),
                        color = Color(0xFFCBD5E1),
                    )
                } else {
                    Text(
                        text = "$cardinal ${heading.roundToInt()}°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = ClipperCyan,
                    )
                    val altText = state.liveLocation?.let { "Alt ${it.altitudeMeters.roundToInt()}m • ±${it.accuracyMeters.roundToInt()}m ▸" }
                        ?: "Tap for GPS details ▸"
                    Text(
                        text = altText,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                        color = Color(0xFFCBD5E1),
                    )
                }
            }

            // Action Button
            Button(
                onClick = if (state.isNavigating) onStopNav else onExitTeslaMode,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isNavigating) ClipperRose else ClipperIndigo,
                    contentColor = StudioWhite,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (state.isNavigating) "End" else "2D/3D")
            }
        }
    }
}

@Composable
internal fun CompactTelemetryStrip(
    state: MapsUiState,
    onOpenTeslaDrive: () -> Unit,
    onOpenTelemetryDetail: () -> Unit,
) {
    val (speedVal, speedUnit) = MapsGeoMath.formatSpeed(state.liveLocation?.speedMps ?: 0f, state.useImperialUnits)
    val cardinal = MapsGeoMath.compassCardinal(state.effectiveHeadingDeg)
    val coords = state.liveLocation?.point ?: state.mapCenter

    Surface(
        color = Color(0xF00F172A),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x5538BDF8)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_telemetry_strip"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenTelemetryDetail)
                    .padding(end = 8.dp)
                    .testTag("maps_telemetry_detail_trigger"),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$speedVal $speedUnit",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = ClipperCyan,
                    )
                    Text(
                        text = "$cardinal ${state.effectiveHeadingDeg.roundToInt()}°",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = StudioWhite,
                    )
                    if (state.isRecordingTrip) {
                        Text(
                            text = "● ${MapsGeoMath.formatDistance(state.tripDistanceMeters, state.useImperialUnits)}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = ClipperEmerald,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = String.format(Locale.US, "%.5f, %.5f • %s", coords.lat, coords.lng, state.stylePreset.label),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Open GPS telemetry details",
                        tint = ClipperCyan,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Button(
                onClick = onOpenTeslaDrive,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperCyan, contentColor = Color(0xFF0F172A)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.testTag("maps_tesla_drive_cta"),
            ) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("3D Drive", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
internal fun PlaceInspectorAndRouteCard(
    state: MapsUiState,
    place: PlaceResult,
    onSelectProfile: (TravelProfile) -> Unit,
    onComputeRoute: () -> Unit,
    onSelectAlternative: (Int) -> Unit,
    onStartNavigation: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTelemetryDetail: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isBookmarked = state.savedBookmarks.any { MapsGeoMath.haversineMeters(it.point, place.point) < 25.0 }
    val origin = state.liveLocation?.point ?: state.mapCenter
    val straightDist = MapsGeoMath.haversineMeters(origin, place.point)
    val bearingToPlace = MapsGeoMath.initialBearingDegrees(origin, place.point)
    val activeRoute = state.activeRoute

    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_place_inspector_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
                        color = InkObsidian,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = place.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSlate,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Save bookmark",
                            tint = if (isBookmarked) ClipperAmber else InkSlate,
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(String.format(Locale.US, "%.6f, %.6f", place.point.lat, place.point.lng)),
                            )
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy coordinates", tint = InkSlate, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close place details", tint = InkSlate)
                    }
                }
            }

            // Prominent Bigger, Bolder & Clickable Distance + Coordinates Detail Chip
            Surface(
                onClick = onOpenTelemetryDetail,
                color = Color(0xFFEEF2FF),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("maps_place_distance_detail_chip"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = MapsGeoMath.formatDistance(straightDist, state.useImperialUnits),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                ),
                                color = ClipperIndigo,
                            )
                            Text(
                                text = "• ${MapsGeoMath.compassCardinal(bearingToPlace)} ${bearingToPlace.roundToInt()}°",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                ),
                                color = InkObsidian,
                            )
                        }
                        Text(
                            text = MapsGeoMath.formatCoordinatesDms(place.point),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                            color = InkSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ClipperIndigo,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "View distance and travel details",
                            tint = ClipperIndigo,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Multi-Modal Travel Selector (Drive / Bus / Metro / 2-Wheeler / Cycle / Walk)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TravelProfile.entries.forEach { prof ->
                    val selected = state.travelProfile == prof
                    Surface(
                        onClick = { onSelectProfile(prof) },
                        color = if (selected) ClipperIndigo else StudioSnow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) ClipperIndigo else StudioBorder),
                        modifier = Modifier.testTag("maps_profile_${prof.name.lowercase(Locale.US)}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = travelProfileIcon(prof),
                                contentDescription = null,
                                tint = if (selected) StudioWhite else InkSlate,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = prof.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) StudioWhite else InkObsidian,
                            )
                        }
                    }
                }
            }

            // Route Alternatives Summary (Bigger legible ETA & Distance!)
            if (state.routes.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.routes.take(3).forEachIndexed { idx, route ->
                        val active = idx == state.activeRouteIndex
                        Surface(
                            onClick = { onSelectAlternative(idx) },
                            color = if (active) Color(0xFFEEF2FF) else StudioSnow,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.25.dp, if (active) ClipperIndigo else StudioBorder),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = MapsGeoMath.formatDurationSeconds(route.durationSeconds),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                    ),
                                    color = if (active) ClipperIndigo else InkObsidian,
                                )
                                Text(
                                    text = MapsGeoMath.formatDistance(route.distanceMeters, state.useImperialUnits),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMono,
                                        fontSize = 13.sp,
                                    ),
                                    color = InkSlate,
                                )
                            }
                        }
                    }
                }
            }

            // Real Bus / Metro Transit Legs Breakdown when a transit route is active
            if (activeRoute != null && activeRoute.transitLegs.isNotEmpty()) {
                Surface(
                    color = StudioSnow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maps_transit_legs_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        activeRoute.transitLegs.forEach { leg ->
                            val badgeColor = when (leg.modeLabel) {
                                "METRO" -> ClipperIndigo
                                "BUS" -> ClipperAmber
                                else -> ClipperEmerald
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    color = badgeColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = leg.modeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = leg.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = InkObsidian,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = leg.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = MapsGeoMath.formatDurationSeconds(leg.durationSeconds),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = InkObsidian,
                                    )
                                    Text(
                                        text = MapsGeoMath.formatDistance(leg.distanceMeters, state.useImperialUnits),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                        color = InkSlate,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clean Collapsible Turn-by-Turn Steps Preview for road/bike/walk routes
            if (activeRoute != null && activeRoute.steps.isNotEmpty() && activeRoute.transitLegs.isEmpty()) {
                var stepsExpanded by remember(activeRoute) { mutableStateOf(false) }
                Surface(
                    onClick = { stepsExpanded = !stepsExpanded },
                    color = StudioSnow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maps_route_steps_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Route steps (${activeRoute.steps.size}) • ${activeRoute.summary}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = InkObsidian,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (stepsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (stepsExpanded) "Collapse route steps" else "Expand route steps",
                                tint = ClipperIndigo,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        val previewSteps = if (stepsExpanded) activeRoute.steps.take(6) else activeRoute.steps.take(2)
                        previewSteps.forEach { step ->
                            val mod = step.modifier.lowercase(Locale.US)
                            val icon = when {
                                mod.contains("left") -> Icons.Outlined.TurnLeft
                                mod.contains("right") -> Icons.Outlined.TurnRight
                                else -> Icons.Outlined.Navigation
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(icon, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(15.dp))
                                Text(
                                    text = step.instruction,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkObsidian,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = MapsGeoMath.formatDistance(step.distanceMeters, state.useImperialUnits),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = InkSlate,
                                )
                            }
                        }
                    }
                }
            }

            // Route & Start 3D Navigation Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onComputeRoute,
                    enabled = !state.isRouting,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (state.isRouting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ClipperIndigo)
                    } else {
                        Icon(Icons.Outlined.Directions, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Directions", color = ClipperIndigo)
                    }
                }

                Button(
                    onClick = onStartNavigation,
                    enabled = !state.isRouting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald, contentColor = StudioWhite),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp)
                        .testTag("maps_start_nav_button"),
                ) {
                    Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.travelProfile.isTransit) "Guide Route" else "Start 3D Nav")
                }
            }
        }
    }
}

@Composable
internal fun TelemetryDetailSheetCard(
    state: MapsUiState,
    onToggleUnits: () -> Unit,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val loc = state.liveLocation
    val refPoint = loc?.point ?: state.mapCenter
    val (speedVal, speedUnit) = MapsGeoMath.formatSpeed(loc?.speedMps ?: 0f, state.useImperialUnits)
    val speedMps = loc?.speedMps ?: 0f
    val altMetric = loc?.altitudeMeters ?: 0.0
    val altDisplay = if (state.useImperialUnits) "${(altMetric * 3.28084).roundToInt()} ft" else "${altMetric.roundToInt()} m"
    val accDisplay = loc?.let { "±${MapsGeoMath.formatDistance(it.accuracyMeters.toDouble(), state.useImperialUnits)}" } ?: "No live GPS fix"
    val targetDistMeters = state.activeRoute?.distanceMeters
        ?: state.selectedPlace?.let { MapsGeoMath.haversineMeters(refPoint, it.point) }
        ?: if (state.isMeasuring && state.measuredLengthMeters > 0.0) state.measuredLengthMeters else null

    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.25.dp, ClipperIndigo.copy(alpha = 0.3f)),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .testTag("maps_telemetry_detail_sheet"),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = ClipperIndigo)
                    Column {
                        Text("GPS & Spatial Telemetry", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = InkObsidian)
                        Text(
                            text = if (state.isRegionRendering) "Streaming tiles for current region…" else "${state.stylePreset.label} • Zoom ${String.format(Locale.US, "%.1f", state.mapZoom)} • Tilt ${state.mapPitch.roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = InkSlate,
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("maps_telemetry_detail_close")) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close telemetry details", tint = InkSlate)
                }
            }

            // Key Live Metric Tiles (Large, Crisp Readouts)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TelemetryMetricBox(
                    label = "SPEED",
                    value = "$speedVal $speedUnit",
                    sub = String.format(Locale.US, "%.1f m/s", speedMps),
                    accent = ClipperCyan,
                    modifier = Modifier.weight(1f),
                )
                TelemetryMetricBox(
                    label = "HEADING",
                    value = "${MapsGeoMath.compassCardinal(state.effectiveHeadingDeg)} ${state.effectiveHeadingDeg.roundToInt()}°",
                    sub = "Compass bearing",
                    accent = ClipperIndigo,
                    modifier = Modifier.weight(1f),
                )
                TelemetryMetricBox(
                    label = "ALTITUDE",
                    value = altDisplay,
                    sub = accDisplay,
                    accent = ClipperEmerald,
                    modifier = Modifier.weight(1f),
                )
            }

            // Coordinates Box + One-Tap Copy
            Surface(
                color = StudioSnow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("COORDINATES (WGS84)", style = MaterialTheme.typography.labelSmall, color = InkSlate)
                        Text(
                            text = String.format(Locale.US, "%.6f, %.6f", refPoint.lat, refPoint.lng),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                            color = InkObsidian,
                        )
                        Text(
                            text = MapsGeoMath.formatCoordinatesDms(refPoint),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = ClipperIndigo,
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(String.format(Locale.US, "%.6f, %.6f", refPoint.lat, refPoint.lng)),
                            )
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy coordinates", tint = ClipperIndigo)
                    }
                }
            }

            // Multi-Modal Travel Comparison for Selected Destination / Measured Distance
            if (targetDistMeters != null && targetDistMeters > 5.0) {
                val headingToTarget = state.selectedPlace?.let { MapsGeoMath.initialBearingDegrees(refPoint, it.point) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.selectedPlace?.let { "To ${it.name}" } ?: "Measured Distance Breakdown",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkObsidian,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = MapsGeoMath.formatDistance(targetDistMeters, state.useImperialUnits) +
                                (headingToTarget?.let { " • ${MapsGeoMath.compassCardinal(it)} ${it.roundToInt()}°" } ?: ""),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                            color = ClipperIndigo,
                        )
                    }

                    val estimates = MapsGeoMath.estimateTravelComparison(targetDistMeters)
                    estimates.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { est ->
                                Surface(
                                    color = StudioSnow,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(travelProfileIcon(est.profile), contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
                                        Column {
                                            Text(
                                                text = "${est.profile.label} • ${est.durationText}",
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                                color = InkObsidian,
                                                maxLines = 1,
                                            )
                                            Text(
                                                text = est.note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = InkSlate,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Unit Switch CTA
            OutlinedButton(
                onClick = onToggleUnits,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Text(
                    text = if (state.useImperialUnits) "Units: Imperial (mi / ft / mph) — Tap for Metric" else "Units: Metric (km / m / km/h) — Tap for Imperial",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkObsidian,
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricBox(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = StudioSnow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = InkSlate)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                color = accent,
                maxLines = 1,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = InkSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MeasurementHudCard(
    state: MapsUiState,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onTogglePolygon: () -> Unit,
    onOpenTelemetryDetail: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.25.dp, ClipperAmber.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_measure_hud_card"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Straighten, contentDescription = null, tint = ClipperAmber)
                    Column {
                        Text("Geodesic Measure Tool", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkObsidian)
                        Text(
                            text = if (state.measurePoints.isEmpty()) "Tap map points to measure distance & area" else "${state.measurePoints.size} pins placed • tap metrics for details",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    }
                }
                IconButton(onClick = onDone) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close measure tool", tint = InkSlate)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSnow)
                    .clickable(onClick = onOpenTelemetryDetail)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = InkSlate)
                    Text(
                        text = MapsGeoMath.formatDistance(state.measuredLengthMeters, state.useImperialUnits),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                        ),
                        color = ClipperIndigo,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("POLYGON AREA", style = MaterialTheme.typography.labelSmall, color = InkSlate)
                    Text(
                        text = if (state.measurePoints.size >= 3) {
                            MapsGeoMath.formatArea(state.measuredAreaSqMeters, state.useImperialUnits)
                        } else {
                            "Add 3+ pts"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                        ),
                        color = ClipperAmber,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = state.measurePoints.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo")
                }
                OutlinedButton(
                    onClick = onTogglePolygon,
                    enabled = state.measurePoints.size >= 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.measureClosedPolygon) "Open Path" else "Close Area")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.measurePoints.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Clear", color = ClipperRose)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapLayersSheetCard(
    state: MapsUiState,
    onSelectStyle: (MapStylePreset) -> Unit,
    onToggleTerrain3d: () -> Unit,
    onToggleBuildings3d: () -> Unit,
    onToggleTransitOverlay: () -> Unit,
    onToggleUnits: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .testTag("maps_layers_sheet"),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Map Engine & 3D Layers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = InkObsidian)
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close layers", tint = InkSlate)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapStylePreset.entries.forEach { preset ->
                    val selected = state.stylePreset == preset
                    Surface(
                        onClick = { onSelectStyle(preset) },
                        color = if (selected) ClipperIndigo else StudioSnow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) ClipperCyan else StudioBorder),
                        modifier = Modifier.testTag("maps_style_${preset.styleId}"),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) StudioWhite else InkObsidian,
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color(0xFFE0E7FF) else InkSlate,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = StudioBorder)

            LayerToggleRow("3D Buildings Extrusion", "Render real OSM architectural heights", state.buildings3dEnabled, onToggleBuildings3d)
            LayerToggleRow("Transit & Rail Lines Overlay", "Highlight Metro, Rail & Tram corridors", state.transitOverlayEnabled, onToggleTransitOverlay)
            LayerToggleRow("3D Elevation Hillshade (DEM)", "AWS Terrarium terrain relief on Topo style", state.terrain3dEnabled, onToggleTerrain3d)
            LayerToggleRow("Imperial Units (mi / mph / °F)", "Switch between Metric & Imperial telemetry", state.useImperialUnits, onToggleUnits)
        }
    }
}

@Composable
private fun LayerToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = InkObsidian)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSlate)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = ClipperIndigo),
        )
    }
}

@Composable
internal fun SavedBookmarksSheetCard(
    bookmarks: List<SavedPlaceBookmark>,
    onSelectBookmark: (SavedPlaceBookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_bookmarks_sheet"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Saved Places (${bookmarks.size})", style = MaterialTheme.typography.titleMedium, color = InkObsidian)
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close bookmarks", tint = InkSlate)
                }
            }

            if (bookmarks.isEmpty()) {
                Text(
                    text = "Tap anywhere on the map or search a destination, then tap the bookmark icon to save favorite spots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBookmark(bm) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bm.name, style = MaterialTheme.typography.titleSmall, color = InkObsidian, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bm.subtitle, style = MaterialTheme.typography.bodySmall, color = InkSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove saved place", tint = ClipperRose, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
