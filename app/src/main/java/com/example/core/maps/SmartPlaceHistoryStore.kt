package com.example.core.maps

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SavedRouteCategory(val label: String) {
    HOME("Home"),
    WORK("Work"),
    FAVORITE("Favorites"),
    AUTO_DWELL("Stayed 1–4h"),
    FREQUENT("Frequent"),
    WANT_TO_GO("Want to go"),
    RECENT("Recent"),
}

data class SmartSavedPlace(
    val id: String,
    val name: String,
    val subtitle: String,
    val point: GeoPoint,
    val categoryTag: String = "",
    val listType: SavedRouteCategory = SavedRouteCategory.RECENT,
    val visitCount: Int = 1,
    val dwellMinutes: Int = 0,
    val lastVisitedMs: Long = System.currentTimeMillis(),
    val photoUrl: String = "",
)

data class DwellAutoSaveTrigger(
    val point: GeoPoint,
    val dwellMinutes: Int,
    val isDurationUpdate: Boolean,
)

/**
 * Pure JVM-testable GPS Dwell-Time Detector.
 * Monitors continuous live GPS samples and triggers automatic location saving when the user
 * remains within a 120-meter radius for 1 to 4 hours (60–240 minutes).
 */
class DwellSessionTracker(
    private val stayRadiusMeters: Double = 120.0,
    private val minAutoSaveMinutes: Int = 60,
    private val maxTrackedDwellMinutes: Int = 240,
) {
    private var anchorPoint: GeoPoint? = null
    private var anchorStartMs: Long = 0L
    private var lastReportedMinutes: Int = 0

    fun onGpsSample(point: GeoPoint, timestampMs: Long): DwellAutoSaveTrigger? {
        val currentAnchor = anchorPoint
        if (currentAnchor == null || MapsGeoMath.haversineMeters(currentAnchor, point) > stayRadiusMeters) {
            anchorPoint = point
            anchorStartMs = timestampMs
            lastReportedMinutes = 0
            return null
        }

        val elapsedMinutes = ((timestampMs - anchorStartMs) / 60_000L).toInt().coerceAtLeast(0)
        if (elapsedMinutes < minAutoSaveMinutes) return null

        val clampedMinutes = elapsedMinutes.coerceAtMost(maxTrackedDwellMinutes)
        if (lastReportedMinutes == 0) {
            lastReportedMinutes = clampedMinutes
            return DwellAutoSaveTrigger(
                point = currentAnchor,
                dwellMinutes = clampedMinutes,
                isDurationUpdate = false,
            )
        }
        // Update stored stay duration every +30 minutes up to 4 hours (240 min)
        if (clampedMinutes - lastReportedMinutes >= 30) {
            lastReportedMinutes = clampedMinutes
            return DwellAutoSaveTrigger(
                point = currentAnchor,
                dwellMinutes = clampedMinutes,
                isDurationUpdate = true,
            )
        }
        return null
    }
}

/**
 * Local persistent manager for Home, Work, Favorites, Want-to-Go, Recent History,
 * Auto-Saved 1–4h Dwell Locations, and Frequently Opened Auto-Learned Routes.
 */
object SmartPlaceHistoryCodec {

    private const val KEY_SMART_PLACES = "clipper_maps_smart_places_v1"
    private const val KEY_HOME_PLACE = "clipper_maps_home_place_v1"
    private const val KEY_WORK_PLACE = "clipper_maps_work_place_v1"

    fun loadSmartPlaces(prefs: SharedPreferences): List<SmartSavedPlace> {
        val raw = prefs.getString(KEY_SMART_PLACES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(fromJson(o))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSmartPlaces(prefs: SharedPreferences, items: List<SmartSavedPlace>) {
        val arr = JSONArray()
        items.take(80).forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_SMART_PLACES, arr.toString()).apply()
    }

    fun loadHomePlace(prefs: SharedPreferences): SmartSavedPlace? {
        val raw = prefs.getString(KEY_HOME_PLACE, null) ?: return null
        return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun saveHomePlace(prefs: SharedPreferences, place: SmartSavedPlace?) {
        val editor = prefs.edit()
        if (place == null) editor.remove(KEY_HOME_PLACE) else editor.putString(KEY_HOME_PLACE, toJson(place).toString())
        editor.apply()
    }

    fun loadWorkPlace(prefs: SharedPreferences): SmartSavedPlace? {
        val raw = prefs.getString(KEY_WORK_PLACE, null) ?: return null
        return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun saveWorkPlace(prefs: SharedPreferences, place: SmartSavedPlace?) {
        val editor = prefs.edit()
        if (place == null) editor.remove(KEY_WORK_PLACE) else editor.putString(KEY_WORK_PLACE, toJson(place).toString())
        editor.apply()
    }

    /**
     * Pure functional reducer: records a user opening/inspecting/routing to a location.
     * Clusters within 150m; automatically promotes to FREQUENT once opened 2+ times!
     */
    fun recordPlaceInteraction(
        existing: List<SmartSavedPlace>,
        place: PlaceResult,
        nowMs: Long,
    ): List<SmartSavedPlace> {
        val matchIdx = existing.indexOfFirst { MapsGeoMath.haversineMeters(it.point, place.point) < 150.0 }
        val photo = place.photoUrls.firstOrNull().orEmpty().ifBlank { MapsGeoMath.osmTilePreviewUrl(place.point, 17) }
        return if (matchIdx >= 0) {
            val cur = existing[matchIdx]
            val newCount = cur.visitCount + 1
            val promotedType = when {
                cur.listType == SavedRouteCategory.FAVORITE ||
                    cur.listType == SavedRouteCategory.WANT_TO_GO ||
                    cur.listType == SavedRouteCategory.AUTO_DWELL -> cur.listType
                newCount >= 2 -> SavedRouteCategory.FREQUENT
                else -> SavedRouteCategory.RECENT
            }
            val updated = cur.copy(
                name = place.name.ifBlank { cur.name },
                subtitle = place.subtitle.ifBlank { cur.subtitle },
                categoryTag = place.category.ifBlank { cur.categoryTag },
                listType = promotedType,
                visitCount = newCount,
                lastVisitedMs = nowMs,
                photoUrl = place.photoUrls.firstOrNull { !it.contains("tile.openstreetmap.org") }
                    ?: cur.photoUrl.ifBlank { photo },
            )
            listOf(updated) + existing.filterIndexed { idx, _ -> idx != matchIdx }
        } else {
            val created = SmartSavedPlace(
                id = UUID.randomUUID().toString().take(8),
                name = place.name,
                subtitle = place.subtitle,
                point = place.point,
                categoryTag = place.category,
                listType = SavedRouteCategory.RECENT,
                visitCount = 1,
                dwellMinutes = 0,
                lastVisitedMs = nowMs,
                photoUrl = photo,
            )
            (listOf(created) + existing).take(80)
        }
    }

    /**
     * Pure functional reducer: saves or updates a location where the user stayed 1–4 hours.
     */
    fun upsertDwellAutoSavedPlace(
        existing: List<SmartSavedPlace>,
        place: PlaceResult,
        dwellMinutes: Int,
        nowMs: Long,
    ): List<SmartSavedPlace> {
        val matchIdx = existing.indexOfFirst { MapsGeoMath.haversineMeters(it.point, place.point) < 150.0 }
        val photo = place.photoUrls.firstOrNull().orEmpty().ifBlank { MapsGeoMath.osmTilePreviewUrl(place.point, 17) }
        return if (matchIdx >= 0) {
            val cur = existing[matchIdx]
            val nextType = if (cur.listType == SavedRouteCategory.FAVORITE) SavedRouteCategory.FAVORITE else SavedRouteCategory.AUTO_DWELL
            val updated = cur.copy(
                name = place.name.ifBlank { cur.name },
                subtitle = place.subtitle.ifBlank { cur.subtitle },
                listType = nextType,
                dwellMinutes = maxOf(cur.dwellMinutes, dwellMinutes),
                lastVisitedMs = nowMs,
                photoUrl = cur.photoUrl.ifBlank { photo },
            )
            listOf(updated) + existing.filterIndexed { idx, _ -> idx != matchIdx }
        } else {
            val created = SmartSavedPlace(
                id = UUID.randomUUID().toString().take(8),
                name = place.name,
                subtitle = place.subtitle,
                point = place.point,
                categoryTag = place.category.ifBlank { "Auto-saved stay" },
                listType = SavedRouteCategory.AUTO_DWELL,
                visitCount = 1,
                dwellMinutes = dwellMinutes,
                lastVisitedMs = nowMs,
                photoUrl = photo,
            )
            (listOf(created) + existing).take(80)
        }
    }

    /**
     * Pure functional reducer: toggles FAVORITE (or WANT_TO_GO) status on a place.
     */
    fun togglePlaceInList(
        existing: List<SmartSavedPlace>,
        place: PlaceResult,
        targetList: SavedRouteCategory,
        nowMs: Long,
    ): Pair<List<SmartSavedPlace>, Boolean> {
        val matchIdx = existing.indexOfFirst { MapsGeoMath.haversineMeters(it.point, place.point) < 65.0 }
        val photo = place.photoUrls.firstOrNull().orEmpty().ifBlank { MapsGeoMath.osmTilePreviewUrl(place.point, 17) }
        return if (matchIdx >= 0) {
            val cur = existing[matchIdx]
            if (cur.listType == targetList) {
                val demoted = if (cur.dwellMinutes >= 60) {
                    SavedRouteCategory.AUTO_DWELL
                } else if (cur.visitCount >= 2) {
                    SavedRouteCategory.FREQUENT
                } else {
                    SavedRouteCategory.RECENT
                }
                val updated = cur.copy(listType = demoted, lastVisitedMs = nowMs)
                (listOf(updated) + existing.filterIndexed { idx, _ -> idx != matchIdx }) to false
            } else {
                val updated = cur.copy(
                    name = place.name.ifBlank { cur.name },
                    subtitle = place.subtitle.ifBlank { cur.subtitle },
                    listType = targetList,
                    lastVisitedMs = nowMs,
                    photoUrl = place.photoUrls.firstOrNull { !it.contains("tile.openstreetmap.org") }
                        ?: cur.photoUrl.ifBlank { photo },
                )
                (listOf(updated) + existing.filterIndexed { idx, _ -> idx != matchIdx }) to true
            }
        } else {
            val created = SmartSavedPlace(
                id = UUID.randomUUID().toString().take(8),
                name = place.name,
                subtitle = place.subtitle,
                point = place.point,
                categoryTag = place.category,
                listType = targetList,
                visitCount = 1,
                dwellMinutes = 0,
                lastVisitedMs = nowMs,
                photoUrl = photo,
            )
            ((listOf(created) + existing).take(80)) to true
        }
    }

    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMin = ((nowMs - timestampMs) / 60_000L).coerceAtLeast(0L)
        return when {
            diffMin < 2L -> "Just now"
            diffMin < 60L -> "${diffMin}m ago"
            diffMin < 24L * 60L -> "${diffMin / 60L}h ago"
            else -> "${diffMin / (24L * 60L)}d ago"
        }
    }

    fun formatDwellBadge(minutes: Int): String {
        if (minutes <= 0) return ""
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "Stayed ${m}m"
            m == 0 -> "Stayed ${h}h"
            else -> "Stayed ${h}h ${m}m"
        }
    }

    private fun toJson(p: SmartSavedPlace): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("subtitle", p.subtitle)
        put("lat", p.point.lat)
        put("lng", p.point.lng)
        put("cat", p.categoryTag)
        put("list", p.listType.name)
        put("visits", p.visitCount)
        put("dwell", p.dwellMinutes)
        put("ts", p.lastVisitedMs)
        put("photo", p.photoUrl)
    }

    private fun fromJson(o: JSONObject): SmartSavedPlace = SmartSavedPlace(
        id = o.optString("id", UUID.randomUUID().toString().take(8)),
        name = o.optString("name", "Saved Place"),
        subtitle = o.optString("subtitle", ""),
        point = GeoPoint(o.optDouble("lat"), o.optDouble("lng")),
        categoryTag = o.optString("cat", ""),
        listType = runCatching { SavedRouteCategory.valueOf(o.optString("list", "RECENT")) }
            .getOrDefault(SavedRouteCategory.RECENT),
        visitCount = o.optInt("visits", 1).coerceAtLeast(1),
        dwellMinutes = o.optInt("dwell", 0).coerceAtLeast(0),
        lastVisitedMs = o.optLong("ts", System.currentTimeMillis()),
        photoUrl = o.optString("photo", ""),
    )
}
