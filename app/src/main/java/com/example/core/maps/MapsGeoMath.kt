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
    LIBERTY("liberty", "Vector 3D", "OpenFreeMap OSM + 3D buildings", false),
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
) {
    DRIVING("Drive", "driving"),
    CYCLING("Cycle", "cycling"),
    WALKING("Walk", "foot"),
}

enum class PoiCategory(
    val label: String,
    val key: String,
    val badge: String,
    val overpassTag: String,
) {
    EV_CHARGER("EV Charge", "ev", "⚡", "\"amenity\"=\"charging_station\""),
    FUEL("Fuel", "fuel", "⛽", "\"amenity\"=\"fuel\""),
    CAFE("Cafes", "food", "☕", "\"amenity\"=\"cafe\""),
    RESTAURANT("Food", "food", "🍽", "\"amenity\"=\"restaurant\""),
    HOSPITAL("Medical", "hospital", "✚", "\"amenity\"=\"hospital\""),
    PARKING("Parking", "parking", "P", "\"amenity\"=\"parking\""),
}

data class PlaceResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val point: GeoPoint,
    val category: String = "",
)

data class NearbyPoi(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val point: GeoPoint,
    val distanceMeters: Double,
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

data class NavigationRoute(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val summary: String,
    val geometry: List<GeoPoint>,
    val steps: List<RouteStep>,
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
            val name = tags?.optString("name", "")?.trim().orEmpty()
                .ifBlank { tags?.optString("operator", "")?.trim().orEmpty() }
                .ifBlank { category.label }
            val pt = GeoPoint(lat, lng)
            list.add(
                NearbyPoi(
                    id = el.optLong("id", i.toLong()).toString(),
                    name = name,
                    category = category,
                    point = pt,
                    distanceMeters = haversineMeters(center, pt),
                ),
            )
        }
        return list.sortedBy { it.distanceMeters }.take(24)
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
}
