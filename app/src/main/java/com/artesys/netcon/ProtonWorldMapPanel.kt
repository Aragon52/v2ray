package com.artesys.netcon

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

internal data class MapHubNode(
    val code: String,
    val countryName: String,
    val cityLabel: String,
    val latitude: Double,
    val longitude: Double,
)

internal val WorldHubNodes = listOf(
    MapHubNode("US", "United States", "Ashburn", 39.0438, -77.4874),
    MapHubNode("CA", "Canada", "Toronto", 43.6532, -79.3832),
    MapHubNode("GB", "United Kingdom", "London", 51.5072, -0.1276),
    MapHubNode("FR", "France", "Paris", 48.8566, 2.3522),
    MapHubNode("NL", "Netherlands", "Amsterdam", 52.3676, 4.9041),
    MapHubNode("DE", "Germany", "Frankfurt", 50.1109, 8.6821),
    MapHubNode("SE", "Sweden", "Stockholm", 59.3293, 18.0686),
    MapHubNode("FI", "Finland", "Helsinki", 60.1699, 24.9384),
    MapHubNode("TR", "Turkey", "Istanbul", 41.0082, 28.9784),
    MapHubNode("IR", "Iran", "Tehran", 35.6892, 51.3890),
    MapHubNode("AE", "UAE", "Dubai", 25.2048, 55.2708),
    MapHubNode("IN", "India", "Mumbai", 19.0760, 72.8777),
    MapHubNode("SG", "Singapore", "Singapore", 1.3521, 103.8198),
    MapHubNode("HK", "Hong Kong", "Hong Kong", 22.3193, 114.1694),
    MapHubNode("KR", "South Korea", "Seoul", 37.5665, 126.9780),
    MapHubNode("JP", "Japan", "Tokyo", 35.6762, 139.6503),
    MapHubNode("AU", "Australia", "Sydney", -33.8688, 151.2093),
)

private val HubCodeSet = WorldHubNodes.map { it.code }.toSet()

private data class VectorCountry(
    val isoCode: String,
    val name: String,
    val rings: List<FloatArray>, // interleaved normalized [nx0, ny0, nx1, ny1, ...]
)

private object WorldVectorCache {
    @Volatile
    private var cached: List<VectorCountry>? = null

    suspend fun load(context: Context): List<VectorCountry> {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            cached?.let { return@withContext it }
            val rawJson = context.resources.openRawResource(R.raw.world_countries)
                .bufferedReader()
                .use { it.readText() }
            val array = JSONArray(rawJson)
            val result = ArrayList<VectorCountry>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val code = obj.optString("c", "")
                val name = obj.optString("n", "")
                val polygons = obj.getJSONArray("p")
                val rings = ArrayList<FloatArray>(polygons.length())
                for (p in 0 until polygons.length()) {
                    val pts = polygons.getJSONArray(p)
                    val coords = FloatArray(pts.length() * 2)
                    for (k in 0 until pts.length()) {
                        val pt = pts.getJSONArray(k)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        val (nx, ny) = projectNormalized(lat, lon)
                        coords[k * 2] = nx
                        coords[k * 2 + 1] = ny
                    }
                    rings.add(coords)
                }
                result.add(VectorCountry(code, name, rings))
            }
            cached = result
            result
        }
    }
}

private const val MIN_LAT = -58.0
private const val MAX_LAT = 82.0

private fun mercatorY(latDeg: Double): Double {
    val clamped = latDeg.coerceIn(MIN_LAT, MAX_LAT)
    val rad = Math.toRadians(clamped)
    return ln(tan(PI / 4.0 + rad / 2.0))
}

private val MERC_MAX = mercatorY(MAX_LAT)
private val MERC_MIN = mercatorY(MIN_LAT)
private val MERC_SPAN = MERC_MAX - MERC_MIN

private fun projectNormalized(latitude: Double, longitude: Double): Pair<Float, Float> {
    val nx = ((longitude.coerceIn(-180.0, 180.0) + 180.0) / 360.0).toFloat()
    val ny = ((MERC_MAX - mercatorY(latitude)) / MERC_SPAN).toFloat()
    return nx to ny
}

private fun unprojectNormalized(nx: Float, ny: Float): Pair<Double, Double> {
    val lon = (nx.coerceIn(0f, 1f) * 360.0) - 180.0
    val mercY = MERC_MAX - (ny.coerceIn(0f, 1f) * MERC_SPAN)
    val lat = Math.toDegrees(2.0 * atan(exp(mercY)) - PI / 2.0)
        .coerceIn(-85.0, 85.0)
    return lat to lon
}

internal fun inferCountryCode(locationName: String, latitude: Double?, longitude: Double?): String? {
    val upper = locationName.uppercase()
    WorldHubNodes.firstOrNull { node ->
        upper.contains(node.countryName.uppercase()) ||
            upper.contains(node.cityLabel.uppercase()) ||
            Regex("\\b${node.code}\\b").containsMatchIn(upper)
    }?.let { return it.code }
    if (latitude != null && longitude != null) {
        val closest = WorldHubNodes.minByOrNull { hypot(it.latitude - latitude, it.longitude - longitude) }
        if (closest != null && hypot(closest.latitude - latitude, closest.longitude - longitude) < 8.0) {
            return closest.code
        }
    }
    return null
}

@Composable
internal fun ProtonWorldMapPanel(
    serverName: String,
    locationName: String,
    latitude: Double?,
    longitude: Double?,
    vpnState: String,
    onSetLocation: () -> Unit,
    onBrowseCountryServers: (String) -> Unit,
    onSaveCoordinates: (String, Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val countries by produceState<List<VectorCountry>>(initialValue = emptyList()) {
        value = WorldVectorCache.load(context)
    }

    val activeCountryCode = remember(locationName, latitude, longitude) {
        inferCountryCode(locationName, latitude, longitude)
    }

    var selectedHub by remember { mutableStateOf<MapHubNode?>(null) }
    var showFullMap by rememberSaveable { mutableStateOf(false) }

    // Camera state: zoom = 1f fits full world; panX/panY in [0..1] normalized map space for viewport center
    var targetZoom by rememberSaveable { mutableFloatStateOf(1.15f) }
    var targetCenterX by rememberSaveable { mutableFloatStateOf(0.55f) }
    var targetCenterY by rememberSaveable { mutableFloatStateOf(0.45f) }

    // Center gently on active server when location changes
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            val (nx, ny) = projectNormalized(latitude, longitude)
            targetCenterX = nx
            targetCenterY = ny
            targetZoom = 2.0f
        } else {
            targetCenterX = 0.50f
            targetCenterY = 0.45f
            targetZoom = 1.05f
        }
    }

    val animZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.85f),
        label = "mapZoom",
    )
    val animCenterX by animateFloatAsState(
        targetValue = targetCenterX,
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.85f),
        label = "mapCenterX",
    )
    val animCenterY by animateFloatAsState(
        targetValue = targetCenterY,
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.85f),
        label = "mapCenterY",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFDCE2E8), Color(0xFFB3BCC5), Color(0xFF939CA6)),
                ),
            )
            .border(1.5.dp, Color(0xFFE9EEF2), RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            when (vpnState) {
                                "connected" -> NetconColors.Online
                                "connecting" -> NetconColors.Copper
                                else -> Color(0xFF64748B)
                            },
                            CircleShape,
                        ),
                )
                Text(
                    "GLOBAL ROUTING MAP",
                    color = Color(0xFF232B35),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "EDIT",
                    color = Color(0xFF7A3E18),
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("map_edit_button")
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onSetLocation)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Text(
                    "FULL MAP",
                    color = Color(0xFF0E3A53),
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("map_expand_button")
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showFullMap = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        // Interactive Proton-style World Map Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .testTag("map_panel")
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(NetconColors.OceanDeep, NetconColors.OceanDark),
                    ),
                )
                .border(1.5.dp, Color(0xFF444E5A), RoundedCornerShape(10.dp))
                .clipToBounds(),
        ) {
            WorldVectorCanvas(
                countries = countries,
                activeCountryCode = activeCountryCode,
                inspectedCountryCode = selectedHub?.code,
                activeLat = latitude,
                activeLon = longitude,
                vpnState = vpnState,
                zoom = animZoom,
                centerX = animCenterX,
                centerY = animCenterY,
                onTransform = { zoomFactor, panDelta, canvasSize ->
                    val newZoom = (targetZoom * zoomFactor).coerceIn(1.0f, 6.5f)
                    val mapScale = canvasSize.width.toFloat().coerceAtLeast(1f) * newZoom
                    val mapHeight = mapScale / 1.607f
                    targetZoom = newZoom
                    targetCenterX = (targetCenterX - panDelta.x / mapScale).coerceIn(0.05f, 0.95f)
                    targetCenterY = (targetCenterY - panDelta.y / mapHeight).coerceIn(0.08f, 0.92f)
                },
                onHubTapped = { hub ->
                    selectedHub = if (selectedHub?.code == hub.code) null else hub
                    val (nx, ny) = projectNormalized(hub.latitude, hub.longitude)
                    targetCenterX = nx
                    targetCenterY = ny
                    if (targetZoom < 2.1f) targetZoom = 2.2f
                },
                onBackgroundTapped = { selectedHub = null },
            )

            // Top-left status & hub count chip
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetconColors.DeepInk.copy(alpha = 0.82f))
                    .border(1.dp, NetconColors.BevelBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (vpnState == "connected") NetconColors.Online else NetconColors.Copper,
                            CircleShape,
                        ),
                )
                Text(
                    "${WorldHubNodes.size} REGIONS",
                    color = NetconColors.TextPrimary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Top-right Expand button
            MapHudButton(
                icon = Icons.Filled.OpenInFull,
                contentDescription = "Open fullscreen world map",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                onClick = { showFullMap = true },
            )

            // Bottom-right Zoom / Recenter / World Overview controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                MapHudButton(
                    icon = Icons.Filled.Public,
                    contentDescription = "Fit full world map",
                    modifier = Modifier.testTag("map_world_button"),
                    onClick = {
                        targetZoom = 1.0f
                        targetCenterX = 0.50f
                        targetCenterY = 0.48f
                    },
                )
                MapHudButton(
                    icon = Icons.Filled.MyLocation,
                    contentDescription = "Center on active server",
                    modifier = Modifier.testTag("map_recenter_button"),
                    onClick = {
                        if (latitude != null && longitude != null) {
                            val (nx, ny) = projectNormalized(latitude, longitude)
                            targetCenterX = nx
                            targetCenterY = ny
                            targetZoom = 2.4f
                        } else {
                            targetCenterX = 0.50f
                            targetCenterY = 0.48f
                            targetZoom = 1.0f
                        }
                    },
                )
                MapHudButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Zoom in",
                    modifier = Modifier.testTag("map_zoom_in_button"),
                    onClick = { targetZoom = (targetZoom * 1.45f).coerceAtMost(6.5f) },
                )
                MapHudButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Zoom out",
                    modifier = Modifier.testTag("map_zoom_out_button"),
                    onClick = { targetZoom = (targetZoom / 1.45f).coerceAtLeast(1.0f) },
                )
            }

            // Floating HUD card when user taps any country hub pin on the world map
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedHub != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                selectedHub?.let { hub ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetconColors.DeepInk.copy(alpha = 0.92f))
                            .border(1.dp, NetconColors.Copper, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(
                                "${hub.code} · ${hub.countryName}",
                                color = NetconColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                hub.cityLabel,
                                color = NetconColors.TextSecondary,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .testTag("map_browse_country_button")
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.verticalGradient(listOf(NetconColors.CopperLight, NetconColors.Copper)),
                                )
                                .clickable {
                                    onBrowseCountryServers(hub.code)
                                    selectedHub = null
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "SERVERS",
                                color = Color(0xFF1D0E05),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Bottom active server coordinate readout bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A1017))
                .border(1.dp, Color(0xFF47525E), RoundedCornerShape(8.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    locationName.ifBlank { "Location Not Set" },
                    color = NetconColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val coordText = if (latitude != null && longitude != null) {
                    "%.4f°, %.4f° · Tap any globe node to browse region".format(latitude, longitude)
                } else {
                    "No coordinates · Tap FULL MAP or EDIT to set node"
                }
                Text(
                    coordText,
                    color = NetconColors.CyberCyan,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.EditLocationAlt,
                contentDescription = "Edit location",
                tint = NetconColors.CopperLight,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onSetLocation),
            )
        }
    }

    if (showFullMap) {
        FullProtonMapDialog(
            countries = countries,
            serverName = serverName,
            initialLocationName = locationName,
            initialLat = latitude,
            initialLon = longitude,
            vpnState = vpnState,
            onDismiss = { showFullMap = false },
            onBrowseCountryServers = { code ->
                showFullMap = false
                onBrowseCountryServers(code)
            },
            onSaveCoordinates = { name, lat, lon ->
                onSaveCoordinates(name, lat, lon)
                showFullMap = false
            },
        )
    }
}

@Composable
private fun WorldVectorCanvas(
    countries: List<VectorCountry>,
    activeCountryCode: String?,
    inspectedCountryCode: String?,
    activeLat: Double?,
    activeLon: Double?,
    vpnState: String,
    zoom: Float,
    centerX: Float,
    centerY: Float,
    onTransform: (zoomFactor: Float, panDelta: Offset, size: IntSize) -> Unit,
    onHubTapped: (MapHubNode) -> Unit,
    onBackgroundTapped: (Pair<Double, Double>) -> Unit,
) {
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val connected = vpnState == "connected"
    val connecting = vpnState == "connecting"
    val beaconColor = when {
        connected -> NetconColors.OnlineSoft
        connecting -> NetconColors.CopperLight
        else -> NetconColors.Copper
    }

    val tapThresholdPx = with(density) { 22.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { _, pan, gestureZoom, _ ->
                    onTransform(gestureZoom, pan, canvasSize)
                }
            }
            .pointerInput(zoom, centerX, centerY, canvasSize) {
                detectTapGestures { tapOffset ->
                    if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTapGestures
                    val mapW = canvasSize.width.toFloat() * zoom
                    val mapH = mapW / 1.607f
                    val originX = canvasSize.width / 2f - centerX * mapW
                    val originY = canvasSize.height / 2f - centerY * mapH

                    val hitHub = WorldHubNodes.minByOrNull { hub ->
                        val (hx, hy) = projectNormalized(hub.latitude, hub.longitude)
                        val sx = originX + hx * mapW
                        val sy = originY + hy * mapH
                        hypot(sx - tapOffset.x, sy - tapOffset.y)
                    }
                    if (hitHub != null) {
                        val (hx, hy) = projectNormalized(hitHub.latitude, hitHub.longitude)
                        val sx = originX + hx * mapW
                        val sy = originY + hy * mapH
                        if (hypot(sx - tapOffset.x, sy - tapOffset.y) <= tapThresholdPx) {
                            onHubTapped(hitHub)
                            return@detectTapGestures
                        }
                    }
                    val nx = ((tapOffset.x - originX) / mapW).coerceIn(0f, 1f)
                    val ny = ((tapOffset.y - originY) / mapH).coerceIn(0f, 1f)
                    onBackgroundTapped(unprojectNormalized(nx, ny))
                }
            },
    ) {
        // Static base map layer: only redraws when countries/camera/selection change (not 60fps!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mapW = size.width * zoom
            val mapH = mapW / 1.607f
            val originX = size.width / 2f - centerX * mapW
            val originY = size.height / 2f - centerY * mapH

            val gridPaint = NetconColors.BevelBorder.copy(alpha = 0.22f)
            for (lonDeg in -150..150 step 30) {
                val (gx, _) = projectNormalized(0.0, lonDeg.toDouble())
                val sx = originX + gx * mapW
                if (sx in -2f..size.width + 2f) {
                    drawLine(gridPaint, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 0.6.dp.toPx())
                }
            }
            for (latDeg in -30..60 step 30) {
                val (_, gy) = projectNormalized(latDeg.toDouble(), 0.0)
                val sy = originY + gy * mapH
                if (sy in -2f..size.height + 2f) {
                    drawLine(gridPaint, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 0.6.dp.toPx())
                }
            }

            val strokeThin = 0.7.dp.toPx()
            val strokeHighlight = 1.3.dp.toPx()

            countries.forEach { country ->
                val isActiveCountry = activeCountryCode != null && country.isoCode == activeCountryCode
                val isInspectedCountry = inspectedCountryCode != null && country.isoCode == inspectedCountryCode
                val isHubCountry = country.isoCode in HubCodeSet

                val fillColor = when {
                    isActiveCountry -> beaconColor.copy(alpha = if (connected) 0.30f else 0.34f)
                    isInspectedCountry -> NetconColors.CyberCyan.copy(alpha = 0.28f)
                    isHubCountry -> NetconColors.LandHub
                    else -> NetconColors.LandBase
                }
                val borderColor = when {
                    isActiveCountry -> beaconColor
                    isInspectedCountry -> NetconColors.CyberCyan
                    isHubCountry -> NetconColors.LandHubStroke
                    else -> NetconColors.LandStroke
                }
                val borderWidth = if (isActiveCountry || isInspectedCountry) strokeHighlight else strokeThin

                country.rings.forEach { ring ->
                    if (ring.size < 6) return@forEach
                    val path = Path()
                    var minX = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE

                    val count = ring.size / 2
                    for (k in 0 until count) {
                        val sx = originX + ring[k * 2] * mapW
                        val sy = originY + ring[k * 2 + 1] * mapH
                        if (sx < minX) minX = sx
                        if (sx > maxX) maxX = sx
                        if (sy < minY) minY = sy
                        if (sy > maxY) maxY = sy
                        if (k == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                    }
                    if (maxX < 0f || minX > size.width || maxY < 0f || minY > size.height) {
                        return@forEach
                    }
                    path.close()
                    drawPath(path, fillColor)
                    drawPath(path, borderColor, style = Stroke(width = borderWidth))
                }
            }

            // Draw all 17 regional hub dots
            WorldHubNodes.forEach { hub ->
                val (hx, hy) = projectNormalized(hub.latitude, hub.longitude)
                val pos = Offset(originX + hx * mapW, originY + hy * mapH)
                if (pos.x !in -20f..(size.width + 20f) || pos.y !in -20f..(size.height + 20f)) return@forEach

                val isSelected = hub.code == inspectedCountryCode
                val isActive = hub.code == activeCountryCode
                val dotColor = when {
                    isActive -> beaconColor
                    isSelected -> NetconColors.CyberCyan
                    else -> NetconColors.TextSecondary
                }

                if (!isActive) {
                    drawCircle(
                        color = dotColor.copy(alpha = if (isSelected) 0.30f else 0.16f),
                        radius = if (isSelected) 9.dp.toPx() else 5.5.dp.toPx(),
                        center = pos,
                    )
                    drawCircle(
                        color = dotColor,
                        radius = if (isSelected) 3.8.dp.toPx() else 2.6.dp.toPx(),
                        center = pos,
                    )
                }
            }
        }

        // Lightweight animated beacon & tunnel arc overlay
        if (activeLat != null && activeLon != null) {
            AnimatedBeaconOverlay(
                activeLat = activeLat,
                activeLon = activeLon,
                connected = connected,
                connecting = connecting,
                beaconColor = beaconColor,
                zoom = zoom,
                centerX = centerX,
                centerY = centerY,
            )
        }
    }
}

@Composable
private fun AnimatedBeaconOverlay(
    activeLat: Double,
    activeLon: Double,
    connected: Boolean,
    connecting: Boolean,
    beaconColor: Color,
    zoom: Float,
    centerX: Float,
    centerY: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beaconPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseProgress",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val mapW = size.width * zoom
        val mapH = mapW / 1.607f
        val originX = size.width / 2f - centerX * mapW
        val originY = size.height / 2f - centerY * mapH

        if (connected || connecting) {
            val (ax, ay) = projectNormalized(activeLat, activeLon)
            val activePos = Offset(originX + ax * mapW, originY + ay * mapH)
            val originLon = if (activeLon > 20.0) activeLon - 55.0 else activeLon + 55.0
            val originLat = (activeLat * 0.5 + 25.0).coerceIn(-40.0, 55.0)
            val (ox, oy) = projectNormalized(originLat, originLon)
            val relayStart = Offset(originX + ox * mapW, originY + oy * mapH)
            val mid = Offset(
                (relayStart.x + activePos.x) / 2f,
                minOf(relayStart.y, activePos.y) - 36.dp.toPx(),
            )
            val arcPath = Path().apply {
                moveTo(relayStart.x, relayStart.y)
                quadraticTo(mid.x, mid.y, activePos.x, activePos.y)
            }
            drawPath(
                arcPath,
                color = beaconColor.copy(alpha = 0.55f),
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), phase = -pulse * 36f),
                ),
            )
        }
        drawActiveBeacon(activeLat, activeLon, originX, originY, mapW, mapH, beaconColor, pulse)
    }
}

private fun DrawScope.drawActiveBeacon(
    lat: Double,
    lon: Double,
    originX: Float,
    originY: Float,
    mapW: Float,
    mapH: Float,
    beaconColor: Color,
    pulse: Float,
) {
    val (nx, ny) = projectNormalized(lat, lon)
    val center = Offset(originX + nx * mapW, originY + ny * mapH)

    // Expanding radar pulse ring
    val pulseRadius = 6.dp.toPx() + pulse * 18.dp.toPx()
    drawCircle(
        color = beaconColor.copy(alpha = (1f - pulse) * 0.55f),
        radius = pulseRadius,
        center = center,
        style = Stroke(width = 1.6.dp.toPx()),
    )
    // Inner glow
    drawCircle(
        color = beaconColor.copy(alpha = 0.25f),
        radius = 10.dp.toPx(),
        center = center,
    )
    // Bright outer ring + dark core pin
    drawCircle(
        color = beaconColor,
        radius = 5.2.dp.toPx(),
        center = center,
    )
    drawCircle(
        color = NetconColors.DeepInk,
        radius = 2.2.dp.toPx(),
        center = center,
    )
}

@Composable
private fun MapHudButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(NetconColors.DeepInk.copy(alpha = 0.85f))
            .border(1.dp, NetconColors.BevelHighlight.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NetconColors.CopperLight,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun FullProtonMapDialog(
    countries: List<VectorCountry>,
    serverName: String,
    initialLocationName: String,
    initialLat: Double?,
    initialLon: Double?,
    vpnState: String,
    onDismiss: () -> Unit,
    onBrowseCountryServers: (String) -> Unit,
    onSaveCoordinates: (String, Double, Double) -> Unit,
) {
    var pickedLat by remember { mutableStateOf(initialLat) }
    var pickedLon by remember { mutableStateOf(initialLon) }
    var pickedHub by remember { mutableStateOf<MapHubNode?>(null) }

    var targetZoom by remember { mutableFloatStateOf(1.35f) }
    var targetCenterX by remember {
        mutableFloatStateOf(
            if (initialLat != null && initialLon != null) projectNormalized(initialLat, initialLon).first else 0.50f,
        )
    }
    var targetCenterY by remember {
        mutableFloatStateOf(
            if (initialLat != null && initialLon != null) projectNormalized(initialLat, initialLon).second else 0.46f,
        )
    }

    val animZoom by animateFloatAsState(targetValue = targetZoom, label = "fullZoom")
    val animCenterX by animateFloatAsState(targetValue = targetCenterX, label = "fullCenterX")
    val animCenterY by animateFloatAsState(targetValue = targetCenterY, label = "fullCenterY")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NetconColors.DeepInk)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "WORLD SERVER MAP",
                        color = NetconColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Pinch to zoom · Tap a country hub or tap anywhere to pin coordinates",
                        color = NetconColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close world map",
                        tint = NetconColors.TextPrimary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(listOf(NetconColors.OceanDeep, NetconColors.OceanDark)),
                    )
                    .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(12.dp))
                    .clipToBounds(),
            ) {
                WorldVectorCanvas(
                    countries = countries,
                    activeCountryCode = pickedHub?.code ?: inferCountryCode(initialLocationName, pickedLat, pickedLon),
                    inspectedCountryCode = pickedHub?.code,
                    activeLat = pickedLat,
                    activeLon = pickedLon,
                    vpnState = vpnState,
                    zoom = animZoom,
                    centerX = animCenterX,
                    centerY = animCenterY,
                    onTransform = { zoomFactor, panDelta, canvasSize ->
                        val newZoom = (targetZoom * zoomFactor).coerceIn(1.0f, 8.0f)
                        val mapScale = canvasSize.width.toFloat().coerceAtLeast(1f) * newZoom
                        val mapHeight = mapScale / 1.607f
                        targetZoom = newZoom
                        targetCenterX = (targetCenterX - panDelta.x / mapScale).coerceIn(0.05f, 0.95f)
                        targetCenterY = (targetCenterY - panDelta.y / mapHeight).coerceIn(0.08f, 0.92f)
                    },
                    onHubTapped = { hub ->
                        pickedHub = hub
                        pickedLat = hub.latitude
                        pickedLon = hub.longitude
                        val (nx, ny) = projectNormalized(hub.latitude, hub.longitude)
                        targetCenterX = nx
                        targetCenterY = ny
                    },
                    onBackgroundTapped = { (lat, lon) ->
                        pickedHub = null
                        pickedLat = lat
                        pickedLon = lon
                    },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MapHudButton(
                        icon = Icons.Filled.Public,
                        contentDescription = "Fit world",
                        onClick = {
                            targetZoom = 1.05f
                            targetCenterX = 0.50f
                            targetCenterY = 0.47f
                        },
                    )
                    MapHudButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Zoom in",
                        onClick = { targetZoom = (targetZoom * 1.45f).coerceAtMost(8.0f) },
                    )
                    MapHudButton(
                        icon = Icons.Filled.Remove,
                        contentDescription = "Zoom out",
                        onClick = { targetZoom = (targetZoom / 1.45f).coerceAtLeast(1.0f) },
                    )
                }
            }

            // Bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NetconColors.PanelSurface)
                    .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pickedHub?.let { "${it.countryName} (${it.code})" }
                            ?: serverName.ifBlank { "Custom Map Pin" },
                        color = NetconColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val coordsLabel = if (pickedLat != null && pickedLon != null) {
                        "%.4f°, %.4f°".format(pickedLat, pickedLon)
                    } else {
                        "Tap anywhere on map to pin"
                    }
                    Text(
                        coordsLabel,
                        color = NetconColors.TextSecondary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 10.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pickedHub?.let { hub ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(NetconColors.PanelElevated)
                                .border(1.dp, NetconColors.Copper, RoundedCornerShape(7.dp))
                                .clickable { onBrowseCountryServers(hub.code) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "BROWSE ${hub.code}",
                                color = NetconColors.CopperLight,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (pickedLat != null && pickedLon != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.verticalGradient(listOf(NetconColors.CopperLight, NetconColors.Copper)),
                                )
                                .clickable {
                                    val label = pickedHub?.let { "${it.cityLabel}, ${it.countryName}" }
                                        ?: initialLocationName.ifBlank { "%.2f°, %.2f°".format(pickedLat, pickedLon) }
                                    onSaveCoordinates(label, pickedLat!!, pickedLon!!)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "USE PIN",
                                color = Color(0xFF1D0E05),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
