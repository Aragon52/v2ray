package com.artesys.netcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private enum class DashboardTab(val label: String, val tag: String) {
    Connection("CONNECT", "tab_connect"),
    Settings("CONFIG", "tab_config"),
    Info("INFO", "tab_info"),
}

// Brushed-steel / burnished-copper Design #2 surface tokens
private val SilverTop = Color(0xFFE7ECF0)
private val SilverMid = Color(0xFFCAD2DA)
private val SilverBottom = Color(0xFFA7B1BC)
private val SilverBorderLight = Color(0xFFF3F6F9)
private val SilverBorderDark = Color(0xFF7D8792)
private val DarkInk = Color(0xFF1F2730)
private val DarkInkMuted = Color(0xFF47525E)
private val GoldText = Color(0xFFF5A623)
private val GoldAccent = Color(0xFFD9822B)
private val CyanGauge = Color(0xFF38BDF8)
private val EmeraldOnline = Color(0xFF10B981)
private val ServerNavyTop = Color(0xFF131D2D)
private val ServerNavyBottom = Color(0xFF0B111C)
private val SentinelTealTop = Color(0xFF0E212B)
private val SentinelTealBottom = Color(0xFF081219)

@Composable
internal fun NetconDashboard(
    serverName: String,
    protocol: String,
    serverLocation: String,
    serverLatitude: Double?,
    serverLongitude: Double?,
    onSaveLocation: (String, Double, Double) -> Unit,
    feedback: String,
    onFeedbackDismiss: () -> Unit,
    onImport: (String) -> Boolean,
    onFetchPublicNodes: suspend (String) -> NativePublicDirectory.Result,
    onRecommend: suspend (String, String, List<NativePublicDirectory.Node>) -> NativeGeminiRecommender.Recommendation,
    onSelectPublicNode: (NativePublicDirectory.Node) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBlockedProtocol: () -> Unit,
    onStartConfigured: (String, String) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
    onMeasurePing: suspend () -> Int?,
) {
    val vpnStatus by produceState(initialValue = NetconVpnService.getStatus()) {
        while (true) {
            value = NetconVpnService.getStatus()
            delay(1000)
        }
    }
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Connection) }
    var serverDialog by rememberSaveable { mutableStateOf(false) }
    var serverDialogCountry by rememberSaveable { mutableStateOf("") }
    var locationDialog by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    val defaultApiKey = remember {
        try {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } ?: ""
        } catch (_: Throwable) {
            ""
        }
    }
    var apiKey by remember { mutableStateOf(defaultApiKey) }
    var recommendationMessage by remember { mutableStateOf("") }
    var recommendationBusy by remember { mutableStateOf(false) }
    var dns by rememberSaveable { mutableStateOf("1.1.1.1") }
    var route by rememberSaveable { mutableStateOf("Bypass LAN") }
    var enabledProtocols by rememberSaveable {
        mutableStateOf(setOf("vless", "vmess", "trojan", "shadowsocks", "freedom"))
    }
    var pingMs by remember { mutableStateOf<Int?>(null) }
    var pingBusy by remember { mutableStateOf(false) }

    var sentinelPaused by rememberSaveable { mutableStateOf(false) }
    val sentinelLogs = remember {
        mutableStateListOf(
            "[CORE] Xray Core V1.8.16 · Copper Compass Engine ready",
            "[NODE] Loaded $serverName (${protocol.ifBlank { "FREEDOM" }})",
        )
    }

    fun appendSentinelLog(entry: String) {
        if (sentinelPaused) return
        sentinelLogs.add(entry)
        if (sentinelLogs.size > 24) {
            sentinelLogs.removeAt(0)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val errorState = vpnStatus.state == "error"
    val duration = "%02d:%02d:%02d".format(
        vpnStatus.elapsedSeconds / 3600,
        vpnStatus.elapsedSeconds / 60 % 60,
        vpnStatus.elapsedSeconds % 60,
    )
    val downloadMbps = vpnStatus.downloadBytesPerSecond * 8 / 1_000_000
    val uploadMbps = vpnStatus.uploadBytesPerSecond * 8 / 1_000_000

    fun refreshPing() {
        if (pingBusy) return
        pingBusy = true
        coroutineScope.launch {
            try {
                val measured = onMeasurePing()
                pingMs = measured
                appendSentinelLog(
                    if (measured != null) "[PING] Handshake latency: ${measured}ms"
                    else "[PING] Direct probe timed out",
                )
            } finally {
                pingBusy = false
            }
        }
    }

    LaunchedEffect(serverName) {
        appendSentinelLog("[NODE] Active node: $serverName")
        pingMs = onMeasurePing()
    }

    LaunchedEffect(vpnStatus.state) {
        when (vpnStatus.state) {
            "connecting" -> appendSentinelLog("[VPN] Initializing Android TUN + Xray core…")
            "connected" -> appendSentinelLog("[VPN] Tunnel active · Routing mode: $route · DNS: $dns")
            "error" -> appendSentinelLog("[ERR] ${vpnStatus.error.ifBlank { "Tunnel stopped with error" }}")
        }
    }

    fun askGemini() {
        if (recommendationBusy) return
        if (apiKey.isBlank()) {
            recommendationMessage = "Enter your Gemini API key. It stays in this screen session."
            return
        }
        if (Regex("(?i)^(vmess|vless|trojan|ss)://").containsMatchIn(purpose.trim())) {
            recommendationMessage = "Paste configuration links into the local import field; credentials are never sent to Gemini."
            return
        }
        recommendationBusy = true
        recommendationMessage = "Loading public server metadata…"
        appendSentinelLog("[AI] Querying public feed metadata for Gemini match…")
        coroutineScope.launch {
            try {
                val result = withTimeout(25_000) { onFetchPublicNodes("") }
                if (result.nodes.isEmpty()) throw IllegalStateException("No supported public servers were found in the top feed.")
                recommendationMessage = "Asking Gemini to choose from ${minOf(result.nodes.size, 30)} of ${result.nodes.size} public servers…"
                val recommendation = withTimeout(25_000) {
                    onRecommend(purpose.trim(), apiKey, result.nodes)
                }
                val selected = result.nodes.firstOrNull { it.id == recommendation.nodeId }
                    ?: throw IllegalStateException("Gemini selected a server outside the downloaded list.")
                onSelectPublicNode(selected)
                recommendationMessage = "${recommendation.reason} Imported locally; tap Main Chassis to connect."
                appendSentinelLog("[AI] Selected ${selected.name} (${selected.country})")
            } catch (error: Exception) {
                recommendationMessage = error.message ?: "Unable to get a Gemini recommendation."
                appendSentinelLog("[AI] ${recommendationMessage}")
            } finally {
                recommendationBusy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFD5DCE2), Color(0xFFBDC6CF), Color(0xFFA7B1BC)),
                ),
            ),
    ) {
        // Subtle vertical brushed aluminum ribs
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.18f),
        ) {
            val step = 5.dp.toPx()
            var x = 0f
            var idx = 0
            while (x < size.width) {
                drawLine(
                    color = if (idx % 2 == 0) Color.White else Color(0xFF6B7580),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
                idx++
            }
        }

        Image(
            painter = painterResource(R.drawable.brushed_steel),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.06f),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header plate matching Design #2
                MetallicHeaderPlate()

                // Three top navigation pills matching Design #2
                DesignTwoNavigation(
                    current = tab,
                    onSelect = { tab = it },
                )

                when (tab) {
                    DashboardTab.Connection -> ConnectionScreen(
                        importText = importText,
                        onImportTextChange = { importText = it },
                        purpose = purpose,
                        onPurposeChange = { purpose = it },
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        recommendationMessage = recommendationMessage,
                        recommendationBusy = recommendationBusy,
                        onAskGemini = ::askGemini,
                        serverName = serverName,
                        protocol = protocol,
                        serverLocation = serverLocation,
                        serverLatitude = serverLatitude,
                        serverLongitude = serverLongitude,
                        state = vpnStatus.state,
                        error = if (errorState) vpnStatus.error else feedback,
                        duration = duration,
                        pingMs = pingMs,
                        pingBusy = pingBusy,
                        onRefreshPing = ::refreshPing,
                        receivedBytes = vpnStatus.receivedBytes,
                        sentBytes = vpnStatus.sentBytes,
                        downloadMbps = downloadMbps,
                        uploadMbps = uploadMbps,
                        sentinelPaused = sentinelPaused,
                        onToggleSentinelPause = { sentinelPaused = !sentinelPaused },
                        sentinelLogs = sentinelLogs,
                        onClearSentinelLogs = { sentinelLogs.clear() },
                        onOpenServers = { countryCode ->
                            serverDialogCountry = countryCode
                            serverDialog = true
                        },
                        onSetLocation = { locationDialog = true },
                        onSaveLocation = onSaveLocation,
                        onImport = { raw ->
                            if (onImport(raw)) {
                                appendSentinelLog("[CFG] Imported local configuration")
                                importText = ""
                                serverDialog = false
                            }
                        },
                        onProtocolAllowed = {
                            val normalized = protocol.lowercase(Locale.ROOT)
                            normalized == "freedom" || enabledProtocols.contains(normalized)
                        },
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onBlockedProtocol = onBlockedProtocol,
                        onStartConfigured = { onStartConfigured(dns, route) },
                        onDismissError = onFeedbackDismiss,
                    )

                    DashboardTab.Settings -> SettingsScreen(
                        dns = dns,
                        onDnsChange = { dns = it },
                        route = route,
                        onRouteChange = { route = it },
                        protocols = enabledProtocols,
                        onProtocolsChange = { enabledProtocols = it },
                        onOpenVpnSettings = onOpenVpnSettings,
                        onExport = onExport,
                    )

                    DashboardTab.Info -> InfoScreen(
                        serverName = serverName,
                        protocol = protocol,
                        state = vpnStatus.state,
                        onOpenVpnSettings = onOpenVpnSettings,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "COPPER COMPASS ENGINE",
                        color = DarkInkMuted,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "XRAY CORE V1.8.16",
                        color = DarkInkMuted,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    if (serverDialog) {
        PublicServerDialog(
            initialCountryCode = serverDialogCountry,
            onDismiss = { serverDialog = false },
            fetchNodes = onFetchPublicNodes,
            onSelectNode = { node ->
                onSelectPublicNode(node)
                appendSentinelLog("[NODE] Switched to ${node.name} (${node.country})")
                serverDialog = false
            },
            onImport = { raw ->
                if (onImport(raw)) {
                    appendSentinelLog("[CFG] Imported local configuration")
                    serverDialog = false
                }
            },
        )
    }

    if (locationDialog) {
        LocationDialog(
            initialName = serverLocation,
            initialLatitude = serverLatitude,
            initialLongitude = serverLongitude,
            onDismiss = { locationDialog = false },
            onSave = { name, latitude, longitude ->
                onSaveLocation(name, latitude, longitude)
                appendSentinelLog("[MAP] Location pinned: $name")
                locationDialog = false
            },
        )
    }
}

@Composable
private fun MetallicHeaderPlate() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE7EBEE),
                        Color(0xFFDCE1E6),
                        Color(0xFFEBD9E0), // subtle holographic copper-rose sheen
                        Color(0xFFD1D8DF),
                    ),
                ),
            )
            .border(1.5.dp, SilverBorderLight, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        // Corner slotted screw heads matching Design #2
        ScrewHead(Modifier.align(Alignment.TopStart), angle = 35f)
        ScrewHead(Modifier.align(Alignment.TopEnd), angle = -40f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFF9D98C), Color(0xFFD08328))),
                        )
                        .border(1.dp, Color(0xFF7A4A12), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "AI V2Ray Client Shield",
                        tint = Color(0xFF231405),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    "AI V2RAY CLIENT",
                    color = Color(0xFFC87D1D),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
            }
            Text(
                "COPPER COMPASS ENGINE • XRAY CORE V1.8.16",
                color = DarkInk,
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScrewHead(modifier: Modifier = Modifier, angle: Float = 30f) {
    Canvas(modifier = modifier.size(13.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFEFF3F6), Color(0xFF8C96A0))),
            radius = r,
        )
        drawCircle(
            color = Color(0xFF5D6670),
            radius = r,
            style = Stroke(width = 0.8.dp.toPx()),
        )
        rotate(angle) {
            drawLine(
                color = Color(0xFF3E464F),
                start = Offset(center.x - r * 0.65f, center.y),
                end = Offset(center.x + r * 0.65f, center.y),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
    }
}

@Composable
private fun DesignTwoNavigation(current: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardTab.entries.forEach { item ->
            val active = item == current
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(item.tag)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) {
                            Brush.verticalGradient(listOf(Color(0xFFF3F6F9), Color(0xFFD5DCE3)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFFD8DFE6), Color(0xFFBFC8D1)))
                        },
                    )
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) SilverBorderLight else Color(0xFF9EA7B2),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (active) EmeraldOnline else Color(0xFF87929E),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    item.label,
                    color = if (active) DarkInk else DarkInkMuted,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    importText: String,
    onImportTextChange: (String) -> Unit,
    purpose: String,
    onPurposeChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    recommendationMessage: String,
    recommendationBusy: Boolean,
    onAskGemini: () -> Unit,
    serverName: String,
    protocol: String,
    serverLocation: String,
    serverLatitude: Double?,
    serverLongitude: Double?,
    state: String,
    error: String,
    duration: String,
    pingMs: Int?,
    pingBusy: Boolean,
    onRefreshPing: () -> Unit,
    receivedBytes: Long,
    sentBytes: Long,
    downloadMbps: Double,
    uploadMbps: Double,
    sentinelPaused: Boolean,
    onToggleSentinelPause: () -> Unit,
    sentinelLogs: List<String>,
    onClearSentinelLogs: () -> Unit,
    onProtocolAllowed: () -> Boolean,
    onStartConfigured: () -> Unit,
    onOpenServers: (String) -> Unit,
    onSetLocation: () -> Unit,
    onSaveLocation: (String, Double, Double) -> Unit,
    onImport: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBlockedProtocol: () -> Unit,
    onDismissError: () -> Unit,
) {
    var showToolsDrawer by rememberSaveable { mutableStateOf(false) }
    val countryCode = remember(serverLocation, serverLatitude, serverLongitude) {
        inferCountryCode(serverLocation, serverLatitude, serverLongitude) ?: "SG"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Dashboard Metrics Card matching Design #2 (3 Gauges + RX RECV / TX SENT OLED cards)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(listOf(SilverTop, SilverMid, SilverBottom)),
                )
                .border(1.5.dp, SilverBorderLight, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DesignTwoGauge(
                    topLabel = if (pingBusy) "PING…" else "MS PING",
                    value = pingMs?.toString() ?: "—",
                    bottomLabel = "LATENCY",
                    progress = ((pingMs ?: 0).coerceIn(0, 250) / 250f),
                    accentColor = GoldText,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ping_gauge")
                        .clickable(onClick = onRefreshPing),
                )

                MainChassisCenterControl(
                    state = state,
                    duration = duration,
                    onClick = {
                        when {
                            state == "connected" || state == "connecting" || state == "error" -> onDisconnect()
                            protocol.isBlank() -> onConnect()
                            onProtocolAllowed() -> onStartConfigured()
                            else -> onBlockedProtocol()
                        }
                    },
                    modifier = Modifier.weight(1.08f),
                )

                val totalRate = downloadMbps + uploadMbps
                DesignTwoGauge(
                    topLabel = "MB/S RATE",
                    value = "%.1f".format(totalRate),
                    bottomLabel = "BANDWIDTH",
                    progress = (totalRate / 50.0).toFloat().coerceIn(0f, 1f),
                    accentColor = CyanGauge,
                    modifier = Modifier.weight(1f),
                )
            }

            // Two OLED Data Usage Readout Boxes: RX RECV & TX SENT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OledTelemetryCard(
                    label = "RX RECV",
                    value = formatBytes(receivedBytes),
                    valueColor = GoldText,
                    modifier = Modifier.weight(1f),
                )
                OledTelemetryCard(
                    label = "TX SENT",
                    value = formatBytes(sentBytes),
                    valueColor = CyanGauge,
                    modifier = Modifier.weight(1f),
                )
            }

            if (error.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF331418))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismissError)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        error,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss error",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // 2. Server Selection Card matching Design #2 ('SG-Cloudflare-Reality-01' + SWITCH)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(listOf(ServerNavyTop, ServerNavyBottom)),
                )
                .border(1.dp, Color(0xFF29384D), RoundedCornerShape(14.dp))
                .clickable { onOpenServers("") }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountryFlagBadge(countryCode = countryCode)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        serverName.ifBlank { "No Server Selected" },
                        color = Color(0xFFF8FAFC),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (state == "connected") EmeraldOnline else GoldAccent,
                                CircleShape,
                            ),
                    )
                }
                val protoLabel = when (protocol.uppercase(Locale.ROOT)) {
                    "FREEDOM", "" -> "VLESS-XTLS"
                    else -> protocol.uppercase(Locale.ROOT)
                }
                val pingLabel = pingMs?.let { "${it}ms" } ?: "—"
                Text(
                    "• $protoLabel | ${serverLocation.ifBlank { "PORT: 443" }} | PING: $pingLabel",
                    color = CyanGauge,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("switch_server_button")
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1F2B3D), Color(0xFF131B28))),
                    )
                    .border(1.dp, Color(0xFF3E4E68), RoundedCornerShape(9.dp))
                    .clickable { onOpenServers("") }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "SWITCH",
                    color = Color(0xFFE2E8F0),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Switch server",
                    tint = Color(0xFFE2E8F0),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // 3. Proton VPN-Style World Map preserved from previous step!
        ProtonWorldMapPanel(
            serverName = serverName,
            locationName = serverLocation,
            latitude = serverLatitude,
            longitude = serverLongitude,
            vpnState = state,
            onSetLocation = onSetLocation,
            onBrowseCountryServers = { code -> onOpenServers(code) },
            onSaveCoordinates = onSaveLocation,
        )

        // 4. AI Live Sentinel Stream Card matching Design #2
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF8C9AA8), Color(0xFF515E6B))),
                )
                .border(1.5.dp, Color(0xFFB8C4D0), RoundedCornerShape(14.dp))
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.verticalGradient(listOf(SentinelTealTop, SentinelTealBottom)),
                    )
                    .border(1.dp, Color(0xFF284357), RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                ScrewHead(Modifier.align(Alignment.TopStart), angle = 25f)
                ScrewHead(Modifier.align(Alignment.TopEnd), angle = -35f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "AI LIVE SENTINEL STREAM",
                                color = Color(0xFF7DD3FC),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                            )
                            Text(
                                if (sentinelPaused) "STREAM PAUSED · LOCAL TELEMETRY"
                                else "XRAY CORE + GEMINI ROUTE ENGINE",
                                color = Color(0xFF94A3B8),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 8.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SentinelControlChip(
                                label = if (sentinelPaused) "RESUME" else "PAUSE",
                                testTag = "sentinel_pause_button",
                                onClick = onToggleSentinelPause,
                            )
                            SentinelControlChip(
                                label = "CLEAR",
                                testTag = "sentinel_clear_button",
                                onClick = onClearSentinelLogs,
                            )
                        }
                    }

                    // Terminal log viewport
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF04090E))
                            .border(1.dp, Color(0xFF1D3342), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (sentinelLogs.isEmpty()) {
                            Text(
                                "[SENTINEL] Stream cleared. Awaiting tunnel or config activity…",
                                color = Color(0xFF64748B),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                            )
                        } else {
                            sentinelLogs.takeLast(5).forEach { line ->
                                val lineColor = when {
                                    line.startsWith("[ERR]") -> Color(0xFFFCA5A5)
                                    line.startsWith("[VPN]") -> Color(0xFF4ADE80)
                                    line.startsWith("[AI]") -> Color(0xFFFCD34D)
                                    line.startsWith("[PING]") -> CyanGauge
                                    else -> Color(0xFFCBD5E1)
                                }
                                Text(
                                    line,
                                    color = lineColor,
                                    fontFamily = JetBrainsMonoFont,
                                    fontSize = 9.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Expandable AI Route & Quick Config Import controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF0D1D28))
                            .border(1.dp, Color(0xFF284357), RoundedCornerShape(7.dp))
                            .testTag("ai_panel_toggle")
                            .clickable { showToolsDrawer = !showToolsDrawer }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = GoldText,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                "IMPORT CONFIG & GEMINI ROUTE MATCHER",
                                color = Color(0xFFE2E8F0),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            imageVector = if (showToolsDrawer) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle import and Gemini tools",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    AnimatedVisibility(visible = showToolsDrawer) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            RecessedInput(
                                value = importText,
                                onValueChange = onImportTextChange,
                                placeholder = "Paste vless://, vmess://, trojan://, ss:// or Xray JSON",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CopperButton(
                                    label = "IMPORT LINK",
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_link_button"),
                                ) { onImport(importText.trim()) }
                                SecondaryActionButton(
                                    label = "PUBLIC SERVERS",
                                    icon = Icons.Filled.Public,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_public_servers_button"),
                                ) { onOpenServers("") }
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF1F3647)),
                            )

                            RecessedInput(
                                value = purpose,
                                onValueChange = onPurposeChange,
                                placeholder = "Purpose for Gemini route, e.g. gaming or 4K streaming",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("PUBG Mobile", "YouTube 4K", "Anti-Censorship").forEach { label ->
                                    Keycap(
                                        label = label,
                                        modifier = Modifier.weight(1f),
                                        selected = purpose == label,
                                    ) { onPurposeChange(label) }
                                }
                            }
                            RecessedInput(
                                value = apiKey,
                                onValueChange = onApiKeyChange,
                                placeholder = "Gemini API key · session only",
                                secure = true,
                            )
                            CopperButton(
                                label = if (recommendationBusy) "ASKING GEMINI…" else "ASK GEMINI · FIND FREE SERVER",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ask_gemini_button"),
                                onClick = onAskGemini,
                            )
                            if (recommendationMessage.isNotBlank()) {
                                Text(
                                    recommendationMessage,
                                    color = if (recommendationBusy) GoldText else Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignTwoGauge(
    topLabel: String,
    value: String,
    bottomLabel: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.85f),
        label = "gaugeNeedle",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp),
            ) {
                val outerRadius = size.minDimension / 2f
                // Brushed silver metallic bezel ring
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFF4F7FA), Color(0xFFB5BFC9), Color(0xFF6B7580)),
                    ),
                    radius = outerRadius,
                )
                val dialRadius = outerRadius * 0.86f
                drawCircle(Color(0xFF0B0F15), dialRadius)

                val arcSize = Size(dialRadius * 1.62f, dialRadius * 1.62f)
                val arcTopLeft = Offset(center.x - dialRadius * 0.81f, center.y - dialRadius * 0.81f)

                drawArc(
                    color = accentColor.copy(alpha = 0.22f),
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = 1.6.dp.toPx()),
                    topLeft = arcTopLeft,
                    size = arcSize,
                )
                if (animatedProgress > 0.01f) {
                    drawArc(
                        color = accentColor,
                        startAngle = 140f,
                        sweepAngle = 260f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 2.2.dp.toPx()),
                        topLeft = arcTopLeft,
                        size = arcSize,
                    )
                }

                // Precision dial ticks
                for (tick in 0..12) {
                    val angle = Math.toRadians(140.0 + tick * (260.0 / 12.0))
                    val outer = dialRadius * 0.89f
                    val inner = dialRadius * if (tick % 3 == 0) 0.76f else 0.82f
                    drawLine(
                        color = if (tick % 3 == 0) accentColor else Color(0xFF475569),
                        start = Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner,
                        ),
                        end = Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer,
                        ),
                        strokeWidth = if (tick % 3 == 0) 1.3.dp.toPx() else 0.8.dp.toPx(),
                    )
                }

                // Needle
                val needleDeg = 140.0 + 260.0 * animatedProgress
                val rad = Math.toRadians(needleDeg)
                val tip = Offset(
                    center.x + cos(rad).toFloat() * dialRadius * 0.58f,
                    center.y + sin(rad).toFloat() * dialRadius * 0.58f,
                )
                drawLine(
                    color = accentColor,
                    start = center,
                    end = tip,
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(accentColor, radius = 3.dp.toPx(), center = center)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 13.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    topLabel,
                    color = Color(0xFFCBD5E1),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    value,
                    color = accentColor,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            bottomLabel,
            color = DarkInk,
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun MainChassisCenterControl(
    state: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state == "connected"
    val connecting = state == "connecting"
    val isError = state == "error"

    val statusColor = when {
        connected -> EmeraldOnline
        connecting -> GoldText
        isError -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }
    val statusWord = when {
        connected -> "ONLINE"
        connecting -> "WAIT"
        isError -> "ERROR"
        else -> "OFFLINE"
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag("vpn_power_button")
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFDCE2E8), Color(0xFF9AA3AE)),
                    ),
                )
                .border(1.5.dp, Color(0xFFF8FAFC), CircleShape)
                .clickable(onClick = onClick)
                .padding(7.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (connected) Color(0xFF0A2E25) else Color(0xFF101923),
                                Color(0xFF060B10),
                            ),
                        ),
                    )
                    .border(
                        width = 2.dp,
                        color = if (connected) EmeraldOnline else if (connecting) GoldText else Color(0xFF334155),
                        shape = CircleShape,
                    ),
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        color = GoldText,
                        strokeWidth = 2.dp,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = if (connected) "Disconnect VPN" else "Connect VPN",
                        tint = if (connected || connecting) GoldText else Color(0xFFD9822B),
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        statusWord,
                        color = statusColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                    )
                    if (connected) {
                        Text(
                            duration,
                            color = Color(0xFFCBD5E1),
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 7.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "MAIN CHASSIS",
            color = DarkInk,
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun OledTelemetryCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF070C12))
            .border(1.5.dp, Color(0xFF535D68), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color(0xFF94A3B8),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            value,
            color = valueColor,
            fontFamily = JetBrainsMonoFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CountryFlagBadge(countryCode: String) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF172233))
            .border(1.dp, Color(0xFF394B66), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode == "SG") {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val halfH = size.height / 2f
                drawRect(Color(0xFFEF3340), topLeft = Offset.Zero, size = Size(size.width, halfH))
                drawRect(Color.White, topLeft = Offset(0f, halfH), size = Size(size.width, halfH))
                drawCircle(
                    color = Color.White,
                    radius = halfH * 0.56f,
                    center = Offset(size.width * 0.28f, halfH * 0.5f),
                )
                drawCircle(
                    color = Color(0xFFEF3340),
                    radius = halfH * 0.45f,
                    center = Offset(size.width * 0.34f, halfH * 0.5f),
                )
            }
        } else {
            Text(
                countryCode,
                color = NetconColors.CopperLight,
                fontFamily = JetBrainsMonoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SentinelControlChip(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF132634))
            .border(1.dp, Color(0xFF3A5B73), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = Color(0xFFE2E8F0),
            fontFamily = JetBrainsMonoFont,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsScreen(
    dns: String,
    onDnsChange: (String) -> Unit,
    route: String,
    onRouteChange: (String) -> Unit,
    protocols: Set<String>,
    onProtocolsChange: (Set<String>) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
) {
    var dnsMenu by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("NETWORK & ROUTING")
        MetalPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow("Android VPN Lockdown", "Always-on VPN & block non-VPN traffic") {
                    SecondaryActionButton(label = "OPEN", onClick = onOpenVpnSettings)
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

                SettingRow("DNS Resolver", "Active upstream DNS: $dns") {
                    Box {
                        SecondaryActionButton(label = "CHANGE", onClick = { dnsMenu = true })
                        DropdownMenu(expanded = dnsMenu, onDismissRequest = { dnsMenu = false }) {
                            listOf(
                                "Cloudflare · 1.1.1.1" to "1.1.1.1",
                                "Google · 8.8.8.8" to "8.8.8.8",
                                "AdGuard · 94.140.14.14" to "94.140.14.14",
                                "Quad9 · 9.9.9.9" to "9.9.9.9",
                            ).forEach { (label, address) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onDnsChange(address)
                                        dnsMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1.1.1.1", "8.8.8.8", "94.140.14.14", "9.9.9.9").forEach { addr ->
                        Keycap(
                            label = addr,
                            modifier = Modifier.weight(1f),
                            selected = dns == addr,
                        ) { onDnsChange(addr) }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

                Text(
                    "ALLOWED PROTOCOLS",
                    color = NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(
                        "VLESS" to "vless",
                        "VMESS" to "vmess",
                        "TROJAN" to "trojan",
                        "SS" to "shadowsocks",
                    ).forEach { (name, id) ->
                        val checked = id in protocols
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    onProtocolsChange(if (enabled) protocols + id else protocols - id)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF1D0E05),
                                    checkedTrackColor = GoldAccent,
                                    uncheckedThumbColor = NetconColors.TextSecondary,
                                    uncheckedTrackColor = NetconColors.ChassisDark,
                                    uncheckedBorderColor = NetconColors.BevelBorder,
                                ),
                            )
                            Text(
                                name,
                                color = if (checked) NetconColors.CopperLight else NetconColors.TextMuted,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

                Text(
                    "ROUTING MODE",
                    color = NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Global", "Bypass LAN", "Rule-based").forEach { value ->
                        Keycap(
                            label = value,
                            modifier = Modifier.weight(1f),
                            selected = route == value,
                        ) { onRouteChange(value) }
                    }
                }
            }
        }

        CopperButton(
            label = "EXPORT XRAY / V2RAY JSON",
            icon = Icons.Filled.ContentCopy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_json_button"),
            onClick = onExport,
        )
        TextButton(
            onClick = onOpenVpnSettings,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                "OPEN ANDROID VPN SETTINGS",
                color = DarkInk,
                fontFamily = JetBrainsMonoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun InfoScreen(
    serverName: String,
    protocol: String,
    state: String,
    onOpenVpnSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("DEVICE & CORE TELEMETRY")
        MetalPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Engine", "Copper Compass · Xray V1.8.16")
                InfoRow("App Version", "2.4.1")
                InfoRow("Tunnel Service", "${state.uppercase(Locale.ROOT)} · Xray")
                InfoRow("Active Protocol", protocol.ifBlank { "—" })
                InfoRow("Active Node", serverName.ifBlank { "—" })
                InfoRow("Config Storage", "Device Private Preferences")
            }
        }
        MetalPanel {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = GoldText,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "ZERO-LOG & DATA SAFETY",
                        color = NetconColors.CopperLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Configuration links are parsed locally and saved in private app preferences. No telemetry or advertising SDK is included.",
                        color = NetconColors.TextPrimary,
                        fontSize = 12.sp,
                    )
                    Text(
                        "VPN routing is powered by Android VpnService and the bundled Xray core. Always verify IP and DNS leak behavior on a physical device.",
                        color = NetconColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        MetalPanel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "ANDROID SYSTEM VPN",
                    color = NetconColors.CopperLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Configure Always-on VPN and Block connections without VPN inside Android system settings.",
                    color = NetconColors.TextSecondary,
                    fontSize = 11.sp,
                )
                TextButton(onClick = onOpenVpnSettings) {
                    Text(
                        "OPEN SYSTEM SETTINGS",
                        color = NetconColors.CopperLight,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicServerDialog(
    initialCountryCode: String,
    onDismiss: () -> Unit,
    fetchNodes: suspend (String) -> NativePublicDirectory.Result,
    onSelectNode: (NativePublicDirectory.Node) -> Unit,
    onImport: (String) -> Unit,
) {
    var raw by rememberSaveable { mutableStateOf("") }
    var countryCode by rememberSaveable(initialCountryCode) { mutableStateOf(initialCountryCode) }
    var search by rememberSaveable { mutableStateOf("") }
    var countryMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf(emptyList<NativePublicDirectory.Node>()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (loading) return
        loading = true
        error = ""
        scope.launch {
            try {
                val result = withTimeout(25_000) { fetchNodes(countryCode) }
                nodes = result.nodes
                error = "${result.nodes.size} supported · ${result.skipped} skipped"
            } catch (cause: Exception) {
                error = cause.message ?: "Public directory unavailable. Try another feed."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(countryCode) { refresh() }

    val visibleNodes = nodes.filter {
        search.isBlank() || "${it.name} ${it.country} ${it.protocol}".contains(search, ignoreCase = true)
    }.take(40)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelHighlight, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "FREE PUBLIC SERVERS",
                    color = NetconColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }
            Text(
                "FastNodes community feed. Public nodes are untrusted and may have variable uptime.",
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )

            Box {
                Keycap(
                    label = if (countryCode.isEmpty()) "TOP FEED · ALL REGIONS"
                    else "${Locale("", countryCode).displayCountry.uppercase(Locale.ROOT)} ($countryCode)",
                    modifier = Modifier.fillMaxWidth(),
                    selected = countryCode.isNotEmpty(),
                    onClick = { countryMenu = true },
                )
                DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Top feed · All regions") },
                        onClick = {
                            countryCode = ""
                            countryMenu = false
                        },
                    )
                    NativePublicDirectory.countryCodes.forEach { code ->
                        DropdownMenuItem(
                            text = { Text("${Locale("", code).displayCountry} ($code)") },
                            onClick = {
                                countryCode = code
                                countryMenu = false
                            },
                        )
                    }
                }
            }

            RecessedInput(search, { search = it }, "Search protocol or location")

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (loading) "Loading public feed…" else error,
                    color = if (loading) NetconColors.CopperLight else NetconColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SecondaryActionButton(
                    label = "REFRESH",
                    icon = Icons.Filled.Refresh,
                    onClick = ::refresh,
                )
            }

            visibleNodes.forEach { node ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetconColors.ChassisDark)
                        .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name,
                            color = NetconColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${node.protocol.uppercase(Locale.ROOT)} · ${node.country}",
                            color = NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.sp,
                        )
                    }
                    CopperButton("IMPORT", onClick = { onSelectNode(node) })
                }
            }

            if (nodes.isEmpty() && !loading && error.isNotBlank()) {
                Text(
                    "Try refreshing, picking another country, or pasting a link below.",
                    color = NetconColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }

            Text(
                "PASTE A SERVER LINK",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            RecessedInput(raw, { raw = it }, "vless://, vmess://, trojan://, ss:// or Xray JSON", minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton("CANCEL", modifier = Modifier.weight(1f), onClick = onDismiss)
                CopperButton("IMPORT LOCALLY", Modifier.weight(1f)) { onImport(raw.trim()) }
            }
        }
    }
}

@Composable
private fun LocationDialog(
    initialName: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var latitude by rememberSaveable(initialLatitude) { mutableStateOf(initialLatitude?.toString().orEmpty()) }
    var longitude by rememberSaveable(initialLongitude) { mutableStateOf(initialLongitude?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelHighlight, RoundedCornerShape(12.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "SERVER MAP LOCATION",
                color = NetconColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Enter location name and coordinates for your server node. Saved locally on this device.",
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )
            RecessedInput(name, { name = it }, "Location name, e.g. Singapore")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecessedInput(latitude, { latitude = it }, "Latitude", Modifier.weight(1f))
                RecessedInput(longitude, { longitude = it }, "Longitude", Modifier.weight(1f))
            }
            if (error.isNotBlank()) Text(error, color = NetconColors.ErrorRed, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton("CANCEL", Modifier.weight(1f), onClick = onDismiss)
                CopperButton("SAVE LOCATION", Modifier.weight(1f)) {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    if (name.isBlank() || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                        error = "Enter a valid name and coordinates."
                    } else {
                        onSave(name.trim(), lat, lon)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetalPanel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ServerNavyTop, ServerNavyBottom),
                ),
            )
            .border(1.dp, Color(0xFF2D3B50), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun RecessedInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    secure: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF060A10))
            .border(1.dp, Color(0xFF2D3C50), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = NetconColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = NetconColors.TextPrimary,
                fontFamily = SpaceGroteskFont,
                fontSize = 13.sp,
            ),
            minLines = minLines,
            cursorBrush = Brush.verticalGradient(listOf(NetconColors.CopperLight, GoldAccent)),
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}

@Composable
private fun Keycap(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (selected) GoldAccent.copy(alpha = 0.24f) else Color(0xFF0D1722),
            )
            .border(
                1.dp,
                if (selected) GoldText else Color(0xFF2C3E52),
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) NetconColors.CopperLight else NetconColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CopperButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6D08A), Color(0xFFD9822B), Color(0xFF9C5114)),
                ),
            )
            .border(1.dp, Color(0xFFFBE0A9).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF1D0E05),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = Color(0xFF1D0E05),
            fontFamily = JetBrainsMonoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131D2B))
            .border(1.dp, Color(0xFF3E4E68), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NetconColors.CopperLight,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = NetconColors.TextPrimary,
            fontFamily = JetBrainsMonoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = DarkInk,
        fontFamily = JetBrainsMonoFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun SettingRow(label: String, detail: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = NetconColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                detail,
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box { control() }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = NetconColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            color = NetconColors.TextPrimary,
            fontSize = 11.sp,
            fontFamily = JetBrainsMonoFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1000 -> "$bytes B"
    bytes < 1_000_000 -> "%.2f KB".format(bytes / 1000.0)
    bytes < 1_000_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
    else -> "%.2f GB".format(bytes / 1_000_000_000.0)
}