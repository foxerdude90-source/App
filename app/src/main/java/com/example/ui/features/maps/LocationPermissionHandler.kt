package com.example.ui.features.maps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@Stable
data class MapsLocationPermissionController(
    val hasLocationPermission: Boolean,
    val hasFineLocationPermission: Boolean,
    val shouldShowRationale: Boolean,
    val isPermanentlyDenied: Boolean,
    val requestLocationPermissions: () -> Unit,
    val openAppSettings: () -> Unit,
)

/**
 * Accompanist Permissions controller for foreground GPS access (`ACCESS_FINE_LOCATION` and
 * `ACCESS_COARSE_LOCATION`). Syncs permission grant state directly into [onPermissionStateChanged]
 * and supports launching Android App Settings if the user permanently denies access.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMapsLocationPermissionController(
    onPermissionStateChanged: (granted: Boolean, userInitiated: Boolean) -> Unit,
): MapsLocationPermissionController {
    val context = LocalContext.current
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    ) { results ->
        val granted = (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        onPermissionStateChanged(granted, true)
    }

    val fineGranted = permissionState.permissions
        .firstOrNull { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }
        ?.status?.isGranted == true
    val coarseGranted = permissionState.permissions
        .firstOrNull { it.permission == Manifest.permission.ACCESS_COARSE_LOCATION }
        ?.status?.isGranted == true
    val anyGranted = fineGranted || coarseGranted
    val shouldShowRationale = !anyGranted && permissionState.permissions.any { it.status.shouldShowRationale }
    val isPermanentlyDenied = !anyGranted && hasRequestedOnce && !shouldShowRationale

    // Keep ViewModel synced whenever the user returns from system Settings or grants permissions
    LaunchedEffect(anyGranted) {
        onPermissionStateChanged(anyGranted, false)
    }

    return remember(anyGranted, fineGranted, shouldShowRationale, isPermanentlyDenied, permissionState, context) {
        MapsLocationPermissionController(
            hasLocationPermission = anyGranted,
            hasFineLocationPermission = fineGranted,
            shouldShowRationale = shouldShowRationale,
            isPermanentlyDenied = isPermanentlyDenied,
            requestLocationPermissions = {
                hasRequestedOnce = true
                permissionState.launchMultiplePermissionRequest()
            },
            openAppSettings = {
                openApplicationLocationSettings(context)
            },
        )
    }
}

private fun openApplicationLocationSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
