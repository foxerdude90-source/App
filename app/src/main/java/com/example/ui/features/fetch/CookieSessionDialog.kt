package com.example.ui.features.fetch

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.media.CookieSessionManager
import com.example.core.media.YoutubeDlMediaEngine
import com.example.ui.components.ChoicePill
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperEmeraldSoft
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite

private data class LoginPreset(val label: String, val url: String)

private val DefaultLoginPresets = listOf(
    LoginPreset("Instagram", "https://www.instagram.com/accounts/login/"),
    LoginPreset("YouTube", "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"),
    LoginPreset("X / Twitter", "https://x.com/i/flow/login"),
    LoginPreset("Vimeo", "https://vimeo.com/log_in"),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieSessionDialog(
    initialUrl: String,
    hasSavedCookies: Boolean,
    onDismiss: () -> Unit,
    onCookiesChanged: (saved: Boolean, statusMessage: String) -> Unit,
) {
    val context = LocalContext.current
    val startUrl = remember(initialUrl) {
        initialUrl.trim().ifBlank { DefaultLoginPresets.first().url }
    }

    var addressInput by remember { mutableStateOf(startUrl) }
    var currentLoadedUrl by remember { mutableStateOf(startUrl) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }
    var inlineFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.destroy()
            }
            webViewRef = null
        }
    }

    fun navigateToInputUrl(raw: String) {
        val target = runCatching { YoutubeDlMediaEngine.normalizeAndValidateUrl(raw) }
            .getOrElse { return }
        addressInput = target
        currentLoadedUrl = target
        inlineFeedback = null
        webViewRef?.loadUrl(target)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("cookie_session_dialog"),
            color = StudioWhite,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, StudioBorder),
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cookie,
                            contentDescription = null,
                            tint = ClipperIndigo,
                            modifier = Modifier.size(22.dp),
                        )
                        Column {
                            Text(
                                text = "Session Cookie Manager",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkObsidian,
                            )
                            Text(
                                text = if (hasSavedCookies) "cookies.txt active • Auto-attached to requests" else "Sign in, then tap Save Session",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasSavedCookies) ClipperEmerald else InkSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cookie_dialog_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = InkSlate,
                        )
                    }
                }

                // Quick presets + Configurable Login URL bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(DefaultLoginPresets) { preset ->
                            ChoicePill(
                                text = preset.label,
                                selected = currentLoadedUrl.contains(
                                    CookieSessionManager.extractBaseDomain(
                                        preset.url.substringAfter("://").substringBefore('/'),
                                    ),
                                    ignoreCase = true,
                                ),
                                onClick = { navigateToInputUrl(preset.url) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cookie_url_input"),
                            placeholder = { Text("https://example.com/login", color = InkMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { navigateToInputUrl(addressInput) },
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClipperIndigo,
                                unfocusedBorderColor = StudioBorder,
                                focusedContainerColor = StudioSnow,
                                unfocusedContainerColor = StudioSnow,
                            ),
                        )

                        OutlinedButton(
                            onClick = { navigateToInputUrl(addressInput) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("cookie_load_url_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = "Load URL",
                                tint = ClipperIndigo,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { (loadProgress / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = ClipperIndigo,
                        trackColor = StudioSnow,
                    )
                } else {
                    HorizontalDivider(thickness = 0.75.dp, color = StudioBorder)
                }

                // Embedded Android WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(StudioSnow),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.javaScriptCanOpenWindowsAutomatically = true

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val nextUrl = request?.url?.toString() ?: return false
                                        if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                                            currentLoadedUrl = nextUrl
                                            addressInput = nextUrl
                                            return false
                                        }
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                        if (!url.isNullOrBlank()) {
                                            currentLoadedUrl = url
                                            addressInput = url
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        if (!url.isNullOrBlank()) {
                                            currentLoadedUrl = url
                                            addressInput = url
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        isPageLoading = newProgress < 100
                                    }
                                }

                                webViewRef = this
                                loadUrl(startUrl)
                            }
                        },
                    )
                }

                HorizontalDivider(thickness = 0.75.dp, color = StudioBorder)

                // Feedback banner if user just saved/cleared cookies
                inlineFeedback?.let { (isOk, msg) ->
                    Surface(
                        color = if (isOk) ClipperEmeraldSoft else ClipperRoseSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOk) ClipperEmerald else ClipperRose,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                // Bottom Actions: Save Session & Clear Session
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            CookieSessionManager.clearSession(context)
                            inlineFeedback = true to "Cleared cookies.txt and WebView session."
                            onCookiesChanged(false, "Session cookies cleared.")
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ClipperRose.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cookie_clear_session_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = ClipperRose,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Session", color = ClipperRose)
                    }

                    Button(
                        onClick = {
                            val activeUrl = webViewRef?.url?.takeIf { it.isNotBlank() } ?: currentLoadedUrl
                            runCatching {
                                CookieSessionManager.saveSessionCookies(context, activeUrl)
                            }.onSuccess { res ->
                                val msg = "Saved ${res.cookieCount} cookies for ${res.domain} to cookies.txt"
                                inlineFeedback = true to msg
                                onCookiesChanged(true, msg)
                            }.onFailure { err ->
                                inlineFeedback = false to (err.message ?: "Could not read cookies from this page.")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClipperIndigo,
                            contentColor = StudioWhite,
                        ),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("cookie_save_session_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Session")
                    }
                }
            }
        }
    }
}
