package com.example.ui.features.maps

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.maps.GeoPoint
import com.example.core.maps.MapCameraMode
import com.example.core.maps.MapStylePreset
import com.example.core.maps.NavigationRoute
import com.example.core.maps.NearbyPoi
import com.example.core.maps.PlaceResult
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

private const val SRC_ROUTES = "clipper-routes-src"
private const val SRC_BREADCRUMB = "clipper-breadcrumb-src"
private const val SRC_MEASURE = "clipper-measure-src"
private const val SRC_POIS = "clipper-pois-src"
private const val SRC_PIN = "clipper-pin-src"
private const val SRC_VEHICLE = "clipper-vehicle-src"
private const val SRC_TRANSIT_OVERLAY = "clipper-transit-rail-src"
private const val SRC_OSM_MICRO_PATHS = "clipper-osm-micro-paths-src"
private const val SRC_OSM_3D_ROOFS = "clipper-osm-3d-roofs-src"
private const val SRC_OSM_3D_TREES = "clipper-osm-3d-trees-src"

private const val LAYER_VEC_SERVICE_CASING = "clipper-vec-colony-service-casing"
private const val LAYER_VEC_SERVICE_FILL = "clipper-vec-colony-service-fill"
private const val LAYER_VEC_FOOTPATH_CASING = "clipper-vec-colony-footpath-casing"
private const val LAYER_VEC_FOOTPATH_DASH = "clipper-vec-colony-footpath-dash"
private const val LAYER_VEC_WOOD_CANOPY_3D = "clipper-vec-wood-canopy-3d"
private const val LAYER_OSM_MICRO_SERVICE_CASING = "clipper-osm-micro-service-casing"
private const val LAYER_OSM_MICRO_SERVICE_FILL = "clipper-osm-micro-service-fill"
private const val LAYER_OSM_MICRO_FOOTPATH = "clipper-osm-micro-footpath"
private const val LAYER_OSM_MICRO_STEPS = "clipper-osm-micro-steps"
private const val LAYER_OSM_3D_ROOFS = "clipper-osm-3d-roofs"
private const val LAYER_OSM_3D_TREES = "clipper-osm-3d-trees"

private const val LAYER_TRANSIT_OVERLAY = "clipper-transit-rail-layer"
private const val LAYER_ROUTE_ALT = "clipper-route-alt"
private const val LAYER_ROUTE_GLOW = "clipper-route-glow"
private const val LAYER_ROUTE_CASING = "clipper-route-casing"
private const val LAYER_ROUTE_CORE = "clipper-route-core"
private const val LAYER_BREADCRUMB = "clipper-breadcrumb-line"
private const val LAYER_MEASURE_FILL = "clipper-measure-fill"
private const val LAYER_MEASURE_LINE = "clipper-measure-line"
private const val LAYER_MEASURE_PTS = "clipper-measure-pts"
private const val LAYER_POI_CIRCLES = "clipper-poi-circles"
private const val LAYER_POI_LABELS = "clipper-poi-labels"
private const val LAYER_SEARCH_PIN = "clipper-search-pin"
private const val LAYER_VEHICLE_HALO = "clipper-vehicle-halo"
private const val LAYER_VEHICLE_ICON = "clipper-vehicle-icon"

private const val IMG_USER_BLUE_ARROW = "clipper-user-blue-puck-arrow"
private const val IMG_VEHICLE_EV = "clipper-ev-vehicle-icon"
private const val IMG_SEARCH_PIN = "clipper-red-3d-pin-icon"

/**
 * Full-screen hardware-accelerated MapLibre GL Native Compose wrapper.
 * - Rock-solid multi-touch pinch-zoom & pan gesture handling (prevents Compose parent interception,
 *   disables quick-zoom & scale-velocity bounce, locks rotation while pinching, and never overrides
 *   user zoom gestures with compass/GPS ticks).
 * - High-contrast enhancement of OpenFreeMap minor streets, pathways, alleys & footways plus full
 *   OSM Standard Streets mode and toggleable OpenRailwayMap transit overlay.
 * - Genuine MapLibre tile & frame rendering callbacks so the HUD accurately indicates when a region
 *   is still streaming or rendering.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapViewWrapper(
    state: MapsUiState,
    onMapReady: () -> Unit,
    onMapTap: (GeoPoint) -> Unit,
    onMapDoubleTapSave: (GeoPoint) -> Unit,
    onCameraIdle: (Double, Double, Double, Double, Double) -> Unit,
    onUserDetachedCamera: () -> Unit,
    onPoiClick: (String) -> Unit,
    onMeasurePointAdded: (GeoPoint) -> Unit,
    onRenderStateChanged: (isRendering: Boolean, errorText: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestReady by rememberUpdatedState(onMapReady)
    val latestTap by rememberUpdatedState(onMapTap)
    val latestDoubleTapSave by rememberUpdatedState(onMapDoubleTapSave)
    val latestIdle by rememberUpdatedState(onCameraIdle)
    val latestDetach by rememberUpdatedState(onUserDetachedCamera)
    val latestPoi by rememberUpdatedState(onPoiClick)
    val latestMeasure by rememberUpdatedState(onMeasurePointAdded)
    val latestRenderState by rememberUpdatedState(onRenderStateChanged)
    val latestIsMeasuring by rememberUpdatedState(state.isMeasuring)
    val latestDoubleTapSaveEnabled by rememberUpdatedState(state.doubleTapToSaveEnabled)
    val latestCameraMode by rememberUpdatedState(state.cameraMode)

    val nativeInitialized = remember(context) {
        runCatching {
            MapLibre.getInstance(context.applicationContext)
            true
        }.getOrDefault(false)
    }

    if (!nativeInitialized) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0B1120))
                .testTag("map_view_wrapper"),
        )
        return
    }

    val mapView = remember(context) {
        MapView(context).apply {
            onCreate(null)
        }
    }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var appliedStylePreset by remember { mutableStateOf<MapStylePreset?>(null) }
    var appliedTerrain3d by remember { mutableStateOf<Boolean?>(null) }
    var isUserTouchingMap by remember { mutableStateOf(false) }
    var lastTouchEndMs by remember { mutableLongStateOf(0L) }
    var lastCameraEaseMs by remember { mutableLongStateOf(0L) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Bind MapView lifecycle strictly to the hosting Compose LifecycleOwner
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    // Load or switch free MapLibre style whenever stylePreset or terrain3d toggles
    LaunchedEffect(mapLibreMap, state.stylePreset, state.terrain3dEnabled) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (appliedStylePreset == state.stylePreset && appliedTerrain3d == state.terrain3dEnabled && loadedStyle != null) {
            return@LaunchedEffect
        }
        appliedStylePreset = state.stylePreset
        appliedTerrain3d = state.terrain3dEnabled
        latestRenderState(true, null)
        val builder = buildFreeStyleBuilder(state.stylePreset, state.terrain3dEnabled)
        map.setStyle(builder) { style ->
            if (state.stylePreset == MapStylePreset.LIBERTY || state.stylePreset == MapStylePreset.BRIGHT) {
                enhanceVectorRoadAndPathwayVisibility(style, isDarkStyle = state.stylePreset.isDark, scene3dEnabled = state.scene3dEnabled)
            }
            ensureOverlaySourcesAndLayers(
                style = style,
                isDarkStyle = state.stylePreset.isDark,
                buildings3dEnabled = state.buildings3dEnabled,
                scene3dEnabled = state.scene3dEnabled,
                transitOverlayEnabled = state.transitOverlayEnabled,
            )
            loadedStyle = style
            syncAllOverlaysToStyle(style, state)
        }
    }

    // Sync 3D Buildings extrusion layer visibility without reloading the style
    LaunchedEffect(loadedStyle, state.buildings3dEnabled) {
        val style = loadedStyle ?: return@LaunchedEffect
        apply3dBuildingsVisibility(style, state.buildings3dEnabled)
    }

    // Sync 3D Roofs, 3D Trees & Live Colony Micro-Pathways visibility without reloading the style
    LaunchedEffect(loadedStyle, state.scene3dEnabled) {
        val style = loadedStyle ?: return@LaunchedEffect
        applyScene3dVisibility(style, state.scene3dEnabled)
    }

    // Sync live Overpass OSM Colony Micro-Pathways, Pitched 3D Roofs & 3D Trees GeoJSON
    LaunchedEffect(loadedStyle, state.osmMicroPathwaysGeoJson, state.osm3dRoofsGeoJson, state.osm3dTreesGeoJson) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourceGeoJson(style, SRC_OSM_MICRO_PATHS, state.osmMicroPathwaysGeoJson)
        updateSourceGeoJson(style, SRC_OSM_3D_ROOFS, state.osm3dRoofsGeoJson)
        updateSourceGeoJson(style, SRC_OSM_3D_TREES, state.osm3dTreesGeoJson)
    }

    // Sync Double-Tap-to-Save mode cleanly with MapLibre double-tap zoom gesture
    LaunchedEffect(mapLibreMap, state.doubleTapToSaveEnabled) {
        mapLibreMap?.uiSettings?.isDoubleTapGesturesEnabled = !state.doubleTapToSaveEnabled
    }

    // Sync Transit & Rail overlay layer visibility without reloading the style
    LaunchedEffect(loadedStyle, state.transitOverlayEnabled) {
        val style = loadedStyle ?: return@LaunchedEffect
        applyTransitOverlayVisibility(style, state.transitOverlayEnabled)
    }

    // 1) Lightweight Blue Location Dot + Attached Live Arrow Update (NEVER fights camera gestures!)
    LaunchedEffect(loadedStyle, state.liveLocation, state.compassHeadingDeg, state.cameraMode) {
        val style = loadedStyle ?: return@LaunchedEffect
        val loc = state.liveLocation ?: return@LaunchedEffect
        val iconName = if (state.cameraMode == MapCameraMode.TESLA_3D) IMG_VEHICLE_EV else IMG_USER_BLUE_ARROW
        updateSourceGeoJson(
            style = style,
            sourceId = SRC_VEHICLE,
            geoJson = buildVehicleGeoJson(loc.point, state.effectiveHeadingDeg, loc.accuracyMeters, iconName),
        )
    }

    // 2) Camera Tracking (strictly skips while user is touching/pinching, preserves user zoom in FOLLOW!)
    LaunchedEffect(mapLibreMap, state.liveLocation, state.cameraMode) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val loc = state.liveLocation ?: return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        // Never fight active finger gestures or right after a pinch/pan gesture finishes
        if (isUserTouchingMap || (now - lastTouchEndMs) < 900L) return@LaunchedEffect
        if ((now - lastCameraEaseMs) < 550L) return@LaunchedEffect

        if (state.cameraMode == MapCameraMode.TESLA_3D) {
            val speedKmh = loc.speedMps * 3.6f
            val dynamicZoom = when {
                speedKmh > 80f -> 15.6
                speedKmh > 40f -> 16.5
                else -> 17.4
            }
            val cam = CameraPosition.Builder()
                .target(LatLng(loc.point.lat, loc.point.lng))
                .zoom(dynamicZoom)
                .tilt(60.0)
                .bearing(state.effectiveHeadingDeg.toDouble())
                .build()
            lastCameraEaseMs = now
            map.easeCamera(CameraUpdateFactory.newCameraPosition(cam), 500)
        } else if (state.cameraMode == MapCameraMode.FOLLOW) {
            // Preserve the user's exact zoom, tilt, and bearing — only smoothly recenter target!
            val currentPos = map.cameraPosition
            val cam = CameraPosition.Builder(currentPos)
                .target(LatLng(loc.point.lat, loc.point.lng))
                .build()
            lastCameraEaseMs = now
            map.easeCamera(CameraUpdateFactory.newCameraPosition(cam), 450)
        }
    }

    // Sync OSRM & Transit Routes
    LaunchedEffect(loadedStyle, state.routes, state.activeRouteIndex) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourceGeoJson(style, SRC_ROUTES, buildRoutesGeoJson(state.routes, state.activeRouteIndex))
    }

    // Sync Selected Pin
    LaunchedEffect(loadedStyle, state.selectedPlace) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourceGeoJson(style, SRC_PIN, buildSearchPinGeoJson(state.selectedPlace))
    }

    // Sync Nearby OSM POIs
    LaunchedEffect(loadedStyle, state.nearbyPois) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourceGeoJson(style, SRC_POIS, buildPoisGeoJson(state.nearbyPois))
    }

    // Sync Live GPS Breadcrumb Trail
    LaunchedEffect(loadedStyle, state.tripBreadcrumb) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourceGeoJson(style, SRC_BREADCRUMB, buildBreadcrumbGeoJson(state.tripBreadcrumb))
    }

    // Sync Geodesic Measure Overlay
    LaunchedEffect(loadedStyle, state.measurePoints, state.measureClosedPolygon) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourceGeoJson(style, SRC_MEASURE, buildMeasureGeoJson(state.measurePoints, state.measureClosedPolygon))
    }

    // Dispatch One-Shot Camera Commands (FlyTo, FitRoutes, ZoomBy, ResetNorth, TogglePitch3d)
    LaunchedEffect(mapLibreMap, state.cameraCommandSeq) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val cmd = state.pendingCameraCommand ?: return@LaunchedEffect
        when (cmd) {
            is MapCameraCommand.FlyTo -> {
                val cam = CameraPosition.Builder()
                    .target(LatLng(cmd.point.lat, cmd.point.lng))
                    .zoom(cmd.zoom.coerceIn(2.0, 20.5))
                    .tilt(cmd.pitch.coerceIn(0.0, 60.0))
                    .bearing(cmd.bearing)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 750)
            }
            is MapCameraCommand.FitRoutes -> {
                val allPoints = state.routes.flatMap { it.geometry }
                if (allPoints.size >= 2) {
                    val boundsBuilder = LatLngBounds.Builder()
                    allPoints.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
                    runCatching {
                        val bounds = boundsBuilder.build()
                        val fitCam = map.getCameraForLatLngBounds(bounds, intArrayOf(96, 220, 96, 390))
                        if (fitCam != null) {
                            // Always level North-Up (bearing=0, tilt=0) when previewing Directions!
                            val uprightCam = CameraPosition.Builder(fitCam)
                                .bearing(0.0)
                                .tilt(0.0)
                                .build()
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(uprightCam), 750)
                        } else {
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(bounds, 96, 220, 96, 390),
                                750,
                            )
                        }
                    }
                }
            }
            is MapCameraCommand.ZoomBy -> {
                val cur = map.cameraPosition
                val nextZoom = (cur.zoom + cmd.delta).coerceIn(2.0, 20.5)
                val cam = CameraPosition.Builder(cur).zoom(nextZoom).build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 240)
            }
            MapCameraCommand.ResetNorth -> {
                val cur = map.cameraPosition
                val cam = CameraPosition.Builder(cur)
                    .bearing(0.0)
                    .tilt(0.0)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 450)
            }
            MapCameraCommand.TogglePitch3d -> {
                val cur = map.cameraPosition
                val nextTilt = if (cur.tilt > 25.0) 0.0 else 58.0
                val cam = CameraPosition.Builder(cur)
                    .tilt(nextTilt)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 420)
            }
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                // Prevent Compose parent containers from stealing multi-touch pinch fingers
                // and immediately release camera lock when the user touches with 2+ fingers!
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            isUserTouchingMap = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            isUserTouchingMap = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            if (latestCameraMode != MapCameraMode.FREE) {
                                latestDetach()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isUserTouchingMap = false
                            lastTouchEndMs = SystemClock.uptimeMillis()
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

                // Genuine MapLibre tile & frame rendering listeners (debounced cleanly to avoid 60Hz flicker)
                var pendingShowRenderBadge: Runnable? = null
                fun scheduleRenderBadge(show: Boolean, err: String? = null) {
                    pendingShowRenderBadge?.let { mainHandler.removeCallbacks(it) }
                    if (!show) {
                        mainHandler.post { latestRenderState(false, err) }
                    } else {
                        val r = Runnable { latestRenderState(true, err) }
                        pendingShowRenderBadge = r
                        mainHandler.postDelayed(r, 180L)
                    }
                }

                addOnWillStartLoadingMapListener {
                    scheduleRenderBadge(true, null)
                }
                addOnDidFinishLoadingMapListener {
                    scheduleRenderBadge(false, null)
                }
                addOnDidFailLoadingMapListener { err ->
                    mainHandler.post {
                        latestRenderState(false, if (err.isNullOrBlank()) "Slow network — retrying tiles" else "Network slow — retrying map tiles")
                    }
                }
                addOnDidFinishRenderingFrameListener(
                    MapView.OnDidFinishRenderingFrameListener { fullyRendered, _, _ ->
                        if (fullyRendered) {
                            scheduleRenderBadge(false, null)
                        } else {
                            scheduleRenderBadge(true, null)
                        }
                    },
                )

                getMapAsync { map ->
                    map.setMinZoomPreference(2.0)
                    map.setMaxZoomPreference(20.5)
                    map.uiSettings.apply {
                        isCompassEnabled = false
                        isAttributionEnabled = false
                        isLogoEnabled = false
                        isScrollGesturesEnabled = true
                        isHorizontalScrollGesturesEnabled = true
                        isZoomGesturesEnabled = true
                        isDoubleTapGesturesEnabled = true
                        isTiltGesturesEnabled = true
                        isRotateGesturesEnabled = true
                        // Critical fixes for clean, predictable pinch-to-zoom without accidental zoom-in/bounce:
                        isQuickZoomGesturesEnabled = false
                        isDisableRotateWhenScaling = true
                        isIncreaseScaleThresholdWhenRotating = true
                        isScaleVelocityAnimationEnabled = false
                        isFlingVelocityAnimationEnabled = true
                    }
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(state.mapCenter.lat, state.mapCenter.lng))
                        .zoom(state.mapZoom)
                        .tilt(state.mapPitch.coerceIn(0.0, 60.0))
                        .bearing(state.mapBearing)
                        .build()

                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            latestDetach()
                        }
                    }

                    map.addOnCameraIdleListener {
                        val pos = map.cameraPosition
                        val target = pos.target ?: return@addOnCameraIdleListener
                        latestIdle(target.latitude, target.longitude, pos.zoom, pos.tilt, pos.bearing)
                    }

                    var lastTapTimeMs = 0L
                    var lastTapX = 0f
                    var lastTapY = 0f
                    map.addOnMapClickListener { latLng ->
                        if (latestIsMeasuring) {
                            latestMeasure(GeoPoint(latLng.latitude, latLng.longitude))
                            return@addOnMapClickListener true
                        }
                        val screenPt = map.projection.toScreenLocation(latLng)
                        val now = SystemClock.uptimeMillis()
                        val isDoubleTap = latestDoubleTapSaveEnabled &&
                            (now - lastTapTimeMs) <= 340L &&
                            kotlin.math.hypot((screenPt.x - lastTapX).toDouble(), (screenPt.y - lastTapY).toDouble()) < 84.0
                        lastTapTimeMs = now
                        lastTapX = screenPt.x
                        lastTapY = screenPt.y

                        if (isDoubleTap) {
                            lastTapTimeMs = 0L
                            latestDoubleTapSave(GeoPoint(latLng.latitude, latLng.longitude))
                            return@addOnMapClickListener true
                        }

                        val hitPois = map.queryRenderedFeatures(screenPt, LAYER_POI_CIRCLES, LAYER_POI_LABELS)
                        val tappedPoiId = hitPois.firstOrNull()?.getStringProperty("id")
                        if (!tappedPoiId.isNullOrBlank()) {
                            latestPoi(tappedPoiId)
                        } else {
                            latestTap(GeoPoint(latLng.latitude, latLng.longitude))
                        }
                        true
                    }

                    map.addOnMapLongClickListener { latLng ->
                        if (latestIsMeasuring) {
                            latestMeasure(GeoPoint(latLng.latitude, latLng.longitude))
                        } else {
                            latestTap(GeoPoint(latLng.latitude, latLng.longitude))
                        }
                        true
                    }

                    mapLibreMap = map
                    latestReady()
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("map_view_wrapper"),
    )
}

private fun buildFreeStyleBuilder(preset: MapStylePreset, terrain3d: Boolean): Style.Builder {
    return when (preset) {
        MapStylePreset.LIBERTY -> Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
        MapStylePreset.OSM_STREETS -> Style.Builder().fromJson(buildOsmStandardStreetsStyleJson())
        MapStylePreset.BRIGHT -> Style.Builder().fromUri("https://tiles.openfreemap.org/styles/bright")
        MapStylePreset.TESLA_DARK -> Style.Builder().fromJson(buildTeslaDarkStyleJson())
        MapStylePreset.SATELLITE -> Style.Builder().fromJson(buildEsriSatelliteHybridStyleJson())
        MapStylePreset.TOPO_3D -> Style.Builder().fromJson(buildTopoReliefStyleJson(terrain3d))
    }
}

private fun buildOsmStandardStreetsStyleJson(): String = """
{
  "version": 8,
  "name": "Clipper OSM Full Street Detail",
  "sources": {
    "osm-carto": {
      "type": "raster",
      "tiles": [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "maxzoom": 19
    }
  },
  "layers": [
    { "id": "bg", "type": "background", "paint": { "background-color": "#F1F5F9" } },
    { "id": "osm-carto-layer", "type": "raster", "source": "osm-carto" }
  ]
}
""".trimIndent()

private fun buildTeslaDarkStyleJson(): String = """
{
  "version": 8,
  "name": "Clipper Tesla Night",
  "sources": {
    "carto-dark": {
      "type": "raster",
      "tiles": [
        "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
        "https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
        "https://c.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"
      ],
      "tileSize": 256,
      "maxzoom": 19
    }
  },
  "layers": [
    { "id": "bg", "type": "background", "paint": { "background-color": "#090D18" } },
    { "id": "carto-dark-layer", "type": "raster", "source": "carto-dark", "paint": { "raster-contrast": 0.14, "raster-saturation": -0.05 } }
  ]
}
""".trimIndent()

private fun buildEsriSatelliteHybridStyleJson(): String = """
{
  "version": 8,
  "name": "Clipper ESRI Satellite Hybrid",
  "sources": {
    "esri-world-imagery": {
      "type": "raster",
      "tiles": [
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
      ],
      "tileSize": 256,
      "maxzoom": 19
    },
    "carto-labels": {
      "type": "raster",
      "tiles": [
        "https://a.basemaps.cartocdn.com/dark_only_labels/{z}/{x}/{y}@2x.png",
        "https://b.basemaps.cartocdn.com/dark_only_labels/{z}/{x}/{y}@2x.png"
      ],
      "tileSize": 256,
      "maxzoom": 19
    }
  },
  "layers": [
    { "id": "bg", "type": "background", "paint": { "background-color": "#060B14" } },
    { "id": "esri-sat", "type": "raster", "source": "esri-world-imagery" },
    { "id": "hybrid-labels", "type": "raster", "source": "carto-labels", "paint": { "raster-opacity": 0.92 } }
  ]
}
""".trimIndent()

private fun buildTopoReliefStyleJson(withHillshade: Boolean): String {
    val hillshadeSource = if (withHillshade) {
        """
        ,"aws-terrarium-dem": {
          "type": "raster-dem",
          "tiles": ["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],
          "encoding": "terrarium",
          "tileSize": 256,
          "maxzoom": 14
        }
        """.trimIndent()
    } else {
        ""
    }
    val hillshadeLayer = if (withHillshade) {
        """
        ,{
          "id": "clipper-hillshade",
          "type": "hillshade",
          "source": "aws-terrarium-dem",
          "paint": {
            "hillshade-exaggeration": 0.65,
            "hillshade-shadow-color": "#1E293B",
            "hillshade-highlight-color": "#F8FAFC"
          }
        }
        """.trimIndent()
    } else {
        ""
    }
    return """
    {
      "version": 8,
      "name": "Clipper Topo Relief",
      "sources": {
        "opentopo": {
          "type": "raster",
          "tiles": [
            "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
          ],
          "tileSize": 256,
          "maxzoom": 17
        }
        $hillshadeSource
      },
      "layers": [
        { "id": "bg", "type": "background", "paint": { "background-color": "#E2E8F0" } },
        { "id": "opentopo-layer", "type": "raster", "source": "opentopo" }
        $hillshadeLayer
      ]
    }
    """.trimIndent()
}

/**
 * Enhances OpenFreeMap / OpenMapTiles vector styles so narrow residential colony lanes, service roads
 * (including untagged highway=service without service=alley/driveway), living_streets, footways,
 * pedestrian passages, and tracks render clearly starting at medium zoom (z12+) instead of staying hidden.
 * Also adds a lightweight 3D wood/park canopy extrusion from OpenMapTiles landcover tiles.
 */
private fun enhanceVectorRoadAndPathwayVisibility(
    style: Style,
    isDarkStyle: Boolean,
    scene3dEnabled: Boolean,
) {
    // 1. Lower minZoom & boost contrast on any existing OpenFreeMap road/path layers
    style.layers.forEach { layer ->
        if (layer !is LineLayer) return@forEach
        val id = layer.id.lowercase()
        when {
            id.contains("path") || id.contains("foot") || id.contains("pedestrian") || id.contains("step") -> {
                layer.minZoom = 12.0f
                layer.setProperties(
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.lineColor("#475569"),
                    PropertyFactory.lineOpacity(0.95f),
                )
            }
            id.contains("service") || id.contains("track") || id.contains("alley") || id.contains("living") -> {
                layer.minZoom = 12.0f
                if (id.contains("casing")) {
                    layer.setProperties(
                        PropertyFactory.visibility(Property.VISIBLE),
                        PropertyFactory.lineColor("#94A3B8"),
                        PropertyFactory.lineOpacity(0.95f),
                    )
                } else {
                    layer.setProperties(
                        PropertyFactory.visibility(Property.VISIBLE),
                        PropertyFactory.lineColor(if (isDarkStyle) "#334155" else "#F8FAFC"),
                        PropertyFactory.lineOpacity(1.0f),
                    )
                }
            }
            (id.contains("minor") || id.contains("residential") || id.contains("street")) && id.contains("casing") -> {
                layer.minZoom = 11.5f
                layer.setProperties(
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.lineColor("#94A3B8"),
                    PropertyFactory.lineOpacity(0.95f),
                )
            }
        }
    }

    // 2. Inject dedicated unfiltered OpenMapTiles `transportation` layers for ALL service/minor/track/path
    // geometries so colony lanes without service=alley/driveway subtypes are NEVER omitted!
    val vecSourceId = style.sources.firstOrNull { it.id == "openmaptiles" || it is VectorSource }?.id ?: return
    val belowBuildingsAnchor = style.layers.firstOrNull {
        it is FillExtrusionLayer || it.id.contains("building", ignoreCase = true) || it is SymbolLayer
    }?.id

    val serviceMinorFilter = Expression.any(
        Expression.eq(Expression.get("class"), Expression.literal("service")),
        Expression.eq(Expression.get("class"), Expression.literal("minor")),
        Expression.eq(Expression.get("class"), Expression.literal("track")),
    )
    val pathFilter = Expression.eq(Expression.get("class"), Expression.literal("path"))

    if (style.getLayer(LAYER_VEC_SERVICE_CASING) == null) {
        val layer = LineLayer(LAYER_VEC_SERVICE_CASING, vecSourceId).apply {
            sourceLayer = "transportation"
            minZoom = 12.0f
            setFilter(serviceMinorFilter)
            setProperties(
                PropertyFactory.lineColor(if (isDarkStyle) "#475569" else "#94A3B8"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(12, 1.8f),
                        Expression.stop(14, 3.8f),
                        Expression.stop(16, 7.2f),
                        Expression.stop(18, 12.5f),
                    ),
                ),
                PropertyFactory.lineOpacity(0.92f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    if (style.getLayer(LAYER_VEC_SERVICE_FILL) == null) {
        val layer = LineLayer(LAYER_VEC_SERVICE_FILL, vecSourceId).apply {
            sourceLayer = "transportation"
            minZoom = 12.0f
            setFilter(serviceMinorFilter)
            setProperties(
                PropertyFactory.lineColor(if (isDarkStyle) "#1E293B" else "#FFFFFF"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(12, 1.0f),
                        Expression.stop(14, 2.4f),
                        Expression.stop(16, 5.2f),
                        Expression.stop(18, 9.8f),
                    ),
                ),
                PropertyFactory.lineOpacity(0.98f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    if (style.getLayer(LAYER_VEC_FOOTPATH_CASING) == null) {
        val layer = LineLayer(LAYER_VEC_FOOTPATH_CASING, vecSourceId).apply {
            sourceLayer = "transportation"
            minZoom = 12.5f
            setFilter(pathFilter)
            setProperties(
                PropertyFactory.lineColor(if (isDarkStyle) "#0F172A" else "#FFFFFF"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(13, 2.2f),
                        Expression.stop(16, 4.2f),
                        Expression.stop(18, 6.2f),
                    ),
                ),
                PropertyFactory.lineOpacity(0.88f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    if (style.getLayer(LAYER_VEC_FOOTPATH_DASH) == null) {
        val layer = LineLayer(LAYER_VEC_FOOTPATH_DASH, vecSourceId).apply {
            sourceLayer = "transportation"
            minZoom = 12.5f
            setFilter(pathFilter)
            setProperties(
                PropertyFactory.lineColor(if (isDarkStyle) "#94A3B8" else "#475569"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(13, 1.2f),
                        Expression.stop(16, 2.3f),
                        Expression.stop(18, 3.4f),
                    ),
                ),
                PropertyFactory.lineDasharray(arrayOf(2.0f, 1.3f)),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    // 3. Instant 3D Park/Wood Canopy Extrusion directly from OpenMapTiles landcover tiles
    if (style.getLayer(LAYER_VEC_WOOD_CANOPY_3D) == null) {
        val woodAnchor = style.layers.firstOrNull { it is SymbolLayer }?.id
        val woodLayer = FillExtrusionLayer(LAYER_VEC_WOOD_CANOPY_3D, vecSourceId).apply {
            sourceLayer = "landcover"
            minZoom = 13.5f
            setFilter(Expression.eq(Expression.get("class"), Expression.literal("wood")))
            setProperties(
                PropertyFactory.fillExtrusionColor("#15803D"),
                PropertyFactory.fillExtrusionHeight(5.2f),
                PropertyFactory.fillExtrusionBase(0.0f),
                PropertyFactory.fillExtrusionOpacity(0.42f),
                PropertyFactory.fillExtrusionVerticalGradient(true),
                PropertyFactory.visibility(if (scene3dEnabled) Property.VISIBLE else Property.NONE),
            )
        }
        addLayerSafelyBelow(style, woodLayer, woodAnchor)
    }
}

private fun addLayerSafelyBelow(style: Style, layer: Layer, belowId: String?) {
    if (belowId != null && style.getLayer(belowId) != null) {
        style.addLayerBelow(layer, belowId)
    } else {
        style.addLayer(layer)
    }
}

private fun ensureOverlaySourcesAndLayers(
    style: Style,
    isDarkStyle: Boolean,
    buildings3dEnabled: Boolean,
    scene3dEnabled: Boolean,
    transitOverlayEnabled: Boolean,
) {
    apply3dBuildingsVisibility(style, buildings3dEnabled)

    // Free OpenRailwayMap transparent rail & metro transit lines overlay
    if (style.getSource(SRC_TRANSIT_OVERLAY) == null) {
        val tileSet = TileSet(
            "2.2.0",
            "https://a.tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png",
            "https://b.tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png",
            "https://c.tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png",
        ).apply {
            minZoom = 2f
            maxZoom = 18f
        }
        style.addSource(RasterSource(SRC_TRANSIT_OVERLAY, tileSet, 256))
    }
    if (style.getLayer(LAYER_TRANSIT_OVERLAY) == null) {
        style.addLayer(
            RasterLayer(LAYER_TRANSIT_OVERLAY, SRC_TRANSIT_OVERLAY).withProperties(
                PropertyFactory.rasterOpacity(0.85f),
                PropertyFactory.visibility(if (transitOverlayEnabled) Property.VISIBLE else Property.NONE),
            ),
        )
    }

    if (style.getImage(IMG_USER_BLUE_ARROW) == null) {
        style.addImage(IMG_USER_BLUE_ARROW, createUserBluePuckArrowBitmap())
    }
    if (style.getImage(IMG_VEHICLE_EV) == null) {
        style.addImage(IMG_VEHICLE_EV, createTeslaEvMarkerBitmap())
    }
    if (style.getImage(IMG_SEARCH_PIN) == null) {
        style.addImage(IMG_SEARCH_PIN, createRed3dPinBitmap())
    }
    Poi3dPinFactory.registerAllCategoryPins(style)

    val emptyFc = """{"type":"FeatureCollection","features":[]}"""
    listOf(
        SRC_OSM_MICRO_PATHS,
        SRC_OSM_3D_ROOFS,
        SRC_OSM_3D_TREES,
        SRC_ROUTES,
        SRC_BREADCRUMB,
        SRC_MEASURE,
        SRC_POIS,
        SRC_PIN,
        SRC_VEHICLE,
    ).forEach { id ->
        if (style.getSource(id) == null) {
            style.addSource(GeoJsonSource(id, emptyFc))
        }
    }

    val sceneVis = if (scene3dEnabled) Property.VISIBLE else Property.NONE
    val belowBuildingsAnchor = style.layers.firstOrNull {
        it is FillExtrusionLayer || it.id.contains("building", ignoreCase = true) || it is SymbolLayer
    }?.id

    // Live Overpass Colony Service Roads & Living Streets (unfiltered OSM ground-truth!)
    if (style.getLayer(LAYER_OSM_MICRO_SERVICE_CASING) == null) {
        val layer = LineLayer(LAYER_OSM_MICRO_SERVICE_CASING, SRC_OSM_MICRO_PATHS).apply {
            minZoom = 14.2f
            setFilter(
                Expression.any(
                    Expression.eq(Expression.get("kind"), Expression.literal("service")),
                    Expression.eq(Expression.get("kind"), Expression.literal("living_street")),
                ),
            )
            setProperties(
                PropertyFactory.visibility(sceneVis),
                PropertyFactory.lineColor(if (isDarkStyle) "#64748B" else "#94A3B8"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(14, 3.6f),
                        Expression.stop(16, 6.8f),
                        Expression.stop(18, 11.5f),
                    ),
                ),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    if (style.getLayer(LAYER_OSM_MICRO_SERVICE_FILL) == null) {
        val layer = LineLayer(LAYER_OSM_MICRO_SERVICE_FILL, SRC_OSM_MICRO_PATHS).apply {
            minZoom = 14.2f
            setFilter(
                Expression.any(
                    Expression.eq(Expression.get("kind"), Expression.literal("service")),
                    Expression.eq(Expression.get("kind"), Expression.literal("living_street")),
                ),
            )
            setProperties(
                PropertyFactory.visibility(sceneVis),
                PropertyFactory.lineColor(if (isDarkStyle) "#283548" else "#FFFFFF"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(14, 2.2f),
                        Expression.stop(16, 4.8f),
                        Expression.stop(18, 8.8f),
                    ),
                ),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    if (style.getLayer(LAYER_OSM_MICRO_FOOTPATH) == null) {
        val layer = LineLayer(LAYER_OSM_MICRO_FOOTPATH, SRC_OSM_MICRO_PATHS).apply {
            minZoom = 14.4f
            setFilter(Expression.eq(Expression.get("kind"), Expression.literal("footpath")))
            setProperties(
                PropertyFactory.visibility(sceneVis),
                PropertyFactory.lineColor(if (isDarkStyle) "#CBD5E1" else "#475569"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(14, 1.5f),
                        Expression.stop(16, 2.6f),
                        Expression.stop(18, 3.8f),
                    ),
                ),
                PropertyFactory.lineDasharray(arrayOf(2.0f, 1.2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    if (style.getLayer(LAYER_OSM_MICRO_STEPS) == null) {
        val layer = LineLayer(LAYER_OSM_MICRO_STEPS, SRC_OSM_MICRO_PATHS).apply {
            minZoom = 14.5f
            setFilter(Expression.eq(Expression.get("kind"), Expression.literal("steps")))
            setProperties(
                PropertyFactory.visibility(sceneVis),
                PropertyFactory.lineColor("#D97706"),
                PropertyFactory.lineWidth(3.0f),
                PropertyFactory.lineDasharray(arrayOf(0.8f, 0.8f)),
            )
        }
        addLayerSafelyBelow(style, layer, belowBuildingsAnchor)
    }

    // Live OSM 3D Pitched / Gabled / Hipped / Pyramidal / Dome Roofs (`roof:shape`)
    if (style.getLayer(LAYER_OSM_3D_ROOFS) == null) {
        val roofLayer = FillExtrusionLayer(LAYER_OSM_3D_ROOFS, SRC_OSM_3D_ROOFS).apply {
            minZoom = 14.5f
            setProperties(
                PropertyFactory.visibility(sceneVis),
                PropertyFactory.fillExtrusionColor(Expression.get("color")),
                PropertyFactory.fillExtrusionBase(Expression.get("base")),
                PropertyFactory.fillExtrusionHeight(Expression.get("height")),
                PropertyFactory.fillExtrusionOpacity(0.95f),
                PropertyFactory.fillExtrusionVerticalGradient(true),
            )
        }
        style.addLayer(roofLayer)
    }

    // Live OSM 3D Trees (Bark Trunk + Multi-Tier Conifer / Broadleaf Canopy from `natural=tree`)
    if (style.getLayer(LAYER_OSM_3D_TREES) == null) {
        val treeLayer = FillExtrusionLayer(LAYER_OSM_3D_TREES, SRC_OSM_3D_TREES).apply {
            minZoom = 14.5f
            setProperties(
                PropertyFactory.visibility(sceneVis),
                PropertyFactory.fillExtrusionColor(Expression.get("color")),
                PropertyFactory.fillExtrusionBase(Expression.get("base")),
                PropertyFactory.fillExtrusionHeight(Expression.get("height")),
                PropertyFactory.fillExtrusionOpacity(0.92f),
                PropertyFactory.fillExtrusionVerticalGradient(true),
            )
        }
        style.addLayer(treeLayer)
    }

    if (style.getLayer(LAYER_ROUTE_ALT) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE_ALT, SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("active"), Expression.literal(false)))
                .withProperties(
                    PropertyFactory.lineColor("#94A3B8"),
                    PropertyFactory.lineWidth(5.5f),
                    PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
    }

    if (style.getLayer(LAYER_ROUTE_GLOW) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE_GLOW, SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("active"), Expression.literal(true)))
                .withProperties(
                    PropertyFactory.lineColor("#22D3EE"),
                    PropertyFactory.lineWidth(15f),
                    PropertyFactory.lineOpacity(0.28f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
    }

    if (style.getLayer(LAYER_ROUTE_CASING) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE_CASING, SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("active"), Expression.literal(true)))
                .withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(9.5f),
                    PropertyFactory.lineOpacity(0.95f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
    }

    if (style.getLayer(LAYER_ROUTE_CORE) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE_CORE, SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("active"), Expression.literal(true)))
                .withProperties(
                    PropertyFactory.lineColor("#2563EB"),
                    PropertyFactory.lineWidth(6.2f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
    }

    if (style.getLayer(LAYER_BREADCRUMB) == null) {
        style.addLayer(
            LineLayer(LAYER_BREADCRUMB, SRC_BREADCRUMB).withProperties(
                PropertyFactory.lineColor("#10B981"),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }

    if (style.getLayer(LAYER_MEASURE_FILL) == null) {
        style.addLayer(
            FillLayer(LAYER_MEASURE_FILL, SRC_MEASURE)
                .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Polygon")))
                .withProperties(
                    PropertyFactory.fillColor("#F59E0B"),
                    PropertyFactory.fillOpacity(0.22f),
                ),
        )
    }

    if (style.getLayer(LAYER_MEASURE_LINE) == null) {
        style.addLayer(
            LineLayer(LAYER_MEASURE_LINE, SRC_MEASURE)
                .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
                .withProperties(
                    PropertyFactory.lineColor("#F59E0B"),
                    PropertyFactory.lineWidth(3.5f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
                ),
        )
    }

    if (style.getLayer(LAYER_MEASURE_PTS) == null) {
        style.addLayer(
            CircleLayer(LAYER_MEASURE_PTS, SRC_MEASURE)
                .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Point")))
                .withProperties(
                    PropertyFactory.circleRadius(6.5f),
                    PropertyFactory.circleColor("#F59E0B"),
                    PropertyFactory.circleStrokeWidth(2.5f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                ),
        )
    }

    // Specific 3D-Sculpted Pointed Category Pins (Coffee cup, ATM, Hospital, Fuel, Metro, Shopping, etc.)
    if (style.getLayer(LAYER_POI_CIRCLES) == null) {
        style.addLayer(
            SymbolLayer(LAYER_POI_CIRCLES, SRC_POIS).withProperties(
                PropertyFactory.iconImage(Expression.get("pinIcon")),
                PropertyFactory.iconSize(0.86f),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    // Crisp Place Name Labels right alongside each 3D Pointed Category Marker
    if (style.getLayer(LAYER_POI_LABELS) == null) {
        style.addLayer(
            SymbolLayer(LAYER_POI_LABELS, SRC_POIS).withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(11.8f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 0.35f)),
                PropertyFactory.textMaxWidth(9.5f),
                PropertyFactory.textColor(if (isDarkStyle) "#F8FAFC" else "#0F172A"),
                PropertyFactory.textHaloColor(if (isDarkStyle) "#0F172A" else "#FFFFFF"),
                PropertyFactory.textHaloWidth(1.8f),
                PropertyFactory.textOptional(true),
            ),
        )
    }

    // Upright 3D Red Location Pin — viewport-aligned pitch & rotation so it NEVER tilts or skews sideways!
    if (style.getLayer(LAYER_SEARCH_PIN) == null) {
        style.addLayer(
            SymbolLayer(LAYER_SEARCH_PIN, SRC_PIN).withProperties(
                PropertyFactory.iconImage(IMG_SEARCH_PIN),
                PropertyFactory.iconSize(0.95f),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    if (style.getLayer(LAYER_VEHICLE_HALO) == null) {
        style.addLayer(
            CircleLayer(LAYER_VEHICLE_HALO, SRC_VEHICLE).withProperties(
                PropertyFactory.circleRadius(26f),
                PropertyFactory.circleColor("#3B82F6"),
                PropertyFactory.circleOpacity(0.18f),
                PropertyFactory.circleStrokeWidth(1.4f),
                PropertyFactory.circleStrokeColor("#60A5FA"),
            ),
        )
    }

    // Live User Blue Location Dot + Attached Forward Direction Arrow (rotates smoothly with phone compass!)
    if (style.getLayer(LAYER_VEHICLE_ICON) == null) {
        style.addLayer(
            SymbolLayer(LAYER_VEHICLE_ICON, SRC_VEHICLE).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(0.92f),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }
}

private fun apply3dBuildingsVisibility(style: Style, enabled: Boolean) {
    val vis = if (enabled) Property.VISIBLE else Property.NONE
    val nonBuildingExtrusions = setOf(LAYER_OSM_3D_ROOFS, LAYER_OSM_3D_TREES, LAYER_VEC_WOOD_CANOPY_3D)
    style.layers.forEach { layer ->
        if (layer.id in nonBuildingExtrusions) return@forEach
        if (layer is FillExtrusionLayer || layer.id.contains("building-3d", ignoreCase = true)) {
            layer.setProperties(PropertyFactory.visibility(vis))
        }
    }
}

private fun applyScene3dVisibility(style: Style, enabled: Boolean) {
    val vis = if (enabled) Property.VISIBLE else Property.NONE
    listOf(
        LAYER_VEC_WOOD_CANOPY_3D,
        LAYER_OSM_MICRO_SERVICE_CASING,
        LAYER_OSM_MICRO_SERVICE_FILL,
        LAYER_OSM_MICRO_FOOTPATH,
        LAYER_OSM_MICRO_STEPS,
        LAYER_OSM_3D_ROOFS,
        LAYER_OSM_3D_TREES,
    ).forEach { id ->
        style.getLayer(id)?.setProperties(PropertyFactory.visibility(vis))
    }
}

private fun applyTransitOverlayVisibility(style: Style, enabled: Boolean) {
    style.getLayer(LAYER_TRANSIT_OVERLAY)?.setProperties(
        PropertyFactory.visibility(if (enabled) Property.VISIBLE else Property.NONE),
    )
}

private fun syncAllOverlaysToStyle(style: Style, state: MapsUiState) {
    state.liveLocation?.let { loc ->
        val iconName = if (state.cameraMode == MapCameraMode.TESLA_3D) IMG_VEHICLE_EV else IMG_USER_BLUE_ARROW
        updateSourceGeoJson(style, SRC_VEHICLE, buildVehicleGeoJson(loc.point, state.effectiveHeadingDeg, loc.accuracyMeters, iconName))
    }
    updateSourceGeoJson(style, SRC_OSM_MICRO_PATHS, state.osmMicroPathwaysGeoJson)
    updateSourceGeoJson(style, SRC_OSM_3D_ROOFS, state.osm3dRoofsGeoJson)
    updateSourceGeoJson(style, SRC_OSM_3D_TREES, state.osm3dTreesGeoJson)
    updateSourceGeoJson(style, SRC_ROUTES, buildRoutesGeoJson(state.routes, state.activeRouteIndex))
    updateSourceGeoJson(style, SRC_PIN, buildSearchPinGeoJson(state.selectedPlace))
    updateSourceGeoJson(style, SRC_POIS, buildPoisGeoJson(state.nearbyPois))
    updateSourceGeoJson(style, SRC_BREADCRUMB, buildBreadcrumbGeoJson(state.tripBreadcrumb))
    updateSourceGeoJson(style, SRC_MEASURE, buildMeasureGeoJson(state.measurePoints, state.measureClosedPolygon))
}

private fun updateSourceGeoJson(style: Style, sourceId: String, geoJson: String) {
    val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
    source.setGeoJson(geoJson)
}

private fun buildVehicleGeoJson(
    point: GeoPoint,
    bearingDeg: Float,
    accuracyMeters: Float,
    iconName: String = IMG_USER_BLUE_ARROW,
): String {
    val props = JSONObject()
        .put("bearing", bearingDeg.toDouble())
        .put("accuracy", accuracyMeters.toDouble())
        .put("icon", iconName)
    val geom = JSONObject()
        .put("type", "Point")
        .put("coordinates", JSONArray().put(point.lng).put(point.lat))
    val feat = JSONObject()
        .put("type", "Feature")
        .put("properties", props)
        .put("geometry", geom)
    return JSONObject()
        .put("type", "FeatureCollection")
        .put("features", JSONArray().put(feat))
        .toString()
}

private fun buildSearchPinGeoJson(place: PlaceResult?): String {
    val features = JSONArray()
    if (place != null) {
        val geom = JSONObject()
            .put("type", "Point")
            .put("coordinates", JSONArray().put(place.point.lng).put(place.point.lat))
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", place.name))
                .put("geometry", geom),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun buildRoutesGeoJson(routes: List<NavigationRoute>, activeIndex: Int): String {
    val features = JSONArray()
    routes.forEachIndexed { idx, route ->
        if (route.geometry.size >= 2) {
            val coords = JSONArray()
            route.geometry.forEach { pt ->
                coords.put(JSONArray().put(pt.lng).put(pt.lat))
            }
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("properties", JSONObject().put("active", idx == activeIndex))
                    .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords)),
            )
        }
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun buildPoisGeoJson(pois: List<NearbyPoi>): String {
    val features = JSONArray()
    pois.forEach { poi ->
        val props = JSONObject()
            .put("id", poi.id)
            .put("name", poi.name)
            .put("badge", poi.category.badge)
            .put("pinIcon", poi.category.pinType.pinImageId)
        val geom = JSONObject()
            .put("type", "Point")
            .put("coordinates", JSONArray().put(poi.point.lng).put(poi.point.lat))
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", props)
                .put("geometry", geom),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun buildBreadcrumbGeoJson(points: List<GeoPoint>): String {
    val features = JSONArray()
    if (points.size >= 2) {
        val coords = JSONArray()
        points.forEach { pt -> coords.put(JSONArray().put(pt.lng).put(pt.lat)) }
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject())
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords)),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun buildMeasureGeoJson(points: List<GeoPoint>, closedPolygon: Boolean): String {
    val features = JSONArray()
    points.forEach { pt ->
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject())
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(pt.lng).put(pt.lat))),
        )
    }
    if (points.size >= 2) {
        val lineCoords = JSONArray()
        points.forEach { pt -> lineCoords.put(JSONArray().put(pt.lng).put(pt.lat)) }
        if (closedPolygon && points.size >= 3) {
            lineCoords.put(JSONArray().put(points.first().lng).put(points.first().lat))
        }
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject())
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", lineCoords)),
        )
    }
    if (closedPolygon && points.size >= 3) {
        val ring = JSONArray()
        points.forEach { pt -> ring.put(JSONArray().put(pt.lng).put(pt.lat)) }
        ring.put(JSONArray().put(points.first().lng).put(points.first().lat))
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject())
                .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring))),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun createTeslaEvMarkerBitmap(): Bitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        64f, 6f, 64f, 44f,
        AndroidColor.argb(120, 56, 189, 248),
        AndroidColor.argb(0, 56, 189, 248),
        Shader.TileMode.CLAMP,
    )
    val cone = Path().apply {
        moveTo(46f, 36f)
        lineTo(30f, 6f)
        lineTo(98f, 6f)
        lineTo(82f, 36f)
        close()
    }
    canvas.drawPath(cone, paint)
    paint.shader = null

    paint.color = AndroidColor.argb(110, 15, 23, 42)
    canvas.drawRoundRect(RectF(40f, 30f, 88f, 108f), 18f, 18f, paint)

    paint.shader = LinearGradient(
        42f, 26f, 86f, 104f,
        AndroidColor.parseColor("#FFFFFF"),
        AndroidColor.parseColor("#CBD5E1"),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRoundRect(RectF(42f, 26f, 86f, 104f), 16f, 16f, paint)
    paint.shader = null

    paint.color = AndroidColor.parseColor("#0F172A")
    canvas.drawRoundRect(RectF(47f, 44f, 81f, 88f), 10f, 10f, paint)

    paint.color = AndroidColor.parseColor("#38BDF8")
    canvas.drawRoundRect(RectF(47f, 27f, 81f, 32f), 3f, 3f, paint)

    paint.color = AndroidColor.parseColor("#F43F5E")
    canvas.drawRoundRect(RectF(47f, 99f, 81f, 103f), 2f, 2f, paint)

    return bmp
}

/**
 * Live User Blue Location Dot + Attached Forward Directional Arrow + Vision Cone.
 * Centered precisely at (size/2, size/2) for ICON_ANCHOR_CENTER, pointing straight UP (0° North)
 * so MapLibre `iconRotate(bearing)` smoothly pivots the attached arrow with the user's phone compass.
 */
private fun createUserBluePuckArrowBitmap(): Bitmap {
    val size = 160
    val cx = size / 2f
    val cy = size / 2f
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Soft directional vision cone fanning forward from the blue location puck
    paint.shader = RadialGradient(
        cx, cy - 8f, 68f,
        intArrayOf(
            AndroidColor.argb(145, 59, 130, 246),
            AndroidColor.argb(75, 56, 189, 248),
            AndroidColor.argb(0, 56, 189, 248),
        ),
        floatArrayOf(0f, 0.52f, 1f),
        Shader.TileMode.CLAMP,
    )
    val cone = Path().apply {
        moveTo(cx, cy)
        lineTo(cx - 42f, 14f)
        quadTo(cx, 2f, cx + 42f, 14f)
        close()
    }
    canvas.drawPath(cone, paint)
    paint.shader = null

    // 2. Attached Forward Direction Arrow right in front of the blue dot
    val arrowPath = Path().apply {
        moveTo(cx, 22f)          // Crisp arrow tip pointing forward
        lineTo(cx + 15f, 47f)    // Right wing
        lineTo(cx, 41.5f)        // Inner chevron notch
        lineTo(cx - 15f, 47f)    // Left wing
        close()
    }

    // Subtle elevation shadow beneath arrow
    canvas.save()
    canvas.translate(0f, 2f)
    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.argb(85, 15, 23, 42)
    canvas.drawPath(arrowPath, paint)
    canvas.restore()

    // Crisp white casing around arrow so it stays razor-sharp on dark, satellite, and street maps
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5.5f
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = AndroidColor.WHITE
    canvas.drawPath(arrowPath, paint)

    // Solid vibrant blue arrow fill attached right in front of the blue puck
    paint.style = Paint.Style.FILL
    paint.shader = LinearGradient(
        cx, 22f, cx, 47f,
        AndroidColor.parseColor("#38BDF8"),
        AndroidColor.parseColor("#1D4ED8"),
        Shader.TileMode.CLAMP,
    )
    canvas.drawPath(arrowPath, paint)
    paint.shader = null

    // 3. Soft ground shadow under the blue location dot
    paint.color = AndroidColor.argb(85, 15, 23, 42)
    canvas.drawCircle(cx, cy + 3f, 24f, paint)

    // 4. Crisp pure-white outer ring
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(cx, cy, 22.5f, paint)

    // 5. 3D spherical Cobalt Blue core
    paint.shader = RadialGradient(
        cx - 5f, cy - 6f, 22f,
        intArrayOf(
            AndroidColor.parseColor("#60A5FA"),
            AndroidColor.parseColor("#2563EB"),
            AndroidColor.parseColor("#1E40AF"),
        ),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(cx, cy, 17f, paint)
    paint.shader = null

    // 6. Subtle 3D specular highlight dot
    paint.color = AndroidColor.argb(125, 255, 255, 255)
    canvas.drawCircle(cx - 5.5f, cy - 6f, 4.2f, paint)

    return bmp
}

/**
 * Upright, 3D-Sculpted Crimson Red Location Pin with grounded elliptical shadow,
 * spherical specular gloss, and recessed inner precision ring.
 * Anchored at ICON_ANCHOR_BOTTOM with VIEWPORT alignment so it never tilts sideways.
 */
private fun createRed3dPinBitmap(): Bitmap {
    val w = 112
    val h = 140
    val cx = w / 2f
    val headCy = 44f
    val headR = 33f
    val tipY = 130f

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Grounded elliptical contact shadow right beneath the pin tip
    paint.shader = RadialGradient(
        cx, tipY + 1f, 24f,
        intArrayOf(
            AndroidColor.argb(125, 15, 23, 42),
            AndroidColor.argb(45, 15, 23, 42),
            AndroidColor.argb(0, 15, 23, 42),
        ),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawOval(RectF(cx - 23f, tipY - 5f, cx + 23f, tipY + 8f), paint)
    paint.shader = null

    // 2. Smooth vertical 3D teardrop pin contour (symmetrical, zero diagonal tilt)
    val pinPath = Path().apply {
        moveTo(cx, tipY)
        // Left tapering curve up into circular head
        cubicTo(
            cx - 6f, tipY - 26f,
            cx - headR, headCy + 26f,
            cx - headR, headCy,
        )
        // Top semicircular dome
        arcTo(
            RectF(cx - headR, headCy - headR, cx + headR, headCy + headR),
            180f,
            180f,
            false,
        )
        // Right tapering curve back down to pinpoint tip
        cubicTo(
            cx + headR, headCy + 26f,
            cx + 6f, tipY - 26f,
            cx, tipY,
        )
        close()
    }

    // Crisp outer white halo outline for strong legibility over all map styles
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = AndroidColor.WHITE
    canvas.drawPath(pinPath, paint)

    // Sculpted 3D Crimson Red radial gradient fill (lit top-left, deep ruby right & neck)
    paint.style = Paint.Style.FILL
    paint.shader = RadialGradient(
        cx - 12f, headCy - 14f, 68f,
        intArrayOf(
            AndroidColor.parseColor("#FF7A7A"),
            AndroidColor.parseColor("#EF4444"),
            AndroidColor.parseColor("#DC2626"),
            AndroidColor.parseColor("#991B1B"),
        ),
        floatArrayOf(0f, 0.28f, 0.66f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawPath(pinPath, paint)
    paint.shader = null

    // Fine dark-crimson contour rim for crisp 3D edge definition
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.8f
    paint.color = AndroidColor.parseColor("#7F1D1D")
    canvas.drawPath(pinPath, paint)

    // 3. Upper-left 3D specular gloss highlight on the dome
    paint.style = Paint.Style.FILL
    paint.shader = LinearGradient(
        cx - 22f, headCy - 26f, cx - 2f, headCy - 8f,
        AndroidColor.argb(140, 255, 255, 255),
        AndroidColor.argb(0, 255, 255, 255),
        Shader.TileMode.CLAMP,
    )
    canvas.drawOval(RectF(cx - 24f, headCy - 27f, cx + 2f, headCy - 10f), paint)
    paint.shader = null

    // 4. Recessed inner 3D bevel + crisp white ring + dark ruby precision pupil
    paint.color = AndroidColor.parseColor("#991B1B")
    canvas.drawCircle(cx, headCy + 1f, 15.5f, paint)

    paint.shader = LinearGradient(
        cx, headCy - 14f, cx, headCy + 14f,
        AndroidColor.parseColor("#FFFFFF"),
        AndroidColor.parseColor("#E2E8F0"),
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(cx, headCy, 13.5f, paint)
    paint.shader = null

    paint.color = AndroidColor.parseColor("#B91C1C")
    canvas.drawCircle(cx, headCy, 5.5f, paint)

    return bmp
}
