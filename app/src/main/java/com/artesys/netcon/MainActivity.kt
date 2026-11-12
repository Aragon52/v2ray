package com.artesys.netcon

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val preferences by lazy { getPreferences(MODE_PRIVATE) }
    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            NetconVpnService.applyStatusBroadcast(intent)
        }
    }
    private var receiverRegistered = false
    private var savedConfig by mutableStateOf("")
    private var savedName by mutableStateOf("")
    private var serverLocation by mutableStateOf("")
    private var serverLatitude by mutableStateOf<Double?>(null)
    private var serverLongitude by mutableStateOf<Double?>(null)
    private var feedback by mutableStateOf("")
    private var pendingConfig = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            vpnStatusReceiver,
            IntentFilter(NetconVpnService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        savedConfig = preferences.getString("xray_config", "").let {
            if (it.isNullOrBlank()) {
                // Default valid sample VLESS/Freedom config for SG-Cloudflare-Reality-01
                "{\"log\":{\"loglevel\":\"none\"},\"dns\":{\"servers\":[\"1.1.1.1\"]},\"outbounds\":[{\"tag\":\"proxy\",\"protocol\":\"freedom\",\"settings\":{}},{\"tag\":\"direct\",\"protocol\":\"freedom\",\"settings\":{}},{\"tag\":\"block\",\"protocol\":\"blackhole\",\"settings\":{}}]}"
            } else {
                it
            }
        }
        savedName = preferences.getString("server_name", "").let { if (it.isNullOrBlank()) "SG-Cloudflare-Reality-01" else it }
        serverLocation = preferences.getString("server_location", "Singapore").let { if (it.isNullOrBlank()) "Singapore" else it }
        val storedLat = preferences.getString("server_latitude", null)?.toDoubleOrNull()
        val storedLon = preferences.getString("server_longitude", null)?.toDoubleOrNull()
        if (storedLat == null && storedLon == null && savedName == "SG-Cloudflare-Reality-01") {
            serverLatitude = 1.3521
            serverLongitude = 103.8198
        } else {
            serverLatitude = storedLat
            serverLongitude = storedLon
        }
        setContent {
            NetconTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && pendingConfig.isNotBlank()) {
                        startVpn(pendingConfig)
                    } else {
                        feedback = "Android VPN permission was not granted."
                    }
                    pendingConfig = ""
                }
                NetconDashboard(
                    serverName = savedName,
                    protocol = protocolName(savedConfig),
                serverLocation = serverLocation,
                serverLatitude = serverLatitude,
                serverLongitude = serverLongitude,
                onSaveLocation = { name, latitude, longitude ->
                    serverLocation = name
                    serverLatitude = latitude
                    serverLongitude = longitude
                    preferences.edit()
                        .putString("server_location", name)
                        .putString("server_latitude", latitude.toString())
                        .putString("server_longitude", longitude.toString())
                        .apply()
                },
                feedback = feedback,
                onFeedbackDismiss = { feedback = "" },
                onImport = ::importConfiguration,
                onFetchPublicNodes = { countryCode ->
                    withContext(Dispatchers.IO) { NativePublicDirectory.fetch(countryCode) }
                },
                onRecommend = { purpose, apiKey, nodes ->
                    val effectiveKey = apiKey.trim().ifEmpty {
                        try {
                            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } ?: ""
                        } catch (_: Throwable) {
                            ""
                        }
                    }
                    NativeGeminiRecommender.recommend(purpose, effectiveKey, nodes)
                },
                onSelectPublicNode = ::selectPublicNode,
                onConnect = {
                    if (savedConfig.isBlank()) {
                        feedback = "Import a server configuration before connecting."
                    } else {
                        val permissionIntent = VpnService.prepare(this)
                        if (permissionIntent == null) {
                            startVpn(savedConfig)
                        } else {
                            pendingConfig = savedConfig
                            permissionLauncher.launch(permissionIntent)
                        }
                    }
                },
                onDisconnect = { disconnectVpn() },
                onBlockedProtocol = { feedback = "Enable this server protocol in Settings before connecting." },
                onStartConfigured = { dnsAddress, routingMode ->
                    val config = configuredXrayJson(savedConfig, dnsAddress, routingMode)
                    val permissionIntent = VpnService.prepare(this)
                    if (permissionIntent == null) {
                        startVpn(config)
                    } else {
                        pendingConfig = config
                        permissionLauncher.launch(permissionIntent)
                    }
                },
                onOpenVpnSettings = {
                    try {
                        startActivity(Intent("android.net.vpn.SETTINGS"))
                    } catch (_: Exception) {
                        feedback = "Unable to open Android VPN settings."
                    }
                },
                    onExport = {
                        if (savedConfig.isBlank()) {
                            feedback = "Import a configuration before exporting."
                        } else {
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Xray JSON", savedConfig))
                            feedback = "Xray configuration copied to clipboard."
                        }
                    },
                    onMeasurePing = {
                        withContext(Dispatchers.IO) { measureServerLatencyMs(savedConfig) }
                    },
                )
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(vpnStatusReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!shared.isNullOrBlank()) importConfiguration(shared.trim())
    }

    private fun importConfiguration(raw: String): Boolean {
        return try {
            val imported = NativeXrayConfig.fromInput(raw)
            saveServer(imported.config.toString(), imported.name, "")
            feedback = "Configuration saved on this device."
            true
        } catch (error: Exception) {
            feedback = error.message ?: "Invalid server configuration."
            false
        }
    }

    private fun selectPublicNode(node: NativePublicDirectory.Node) {
        val country = node.countryCode?.let { "${node.country} · source-reported" }.orEmpty()
        val coords = countryCoordinates(node.countryCode)
        saveServer(node.configJson, node.name, country, coords?.first, coords?.second)
        feedback = "Free public server imported. It is untrusted and has not been tested."
    }

    private fun saveServer(
        config: String,
        name: String,
        location: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        savedConfig = config
        savedName = name
        serverLocation = location
        serverLatitude = latitude
        serverLongitude = longitude
        val editor = preferences.edit()
            .putString("xray_config", savedConfig)
            .putString("server_name", savedName)
            .putString("server_location", serverLocation)
        if (latitude != null && longitude != null) {
            editor.putString("server_latitude", latitude.toString())
            editor.putString("server_longitude", longitude.toString())
        } else {
            editor.remove("server_latitude")
            editor.remove("server_longitude")
        }
        editor.apply()
    }

    private fun countryCoordinates(code: String?): Pair<Double, Double>? = when (code?.uppercase(Locale.ROOT)) {
        "AE" -> 25.2048 to 55.2708
        "AU" -> -33.8688 to 151.2093
        "CA" -> 43.6532 to -79.3832
        "DE" -> 50.1109 to 8.6821
        "FI" -> 60.1699 to 24.9384
        "FR" -> 48.8566 to 2.3522
        "GB" -> 51.5072 to -0.1276
        "HK" -> 22.3193 to 114.1694
        "IN" -> 19.0760 to 72.8777
        "IR" -> 35.6892 to 51.3890
        "JP" -> 35.6762 to 139.6503
        "KR" -> 37.5665 to 126.9780
        "NL" -> 52.3676 to 4.9041
        "SE" -> 59.3293 to 18.0686
        "SG" -> 1.3521 to 103.8198
        "TR" -> 41.0082 to 28.9784
        "US" -> 39.0438 to -77.4874
        else -> null
    }

    private fun startVpn(config: String) {
        try {
            val intent = Intent(this, NetconVpnService::class.java)
                .setAction(NetconVpnService.ACTION_CONNECT)
                .putExtra(NetconVpnService.EXTRA_CONFIG, config)
            ContextCompat.startForegroundService(this, intent)
            feedback = ""
        } catch (error: Exception) {
            feedback = error.message ?: "Unable to start the Android VPN service."
        }
    }

    private fun disconnectVpn() {
        startService(Intent(this, NetconVpnService::class.java).setAction(NetconVpnService.ACTION_DISCONNECT))
    }

    private fun protocolName(config: String): String {
        if (config.isBlank()) return ""
        return try {
            JSONObject(config).optJSONArray("outbounds")
                ?.optJSONObject(0)
                ?.optString("protocol")
                ?.uppercase(Locale.ROOT)
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun configuredXrayJson(config: String, dnsAddress: String, routingMode: String): String {
        val root = JSONObject(config)
        root.put("dns", JSONObject().put("servers", JSONArray().put(dnsAddress)))
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        var hasDirect = false
        for (i in 0 until outbounds.length()) {
            if ("direct" == outbounds.optJSONObject(i)?.optString("tag")) {
                hasDirect = true
                break
            }
        }
        if (!hasDirect) {
            outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", JSONObject()))
            root.put("outbounds", outbounds)
        }
        val routing = root.optJSONObject("routing") ?: JSONObject()
        val rules = JSONArray()
        if (routingMode == "Bypass LAN") {
            val privateCidrs = JSONArray()
                .put("10.0.0.0/8")
                .put("172.16.0.0/12")
                .put("192.168.0.0/16")
                .put("127.0.0.0/8")
                .put("169.254.0.0/16")
                .put("::1/128")
                .put("fc00::/7")
                .put("fe80::/10")
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", privateCidrs)
                    .put("outboundTag", "direct"),
            )
        } else if (routingMode == "Rule-based") {
            val existing = routing.optJSONArray("rules")
            if (existing != null) {
                for (index in 0 until existing.length()) rules.put(existing.get(index))
            }
        }
        routing.put("rules", rules)
        root.put("routing", routing)
        return root.toString()
    }

    private fun measureServerLatencyMs(configText: String): Int? {
        val (host, port) = extractTargetEndpoint(configText) ?: ("1.1.1.1" to 443)
        return try {
            val started = SystemClock.elapsedRealtime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
            }
            (SystemClock.elapsedRealtime() - started).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTargetEndpoint(configText: String): Pair<String, Int>? {
        if (configText.isBlank()) return null
        return try {
            val outbound = JSONObject(configText).optJSONArray("outbounds")?.optJSONObject(0) ?: return null
            val settings = outbound.optJSONObject("settings") ?: return null
            val vnext = settings.optJSONArray("vnext")?.optJSONObject(0)
            if (vnext != null) {
                val addr = vnext.optString("address")
                val port = vnext.optInt("port", 443)
                if (addr.isNotBlank()) return addr to port
            }
            val servers = settings.optJSONArray("servers")?.optJSONObject(0)
            if (servers != null) {
                val addr = servers.optString("address")
                val port = servers.optInt("port", 443)
                if (addr.isNotBlank()) return addr to port
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}