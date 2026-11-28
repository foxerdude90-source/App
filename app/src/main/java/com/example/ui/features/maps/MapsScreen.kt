package com.example.ui.features.maps

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.maps.GeoPoint
import com.example.core.maps.MapCameraMode
import com.example.core.maps.MapsGeoMath
import com.example.core.maps.PoiCategory
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioWhite
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-Screen Edge-to-Edge Clipper Maps 3D Workspace.
 * Combines MapLibre GL JS v5 + Three.js 3D EV Car Custom Layer + Free OSM/ESRI/OSRM/Overpass feeds
 * with native Android Fused GPS + Hardware Compass + Tesla Drive Cockpit HUD.
 */
@Composable
fun MapsScreen(
    viewModel: MapsViewModel,
    onExitFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        viewModel.onLocationPermissionResult(granted)
    }

    val requestLiveLocation: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    // Sync Map Style & 3D Layer switches into MapLibre GL JS
    LaunchedEffect(
        state.isMapReady,
        state.stylePreset,
        state.terrain3dEnabled,
        state.buildings3dEnabled,
        state.globeProjectionEnabled,
    ) {
        if (!state.isMapReady) return@LaunchedEffect
        val js = "window.ClipperMap && window.ClipperMap.setStyle(" +
            "${JSONObject.quote(state.stylePreset.styleId)}, ${state.terrain3dEnabled}, ${state.buildings3dEnabled}, ${state.globeProjectionEnabled});"
        webViewRef?.evaluateJavascript(js, null)
    }

    // Sync Live Vehicle GPS + Heading into Three.js 3D Car Layer
    LaunchedEffect(state.isMapReady, state.liveLocation, state.compassHeadingDeg, state.cameraMode) {
        val loc = state.liveLocation ?: return@LaunchedEffect
        if (!state.isMapReady) return@LaunchedEffect
        val heading = state.effectiveHeadingDeg
        val js = String.format(
            Locale.US,
            "window.ClipperMap && window.ClipperMap.updateVehicleLocation(%.7f, %.7f, %.2f, %.1f, %.2f, %s);",
            loc.point.lat,
            loc.point.lng,
            heading,
            loc.accuracyMeters,
            loc.speedMps,
            JSONObject.quote(state.cameraMode.name),
        )
        webViewRef?.evaluateJavascript(js, null)
    }

    // Sync Routes
    LaunchedEffect(state.isMapReady, state.routes, state.activeRouteIndex) {
        if (!state.isMapReady) return@LaunchedEffect
        val json = MapsGeoMath.routesToGeoJsonCoordsArray(state.routes)
        val js = "window.ClipperMap && window.ClipperMap.setRoutes(${JSONObject.quote(json)}, ${state.activeRouteIndex});"
        webViewRef?.evaluateJavascript(js, null)
    }

    // Sync Selected Pin
    LaunchedEffect(state.isMapReady, state.selectedPlace) {
        if (!state.isMapReady) return@LaunchedEffect
        val place = state.selectedPlace
        val js = if (place == null) {
            "window.ClipperMap && window.ClipperMap.setSearchPin(null, null, '');"
        } else {
            String.format(
                Locale.US,
                "window.ClipperMap && window.ClipperMap.setSearchPin(%.7f, %.7f, %s);",
                place.point.lat,
                place.point.lng,
                JSONObject.quote(place.name),
            )
        }
        webViewRef?.evaluateJavascript(js, null)
    }

    // Sync Nearby POIs
    LaunchedEffect(state.isMapReady, state.nearbyPois) {
        if (!state.isMapReady) return@LaunchedEffect
        val json = MapsGeoMath.poisToMarkerJson(state.nearbyPois)
        webViewRef?.evaluateJavascript("window.ClipperMap && window.ClipperMap.setPoiMarkers(${JSONObject.quote(json)});", null)
    }

    // Sync Live Breadcrumb Trail
    LaunchedEffect(state.isMapReady, state.tripBreadcrumb) {
        if (!state.isMapReady) return@LaunchedEffect
        val json = MapsGeoMath.pointsToLngLatArrayJson(state.tripBreadcrumb)
        webViewRef?.evaluateJavascript("window.ClipperMap && window.ClipperMap.setBreadcrumb(${JSONObject.quote(json)});", null)
    }

    // Sync Measurement Mode & Points
    LaunchedEffect(state.isMapReady, state.isMeasuring, state.measurePoints, state.measureClosedPolygon) {
        if (!state.isMapReady) return@LaunchedEffect
        val json = MapsGeoMath.pointsToLngLatArrayJson(state.measurePoints)
        webViewRef?.evaluateJavascript(
            "window.ClipperMap && window.ClipperMap.setMeasureActive(${state.isMeasuring});" +
                "window.ClipperMap && window.ClipperMap.setMeasureOverlay(${JSONObject.quote(json)}, ${state.measureClosedPolygon});",
            null,
        )
    }

    // Dispatch One-Shot Camera Commands
    LaunchedEffect(state.isMapReady, state.cameraCommandSeq) {
        val cmd = state.pendingCameraCommand ?: return@LaunchedEffect
        if (!state.isMapReady) return@LaunchedEffect
        val js = when (cmd) {
            is MapCameraCommand.FlyTo -> String.format(
                Locale.US,
                "window.ClipperMap && window.ClipperMap.flyTo(%.7f, %.7f, %.2f, %.1f, %.1f);",
                cmd.point.lat,
                cmd.point.lng,
                cmd.zoom,
                cmd.pitch,
                cmd.bearing,
            )
            is MapCameraCommand.FitRoutes -> "window.ClipperMap && window.ClipperMap.fitRouteBounds(${JSONObject.quote(cmd.routesJson)});"
            is MapCameraCommand.ZoomBy -> String.format(Locale.US, "window.ClipperMap && window.ClipperMap.zoomBy(%.2f);", cmd.delta)
            MapCameraCommand.ResetNorth -> "window.ClipperMap && window.ClipperMap.resetNorth();"
            MapCameraCommand.TogglePitch3d -> "window.ClipperMap && window.ClipperMap.togglePitch3d();"
        }
        webViewRef?.evaluateJavascript(js, null)
    }

    val isDarkHud = state.stylePreset.isDark || state.isNavigating || state.cameraMode == MapCameraMode.TESLA_3D
    val hudSurface = if (isDarkHud) Color(0xE60F172A) else Color(0xF5FFFFFF)
    val hudText = if (isDarkHud) StudioWhite else InkObsidian
    val hudSubText = if (isDarkHud) Color(0xFF94A3B8) else InkSlate
    val hudBorder = if (isDarkHud) Color(0x4438BDF8) else StudioBorder

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .testTag("maps_fullscreen_root"),
    ) {
        // 1. Full-Screen MapLibre GL JS + Three.js 3D Engine WebView
        MapLibreWebViewSurface(
            onWebViewCreated = { webViewRef = it },
            onMapReady = viewModel::onMapReady,
            onMapTap = viewModel::onMapTapOrLongPress,
            onCameraIdle = viewModel::onCameraIdle,
            onUserDetachedCamera = viewModel::onUserDetachedCamera,
            onPoiClick = viewModel::onPoiClicked,
            onMeasurePointAdded = viewModel::addMeasurePoint,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Top Floating HUD (Navigation Banner OR Search Bar + POI Chips + Weather/GPS)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isNavigating) {
                TeslaTurnByTurnBanner(
                    state = state,
                    onToggleVoice = viewModel::toggleVoiceGuidance,
                    onStopNavigation = viewModel::stopNavigation,
                )
            } else {
                // Floating Search Bar + Back Button
                Surface(
                    color = hudSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, hudBorder),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onExitFullScreen,
                            modifier = Modifier.testTag("maps_exit_fullscreen_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Exit full-screen maps",
                                tint = hudText,
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search places, addresses, or lat, lng…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = hudSubText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = hudText),
                                cursorBrush = SolidColor(ClipperCyan),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        viewModel.submitSearch()
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("maps_search_input"),
                            )
                        }

                        if (state.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                                color = ClipperCyan,
                            )
                        } else if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = hudSubText, modifier = Modifier.size(19.dp))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.submitSearch()
                                },
                                modifier = Modifier.testTag("maps_search_submit_button"),
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = ClipperCyan)
                            }
                        }
                    }
                }

                // Autocomplete Place Results Dropdown
                AnimatedVisibility(visible = state.searchResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        color = hudSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, hudBorder),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                    ) {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(state.searchResults, key = { it.id }) { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            keyboardController?.hide()
                                            viewModel.selectPlaceResult(place)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Outlined.Place, contentDescription = null, tint = ClipperCyan, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(place.name, style = MaterialTheme.typography.titleSmall, color = hudText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(place.subtitle, style = MaterialTheme.typography.bodySmall, color = hudSubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                HorizontalDivider(color = hudBorder.copy(alpha = 0.35f))
                            }
                        }
                    }
                }

                // Nearby OpenStreetMap POI Quick Chips + Live Weather Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.weather?.let { w ->
                        val tempStr = if (state.useImperialUnits) {
                            "${(w.temperatureC * 9.0 / 5.0 + 32.0).roundToInt()}°F"
                        } else {
                            "${w.temperatureC.roundToInt()}°C"
                        }
                        Surface(
                            color = hudSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.75.dp, hudBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = ClipperAmber, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "$tempStr • ${w.conditionLabel}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = hudText,
                                )
                            }
                        }
                    }

                    PoiCategory.entries.forEach { cat ->
                        val active = state.activePoiCategory == cat
                        Surface(
                            onClick = { viewModel.togglePoiCategory(cat) },
                            color = if (active) ClipperIndigo else hudSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.85.dp, if (active) ClipperCyan else hudBorder),
                            modifier = Modifier.testTag("maps_poi_${cat.name.lowercase(Locale.US)}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(cat.badge, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (active) StudioWhite else hudText,
                                )
                            }
                        }
                    }
                }

                // Live GPS Permission Callout (Non-Blocking)
                if (!state.hasLocationPermission) {
                    Surface(
                        onClick = requestLiveLocation,
                        color = Color(0xEB0F172A),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ClipperCyan.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("maps_allow_location_banner"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.GpsFixed, contentDescription = null, tint = ClipperCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Allow live GPS to unlock 3D vehicle tracking & turn-by-turn nav",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StudioWhite,
                                )
                            }
                            Text(
                                text = "ENABLE",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                color = ClipperCyan,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            // Ephemeral Status Pill (POI count / Bookmark saved / Rerouting)
            state.statusBanner?.let { msg ->
                Surface(
                    onClick = viewModel::clearStatusBanner,
                    color = Color(0xDC0F172A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.75.dp, ClipperCyan.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioWhite,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // 3. Right-Hand Precision Control Rail (Layers, 3D Pitch, Compass, Zoom +/-, Measure, Trail, Bookmarks, Live GPS / Tesla 3D)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MapRailIconButton(
                icon = Icons.Outlined.Layers,
                contentDescription = "Map styles and 3D layers",
                active = state.showLayersSheet,
                surfaceColor = hudSurface,
                contentColor = hudText,
                borderColor = hudBorder,
                tag = "maps_layers_button",
                onClick = { viewModel.setShowLayersSheet(!state.showLayersSheet) },
            )
            MapRailIconButton(
                icon = Icons.Outlined.ViewInAr,
                contentDescription = "Toggle 3D tilt angle",
                active = state.mapPitch > 25.0,
                surfaceColor = hudSurface,
                contentColor = hudText,
                borderColor = hudBorder,
                tag = "maps_3d_pitch_button",
                onClick = viewModel::togglePitch3d,
            )
            MapRailIconButton(
                icon = Icons.Outlined.Explore,
                contentDescription = "Reset compass North",
                active = false,
                surfaceColor = hudSurface,
                contentColor = if (state.mapBearing != 0.0) ClipperRose else hudText,
                borderColor = hudBorder,
                tag = "maps_compass_button",
                onClick = viewModel::resetNorth,
            )
            MapRailIconButton(
                icon = Icons.Outlined.Straighten,
                contentDescription = "Distance and area measurement tool",
                active = state.isMeasuring,
                surfaceColor = hudSurface,
                contentColor = hudText,
                borderColor = hudBorder,
                tag = "maps_measure_button",
                onClick = viewModel::toggleMeasureMode,
            )
            MapRailIconButton(
                icon = Icons.Outlined.FiberManualRecord,
                contentDescription = "Record live GPS trail",
                active = state.isRecordingTrip,
                activeTint = ClipperEmerald,
                surfaceColor = hudSurface,
                contentColor = hudText,
                borderColor = hudBorder,
                tag = "maps_trail_button",
                onClick = viewModel::toggleTripRecording,
            )
            MapRailIconButton(
                icon = Icons.Outlined.BookmarkBorder,
                contentDescription = "Saved places",
                active = state.showBookmarksSheet,
                surfaceColor = hudSurface,
                contentColor = hudText,
                borderColor = hudBorder,
                tag = "maps_bookmarks_button",
                onClick = { viewModel.setShowBookmarksSheet(!state.showBookmarksSheet) },
            )

            // Compact Zoom +/- Pair
            Surface(
                color = hudSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, hudBorder),
                shadowElevation = 4.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = viewModel::zoomIn, modifier = Modifier.size(44.dp).testTag("maps_zoom_in")) {
                        Icon(Icons.Outlined.Add, contentDescription = "Zoom in", tint = hudText, modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(modifier = Modifier.width(24.dp), color = hudBorder)
                    IconButton(onClick = viewModel::zoomOut, modifier = Modifier.size(44.dp).testTag("maps_zoom_out")) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Zoom out", tint = hudText, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Primary Live GPS / Tesla 3D Drive Mode Cycle FAB
            Surface(
                onClick = {
                    if (!state.hasLocationPermission) {
                        requestLiveLocation()
                    } else {
                        viewModel.cycleCameraMode()
                    }
                },
                color = when (state.cameraMode) {
                    MapCameraMode.TESLA_3D -> ClipperCyan
                    MapCameraMode.FOLLOW -> ClipperIndigo
                    MapCameraMode.FREE -> hudSurface
                },
                shape = CircleShape,
                border = BorderStroke(1.25.dp, if (state.cameraMode == MapCameraMode.FREE) ClipperCyan else StudioWhite),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("maps_locate_tesla_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.cameraMode == MapCameraMode.TESLA_3D) Icons.Outlined.DirectionsCar else Icons.Outlined.MyLocation,
                        contentDescription = "Locate me or switch to Tesla 3D Drive mode",
                        tint = if (state.cameraMode == MapCameraMode.FREE) ClipperIndigo else Color(0xFF0F172A),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // 4. Bottom Interactive Overlays (safe above system navigation bar!)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            when {
                state.showLayersSheet -> {
                    MapLayersSheetCard(
                        state = state,
                        onSelectStyle = viewModel::selectStylePreset,
                        onToggleTerrain3d = viewModel::toggleTerrain3d,
                        onToggleBuildings3d = viewModel::toggleBuildings3d,
                        onToggleGlobe = viewModel::toggleGlobeProjection,
                        onToggleUnits = viewModel::toggleUnits,
                        onClose = { viewModel.setShowLayersSheet(false) },
                    )
                }
                state.showBookmarksSheet -> {
                    SavedBookmarksSheetCard(
                        bookmarks = state.savedBookmarks,
                        onSelectBookmark = { bm ->
                            viewModel.selectPlaceResult(
                                com.example.core.maps.PlaceResult(
                                    id = bm.id,
                                    name = bm.name,
                                    subtitle = bm.subtitle,
                                    point = bm.point,
                                ),
                            )
                        },
                        onDeleteBookmark = viewModel::deleteBookmark,
                        onClose = { viewModel.setShowBookmarksSheet(false) },
                    )
                }
                state.isMeasuring -> {
                    MeasurementHudCard(
                        state = state,
                        onUndo = viewModel::undoMeasurePoint,
                        onClear = viewModel::clearMeasurePoints,
                        onTogglePolygon = viewModel::toggleMeasurePolygon,
                        onDone = viewModel::toggleMeasureMode,
                    )
                }
                state.isNavigating || state.cameraMode == MapCameraMode.TESLA_3D -> {
                    TeslaDriveCockpitHud(
                        state = state,
                        onSwitchCamera = viewModel::cycleCameraMode,
                        onStopNav = viewModel::stopNavigation,
                        onExitTeslaMode = { viewModel.setCameraMode(MapCameraMode.FOLLOW) },
                    )
                }
                state.selectedPlace != null -> {
                    PlaceInspectorAndRouteCard(
                        state = state,
                        place = state.selectedPlace!!,
                        onSelectProfile = viewModel::selectTravelProfile,
                        onComputeRoute = { viewModel.calculateRouteTo(state.selectedPlace!!, autoStartNav = false) },
                        onSelectAlternative = viewModel::selectRouteAlternative,
                        onStartNavigation = viewModel::startTeslaNavigation,
                        onToggleBookmark = { viewModel.toggleSaveBookmark(state.selectedPlace!!) },
                        onDismiss = viewModel::dismissSelectedPlace,
                    )
                }
                else -> {
                    CompactTelemetryStrip(
                        state = state,
                        onOpenTeslaDrive = {
                            if (!state.hasLocationPermission) {
                                requestLiveLocation()
                            } else {
                                viewModel.setCameraMode(MapCameraMode.TESLA_3D)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapRailIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    surfaceColor: Color,
    contentColor: Color,
    borderColor: Color,
    tag: String,
    activeTint: Color = ClipperCyan,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (active) Color(0xFF0F172A) else surfaceColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (active) activeTint else borderColor),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(48.dp)
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) activeTint else contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapLibreWebViewSurface(
    onWebViewCreated: (WebView) -> Unit,
    onMapReady: () -> Unit,
    onMapTap: (GeoPoint) -> Unit,
    onCameraIdle: (Double, Double, Double, Double, Double) -> Unit,
    onUserDetachedCamera: () -> Unit,
    onPoiClick: (String) -> Unit,
    onMeasurePointAdded: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestReady by rememberUpdatedState(onMapReady)
    val latestTap by rememberUpdatedState(onMapTap)
    val latestIdle by rememberUpdatedState(onCameraIdle)
    val latestDetach by rememberUpdatedState(onUserDetachedCamera)
    val latestPoi by rememberUpdatedState(onPoiClick)
    val latestMeasure by rememberUpdatedState(onMeasurePointAdded)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var createdWebView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            createdWebView?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.removeJavascriptInterface("ClipperMapBridge")
                    wv.destroy()
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(AndroidColor.parseColor("#0B1120"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    setSupportZoom(false)
                    builtInZoomControls = false
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMapReady() {
                            mainHandler.post { latestReady() }
                        }

                        @JavascriptInterface
                        fun onMapClick(lat: Double, lng: Double) {
                            mainHandler.post { latestTap(GeoPoint(lat, lng)) }
                        }

                        @JavascriptInterface
                        fun onMapLongClick(lat: Double, lng: Double) {
                            mainHandler.post { latestTap(GeoPoint(lat, lng)) }
                        }

                        @JavascriptInterface
                        fun onCameraIdle(lat: Double, lng: Double, zoom: Double, pitch: Double, bearing: Double) {
                            mainHandler.post { latestIdle(lat, lng, zoom, pitch, bearing) }
                        }

                        @JavascriptInterface
                        fun onUserDetachedCamera() {
                            mainHandler.post { latestDetach() }
                        }

                        @JavascriptInterface
                        fun onPoiClick(id: String) {
                            mainHandler.post { latestPoi(id) }
                        }

                        @JavascriptInterface
                        fun onMeasurePointAdded(lat: Double, lng: Double) {
                            mainHandler.post { latestMeasure(GeoPoint(lat, lng)) }
                        }
                    },
                    "ClipperMapBridge",
                )
                loadUrl("file:///android_asset/maps/clipper_map.html")
                createdWebView = this
                onWebViewCreated(this)
            }
        },
        modifier = modifier,
    )
}
