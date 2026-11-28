package com.example.ui.features.maps

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.maps.MapCameraMode
import com.example.core.maps.MapsGeoMath
import com.example.core.maps.PlaceResult
import com.example.core.maps.PoiCategory
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioWhite
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-Screen Edge-to-Edge Clipper Maps 3D Workspace powered by MapLibre GL Native (`MapViewWrapper`),
 * Accompanist Location Permissions, native Android Fused GPS + Hardware Compass, genuine region
 * rendering telemetry, dynamic Web Mercator scale bar, and multi-modal transit routing.
 */
@Composable
fun MapsScreen(
    viewModel: MapsViewModel,
    onExitFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    val locationPermissions = rememberMapsLocationPermissionController(
        onPermissionStateChanged = viewModel::onLocationPermissionResult,
    )

    val triggerLocationAccess: () -> Unit = {
        if (locationPermissions.isPermanentlyDenied) {
            locationPermissions.openAppSettings()
        } else {
            locationPermissions.requestLocationPermissions()
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                viewModel.onSearchQueryChanged(spoken)
                viewModel.submitSearch()
            }
        }
    }

    val launchVoiceSearch: () -> Unit = {
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search places, coffee, ATMs, or addresses")
            }
            voiceSearchLauncher.launch(intent)
        }
    }

    // Intercept system back button to dismiss overlays cleanly before leaving Maps
    val hasActiveOverlay = state.showLeftSidebar || state.showMoreCategories || state.showSearchOverlay ||
        state.showPoiResultsSheet || state.showLayersSheet || state.showBookmarksSheet || state.showTelemetryDetailSheet
    BackHandler(enabled = hasActiveOverlay) {
        when {
            state.showLeftSidebar -> viewModel.setShowLeftSidebar(false)
            state.showMoreCategories -> viewModel.setShowMoreCategories(false)
            state.showSearchOverlay -> viewModel.setShowSearchOverlay(false)
            state.showLayersSheet -> viewModel.setShowLayersSheet(false)
            state.showBookmarksSheet -> viewModel.setShowBookmarksSheet(false)
            state.showTelemetryDetailSheet -> viewModel.setShowTelemetryDetailSheet(false)
            state.showPoiResultsSheet -> viewModel.setShowPoiResultsSheet(false)
        }
    }

    // Auto-dismiss ephemeral status pill so it never permanently crowds the viewport
    LaunchedEffect(state.statusBanner) {
        if (state.statusBanner != null) {
            delay(3600L)
            viewModel.clearStatusBanner()
        }
    }

    val hasTallBottomSheet = state.showLayersSheet || state.showBookmarksSheet ||
        state.showTelemetryDetailSheet || state.selectedPlace != null || state.isMeasuring ||
        (state.showPoiResultsSheet && state.activePoiCategory != null)

    val isDarkHud = state.stylePreset.isDark || state.isNavigating || state.cameraMode == MapCameraMode.TESLA_3D
    val hudSurface = if (isDarkHud) Color(0xE80F172A) else Color(0xF8FFFFFF)
    val hudText = if (isDarkHud) StudioWhite else InkObsidian
    val hudSubText = if (isDarkHud) Color(0xFF94A3B8) else InkSlate
    val hudBorder = if (isDarkHud) Color(0x4438BDF8) else StudioBorder

    val scaleSpec = MapsGeoMath.computeScaleBarSpec(
        latitudeDeg = state.mapCenter.lat,
        zoom = state.mapZoom,
        useImperial = state.useImperialUnits,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .testTag("maps_fullscreen_root"),
    ) {
        // 1. Full-Screen Hardware-Accelerated MapLibre GL Native Engine
        MapViewWrapper(
            state = state,
            onMapReady = viewModel::onMapReady,
            onMapTap = viewModel::onMapTapOrLongPress,
            onMapDoubleTapSave = viewModel::onMapDoubleTapSave,
            onCameraIdle = viewModel::onCameraIdle,
            onUserDetachedCamera = viewModel::onUserDetachedCamera,
            onPoiClick = viewModel::onPoiClicked,
            onMeasurePointAdded = viewModel::addMeasurePoint,
            onRenderStateChanged = viewModel::onRenderStateChanged,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Genuine Region Rendering Micro-Indicator (appears ONLY while MapLibre reports frames/tiles still rendering)
        AnimatedVisibility(
            visible = state.isRegionRendering || state.renderErrorText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
        ) {
            Surface(
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.75.dp, ClipperCyan.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("maps_region_rendering_pill"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.isRegionRendering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = ClipperCyan,
                        )
                    }
                    Text(
                        text = state.renderErrorText ?: String.format(
                            Locale.US,
                            "Rendering region • %.4f, %.4f (z%.0f)",
                            state.mapCenter.lat,
                            state.mapCenter.lng,
                            state.mapZoom,
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontSize = 10.5.sp,
                        ),
                        color = StudioWhite,
                    )
                }
            }
        }

        // 3. Responsive Non-Overlapping Overlay Column (Top HUD -> Middle Rail + Scale Bar -> Bottom Sheet)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // --- TOP FLOATING HUD ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isNavigating) {
                    TeslaTurnByTurnBanner(
                        state = state,
                        onToggleVoice = viewModel::toggleVoiceGuidance,
                        onStopNavigation = viewModel::stopNavigation,
                    )
                    TopCompassTapeBar(
                        headingDeg = state.effectiveHeadingDeg,
                        mapBearingDeg = state.mapBearing,
                        isDarkHud = isDarkHud,
                        onResetNorth = viewModel::resetNorth,
                    )
                } else {
                    // Sleek Google-Maps-Style Floating Pill Search Bar + Left Sidebar Inbox Button + Voice Search
                    Surface(
                        color = hudSurface,
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, hudBorder),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp),
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

                            // Left Sidebar Saved Routes Inbox Trigger (with count badge)
                            Box(contentAlignment = Alignment.TopEnd) {
                                IconButton(
                                    onClick = { viewModel.setShowLeftSidebar(true) },
                                    modifier = Modifier.testTag("maps_sidebar_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Menu,
                                        contentDescription = "Open Saved & Auto Routes Inbox",
                                        tint = hudText,
                                    )
                                }
                                if (state.savedInboxCount > 0) {
                                    Surface(
                                        color = ClipperRose,
                                        shape = CircleShape,
                                        modifier = Modifier.padding(top = 6.dp, end = 6.dp),
                                    ) {
                                        Text(
                                            text = state.savedInboxCount.coerceAtMost(99).toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = StudioWhite,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (state.searchQuery.isEmpty()) viewModel.setShowSearchOverlay(true)
                                    },
                            ) {
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search here",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                        color = hudSubText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = state.searchQuery,
                                    onValueChange = { q ->
                                        viewModel.onSearchQueryChanged(q)
                                        if (q.isNotEmpty() && state.showSearchOverlay) {
                                            viewModel.setShowSearchOverlay(false)
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = hudText, fontSize = 16.sp),
                                    cursorBrush = SolidColor(ClipperCyan),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            keyboardController?.hide()
                                            viewModel.setShowSearchOverlay(false)
                                            viewModel.submitSearch()
                                        },
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focus ->
                                            if (focus.isFocused && state.searchQuery.isEmpty()) {
                                                viewModel.setShowSearchOverlay(true)
                                            }
                                        }
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
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = hudSubText, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        viewModel.submitSearch()
                                    },
                                    modifier = Modifier.testTag("maps_search_submit_button"),
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = SkyPillActiveBg)
                                }
                            } else {
                                IconButton(
                                    onClick = launchVoiceSearch,
                                    modifier = Modifier.testTag("maps_voice_search_button"),
                                ) {
                                    Icon(Icons.Outlined.Mic, contentDescription = "Voice search", tint = hudText)
                                }
                            }
                        }
                    }

                    // Search Focus Quick Sheet (Home ETA | Work | Transit | Recent & Frequent)
                    AnimatedVisibility(
                        visible = state.showSearchOverlay && state.searchResults.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        SearchFocusOverlay(
                            state = state,
                            onRouteToSmartPlace = viewModel::routeToSmartPlace,
                            onSetHomeCurrent = viewModel::setHomeFromCurrentLocation,
                            onSetWorkCurrent = viewModel::setWorkFromCurrentLocation,
                            onSelectTransitNearYou = { viewModel.togglePoiCategory(PoiCategory.BUS_STOP) },
                            onClose = { viewModel.setShowSearchOverlay(false) },
                        )
                    }

                    // Autocomplete Place Results Dropdown
                    AnimatedVisibility(visible = state.searchResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            color = hudSurface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, hudBorder),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
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
                                        Icon(Icons.Outlined.Place, contentDescription = null, tint = SkyPillActiveBg, modifier = Modifier.size(20.dp))
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

                    // Live Top Compass Strip (North / East / South / West sliding smoothly with phone orientation)
                    TopCompassTapeBar(
                        headingDeg = state.effectiveHeadingDeg,
                        mapBearingDeg = state.mapBearing,
                        isDarkHud = isDarkHud,
                        onResetNorth = viewModel::resetNorth,
                    )

                    // Pinned Weather + Home Quick-Route Chip + Material 3 Finder Chips + "More" Directory Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                onClick = { viewModel.setShowTelemetryDetailSheet(true) },
                                color = hudSurface,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(0.85.dp, hudBorder),
                                shadowElevation = 2.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = ClipperAmber, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = tempStr,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = hudText,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // One-Tap Home Route Pill with live ETA when Home is configured
                            state.homePlace?.let { home ->
                                val eta = estimateDriveMinutesText(state.liveLocation?.point ?: state.mapCenter, home.point)
                                Surface(
                                    onClick = { viewModel.routeToSmartPlace(home) },
                                    color = hudSurface,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(0.9.dp, hudBorder),
                                    shadowElevation = 2.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(Icons.Outlined.Home, contentDescription = null, tint = SkyPillActiveBg, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Home • $eta",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = hudText,
                                        )
                                    }
                                }
                            }

                            PoiCategory.entries.filter { it.isQuickStrip }.forEach { cat ->
                                val active = state.activePoiCategory == cat
                                Surface(
                                    onClick = { viewModel.togglePoiCategory(cat) },
                                    color = if (active) SkyPillActiveBg else hudSurface,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(0.9.dp, if (active) ClipperCyan else hudBorder),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.testTag("maps_poi_${cat.name.lowercase(Locale.US)}"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = poiCategoryMaterialIcon(cat),
                                            contentDescription = null,
                                            tint = if (active) StudioWhite else if (isDarkHud) ClipperCyan else InkObsidian,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = cat.label,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (active) StudioWhite else hudText,
                                        )
                                    }
                                }
                            }

                            // "More" Category Directory Pill (41 Real OSM Categories)
                            Surface(
                                onClick = { viewModel.setShowMoreCategories(true) },
                                color = if (isDarkHud) Color(0xFF1E293B) else SkyPillBg,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(0.9.dp, if (isDarkHud) ClipperCyan.copy(alpha = 0.45f) else SkyPillActiveBg.copy(alpha = 0.25f)),
                                shadowElevation = 2.dp,
                                modifier = Modifier.testTag("maps_more_categories_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = "More categories",
                                        tint = if (isDarkHud) ClipperCyan else SkyPillText,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Text(
                                        text = "More",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDarkHud) ClipperCyan else SkyPillText,
                                    )
                                }
                            }
                        }
                    }

                    // Floating "Search this area" Pill whenever user pans away during an active category search
                    AnimatedVisibility(visible = state.showSearchThisAreaPill && state.activePoiCategory != null, enter = fadeIn(), exit = fadeOut()) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                onClick = viewModel::searchActiveCategoryInCurrentArea,
                                color = StudioWhite,
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, SkyPillActiveBg.copy(alpha = 0.35f)),
                                shadowElevation = 6.dp,
                                modifier = Modifier.testTag("maps_search_this_area_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = SkyPillActiveBg, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Search this area",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = SkyPillActiveBg,
                                    )
                                }
                            }
                        }
                    }

                    // Live GPS Permission Callout (Accompanist Permissions with rationale + Settings fallback)
                    if (!locationPermissions.hasLocationPermission && !hasTallBottomSheet) {
                        val promptText = when {
                            locationPermissions.isPermanentlyDenied ->
                                "Location is disabled in Android Settings — open Settings to enable live GPS"
                            locationPermissions.shouldShowRationale ->
                                "Precise GPS powers live 3D vehicle tracking, speed telemetry & turn-by-turn routes"
                            else ->
                                "Allow live GPS to unlock 3D vehicle tracking & turn-by-turn nav"
                        }
                        val ctaLabel = if (locationPermissions.isPermanentlyDenied) "SETTINGS" else "ENABLE"

                        Surface(
                            onClick = triggerLocationAccess,
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
                                        text = promptText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioWhite,
                                    )
                                }
                                Text(
                                    text = ctaLabel,
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

            // --- MIDDLE FREE VIEWPORT AREA: Scale Bar (Bottom-Left) + Control Rail (Right) — ZERO Overlap with Bottom Sheet! ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                // Mathematical Scale Bar (Bottom-Left, tappable for Telemetry Details!)
                Surface(
                    onClick = { viewModel.setShowTelemetryDetailSheet(true) },
                    color = hudSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.85.dp, hudBorder),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .testTag("maps_scale_bar"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = scaleSpec.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                            ),
                            color = hudText,
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.width(scaleSpec.widthDp.dp),
                        ) {
                            Box(modifier = Modifier.width(2.dp).height(6.dp).background(ClipperCyan))
                            Box(modifier = Modifier.weight(1f).height(2.dp).background(ClipperCyan))
                            Box(modifier = Modifier.width(2.dp).height(6.dp).background(ClipperCyan))
                        }
                    }
                }

                // Right-Hand Precision Control Rail (Never clips or overlaps Bottom Sheets!)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    if (!hasTallBottomSheet) {
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
                    }
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
                    if (!hasTallBottomSheet) {
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
                    }

                    // Primary Live GPS / Tesla 3D Drive Mode Cycle FAB
                    Surface(
                        onClick = {
                            if (!locationPermissions.hasLocationPermission) {
                                triggerLocationAccess()
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
                            .size(50.dp)
                            .testTag("maps_locate_tesla_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.cameraMode == MapCameraMode.TESLA_3D) Icons.Outlined.DirectionsCar else Icons.Outlined.MyLocation,
                                contentDescription = "Locate me or switch to Tesla 3D Drive mode",
                                tint = if (state.cameraMode == MapCameraMode.FREE) ClipperIndigo else Color(0xFF0F172A),
                                modifier = Modifier.size(23.dp),
                            )
                        }
                    }
                }
            }

            // --- BOTTOM INTERACTIVE OVERLAY SHEET ---
            Box(modifier = Modifier.fillMaxWidth()) {
                when {
                    state.showTelemetryDetailSheet -> {
                        TelemetryDetailSheetCard(
                            state = state,
                            onToggleUnits = viewModel::toggleUnits,
                            onClose = { viewModel.setShowTelemetryDetailSheet(false) },
                        )
                    }
                    state.showLayersSheet -> {
                        MapLayersSheetCard(
                            state = state,
                            onSelectStyle = viewModel::selectStylePreset,
                            onToggleTerrain3d = viewModel::toggleTerrain3d,
                            onToggleBuildings3d = viewModel::toggleBuildings3d,
                            onToggleScene3d = viewModel::toggleScene3d,
                            onToggleTransitOverlay = viewModel::toggleTransitOverlay,
                            onToggleUnits = viewModel::toggleUnits,
                            onClose = { viewModel.setShowLayersSheet(false) },
                        )
                    }
                    state.showBookmarksSheet -> {
                        SavedBookmarksSheetCard(
                            bookmarks = state.savedBookmarks,
                            onSelectBookmark = { bm ->
                                viewModel.selectPlaceResult(
                                    PlaceResult(
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
                            onOpenTelemetryDetail = { viewModel.setShowTelemetryDetailSheet(true) },
                            onDone = viewModel::toggleMeasureMode,
                        )
                    }
                    state.isNavigating || state.cameraMode == MapCameraMode.TESLA_3D -> {
                        TeslaDriveCockpitHud(
                            state = state,
                            onSwitchCamera = viewModel::cycleCameraMode,
                            onStopNav = viewModel::stopNavigation,
                            onExitTeslaMode = { viewModel.setCameraMode(MapCameraMode.FOLLOW) },
                            onOpenTelemetryDetail = { viewModel.setShowTelemetryDetailSheet(true) },
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
                            onToggleWantToGo = { viewModel.toggleWantToGoPlace(state.selectedPlace!!) },
                            onSetHome = { viewModel.setHomePlace(state.selectedPlace!!) },
                            onSetWork = { viewModel.setWorkPlace(state.selectedPlace!!) },
                            onOpenTelemetryDetail = { viewModel.setShowTelemetryDetailSheet(true) },
                            onDismiss = viewModel::dismissSelectedPlace,
                        )
                    }
                    state.showPoiResultsSheet && state.activePoiCategory != null -> {
                        CategoryResultsPopupSheet(
                            state = state,
                            category = state.activePoiCategory!!,
                            onSelectPoi = viewModel::selectNearbyPoi,
                            onRouteToPoi = viewModel::routeToNearbyPoi,
                            onToggleFavoritePoi = { poi -> viewModel.toggleFavoritePlace(viewModel.poiToPlaceResult(poi)) },
                            onToggleSortByName = viewModel::togglePoiSortByName,
                            onToggleOpenNow = viewModel::togglePoiFilterOpenNow,
                            onToggleHasPhone = viewModel::togglePoiFilterHasPhone,
                            onToggleHasWebsite = viewModel::togglePoiFilterHasWebsite,
                            onToggleWheelchair = viewModel::togglePoiFilterWheelchair,
                            onClose = viewModel::clearActivePoiCategory,
                        )
                    }
                    else -> {
                        CompactTelemetryStrip(
                            state = state,
                            onOpenTeslaDrive = {
                                if (!locationPermissions.hasLocationPermission) {
                                    triggerLocationAccess()
                                } else {
                                    viewModel.setCameraMode(MapCameraMode.TESLA_3D)
                                }
                            },
                            onOpenTelemetryDetail = { viewModel.setShowTelemetryDetailSheet(true) },
                        )
                    }
                }
            }
        }

        // 4. Left Sidebar Slide-Out Inbox for Favorites, 1–4h Auto-Saved Stays, Frequent Routes, Home & Work
        MapsLeftSidebarDrawer(
            state = state,
            onRouteToPlace = viewModel::routeToSmartPlace,
            onDeleteSmartPlace = viewModel::deleteSmartPlace,
            onSetHomeCurrent = viewModel::setHomeFromCurrentLocation,
            onClearHome = viewModel::clearHomePlace,
            onSetWorkCurrent = viewModel::setWorkFromCurrentLocation,
            onClearWork = viewModel::clearWorkPlace,
            onToggleDoubleTapSave = viewModel::toggleDoubleTapToSave,
            onClose = { viewModel.setShowLeftSidebar(false) },
        )

        // 5. Full-Screen Material 3 "More Categories" Directory Overlay (41 Live OSM Categories)
        MoreCategoriesOverlay(
            visible = state.showMoreCategories,
            activeCategory = state.activePoiCategory,
            onSelectCategory = { cat -> viewModel.togglePoiCategory(cat) },
            onClose = { viewModel.setShowMoreCategories(false) },
        )
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
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
