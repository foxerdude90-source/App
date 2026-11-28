package com.example.core.maps

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class OsmMicroPathway(
    val id: String,
    val kind: String, // "service", "living_street", "footpath", "steps"
    val name: String,
    val points: List<GeoPoint>,
)

data class OsmBuildingRoof(
    val id: String,
    val footprint: List<GeoPoint>,
    val wallHeightMeters: Double,
    val roofHeightMeters: Double,
    val roofShape: String,
    val roofColorHex: String,
)

data class OsmTreeNode(
    val id: String,
    val point: GeoPoint,
    val heightMeters: Double,
    val crownRadiusMeters: Double,
    val isConifer: Boolean,
)

data class OsmLocalSceneBundle(
    val center: GeoPoint,
    val pathwaysCount: Int,
    val roofsCount: Int,
    val treesCount: Int,
    val pathwaysGeoJson: String,
    val roofsGeoJson: String,
    val treesGeoJson: String,
)

/**
 * Pure Geodesic 3D Scene & Colony Micro-Pathways Engine powered by 100% free OpenStreetMap data.
 * Parses Overpass API geometry and mathematically generates:
 *  1. Unfiltered colony service roads, living streets, alleys, footpaths & steps (even without service=* subtypes)
 *  2. Multi-tier 3D pitched/gabled/hipped/pyramidal/dome/skillion roof meshes from OSM `roof:shape` tags
 *  3. Multi-tier 3D tree trunks & conical/broadleaf foliage crowns from OSM `natural=tree` nodes
 */
object Osm3dSceneEngine {

    private const val EARTH_RADIUS_M = 6_378_137.0
    const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

    fun parseOverpassLocalSceneJson(jsonText: String, center: GeoPoint): OsmLocalSceneBundle {
        val root = JSONObject(jsonText)
        val elements = root.optJSONArray("elements") ?: return emptyBundle(center)

        val nodeCoords = HashMap<Long, GeoPoint>(elements.length())
        val trees = ArrayList<OsmTreeNode>()

        // Pass 1: Index all OSM node coordinates + extract real natural=tree nodes
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            if (el.optString("type") != "node") continue
            val id = el.optLong("id", -1L)
            val lat = el.optDouble("lat", Double.NaN)
            val lon = el.optDouble("lon", Double.NaN)
            if (id < 0 || lat.isNaN() || lon.isNaN()) continue

            val pt = GeoPoint(lat, lon)
            nodeCoords[id] = pt

            val tags = el.optJSONObject("tags")
            if (tags != null && tags.optString("natural") == "tree") {
                val h = parseMetersTag(tags.optString("height"), defaultMeters = 7.5).coerceIn(3.0, 28.0)
                val crownDiam = parseMetersTag(tags.optString("diameter_crown"), defaultMeters = (h * 0.62).coerceIn(3.2, 8.0))
                val radius = (crownDiam * 0.5).coerceIn(1.4, 5.5)
                val leafType = tags.optString("leaf_type").lowercase()
                val isConifer = leafType == "needleleaved" ||
                    tags.optString("genus").lowercase() in setOf("pinus", "picea", "abies", "cedrus", "cupressus")
                trees.add(
                    OsmTreeNode(
                        id = "tree_$id",
                        point = pt,
                        heightMeters = h,
                        crownRadiusMeters = radius,
                        isConifer = isConifer,
                    ),
                )
            }
        }

        val pathways = ArrayList<OsmMicroPathway>()
        val roofs = ArrayList<OsmBuildingRoof>()

        // Pass 2: Extract colony micro-pathways and buildings with 3D roof:shape tags
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            if (el.optString("type") != "way") continue
            val tags = el.optJSONObject("tags") ?: continue
            val wayId = el.optLong("id", i.toLong())
            val nodeRefs = el.optJSONArray("nodes") ?: continue
            if (nodeRefs.length() < 2) continue

            val pts = ArrayList<GeoPoint>(nodeRefs.length())
            for (k in 0 until nodeRefs.length()) {
                val pt = nodeCoords[nodeRefs.optLong(k, -1L)]
                if (pt != null) pts.add(pt)
            }
            if (pts.size < 2) continue

            val highway = tags.optString("highway").lowercase()
            if (highway.isNotEmpty()) {
                val kind = when (highway) {
                    "service", "track" -> "service"
                    "living_street", "unclassified", "residential" -> "living_street"
                    "steps" -> "steps"
                    "footway", "pedestrian", "path", "cycleway" -> "footpath"
                    else -> ""
                }
                if (kind.isNotEmpty()) {
                    pathways.add(
                        OsmMicroPathway(
                            id = "way_$wayId",
                            kind = kind,
                            name = tags.optString("name", "").trim(),
                            points = pts,
                        ),
                    )
                }
            }

            val building = tags.optString("building")
            val roofShapeRaw = tags.optString("roof:shape").lowercase().trim()
            if (building.isNotEmpty() && roofShapeRaw.isNotEmpty() && roofShapeRaw != "flat" && pts.size >= 4) {
                val cleanRing = if (pts.first() == pts.last()) pts.dropLast(1) else pts
                if (cleanRing.size >= 3) {
                    val levels = parseMetersTag(tags.optString("building:levels"), defaultMeters = 2.0).coerceIn(1.0, 50.0)
                    val totalHeight = parseMetersTag(tags.optString("height"), defaultMeters = levels * 3.3 + 2.8)
                    val roofHeight = parseMetersTag(
                        tags.optString("roof:height"),
                        defaultMeters = when (roofShapeRaw) {
                            "dome", "onion", "round" -> 4.2
                            "pyramidal" -> 3.8
                            else -> 3.0
                        },
                    ).coerceIn(1.2, max(2.0, totalHeight * 0.55))
                    val wallHeight = (totalHeight - roofHeight).coerceAtLeast(3.0)
                    val roofColor = normalizeOsmColorHex(
                        raw = tags.optString("roof:colour").ifBlank { tags.optString("roof:color") },
                        roofShape = roofShapeRaw,
                    )
                    roofs.add(
                        OsmBuildingRoof(
                            id = "roof_$wayId",
                            footprint = cleanRing,
                            wallHeightMeters = wallHeight,
                            roofHeightMeters = roofHeight,
                            roofShape = roofShapeRaw,
                            roofColorHex = roofColor,
                        ),
                    )
                }
            }
        }

        val cappedPaths = pathways.take(260)
        val cappedRoofs = roofs.take(90)
        val cappedTrees = trees.take(180)

        return OsmLocalSceneBundle(
            center = center,
            pathwaysCount = cappedPaths.size,
            roofsCount = cappedRoofs.size,
            treesCount = cappedTrees.size,
            pathwaysGeoJson = buildMicroPathwaysGeoJson(cappedPaths),
            roofsGeoJson = build3dRoofsGeoJson(cappedRoofs),
            treesGeoJson = build3dTreesGeoJson(cappedTrees),
        )
    }

    fun buildMicroPathwaysGeoJson(pathways: List<OsmMicroPathway>): String {
        val features = JSONArray()
        pathways.forEach { path ->
            if (path.points.size < 2) return@forEach
            val coords = JSONArray()
            path.points.forEach { pt ->
                coords.put(JSONArray().put(pt.lng).put(pt.lat))
            }
            val props = JSONObject()
                .put("kind", path.kind)
                .put("name", path.name)
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("properties", props)
                    .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords)),
            )
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    /**
     * Procedurally generates stepped 3D roof extrusion tiers (gabled, hipped, pyramidal, dome,
     * onion, skillion, mansard) using local tangent-plane metric projection so MapLibre GL Native
     * renders genuine sloped/pitched roofs above OSM buildings.
     */
    fun build3dRoofsGeoJson(roofs: List<OsmBuildingRoof>): String {
        val features = JSONArray()
        val tiers = 5

        roofs.forEach { roof ->
            val ring = roof.footprint
            if (ring.size < 3) return@forEach

            // 1. Project polygon vertices to local East/North meters centered at polygon centroid
            val cxLat = ring.map { it.lat }.average()
            val cxLng = ring.map { it.lng }.average()
            val cosLat = cos(Math.toRadians(cxLat)).coerceAtLeast(0.05)

            val localPts = ring.map { pt ->
                val x = Math.toRadians(pt.lng - cxLng) * EARTH_RADIUS_M * cosLat
                val y = Math.toRadians(pt.lat - cxLat) * EARTH_RADIUS_M
                x to y
            }

            // 2. Principal ridge axis (unit vector along longest polygon edge)
            var bestLen = 0.0
            var ux = 1.0
            var uy = 0.0
            for (i in localPts.indices) {
                val a = localPts[i]
                val b = localPts[(i + 1) % localPts.size]
                val dx = b.first - a.first
                val dy = b.second - a.second
                val len = hypot(dx, dy)
                if (len > bestLen) {
                    bestLen = len
                    ux = dx / len
                    uy = dy / len
                }
            }
            val vx = -uy
            val vy = ux

            // Compute min/max along perpendicular axis for skillion (shed) roofs
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            localPts.forEach { (x, y) ->
                val projV = x * vx + y * vy
                if (projV < minV) minV = projV
                if (projV > maxV) maxV = projV
            }

            val stepH = roof.roofHeightMeters / tiers.toDouble()
            for (t in 0 until tiers) {
                val frac = t.toDouble() / tiers.toDouble()
                val baseH = roof.wallHeightMeters + t * stepH
                val topH = baseH + stepH + 0.06 // Tiny overlap eliminates vertical seam flicker

                val transformed = localPts.map { (x, y) ->
                    val pu = x * ux + y * uy
                    val pv = x * vx + y * vy
                    val (nu, nv) = when (roof.roofShape) {
                        "dome", "round", "onion" -> {
                            val rScale = sqrt(max(0.05, 1.0 - frac * frac))
                            (pu * rScale) to (pv * rScale)
                        }
                        "pyramidal" -> {
                            val rScale = (1.0 - frac * 0.88).coerceAtLeast(0.10)
                            (pu * rScale) to (pv * rScale)
                        }
                        "gabled", "gambrel", "saltbox" -> {
                            val vScale = (1.0 - frac * 0.88).coerceAtLeast(0.10)
                            pu to (pv * vScale)
                        }
                        "skillion" -> {
                            val targetV = maxV
                            val lerpV = pv + (targetV - pv) * (frac * 0.85)
                            pu to lerpV
                        }
                        else -> { // hipped, half-hipped, mansard
                            val uScale = (1.0 - frac * 0.42).coerceAtLeast(0.35)
                            val vScale = (1.0 - frac * 0.88).coerceAtLeast(0.10)
                            (pu * uScale) to (pv * vScale)
                        }
                    }
                    val rx = nu * ux + nv * vx
                    val ry = nu * uy + nv * vy
                    val lat = cxLat + Math.toDegrees(ry / EARTH_RADIUS_M)
                    val lng = cxLng + Math.toDegrees(rx / (EARTH_RADIUS_M * cosLat))
                    lat to lng
                }

                val ringCoords = JSONArray()
                transformed.forEach { (lat, lng) -> ringCoords.put(JSONArray().put(lng).put(lat)) }
                ringCoords.put(JSONArray().put(transformed.first().second).put(transformed.first().first))

                val shadeColor = lightenHexColor(roof.roofColorHex, frac * 0.24)
                val props = JSONObject()
                    .put("base", baseH)
                    .put("height", topH)
                    .put("color", shadeColor)
                    .put("shape", roof.roofShape)

                features.put(
                    JSONObject()
                        .put("type", "Feature")
                        .put("properties", props)
                        .put(
                            "geometry",
                            JSONObject()
                                .put("type", "Polygon")
                                .put("coordinates", JSONArray().put(ringCoords)),
                        ),
                )
            }
        }

        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    /**
     * Procedurally generates multi-tier 3D tree extrusions (cylindrical bark trunk + layered
     * conical pine or spherical broadleaf canopy) from real OSM `natural=tree` nodes.
     */
    fun build3dTreesGeoJson(trees: List<OsmTreeNode>): String {
        val features = JSONArray()
        trees.forEach { tree ->
            val h = tree.heightMeters
            val r = tree.crownRadiusMeters
            val trunkR = (r * 0.16).coerceIn(0.28, 0.65)
            val trunkTop = (h * 0.42).coerceAtLeast(1.6)

            // 1. Bark Trunk Cylinder
            features.put(
                createCircularExtrusionFeature(
                    center = tree.point,
                    radiusMeters = trunkR,
                    baseMeters = 0.0,
                    heightMeters = trunkTop,
                    colorHex = "#6B4423",
                    sides = 7,
                ),
            )

            // 2. Layered 3D Foliage Canopy
            if (tree.isConifer) {
                val tiers = listOf(
                    Triple(0.30, 0.54, 1.00 to "#14532D"),
                    Triple(0.50, 0.72, 0.78 to "#166534"),
                    Triple(0.68, 0.87, 0.54 to "#15803D"),
                    Triple(0.84, 1.00, 0.28 to "#22C55E"),
                )
                tiers.forEach { (bFrac, tFrac, radColor) ->
                    features.put(
                        createCircularExtrusionFeature(
                            center = tree.point,
                            radiusMeters = r * radColor.first,
                            baseMeters = h * bFrac,
                            heightMeters = h * tFrac,
                            colorHex = radColor.second,
                            sides = 8,
                        ),
                    )
                }
            } else {
                val tiers = listOf(
                    Triple(0.36, 0.62, 0.84 to "#15803D"),
                    Triple(0.56, 0.84, 1.00 to "#16A34A"),
                    Triple(0.78, 1.00, 0.66 to "#4ADE80"),
                )
                tiers.forEach { (bFrac, tFrac, radColor) ->
                    features.put(
                        createCircularExtrusionFeature(
                            center = tree.point,
                            radiusMeters = r * radColor.first,
                            baseMeters = h * bFrac,
                            heightMeters = h * tFrac,
                            colorHex = radColor.second,
                            sides = 8,
                        ),
                    )
                }
            }
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private fun createCircularExtrusionFeature(
        center: GeoPoint,
        radiusMeters: Double,
        baseMeters: Double,
        heightMeters: Double,
        colorHex: String,
        sides: Int,
    ): JSONObject {
        val cosLat = cos(Math.toRadians(center.lat)).coerceAtLeast(0.05)
        val ring = JSONArray()
        var firstLat = 0.0
        var firstLng = 0.0
        for (i in 0 until sides) {
            val angle = (2.0 * PI * i) / sides.toDouble()
            val dx = radiusMeters * cos(angle)
            val dy = radiusMeters * sin(angle)
            val lat = center.lat + Math.toDegrees(dy / EARTH_RADIUS_M)
            val lng = center.lng + Math.toDegrees(dx / (EARTH_RADIUS_M * cosLat))
            if (i == 0) {
                firstLat = lat
                firstLng = lng
            }
            ring.put(JSONArray().put(lng).put(lat))
        }
        ring.put(JSONArray().put(firstLng).put(firstLat))

        val props = JSONObject()
            .put("base", baseMeters)
            .put("height", heightMeters)
            .put("color", colorHex)

        return JSONObject()
            .put("type", "Feature")
            .put("properties", props)
            .put(
                "geometry",
                JSONObject()
                    .put("type", "Polygon")
                    .put("coordinates", JSONArray().put(ring)),
            )
    }

    private fun parseMetersTag(raw: String, defaultMeters: Double): Double {
        if (raw.isBlank()) return defaultMeters
        val cleaned = raw.lowercase().replace(",", ".").trim()
        val numStr = Regex("""[0-9]+(\.[0-9]+)?""").find(cleaned)?.value ?: return defaultMeters
        val v = numStr.toDoubleOrNull() ?: return defaultMeters
        return if (cleaned.contains("ft") || cleaned.contains("'")) v * 0.3048 else v
    }

    private fun normalizeOsmColorHex(raw: String, roofShape: String): String {
        val c = raw.trim().lowercase()
        if (Regex("""^#[0-9a-f]{6}$""").matches(c)) return c.uppercase()
        return when (c) {
            "red", "terracotta", "brick", "maroon" -> "#B45309"
            "brown", "saddlebrown", "chocolate" -> "#7C2D12"
            "grey", "gray", "slate", "darkgray", "darkgrey" -> "#475569"
            "black", "charcoal" -> "#1E293B"
            "blue", "navy", "steelblue" -> "#1D4ED8"
            "green", "darkgreen", "copper" -> "#0F766E"
            "white", "silver", "lightgrey", "lightgray" -> "#94A3B8"
            "yellow", "gold", "beige", "tan" -> "#B45309"
            else -> when (roofShape) {
                "dome", "onion" -> "#0F766E"
                "pyramidal" -> "#9A3412"
                else -> "#A16207"
            }
        }
    }

    private fun lightenHexColor(hex: String, amount: Double): String {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return hex
        val r = clean.substring(0, 2).toIntOrNull(16) ?: return hex
        val g = clean.substring(2, 4).toIntOrNull(16) ?: return hex
        val b = clean.substring(4, 6).toIntOrNull(16) ?: return hex
        val f = amount.coerceIn(0.0, 0.5)
        val nr = min(255, (r + (255 - r) * f).toInt())
        val ng = min(255, (g + (255 - g) * f).toInt())
        val nb = min(255, (b + (255 - b) * f).toInt())
        return String.format("#%02X%02X%02X", nr, ng, nb)
    }

    private fun emptyBundle(center: GeoPoint) = OsmLocalSceneBundle(
        center = center,
        pathwaysCount = 0,
        roofsCount = 0,
        treesCount = 0,
        pathwaysGeoJson = EMPTY_FEATURE_COLLECTION,
        roofsGeoJson = EMPTY_FEATURE_COLLECTION,
        treesGeoJson = EMPTY_FEATURE_COLLECTION,
    )
}
