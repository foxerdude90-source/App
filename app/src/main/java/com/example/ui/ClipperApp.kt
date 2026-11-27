package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.ClipperTopBar
import com.example.ui.features.captions.CaptionsScreen
import com.example.ui.features.captions.CaptionsViewModel
import com.example.ui.features.compress.CompressScreen
import com.example.ui.features.compress.CompressViewModel
import com.example.ui.features.cutout.CutoutScreen
import com.example.ui.features.cutout.CutoutViewModel
import com.example.ui.features.fetch.FetchScreen
import com.example.ui.features.fetch.FetchViewModel
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.InkSlate
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioWhite

enum class ClipperTab(
    val routeId: String,
    val labelRes: Int,
    val workspaceSubtitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    FETCH(
        routeId = "fetch",
        labelRes = R.string.nav_fetch,
        workspaceSubtitle = "Direct Media Fetcher",
        selectedIcon = Icons.Filled.Link,
        unselectedIcon = Icons.Outlined.Link,
        testTag = "nav_tab_fetch",
    ),
    COMPRESS(
        routeId = "compress",
        labelRes = R.string.nav_compress,
        workspaceSubtitle = "Video & Image Compressor",
        selectedIcon = Icons.Filled.Compress,
        unselectedIcon = Icons.Outlined.Compress,
        testTag = "nav_tab_compress",
    ),
    CUTOUT(
        routeId = "cutout",
        labelRes = R.string.nav_cutout,
        workspaceSubtitle = "Background Remover & Lasso",
        selectedIcon = Icons.Filled.AutoFixHigh,
        unselectedIcon = Icons.Outlined.AutoFixHigh,
        testTag = "nav_tab_cutout",
    ),
    CAPTIONS(
        routeId = "captions",
        labelRes = R.string.nav_captions,
        workspaceSubtitle = "10-Font Caption Studio",
        selectedIcon = Icons.Filled.Subtitles,
        unselectedIcon = Icons.Outlined.Subtitles,
        testTag = "nav_tab_captions",
    ),
}

@Composable
fun ClipperApp(
    fetchViewModel: FetchViewModel = viewModel(),
    compressViewModel: CompressViewModel = viewModel(),
    cutoutViewModel: CutoutViewModel = viewModel(),
    captionsViewModel: CaptionsViewModel = viewModel(),
) {
    var activeTab by rememberSaveable { mutableStateOf(ClipperTab.FETCH) }

    // Return to primary Fetch studio when pressing Back from secondary tabs
    BackHandler(enabled = activeTab != ClipperTab.FETCH) {
        activeTab = ClipperTab.FETCH
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioWhite),
    ) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioWhite)
                    .statusBarsPadding(),
            ) {
                NavigationRail(
                    containerColor = StudioWhite,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    ClipperTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { activeTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes),
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ClipperIndigo,
                                selectedTextColor = ClipperIndigo,
                                indicatorColor = ClipperIndigoSoft,
                                unselectedIconColor = InkSlate,
                                unselectedTextColor = InkSlate,
                            ),
                            modifier = Modifier.testTag(tab.testTag),
                        )
                    }
                }
                VerticalDivider(color = StudioBorder)
                Column(modifier = Modifier.weight(1f)) {
                    ClipperTopBar(workspaceTitle = activeTab.workspaceSubtitle)
                    ClipperTabContent(
                        activeTab = activeTab,
                        fetchViewModel = fetchViewModel,
                        compressViewModel = compressViewModel,
                        cutoutViewModel = cutoutViewModel,
                        captionsViewModel = captionsViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = StudioWhite,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    ClipperTopBar(
                        workspaceTitle = activeTab.workspaceSubtitle,
                        modifier = Modifier.statusBarsPadding(),
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.background(StudioWhite)) {
                        HorizontalDivider(color = StudioBorder)
                        NavigationBar(
                            containerColor = StudioWhite,
                            tonalElevation = 0.dp,
                        ) {
                            ClipperTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ClipperIndigo,
                                        selectedTextColor = ClipperIndigo,
                                        indicatorColor = ClipperIndigoSoft,
                                        unselectedIconColor = InkSlate,
                                        unselectedTextColor = InkSlate,
                                    ),
                                    modifier = Modifier.testTag(tab.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                ClipperTabContent(
                    activeTab = activeTab,
                    fetchViewModel = fetchViewModel,
                    compressViewModel = compressViewModel,
                    cutoutViewModel = cutoutViewModel,
                    captionsViewModel = captionsViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ClipperTabContent(
    activeTab: ClipperTab,
    fetchViewModel: FetchViewModel,
    compressViewModel: CompressViewModel,
    cutoutViewModel: CutoutViewModel,
    captionsViewModel: CaptionsViewModel,
    modifier: Modifier = Modifier,
) {
    when (activeTab) {
        ClipperTab.FETCH -> FetchScreen(viewModel = fetchViewModel, modifier = modifier)
        ClipperTab.COMPRESS -> CompressScreen(viewModel = compressViewModel, modifier = modifier)
        ClipperTab.CUTOUT -> CutoutScreen(viewModel = cutoutViewModel, modifier = modifier)
        ClipperTab.CAPTIONS -> CaptionsScreen(viewModel = captionsViewModel, modifier = modifier)
    }
}
