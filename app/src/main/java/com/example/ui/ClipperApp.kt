package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Tune
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.ClipperTopBar
import com.example.ui.features.captions.CaptionsViewModel
import com.example.ui.features.compress.CompressViewModel
import com.example.ui.features.cutout.CutoutScreen
import com.example.ui.features.cutout.CutoutViewModel
import com.example.ui.features.fetch.FetchScreen
import com.example.ui.features.fetch.FetchViewModel
import com.example.ui.features.image.ImageStudioViewModel
import com.example.ui.features.maps.MapsScreen
import com.example.ui.features.maps.MapsViewModel
import com.example.ui.features.music.MusicDetectorViewModel
import com.example.ui.features.studio.StudioScreen
import com.example.ui.features.studio.StudioToolId
import com.example.ui.features.studio.StudioViewModel
import com.example.ui.features.upscale.UpscaleScreen
import com.example.ui.features.upscale.UpscaleViewModel
import com.example.ui.features.velocity.VelocityViewModel
import com.example.ui.features.voice.VoiceScreen
import com.example.ui.features.voice.VoiceViewModel
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.InkSlate
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
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
        workspaceSubtitle = "Media Downloader",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        testTag = "nav_tab_fetch",
    ),
    UPSCALE(
        routeId = "upscale",
        labelRes = R.string.nav_upscale,
        workspaceSubtitle = "GPU Video Upscaler",
        selectedIcon = Icons.Filled.Hd,
        unselectedIcon = Icons.Outlined.Hd,
        testTag = "nav_tab_upscale",
    ),
    CUTOUT(
        routeId = "cutout",
        labelRes = R.string.nav_cutout,
        workspaceSubtitle = "AI Background Studio",
        selectedIcon = Icons.Filled.AutoFixHigh,
        unselectedIcon = Icons.Outlined.AutoFixHigh,
        testTag = "nav_tab_cutout",
    ),
    VOICE(
        routeId = "voice",
        labelRes = R.string.nav_voice,
        workspaceSubtitle = "Voice Studio & Cloning",
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        testTag = "nav_tab_voice",
    ),
    STUDIO(
        routeId = "studio",
        labelRes = R.string.nav_studio,
        workspaceSubtitle = "Pro Media Lab",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_studio",
    ),
}

@Composable
fun ClipperApp(
    fetchViewModel: FetchViewModel = viewModel(),
    upscaleViewModel: UpscaleViewModel = viewModel(),
    cutoutViewModel: CutoutViewModel = viewModel(),
    voiceViewModel: VoiceViewModel = viewModel(),
    studioViewModel: StudioViewModel = viewModel(),
    compressViewModel: CompressViewModel = viewModel(),
    captionsViewModel: CaptionsViewModel = viewModel(),
    velocityViewModel: VelocityViewModel = viewModel(),
    imageStudioViewModel: ImageStudioViewModel = viewModel(),
    mapsViewModel: MapsViewModel = viewModel(),
    musicDetectorViewModel: MusicDetectorViewModel = viewModel(),
) {
    var activeTab by rememberSaveable { mutableStateOf(ClipperTab.FETCH) }
    val studioState by studioViewModel.uiState.collectAsStateWithLifecycle()
    val isFullScreenMaps = activeTab == ClipperTab.STUDIO && studioState.activeTool == StudioToolId.MAPS

    if (isFullScreenMaps) {
        BackHandler {
            studioViewModel.openTool(null)
        }
        MapsScreen(
            viewModel = mapsViewModel,
            onExitFullScreen = { studioViewModel.openTool(null) },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    BackHandler(enabled = activeTab != ClipperTab.FETCH) {
        activeTab = ClipperTab.FETCH
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioSnow),
    ) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioSnow)
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
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
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
                VerticalDivider(thickness = 0.75.dp, color = StudioBorder)
                Column(modifier = Modifier.weight(1f)) {
                    ClipperTopBar(workspaceTitle = activeTab.workspaceSubtitle)
                    ClipperTabContent(
                        activeTab = activeTab,
                        fetchViewModel = fetchViewModel,
                        upscaleViewModel = upscaleViewModel,
                        cutoutViewModel = cutoutViewModel,
                        voiceViewModel = voiceViewModel,
                        studioViewModel = studioViewModel,
                        compressViewModel = compressViewModel,
                        captionsViewModel = captionsViewModel,
                        velocityViewModel = velocityViewModel,
                        imageStudioViewModel = imageStudioViewModel,
                        musicDetectorViewModel = musicDetectorViewModel,
                        onOpenSongLabWithUrl = { url ->
                            activeTab = ClipperTab.STUDIO
                            studioViewModel.openTool(StudioToolId.MUSIC_DETECTOR)
                            musicDetectorViewModel.preloadSocialUrlAndScan(url)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = StudioSnow,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    ClipperTopBar(
                        workspaceTitle = activeTab.workspaceSubtitle,
                        modifier = Modifier.statusBarsPadding(),
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.background(StudioWhite)) {
                        HorizontalDivider(thickness = 0.75.dp, color = StudioBorder)
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
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
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
                    upscaleViewModel = upscaleViewModel,
                    cutoutViewModel = cutoutViewModel,
                    voiceViewModel = voiceViewModel,
                    studioViewModel = studioViewModel,
                    compressViewModel = compressViewModel,
                    captionsViewModel = captionsViewModel,
                    velocityViewModel = velocityViewModel,
                    imageStudioViewModel = imageStudioViewModel,
                    musicDetectorViewModel = musicDetectorViewModel,
                    onOpenSongLabWithUrl = { url ->
                        activeTab = ClipperTab.STUDIO
                        studioViewModel.openTool(StudioToolId.MUSIC_DETECTOR)
                        musicDetectorViewModel.preloadSocialUrlAndScan(url)
                    },
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
    upscaleViewModel: UpscaleViewModel,
    cutoutViewModel: CutoutViewModel,
    voiceViewModel: VoiceViewModel,
    studioViewModel: StudioViewModel,
    compressViewModel: CompressViewModel,
    captionsViewModel: CaptionsViewModel,
    velocityViewModel: VelocityViewModel,
    imageStudioViewModel: ImageStudioViewModel,
    musicDetectorViewModel: MusicDetectorViewModel,
    onOpenSongLabWithUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = activeTab,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 32 })
                .togetherWith(fadeOut(tween(150)))
        },
        label = "clipper_tab_transition",
        modifier = modifier,
    ) { tab ->
        when (tab) {
            ClipperTab.FETCH -> FetchScreen(
                viewModel = fetchViewModel,
                onOpenInSongDetector = onOpenSongLabWithUrl,
                modifier = Modifier.fillMaxSize(),
            )
            ClipperTab.UPSCALE -> UpscaleScreen(viewModel = upscaleViewModel, modifier = Modifier.fillMaxSize())
            ClipperTab.CUTOUT -> CutoutScreen(viewModel = cutoutViewModel, modifier = Modifier.fillMaxSize())
            ClipperTab.VOICE -> VoiceScreen(viewModel = voiceViewModel, modifier = Modifier.fillMaxSize())
            ClipperTab.STUDIO -> StudioScreen(
                viewModel = studioViewModel,
                compressViewModel = compressViewModel,
                captionsViewModel = captionsViewModel,
                velocityViewModel = velocityViewModel,
                imageStudioViewModel = imageStudioViewModel,
                musicDetectorViewModel = musicDetectorViewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
