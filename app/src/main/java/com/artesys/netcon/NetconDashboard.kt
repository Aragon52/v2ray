package com.artesys.netcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    userLatitude: Double?,
    userLongitude: Double?,
    userLocationSource: String,
    userLocationBusy: Boolean,
    onRequestUserLocation: () -> Unit,
    licenseStatus: NetconLicenseManager.LicenseStatus = NetconLicenseManager.LicenseStatus(
        state = NetconLicenseManager.State.TRIAL_ACTIVE,
        trialStartEpochMs = 0L,
        trialExpiresEpochMs = NetconLicenseManager.TRIAL_DURATION_MS,
        remainingTrialMs = NetconLicenseManager.TRIAL_DURATION_MS,
        activatedKeyMasked = "",
    ),
    onRefreshLicenseStatus: () -> Unit = {},
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult = { _, _ ->
        NetconLicenseManager.ActivationResult(
            success = false,
            message = "License activation unavailable in preview.",
            status = licenseStatus,
        )
    },
    onSimulateTrialExpired: (Boolean) -> Unit = {},
    onResetTrialWindow: () -> Unit = {},
    feedback: String,
    onFeedbackDismiss: () -> Unit,
    onImport: (String) -> Boolean,
    onFetchPublicNodes: suspend (String) -> NativePublicDirectory.Result,
    onRecommend: suspend (String, Double, Double, List<NativePublicDirectory.Node>) -> NativeLocalServerRecommender.Recommendation,
    onSelectPublicNode: (NativePublicDirectory.Node) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBlockedProtocol: () -> Unit,
    onStartConfigured: (String, String) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
    onMeasurePing: suspend () -> Int?,
    onSelectPresetProfile: (NativeXrayConfig.ServerProfilePreset) -> Unit = {},
) {
    val vpnStatus by produceState(initialValue = NetconVpnService.getStatus()) {
        while (true) {
            onRefreshLicenseStatus()
            value = NetconVpnService.getStatus()
            delay(1000)
        }
    }
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Connection) }
    var serverDialog by rememberSaveable { mutableStateOf(false) }
    var licenseDialog by rememberSaveable { mutableStateOf(false) }
    var serverDialogCountry by rememberSaveable { mutableStateOf("") }
    var importText by rememberSaveable { mutableStateOf("") }
    var savedActivity by rememberSaveable {
        mutableStateOf(NativeLocalServerRecommender.activities.first())
    }
    val activity = savedActivity.takeIf { it in NativeLocalServerRecommender.activities }
        ?: NativeLocalServerRecommender.activities.first()
    var recommendationMessage by remember { mutableStateOf("") }
    var recommendationBusy by remember { mutableStateOf(false) }
    var dns by rememberSaveable { mutableStateOf("1.1.1.1") }
    var route by rememberSaveable { mutableStateOf("Bypass LAN") }
    var enabledProtocols by rememberSaveable {
        mutableStateOf(setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "freedom"))
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

    fun findLocalMatch() {
        if (recommendationBusy) return
        val originLatitude = userLatitude
        val originLongitude = userLongitude
        if (originLatitude == null || originLongitude == null) {
            onRequestUserLocation()
            recommendationMessage = "Requesting device location permission to match nearby servers automatically…"
            appendSentinelLog("[LOCAL] Requesting automatic GPS/Network location…")
            return
        }
        val selectedActivity = activity
        recommendationBusy = true
        recommendationMessage = "Checking public server metadata for nearby regions…"
        appendSentinelLog("[LOCAL] Checking nearby public server regions for $selectedActivity…")
        coroutineScope.launch {
            try {
                var result: NativePublicDirectory.Result? = null
                val failures = mutableListOf<String>()
                for (countryCode in NativeLocalServerRecommender.nearestCountryCodes(
                    originLatitude,
                    originLongitude,
                )) {
                    try {
                        val candidate = withTimeout(20_000) { onFetchPublicNodes(countryCode) }
                        if (candidate.nodes.isNotEmpty()) {
                            result = candidate
                            break
                        }
                        failures += "$countryCode: no supported servers in feed"
                    } catch (error: TimeoutCancellationException) {
                        failures += "$countryCode: feed request timed out"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failures += "$countryCode: ${error.message ?: "feed request failed"}"
                    }
                }
                val available = result
                    ?: throw IllegalStateException(
                        failures.joinToString("; ").ifBlank { "No public servers were found in nearby regions." },
                    )
                recommendationMessage = "Comparing ${minOf(available.nodes.size, 30)} public servers locally…"
                val recommendation = onRecommend(
                    selectedActivity,
                    originLatitude,
                    originLongitude,
                    available.nodes,
                )
                val selected = available.nodes.firstOrNull { it.id == recommendation.nodeId }
                    ?: throw IllegalStateException("Local matcher selected a server outside the downloaded list.")
                onSelectPublicNode(selected)
                recommendationMessage = "${recommendation.reason} Imported locally; tap Main Chassis to connect."
                appendSentinelLog("[LOCAL] Selected ${selected.name} (${selected.country})")
            } catch (error: Exception) {
                recommendationMessage = error.message ?: "Unable to find a local server match."
                appendSentinelLog("[LOCAL] ${recommendationMessage}")
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
                DashboardLogoCard(
                    state = vpnStatus.state,
                    protocol = protocol,
                )

                TrialLicenseStatusBar(
                    licenseStatus = licenseStatus,
                    onOpenLicenseDialog = { licenseDialog = true },
                )

                // Three top navigation pills matching Design #2
                DesignTwoNavigation(
                    current = tab,
                    onSelect = { tab = it },
                )

                when (tab) {
                    DashboardTab.Connection -> ConnectionScreen(
                        importText = importText,
                        onImportTextChange = { importText = it },
                        activity = activity,
                        onActivityChange = { savedActivity = it },
                        recommendationMessage = recommendationMessage,
                        recommendationBusy = recommendationBusy,
                        onFindLocalMatch = ::findLocalMatch,
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
                            if (!licenseStatus.isAccessAllowed) {
                                licenseDialog = true
                            } else {
                                serverDialogCountry = countryCode
                                serverDialog = true
                            }
                        },
                        userLatitude = userLatitude,
                        userLongitude = userLongitude,
                        userLocationSource = userLocationSource,
                        userLocationBusy = userLocationBusy,
                        onRequestUserLocation = onRequestUserLocation,
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
                        onConnect = {
                            if (!licenseStatus.isAccessAllowed) {
                                onDisconnect()
                                licenseDialog = true
                            } else {
                                onConnect()
                            }
                        },
                        onDisconnect = onDisconnect,
                        onBlockedProtocol = onBlockedProtocol,
                        onStartConfigured = {
                            if (!licenseStatus.isAccessAllowed) {
                                onDisconnect()
                                licenseDialog = true
                            } else {
                                onStartConfigured(dns, route)
                            }
                        },
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
                        licenseStatus = licenseStatus,
                        onOpenLicenseDialog = { licenseDialog = true },
                        onSimulateTrialExpired = onSimulateTrialExpired,
                        onResetTrialWindow = onResetTrialWindow,
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
                        "NETCON SECURITY ARCHITECTURE",
                        color = DarkInkMuted,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "XRAY CORE · ZERO-LOG",
                        color = DarkInkMuted,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Full-screen non-dismissible lock overlay when the 3-day free trial is expired
        if (licenseStatus.isFrozen) {
            FrozenTrialLockOverlay(
                licenseStatus = licenseStatus,
                onActivateLicenseKey = { accountId, raw ->
                    val res = onActivateLicenseKey(accountId, raw)
                    if (res.success) {
                        appendSentinelLog("[LICENSE] Activated ${res.status.activatedKeyMasked} for ${res.status.activatedAccount}")
                    }
                    res
                },
                onResetTrialWindow = onResetTrialWindow,
            )
        }
    }

    if (licenseDialog && !licenseStatus.isFrozen) {
        LicenseActivationDialog(
            licenseStatus = licenseStatus,
            onDismiss = { licenseDialog = false },
            onActivateLicenseKey = { accountId, raw ->
                val res = onActivateLicenseKey(accountId, raw)
                if (res.success) {
                    appendSentinelLog("[LICENSE] Activated ${res.status.activatedKeyMasked} for ${res.status.activatedAccount}")
                    licenseDialog = false
                }
                res
            },
            onSimulateTrialExpired = {
                licenseDialog = false
                onSimulateTrialExpired(true)
            },
            onResetTrialWindow = onResetTrialWindow,
        )
    }

    if (serverDialog && licenseStatus.isAccessAllowed) {
        PublicServerDialog(
            initialCountryCode = serverDialogCountry,
            activeServerName = serverName,
            onDismiss = { serverDialog = false },
            fetchNodes = onFetchPublicNodes,
            onSelectNode = { node ->
                onSelectPublicNode(node)
                appendSentinelLog("[NODE] Switched to ${node.name} (${node.country})")
                serverDialog = false
            },
            onSelectPreset = { preset ->
                onSelectPresetProfile(preset)
                appendSentinelLog("[PROFILE] Activated ${preset.name} (${preset.protocol})")
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
}

@Composable
private fun DashboardLogoCard(
    state: String = "disconnected",
    protocol: String = "",
) {
    val connected = state == "connected"
    val connecting = state == "connecting"
    val accentDot = when {
        connected -> EmeraldOnline
        connecting -> GoldText
        else -> GoldAccent
    }
    val protoLabel = when (protocol.uppercase(Locale.ROOT)) {
        "", "FREEDOM" -> "VLESS · REALITY"
        else -> protocol.uppercase(Locale.ROOT)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF1F5F8), Color(0xFFD6DEE6), Color(0xFFB7C2CE)),
                ),
            )
            .border(1.5.dp, SilverBorderLight, RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0A1018), Color(0xFF131E2E), Color(0xFF0B121C)),
                    ),
                )
                .border(1.dp, Color(0xFF2E3E54), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Subtle copper & cyan ambient glow behind the logo mark
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x38D9822B), Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.5f),
                        radius = size.minDimension * 0.85f,
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.5f),
                    radius = size.minDimension * 0.85f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2238BDF8), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.35f),
                        radius = size.minDimension * 0.7f,
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.35f),
                    radius = size.minDimension * 0.7f,
                )
            }

            ScrewHead(Modifier.align(Alignment.TopStart), angle = 22f)
            ScrewHead(Modifier.align(Alignment.TopEnd), angle = -28f)
            ScrewHead(Modifier.align(Alignment.BottomStart), angle = -18f)
            ScrewHead(Modifier.align(Alignment.BottomEnd), angle = 34f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.netcon_logo),
                    contentDescription = "Netcon logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(42.dp)
                        .fillMaxWidth(0.76f),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentDot, CircleShape),
                    )
                    Text(
                        "PRECISION MULTI-PROTOCOL TUNNEL",
                        color = Color(0xFFE2E8F0),
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                    )
                }

                Text(
                    "$protoLabel  ·  XRAY CORE  ·  ZERO-LOG",
                    color = Color(0xFF94A3B8),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
                )
            }
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
    activity: String,
    onActivityChange: (String) -> Unit,
    recommendationMessage: String,
    recommendationBusy: Boolean,
    onFindLocalMatch: () -> Unit,
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
    userLatitude: Double?,
    userLongitude: Double?,
    userLocationSource: String,
    userLocationBusy: Boolean,
    onRequestUserLocation: () -> Unit,
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
        // 1. F-16 / F-22 Raptor Avionics MFD Telemetry Cluster (Connection Status, Latency, TX Speed)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF141C27),
                            Color(0xFF0B1119),
                            Color(0xFF070B10),
                        ),
                    ),
                )
                .border(1.5.dp, Color(0xFF334459), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isPulsingStatus = state == "connected" || state == "connecting"
            val statusLabel = when (state) {
                "connected" -> "ONLINE"
                "connecting" -> "CONNECTING"
                "error" -> "ATTENTION"
                else -> "OFFLINE"
            }
            val statusColor = when (state) {
                "connected" -> Color(0xFF22FF88)
                "connecting" -> GoldText
                "error" -> Color(0xFFEF4444)
                else -> Color(0xFF94A3B8)
            }
            val statusTransition = rememberInfiniteTransition(label = "statusPillPulse")
            val pillPulsePhase by statusTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = if (state == "connecting") 750 else 1250,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "pillPulsePhase",
            )
            val hudSweepAngle by statusTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "hudSweepAngle",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "TELEMETRY",
                    color = Color(0xFF22FF88),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.14f))
                        .border(1.dp, statusColor.copy(alpha = 0.48f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.size(13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPulsingStatus) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val maxR = size.minDimension / 2f
                                drawCircle(
                                    color = statusColor.copy(alpha = (1f - pillPulsePhase) * 0.68f),
                                    radius = 3.2.dp.toPx() + (maxR - 3.2.dp.toPx()) * pillPulsePhase,
                                    style = Stroke(width = 1.4.dp.toPx()),
                                )
                                drawCircle(
                                    color = statusColor.copy(alpha = (1f - pillPulsePhase) * 0.28f),
                                    radius = 3.2.dp.toPx() + (maxR - 3.2.dp.toPx()) * pillPulsePhase,
                                )
                            }
                        }
                        val coreScale = if (isPulsingStatus) {
                            0.88f + 0.24f * sin(pillPulsePhase * Math.PI.toFloat())
                        } else {
                            1f
                        }
                        Box(
                            Modifier
                                .size((7f * coreScale).dp)
                                .background(statusColor, CircleShape),
                        )
                    }
                    Text(
                        statusLabel,
                        color = statusColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DesignTwoGauge(
                    topLabel = "ms",
                    value = pingMs?.toString() ?: "—",
                    bottomLabel = "PING",
                    progress = ((pingMs ?: 0).coerceIn(0, 250) / 250f),
                    secondaryProgress = if (pingMs != null) 1f - ((pingMs.coerceIn(0, 250)) / 250f) else 0f,
                    accentColor = Color(0xFF22FF88),
                    secondaryColor = GoldText,
                    sweepAngleDeg = if (state == "connected" || state == "connecting" || pingBusy) hudSweepAngle else 0f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ping_gauge")
                        .clickable(onClick = onRefreshPing),
                )

                MainChassisCenterControl(
                    state = state,
                    duration = duration,
                    sweepAngleDeg = hudSweepAngle,
                    pulsePhase = pillPulsePhase,
                    onClick = {
                        when {
                            state == "connected" || state == "connecting" || state == "error" -> onDisconnect()
                            protocol.isBlank() -> onConnect()
                            onProtocolAllowed() -> onStartConfigured()
                            else -> onBlockedProtocol()
                        }
                    },
                    modifier = Modifier.weight(1.12f),
                )

                val txSpeedMbps = if (state == "connected") {
                    uploadMbps.coerceAtLeast(0.1)
                } else {
                    uploadMbps
                }
                val rxSpeedMbps = downloadMbps
                DesignTwoGauge(
                    topLabel = "Mbps",
                    value = "%.1f".format(txSpeedMbps),
                    bottomLabel = "TX",
                    progress = (txSpeedMbps / 25.0).toFloat().coerceIn(0f, 1f),
                    secondaryProgress = (rxSpeedMbps / 50.0).toFloat().coerceIn(0f, 1f),
                    accentColor = CyanGauge,
                    secondaryColor = Color(0xFF22FF88),
                    sweepAngleDeg = if (state == "connected") hudSweepAngle else 0f,
                    modifier = Modifier.weight(1f),
                )
            }

            // Two F-16 DED (Data Entry Display) Odometer Readout Boxes: RX & TX
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OledTelemetryCard(
                    label = "RX",
                    value = formatBytes(receivedBytes),
                    valueColor = Color(0xFF22FF88),
                    modifier = Modifier.weight(1f),
                )
                OledTelemetryCard(
                    label = "TX",
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

        // 3. Proton VPN-Style World Map with automatic user location
        ProtonWorldMapPanel(
            serverName = serverName,
            locationName = serverLocation,
            latitude = serverLatitude,
            longitude = serverLongitude,
            userLatitude = userLatitude,
            userLongitude = userLongitude,
            userLocationSource = userLocationSource,
            userLocationBusy = userLocationBusy,
            vpnState = state,
            onBrowseCountryServers = { code -> onOpenServers(code) },
            onRequestUserLocation = onRequestUserLocation,
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
                                else "XRAY CORE + LOCAL SERVER MATCH",
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
                                    line.startsWith("[LOCAL]") -> Color(0xFFFCD34D)
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
                                "IMPORT CONFIG & LOCAL SERVER MATCH",
                                color = Color(0xFFE2E8F0),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            imageVector = if (showToolsDrawer) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle import and local server matching tools",
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

                            Text(
                                "ACTIVITY PROFILE · LOCAL DISTANCE MATCH",
                                color = Color(0xFF9FB2C4),
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NativeLocalServerRecommender.activities.forEach { label ->
                                    Keycap(
                                        label = label,
                                        modifier = Modifier.weight(1f),
                                        selected = activity == label,
                                    ) { onActivityChange(label) }
                                }
                            }
                            Text(
                                "Uses approximate region distance only; server latency and streaming capacity are not provided.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                            )
                            CopperButton(
                                label = if (recommendationBusy) "MATCHING LOCALLY…" else "LOCAL MATCH · FIND PUBLIC SERVER",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("local_server_match_button"),
                                onClick = onFindLocalMatch,
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
    subValue: String = "",
    bottomLabel: String,
    progress: Float,
    secondaryProgress: Float = 0f,
    accentColor: Color,
    secondaryColor: Color = GoldText,
    sweepAngleDeg: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.85f),
        label = "gaugeNeedle",
    )
    val animatedSecondary by animateFloatAsState(
        targetValue = secondaryProgress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.88f),
        label = "gaugeSecondary",
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
                // F-16 MFD octagonal/circular anodized bezel
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF283546), Color(0xFF151E29), Color(0xFF090E14)),
                    ),
                    radius = outerRadius,
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.35f),
                    radius = outerRadius * 0.96f,
                    style = Stroke(width = 1.dp.toPx()),
                )

                val dialRadius = outerRadius * 0.88f
                drawCircle(Color(0xFF040A0E), dialRadius)

                // Subtle F-16 radar crosshairs & concentric range rings
                drawCircle(
                    color = accentColor.copy(alpha = 0.10f),
                    radius = dialRadius * 0.62f,
                    style = Stroke(
                        width = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                    ),
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.12f),
                    start = Offset(center.x - dialRadius * 0.72f, center.y),
                    end = Offset(center.x + dialRadius * 0.72f, center.y),
                    strokeWidth = 0.7.dp.toPx(),
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.12f),
                    start = Offset(center.x, center.y - dialRadius * 0.72f),
                    end = Offset(center.x, center.y + dialRadius * 0.72f),
                    strokeWidth = 0.7.dp.toPx(),
                )

                // Corner HUD bracket ticks inside the MFD frame
                val bracketOffset = dialRadius * 0.64f
                val bracketArm = dialRadius * 0.16f
                val bracketColor = accentColor.copy(alpha = 0.42f)
                val bStroke = 1.1.dp.toPx()
                // Top-left bracket
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y - bracketOffset), Offset(center.x - bracketOffset + bracketArm, center.y - bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y - bracketOffset), Offset(center.x - bracketOffset, center.y - bracketOffset + bracketArm), bStroke)
                // Top-right bracket
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y - bracketOffset), Offset(center.x + bracketOffset - bracketArm, center.y - bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y - bracketOffset), Offset(center.x + bracketOffset, center.y - bracketOffset + bracketArm), bStroke)
                // Bottom-left bracket
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y + bracketOffset), Offset(center.x - bracketOffset + bracketArm, center.y + bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y + bracketOffset), Offset(center.x - bracketOffset, center.y + bracketOffset - bracketArm), bStroke)
                // Bottom-right bracket
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y + bracketOffset), Offset(center.x + bracketOffset - bracketArm, center.y + bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y + bracketOffset), Offset(center.x + bracketOffset, center.y + bracketOffset - bracketArm), bStroke)

                // Outer primary telemetry arc
                val arcSize = Size(dialRadius * 1.66f, dialRadius * 1.66f)
                val arcTopLeft = Offset(center.x - dialRadius * 0.83f, center.y - dialRadius * 0.83f)
                drawArc(
                    color = accentColor.copy(alpha = 0.18f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = arcTopLeft,
                    size = arcSize,
                )
                if (animatedProgress > 0.01f) {
                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = arcTopLeft,
                        size = arcSize,
                    )
                }

                // Inner secondary telemetry arc (e.g. signal quality or DL rate)
                val innerArcSize = Size(dialRadius * 1.38f, dialRadius * 1.38f)
                val innerArcTopLeft = Offset(center.x - dialRadius * 0.69f, center.y - dialRadius * 0.69f)
                drawArc(
                    color = secondaryColor.copy(alpha = 0.14f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 1.4.dp.toPx()),
                    topLeft = innerArcTopLeft,
                    size = innerArcSize,
                )
                if (animatedSecondary > 0.01f) {
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.85f),
                        startAngle = 135f,
                        sweepAngle = 270f * animatedSecondary,
                        useCenter = false,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = innerArcTopLeft,
                        size = innerArcSize,
                    )
                }

                // 24 F-16 azimuth/nozzle precision ticks
                for (tick in 0..24) {
                    val angle = Math.toRadians(135.0 + tick * (270.0 / 24.0))
                    val isMajor = tick % 4 == 0
                    val lit = (tick / 24f) <= animatedProgress && animatedProgress > 0.01f
                    val outer = dialRadius * 0.93f
                    val inner = dialRadius * if (isMajor) 0.78f else 0.85f
                    drawLine(
                        color = when {
                            lit -> accentColor
                            isMajor -> accentColor.copy(alpha = 0.55f)
                            else -> Color(0xFF334155)
                        },
                        start = Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner,
                        ),
                        end = Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer,
                        ),
                        strokeWidth = if (isMajor) 1.4.dp.toPx() else 0.8.dp.toPx(),
                    )
                }

                // Rotating radar sweep line when active
                if (sweepAngleDeg > 0f) {
                    val sweepRad = Math.toRadians(sweepAngleDeg.toDouble())
                    drawLine(
                        color = accentColor.copy(alpha = 0.25f),
                        start = center,
                        end = Offset(
                            center.x + cos(sweepRad).toFloat() * dialRadius * 0.75f,
                            center.y + sin(sweepRad).toFloat() * dialRadius * 0.75f,
                        ),
                        strokeWidth = 1.1.dp.toPx(),
                    )
                }

                // Tactical HUD needle + target-lock diamond at tip
                val needleDeg = 135.0 + 270.0 * animatedProgress
                val rad = Math.toRadians(needleDeg)
                val tip = Offset(
                    center.x + cos(rad).toFloat() * dialRadius * 0.68f,
                    center.y + sin(rad).toFloat() * dialRadius * 0.68f,
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.85f),
                    start = center,
                    end = tip,
                    strokeWidth = 1.8.dp.toPx(),
                )
                val dSize = 3.2.dp.toPx()
                val diamond = Path().apply {
                    moveTo(tip.x, tip.y - dSize)
                    lineTo(tip.x + dSize, tip.y)
                    lineTo(tip.x, tip.y + dSize)
                    lineTo(tip.x - dSize, tip.y)
                    close()
                }
                drawPath(diamond, color = accentColor)
                drawCircle(Color(0xFF040A0E), radius = 4.dp.toPx(), center = center)
                drawCircle(accentColor, radius = 2.4.dp.toPx(), center = center)
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
                    color = accentColor.copy(alpha = 0.85f),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xD903080C))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        value,
                        color = accentColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            bottomLabel,
            color = Color(0xFFE2E8F0),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun MainChassisCenterControl(
    state: String,
    duration: String,
    sweepAngleDeg: Float = 0f,
    pulsePhase: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state == "connected"
    val connecting = state == "connecting"
    val isError = state == "error"

    val statusColor = when {
        connected -> Color(0xFF22FF88)
        connecting -> GoldText
        isError -> Color(0xFFEF4444)
        else -> Color(0xFF38BDF8)
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
                        listOf(Color(0xFF2E3D52), Color(0xFF182230), Color(0xFF0A1017)),
                    ),
                )
                .border(1.5.dp, statusColor.copy(alpha = 0.65f), CircleShape)
                .clickable(
                    onClickLabel = if (connected || connecting || isError) "Disconnect VPN" else "Connect VPN",
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = "VPN connection"
                    stateDescription = statusWord
                }
                .padding(6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (connected) Color(0xFF07291E) else if (connecting) Color(0xFF261C0A) else Color(0xFF08131C),
                                Color(0xFF03070B),
                            ),
                        ),
                    )
                    .border(
                        width = 1.8.dp,
                        color = statusColor.copy(alpha = if (connected || connecting) 0.9f else 0.45f),
                        shape = CircleShape,
                    ),
            ) {
                // F-16 HUD Pitch Ladder, Horizon Line, & Rotating Tactical Lock Ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    // Pulsing radar wave when connected or connecting
                    if (connected || connecting) {
                        drawCircle(
                            color = statusColor.copy(alpha = (1f - pulsePhase) * 0.42f),
                            radius = r * (0.48f + 0.44f * pulsePhase),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }

                    // Artificial horizon line & pitch ladder rungs
                    val ladderColor = statusColor.copy(alpha = 0.28f)
                    val lStroke = 1.dp.toPx()
                    // Horizon wings
                    drawLine(ladderColor, Offset(center.x - r * 0.72f, center.y), Offset(center.x - r * 0.36f, center.y), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.36f, center.y), Offset(center.x + r * 0.72f, center.y), lStroke)
                    // Upper pitch rung (+10 deg)
                    val upperY = center.y - r * 0.34f
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, upperY), Offset(center.x - r * 0.22f, upperY), lStroke)
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, upperY), Offset(center.x - r * 0.46f, upperY + r * 0.07f), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.22f, upperY), Offset(center.x + r * 0.46f, upperY), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.46f, upperY), Offset(center.x + r * 0.46f, upperY + r * 0.07f), lStroke)
                    // Lower pitch rung (-10 deg)
                    val lowerY = center.y + r * 0.34f
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, lowerY), Offset(center.x - r * 0.22f, lowerY), lStroke)
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, lowerY), Offset(center.x - r * 0.46f, lowerY - r * 0.07f), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.22f, lowerY), Offset(center.x + r * 0.46f, lowerY), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.46f, lowerY), Offset(center.x + r * 0.46f, lowerY - r * 0.07f), lStroke)

                    // Segmented rotating tactical lock ring
                    val ringRadius = r * 0.84f
                    val arcBox = Size(ringRadius * 2f, ringRadius * 2f)
                    val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
                    val baseAngle = if (connected || connecting) sweepAngleDeg else 0f
                    for (seg in 0 until 4) {
                        drawArc(
                            color = statusColor.copy(alpha = if (connected || connecting) 0.78f else 0.35f),
                            startAngle = baseAngle + seg * 90f + 18f,
                            sweepAngle = 54f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = arcTopLeft,
                            size = arcBox,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        statusWord,
                        color = statusColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    if (connected) {
                        Text(
                            duration,
                            color = Color(0xFFE2E8F0),
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "STATUS",
            color = Color(0xFFE2E8F0),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
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
            .background(Color(0xFF04090E))
            .border(1.2.dp, valueColor.copy(alpha = 0.42f), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color(0xFF94A3B8),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
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
private fun TrialLicenseStatusBar(
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onOpenLicenseDialog: () -> Unit,
) {
    val isLicensed = licenseStatus.isLicensed
    val isFrozen = licenseStatus.isFrozen
    val borderColor = when {
        isLicensed -> EmeraldOnline
        isFrozen -> Color(0xFFEF4444)
        else -> GoldText
    }
    val dotColor = borderColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A111A))
            .border(1.dp, borderColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .testTag("trial_license_bar")
            .clickable { onOpenLicenseDialog() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = licenseStatus.badgeSummary(),
                color = if (isFrozen) Color(0xFFFCA5A5) else Color(0xFFF1F5F9),
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLicensed) Color(0xFF102A22) else GoldAccent.copy(alpha = 0.22f))
                .border(
                    1.dp,
                    if (isLicensed) EmeraldOnline.copy(alpha = 0.6f) else GoldText.copy(alpha = 0.7f),
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (isLicensed) "LICENSED" else "ACTIVATE KEY",
                color = if (isLicensed) Color(0xFF4ADE80) else GoldText,
                fontFamily = JetBrainsMonoFont,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LicenseActivationDialog(
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onDismiss: () -> Unit,
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult,
    onSimulateTrialExpired: () -> Unit,
    onResetTrialWindow: () -> Unit,
) {
    var accountInput by rememberSaveable {
        mutableStateOf(
            licenseStatus.activatedAccount.ifBlank { NetconLicenseManager.DEFAULT_DEMO_ACCOUNT },
        )
    }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf("") }
    var feedbackError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, GoldText.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "1-KEY-PER-ACCOUNT LICENSE",
                    color = NetconColors.TextPrimary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Text(
                text = if (licenseStatus.isLicensed) {
                    "Bound to ${licenseStatus.activatedAccount} (${licenseStatus.activatedKeyMasked}). Each Netcon key works exclusively for its assigned account."
                } else {
                    "3-day (72-hour) free trial (${licenseStatus.formatRemainingCountdown()} remaining). Each activation key is cryptographically bound to one account email and refuses activation on any other account."
                },
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )

            RecessedInput(
                value = accountInput,
                onValueChange = { accountInput = it },
                placeholder = "Account email (e.g. arxdevmm@gmail.com)",
                modifier = Modifier.testTag("dialog_account_input"),
            )

            RecessedInput(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = "NETCON-XXXX-XXXX-XXXX-XXXX",
                modifier = Modifier.testTag("dialog_license_input"),
            )

            if (feedbackMsg.isNotBlank()) {
                Text(
                    text = feedbackMsg,
                    color = if (feedbackError) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    label = "ISSUE FOR EMAIL",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val targetAccount = accountInput.trim().ifBlank { NetconLicenseManager.DEFAULT_DEMO_ACCOUNT }
                        accountInput = targetAccount
                        keyInput = NetconLicenseManager.generateLicenseKeyForAccount(targetAccount, "FULL", "2026")
                        feedbackError = false
                        feedbackMsg = "Generated 1-account key locked to $targetAccount. Tap ACTIVATE KEY."
                    },
                )
                CopperButton(
                    label = "ACTIVATE KEY",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dialog_activate_key_button"),
                    onClick = {
                        val res = onActivateLicenseKey(accountInput, keyInput)
                        feedbackError = !res.success
                        feedbackMsg = res.message
                    },
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    label = "TEST FREEZE",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_freeze_button"),
                    onClick = onSimulateTrialExpired,
                )
                SecondaryActionButton(
                    label = "RESET 3D TRIAL",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onResetTrialWindow()
                        feedbackError = false
                        feedbackMsg = "3-day (72h) trial timer reset."
                    },
                )
            }
        }
    }
}

@Composable
private fun FrozenTrialLockOverlay(
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult,
    onResetTrialWindow: () -> Unit,
) {
    var accountInput by rememberSaveable {
        mutableStateOf(NetconLicenseManager.DEFAULT_DEMO_ACCOUNT)
    }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember {
        mutableStateOf("3-day free trial expired. Enter your registered Account Email and your 1-account Netcon License Key to unlock.")
    }
    var isError by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF205080E))
            .clickable(enabled = true, onClick = {})
            .testTag("frozen_trial_lock_overlay")
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A0F14), Color(0xFF0D131D), Color(0xFF070B11)),
                    ),
                )
                .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2B0D12))
                    .border(1.5.dp, Color(0xFFEF4444), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "APK Frozen",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(25.dp),
                )
            }

            Text(
                text = "APK FROZEN · 3-DAY TRIAL EXPIRED",
                color = Color(0xFFF87171),
                fontFamily = JetBrainsMonoFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "1 KEY = 1 ACCOUNT · ALL SERVERS REFUSED",
                color = GoldText,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = statusMessage,
                color = if (isError) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            RecessedInput(
                value = accountInput,
                onValueChange = { accountInput = it },
                placeholder = "Account email (e.g. arxdevmm@gmail.com)",
                modifier = Modifier.testTag("frozen_account_input"),
            )

            RecessedInput(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = "Enter license key: NETCON-XXXX-XXXX-XXXX-XXXX",
                modifier = Modifier.testTag("frozen_license_input"),
            )

            CopperButton(
                label = "ACTIVATE ACCOUNT-BOUND KEY",
                icon = Icons.Filled.Lock,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("frozen_activate_button"),
                onClick = {
                    val result = onActivateLicenseKey(accountInput, keyInput)
                    isError = !result.success
                    statusMessage = result.message
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryActionButton(
                    label = "FILL ACCOUNT KEY",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("frozen_fill_demo_key_button"),
                    onClick = {
                        val targetAccount = accountInput.trim().ifBlank { NetconLicenseManager.DEFAULT_DEMO_ACCOUNT }
                        accountInput = targetAccount
                        keyInput = NetconLicenseManager.generateLicenseKeyForAccount(targetAccount, "FULL", "2026")
                        isError = false
                        statusMessage = "Loaded key ${keyInput} bound only to $targetAccount."
                    },
                )
                SecondaryActionButton(
                    label = "RESET 3D TRIAL",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("frozen_reset_trial_button"),
                    onClick = onResetTrialWindow,
                )
            }

            Text(
                text = "Each key is HMAC-signed for one account email (${licenseStatus.badgeSummary()})",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 8.5.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InfoScreen(
    serverName: String,
    protocol: String,
    state: String,
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onOpenLicenseDialog: () -> Unit,
    onSimulateTrialExpired: (Boolean) -> Unit,
    onResetTrialWindow: () -> Unit,
    onOpenVpnSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("LICENSE & 1-KEY-PER-ACCOUNT ENFORCEMENT")
        MetalPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("License State", licenseStatus.state.name)
                InfoRow("Trial Countdown", licenseStatus.formatRemainingCountdown())
                InfoRow("Server Access", if (licenseStatus.isAccessAllowed) "ALLOWED" else "FROZEN · REFUSED")
                InfoRow("Account Binding", "1 Key = 1 Account (HMAC-SHA256)")
                if (licenseStatus.isLicensed) {
                    InfoRow("Bound Account", licenseStatus.activatedAccount)
                    InfoRow("Activated Key", licenseStatus.activatedKeyMasked)
                }
                Text(
                    "Issue a single-account key via CLI: bash scripts/manage-keystore.sh generate-license-key <customer@email.com>",
                    color = NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.5.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CopperButton(
                        label = "ENTER LICENSE KEY",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenLicenseDialog,
                    )
                    SecondaryActionButton(
                        label = "SIMULATE EXPIRED",
                        modifier = Modifier.weight(1f),
                        onClick = { onSimulateTrialExpired(true) },
                    )
                }
                SecondaryActionButton(
                    label = "RESET 3-DAY (72H) FREE TRIAL",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onResetTrialWindow,
                )
            }
        }

        SectionTitle("GITHUB RELEASE & KEYSTORE SIGNING")
        MetalPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Workflow", ".github/workflows/release-apk.yml")
                InfoRow("Keystore Manager", "scripts/manage-keystore.sh")
                InfoRow("Signing Alias", "upload (my-upload-key.jks)")
                InfoRow("Env Secrets", "KEYSTORE_BASE64 · STORE_PASSWORD · KEY_PASSWORD")
                Text(
                    "Push any v* git tag (e.g. v1.0.5) or trigger the GitHub Actions workflow to build, sign, verify, and publish the release APK + SHA-256 checksum to GitHub Releases.",
                    color = NetconColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        SectionTitle("DEVICE & CORE TELEMETRY")
        MetalPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Engine", "Copper Compass · Xray V1.8.16")
                InfoRow("App Version", BuildConfig.VERSION_NAME)
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
    activeServerName: String = "",
    onDismiss: () -> Unit,
    fetchNodes: suspend (String) -> NativePublicDirectory.Result,
    onSelectNode: (NativePublicDirectory.Node) -> Unit,
    onSelectPreset: (NativeXrayConfig.ServerProfilePreset) -> Unit = {},
    onImport: (String) -> Unit,
) {
    var raw by rememberSaveable { mutableStateOf("") }
    var countryCode by rememberSaveable(initialCountryCode) { mutableStateOf(initialCountryCode) }
    var search by rememberSaveable { mutableStateOf("") }
    var countryMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf(emptyList<NativePublicDirectory.Node>()) }
    val presets = remember { NativeXrayConfig.presetProfiles() }
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

    val visiblePresets = presets.filter {
        (countryCode.isEmpty() || it.countryCode == countryCode) &&
            (search.isBlank() || "${it.name} ${it.countryName} ${it.protocol} ${it.transportSummary}".contains(search, ignoreCase = true))
    }

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
                    "SERVER PROFILES & DIRECTORY",
                    color = NetconColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            if (visiblePresets.isNotEmpty()) {
                Text(
                    "STABILIZED MULTI-PROTOCOL PROFILES",
                    color = NetconColors.CopperLight,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                visiblePresets.forEach { preset ->
                    val isSelected = preset.name == activeServerName
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF122638) else NetconColors.ChassisDark)
                            .border(
                                1.dp,
                                if (isSelected) NetconColors.CyberCyan else NetconColors.BevelBorder,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${preset.countryCode} · ${preset.name}",
                                color = NetconColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${preset.transportSummary} · ${preset.cityLabel}",
                                color = NetconColors.CyberCyan,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                            )
                        }
                        CopperButton(
                            label = if (isSelected) "ACTIVE" else "USE",
                            onClick = { onSelectPreset(preset) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))
            }

            Text(
                "FREE PUBLIC SERVERS · FASTNODES COMMUNITY FEED",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
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