package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenOnline
import com.example.ui.theme.RedOffline
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.util.LocationHelper

enum class MapTileProvider(val id: String, val titleFa: String) {
    ESRI_STREETS("esri_streets", "نقشه خیابان‌ها (ESRI بدون کلید)"),
    ESRI_DARK("esri_dark", "حالت تیره (ESRI Dark)"),
    ESRI_SATELLITE("esri_satellite", "تصاویر ماهواره‌ای (ESRI Imagery)"),
    OSM_HOT("osm_hot", "نقشه شهری آزاد (OSM Humanitarian)"),
    OSM_STANDARD("osm_standard", "استاندارد OpenStreetMap")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveMapView(
    remoteLat: Double?,
    remoteLng: Double?,
    remoteDeviceName: String?,
    anchorLat: Double?,
    anchorLng: Double?,
    thresholdMeters: Int,
    distanceFromAnchor: Double?,
    isLocationAlarmTriggered: Boolean,
    onResetAnchor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var hasLoadError by remember { mutableStateOf(false) }
    var currentProvider by remember { mutableStateOf(MapTileProvider.ESRI_STREETS) }
    var showLayersMenu by remember { mutableStateOf(false) }
    var showNavAppDialog by remember { mutableStateOf(false) }

    // Fallback default coordinates (Tehran center) if no remote location yet
    val currentLat = remoteLat ?: 35.6892
    val currentLng = remoteLng ?: 51.3890
    val hasValidLocation = remoteLat != null && remoteLng != null

    // Update map marker and geofence circle whenever coordinates or anchor change
    LaunchedEffect(remoteLat, remoteLng, anchorLat, anchorLng, thresholdMeters, isMapLoaded) {
        if (isMapLoaded && webViewRef != null && hasValidLocation) {
            val js = buildString {
                append("if (window.updateDevicePosition) {")
                append("  window.updateDevicePosition($currentLat, $currentLng, '${remoteDeviceName ?: "دستگاه"}', ")
                if (anchorLat != null && anchorLng != null) {
                    append("$anchorLat, $anchorLng, $thresholdMeters);")
                } else {
                    append("null, null, $thresholdMeters);")
                }
                append("}")
            }
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        // Embedded Leaflet Map WebView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("live_map_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        // Custom User-Agent to avoid OpenStreetMap 403 Access Blocked restrictions
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; NetAlertTracker/1.2; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NetAlertApp/1.2"
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            hasLoadError = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            if (hasValidLocation) {
                                val js = buildString {
                                    append("if (window.updateDevicePosition) {")
                                    append("  window.updateDevicePosition($currentLat, $currentLng, '${remoteDeviceName ?: "دستگاه"}', ")
                                    if (anchorLat != null && anchorLng != null) {
                                        append("$anchorLat, $anchorLng, $thresholdMeters);")
                                    } else {
                                        append("null, null, $thresholdMeters);")
                                    }
                                    append("}")
                                }
                                view?.evaluateJavascript(js, null)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                hasLoadError = true
                            }
                        }
                    }

                    val html = buildLeafletMapHtml(
                        lat = currentLat,
                        lng = currentLng,
                        hasLocation = hasValidLocation,
                        deviceName = remoteDeviceName ?: "دستگاه",
                        anchorLat = anchorLat,
                        anchorLng = anchorLng,
                        thresholdMeters = thresholdMeters,
                        initialProvider = currentProvider.id
                    )

                    // Use internal app URL base to prevent origin/referrer mismatch tile blocking
                    loadDataWithBaseURL("https://netalert.app.internal/", html, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
            }
        )

        // Loading overlay
        if (!isMapLoaded && !hasLoadError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate950),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "در حال بارگذاری نقشه ماهواره‌ای و زنده...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "منبع: ESRI ArcGIS و OpenStreetMap (رایگان و بدون نیاز به کلید)",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                    )
                }
            }
        }

        // Top Status Card Overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, if (isLocationAlarmTriggered) AmberWarning else Slate800)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isLocationAlarmTriggered) AmberWarning else if (hasValidLocation) GreenOnline else Color.Gray).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isLocationAlarmTriggered) AmberWarning else if (hasValidLocation) GreenOnline else Color.Gray)
                        ) {
                            Icon(
                                imageVector = if (isLocationAlarmTriggered) Icons.Default.NearMe else Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = if (isLocationAlarmTriggered) AmberWarning else if (hasValidLocation) GreenOnline else Color.Gray,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = remoteDeviceName ?: "دستگاه مقابل",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (hasValidLocation)
                                    LocationHelper.formatCoordinates(remoteLat, remoteLng)
                                else
                                    "هنوز مختصاتی دریافت نشده است",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                            )
                        }
                    }

                    // Open in external navigation app (Neshan, Balad, Google Maps)
                    if (hasValidLocation) {
                        IconButton(
                            onClick = { showNavAppDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCardElevated)
                                .testTag("open_external_nav_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = "مسیریاب‌ها (نشان/بلد/گوگل)",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Distance & Geofence telemetry
                if (hasValidLocation && distanceFromAnchor != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCardElevated)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "فاصله از مبنا: ",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                            Text(
                                text = LocationHelper.formatDistance(distanceFromAnchor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLocationAlarmTriggered) AmberWarning else GreenOnline
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onResetAnchor,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("reset_anchor_from_map")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockReset,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ثبت مبنای فعلی", fontSize = 11.sp, color = CyanAccent)
                        }
                    }
                }
            }
        }

        // Error Banner if tile loading failed
        if (hasLoadError) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = BorderStroke(1.dp, AmberWarning)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "عدم دسترسی به اینترنت یا سرور نقشه",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "می‌توانید منبع نقشه را تغییر دهید یا مختصات را در نشان، بلد یا گوگل‌مپ باز کنید.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                hasLoadError = false
                                webViewRef?.reload()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Slate950)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تلاش مجدد")
                        }

                        if (hasValidLocation) {
                            OutlinedButton(
                                onClick = { showNavAppDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("باز در نشان/بلد", color = CyanAccent)
                            }
                        }
                    }
                }
            }
        }

        // Map Control Floating Buttons (Recenter, Layers Switcher, Zoom +/-)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Layer Switcher
            Box {
                FloatingActionButton(
                    onClick = { showLayersMenu = true },
                    containerColor = SurfaceCard,
                    contentColor = CyanAccent,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_layer_switcher_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "تغییر لایه نقشه",
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showLayersMenu,
                    onDismissRequest = { showLayersMenu = false },
                    modifier = Modifier.background(SurfaceCard)
                ) {
                    MapTileProvider.values().forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentProvider == provider) {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = provider.titleFa,
                                        color = if (currentProvider == provider) CyanAccent else Color.White,
                                        fontWeight = if (currentProvider == provider) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            onClick = {
                                currentProvider = provider
                                showLayersMenu = false
                                webViewRef?.evaluateJavascript(
                                    "if (window.switchLayer) { window.switchLayer('${provider.id}'); }",
                                    null
                                )
                            }
                        )
                    }
                }
            }

            // Recenter to device location
            FloatingActionButton(
                onClick = {
                    if (hasValidLocation) {
                        webViewRef?.evaluateJavascript(
                            "if (window.recenterMap) { window.recenterMap($currentLat, $currentLng); }",
                            null
                        )
                    }
                },
                containerColor = CyanAccent,
                contentColor = Slate950,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_recenter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "مرکز نقشه روی دستگاه",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Zoom In
            FloatingActionButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.map) { window.map.zoomIn(); }", null)
                },
                containerColor = SurfaceCard,
                contentColor = Color.White,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "بزرگنمایی")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.map) { window.map.zoomOut(); }", null)
                },
                containerColor = SurfaceCard,
                contentColor = Color.White,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "کوچک‌نمایی")
            }
        }

        // Navigation Apps Dialog (Neshan, Balad, Google Maps)
        if (showNavAppDialog && hasValidLocation) {
            AlertDialog(
                onDismissRequest = { showNavAppDialog = false },
                title = {
                    Text(
                        text = "انتخاب مسیریاب برای مشاهده زنده",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "مختصات: ${LocationHelper.formatCoordinates(remoteLat, remoteLng)}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )

                        // Option 1: Neshan
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showNavAppDialog = false
                                    LocationHelper.openNeshan(context, remoteLat!!, remoteLng!!)
                                },
                            color = SurfaceCardElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, tint = CyanAccent)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("مسیریاب نشان (Neshan)", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("باز شدن موقعیت در اپلیکیشن نشان", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Option 2: Balad
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showNavAppDialog = false
                                    LocationHelper.openBalad(context, remoteLat!!, remoteLng!!)
                                },
                            color = SurfaceCardElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, tint = GreenOnline)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("مسیریاب بلد (Balad)", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("باز شدن موقعیت در اپلیکیشن بلد", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Option 3: Google Maps / Default Geo
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showNavAppDialog = false
                                    LocationHelper.openMapIntent(context, remoteLat!!, remoteLng!!, "موقعیت ${remoteDeviceName ?: "دستگاه"}")
                                },
                            color = SurfaceCardElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = AmberWarning)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("گوگل مپ / برنامه پیش‌فرض", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Google Maps یا هر مسیریاب نصب شده دیگر", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNavAppDialog = false }) {
                        Text("بستن", color = CyanAccent)
                    }
                },
                containerColor = SurfaceCard
            )
        }
    }
}

private fun buildLeafletMapHtml(
    lat: Double,
    lng: Double,
    hasLocation: Boolean,
    deviceName: String,
    anchorLat: Double?,
    anchorLng: Double?,
    thresholdMeters: Int,
    initialProvider: String
): String {
    return """
        <!DOCTYPE html>
        <html lang="fa" dir="rtl">
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            
            <!-- Resilient CDN loading for Leaflet with multiple CDNs -->
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" 
                  onerror="this.onerror=null;this.href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css';" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js" 
                    onerror="this.onerror=null;this.src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js';"></script>

            <style>
                * {
                    box-sizing: border-box;
                    -webkit-tap-highlight-color: transparent;
                }
                html, body, #map {
                    height: 100%;
                    width: 100%;
                    margin: 0;
                    padding: 0;
                    background: #090D16;
                    overflow: hidden;
                }
                .custom-pulse-marker {
                    position: relative;
                }
                .pulse-ring {
                    border: 3px solid #06B6D4;
                    -webkit-border-radius: 30px;
                    border-radius: 30px;
                    height: 26px;
                    width: 26px;
                    position: absolute;
                    left: -13px;
                    top: -13px;
                    -webkit-animation: pulsate 1.5s ease-out;
                    animation: pulsate 1.5s ease-out;
                    -webkit-animation-iteration-count: infinite; 
                    animation-iteration-count: infinite;
                    opacity: 0.0;
                }
                .pulse-dot {
                    width: 16px;
                    height: 16px;
                    background: #22D3EE;
                    border: 2px solid #FFFFFF;
                    border-radius: 50%;
                    position: absolute;
                    left: -8px;
                    top: -8px;
                    box-shadow: 0 0 10px #06B6D4;
                }
                @-webkit-keyframes pulsate {
                    0% {-webkit-transform: scale(0.1, 0.1); opacity: 0.0;}
                    50% {opacity: 1.0;}
                    100% {-webkit-transform: scale(2.2, 2.2); opacity: 0.0;}
                }
                @keyframes pulsate {
                    0% {transform: scale(0.1, 0.1); opacity: 0.0;}
                    50% {opacity: 1.0;}
                    100% {transform: scale(2.2, 2.2); opacity: 0.0;}
                }
                .leaflet-popup-content-wrapper {
                    background: #111827 !important;
                    color: #FFFFFF !important;
                    border-radius: 12px !important;
                    border: 1px solid #334155 !important;
                    font-family: system-ui, -apple-system, sans-serif !important;
                    direction: rtl !important;
                    box-shadow: 0 10px 25px rgba(0,0,0,0.5) !important;
                }
                .leaflet-popup-tip {
                    background: #111827 !important;
                }
                .leaflet-popup-content {
                    margin: 10px 14px !important;
                    font-size: 13px !important;
                    line-height: 1.5 !important;
                }
                .leaflet-container {
                    background-color: #090D16 !important;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false
                }).setView([$lat, $lng], ${if (hasLocation) 16 else 11});

                // Definitions for reliable tile providers that do NOT require any API key
                var providers = {
                    esri_streets: {
                        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
                        options: {
                            maxZoom: 19,
                            attribution: 'Esri'
                        }
                    },
                    esri_dark: {
                        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
                        options: {
                            maxZoom: 16,
                            attribution: 'Esri'
                        }
                    },
                    esri_satellite: {
                        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                        options: {
                            maxZoom: 19,
                            attribution: 'Esri'
                        }
                    },
                    osm_hot: {
                        url: 'https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png',
                        options: {
                            subdomains: 'ab',
                            maxZoom: 19,
                            attribution: 'OpenStreetMap France'
                        }
                    },
                    osm_standard: {
                        url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                        options: {
                            subdomains: 'abc',
                            maxZoom: 19,
                            attribution: 'OpenStreetMap'
                        }
                    }
                };

                var currentLayer = null;
                var currentProviderKey = '$initialProvider';
                var tileErrorCount = 0;

                function switchLayer(providerKey) {
                    if (!providers[providerKey]) {
                        providerKey = 'esri_streets';
                    }
                    if (currentLayer) {
                        map.removeLayer(currentLayer);
                    }
                    currentProviderKey = providerKey;
                    tileErrorCount = 0;
                    var config = providers[providerKey];
                    currentLayer = L.tileLayer(config.url, config.options);
                    
                    // Fallback to ESRI Streets if another provider fails
                    currentLayer.on('tileerror', function(error, tile) {
                        tileErrorCount++;
                        if (tileErrorCount > 4 && currentProviderKey !== 'esri_streets') {
                            console.warn('Tiles blocked or failing on ' + currentProviderKey + ', falling back to ESRI Streets');
                            switchLayer('esri_streets');
                        }
                    });

                    currentLayer.addTo(map);
                }

                // Initialize with requested provider
                switchLayer('$initialProvider');

                var deviceMarker = null;
                var anchorMarker = null;
                var geofenceCircle = null;

                var pulseIcon = L.divIcon({
                    className: 'custom-pulse-marker',
                    html: '<div class="pulse-ring"></div><div class="pulse-dot"></div>',
                    iconSize: [20, 20]
                });

                function updateDevicePosition(newLat, newLng, name, aLat, aLng, radius) {
                    if (deviceMarker) {
                        deviceMarker.setLatLng([newLat, newLng]);
                    } else {
                        deviceMarker = L.marker([newLat, newLng], { icon: pulseIcon }).addTo(map);
                    }
                    deviceMarker.bindPopup("<b>" + name + "</b><br>موقعیت زنده دستگاه").openPopup();

                    if (aLat !== null && aLng !== null && aLat !== undefined) {
                        if (anchorMarker) {
                            anchorMarker.setLatLng([aLat, aLng]);
                        } else {
                            anchorMarker = L.circleMarker([aLat, aLng], {
                                color: '#F59E0B',
                                fillColor: '#FBBF24',
                                fillOpacity: 0.6,
                                radius: 8
                            }).addTo(map);
                            anchorMarker.bindPopup("موقعیت مبنا (Anchor)");
                        }

                        if (geofenceCircle) {
                            geofenceCircle.setLatLng([aLat, aLng]);
                            geofenceCircle.setRadius(radius);
                        } else {
                            geofenceCircle = L.circle([aLat, aLng], {
                                color: '#F59E0B',
                                fillColor: '#F59E0B',
                                fillOpacity: 0.15,
                                radius: radius,
                                dashArray: '5, 8'
                            }).addTo(map);
                        }
                    }
                }

                function recenterMap(lat, lng) {
                    map.setView([lat, lng], 17, { animate: true });
                }

                ${if (hasLocation) "updateDevicePosition($lat, $lng, '$deviceName', ${anchorLat ?: "null"}, ${anchorLng ?: "null"}, $thresholdMeters);" else ""}
            </script>
        </body>
        </html>
    """.trimIndent()
}
