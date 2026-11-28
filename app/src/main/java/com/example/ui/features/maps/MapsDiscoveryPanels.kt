package com.example.ui.features.maps

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CarRental
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalConvenienceStore
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NightShelter
import androidx.compose.material.icons.outlined.Nightlife
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.maps.GeoPoint
import com.example.core.maps.MapsGeoMath
import com.example.core.maps.NearbyPoi
import com.example.core.maps.PlaceResult
import com.example.core.maps.PoiCategory
import com.example.core.maps.PoiCategoryGroup
import com.example.core.maps.SavedRouteCategory
import com.example.core.maps.SmartPlaceHistoryCodec
import com.example.core.maps.SmartSavedPlace
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
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

internal val SkyPillBg = Color(0xFFDFF4FB)
internal val SkyPillText = Color(0xFF004F61)
internal val SkyPillActiveBg = Color(0xFF00677F)

internal fun poiCategoryMaterialIcon(category: PoiCategory): ImageVector = when (category) {
    PoiCategory.RESTAURANT -> Icons.Outlined.Restaurant
    PoiCategory.CAFE -> Icons.Outlined.LocalCafe
    PoiCategory.BAR -> Icons.Outlined.LocalBar
    PoiCategory.TAKEOUT -> Icons.Outlined.TakeoutDining
    PoiCategory.DELIVERY -> Icons.Outlined.DeliveryDining
    PoiCategory.BAKERY -> Icons.Outlined.BakeryDining
    PoiCategory.ATM -> Icons.Outlined.LocalAtm
    PoiCategory.FUEL -> Icons.Outlined.LocalGasStation
    PoiCategory.EV_CHARGER -> Icons.Outlined.EvStation
    PoiCategory.HOSPITAL -> Icons.Outlined.LocalHospital
    PoiCategory.CHEMIST -> Icons.Outlined.LocalPharmacy
    PoiCategory.HOTEL -> Icons.Outlined.Hotel
    PoiCategory.BUS_STOP -> Icons.Outlined.DirectionsBus
    PoiCategory.METRO_STATION -> Icons.Outlined.DirectionsSubway
    PoiCategory.PARKING -> Icons.Outlined.LocalParking
    PoiCategory.PARK -> Icons.Outlined.Park
    PoiCategory.GYM -> Icons.Outlined.FitnessCenter
    PoiCategory.LIVE_MUSIC -> Icons.Outlined.MusicNote
    PoiCategory.FILMS -> Icons.Outlined.Movie
    PoiCategory.ART -> Icons.Outlined.Palette
    PoiCategory.MUSEUM -> Icons.Outlined.Museum
    PoiCategory.ATTRACTION -> Icons.Outlined.Attractions
    PoiCategory.LIBRARY -> Icons.Outlined.LocalLibrary
    PoiCategory.NIGHTLIFE -> Icons.Outlined.Nightlife
    PoiCategory.GROCERIES -> Icons.Outlined.ShoppingCart
    PoiCategory.SHOPPING_CENTRE -> Icons.Outlined.Storefront
    PoiCategory.BEAUTY_SUPPLIES -> Icons.Outlined.FaceRetouchingNatural
    PoiCategory.ELECTRONICS -> Icons.Outlined.Devices
    PoiCategory.CAR_DEALERS -> Icons.Outlined.DirectionsCar
    PoiCategory.SPORTING_GOODS -> Icons.Outlined.SportsSoccer
    PoiCategory.HOME_GARDEN -> Icons.Outlined.HomeWork
    PoiCategory.CONVENIENCE -> Icons.Outlined.LocalConvenienceStore
    PoiCategory.CLOTHING -> Icons.Outlined.Checkroom
    PoiCategory.POST -> Icons.Outlined.MailOutline
    PoiCategory.BEAUTY_SALON -> Icons.Outlined.ContentCut
    PoiCategory.DRY_CLEANING -> Icons.Outlined.LocalLaundryService
    PoiCategory.CAR_HIRE -> Icons.Outlined.CarRental
    PoiCategory.CAR_REPAIR -> Icons.Outlined.CarRepair
    PoiCategory.CAR_WASH -> Icons.Outlined.LocalCarWash
    PoiCategory.FOOD_SHELTER -> Icons.Outlined.VolunteerActivism
    PoiCategory.NIGHT_SHELTER -> Icons.Outlined.NightShelter
}

internal fun estimateDriveMinutesText(from: GeoPoint, to: GeoPoint): String {
    val meters = MapsGeoMath.haversineMeters(from, to)
    val mins = (meters / 420.0).roundToInt().coerceAtLeast(1)
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min"
}

/**
 * Full-screen Material 3 "More Categories" Directory matching modern navigation finder chips:
 * Section tabs (`Food & drink`, `Things to do`, `Shopping`, `Services`, `Transit`) +
 * soft sky-cyan tonal pill buttons for all 41 real OpenStreetMap Overpass categories.
 */
@Composable
internal fun MoreCategoriesOverlay(
    visible: Boolean,
    activeCategory: PoiCategory?,
    onSelectCategory: (PoiCategory) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        var selectedTab by remember { mutableStateOf<PoiCategoryGroup?>(null) }
        val groupsToShow = remember(selectedTab) {
            if (selectedTab == null) PoiCategoryGroup.entries else listOf(selectedTab!!)
        }

        Surface(
            color = StudioWhite,
            modifier = Modifier
                .fillMaxSize()
                .testTag("maps_more_categories_sheet"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("maps_more_categories_close"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to map",
                            tint = InkObsidian,
                        )
                    }
                    Text(
                        text = "More Categories",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                        ),
                        color = InkObsidian,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(color = StudioBorder)

                // Group Tab Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { selectedTab = null },
                        color = if (selectedTab == null) Color(0xFFE2E8F0) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == null) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = InkObsidian,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    PoiCategoryGroup.entries.forEach { group ->
                        val active = selectedTab == group
                        Surface(
                            onClick = { selectedTab = group },
                            color = if (active) Color(0xFFE2E8F0) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("maps_cat_tab_${group.name.lowercase(Locale.US)}"),
                        ) {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (active) InkObsidian else InkSlate,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = StudioBorder.copy(alpha = 0.7f))

                // Scrollable Sectioned 2-Column Tonal Pill Grid
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    groupsToShow.forEach { group ->
                        val cats = PoiCategory.entries.filter { it.group == group }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                ),
                                color = InkObsidian,
                            )

                            cats.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    pair.forEach { cat ->
                                        val isSelected = activeCategory == cat
                                        Surface(
                                            onClick = { onSelectCategory(cat) },
                                            color = if (isSelected) SkyPillActiveBg else SkyPillBg,
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("maps_more_cat_${cat.key}"),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                            ) {
                                                Icon(
                                                    imageVector = poiCategoryMaterialIcon(cat),
                                                    contentDescription = null,
                                                    tint = if (isSelected) StudioWhite else SkyPillText,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                                Text(
                                                    text = cat.label,
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 14.sp,
                                                    ),
                                                    color = if (isSelected) StudioWhite else SkyPillText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Rich Expandable Category Results Popup Sheet displaying live OpenStreetMap places
 * with live filter chips, auto-fetched Wikimedia/Wikipedia/OSM horizontal photo carousels,
 * double-tap-to-favorite gesture, and sky-cyan action pills (`Directions`, `3D Nav`, `Call`, `Website`, `Save`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CategoryResultsPopupSheet(
    state: MapsUiState,
    category: PoiCategory,
    onSelectPoi: (NearbyPoi) -> Unit,
    onRouteToPoi: (NearbyPoi, Boolean) -> Unit,
    onToggleFavoritePoi: (NearbyPoi) -> Unit,
    onToggleSortByName: () -> Unit,
    onToggleOpenNow: () -> Unit,
    onToggleHasPhone: () -> Unit,
    onToggleHasWebsite: () -> Unit,
    onToggleWheelchair: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember(category) { mutableStateOf(false) }

    val nowCal = remember { Calendar.getInstance() }
    val curHour = nowCal.get(Calendar.HOUR_OF_DAY)
    val curMinute = nowCal.get(Calendar.MINUTE)

    val filteredPois = remember(
        state.nearbyPois,
        state.poiFilterOpenNow,
        state.poiFilterHasPhone,
        state.poiFilterHasWebsite,
        state.poiFilterWheelchair,
        state.poiSortByName,
    ) {
        val base = state.nearbyPois.filter { poi ->
            (!state.poiFilterOpenNow || MapsGeoMath.isLikelyOpenNow(poi.openingHours, curHour, curMinute) == true) &&
                (!state.poiFilterHasPhone || poi.phone.isNotBlank()) &&
                (!state.poiFilterHasWebsite || poi.website.isNotBlank()) &&
                (!state.poiFilterWheelchair || poi.wheelchair)
        }
        if (state.poiSortByName) base.sortedBy { it.name.lowercase(Locale.US) } else base.sortedBy { it.distanceMeters }
    }

    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp, max = if (expanded) 520.dp else 330.dp)
            .testTag("maps_poi_results_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Drag Handle + Category Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCBD5E1)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        color = SkyPillBg,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = poiCategoryMaterialIcon(category),
                                contentDescription = null,
                                tint = SkyPillText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = InkObsidian,
                        )
                        Text(
                            text = if (state.isLoadingPois) {
                                "Discovering live OpenStreetMap places…"
                            } else {
                                "${filteredPois.size} live places • Double-tap any card to Favorite"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSlate,
                        )
                    }
                }
                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            contentDescription = if (expanded) "Collapse list" else "Expand list",
                            tint = InkSlate,
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("maps_poi_results_close"),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close category results", tint = InkSlate)
                    }
                }
            }

            // Live Filter Pills Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPillChip(
                    label = if (state.poiSortByName) "Sort: A–Z" else "Sort: Distance",
                    active = state.poiSortByName,
                    onClick = onToggleSortByName,
                )
                FilterPillChip(
                    label = "Open now",
                    active = state.poiFilterOpenNow,
                    onClick = onToggleOpenNow,
                )
                FilterPillChip(
                    label = "Has phone",
                    active = state.poiFilterHasPhone,
                    onClick = onToggleHasPhone,
                )
                FilterPillChip(
                    label = "Website",
                    active = state.poiFilterHasWebsite,
                    onClick = onToggleHasWebsite,
                )
                FilterPillChip(
                    label = "Wheelchair",
                    active = state.poiFilterWheelchair,
                    icon = Icons.Outlined.Accessible,
                    onClick = onToggleWheelchair,
                )
            }

            HorizontalDivider(color = StudioBorder.copy(alpha = 0.7f))

            if (state.isLoadingPois) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(color = SkyPillActiveBg, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        Text(
                            text = "Fetching real ${category.label} & photos…",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    }
                }
            } else if (filteredPois.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.nearbyPois.isNotEmpty()) {
                            "No places match the active filters — tap a filter above to broaden."
                        } else {
                            "No ${category.label} mapped nearby. Pan the map and tap \"Search this area\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSlate,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredPois, key = { it.id }) { poi ->
                        val isFav = state.smartPlaces.any {
                            it.listType == SavedRouteCategory.FAVORITE &&
                                MapsGeoMath.haversineMeters(it.point, poi.point) < 65.0
                        }
                        val openStatus = MapsGeoMath.isLikelyOpenNow(poi.openingHours, curHour, curMinute)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onSelectPoi(poi) },
                                    onDoubleClick = { onToggleFavoritePoi(poi) },
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Title + Distance + Favorite Heart
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = poi.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 17.sp,
                                        ),
                                        color = InkObsidian,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = poi.cuisine.takeIf { it.isNotBlank() }
                                                ?.replace(';', ',')
                                                ?.replace('_', ' ')
                                                ?.replaceFirstChar { it.uppercase() }
                                                ?: poi.category.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkSlate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text("•", color = InkSlate, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = MapsGeoMath.formatDistance(poi.distanceMeters, state.useImperialUnits),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = SkyPillActiveBg,
                                        )
                                        if (openStatus != null) {
                                            Text("•", color = InkSlate, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = if (openStatus) "Open" else "Closed",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (openStatus) Color(0xFF15803D) else ClipperRose,
                                            )
                                        }
                                    }
                                    if (poi.address.isNotBlank() || poi.openingHours.isNotBlank()) {
                                        Text(
                                            text = listOfNotNull(
                                                poi.address.takeIf { it.isNotBlank() },
                                                poi.openingHours.takeIf { it.isNotBlank() },
                                            ).joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkSlate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleFavoritePoi(poi) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Save to Favorites",
                                        tint = if (isFav) ClipperRose else InkSlate,
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                            }

                            // Horizontal Auto-Fetched Real Photo Carousel (Wikimedia / Wikipedia / Live OSM Map Tile)
                            val displayPhotos = remember(poi.photoUrls, poi.point) {
                                poi.photoUrls.ifEmpty { listOf(MapsGeoMath.osmTilePreviewUrl(poi.point, 17)) }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(displayPhotos, key = { "${poi.id}_$it" }) { url ->
                                    Box(
                                        modifier = Modifier
                                            .width(126.dp)
                                            .height(98.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFE2E8F0)),
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "${poi.name} photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (url.contains("tile.openstreetmap.org")) {
                                            Surface(
                                                color = Color(0xAA0F172A),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(6.dp),
                                            ) {
                                                Text(
                                                    text = "Map View",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                    color = StudioWhite,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Sky-Cyan Action Pills Row (Directions, 3D Nav, Call, Website, Save)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SkyActionPill(
                                    label = "Directions",
                                    icon = Icons.Outlined.Directions,
                                    filled = true,
                                    onClick = { onRouteToPoi(poi, false) },
                                )
                                SkyActionPill(
                                    label = "Start 3D",
                                    icon = Icons.Outlined.Navigation,
                                    filled = false,
                                    onClick = { onRouteToPoi(poi, true) },
                                )
                                if (poi.phone.isNotBlank()) {
                                    SkyActionPill(
                                        label = "Call",
                                        icon = Icons.Outlined.Call,
                                        filled = false,
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${poi.phone}")),
                                                )
                                            }
                                        },
                                    )
                                }
                                if (poi.website.isNotBlank()) {
                                    SkyActionPill(
                                        label = "Website",
                                        icon = Icons.Outlined.Language,
                                        filled = false,
                                        onClick = {
                                            runCatching {
                                                val normalized = if (poi.website.startsWith("http")) poi.website else "https://${poi.website}"
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                                            }
                                        },
                                    )
                                }
                                SkyActionPill(
                                    label = if (isFav) "Saved" else "Save",
                                    icon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    filled = false,
                                    onClick = { onToggleFavoritePoi(poi) },
                                )
                            }
                        }
                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}

/**
 * Left Sidebar Slide-Out Inbox for Saved Routes, Favorites, Auto-Saved 1–4h Stays,
 * Auto-Learned Frequent Locations, Want-to-Go, and One-Tap Home / Work exact routing.
 */
@Composable
internal fun MapsLeftSidebarDrawer(
    state: MapsUiState,
    onRouteToPlace: (SmartSavedPlace) -> Unit,
    onDeleteSmartPlace: (String) -> Unit,
    onSetHomeCurrent: () -> Unit,
    onClearHome: () -> Unit,
    onSetWorkCurrent: () -> Unit,
    onClearWork: () -> Unit,
    onToggleDoubleTapSave: () -> Unit,
    onClose: () -> Unit,
) {
    val origin = state.liveLocation?.point ?: state.mapCenter
    var selectedFilter by remember { mutableStateOf<SavedRouteCategory?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dark Scrim
        AnimatedVisibility(
            visible = state.showLeftSidebar,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable(onClick = onClose),
            )
        }

        // Slide-In Left Sidebar Inbox Panel
        AnimatedVisibility(
            visible = state.showLeftSidebar,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            val filteredItems = remember(state.smartPlaces, selectedFilter) {
                when (selectedFilter) {
                    null -> state.smartPlaces
                    else -> state.smartPlaces.filter { it.listType == selectedFilter }
                }
            }

            Surface(
                color = StudioWhite,
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 360.dp)
                    .testTag("maps_left_sidebar"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saved & Auto Routes",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
                                color = InkObsidian,
                            )
                            Text(
                                text = "Tap any inbox card for instant route",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSlate,
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("maps_sidebar_close"),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close saved routes sidebar", tint = InkSlate)
                        }
                    }

                    // Double-Tap Map to Save Favorite Toggle
                    Surface(
                        color = if (state.doubleTapToSaveEnabled) SkyPillBg else StudioSnow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (state.doubleTapToSaveEnabled) SkyPillActiveBg else StudioBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TouchApp,
                                contentDescription = null,
                                tint = if (state.doubleTapToSaveEnabled) SkyPillActiveBg else InkSlate,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Double-tap map to Favorite",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = InkObsidian,
                                )
                                Text(
                                    text = "Also auto-saves 1–4h GPS stays & frequent stops",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSlate,
                                )
                            }
                            Switch(
                                checked = state.doubleTapToSaveEnabled,
                                onCheckedChange = { onToggleDoubleTapSave() },
                                colors = SwitchDefaults.colors(checkedThumbColor = StudioWhite, checkedTrackColor = SkyPillActiveBg),
                                modifier = Modifier.testTag("maps_double_tap_save_toggle"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Home & Work Quick-Route Tiles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeWorkTile(
                            title = "Home",
                            icon = Icons.Outlined.Home,
                            saved = state.homePlace,
                            origin = origin,
                            onRoute = { state.homePlace?.let(onRouteToPlace) ?: onSetHomeCurrent() },
                            onClear = onClearHome,
                            modifier = Modifier.weight(1f),
                        )
                        HomeWorkTile(
                            title = "Work",
                            icon = Icons.Outlined.Work,
                            saved = state.workPlace,
                            origin = origin,
                            onRoute = { state.workPlace?.let(onRouteToPlace) ?: onSetWorkCurrent() },
                            onClear = onClearWork,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Inbox Filter Tabs
                    val inboxTabs = listOf(
                        null to "All",
                        SavedRouteCategory.FAVORITE to "Favorites",
                        SavedRouteCategory.AUTO_DWELL to "Stayed 1–4h",
                        SavedRouteCategory.FREQUENT to "Frequent",
                        SavedRouteCategory.WANT_TO_GO to "Want to go",
                        SavedRouteCategory.RECENT to "Recent",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        inboxTabs.forEach { (cat, label) ->
                            val active = selectedFilter == cat
                            val count = if (cat == null) state.smartPlaces.size else state.smartPlaces.count { it.listType == cat }
                            Surface(
                                onClick = { selectedFilter = cat },
                                color = if (active) SkyPillActiveBg else StudioSnow,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (active) SkyPillActiveBg else StudioBorder),
                            ) {
                                Text(
                                    text = if (count > 0) "$label ($count)" else label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (active) StudioWhite else InkObsidian,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = StudioBorder.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = SkyPillActiveBg,
                                    modifier = Modifier.size(34.dp),
                                )
                                Text(
                                    text = "Your Smart Route Inbox is ready",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = InkObsidian,
                                )
                                Text(
                                    text = "Double-tap any place card to Favorite, visit places twice for Frequent auto-routes, or stay 1–4h with GPS on for automatic saving.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                val distMeters = MapsGeoMath.haversineMeters(origin, item.point)
                                val badgeText = when {
                                    item.listType == SavedRouteCategory.FAVORITE -> "Favorite"
                                    item.listType == SavedRouteCategory.AUTO_DWELL && item.dwellMinutes > 0 ->
                                        SmartPlaceHistoryCodec.formatDwellBadge(item.dwellMinutes)
                                    item.listType == SavedRouteCategory.FREQUENT -> "Opened ${item.visitCount}×"
                                    item.listType == SavedRouteCategory.WANT_TO_GO -> "Want to go"
                                    else -> SmartPlaceHistoryCodec.formatRelativeTime(item.lastVisitedMs)
                                }
                                val badgeColor = when (item.listType) {
                                    SavedRouteCategory.FAVORITE -> ClipperRose
                                    SavedRouteCategory.AUTO_DWELL -> ClipperEmerald
                                    SavedRouteCategory.FREQUENT -> ClipperIndigo
                                    SavedRouteCategory.WANT_TO_GO -> ClipperAmber
                                    else -> InkSlate
                                }
                                val thumbUrl = item.photoUrl.ifBlank { MapsGeoMath.osmTilePreviewUrl(item.point, 17) }

                                Surface(
                                    onClick = { onRouteToPlace(item) },
                                    color = StudioSnow,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        AsyncImage(
                                            model = thumbUrl,
                                            contentDescription = "${item.name} thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFE2E8F0)),
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = InkObsidian,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Surface(
                                                    color = badgeColor.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(6.dp),
                                                ) {
                                                    Text(
                                                        text = badgeText,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = badgeColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                                Text(
                                                    text = MapsGeoMath.formatDistance(distMeters, state.useImperialUnits),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                                    color = InkSlate,
                                                )
                                            }
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Surface(
                                                onClick = { onRouteToPlace(item) },
                                                color = SkyPillBg,
                                                shape = RoundedCornerShape(10.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Directions,
                                                        contentDescription = "Route to ${item.name}",
                                                        tint = SkyPillText,
                                                        modifier = Modifier.size(15.dp),
                                                    )
                                                    Text(
                                                        text = "Route",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = SkyPillText,
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onDeleteSmartPlace(item.id) },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = "Remove saved place",
                                                    tint = InkSlate,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search-Focus Quick Overlay inspired by Google Maps Search Sheet:
 * Shows Home (with live ETA), Work, and Transit Near You strip + Recent & Frequent saved stops!
 */
@Composable
internal fun SearchFocusOverlay(
    state: MapsUiState,
    onRouteToSmartPlace: (SmartSavedPlace) -> Unit,
    onSetHomeCurrent: () -> Unit,
    onSetWorkCurrent: () -> Unit,
    onSelectTransitNearYou: () -> Unit,
    onClose: () -> Unit,
) {
    val origin = state.liveLocation?.point ?: state.mapCenter
    val recentAndFrequent = remember(state.smartPlaces) {
        state.smartPlaces.sortedByDescending { it.lastVisitedMs }.take(8)
    }

    Surface(
        color = StudioWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .testTag("maps_search_overlay"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Home | Work | Transit Near You Quick Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchQuickAnchorItem(
                    icon = Icons.Outlined.Home,
                    title = "Home",
                    subtitle = state.homePlace?.let { estimateDriveMinutesText(origin, it.point) } ?: "Set location",
                    subtitleColor = if (state.homePlace != null) Color(0xFFB91C1C) else InkSlate,
                    onClick = { state.homePlace?.let(onRouteToSmartPlace) ?: onSetHomeCurrent() },
                )
                Box(modifier = Modifier.width(1.dp).height(34.dp).background(StudioBorder))
                SearchQuickAnchorItem(
                    icon = Icons.Outlined.Work,
                    title = "Work",
                    subtitle = state.workPlace?.let { estimateDriveMinutesText(origin, it.point) } ?: "Set location",
                    subtitleColor = if (state.workPlace != null) SkyPillActiveBg else InkSlate,
                    onClick = { state.workPlace?.let(onRouteToSmartPlace) ?: onSetWorkCurrent() },
                )
                Box(modifier = Modifier.width(1.dp).height(34.dp).background(StudioBorder))
                SearchQuickAnchorItem(
                    icon = Icons.Outlined.DirectionsTransit,
                    title = "Transit",
                    subtitle = "Near you",
                    subtitleColor = InkSlate,
                    onClick = onSelectTransitNearYou,
                )
            }

            HorizontalDivider(color = StudioBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent & Frequent",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = InkObsidian,
                )
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = SkyPillActiveBg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (recentAndFrequent.isEmpty()) {
                Text(
                    text = "Search any street, coffee shop, ATM, or tap a place on the map — your recent & frequent stops will appear here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(recentAndFrequent, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRouteToSmartPlace(item) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                color = SkyPillBg,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (item.listType) {
                                            SavedRouteCategory.FAVORITE -> Icons.Filled.Favorite
                                            SavedRouteCategory.FREQUENT -> Icons.Outlined.TrendingUp
                                            SavedRouteCategory.AUTO_DWELL -> Icons.Outlined.Schedule
                                            else -> Icons.Outlined.History
                                        },
                                        contentDescription = null,
                                        tint = if (item.listType == SavedRouteCategory.FAVORITE) ClipperRose else SkyPillText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkObsidian,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.subtitle.ifBlank { item.listType.label },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = MapsGeoMath.formatDistance(
                                    MapsGeoMath.haversineMeters(origin, item.point),
                                    state.useImperialUnits,
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = InkSlate,
                            )
                        }
                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWorkTile(
    title: String,
    icon: ImageVector,
    saved: SmartSavedPlace?,
    origin: GeoPoint,
    onRoute: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onRoute,
        color = SkyPillBg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = StudioWhite,
                shape = CircleShape,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = SkyPillText, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = InkObsidian,
                )
                Text(
                    text = saved?.let { estimateDriveMinutesText(origin, it.point) } ?: "Tap to set",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (saved != null) SkyPillActiveBg else InkSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (saved != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear $title", tint = InkSlate, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchQuickAnchorItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = SkyPillBg,
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = SkyPillText, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = InkObsidian,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = subtitleColor,
            )
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    active: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (active) SkyPillActiveBg else StudioWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (active) SkyPillActiveBg else StudioBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) StudioWhite else InkObsidian,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (active) StudioWhite else InkObsidian,
            )
        }
    }
}

@Composable
internal fun SkyActionPill(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (filled) SkyPillActiveBg else SkyPillBg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.heightIn(min = 38.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) StudioWhite else SkyPillText,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (filled) StudioWhite else SkyPillText,
            )
        }
    }
}
