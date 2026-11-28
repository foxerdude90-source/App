package com.example.ui.features.maps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TurnLeft
import androidx.compose.material.icons.outlined.TurnRight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

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
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClipperCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(turnIcon, contentDescription = null, tint = ClipperCyan, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step?.let { MapsGeoMath.formatDistance(it.distanceMeters, state.useImperialUnits) } ?: "Following route",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = ClipperCyan,
                    )
                    Text(
                        text = step?.instruction ?: "Proceed along highlighted corridor",
                        style = MaterialTheme.typography.titleSmall,
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
                    style = MaterialTheme.typography.labelSmall,
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
            // Speedometer Cluster
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = speedVal.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                    ),
                    color = StudioWhite,
                )
                Text(
                    text = speedUnit,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                    color = ClipperCyan,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // Center Telemetry (ETA if navigating, else Heading + Altitude + GPS accuracy)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isNavigating) {
                    Text(
                        text = MapsGeoMath.formatDurationSeconds(state.remainingDurationSeconds),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = ClipperEmerald,
                    )
                    Text(
                        text = "${MapsGeoMath.formatDistance(state.remainingDistanceMeters, state.useImperialUnits)} left",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = Color(0xFF94A3B8),
                    )
                } else {
                    Text(
                        text = "$cardinal ${heading.roundToInt()}°",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = ClipperCyan,
                    )
                    val altText = state.liveLocation?.let { "Alt ${it.altitudeMeters.roundToInt()}m • ±${it.accuracyMeters.roundToInt()}m" }
                        ?: "3D EV Drive Cockpit"
                    Text(
                        text = altText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = Color(0xFF94A3B8),
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
) {
    val (speedVal, speedUnit) = MapsGeoMath.formatSpeed(state.liveLocation?.speedMps ?: 0f, state.useImperialUnits)
    val cardinal = MapsGeoMath.compassCardinal(state.effectiveHeadingDeg)
    val coords = state.liveLocation?.point ?: state.mapCenter

    Surface(
        color = Color(0xEC0F172A),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x4438BDF8)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maps_telemetry_strip"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$speedVal $speedUnit",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = ClipperCyan,
                    )
                    Text(
                        text = "$cardinal ${state.effectiveHeadingDeg.roundToInt()}°",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = StudioWhite,
                    )
                    if (state.isRecordingTrip) {
                        Text(
                            text = "● ${MapsGeoMath.formatDistance(state.tripDistanceMeters, state.useImperialUnits)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = ClipperEmerald,
                        )
                    }
                }
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f • %s", coords.lat, coords.lng, state.stylePreset.label),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onOpenTeslaDrive,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperCyan, contentColor = Color(0xFF0F172A)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("maps_tesla_drive_cta"),
            ) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("3D Drive", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
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
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isBookmarked = state.savedBookmarks.any { MapsGeoMath.haversineMeters(it.point, place.point) < 25.0 }
    val origin = state.liveLocation?.point ?: state.mapCenter
    val straightDist = MapsGeoMath.haversineMeters(origin, place.point)

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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkObsidian,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = place.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${MapsGeoMath.formatCoordinatesDms(place.point)} • ${MapsGeoMath.formatDistance(straightDist, state.useImperialUnits)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = ClipperIndigo,
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

            // Travel Profile Selector (Drive / Cycle / Walk)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TravelProfile.entries.forEach { prof ->
                    val selected = state.travelProfile == prof
                    val icon = when (prof) {
                        TravelProfile.DRIVING -> Icons.Outlined.DirectionsCar
                        TravelProfile.CYCLING -> Icons.Outlined.DirectionsBike
                        TravelProfile.WALKING -> Icons.Outlined.DirectionsWalk
                    }
                    Surface(
                        onClick = { onSelectProfile(prof) },
                        color = if (selected) ClipperIndigo else StudioSnow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) ClipperIndigo else StudioBorder),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, tint = if (selected) StudioWhite else InkSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(prof.label, style = MaterialTheme.typography.labelMedium, color = if (selected) StudioWhite else InkObsidian)
                        }
                    }
                }
            }

            // Route Alternatives Summary (when computed via OSRM)
            if (state.routes.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.routes.take(3).forEachIndexed { idx, route ->
                        val active = idx == state.activeRouteIndex
                        Surface(
                            onClick = { onSelectAlternative(idx) },
                            color = if (active) Color(0xFFEEF2FF) else StudioSnow,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (active) ClipperIndigo else StudioBorder),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = MapsGeoMath.formatDurationSeconds(route.durationSeconds),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                    color = if (active) ClipperIndigo else InkObsidian,
                                )
                                Text(
                                    text = MapsGeoMath.formatDistance(route.distanceMeters, state.useImperialUnits),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
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
                    modifier = Modifier.weight(1f).height(46.dp),
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
                        .height(46.dp)
                        .testTag("maps_start_nav_button"),
                ) {
                    Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start 3D Nav")
                }
            }
        }
    }
}

@Composable
internal fun MeasurementHudCard(
    state: MapsUiState,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onTogglePolygon: () -> Unit,
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
                        Text("Geodesic Measure Tool", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                        Text(
                            text = if (state.measurePoints.isEmpty()) "Tap map points to measure distance & area" else "${state.measurePoints.size} pins placed",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    }
                }
                IconButton(onClick = onDone) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close measure tool", tint = InkSlate)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = InkSlate)
                    Text(
                        text = MapsGeoMath.formatDistance(state.measuredLengthMeters, state.useImperialUnits),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
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
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
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
    onToggleGlobe: () -> Unit,
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
            .testTag("maps_layers_sheet"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Map Engine & 3D Layers", style = MaterialTheme.typography.titleMedium, color = InkObsidian)
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
            LayerToggleRow("3D Elevation Terrain (DEM)", "AWS Terrarium hillshading & mountain relief", state.terrain3dEnabled, onToggleTerrain3d)
            LayerToggleRow("3D Globe View", "Curved planetary projection at space zooms", state.globeProjectionEnabled, onToggleGlobe)
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
