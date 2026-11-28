package com.example.core.maps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 100% Free, Zero-API-Key Open-Source Geospatial Services Client:
 * - OpenStreetMap Nominatim (forward place search & reverse geocoding)
 * - OSRM (Open Source Routing Machine) for multi-alternative turn-by-turn routes
 * - OpenStreetMap Overpass API for live nearby POIs (EV chargers, fuel, food, medical, parking)
 * - Open-Meteo for real-time local temperature & wind telemetry
 */
class MapsApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .build(),
) {
    private val userAgent = "ClipperAndroidMaps/1.0 (OpenStreetMap-Client)"
    private val photoCache = ConcurrentHashMap<String, List<String>>()

    suspend fun searchPlaces(
        query: String,
        biasNear: GeoPoint? = null,
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // Allow direct "lat, lng" coordinate jumps offline/instantly
        MapsGeoMath.tryParseDirectCoordinates(trimmed)?.let { direct ->
            return@withContext listOf(
                PlaceResult(
                    id = "coords_${direct.lat}_${direct.lng}",
                    name = "Coordinates ${String.format(Locale.US, "%.4f, %.4f", direct.lat, direct.lng)}",
                    subtitle = MapsGeoMath.formatCoordinatesDms(direct),
                    point = direct,
                    category = "coordinates",
                ),
            )
        }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val viewbox = biasNear?.let { pt ->
            val d = 0.65
            String.format(
                Locale.US,
                "&viewbox=%.4f,%.4f,%.4f,%.4f",
                pt.lng - d,
                pt.lat + d,
                pt.lng + d,
                pt.lat - d,
            )
        }.orEmpty()
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&addressdetails=1&q=$encoded$viewbox"
        val body = executeGet(url) ?: return@withContext emptyList()
        MapsGeoMath.parseNominatimSearchJson(body)
    }

    suspend fun reverseGeocode(point: GeoPoint): PlaceResult = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1",
            point.lat,
            point.lng,
        )
        val body = executeGet(url)
        if (body.isNullOrBlank()) {
            PlaceResult(
                id = "${point.lat},${point.lng}",
                name = "Dropped Pin",
                subtitle = MapsGeoMath.formatCoordinatesDms(point),
                point = point,
            )
        } else {
            runCatching { MapsGeoMath.parseNominatimReverseJson(body, point) }.getOrElse {
                PlaceResult(
                    id = "${point.lat},${point.lng}",
                    name = "Dropped Pin",
                    subtitle = MapsGeoMath.formatCoordinatesDms(point),
                    point = point,
                )
            }
        }
    }

    suspend fun fetchRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        profile: TravelProfile,
    ): List<NavigationRoute> = withContext(Dispatchers.IO) {
        when (profile) {
            TravelProfile.BUS, TravelProfile.METRO -> {
                val baseRoutes = queryOsrmWithFallback(origin, destination, "driving", alternatives = false)
                val corridor = baseRoutes.firstOrNull() ?: return@withContext emptyList()

                val stopCategory = if (profile == TravelProfile.METRO) PoiCategory.METRO_STATION else PoiCategory.BUS_STOP
                val searchRadius = if (profile == TravelProfile.METRO) 4500 else 1800
                val originStops = fetchNearbyPois(origin, stopCategory, radiusMeters = searchRadius)
                val destStops = fetchNearbyPois(destination, stopCategory, radiusMeters = searchRadius)

                val originStop = originStops.firstOrNull()
                val destStop = destStops.firstOrNull { it.id != originStop?.id } ?: destStops.firstOrNull()

                val transitRoute = MapsGeoMath.buildTransitRouteFromRealStops(
                    profile = profile,
                    origin = origin,
                    destination = destination,
                    corridorRoute = corridor,
                    originStop = originStop,
                    destStop = destStop,
                )
                listOf(transitRoute)
            }
            TravelProfile.TWO_WHEELER -> {
                queryOsrmWithFallback(origin, destination, "driving", alternatives = true).map { route ->
                    route.copy(
                        durationSeconds = (route.durationSeconds * 0.84).coerceAtLeast(60.0),
                        summary = "2-Wheeler • ${route.summary}",
                    )
                }
            }
            else -> {
                queryOsrmWithFallback(origin, destination, profile.osrmProfile, alternatives = true)
            }
        }
    }

    private fun queryOsrmWithFallback(
        origin: GeoPoint,
        destination: GeoPoint,
        osrmProfile: String,
        alternatives: Boolean,
    ): List<NavigationRoute> {
        val fossgisSubpath = when (osrmProfile) {
            "cycling" -> "routed-bike"
            "foot" -> "routed-foot"
            else -> "routed-car"
        }
        val altFlag = if (alternatives) "true" else "false"
        val candidateUrls = listOf(
            String.format(
                Locale.US,
                "https://routing.openstreetmap.de/%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=%s",
                fossgisSubpath,
                origin.lng,
                origin.lat,
                destination.lng,
                destination.lat,
                altFlag,
            ),
            String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=%s",
                osrmProfile,
                origin.lng,
                origin.lat,
                destination.lng,
                destination.lat,
                altFlag,
            ),
        )
        for (url in candidateUrls) {
            val body = executeGet(url) ?: continue
            val parsed = runCatching { MapsGeoMath.parseOsrmRoutesJson(body) }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    suspend fun fetchNearbyPois(
        center: GeoPoint,
        category: PoiCategory,
        radiusMeters: Int = 3500,
    ): List<NearbyPoi> = withContext(Dispatchers.IO) {
        val query = String.format(
            Locale.US,
            "[out:json][timeout:12];(node[%s](around:%d,%.5f,%.5f);way[%s](around:%d,%.5f,%.5f););out center 36;",
            category.overpassTag,
            radiusMeters,
            center.lat,
            center.lng,
            category.overpassTag,
            radiusMeters,
            center.lat,
            center.lng,
        )
        val encoded = URLEncoder.encode(query, "UTF-8")
        val mirrors = listOf(
            "https://overpass-api.de/api/interpreter?data=$encoded",
            "https://overpass.kumi.systems/api/interpreter?data=$encoded",
        )
        for (url in mirrors) {
            val body = executeGet(url) ?: continue
            val parsed = runCatching { MapsGeoMath.parseOverpassPoisJson(body, center, category) }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        emptyList()
    }

    /**
     * Automatically fetches real, open-licensed photographs for any live OSM place using:
     *  1. Direct OSM `image` / `wikimedia_commons` tags
     *  2. Wikipedia REST summary thumbnail (when `wikipedia` tag is present)
     *  3. Wikimedia Commons Search API by real place name + category keyword
     *  4. Wikimedia Commons Geosearch API around the exact GPS coordinates
     * Always appends the live OSM Slippy Map preview tile as a guaranteed crisp visual.
     */
    suspend fun fetchRealPlacePhotos(
        name: String,
        point: GeoPoint,
        categoryHint: String = "",
        wikipediaTag: String = "",
        commonsTag: String = "",
        directImageUrl: String = "",
    ): List<String> = withContext(Dispatchers.IO) {
        val cacheKey = "${name.lowercase(Locale.US)}_${String.format(Locale.US, "%.4f,%.4f", point.lat, point.lng)}"
        photoCache[cacheKey]?.let { return@withContext it }

        val urls = LinkedHashSet<String>()
        if (directImageUrl.startsWith("https://")) {
            urls.add(directImageUrl)
        }
        if (commonsTag.startsWith("File:", ignoreCase = true)) {
            val filePart = commonsTag.substringAfter(":").trim().replace(' ', '_')
            if (filePart.isNotEmpty()) {
                urls.add("https://commons.wikimedia.org/wiki/Special:FilePath/${URLEncoder.encode(filePart, "UTF-8")}?width=480")
            }
        }
        if (wikipediaTag.isNotBlank() && urls.size < 3) {
            val lang = wikipediaTag.substringBefore(":", "en").ifBlank { "en" }
            val title = wikipediaTag.substringAfter(":", wikipediaTag).trim().replace(' ', '_')
            val wikiUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(title, "UTF-8")}"
            executeGet(wikiUrl)?.let { body ->
                runCatching {
                    val thumb = JSONObject(body).optJSONObject("thumbnail")?.optString("source").orEmpty()
                    if (thumb.startsWith("https://")) urls.add(thumb)
                }
            }
        }

        // Wikimedia Commons Geosearch (real photographs taken near these exact coordinates!)
        if (urls.size < 3) {
            val geoUrl = String.format(
                Locale.US,
                "https://commons.wikimedia.org/w/api.php?action=query&generator=geosearch&ggscoord=%.5f%%7C%.5f&ggsradius=650&ggslimit=6&ggsnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=480&format=json",
                point.lat,
                point.lng,
            )
            executeGet(geoUrl)?.let { body ->
                extractWikimediaThumbUrls(body).forEach { if (urls.size < 4) urls.add(it) }
            }
        }

        // Wikimedia Commons Title Search by real place name (when distinct from generic label)
        val cleanName = name.trim()
        if (urls.size < 3 && cleanName.length >= 4 && !cleanName.equals(categoryHint, ignoreCase = true)) {
            val q = URLEncoder.encode("$cleanName filetype:bitmap", "UTF-8")
            val searchUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$q&gsrnamespace=6&gsrlimit=4&prop=imageinfo&iiprop=url&iiurlwidth=480&format=json"
            executeGet(searchUrl)?.let { body ->
                extractWikimediaThumbUrls(body).forEach { if (urls.size < 4) urls.add(it) }
            }
        }

        // Always include exact live OSM map preview tile for authentic geographic context
        urls.add(MapsGeoMath.osmTilePreviewUrl(point, zoom = 17))
        val finalList = urls.toList().take(4)
        photoCache[cacheKey] = finalList
        finalList
    }

    private fun extractWikimediaThumbUrls(jsonText: String): List<String> {
        val out = ArrayList<String>()
        runCatching {
            val pages = JSONObject(jsonText).optJSONObject("query")?.optJSONObject("pages") ?: return out
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val thumb = info.optString("thumburl", "").ifBlank { info.optString("url", "") }
                val lower = thumb.lowercase(Locale.US)
                if (thumb.startsWith("https://") &&
                    (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.contains("/thumb/")) &&
                    !lower.contains(".svg") && !lower.contains("flag_of") && !lower.contains("map")
                ) {
                    out.add(thumb)
                }
            }
        }
        return out
    }

    suspend fun fetchLocalOsm3dScene(
        center: GeoPoint,
        radiusMeters: Int = 340,
    ): OsmLocalSceneBundle? = withContext(Dispatchers.IO) {
        val r = radiusMeters.coerceIn(150, 500)
        val query = String.format(
            Locale.US,
            "[out:json][timeout:10];(" +
                "way[\"highway\"~\"^(service|living_street|footway|pedestrian|path|steps|track)$\"](around:%d,%.5f,%.5f);" +
                "way[\"building\"][\"roof:shape\"](around:%d,%.5f,%.5f);" +
                "node[\"natural\"=\"tree\"](around:%d,%.5f,%.5f);" +
                ");out body;>;out skel qt;",
            r, center.lat, center.lng,
            r, center.lat, center.lng,
            r, center.lat, center.lng,
        )
        val encoded = URLEncoder.encode(query, "UTF-8")
        val mirrors = listOf(
            "https://overpass-api.de/api/interpreter?data=$encoded",
            "https://overpass.kumi.systems/api/interpreter?data=$encoded",
        )
        for (url in mirrors) {
            val body = executeGet(url) ?: continue
            val parsed = runCatching { Osm3dSceneEngine.parseOverpassLocalSceneJson(body, center) }.getOrNull()
            if (parsed != null) return@withContext parsed
        }
        null
    }

    suspend fun fetchLocalWeather(point: GeoPoint): LocalWeatherSnapshot? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m",
            point.lat,
            point.lng,
        )
        val body = executeGet(url) ?: return@withContext null
        runCatching { MapsGeoMath.parseOpenMeteoCurrentJson(body) }.getOrNull()
    }

    private fun executeGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
