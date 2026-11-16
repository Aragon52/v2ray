package com.artesys.netcon

import android.content.Context
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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

private const val MIN_ZOOM = 1.0f
private const val MAX_ZOOM = 18.0f

private data class VectorCountry(
    val isoCode: String,
    val name: String,
    val rings: List<FloatArray>,
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

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return (r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))).roundToInt()
}

private fun clampCenterX(cx: Float, zoom: Float): Float {
    val pad = (0.38f / zoom).coerceIn(0.01f, 0.45f)
    return cx.coerceIn(pad, 1f - pad)
}

private fun clampCenterY(cy: Float, zoom: Float): Float {
    val pad = (0.38f / zoom).coerceIn(0.015f, 0.45f)
    return cy.coerceIn(pad, 1f - pad)
}

private fun applyFocalTransform(
    currentZoom: Float,
    currentCenterX: Float,
    currentCenterY: Float,
    zoomFactor: Float,
    panDelta: Offset,
    centroid: Offset,
    canvasSize: IntSize,
): Triple<Float, Float, Float> {
    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
    val newZoom = (currentZoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)

    val oldMapW = w * currentZoom
    val oldMapH = oldMapW / 1.607f
    val oldOriginX = w / 2f - currentCenterX * oldMapW
    val oldOriginY = h / 2f - currentCenterY * oldMapH

    // Normalized point under gesture centroid before transform
    val focusNx = (centroid.x - oldOriginX) / oldMapW
    val focusNy = (centroid.y - oldOriginY) / oldMapH

    val newMapW = w * newZoom
    val newMapH = newMapW / 1.607f

    // Keep focusNx/focusNy under centroid + panDelta
    val targetScreenX = centroid.x + panDelta.x
    val targetScreenY = centroid.y + panDelta.y
    val newOriginX = targetScreenX - focusNx * newMapW
    val newOriginY = targetScreenY - focusNy * newMapH

    val nextCx = clampCenterX((w / 2f - newOriginX) / newMapW, newZoom)
    val nextCy = clampCenterY((h / 2f - newOriginY) / newMapH, newZoom)
    return Triple(newZoom, nextCx, nextCy)
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
    userLatitude: Double?,
    userLongitude: Double?,
    userLocationSource: String,
    userLocationBusy: Boolean,
    vpnState: String,
    onBrowseCountryServers: (String) -> Unit,
    onRequestUserLocation: () -> Unit,
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

    var targetZoom by rememberSaveable { mutableFloatStateOf(1.65f) }
    var targetCenterX by rememberSaveable { mutableFloatStateOf(0.55f) }
    var targetCenterY by rememberSaveable { mutableFloatStateOf(0.45f) }

    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            val (nx, ny) = projectNormalized(latitude, longitude)
            targetCenterX = clampCenterX(nx, targetZoom.coerceAtLeast(2.2f))
            targetCenterY = clampCenterY(ny, targetZoom.coerceAtLeast(2.2f))
            if (targetZoom < 2.0f) targetZoom = 2.2f
        } else {
            targetCenterX = 0.50f
            targetCenterY = 0.45f
            targetZoom = 1.15f
        }
    }

    val animZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.86f),
        label = "mapZoom",
    )
    val animCenterX by animateFloatAsState(
        targetValue = targetCenterX,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.86f),
        label = "mapCenterX",
    )
    val animCenterY by animateFloatAsState(
        targetValue = targetCenterY,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.86f),
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

        // Interactive World Map Canvas
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
                userPinLat = userLatitude,
                userPinLon = userLongitude,
                vpnState = vpnState,
                zoom = animZoom,
                centerX = animCenterX,
                centerY = animCenterY,
                onTransform = { zoomFactor, panDelta, centroid, canvasSize ->
                    val (nz, ncx, ncy) = applyFocalTransform(
                        targetZoom,
                        targetCenterX,
                        targetCenterY,
                        zoomFactor,
                        panDelta,
                        centroid,
                        canvasSize,
                    )
                    targetZoom = nz
                    targetCenterX = ncx
                    targetCenterY = ncy
                },
                onDoubleTap = { (lat, lon) ->
                    val (nx, ny) = projectNormalized(lat, lon)
                    val nextZ = if (targetZoom >= 12.0f) 1.5f else (targetZoom * 2.25f).coerceAtMost(MAX_ZOOM)
                    targetZoom = nextZ
                    targetCenterX = clampCenterX(nx, nextZ)
                    targetCenterY = clampCenterY(ny, nextZ)
                },
                onHubTapped = { hub ->
                    selectedHub = if (selectedHub?.code == hub.code) null else hub
                    val (nx, ny) = projectNormalized(hub.latitude, hub.longitude)
                    val nextZ = targetZoom.coerceAtLeast(3.2f)
                    targetZoom = nextZ
                    targetCenterX = clampCenterX(nx, nextZ)
                    targetCenterY = clampCenterY(ny, nextZ)
                },
                onBackgroundTapped = {
                    selectedHub = null
                },
            )

            // Top-left status, zoom magnification, & tunnel distance HUD chip
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetconColors.DeepInk.copy(alpha = 0.86f))
                    .border(1.dp, NetconColors.BevelBorder.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            when (vpnState) {
                                "connected" -> NetconColors.Online
                                "connecting" -> NetconColors.CopperLight
                                else -> NetconColors.Copper
                            },
                            CircleShape,
                        ),
                )
                val distKm = if (latitude != null && longitude != null && userLatitude != null && userLongitude != null) {
                    " · ${haversineKm(userLatitude, userLongitude, latitude, longitude)} KM"
                } else {
                    ""
                }
                val modeTag = when (vpnState) {
                    "connected" -> "ACTIVE TUNNEL$distKm"
                    "connecting" -> "HANDSHAKE…"
                    else -> "${WorldHubNodes.size} REGIONS"
                }
                Text(
                    "$modeTag · ${"%.1f".format(animZoom)}×",
                    color = NetconColors.TextPrimary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Top-right Expand button
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                MapHudButton(
                    icon = Icons.Filled.OpenInFull,
                    contentDescription = "Open fullscreen world map",
                    onClick = { showFullMap = true },
                )
            }

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
                            val z = if (targetZoom < 3.5f) 4.2f else (targetZoom * 1.6f).coerceAtMost(MAX_ZOOM)
                            targetZoom = z
                            targetCenterX = clampCenterX(nx, z)
                            targetCenterY = clampCenterY(ny, z)
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
                    onClick = {
                        val nextZ = (targetZoom * 1.55f).coerceAtMost(MAX_ZOOM)
                        targetZoom = nextZ
                        targetCenterX = clampCenterX(targetCenterX, nextZ)
                        targetCenterY = clampCenterY(targetCenterY, nextZ)
                    },
                )
                MapHudButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Zoom out",
                    modifier = Modifier.testTag("map_zoom_out_button"),
                    onClick = {
                        val nextZ = (targetZoom / 1.55f).coerceAtLeast(MIN_ZOOM)
                        targetZoom = nextZ
                        targetCenterX = clampCenterX(targetCenterX, nextZ)
                        targetCenterY = clampCenterY(targetCenterY, nextZ)
                    },
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
                                "${hub.cityLabel} · %.2f°, %.2f°".format(hub.latitude, hub.longitude),
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

        // Quick Zoom Level Presets Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "1× WORLD" to 1.0f,
                "3× REGION" to 3.0f,
                "7× METRO" to 7.0f,
                "14× DETAIL" to 14.0f,
            ).forEach { (label, presetZoom) ->
                val isCurrent = kotlin.math.abs(targetZoom - presetZoom) < 0.85f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrent) Color(0xFF162435) else Color(0xFF0A1017))
                        .border(
                            1.dp,
                            if (isCurrent) NetconColors.CyberCyan else Color(0xFF47525E),
                            RoundedCornerShape(6.dp),
                        )
                        .clickable {
                            targetZoom = presetZoom
                            if (presetZoom == 1.0f) {
                                targetCenterX = 0.50f
                                targetCenterY = 0.48f
                            } else if (latitude != null && longitude != null) {
                                val (nx, ny) = projectNormalized(latitude, longitude)
                                targetCenterX = clampCenterX(nx, presetZoom)
                                targetCenterY = clampCenterY(ny, presetZoom)
                            }
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (isCurrent) NetconColors.CyberCyan else NetconColors.TextSecondary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Active server node readout bar
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
                    locationName.ifBlank { "Server Region" },
                    color = NetconColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val coordText = if (latitude != null && longitude != null) {
                    "%.4f°, %.4f° · Tap any globe node to browse servers".format(latitude, longitude)
                } else {
                    "Select a server profile or tap a regional node on the map"
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
        }

        // Automatic User Location Bar (GPS / Network Permission)
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
                    "YOUR LOCATION (AUTO GPS / NETWORK)",
                    color = NetconColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val userLocSummary = when {
                    userLocationBusy -> userLocationSource.ifBlank { "Acquiring device location…" }
                    userLatitude != null && userLongitude != null -> {
                        val prefix = userLocationSource.ifBlank { "Auto-detected" }
                        "$prefix · %.4f°, %.4f°".format(userLatitude, userLongitude)
                    }
                    userLocationSource.isNotBlank() -> userLocationSource
                    else -> "Tap AUTO LOCATE to grant location permission"
                }
                Text(
                    userLocSummary,
                    color = NetconColors.CyberCyan,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .testTag("user_location_map_button")
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetconColors.PanelElevated)
                    .border(1.dp, NetconColors.CyberCyan, RoundedCornerShape(6.dp))
                    .clickable {
                        onRequestUserLocation()
                        if (userLatitude != null && userLongitude != null) {
                            val (nx, ny) = projectNormalized(userLatitude, userLongitude)
                            val z = targetZoom.coerceAtLeast(3.2f)
                            targetZoom = z
                            targetCenterX = clampCenterX(nx, z)
                            targetCenterY = clampCenterY(ny, z)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    when {
                        userLocationBusy -> "LOCATING…"
                        userLatitude != null && userLongitude != null -> "REFRESH GPS"
                        else -> "AUTO LOCATE"
                    },
                    color = NetconColors.CyberCyan,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (showFullMap) {
        FullProtonMapDialog(
            countries = countries,
            serverName = serverName,
            initialLocationName = locationName,
            initialLat = latitude,
            initialLon = longitude,
            userPinLat = userLatitude,
            userPinLon = userLongitude,
            vpnState = vpnState,
            onDismiss = { showFullMap = false },
            onBrowseCountryServers = { code ->
                showFullMap = false
                onBrowseCountryServers(code)
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
    onTransform: (zoomFactor: Float, panDelta: Offset, centroid: Offset, size: IntSize) -> Unit,
    onDoubleTap: (Pair<Double, Double>) -> Unit = {},
    onHubTapped: (MapHubNode) -> Unit,
    onBackgroundTapped: (Pair<Double, Double>) -> Unit,
    userPinLat: Double? = null,
    userPinLon: Double? = null,
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

    val tapThresholdPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, gestureZoom, _ ->
                    onTransform(gestureZoom, pan, centroid, canvasSize)
                }
            }
            .pointerInput(zoom, centerX, centerY, canvasSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTapGestures
                        val mapW = canvasSize.width.toFloat() * zoom
                        val mapH = mapW / 1.607f
                        val originX = canvasSize.width / 2f - centerX * mapW
                        val originY = canvasSize.height / 2f - centerY * mapH
                        val nx = ((tapOffset.x - originX) / mapW).coerceIn(0f, 1f)
                        val ny = ((tapOffset.y - originY) / mapH).coerceIn(0f, 1f)
                        onDoubleTap(unprojectNormalized(nx, ny))
                    },
                    onTap = { tapOffset ->
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
                    },
                )
            },
    ) {
        // Base vector map layer with adaptive multi-resolution coordinate graticules
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mapW = size.width * zoom
            val mapH = mapW / 1.607f
            val originX = size.width / 2f - centerX * mapW
            val originY = size.height / 2f - centerY * mapH

            val majorGridPaint = NetconColors.BevelBorder.copy(alpha = 0.25f)
            val minorGridPaint = NetconColors.CyberCyan.copy(alpha = 0.11f)
            val microGridPaint = NetconColors.CyberCyan.copy(alpha = 0.07f)

            val lonStep = when {
                zoom >= 7.5f -> 2
                zoom >= 2.8f -> 10
                else -> 30
            }
            for (lonDeg in -180..180 step lonStep) {
                val (gx, _) = projectNormalized(0.0, lonDeg.toDouble())
                val sx = originX + gx * mapW
                if (sx in -2f..size.width + 2f) {
                    val isMajor = lonDeg % 30 == 0
                    val isMinor = lonDeg % 10 == 0
                    drawLine(
                        color = if (isMajor) majorGridPaint else if (isMinor) minorGridPaint else microGridPaint,
                        start = Offset(sx, 0f),
                        end = Offset(sx, size.height),
                        strokeWidth = if (isMajor) 0.7.dp.toPx() else 0.45.dp.toPx(),
                    )
                }
            }

            val latStep = when {
                zoom >= 7.5f -> 2
                zoom >= 2.8f -> 10
                else -> 30
            }
            for (latDeg in -54..78 step latStep) {
                val (_, gy) = projectNormalized(latDeg.toDouble(), 0.0)
                val sy = originY + gy * mapH
                if (sy in -2f..size.height + 2f) {
                    val isMajor = latDeg % 30 == 0
                    val isMinor = latDeg % 10 == 0
                    drawLine(
                        color = if (isMajor) majorGridPaint else if (isMinor) minorGridPaint else microGridPaint,
                        start = Offset(0f, sy),
                        end = Offset(size.width, sy),
                        strokeWidth = if (isMajor) 0.7.dp.toPx() else 0.45.dp.toPx(),
                    )
                }
            }

            val strokeThin = 0.75.dp.toPx()
            val strokeHighlight = 1.45.dp.toPx()

            countries.forEach { country ->
                val isActiveCountry = activeCountryCode != null && country.isoCode == activeCountryCode
                val isInspectedCountry = inspectedCountryCode != null && country.isoCode == inspectedCountryCode
                val isHubCountry = country.isoCode in HubCodeSet

                val fillColor = when {
                    isActiveCountry -> beaconColor.copy(alpha = if (connected) 0.32f else 0.35f)
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

            // Draw all 17 regional hub pins with adaptive detail at higher zoom
            WorldHubNodes.forEach { hub ->
                val (hx, hy) = projectNormalized(hub.latitude, hub.longitude)
                val pos = Offset(originX + hx * mapW, originY + hy * mapH)
                if (pos.x !in -24f..(size.width + 24f) || pos.y !in -24f..(size.height + 24f)) return@forEach

                val isSelected = hub.code == inspectedCountryCode
                val isActive = hub.code == activeCountryCode
                val dotColor = when {
                    isActive -> beaconColor
                    isSelected -> NetconColors.CyberCyan
                    else -> NetconColors.TextSecondary
                }

                if (!isActive) {
                    val outerRadius = when {
                        isSelected -> 10.dp.toPx()
                        zoom >= 4.0f -> 7.5.dp.toPx()
                        else -> 5.5.dp.toPx()
                    }
                    val innerRadius = when {
                        isSelected -> 4.2.dp.toPx()
                        zoom >= 4.0f -> 3.4.dp.toPx()
                        else -> 2.6.dp.toPx()
                    }
                    drawCircle(
                        color = dotColor.copy(alpha = if (isSelected) 0.34f else 0.18f),
                        radius = outerRadius,
                        center = pos,
                    )
                    if (zoom >= 3.0f || isSelected) {
                        drawCircle(
                            color = dotColor.copy(alpha = 0.65f),
                            radius = outerRadius * 0.72f,
                            center = pos,
                            style = Stroke(width = 0.9.dp.toPx()),
                        )
                    }
                    drawCircle(
                        color = dotColor,
                        radius = innerRadius,
                        center = pos,
                    )
                }
            }
        }

        // Animated Connection Tunnel & Beacon Overlay
        if (activeLat != null && activeLon != null) {
            AnimatedBeaconOverlay(
                activeLat = activeLat,
                activeLon = activeLon,
                userPinLat = userPinLat,
                userPinLon = userPinLon,
                connected = connected,
                connecting = connecting,
                beaconColor = beaconColor,
                zoom = zoom,
                centerX = centerX,
                centerY = centerY,
            )
        } else if (userPinLat != null && userPinLon != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val mapW = size.width * zoom
                val mapH = mapW / 1.607f
                val originX = size.width / 2f - centerX * mapW
                val originY = size.height / 2f - centerY * mapH
                val (nx, ny) = projectNormalized(userPinLat, userPinLon)
                val point = Offset(originX + nx * mapW, originY + ny * mapH)
                if (point.x in -16f..size.width + 16f && point.y in -16f..size.height + 16f) {
                    drawCircle(Color(0xFF06131A), 8.dp.toPx(), point)
                    drawCircle(NetconColors.CyberCyan, 5.dp.toPx(), point)
                    drawCircle(Color.White, 2.dp.toPx(), point)
                }
            }
        }
    }
}

private fun quadraticBezierPoint(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    return Offset(
        x = uu * p0.x + 2f * u * t * p1.x + tt * p2.x,
        y = uu * p0.y + 2f * u * t * p1.y + tt * p2.y,
    )
}

@Composable
private fun AnimatedBeaconOverlay(
    activeLat: Double,
    activeLon: Double,
    userPinLat: Double?,
    userPinLon: Double?,
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
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseProgress",
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lockRingSweep",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val mapW = size.width * zoom
        val mapH = mapW / 1.607f
        val originX = size.width / 2f - centerX * mapW
        val originY = size.height / 2f - centerY * mapH

        val (ax, ay) = projectNormalized(activeLat, activeLon)
        val activePos = Offset(originX + ax * mapW, originY + ay * mapH)

        val originLat = userPinLat ?: (activeLat * 0.5 + 22.0).coerceIn(-40.0, 55.0)
        val originLon = userPinLon ?: if (activeLon > 20.0) activeLon - 52.0 else activeLon + 52.0
        val (ox, oy) = projectNormalized(originLat, originLon)
        val relayStart = Offset(originX + ox * mapW, originY + oy * mapH)

        // Draw user origin pin & pulse if user has pinned their location or tunnel is active
        if (userPinLat != null && userPinLon != null || connected || connecting) {
            val originPulseRadius = 5.dp.toPx() + ((pulse + 0.5f) % 1f) * 14.dp.toPx()
            if (connected || connecting) {
                drawCircle(
                    color = NetconColors.CyberCyan.copy(alpha = (1f - ((pulse + 0.5f) % 1f)) * 0.5f),
                    radius = originPulseRadius,
                    center = relayStart,
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }
            drawCircle(Color(0xFF06131A), 7.5.dp.toPx(), relayStart)
            drawCircle(NetconColors.CyberCyan, 4.8.dp.toPx(), relayStart)
            drawCircle(Color.White, 2.0.dp.toPx(), relayStart)
        }

        if (connected || connecting) {
            val spanPx = hypot(activePos.x - relayStart.x, activePos.y - relayStart.y)
            val liftPx = (spanPx * 0.24f).coerceIn(24.dp.toPx(), 84.dp.toPx())
            val controlPt = Offset(
                x = (relayStart.x + activePos.x) / 2f,
                y = minOf(relayStart.y, activePos.y) - liftPx,
            )
            val arcPath = Path().apply {
                moveTo(relayStart.x, relayStart.y)
                quadraticTo(controlPt.x, controlPt.y, activePos.x, activePos.y)
            }

            // 1. Atmospheric outer glow corridor
            drawPath(
                path = arcPath,
                color = beaconColor.copy(alpha = if (connected) 0.18f else 0.12f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )

            // 2. Continuous core carrier line
            drawPath(
                path = arcPath,
                color = NetconColors.CyberCyan.copy(alpha = 0.28f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )

            // 3. Animated dashed encrypted stream
            drawPath(
                path = arcPath,
                color = beaconColor.copy(alpha = 0.82f),
                style = Stroke(
                    width = 2.0.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = -pulse * 40f),
                ),
            )

            // 4. Traveling uplink data packets along Bezier arc
            val packetOffsets = if (connected) floatArrayOf(0f, 0.33f, 0.66f) else floatArrayOf(0f, 0.5f)
            for (phaseShift in packetOffsets) {
                val t = (pulse + phaseShift) % 1f
                val pt = quadraticBezierPoint(relayStart, controlPt, activePos, t)
                drawCircle(
                    color = beaconColor.copy(alpha = 0.36f),
                    radius = 6.5.dp.toPx(),
                    center = pt,
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.4.dp.toPx(),
                    center = pt,
                )
            }

            // 5. Return downlink cyan telemetry packet when connected
            if (connected) {
                val downT = 1f - ((pulse * 0.9f + 0.2f) % 1f)
                val downPt = quadraticBezierPoint(relayStart, controlPt, activePos, downT)
                drawCircle(
                    color = NetconColors.CyberCyan.copy(alpha = 0.38f),
                    radius = 5.5.dp.toPx(),
                    center = downPt,
                )
                drawCircle(
                    color = NetconColors.CyberCyan,
                    radius = 2.2.dp.toPx(),
                    center = downPt,
                )
            }
        }

        drawActiveBeacon(
            center = activePos,
            beaconColor = beaconColor,
            connected = connected,
            connecting = connecting,
            pulse = pulse,
            sweepAngle = sweepAngle,
        )
    }
}

private fun DrawScope.drawActiveBeacon(
    center: Offset,
    beaconColor: Color,
    connected: Boolean,
    connecting: Boolean,
    pulse: Float,
    sweepAngle: Float,
) {
    // Triple concentric radar waves when connected/connecting, single wave when idle
    val wavePhases = if (connected || connecting) floatArrayOf(0f, 0.33f, 0.66f) else floatArrayOf(0f)
    val maxWaveRadius = if (connected) 26.dp.toPx() else 18.dp.toPx()
    for (shift in wavePhases) {
        val p = (pulse + shift) % 1f
        val radius = 6.dp.toPx() + p * maxWaveRadius
        drawCircle(
            color = beaconColor.copy(alpha = (1f - p) * (if (connected) 0.62f else 0.45f)),
            radius = radius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }

    // Rotating tactical lock ring when connected or connecting
    if (connected || connecting) {
        val lockRadius = 11.5.dp.toPx()
        val arcTopLeft = Offset(center.x - lockRadius, center.y - lockRadius)
        val arcSize = Size(lockRadius * 2f, lockRadius * 2f)
        rotate(degrees = sweepAngle, pivot = center) {
            drawArc(
                color = beaconColor.copy(alpha = 0.85f),
                startAngle = 0f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = NetconColors.CyberCyan.copy(alpha = 0.85f),
                startAngle = 180f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }

    // Inner glow halo
    drawCircle(
        color = beaconColor.copy(alpha = 0.28f),
        radius = 10.dp.toPx(),
        center = center,
    )
    // Bright outer ring + dark core pin
    drawCircle(
        color = beaconColor,
        radius = 5.5.dp.toPx(),
        center = center,
    )
    drawCircle(
        color = NetconColors.DeepInk,
        radius = 2.3.dp.toPx(),
        center = center,
    )
}

@Composable
private fun MapHudButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (isActive) Color(0xFF102A3A) else NetconColors.DeepInk.copy(alpha = 0.85f),
            )
            .border(
                1.dp,
                if (isActive) NetconColors.CyberCyan else NetconColors.BevelHighlight.copy(alpha = 0.7f),
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) NetconColors.CyberCyan else NetconColors.CopperLight,
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
    userPinLat: Double? = null,
    userPinLon: Double? = null,
    vpnState: String,
    onDismiss: () -> Unit,
    onBrowseCountryServers: (String) -> Unit,
) {
    var pickedHub by remember { mutableStateOf<MapHubNode?>(null) }

    var targetZoom by remember { mutableFloatStateOf(if (initialLat != null) 2.6f else 1.4f) }
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
                        "Pinch or double-tap up to 18× · Tap any country hub to browse regional servers",
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

            // Quick Zoom Presets bar in Fullscreen Map
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "1× WORLD" to 1.05f,
                    "3× REGION" to 3.2f,
                    "8× CITY" to 8.0f,
                    "16× DETAIL" to 16.0f,
                ).forEach { (label, presetZ) ->
                    val active = kotlin.math.abs(targetZoom - presetZ) < 0.9f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF15293B) else NetconColors.PanelSurface)
                            .border(
                                1.dp,
                                if (active) NetconColors.CyberCyan else NetconColors.BevelBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable {
                                targetZoom = presetZ
                                targetCenterX = clampCenterX(targetCenterX, presetZ)
                                targetCenterY = clampCenterY(targetCenterY, presetZ)
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (active) NetconColors.CyberCyan else NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
                    activeCountryCode = inferCountryCode(initialLocationName, initialLat, initialLon),
                    inspectedCountryCode = pickedHub?.code,
                    activeLat = initialLat,
                    activeLon = initialLon,
                    userPinLat = userPinLat,
                    userPinLon = userPinLon,
                    vpnState = vpnState,
                    zoom = animZoom,
                    centerX = animCenterX,
                    centerY = animCenterY,
                    onTransform = { zoomFactor, panDelta, centroid, canvasSize ->
                        val (nz, ncx, ncy) = applyFocalTransform(
                            targetZoom,
                            targetCenterX,
                            targetCenterY,
                            zoomFactor,
                            panDelta,
                            centroid,
                            canvasSize,
                        )
                        targetZoom = nz
                        targetCenterX = ncx
                        targetCenterY = ncy
                    },
                    onDoubleTap = { (lat, lon) ->
                        val (nx, ny) = projectNormalized(lat, lon)
                        val nextZ = if (targetZoom >= 13.0f) 1.4f else (targetZoom * 2.25f).coerceAtMost(MAX_ZOOM)
                        targetZoom = nextZ
                        targetCenterX = clampCenterX(nx, nextZ)
                        targetCenterY = clampCenterY(ny, nextZ)
                    },
                    onHubTapped = { hub ->
                        pickedHub = hub
                        val (nx, ny) = projectNormalized(hub.latitude, hub.longitude)
                        val nextZ = targetZoom.coerceAtLeast(3.6f)
                        targetZoom = nextZ
                        targetCenterX = clampCenterX(nx, nextZ)
                        targetCenterY = clampCenterY(ny, nextZ)
                    },
                    onBackgroundTapped = {
                        pickedHub = null
                    },
                )

                // Top-left Live Zoom Readout
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NetconColors.DeepInk.copy(alpha = 0.88f))
                        .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text(
                        "ZOOM ${"%.1f".format(animZoom)}× · MAX 18.0×",
                        color = NetconColors.CyberCyan,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

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
                        onClick = {
                            val nextZ = (targetZoom * 1.55f).coerceAtMost(MAX_ZOOM)
                            targetZoom = nextZ
                            targetCenterX = clampCenterX(targetCenterX, nextZ)
                            targetCenterY = clampCenterY(targetCenterY, nextZ)
                        },
                    )
                    MapHudButton(
                        icon = Icons.Filled.Remove,
                        contentDescription = "Zoom out",
                        onClick = {
                            val nextZ = (targetZoom / 1.55f).coerceAtLeast(MIN_ZOOM)
                            targetZoom = nextZ
                            targetCenterX = clampCenterX(targetCenterX, nextZ)
                            targetCenterY = clampCenterY(targetCenterY, nextZ)
                        },
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
                            ?: serverName.ifBlank { "Active Server Node" },
                        color = NetconColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val displayLat = pickedHub?.latitude ?: initialLat
                    val displayLon = pickedHub?.longitude ?: initialLon
                    Text(
                        if (displayLat != null && displayLon != null) {
                            "%.4f°, %.4f°".format(displayLat, displayLon)
                        } else {
                            "Tap a regional hub pin to browse servers"
                        },
                        color = NetconColors.CyberCyan,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 10.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pickedHub?.let { hub ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.verticalGradient(listOf(NetconColors.CopperLight, NetconColors.Copper)),
                                )
                                .clickable { onBrowseCountryServers(hub.code) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "BROWSE ${hub.code}",
                                color = Color(0xFF1D0E05),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(NetconColors.PanelElevated)
                            .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(7.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "CLOSE",
                            color = NetconColors.TextPrimary,
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
