package com.example.core.maps

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val lat: Double,
    val lng: Double,
)

enum class MapStylePreset(
    val styleId: String,
    val label: String,
    val subtitle: String,
    val isDark: Boolean,
) {
    LIBERTY("liberty", "Vector 3D+", "OpenFreeMap 3D + enhanced lanes", false),
    OSM_STREETS("osm_streets", "OSM Streets", "Every alley, footpath & local shop", false),
    BRIGHT("bright", "Day Light", "Crisp high-contrast street view", false),
    TESLA_DARK("tesla_dark", "Tesla Night", "Obsidian dark cockpit vector map", true),
    SATELLITE("satellite", "Satellite", "ESRI World Imagery + Hybrid labels", true),
    TOPO_3D("topo_3d", "3D Terrain", "OpenTopoMap + DEM elevation relief", false),
}

enum class MapCameraMode(val label: String) {
    FREE("Free"),
    FOLLOW("Follow"),
    TESLA_3D("3D Drive"),
}

enum class TravelProfile(
    val label: String,
    val osrmProfile: String,
    val isTransit: Boolean = false,
) {
    DRIVING("Drive", "driving"),
    BUS("Bus", "driving", isTransit = true),
    METRO("Metro", "foot", isTransit = true),
    TWO_WHEELER("2-Wheeler", "driving"),
    CYCLING("Cycle", "cycling"),
    WALKING("Walk", "foot"),
}

enum class PoiCategoryGroup(val label: String) {
    FOOD_DRINK("Food & drink"),
    THINGS_TO_DO("Things to do"),
    SHOPPING("Shopping"),
    SERVICES("Services"),
    TRANSIT("Transit"),
}

enum class PoiPinIconType(val pinImageId: String, val accentHex: String, val darkHex: String) {
    RESTAURANT("clipper-poi-pin-restaurant", "#EA580C", "#9A3412"),
    COFFEE("clipper-poi-pin-coffee", "#D97706", "#92400E"),
    BAR("clipper-poi-pin-bar", "#E11D48", "#9F1239"),
    PARK("clipper-poi-pin-park", "#16A34A", "#166534"),
    GYM("clipper-poi-pin-gym", "#0D9488", "#115E59"),
    CULTURE("clipper-poi-pin-culture", "#7C3AED", "#5B21B6"),
    CINEMA("clipper-poi-pin-cinema", "#9333EA", "#6B21A8"),
    SHOPPING("clipper-poi-pin-shopping", "#2563EB", "#1E40AF"),
    GROCERY("clipper-poi-pin-grocery", "#0284C7", "#075985"),
    HOTEL("clipper-poi-pin-hotel", "#0F766E", "#134E4A"),
    ATM("clipper-poi-pin-atm", "#DC2626", "#991B1B"),
    FUEL("clipper-poi-pin-fuel", "#EA580C", "#9A3412"),
    EV("clipper-poi-pin-ev", "#059669", "#065F46"),
    HOSPITAL("clipper-poi-pin-hospital", "#E11D48", "#9F1239"),
    PHARMACY("clipper-poi-pin-pharmacy", "#10B981", "#047857"),
    PARKING("clipper-poi-pin-parking", "#1D4ED8", "#1E3A8A"),
    CAR_SERVICE("clipper-poi-pin-car", "#475569", "#1E293B"),
    POST("clipper-poi-pin-post", "#CA8A04", "#854D0E"),
    BUS("clipper-poi-pin-bus", "#2563EB", "#1E3A8A"),
    METRO("clipper-poi-pin-metro", "#4F46E5", "#312E81"),
    SERVICE_GENERIC("clipper-poi-pin-service", "#0891B2", "#155E75"),
}

enum class PoiCategory(
    val label: String,
    val key: String,
    val badge: String,
    val overpassTag: String,
    val group: PoiCategoryGroup,
    val pinType: PoiPinIconType,
    val isQuickStrip: Boolean = false,
) {
    // --- Quick Strip + Food & Drink ---
    RESTAURANT("Restaurants", "restaurant", "🍽", "\"amenity\"~\"restaurant|fast_food\"", PoiCategoryGroup.FOOD_DRINK, PoiPinIconType.RESTAURANT, isQuickStrip = true),
    CAFE("Coffee", "coffee", "☕", "\"amenity\"=\"cafe\"", PoiCategoryGroup.FOOD_DRINK, PoiPinIconType.COFFEE, isQuickStrip = true),
    BAR("Bars", "bar", "🍸", "\"amenity\"~\"bar|pub\"", PoiCategoryGroup.FOOD_DRINK, PoiPinIconType.BAR),
    TAKEOUT("Takeout", "takeout", "🥡", "\"takeaway\"~\"yes|only\"", PoiCategoryGroup.FOOD_DRINK, PoiPinIconType.RESTAURANT),
    DELIVERY("Delivery", "delivery", "🛵", "\"delivery\"=\"yes\"", PoiCategoryGroup.FOOD_DRINK, PoiPinIconType.RESTAURANT),
    BAKERY("Bakeries", "bakery", "🥐", "\"shop\"=\"bakery\"", PoiCategoryGroup.FOOD_DRINK, PoiPinIconType.COFFEE),

    // --- Quick Strip + Services & Transit ---
    ATM("ATMs", "atm", "🏧", "\"amenity\"~\"atm|bank\"", PoiCategoryGroup.SERVICES, PoiPinIconType.ATM, isQuickStrip = true),
    FUEL("Petrol", "fuel", "⛽", "\"amenity\"=\"fuel\"", PoiCategoryGroup.SERVICES, PoiPinIconType.FUEL, isQuickStrip = true),
    EV_CHARGER("Charging stations", "ev", "⚡", "\"amenity\"=\"charging_station\"", PoiCategoryGroup.SERVICES, PoiPinIconType.EV, isQuickStrip = true),
    HOSPITAL("Hospitals & clinics", "hospital", "✚", "\"amenity\"~\"hospital|clinic|doctors\"", PoiCategoryGroup.SERVICES, PoiPinIconType.HOSPITAL, isQuickStrip = true),
    CHEMIST("Chemists", "chemist", "💊", "\"amenity\"=\"pharmacy\"", PoiCategoryGroup.SERVICES, PoiPinIconType.PHARMACY, isQuickStrip = true),
    HOTEL("Hotels", "hotel", "🛏", "\"tourism\"~\"hotel|motel|guest_house|hostel\"", PoiCategoryGroup.SERVICES, PoiPinIconType.HOTEL, isQuickStrip = true),
    BUS_STOP("Buses", "bus", "🚌", "\"highway\"~\"bus_stop|bus_station\"", PoiCategoryGroup.TRANSIT, PoiPinIconType.BUS, isQuickStrip = true),
    METRO_STATION("Metro", "metro", "🚇", "\"railway\"~\"station|subway_entrance|tram_stop\"", PoiCategoryGroup.TRANSIT, PoiPinIconType.METRO, isQuickStrip = true),
    PARKING("Parking", "parking", "P", "\"amenity\"=\"parking\"", PoiCategoryGroup.SERVICES, PoiPinIconType.PARKING, isQuickStrip = true),

    // --- Things to do ---
    PARK("Parks", "park", "🌲", "\"leisure\"~\"park|garden|nature_reserve\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.PARK, isQuickStrip = true),
    GYM("Gyms", "gym", "🏋", "\"leisure\"~\"fitness_centre|sports_centre\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.GYM),
    LIVE_MUSIC("Live music", "music", "🎵", "\"amenity\"~\"music_venue|nightclub|theatre\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.CULTURE),
    FILMS("Films", "films", "🎬", "\"amenity\"=\"cinema\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.CINEMA),
    ART("Art", "art", "🎨", "\"tourism\"~\"gallery|artwork\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.CULTURE),
    MUSEUM("Museums", "museum", "🏛", "\"tourism\"=\"museum\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.CULTURE),
    ATTRACTION("Attractions", "attraction", "🎡", "\"tourism\"~\"attraction|viewpoint|theme_park\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.CULTURE),
    LIBRARY("Libraries", "library", "📚", "\"amenity\"=\"library\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.CULTURE),
    NIGHTLIFE("Nightlife", "nightlife", "🥂", "\"amenity\"~\"nightclub|bar|pub\"", PoiCategoryGroup.THINGS_TO_DO, PoiPinIconType.BAR),

    // --- Shopping ---
    GROCERIES("Groceries", "groceries", "🛒", "\"shop\"~\"supermarket|greengrocer|grocery\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.GROCERY, isQuickStrip = true),
    SHOPPING_CENTRE("Shopping centres", "mall", "🏬", "\"shop\"~\"mall|department_store\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.SHOPPING),
    BEAUTY_SUPPLIES("Beauty supplies", "beauty_shop", "💄", "\"shop\"~\"cosmetics|perfumery|chemist\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.SHOPPING),
    ELECTRONICS("Electronics", "electronics", "💻", "\"shop\"~\"electronics|mobile_phone|computer\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.SHOPPING),
    CAR_DEALERS("Car dealers", "car_dealer", "🚘", "\"shop\"~\"car|motorcycle\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.CAR_SERVICE),
    SPORTING_GOODS("Sporting goods", "sports_shop", "⚽", "\"shop\"~\"sports|outdoor\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.SHOPPING),
    HOME_GARDEN("Home & garden", "home_garden", "🪴", "\"shop\"~\"hardware|furniture|garden_centre|doityourself\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.SHOPPING),
    CONVENIENCE("Convenience shops", "convenience", "🏪", "\"shop\"=\"convenience\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.GROCERY),
    CLOTHING("Clothing", "clothing", "👕", "\"shop\"~\"clothes|shoes|boutique\"", PoiCategoryGroup.SHOPPING, PoiPinIconType.SHOPPING),

    // --- Additional Services ---
    POST("Post", "post", "✉", "\"amenity\"=\"post_office\"", PoiCategoryGroup.SERVICES, PoiPinIconType.POST),
    BEAUTY_SALON("Beauty salons", "salon", "💇", "\"shop\"~\"beauty|hairdresser\"", PoiCategoryGroup.SERVICES, PoiPinIconType.SERVICE_GENERIC),
    DRY_CLEANING("Dry cleaning", "laundry", "👔", "\"shop\"~\"dry_cleaning|laundry\"", PoiCategoryGroup.SERVICES, PoiPinIconType.SERVICE_GENERIC),
    CAR_HIRE("Car hire", "car_hire", "🔑", "\"amenity\"=\"car_rental\"", PoiCategoryGroup.SERVICES, PoiPinIconType.CAR_SERVICE),
    CAR_REPAIR("Car repair", "car_repair", "🔧", "\"shop\"=\"car_repair\"", PoiCategoryGroup.SERVICES, PoiPinIconType.CAR_SERVICE),
    CAR_WASH("Car wash", "car_wash", "🚿", "\"amenity\"=\"car_wash\"", PoiCategoryGroup.SERVICES, PoiPinIconType.CAR_SERVICE),
    FOOD_SHELTER("Food shelters", "food_shelter", "🍲", "\"amenity\"~\"social_facility|soup_kitchen\"", PoiCategoryGroup.SERVICES, PoiPinIconType.SERVICE_GENERIC),
    NIGHT_SHELTER("Night shelters", "night_shelter", "🏠", "\"social_facility\"~\"shelter|homeless_shelter\"", PoiCategoryGroup.SERVICES, PoiPinIconType.SERVICE_GENERIC),
}

data class PlaceResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val point: GeoPoint,
    val category: String = "",
    val phone: String = "",
    val website: String = "",
    val openingHours: String = "",
    val photoUrls: List<String> = emptyList(),
)

data class NearbyPoi(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val point: GeoPoint,
    val distanceMeters: Double,
    val address: String = "",
    val openingHours: String = "",
    val phone: String = "",
    val website: String = "",
    val cuisine: String = "",
    val wheelchair: Boolean = false,
    val wikipediaTag: String = "",
    val wikimediaCommonsTag: String = "",
    val directImageUrl: String = "",
    val photoUrls: List<String> = emptyList(),
)

data class RouteStep(
    val instruction: String,
    val streetName: String,
    val maneuverType: String,
    val modifier: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val location: GeoPoint,
)

data class TransitLeg(
    val modeLabel: String,
    val title: String,
    val subtitle: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

data class NavigationRoute(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val summary: String,
    val geometry: List<GeoPoint>,
    val steps: List<RouteStep>,
    val transitLegs: List<TransitLeg> = emptyList(),
)

data class ScaleBarSpec(
    val label: String,
    val widthDp: Float,
    val metersRepresented: Double,
)

data class TravelModeEstimate(
    val profile: TravelProfile,
    val durationText: String,
    val note: String,
)

data class LocalWeatherSnapshot(
    val temperatureC: Double,
    val windKmh: Double,
    val conditionLabel: String,
)

data class RouteProgressMatch(
    val snappedPoint: GeoPoint,
    val distanceFromRouteMeters: Double,
    val remainingDistanceMeters: Double,
    val nearestVertexIndex: Int,
    val isOffRoute: Boolean,
)

data class SavedPlaceBookmark(
    val id: String,
    val name: String,
    val subtitle: String,
    val point: GeoPoint,
    val createdAtMs: Long,
)

/**
 * Pure JVM-testable geospatial math, spherical measurement, route snapping,
 * coordinate formatting, and open-source API JSON parsing engine.
 */
object MapsGeoMath {

    private const val EARTH_RADIUS_M = 6_371_008.8

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2.0).pow(2)
        return 2.0 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(max(0.0, 1.0 - h)))
    }

    fun initialBearingDegrees(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val deg = Math.toDegrees(atan2(y, x))
        return (((deg % 360.0) + 360.0) % 360.0).toFloat()
    }

    fun shortestAngleDeltaDegrees(fromDeg: Float, toDeg: Float): Float {
        var diff = ((toDeg - fromDeg + 540f) % 360f) - 180f
        if (diff < -180f) diff += 360f
        return diff
    }

    fun interpolateAngleDegrees(fromDeg: Float, toDeg: Float, factor: Float): Float {
        val t = factor.coerceIn(0f, 1f)
        val delta = shortestAngleDeltaDegrees(fromDeg, toDeg)
        return (((fromDeg + delta * t) % 360f) + 360f) % 360f
    }

    fun polylineLengthMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += haversineMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Computes geodesic polygon area on a sphere (m²) using the spherical excess / shoelace approximation
     * in local equal-area projection — accurate for plots from 1 m² to 1000+ km².
     */
    fun sphericalPolygonAreaSqMeters(points: List<GeoPoint>): Double {
        if (points.size < 3) return 0.0
        val refLat = Math.toRadians(points.map { it.lat }.average())
        val cosRef = cos(refLat)
        var sum = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val x1 = Math.toRadians(p1.lng) * EARTH_RADIUS_M * cosRef
            val y1 = Math.toRadians(p1.lat) * EARTH_RADIUS_M
            val x2 = Math.toRadians(p2.lng) * EARTH_RADIUS_M * cosRef
            val y2 = Math.toRadians(p2.lat) * EARTH_RADIUS_M
            sum += (x1 * y2 - x2 * y1)
        }
        return abs(sum) * 0.5
    }

    /**
     * Snaps a live GPS position onto the closest segment of an OSRM route polyline and computes
     * off-route deviation and exact remaining distance to destination.
     */
    fun matchPointToRoute(
        position: GeoPoint,
        routeGeometry: List<GeoPoint>,
        offRouteThresholdMeters: Double = 55.0,
    ): RouteProgressMatch {
        if (routeGeometry.isEmpty()) {
            return RouteProgressMatch(position, 0.0, 0.0, 0, false)
        }
        if (routeGeometry.size == 1) {
            val d = haversineMeters(position, routeGeometry[0])
            return RouteProgressMatch(routeGeometry[0], d, d, 0, d > offRouteThresholdMeters)
        }

        val cosLat = cos(Math.toRadians(position.lat)).coerceAtLeast(0.01)
        var bestDist = Double.MAX_VALUE
        var bestPoint = routeGeometry[0]
        var bestSegIndex = 0

        for (i in 0 until routeGeometry.lastIndex) {
            val a = routeGeometry[i]
            val b = routeGeometry[i + 1]
            val ax = (a.lng - position.lng) * cosLat
            val ay = a.lat - position.lat
            val bx = (b.lng - position.lng) * cosLat
            val by = b.lat - position.lat
            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq > 1e-18) (-(ax * dx + ay * dy) / lenSq).coerceIn(0.0, 1.0) else 0.0
            val proj = GeoPoint(
                lat = a.lat + (b.lat - a.lat) * t,
                lng = a.lng + (b.lng - a.lng) * t,
            )
            val dist = haversineMeters(position, proj)
            if (dist < bestDist) {
                bestDist = dist
                bestPoint = proj
                bestSegIndex = i
            }
        }

        var remaining = haversineMeters(bestPoint, routeGeometry[bestSegIndex + 1])
        for (i in (bestSegIndex + 1) until routeGeometry.lastIndex) {
            remaining += haversineMeters(routeGeometry[i], routeGeometry[i + 1])
        }

        return RouteProgressMatch(
            snappedPoint = bestPoint,
            distanceFromRouteMeters = bestDist,
            remainingDistanceMeters = remaining,
            nearestVertexIndex = bestSegIndex,
            isOffRoute = bestDist > offRouteThresholdMeters,
        )
    }

    /**
     * Parses direct coordinate search strings like "37.7749, -122.4194" so users can jump to exact lat/lng instantly.
     */
    fun tryParseDirectCoordinates(query: String): GeoPoint? {
        val cleaned = query.trim()
        val match = Regex("^(-?\\d{1,2}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)$").find(cleaned)
            ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return GeoPoint(lat, lng)
    }

    fun formatCoordinatesDms(point: GeoPoint): String {
        fun toDms(value: Double, pos: Char, neg: Char): String {
            val hemi = if (value >= 0) pos else neg
            val absVal = abs(value)
            val deg = floor(absVal).toInt()
            val minFloat = (absVal - deg) * 60.0
            val minutes = floor(minFloat).toInt()
            val seconds = (minFloat - minutes) * 60.0
            return String.format(Locale.US, "%d°%02d'%04.1f\"%c", deg, minutes, seconds, hemi)
        }
        return "${toDms(point.lat, 'N', 'S')}  ${toDms(point.lng, 'E', 'W')}"
    }

    fun formatDistance(meters: Double, useImperial: Boolean): String {
        if (useImperial) {
            val feet = meters * 3.28084
            return if (feet < 1000.0) {
                "${feet.roundToInt()} ft"
            } else {
                val miles = meters / 1609.344
                String.format(Locale.US, "%.1f mi", miles)
            }
        }
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        }
    }

    fun formatArea(sqMeters: Double, useImperial: Boolean): String {
        if (useImperial) {
            val sqFt = sqMeters * 10.7639
            return if (sqFt < 43_560.0) {
                "${sqFt.roundToInt()} ft²"
            } else {
                String.format(Locale.US, "%.2f ac", sqMeters / 4046.856)
            }
        }
        return when {
            sqMeters < 10_000.0 -> "${sqMeters.roundToInt()} m²"
            sqMeters < 1_000_000.0 -> String.format(Locale.US, "%.2f ha", sqMeters / 10_000.0)
            else -> String.format(Locale.US, "%.2f km²", sqMeters / 1_000_000.0)
        }
    }

    fun formatDurationSeconds(seconds: Double): String {
        val totalMin = (seconds / 60.0).roundToInt().coerceAtLeast(1)
        val hours = totalMin / 60
        val mins = totalMin % 60
        return when {
            hours == 0 -> "$mins min"
            mins == 0 -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }

    fun formatSpeed(speedMps: Float, useImperial: Boolean): Pair<Int, String> {
        val safe = speedMps.coerceAtLeast(0f)
        return if (useImperial) {
            (safe * 2.23694f).roundToInt() to "mph"
        } else {
            (safe * 3.6f).roundToInt() to "km/h"
        }
    }

    fun compassCardinal(bearingDeg: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = ((((bearingDeg % 360f) + 360f) % 360f + 22.5f) / 45f).toInt() % 8
        return dirs[idx]
    }

    // --- Pure Open-Source JSON Parsers (Nominatim, OSRM, Overpass, Open-Meteo) ---

    fun parseNominatimSearchJson(jsonText: String): List<PlaceResult> {
        val arr = JSONArray(jsonText)
        val results = ArrayList<PlaceResult>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lat = obj.optString("lat").toDoubleOrNull() ?: continue
            val lng = obj.optString("lon").toDoubleOrNull() ?: continue
            val displayName = obj.optString("display_name", "")
            val explicitName = obj.optString("name", "").trim()
            val parts = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val title = explicitName.ifEmpty { parts.firstOrNull() ?: "Pinned Location" }
            val subtitle = parts.drop(if (explicitName.isEmpty()) 1 else 0).take(3).joinToString(", ")
                .ifEmpty { String.format(Locale.US, "%.5f, %.5f", lat, lng) }
            results.add(
                PlaceResult(
                    id = obj.optString("place_id", "$lat,$lng"),
                    name = title,
                    subtitle = subtitle,
                    point = GeoPoint(lat, lng),
                    category = obj.optString("type", ""),
                ),
            )
        }
        return results
    }

    fun parseNominatimReverseJson(jsonText: String, fallbackPoint: GeoPoint): PlaceResult {
        val obj = JSONObject(jsonText)
        val displayName = obj.optString("display_name", "")
        val addr = obj.optJSONObject("address")
        val road = addr?.optString("road", "")?.trim().orEmpty()
        val house = addr?.optString("house_number", "")?.trim().orEmpty()
        val city = addr?.optString("city", "")?.ifBlank {
            addr.optString("town", "").ifBlank { addr.optString("village", "") }
        }.orEmpty()
        val state = addr?.optString("state", "")?.trim().orEmpty()

        val title = when {
            obj.optString("name", "").isNotBlank() -> obj.optString("name").trim()
            road.isNotEmpty() && house.isNotEmpty() -> "$house $road"
            road.isNotEmpty() -> road
            displayName.isNotBlank() -> displayName.substringBefore(",").trim()
            else -> "Dropped Pin"
        }
        val subtitle = listOf(city, state).filter { it.isNotEmpty() }.joinToString(", ")
            .ifEmpty { displayName.substringAfter(",", "").trim() }
            .ifEmpty { formatCoordinatesDms(fallbackPoint) }

        return PlaceResult(
            id = obj.optString("place_id", "${fallbackPoint.lat},${fallbackPoint.lng}"),
            name = title,
            subtitle = subtitle,
            point = fallbackPoint,
            category = obj.optString("type", "pin"),
        )
    }

    fun parseOsrmRoutesJson(jsonText: String): List<NavigationRoute> {
        val root = JSONObject(jsonText)
        if (root.optString("code") != "Ok") return emptyList()
        val routesArr = root.optJSONArray("routes") ?: return emptyList()
        val out = ArrayList<NavigationRoute>(routesArr.length())

        for (rIdx in 0 until routesArr.length()) {
            val rObj = routesArr.optJSONObject(rIdx) ?: continue
            val dist = rObj.optDouble("distance", 0.0)
            val dur = rObj.optDouble("duration", 0.0)
            val geomObj = rObj.optJSONObject("geometry")
            val coordsArr = geomObj?.optJSONArray("coordinates") ?: continue

            val geometry = ArrayList<GeoPoint>(coordsArr.length())
            for (cIdx in 0 until coordsArr.length()) {
                val pair = coordsArr.optJSONArray(cIdx) ?: continue
                if (pair.length() >= 2) {
                    geometry.add(GeoPoint(lat = pair.optDouble(1), lng = pair.optDouble(0)))
                }
            }

            val legsArr = rObj.optJSONArray("legs")
            val steps = mutableListOf<RouteStep>()
            var summary = ""
            if (legsArr != null && legsArr.length() > 0) {
                val leg0 = legsArr.optJSONObject(0)
                summary = leg0?.optString("summary", "")?.trim().orEmpty()
                val stepsArr = leg0?.optJSONArray("steps")
                if (stepsArr != null) {
                    for (sIdx in 0 until stepsArr.length()) {
                        val sObj = stepsArr.optJSONObject(sIdx) ?: continue
                        val man = sObj.optJSONObject("maneuver")
                        val manType = man?.optString("type", "continue").orEmpty()
                        val modifier = man?.optString("modifier", "").orEmpty()
                        val locArr = man?.optJSONArray("location")
                        val stepPoint = if (locArr != null && locArr.length() >= 2) {
                            GeoPoint(locArr.optDouble(1), locArr.optDouble(0))
                        } else {
                            geometry.firstOrNull() ?: GeoPoint(0.0, 0.0)
                        }
                        val street = sObj.optString("name", "").trim()
                        steps.add(
                            RouteStep(
                                instruction = buildHumanInstruction(manType, modifier, street),
                                streetName = street,
                                maneuverType = manType,
                                modifier = modifier,
                                distanceMeters = sObj.optDouble("distance", 0.0),
                                durationSeconds = sObj.optDouble("duration", 0.0),
                                location = stepPoint,
                            ),
                        )
                    }
                }
            }

            out.add(
                NavigationRoute(
                    index = rIdx,
                    distanceMeters = dist,
                    durationSeconds = dur,
                    summary = summary.ifBlank { if (rIdx == 0) "Fastest route" else "Alternative ${rIdx + 1}" },
                    geometry = geometry,
                    steps = steps,
                ),
            )
        }
        return out
    }

    fun buildHumanInstruction(type: String, modifier: String, street: String): String {
        val onto = if (street.isNotBlank()) " onto $street" else ""
        val modLabel = modifier.replace('_', ' ').trim()
        return when (type.lowercase(Locale.US)) {
            "depart" -> if (street.isNotBlank()) "Head out on $street" else "Start route"
            "arrive" -> "You have arrived at your destination"
            "turn", "end of road", "fork", "off ramp", "on ramp" -> {
                val dir = when (modLabel) {
                    "left" -> "Turn left"
                    "right" -> "Turn right"
                    "slight left" -> "Keep slight left"
                    "slight right" -> "Keep slight right"
                    "sharp left" -> "Make a sharp left"
                    "sharp right" -> "Make a sharp right"
                    "uturn" -> "Make a U-turn"
                    else -> "Continue"
                }
                "$dir$onto"
            }
            "roundabout", "rotary" -> "Enter roundabout$onto"
            "merge" -> "Merge${if (modLabel.isNotEmpty()) " $modLabel" else ""}$onto"
            "new name", "continue" -> if (street.isNotBlank()) "Continue on $street" else "Continue straight"
            else -> if (street.isNotBlank()) "Follow $street" else "Continue ahead"
        }
    }

    fun parseOverpassPoisJson(
        jsonText: String,
        center: GeoPoint,
        category: PoiCategory,
    ): List<NearbyPoi> {
        val root = JSONObject(jsonText)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val list = ArrayList<NearbyPoi>()
        val seenKeys = HashSet<String>()
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val lat = el.optDouble("lat", Double.NaN).let {
                if (it.isNaN()) el.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN else it
            }
            val lng = el.optDouble("lon", Double.NaN).let {
                if (it.isNaN()) el.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN else it
            }
            if (lat.isNaN() || lng.isNaN()) continue
            val tags = el.optJSONObject("tags")
            val brand = tags?.optString("brand", "")?.trim().orEmpty()
            val name = tags?.optString("name", "")?.trim().orEmpty()
                .ifBlank { brand }
                .ifBlank { tags?.optString("operator", "")?.trim().orEmpty() }
                .ifBlank { category.label }

            val street = tags?.optString("addr:street", "")?.trim().orEmpty()
            val house = tags?.optString("addr:housenumber", "")?.trim().orEmpty()
            val suburb = tags?.optString("addr:suburb", "")?.trim().orEmpty()
                .ifBlank { tags?.optString("addr:city", "")?.trim().orEmpty() }
            val address = listOfNotNull(
                listOf(house, street).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { null },
                suburb.ifBlank { null },
            ).joinToString(", ")

            val phone = tags?.optString("phone", "")?.trim().orEmpty()
                .ifBlank { tags?.optString("contact:phone", "")?.trim().orEmpty() }
            val website = tags?.optString("website", "")?.trim().orEmpty()
                .ifBlank { tags?.optString("contact:website", "")?.trim().orEmpty() }
            val openingHours = tags?.optString("opening_hours", "")?.trim().orEmpty()
            val cuisine = tags?.optString("cuisine", "")?.trim().orEmpty()
                .replace(';', '·').replace('_', ' ')
            val wheelchair = tags?.optString("wheelchair", "")?.lowercase(Locale.US) in setOf("yes", "designated")
            val wiki = tags?.optString("wikipedia", "")?.trim().orEmpty()
            val commons = tags?.optString("wikimedia_commons", "")?.trim().orEmpty()
            val directImg = tags?.optString("image", "")?.trim().orEmpty()
                .takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()

            val pt = GeoPoint(lat, lng)
            val dedupKey = "${name.lowercase(Locale.US)}_${(lat * 2000).roundToInt()}_${(lng * 2000).roundToInt()}"
            if (!seenKeys.add(dedupKey)) continue

            list.add(
                NearbyPoi(
                    id = el.optLong("id", i.toLong()).toString(),
                    name = name,
                    category = category,
                    point = pt,
                    distanceMeters = haversineMeters(center, pt),
                    address = address,
                    openingHours = openingHours,
                    phone = phone,
                    website = website,
                    cuisine = cuisine,
                    wheelchair = wheelchair,
                    wikipediaTag = wiki,
                    wikimediaCommonsTag = commons,
                    directImageUrl = directImg,
                    photoUrls = listOfNotNull(
                        directImg.ifBlank { null },
                        osmTilePreviewUrl(pt, zoom = 17),
                    ),
                ),
            )
        }
        return list.sortedBy { it.distanceMeters }.take(32)
    }

    /**
     * Exact Web Mercator Slippy Map tile URL for a real geographic coordinate so any OSM place
     * always has a crisp, authentic map-context visual even before Wikimedia photos resolve.
     */
    fun osmTilePreviewUrl(point: GeoPoint, zoom: Int = 17): String {
        val z = zoom.coerceIn(2, 18)
        val n = 2.0.pow(z.toDouble())
        val latRad = Math.toRadians(point.lat.coerceIn(-85.0511, 85.0511))
        val x = floor((point.lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n.toInt() - 1)
        val y = floor((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n)
            .toInt().coerceIn(0, n.toInt() - 1)
        return "https://tile.openstreetmap.org/$z/$x/$y.png"
    }

    /**
     * Evaluates common OSM `opening_hours` strings (e.g. "24/7", "08:00-23:00", "Mo-Su 09:30-22:00",
     * overnight "18:00-02:00") against the current local hour/minute. Returns null if unknown.
     */
    fun isLikelyOpenNow(openingHours: String, hourOfDay: Int, minute: Int): Boolean? {
        val raw = openingHours.trim().lowercase(Locale.US)
        if (raw.isEmpty()) return null
        if (raw.contains("24/7") || raw == "open") return true
        if (raw == "off" || raw == "closed") return false
        val rangeMatch = Regex("""(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})""").find(raw) ?: return null
        val startMin = rangeMatch.groupValues[1].toInt() * 60 + rangeMatch.groupValues[2].toInt()
        val endMin = rangeMatch.groupValues[3].toInt() * 60 + rangeMatch.groupValues[4].toInt()
        val nowMin = hourOfDay * 60 + minute
        return if (endMin >= startMin) {
            nowMin in startMin..endMin
        } else {
            // Overnight hours (e.g. 18:00-02:00)
            nowMin >= startMin || nowMin <= endMin
        }
    }

    fun parseOpenMeteoCurrentJson(jsonText: String): LocalWeatherSnapshot? {
        val root = JSONObject(jsonText)
        val cur = root.optJSONObject("current") ?: return null
        val temp = cur.optDouble("temperature_2m", Double.NaN)
        val wind = cur.optDouble("wind_speed_10m", 0.0)
        val code = cur.optInt("weather_code", 0)
        if (temp.isNaN()) return null
        val label = when (code) {
            0 -> "Clear"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55, 61, 63, 65 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            95, 96, 99 -> "Storm"
            else -> "Fair"
        }
        return LocalWeatherSnapshot(temp, wind, label)
    }

    fun routesToGeoJsonCoordsArray(routes: List<NavigationRoute>): String {
        val outer = JSONArray()
        routes.forEach { route ->
            val rArr = JSONArray()
            route.geometry.forEach { pt ->
                val pair = JSONArray()
                pair.put(pt.lng)
                pair.put(pt.lat)
                rArr.put(pair)
            }
            outer.put(rArr)
        }
        return outer.toString()
    }

    fun pointsToLngLatArrayJson(points: List<GeoPoint>): String {
        val arr = JSONArray()
        points.forEach { pt ->
            val pair = JSONArray()
            pair.put(pt.lng)
            pair.put(pt.lat)
            arr.put(pair)
        }
        return arr.toString()
    }

    fun poisToMarkerJson(pois: List<NearbyPoi>): String {
        val arr = JSONArray()
        pois.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("lat", p.point.lat)
            obj.put("lng", p.point.lng)
            obj.put("name", p.name)
            obj.put("categoryKey", p.category.key)
            obj.put("badge", p.category.badge)
            arr.put(obj)
        }
        return arr.toString()
    }

    /**
     * Computes exact physical Web Mercator map scale bar width and human-friendly distance label
     * at the current latitude and MapLibre zoom level (using 512px tile resolution).
     */
    fun computeScaleBarSpec(
        latitudeDeg: Double,
        zoom: Double,
        useImperial: Boolean,
        maxBarWidthDp: Float = 92f,
    ): ScaleBarSpec {
        val clampedLat = latitudeDeg.coerceIn(-85.0, 85.0)
        // MapLibre GL uses 512px base tiles; at 160dpi (1dp = 1px logical) -> 78271.51696 m/dp at z=0
        val metersPerDp = (78_271.51696 * cos(Math.toRadians(clampedLat))) / 2.0.pow(zoom.coerceIn(0.0, 22.0))
        val maxMeters = (metersPerDp * maxBarWidthDp).coerceAtLeast(1.0)

        if (useImperial) {
            val niceFeet = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0)
            val niceMiles = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0)
            val candidatesMeters = niceFeet.map { it / 3.28084 to "${it.toInt()} ft" } +
                niceMiles.map { it * 1609.344 to if (it < 1.0) "0.5 mi" else "${it.toInt()} mi" }
            val chosen = candidatesMeters.lastOrNull { it.first <= maxMeters } ?: candidatesMeters.first()
            val widthDp = (chosen.first / metersPerDp).toFloat().coerceIn(32f, maxBarWidthDp)
            return ScaleBarSpec(chosen.second, widthDp, chosen.first)
        }

        val niceMeters = listOf(
            5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
            1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0, 100_000.0, 250_000.0, 500_000.0,
        )
        val chosenMeters = niceMeters.lastOrNull { it <= maxMeters } ?: niceMeters.first()
        val label = if (chosenMeters < 1000.0) "${chosenMeters.toInt()} m" else "${(chosenMeters / 1000.0).toInt()} km"
        val widthDp = (chosenMeters / metersPerDp).toFloat().coerceIn(32f, maxBarWidthDp)
        return ScaleBarSpec(label, widthDp, chosenMeters)
    }

    /**
     * Computes realistic multi-modal travel time & effort comparisons for a geodesic or routed distance.
     */
    fun estimateTravelComparison(distanceMeters: Double): List<TravelModeEstimate> {
        val d = distanceMeters.coerceAtLeast(10.0)
        val driveSec = (d / 9.5) + 120.0 // ~34 km/h urban avg
        val busSec = (d / 5.8) + 360.0 // ~21 km/h incl. bus stop wait & stops
        val metroSec = (d / 11.5) + 420.0 // ~41 km/h rail + platform transfer
        val twoWheelerSec = (d / 11.0) + 60.0 // ~40 km/h urban agile
        val cycleSec = d / 4.2 // ~15 km/h
        val walkSec = d / 1.35 // ~4.9 km/h
        val steps = (d / 0.76).roundToInt()

        return listOf(
            TravelModeEstimate(TravelProfile.DRIVING, formatDurationSeconds(driveSec), "City traffic avg"),
            TravelModeEstimate(TravelProfile.BUS, formatDurationSeconds(busSec), "Incl. stop boarding"),
            TravelModeEstimate(TravelProfile.METRO, formatDurationSeconds(metroSec), "Rail + platform walk"),
            TravelModeEstimate(TravelProfile.TWO_WHEELER, formatDurationSeconds(twoWheelerSec), "Urban scooter/bike"),
            TravelModeEstimate(TravelProfile.CYCLING, formatDurationSeconds(cycleSec), "~${(d / 1000.0 * 32).roundToInt()} kcal"),
            TravelModeEstimate(TravelProfile.WALKING, formatDurationSeconds(walkSec), "~$steps steps"),
        )
    }

    /**
     * Synthesizes a genuine 3-leg public transit itinerary (Walk -> Bus/Metro -> Walk) from real
     * OpenStreetMap stops/stations and OSRM road/corridor geometry.
     */
    fun buildTransitRouteFromRealStops(
        profile: TravelProfile,
        origin: GeoPoint,
        destination: GeoPoint,
        corridorRoute: NavigationRoute,
        originStop: NearbyPoi?,
        destStop: NearbyPoi?,
    ): NavigationRoute {
        val isMetro = profile == TravelProfile.METRO
        val modeWord = if (isMetro) "Metro / Rail" else "City Bus"
        val walkSpeedMps = 1.35
        val transitSpeedMps = if (isMetro) 11.8 else 6.0
        val waitSec = if (isMetro) 240.0 else 300.0

        val boardPt = originStop?.point ?: origin
        val alightPt = destStop?.point ?: destination
        val walkToBoardM = if (originStop != null) haversineMeters(origin, boardPt) else 0.0
        val walkFromAlightM = if (destStop != null) haversineMeters(alightPt, destination) else 0.0
        val rideDistM = max(
            corridorRoute.distanceMeters - (walkToBoardM + walkFromAlightM) * 0.4,
            haversineMeters(boardPt, alightPt),
        ).coerceAtLeast(150.0)

        val walk1Sec = walkToBoardM / walkSpeedMps
        val rideSec = (rideDistM / transitSpeedMps) + waitSec
        val walk2Sec = walkFromAlightM / walkSpeedMps
        val totalDist = walkToBoardM + rideDistM + walkFromAlightM
        val totalSec = walk1Sec + rideSec + walk2Sec

        val legs = mutableListOf<TransitLeg>()
        val steps = mutableListOf<RouteStep>()

        if (originStop != null && walkToBoardM >= 25.0) {
            legs.add(
                TransitLeg(
                    modeLabel = "WALK",
                    title = "Walk to ${originStop.name}",
                    subtitle = "Nearest OSM ${if (isMetro) "station" else "bus stop"}",
                    distanceMeters = walkToBoardM,
                    durationSeconds = walk1Sec,
                ),
            )
            steps.add(
                RouteStep(
                    instruction = "Walk to ${originStop.name}",
                    streetName = originStop.name,
                    maneuverType = "depart",
                    modifier = "straight",
                    distanceMeters = walkToBoardM,
                    durationSeconds = walk1Sec,
                    location = origin,
                ),
            )
        }

        val boardTitle = originStop?.name ?: "Nearest $modeWord stop"
        val alightTitle = destStop?.name ?: "Destination $modeWord stop"
        val estStops = (rideDistM / if (isMetro) 1250.0 else 550.0).roundToInt().coerceAtLeast(1)
        legs.add(
            TransitLeg(
                modeLabel = if (isMetro) "METRO" else "BUS",
                title = "$modeWord: $boardTitle → $alightTitle",
                subtitle = "~$estStops ${if (estStops == 1) "stop" else "stops"} • via ${corridorRoute.summary}",
                distanceMeters = rideDistM,
                durationSeconds = rideSec,
            ),
        )
        steps.add(
            RouteStep(
                instruction = "Board $modeWord at $boardTitle toward $alightTitle",
                streetName = corridorRoute.summary,
                maneuverType = "continue",
                modifier = "straight",
                distanceMeters = rideDistM,
                durationSeconds = rideSec,
                location = boardPt,
            ),
        )

        if (destStop != null && walkFromAlightM >= 25.0) {
            legs.add(
                TransitLeg(
                    modeLabel = "WALK",
                    title = "Walk from ${destStop.name} to destination",
                    subtitle = "Final pedestrian leg",
                    distanceMeters = walkFromAlightM,
                    durationSeconds = walk2Sec,
                ),
            )
            steps.add(
                RouteStep(
                    instruction = "Exit at ${destStop.name} and walk to destination",
                    streetName = destStop.name,
                    maneuverType = "arrive",
                    modifier = "straight",
                    distanceMeters = walkFromAlightM,
                    durationSeconds = walk2Sec,
                    location = alightPt,
                ),
            )
        }

        val combinedGeom = buildList {
            add(origin)
            if (originStop != null) add(originStop.point)
            addAll(corridorRoute.geometry)
            if (destStop != null) add(destStop.point)
            add(destination)
        }

        val summaryLabel = if (originStop != null && destStop != null) {
            "$modeWord • ${originStop.name} → ${destStop.name}"
        } else {
            "$modeWord corridor via ${corridorRoute.summary}"
        }

        return NavigationRoute(
            index = 0,
            distanceMeters = totalDist,
            durationSeconds = totalSec,
            summary = summaryLabel,
            geometry = combinedGeom,
            steps = if (steps.isNotEmpty()) steps else corridorRoute.steps,
            transitLegs = legs,
        )
    }
}
