package com.example.ui.features.maps

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.maps.DwellSessionTracker
import com.example.core.maps.DeviceLocationSensorTracker
import com.example.core.maps.GeoPoint
import com.example.core.maps.LiveGpsTelemetry
import com.example.core.maps.LocalWeatherSnapshot
import com.example.core.maps.MapCameraMode
import com.example.core.maps.MapStylePreset
import com.example.core.maps.MapsApiClient
import com.example.core.maps.MapsGeoMath
import com.example.core.maps.NavigationRoute
import com.example.core.maps.NearbyPoi
import com.example.core.maps.Osm3dSceneEngine
import com.example.core.maps.PlaceResult
import com.example.core.maps.PoiCategory
import com.example.core.maps.SavedPlaceBookmark
import com.example.core.maps.SavedRouteCategory
import com.example.core.maps.SmartPlaceHistoryCodec
import com.example.core.maps.SmartSavedPlace
import com.example.core.maps.TravelProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

sealed class MapCameraCommand {
    data class FlyTo(val point: GeoPoint, val zoom: Double = 15.8, val pitch: Double = 50.0, val bearing: Double = 0.0) : MapCameraCommand()
    data class FitRoutes(val routesJson: String) : MapCameraCommand()
    data class ZoomBy(val delta: Double) : MapCameraCommand()
    data object ResetNorth : MapCameraCommand()
    data object TogglePitch3d : MapCameraCommand()
}

data class MapsUiState(
    val isMapReady: Boolean = false,
    // Location & Sensors
    val hasLocationPermission: Boolean = false,
    val locationPermissionDenied: Boolean = false,
    val liveLocation: LiveGpsTelemetry? = null,
    val compassHeadingDeg: Float = 0f,
    val mapCenter: GeoPoint = GeoPoint(37.7749, -122.4194),
    val mapZoom: Double = 14.2,
    val mapPitch: Double = 45.0,
    val mapBearing: Double = 0.0,
    // Camera & Visual Layers
    val stylePreset: MapStylePreset = MapStylePreset.LIBERTY,
    val cameraMode: MapCameraMode = MapCameraMode.FREE,
    val terrain3dEnabled: Boolean = false,
    val buildings3dEnabled: Boolean = true,
    val scene3dEnabled: Boolean = true,
    val globeProjectionEnabled: Boolean = false,
    val transitOverlayEnabled: Boolean = false,
    val useImperialUnits: Boolean = false,
    // Live OSM Micro-Pathways (colony service/footways) + 3D Pitched Roofs + 3D Trees
    val osmMicroPathwaysGeoJson: String = Osm3dSceneEngine.EMPTY_FEATURE_COLLECTION,
    val osm3dRoofsGeoJson: String = Osm3dSceneEngine.EMPTY_FEATURE_COLLECTION,
    val osm3dTreesGeoJson: String = Osm3dSceneEngine.EMPTY_FEATURE_COLLECTION,
    val osmScenePathCount: Int = 0,
    val osmSceneRoofCount: Int = 0,
    val osmSceneTreeCount: Int = 0,
    // Genuine MapLibre Region Rendering Telemetry
    val isRegionRendering: Boolean = false,
    val renderErrorText: String? = null,
    // Search & Place Inspector
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<PlaceResult> = emptyList(),
    val selectedPlace: PlaceResult? = null,
    val isReverseGeocoding: Boolean = false,
    // Routing & Turn-by-Turn Navigation
    val travelProfile: TravelProfile = TravelProfile.DRIVING,
    val isRouting: Boolean = false,
    val routes: List<NavigationRoute> = emptyList(),
    val activeRouteIndex: Int = 0,
    val isNavigating: Boolean = false,
    val voiceGuidanceEnabled: Boolean = true,
    val activeStepIndex: Int = 0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingDurationSeconds: Double = 0.0,
    // Nearby POIs
    val activePoiCategory: PoiCategory? = null,
    val isLoadingPois: Boolean = false,
    val nearbyPois: List<NearbyPoi> = emptyList(),
    // Measurement Tool
    val isMeasuring: Boolean = false,
    val measurePoints: List<GeoPoint> = emptyList(),
    val measureClosedPolygon: Boolean = false,
    // Trip Recorder & Telemetry
    val isRecordingTrip: Boolean = false,
    val tripBreadcrumb: List<GeoPoint> = emptyList(),
    val tripDistanceMeters: Double = 0.0,
    val tripMaxSpeedMps: Float = 0f,
    // Weather & Bookmarks & Smart Auto-Saved Inbox
    val weather: LocalWeatherSnapshot? = null,
    val savedBookmarks: List<SavedPlaceBookmark> = emptyList(),
    val smartPlaces: List<SmartSavedPlace> = emptyList(),
    val homePlace: SmartSavedPlace? = null,
    val workPlace: SmartSavedPlace? = null,
    val showLayersSheet: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showTelemetryDetailSheet: Boolean = false,
    val showLeftSidebar: Boolean = false,
    val showMoreCategories: Boolean = false,
    val showSearchOverlay: Boolean = false,
    val showPoiResultsSheet: Boolean = false,
    val poiFilterOpenNow: Boolean = false,
    val poiFilterHasPhone: Boolean = false,
    val poiFilterHasWebsite: Boolean = false,
    val poiFilterWheelchair: Boolean = false,
    val poiSortByName: Boolean = false,
    val showSearchThisAreaPill: Boolean = false,
    val doubleTapToSaveEnabled: Boolean = false,
    val statusBanner: String? = null,
    // One-shot camera command channel
    val cameraCommandSeq: Long = 0L,
    val pendingCameraCommand: MapCameraCommand? = null,
) {
    val activeRoute: NavigationRoute?
        get() = routes.getOrNull(activeRouteIndex)

    val savedInboxCount: Int
        get() = smartPlaces.count { it.listType != SavedRouteCategory.RECENT } +
            (if (homePlace != null) 1 else 0) +
            (if (workPlace != null) 1 else 0)

    val effectiveHeadingDeg: Float
        get() {
            val loc = liveLocation
            val gpsCourse = loc?.gpsBearingDegrees
            return if (loc != null && gpsCourse != null && loc.speedMps >= 2.5f) {
                gpsCourse
            } else {
                compassHeadingDeg
            }
        }

    val measuredLengthMeters: Double
        get() {
            val base = MapsGeoMath.polylineLengthMeters(measurePoints)
            return if (measureClosedPolygon && measurePoints.size >= 3) {
                base + MapsGeoMath.haversineMeters(measurePoints.last(), measurePoints.first())
            } else {
                base
            }
        }

    val measuredAreaSqMeters: Double
        get() = if (measurePoints.size >= 3) MapsGeoMath.sphericalPolygonAreaSqMeters(measurePoints) else 0.0
}

class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val api = MapsApiClient()
    private val prefs = application.getSharedPreferences("clipper_maps_prefs", Context.MODE_PRIVATE)
    private val dwellTracker = DwellSessionTracker()
    private val _uiState = MutableStateFlow(
        MapsUiState(
            hasLocationPermission = checkLocationPermission(application),
            savedBookmarks = loadSavedBookmarks(),
            smartPlaces = SmartPlaceHistoryCodec.loadSmartPlaces(prefs),
            homePlace = SmartPlaceHistoryCodec.loadHomePlace(prefs),
            workPlace = SmartPlaceHistoryCodec.loadWorkPlace(prefs),
            doubleTapToSaveEnabled = prefs.getBoolean("double_tap_save_enabled", false),
        ),
    )
    val uiState: StateFlow<MapsUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var compassJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var poiJob: Job? = null
    private var poiPhotoJob: Job? = null
    private var placePhotoJob: Job? = null
    private var weatherJob: Job? = null
    private var osmSceneJob: Job? = null
    private var lastWeatherPoint: GeoPoint? = null
    private var lastOsmScenePoint: GeoPoint? = null
    private var lastPoiSearchCenter: GeoPoint? = null
    private var lastRerouteMs: Long = 0L
    private var lastSpokenStepIndex: Int = -1
    private var snappedInitialFix: Boolean = false

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    init {
        initTextToSpeech()
        startCompassTracking()
        if (_uiState.value.hasLocationPermission) {
            startLocationTracking()
        }
    }

    private fun initTextToSpeech() {
        runCatching {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    ttsReady = true
                }
            }
        }
    }

    private fun checkLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun onLocationPermissionResult(granted: Boolean, userInitiated: Boolean = true) {
        _uiState.update {
            it.copy(
                hasLocationPermission = granted,
                locationPermissionDenied = if (userInitiated) !granted else it.locationPermissionDenied && !granted,
                statusBanner = when {
                    granted && userInitiated -> "Live GPS activated"
                    !granted && userInitiated -> "Location permission denied — you can still browse, search & plan routes"
                    else -> it.statusBanner
                },
            )
        }
        if (granted) {
            snappedInitialFix = false
            startLocationTracking()
        }
    }

    fun onMapReady() {
        _uiState.update { it.copy(isMapReady = true) }
        val target = _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
        fetchWeatherIfNeeded(target)
        fetchLocalOsm3dSceneIfNeeded(target, _uiState.value.mapZoom)
        _uiState.value.liveLocation?.let { loc ->
            dispatchCamera(MapCameraCommand.FlyTo(loc.point, zoom = 16.0, pitch = 52.0, bearing = _uiState.value.effectiveHeadingDeg.toDouble()))
        }
    }

    fun onCameraIdle(lat: Double, lng: Double, zoom: Double, pitch: Double, bearing: Double) {
        val center = GeoPoint(lat, lng)
        val pannedFromPoiSearch = lastPoiSearchCenter?.let { prev ->
            _uiState.value.activePoiCategory != null && MapsGeoMath.haversineMeters(prev, center) > 450.0
        } ?: false
        _uiState.update {
            it.copy(
                mapCenter = center,
                mapZoom = zoom,
                mapPitch = pitch,
                mapBearing = bearing,
                showSearchThisAreaPill = if (pannedFromPoiSearch) true else it.showSearchThisAreaPill,
            )
        }
        fetchWeatherIfNeeded(center)
        fetchLocalOsm3dSceneIfNeeded(center, zoom)
    }

    fun onUserDetachedCamera() {
        _uiState.update { it.copy(cameraMode = MapCameraMode.FREE) }
    }

    private fun startLocationTracking() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            DeviceLocationSensorTracker.observeLocationUpdates(getApplication()).collect { fix ->
                handleIncomingGpsFix(fix)
            }
        }
    }

    private fun startCompassTracking() {
        if (compassJob?.isActive == true) return
        compassJob = viewModelScope.launch {
            DeviceLocationSensorTracker.observeCompassHeading(getApplication()).collect { heading ->
                _uiState.update { it.copy(compassHeadingDeg = heading) }
            }
        }
    }

    private fun handleIncomingGpsFix(fix: LiveGpsTelemetry) {
        val prevState = _uiState.value
        val shouldSnap = !snappedInitialFix
        if (shouldSnap) snappedInitialFix = true

        // Update trip recorder breadcrumb if active
        var nextTrail = prevState.tripBreadcrumb
        var nextTripDist = prevState.tripDistanceMeters
        var nextMaxSpeed = prevState.tripMaxSpeedMps
        if (prevState.isRecordingTrip && fix.accuracyMeters <= 32f) {
            val lastPt = nextTrail.lastOrNull()
            if (lastPt == null) {
                nextTrail = listOf(fix.point)
            } else {
                val delta = MapsGeoMath.haversineMeters(lastPt, fix.point)
                val minStep = kotlin.math.max(6.0, fix.accuracyMeters * 0.45)
                if (delta >= minStep && delta < 180.0) {
                    nextTrail = (nextTrail + fix.point).takeLast(1200)
                    nextTripDist += delta
                }
            }
            if (fix.speedMps > nextMaxSpeed) nextMaxSpeed = fix.speedMps
        }

        // Update live turn-by-turn navigation progress & auto-reroute
        var nextStepIdx = prevState.activeStepIndex
        var nextRemDist = prevState.remainingDistanceMeters
        var nextRemDur = prevState.remainingDurationSeconds
        var triggerReroute = false

        val activeRoute = prevState.activeRoute
        if (prevState.isNavigating && activeRoute != null && activeRoute.geometry.size >= 2) {
            val match = MapsGeoMath.matchPointToRoute(fix.point, activeRoute.geometry)
            nextRemDist = match.remainingDistanceMeters
            val ratio = (nextRemDist / activeRoute.distanceMeters.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            nextRemDur = activeRoute.durationSeconds * ratio

            // Advance to closest upcoming maneuver step
            if (activeRoute.steps.isNotEmpty()) {
                val step = activeRoute.steps.getOrNull(nextStepIdx)
                if (step != null && nextStepIdx < activeRoute.steps.lastIndex) {
                    val distToStep = MapsGeoMath.haversineMeters(fix.point, step.location)
                    if (distToStep < 28.0) {
                        nextStepIdx++
                    }
                }
            }

            if (match.isOffRoute && System.currentTimeMillis() - lastRerouteMs > 12_000L) {
                lastRerouteMs = System.currentTimeMillis()
                triggerReroute = true
            }
        }

        _uiState.update {
            it.copy(
                liveLocation = fix,
                tripBreadcrumb = nextTrail,
                tripDistanceMeters = nextTripDist,
                tripMaxSpeedMps = nextMaxSpeed,
                activeStepIndex = nextStepIdx,
                remainingDistanceMeters = nextRemDist,
                remainingDurationSeconds = nextRemDur,
                cameraMode = if (shouldSnap && it.cameraMode == MapCameraMode.FREE) MapCameraMode.FOLLOW else it.cameraMode,
                statusBanner = if (shouldSnap) null else it.statusBanner,
            )
        }

        if (shouldSnap) {
            dispatchCamera(MapCameraCommand.FlyTo(fix.point, zoom = 16.2, pitch = 52.0, bearing = _uiState.value.effectiveHeadingDeg.toDouble()))
            fetchWeatherIfNeeded(fix.point)
        }

        if (prevState.isNavigating && nextStepIdx != lastSpokenStepIndex) {
            lastSpokenStepIndex = nextStepIdx
            activeRoute?.steps?.getOrNull(nextStepIdx)?.let { step ->
                speakGuidance(step.instruction)
            }
        }

        // Auto-save places where user stays 1–4 hours using real GPS dwell tracking
        val dwellTrigger = dwellTracker.onGpsSample(fix.point, fix.timestampMs)
        if (dwellTrigger != null) {
            viewModelScope.launch {
                val resolved = runCatching { api.reverseGeocode(dwellTrigger.point) }.getOrElse {
                    PlaceResult(
                        id = "dwell_${dwellTrigger.point.lat}_${dwellTrigger.point.lng}",
                        name = "Stayed Location",
                        subtitle = MapsGeoMath.formatCoordinatesDms(dwellTrigger.point),
                        point = dwellTrigger.point,
                    )
                }
                val photos = runCatching {
                    api.fetchRealPlacePhotos(resolved.name, resolved.point, resolved.category)
                }.getOrDefault(listOf(MapsGeoMath.osmTilePreviewUrl(resolved.point, 17)))
                val enriched = resolved.copy(photoUrls = photos)
                _uiState.update { cur ->
                    val updated = SmartPlaceHistoryCodec.upsertDwellAutoSavedPlace(
                        existing = cur.smartPlaces,
                        place = enriched,
                        dwellMinutes = dwellTrigger.dwellMinutes,
                        nowMs = System.currentTimeMillis(),
                    )
                    SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updated)
                    val badge = SmartPlaceHistoryCodec.formatDwellBadge(dwellTrigger.dwellMinutes)
                    cur.copy(
                        smartPlaces = updated,
                        statusBanner = if (!dwellTrigger.isDurationUpdate) {
                            "Auto-saved stay: ${enriched.name} ($badge)"
                        } else {
                            cur.statusBanner
                        },
                    )
                }
            }
        }

        if (triggerReroute) {
            prevState.selectedPlace?.let { dest ->
                speakGuidance("Recalculating route")
                calculateRouteTo(dest, autoStartNav = true)
            }
        }
    }

    // --- Camera & Layers ---

    fun cycleCameraMode() {
        val state = _uiState.value
        val next = when (state.cameraMode) {
            MapCameraMode.FREE -> MapCameraMode.FOLLOW
            MapCameraMode.FOLLOW -> MapCameraMode.TESLA_3D
            MapCameraMode.TESLA_3D -> MapCameraMode.FOLLOW
        }
        setCameraMode(next)
    }

    fun setCameraMode(mode: MapCameraMode) {
        val loc = _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
        val heading = _uiState.value.effectiveHeadingDeg.toDouble()
        _uiState.update {
            it.copy(
                cameraMode = mode,
                globeProjectionEnabled = if (mode == MapCameraMode.TESLA_3D) false else it.globeProjectionEnabled,
            )
        }
        when (mode) {
            MapCameraMode.TESLA_3D -> dispatchCamera(MapCameraCommand.FlyTo(loc, zoom = 17.4, pitch = 62.0, bearing = heading))
            MapCameraMode.FOLLOW -> dispatchCamera(MapCameraCommand.FlyTo(loc, zoom = 16.0, pitch = 42.0, bearing = 0.0))
            MapCameraMode.FREE -> Unit
        }
    }

    fun selectStylePreset(preset: MapStylePreset) {
        _uiState.update {
            it.copy(
                stylePreset = preset,
                terrain3dEnabled = if (preset == MapStylePreset.TOPO_3D) true else it.terrain3dEnabled,
            )
        }
    }

    fun toggleTerrain3d() = _uiState.update { it.copy(terrain3dEnabled = !it.terrain3dEnabled) }
    fun toggleBuildings3d() = _uiState.update { it.copy(buildings3dEnabled = !it.buildings3dEnabled) }
    fun toggleTransitOverlay() = _uiState.update { it.copy(transitOverlayEnabled = !it.transitOverlayEnabled) }
    fun toggleGlobeProjection() = _uiState.update {
        val next = !it.globeProjectionEnabled
        it.copy(
            globeProjectionEnabled = next,
            cameraMode = if (next) MapCameraMode.FREE else it.cameraMode,
        )
    }
    fun toggleScene3d() {
        val next = !_uiState.value.scene3dEnabled
        _uiState.update { it.copy(scene3dEnabled = next) }
        if (next) {
            lastOsmScenePoint = null
            fetchLocalOsm3dSceneIfNeeded(_uiState.value.mapCenter, _uiState.value.mapZoom)
        }
    }
    fun toggleUnits() = _uiState.update { it.copy(useImperialUnits = !it.useImperialUnits) }
    fun onRenderStateChanged(isRendering: Boolean, errorText: String? = null) {
        _uiState.update { cur ->
            if (cur.isRegionRendering == isRendering && cur.renderErrorText == errorText) cur
            else cur.copy(isRegionRendering = isRendering, renderErrorText = errorText)
        }
    }
    fun setShowLayersSheet(show: Boolean) = _uiState.update {
        it.copy(showLayersSheet = show, showBookmarksSheet = false, showTelemetryDetailSheet = false, showLeftSidebar = false)
    }
    fun setShowBookmarksSheet(show: Boolean) = _uiState.update {
        it.copy(showBookmarksSheet = show, showLayersSheet = false, showTelemetryDetailSheet = false, showLeftSidebar = false)
    }
    fun setShowTelemetryDetailSheet(show: Boolean) = _uiState.update {
        it.copy(showTelemetryDetailSheet = show, showLayersSheet = false, showBookmarksSheet = false, showLeftSidebar = false)
    }
    fun setShowLeftSidebar(show: Boolean) = _uiState.update {
        it.copy(
            showLeftSidebar = show,
            showMoreCategories = if (show) false else it.showMoreCategories,
            showSearchOverlay = if (show) false else it.showSearchOverlay,
            showLayersSheet = if (show) false else it.showLayersSheet,
            showBookmarksSheet = if (show) false else it.showBookmarksSheet,
        )
    }
    fun setShowMoreCategories(show: Boolean) = _uiState.update {
        it.copy(
            showMoreCategories = show,
            showLeftSidebar = if (show) false else it.showLeftSidebar,
            showSearchOverlay = if (show) false else it.showSearchOverlay,
        )
    }
    fun setShowSearchOverlay(show: Boolean) = _uiState.update {
        it.copy(
            showSearchOverlay = show,
            showLeftSidebar = if (show) false else it.showLeftSidebar,
            showMoreCategories = if (show) false else it.showMoreCategories,
        )
    }
    fun setShowPoiResultsSheet(show: Boolean) = _uiState.update {
        it.copy(showPoiResultsSheet = show)
    }
    fun toggleDoubleTapToSave() {
        _uiState.update { cur ->
            val next = !cur.doubleTapToSaveEnabled
            prefs.edit().putBoolean("double_tap_save_enabled", next).apply()
            cur.copy(
                doubleTapToSaveEnabled = next,
                statusBanner = if (next) "Double-tap anywhere on map to Favorite!" else "Map double-tap zoom restored",
            )
        }
    }
    fun togglePoiFilterOpenNow() = _uiState.update { it.copy(poiFilterOpenNow = !it.poiFilterOpenNow) }
    fun togglePoiFilterHasPhone() = _uiState.update { it.copy(poiFilterHasPhone = !it.poiFilterHasPhone) }
    fun togglePoiFilterHasWebsite() = _uiState.update { it.copy(poiFilterHasWebsite = !it.poiFilterHasWebsite) }
    fun togglePoiFilterWheelchair() = _uiState.update { it.copy(poiFilterWheelchair = !it.poiFilterWheelchair) }
    fun togglePoiSortByName() = _uiState.update { it.copy(poiSortByName = !it.poiSortByName) }
    fun zoomIn() = dispatchCamera(MapCameraCommand.ZoomBy(1.15))
    fun zoomOut() = dispatchCamera(MapCameraCommand.ZoomBy(-1.15))
    fun resetNorth() = dispatchCamera(MapCameraCommand.ResetNorth)
    fun togglePitch3d() = dispatchCamera(MapCameraCommand.TogglePitch3d)

    // --- Search & Pin Drop ---

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(380L)
            executePlaceSearch(query)
        }
    }

    fun submitSearch() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executePlaceSearch(q)
        }
    }

    private suspend fun executePlaceSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, statusBanner = null) }
        try {
            val bias = _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
            val results = api.searchPlaces(query, bias)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    statusBanner = if (results.isEmpty()) "No places found for \"$query\"" else null,
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            _uiState.update { it.copy(isSearching = false, statusBanner = "Search unavailable offline") }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun selectPlaceResult(place: PlaceResult) {
        val nowMs = System.currentTimeMillis()
        _uiState.update { cur ->
            val updatedHistory = SmartPlaceHistoryCodec.recordPlaceInteraction(cur.smartPlaces, place, nowMs)
            SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updatedHistory)
            cur.copy(
                selectedPlace = place,
                smartPlaces = updatedHistory,
                searchResults = emptyList(),
                searchQuery = place.name,
                routes = emptyList(),
                isNavigating = false,
                cameraMode = MapCameraMode.FREE,
                showBookmarksSheet = false,
                showLeftSidebar = false,
                showMoreCategories = false,
                showSearchOverlay = false,
                showPoiResultsSheet = false,
            )
        }
        dispatchCamera(MapCameraCommand.FlyTo(place.point, zoom = 16.2, pitch = 52.0, bearing = 0.0))
        enrichSelectedPlacePhotos(place)
    }

    private fun enrichSelectedPlacePhotos(place: PlaceResult) {
        if (place.photoUrls.size >= 2) return
        placePhotoJob?.cancel()
        placePhotoJob = viewModelScope.launch {
            val urls = runCatching {
                api.fetchRealPlacePhotos(
                    name = place.name,
                    point = place.point,
                    categoryHint = place.category,
                )
            }.getOrDefault(listOf(MapsGeoMath.osmTilePreviewUrl(place.point, 17)))
            if (urls.isEmpty()) return@launch
            _uiState.update { cur ->
                val active = cur.selectedPlace
                if (active != null && MapsGeoMath.haversineMeters(active.point, place.point) < 15.0) {
                    val primaryPhoto = urls.firstOrNull { !it.contains("tile.openstreetmap.org") } ?: urls.first()
                    val updatedHistory = cur.smartPlaces.map { saved ->
                        if (MapsGeoMath.haversineMeters(saved.point, place.point) < 60.0 && saved.photoUrl.contains("tile.openstreetmap.org")) {
                            saved.copy(photoUrl = primaryPhoto)
                        } else {
                            saved
                        }
                    }
                    SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updatedHistory)
                    cur.copy(
                        selectedPlace = active.copy(photoUrls = urls),
                        smartPlaces = updatedHistory,
                    )
                } else {
                    cur
                }
            }
        }
    }

    fun onMapTapOrLongPress(point: GeoPoint) {
        if (_uiState.value.isMeasuring) {
            addMeasurePoint(point)
            return
        }
        if (_uiState.value.isNavigating) return

        val immediatePin = PlaceResult(
            id = "${point.lat},${point.lng}",
            name = "Loading address…",
            subtitle = MapsGeoMath.formatCoordinatesDms(point),
            point = point,
            photoUrls = listOf(MapsGeoMath.osmTilePreviewUrl(point, 17)),
        )
        _uiState.update {
            it.copy(
                selectedPlace = immediatePin,
                isReverseGeocoding = true,
                searchResults = emptyList(),
                routes = emptyList(),
                showSearchOverlay = false,
                showPoiResultsSheet = false,
            )
        }
        viewModelScope.launch {
            try {
                val resolved = api.reverseGeocode(point)
                val photos = runCatching {
                    api.fetchRealPlacePhotos(resolved.name, resolved.point, resolved.category)
                }.getOrDefault(listOf(MapsGeoMath.osmTilePreviewUrl(point, 17)))
                val enriched = resolved.copy(photoUrls = photos)
                _uiState.update { cur ->
                    if (cur.selectedPlace?.point == point) {
                        val updatedHistory = SmartPlaceHistoryCodec.recordPlaceInteraction(cur.smartPlaces, enriched, System.currentTimeMillis())
                        SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updatedHistory)
                        cur.copy(selectedPlace = enriched, smartPlaces = updatedHistory, isReverseGeocoding = false)
                    } else {
                        cur.copy(isReverseGeocoding = false)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(isReverseGeocoding = false) }
            }
        }
    }

    /**
     * Double-tap directly on the map (when enabled) to immediately drop a 3D pin, reverse-geocode,
     * fetch real area photos, and save to Favorites in the Left Sidebar Route Inbox!
     */
    fun onMapDoubleTapSave(point: GeoPoint) {
        val immediatePin = PlaceResult(
            id = "fav_${point.lat}_${point.lng}",
            name = "Pinned Location",
            subtitle = MapsGeoMath.formatCoordinatesDms(point),
            point = point,
            photoUrls = listOf(MapsGeoMath.osmTilePreviewUrl(point, 17)),
        )
        _uiState.update { cur ->
            val (updatedSmart, _) = SmartPlaceHistoryCodec.togglePlaceInList(
                existing = cur.smartPlaces.filterNot { MapsGeoMath.haversineMeters(it.point, point) < 65.0 && it.listType == SavedRouteCategory.FAVORITE },
                place = immediatePin,
                targetList = SavedRouteCategory.FAVORITE,
                nowMs = System.currentTimeMillis(),
            )
            SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updatedSmart)
            cur.copy(
                selectedPlace = immediatePin,
                smartPlaces = updatedSmart,
                isReverseGeocoding = true,
                statusBanner = "★ Double-tap saved to Favorites!",
            )
        }
        viewModelScope.launch {
            val resolved = runCatching { api.reverseGeocode(point) }.getOrDefault(immediatePin)
            val photos = runCatching {
                api.fetchRealPlacePhotos(resolved.name, resolved.point, resolved.category)
            }.getOrDefault(listOf(MapsGeoMath.osmTilePreviewUrl(point, 17)))
            val enriched = resolved.copy(photoUrls = photos)
            _uiState.update { cur ->
                val updated = cur.smartPlaces.map { saved ->
                    if (MapsGeoMath.haversineMeters(saved.point, point) < 65.0) {
                        saved.copy(
                            name = enriched.name,
                            subtitle = enriched.subtitle,
                            listType = SavedRouteCategory.FAVORITE,
                            photoUrl = photos.firstOrNull { !it.contains("tile.openstreetmap.org") } ?: photos.first(),
                        )
                    } else {
                        saved
                    }
                }
                SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updated)
                cur.copy(
                    selectedPlace = if (cur.selectedPlace?.point == point) enriched else cur.selectedPlace,
                    smartPlaces = updated,
                    isReverseGeocoding = false,
                    statusBanner = "★ Saved \"${enriched.name}\" to Favorites!",
                )
            }
        }
    }

    fun dismissSelectedPlace() {
        routeJob?.cancel()
        _uiState.update {
            it.copy(
                selectedPlace = null,
                routes = emptyList(),
                activeRouteIndex = 0,
                isNavigating = false,
            )
        }
    }

    // --- Routing & Turn-by-Turn Navigation ---

    fun selectTravelProfile(profile: TravelProfile) {
        _uiState.update { it.copy(travelProfile = profile) }
        _uiState.value.selectedPlace?.let { dest ->
            if (_uiState.value.routes.isNotEmpty() || _uiState.value.isNavigating) {
                calculateRouteTo(dest, autoStartNav = _uiState.value.isNavigating)
            }
        }
    }

    fun selectRouteAlternative(index: Int) {
        val route = _uiState.value.routes.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                activeRouteIndex = index,
                remainingDistanceMeters = route.distanceMeters,
                remainingDurationSeconds = route.durationSeconds,
                activeStepIndex = 0,
            )
        }
    }

    fun calculateRouteTo(destination: PlaceResult, autoStartNav: Boolean = false) {
        val origin = _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
        if (MapsGeoMath.haversineMeters(origin, destination.point) < 20.0) {
            _uiState.update { it.copy(statusBanner = "You are already right at ${destination.name}") }
            return
        }
        val profile = _uiState.value.travelProfile
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _uiState.update { it.copy(isRouting = true, statusBanner = null) }
            try {
                val routes = api.fetchRoutes(origin, destination.point, profile)
                if (routes.isEmpty()) {
                    _uiState.update { it.copy(isRouting = false, statusBanner = "Could not compute route between these points.") }
                    return@launch
                }
                val primary = routes.first()
                _uiState.update {
                    it.copy(
                        isRouting = false,
                        routes = routes,
                        activeRouteIndex = 0,
                        remainingDistanceMeters = primary.distanceMeters,
                        remainingDurationSeconds = primary.durationSeconds,
                        activeStepIndex = 0,
                        isNavigating = autoStartNav,
                        cameraMode = if (autoStartNav) MapCameraMode.TESLA_3D else MapCameraMode.FREE,
                    )
                }
                if (autoStartNav) {
                    lastSpokenStepIndex = 0
                    primary.steps.firstOrNull()?.let { speakGuidance(it.instruction) }
                    dispatchCamera(
                        MapCameraCommand.FlyTo(
                            origin,
                            zoom = 17.5,
                            pitch = 62.0,
                            bearing = _uiState.value.effectiveHeadingDeg.toDouble(),
                        ),
                    )
                } else {
                    dispatchCamera(MapCameraCommand.FitRoutes(MapsGeoMath.routesToGeoJsonCoordsArray(routes)))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(isRouting = false, statusBanner = "Routing engine unreachable right now.") }
            }
        }
    }

    fun startTeslaNavigation() {
        val state = _uiState.value
        val route = state.activeRoute
        if (route == null) {
            state.selectedPlace?.let { calculateRouteTo(it, autoStartNav = true) }
            return
        }
        lastSpokenStepIndex = 0
        val startPt = state.liveLocation?.point ?: route.geometry.firstOrNull() ?: state.mapCenter
        _uiState.update {
            it.copy(
                isNavigating = true,
                cameraMode = MapCameraMode.TESLA_3D,
                globeProjectionEnabled = false,
                showLayersSheet = false,
                showBookmarksSheet = false,
                isMeasuring = false,
                activeStepIndex = 0,
                remainingDistanceMeters = route.distanceMeters,
                remainingDurationSeconds = route.durationSeconds,
            )
        }
        route.steps.firstOrNull()?.let { speakGuidance(it.instruction) }
        dispatchCamera(MapCameraCommand.FlyTo(startPt, zoom = 17.5, pitch = 62.0, bearing = _uiState.value.effectiveHeadingDeg.toDouble()))
    }

    fun stopNavigation() {
        runCatching { tts?.stop() }
        _uiState.update {
            it.copy(
                isNavigating = false,
                cameraMode = MapCameraMode.FREE,
                routes = emptyList(),
            )
        }
    }

    fun toggleVoiceGuidance() {
        _uiState.update {
            val next = !it.voiceGuidanceEnabled
            if (!next) runCatching { tts?.stop() }
            it.copy(voiceGuidanceEnabled = next)
        }
    }

    private fun speakGuidance(text: String) {
        if (!_uiState.value.voiceGuidanceEnabled || !ttsReady || text.isBlank()) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clipper_nav_${System.currentTimeMillis()}")
        }
    }

    // --- Nearby POIs (Overpass OpenStreetMap + Auto-Fetched Wikimedia/OSM Photos) ---

    fun togglePoiCategory(category: PoiCategory, useMapCenter: Boolean = false) {
        if (!useMapCenter && _uiState.value.activePoiCategory == category) {
            clearActivePoiCategory()
            return
        }
        val center = if (useMapCenter) {
            _uiState.value.mapCenter
        } else {
            _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
        }
        lastPoiSearchCenter = center
        poiJob?.cancel()
        poiPhotoJob?.cancel()
        poiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activePoiCategory = category,
                    isLoadingPois = true,
                    showMoreCategories = false,
                    showSearchOverlay = false,
                    showLeftSidebar = false,
                    showPoiResultsSheet = true,
                    showSearchThisAreaPill = false,
                    selectedPlace = null,
                    routes = emptyList(),
                    statusBanner = null,
                )
            }
            try {
                val pois = api.fetchNearbyPois(center, category, radiusMeters = 3800)
                _uiState.update {
                    it.copy(
                        isLoadingPois = false,
                        nearbyPois = pois,
                        statusBanner = if (pois.isEmpty()) "No ${category.label} found within 3.8 km — pan & tap Search this area" else "Found ${pois.size} ${category.label} nearby",
                    )
                }
                if (pois.isNotEmpty()) {
                    enrichPoiPhotosAsync(pois, category)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingPois = false, statusBanner = "Nearby POI search timed out") }
            }
        }
    }

    fun searchActiveCategoryInCurrentArea() {
        val active = _uiState.value.activePoiCategory ?: return
        togglePoiCategory(active, useMapCenter = true)
    }

    fun clearActivePoiCategory() {
        poiJob?.cancel()
        poiPhotoJob?.cancel()
        lastPoiSearchCenter = null
        _uiState.update {
            it.copy(
                activePoiCategory = null,
                nearbyPois = emptyList(),
                isLoadingPois = false,
                showPoiResultsSheet = false,
                showSearchThisAreaPill = false,
            )
        }
    }

    private fun enrichPoiPhotosAsync(pois: List<NearbyPoi>, category: PoiCategory) {
        poiPhotoJob?.cancel()
        poiPhotoJob = viewModelScope.launch {
            // Fetch authentic Wikimedia Commons / Wikipedia / OSM photos in small polite parallel batches
            pois.take(12).chunked(3).forEach { batch ->
                val fetchedPairs = coroutineScope {
                    batch.map { poi ->
                        async {
                            val photos = runCatching {
                                api.fetchRealPlacePhotos(
                                    name = poi.name,
                                    point = poi.point,
                                    categoryHint = category.label,
                                    wikipediaTag = poi.wikipediaTag,
                                    commonsTag = poi.wikimediaCommonsTag,
                                    directImageUrl = poi.directImageUrl,
                                )
                            }.getOrDefault(listOf(MapsGeoMath.osmTilePreviewUrl(poi.point, 17)))
                            poi.id to photos
                        }
                    }.awaitAll()
                }.toMap()

                _uiState.update { cur ->
                    if (cur.activePoiCategory != category) cur
                    else cur.copy(
                        nearbyPois = cur.nearbyPois.map { existing ->
                            fetchedPairs[existing.id]?.let { urls -> existing.copy(photoUrls = urls) } ?: existing
                        },
                    )
                }
            }
        }
    }

    fun poiToPlaceResult(poi: NearbyPoi): PlaceResult {
        val subtitleParts = buildList {
            add(poi.category.label)
            if (poi.cuisine.isNotBlank()) add(poi.cuisine.replace(';', ',').replace('_', ' '))
            if (poi.address.isNotBlank()) add(poi.address)
            add("${MapsGeoMath.formatDistance(poi.distanceMeters, _uiState.value.useImperialUnits)} away")
        }
        return PlaceResult(
            id = poi.id,
            name = poi.name,
            subtitle = subtitleParts.joinToString(" • "),
            point = poi.point,
            category = poi.category.label,
            phone = poi.phone,
            website = poi.website,
            openingHours = poi.openingHours,
            photoUrls = poi.photoUrls.ifEmpty { listOf(MapsGeoMath.osmTilePreviewUrl(poi.point, 17)) },
        )
    }

    fun onPoiClicked(poiId: String) {
        val poi = _uiState.value.nearbyPois.firstOrNull { it.id == poiId } ?: return
        selectPlaceResult(poiToPlaceResult(poi))
    }

    fun selectNearbyPoi(poi: NearbyPoi) {
        selectPlaceResult(poiToPlaceResult(poi))
    }

    fun routeToNearbyPoi(poi: NearbyPoi, autoStartNav: Boolean = false) {
        val place = poiToPlaceResult(poi)
        selectPlaceResult(place)
        calculateRouteTo(place, autoStartNav = autoStartNav)
    }

    // --- Microscopic Measurement Tool ---

    fun toggleMeasureMode() {
        _uiState.update { state ->
            val entering = !state.isMeasuring
            state.copy(
                isMeasuring = entering,
                showLayersSheet = if (entering) false else state.showLayersSheet,
                showBookmarksSheet = if (entering) false else state.showBookmarksSheet,
                showTelemetryDetailSheet = if (entering) false else state.showTelemetryDetailSheet,
                measurePoints = if (entering) state.measurePoints else emptyList(),
                measureClosedPolygon = if (entering) state.measureClosedPolygon else false,
                selectedPlace = if (entering) null else state.selectedPlace,
            )
        }
    }

    fun addMeasurePoint(point: GeoPoint) {
        _uiState.update { it.copy(measurePoints = it.measurePoints + point) }
    }

    fun undoMeasurePoint() {
        _uiState.update { it.copy(measurePoints = it.measurePoints.dropLast(1)) }
    }

    fun clearMeasurePoints() {
        _uiState.update { it.copy(measurePoints = emptyList(), measureClosedPolygon = false) }
    }

    fun toggleMeasurePolygon() {
        _uiState.update { it.copy(measureClosedPolygon = !it.measureClosedPolygon) }
    }

    // --- Trip Recorder ---

    fun toggleTripRecording() {
        _uiState.update { state ->
            val next = !state.isRecordingTrip
            val seed = if (next) listOfNotNull(state.liveLocation?.point) else emptyList()
            state.copy(
                isRecordingTrip = next,
                tripBreadcrumb = seed,
                tripDistanceMeters = 0.0,
                tripMaxSpeedMps = 0f,
                statusBanner = if (next) "Recording live GPS trail" else "Trip trail cleared",
            )
        }
    }

    // --- Smart Saved Routes Inbox (Favorites, Want to Go, Home, Work, Auto-Dwell, Frequent) ---

    fun toggleFavoritePlace(place: PlaceResult) {
        _uiState.update { state ->
            val (updatedSmart, added) = SmartPlaceHistoryCodec.togglePlaceInList(
                existing = state.smartPlaces,
                place = place,
                targetList = SavedRouteCategory.FAVORITE,
                nowMs = System.currentTimeMillis(),
            )
            SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updatedSmart)
            state.copy(
                smartPlaces = updatedSmart,
                statusBanner = if (added) "★ Saved \"${place.name}\" to Favorites" else "Removed \"${place.name}\" from Favorites",
            )
        }
    }

    fun toggleWantToGoPlace(place: PlaceResult) {
        _uiState.update { state ->
            val (updatedSmart, added) = SmartPlaceHistoryCodec.togglePlaceInList(
                existing = state.smartPlaces,
                place = place,
                targetList = SavedRouteCategory.WANT_TO_GO,
                nowMs = System.currentTimeMillis(),
            )
            SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updatedSmart)
            state.copy(
                smartPlaces = updatedSmart,
                statusBanner = if (added) "Added \"${place.name}\" to Want to go" else "Removed from Want to go",
            )
        }
    }

    fun setHomePlace(place: PlaceResult) {
        val saved = SmartSavedPlace(
            id = "home_pin",
            name = place.name.ifBlank { "Home" },
            subtitle = place.subtitle,
            point = place.point,
            categoryTag = "Home",
            listType = SavedRouteCategory.HOME,
            visitCount = 1,
            lastVisitedMs = System.currentTimeMillis(),
            photoUrl = place.photoUrls.firstOrNull().orEmpty().ifBlank { MapsGeoMath.osmTilePreviewUrl(place.point, 17) },
        )
        SmartPlaceHistoryCodec.saveHomePlace(prefs, saved)
        _uiState.update { it.copy(homePlace = saved, statusBanner = "Home location set to ${saved.name}") }
    }

    fun setWorkPlace(place: PlaceResult) {
        val saved = SmartSavedPlace(
            id = "work_pin",
            name = place.name.ifBlank { "Work" },
            subtitle = place.subtitle,
            point = place.point,
            categoryTag = "Work",
            listType = SavedRouteCategory.WORK,
            visitCount = 1,
            lastVisitedMs = System.currentTimeMillis(),
            photoUrl = place.photoUrls.firstOrNull().orEmpty().ifBlank { MapsGeoMath.osmTilePreviewUrl(place.point, 17) },
        )
        SmartPlaceHistoryCodec.saveWorkPlace(prefs, saved)
        _uiState.update { it.copy(workPlace = saved, statusBanner = "Work location set to ${saved.name}") }
    }

    fun setHomeFromCurrentLocation() {
        val pt = _uiState.value.selectedPlace?.point ?: _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
        viewModelScope.launch {
            val resolved = _uiState.value.selectedPlace ?: runCatching { api.reverseGeocode(pt) }.getOrElse {
                PlaceResult("home", "Home", MapsGeoMath.formatCoordinatesDms(pt), pt)
            }
            setHomePlace(resolved)
        }
    }

    fun setWorkFromCurrentLocation() {
        val pt = _uiState.value.selectedPlace?.point ?: _uiState.value.liveLocation?.point ?: _uiState.value.mapCenter
        viewModelScope.launch {
            val resolved = _uiState.value.selectedPlace ?: runCatching { api.reverseGeocode(pt) }.getOrElse {
                PlaceResult("work", "Work", MapsGeoMath.formatCoordinatesDms(pt), pt)
            }
            setWorkPlace(resolved)
        }
    }

    fun clearHomePlace() {
        SmartPlaceHistoryCodec.saveHomePlace(prefs, null)
        _uiState.update { it.copy(homePlace = null, statusBanner = "Home location cleared") }
    }

    fun clearWorkPlace() {
        SmartPlaceHistoryCodec.saveWorkPlace(prefs, null)
        _uiState.update { it.copy(workPlace = null, statusBanner = "Work location cleared") }
    }

    fun routeToSmartPlace(saved: SmartSavedPlace) {
        val place = PlaceResult(
            id = saved.id,
            name = saved.name,
            subtitle = saved.subtitle,
            point = saved.point,
            category = saved.categoryTag,
            photoUrls = listOfNotNull(saved.photoUrl.takeIf { it.isNotBlank() }),
        )
        selectPlaceResult(place)
        calculateRouteTo(place, autoStartNav = false)
    }

    fun deleteSmartPlace(id: String) {
        _uiState.update { state ->
            val updated = state.smartPlaces.filterNot { it.id == id }
            SmartPlaceHistoryCodec.saveSmartPlaces(prefs, updated)
            state.copy(smartPlaces = updated)
        }
    }

    fun toggleSaveBookmark(place: PlaceResult) {
        toggleFavoritePlace(place)
        _uiState.update { state ->
            val existing = state.savedBookmarks.firstOrNull {
                MapsGeoMath.haversineMeters(it.point, place.point) < 25.0
            }
            val updated = if (existing != null) {
                state.savedBookmarks.filterNot { it.id == existing.id }
            } else {
                listOf(
                    SavedPlaceBookmark(
                        id = UUID.randomUUID().toString().take(8),
                        name = place.name,
                        subtitle = place.subtitle,
                        point = place.point,
                        createdAtMs = System.currentTimeMillis(),
                    ),
                ) + state.savedBookmarks
            }
            persistSavedBookmarks(updated)
            state.copy(savedBookmarks = updated)
        }
    }

    fun deleteBookmark(id: String) {
        _uiState.update { state ->
            val updated = state.savedBookmarks.filterNot { it.id == id }
            persistSavedBookmarks(updated)
            state.copy(savedBookmarks = updated)
        }
    }

    fun clearStatusBanner() = _uiState.update { it.copy(statusBanner = null) }

    private fun fetchWeatherIfNeeded(point: GeoPoint) {
        val last = lastWeatherPoint
        if (last != null && MapsGeoMath.haversineMeters(last, point) < 12_000.0) return
        lastWeatherPoint = point
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val snap = api.fetchLocalWeather(point)
            if (snap != null) {
                _uiState.update { it.copy(weather = snap) }
            }
        }
    }

    /**
     * Streams real local OSM colony pathways (service, living_street, footway, pedestrian, steps),
     * 3D pitched/complex roof shapes (`roof:shape`), and 3D `natural=tree` nodes automatically when
     * zoomed into neighborhood scale (z >= 14.8). Cached by 170m radius to keep Overpass fast & polite.
     */
    private fun fetchLocalOsm3dSceneIfNeeded(center: GeoPoint, zoom: Double) {
        if (!_uiState.value.scene3dEnabled || zoom < 14.8) return
        val last = lastOsmScenePoint
        if (last != null && MapsGeoMath.haversineMeters(last, center) < 170.0) return
        osmSceneJob?.cancel()
        osmSceneJob = viewModelScope.launch {
            delay(550L)
            val bundle = api.fetchLocalOsm3dScene(center, radiusMeters = 340) ?: return@launch
            lastOsmScenePoint = center
            _uiState.update {
                it.copy(
                    osmMicroPathwaysGeoJson = bundle.pathwaysGeoJson,
                    osm3dRoofsGeoJson = bundle.roofsGeoJson,
                    osm3dTreesGeoJson = bundle.treesGeoJson,
                    osmScenePathCount = bundle.pathwaysCount,
                    osmSceneRoofCount = bundle.roofsCount,
                    osmSceneTreeCount = bundle.treesCount,
                )
            }
        }
    }

    private fun dispatchCamera(command: MapCameraCommand) {
        _uiState.update {
            it.copy(
                cameraCommandSeq = it.cameraCommandSeq + 1L,
                pendingCameraCommand = command,
            )
        }
    }

    private fun loadSavedBookmarks(): List<SavedPlaceBookmark> {
        val raw = prefs.getString("saved_bookmarks_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SavedPlaceBookmark(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            subtitle = o.optString("subtitle"),
                            point = GeoPoint(o.optDouble("lat"), o.optDouble("lng")),
                            createdAtMs = o.optLong("ts"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistSavedBookmarks(list: List<SavedPlaceBookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("subtitle", b.subtitle)
                    put("lat", b.point.lat)
                    put("lng", b.point.lng)
                    put("ts", b.createdAtMs)
                },
            )
        }
        prefs.edit().putString("saved_bookmarks_json", arr.toString()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        compassJob?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
