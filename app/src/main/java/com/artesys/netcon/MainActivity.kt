package com.artesys.netcon

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.TimeZone

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
    private var userLatitude by mutableStateOf<Double?>(null)
    private var userLongitude by mutableStateOf<Double?>(null)
    private var userLocationSource by mutableStateOf("")
    private var userLocationBusy by mutableStateOf(false)
    private var licenseStatus by mutableStateOf(
        NetconLicenseManager.LicenseStatus(
            state = NetconLicenseManager.State.TRIAL_ACTIVE,
            trialStartEpochMs = 0L,
            trialExpiresEpochMs = NetconLicenseManager.TRIAL_DURATION_MS,
            remainingTrialMs = NetconLicenseManager.TRIAL_DURATION_MS,
            activatedKeyMasked = "",
        ),
    )
    private var feedback by mutableStateOf("")
    private var pendingConfig = ""
    private var locationCancelSignal: CancellationSignal? = null
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>

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
        userLatitude = preferences.getString("user_latitude", null)?.toDoubleOrNull()
        userLongitude = preferences.getString("user_longitude", null)?.toDoubleOrNull()
        userLocationSource = preferences.getString("user_location_source", "").orEmpty()
        licenseStatus = NetconLicenseManager.evaluateStatus(this)

        vpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val config = pendingConfig
            pendingConfig = ""
            if (result.resultCode == Activity.RESULT_OK && config.isNotBlank()) {
                startVpn(config)
            } else {
                feedback = "Android VPN permission was not granted. Tap Connect to try again."
            }
        }

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFineLocationPermission()
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasCoarseLocationPermission()
            if (fineGranted || coarseGranted) {
                resolveDeviceLocation(fineGranted = fineGranted)
            } else {
                userLocationBusy = false
                userLocationSource = "Permission denied · Tap AUTO LOCATE to grant"
                feedback = "Location permission was denied. Grant exact or approximate location permission to detect your origin automatically."
            }
        }

        setContent {
            NetconTheme {
                NetconDashboard(
                    serverName = savedName,
                    protocol = protocolName(savedConfig),
                    serverLocation = serverLocation,
                    serverLatitude = serverLatitude,
                    serverLongitude = serverLongitude,
                    userLatitude = userLatitude,
                    userLongitude = userLongitude,
                    userLocationSource = userLocationSource,
                    userLocationBusy = userLocationBusy,
                    onRequestUserLocation = { requestAutomaticUserLocation(autoTriggered = false) },
                    licenseStatus = licenseStatus,
                    onRefreshLicenseStatus = { refreshLicenseStatus() },
                    onActivateLicenseKey = ::activateLicenseKey,
                    onSimulateTrialExpired = ::simulateTrialExpired,
                    onResetTrialWindow = ::resetTrialWindow,
                    feedback = feedback,
                    onFeedbackDismiss = { feedback = "" },
                    onImport = ::importConfiguration,
                    onFetchPublicNodes = { countryCode ->
                        withContext(Dispatchers.IO) { NativePublicDirectory.fetch(countryCode) }
                    },
                    onRecommend = { activity, latitude, longitude, nodes ->
                        NativeLocalServerRecommender.recommend(activity, latitude, longitude, nodes)
                    },
                    onSelectPublicNode = ::selectPublicNode,
                    onConnect = { requestVpnConnection(savedConfig) },
                    onDisconnect = { disconnectVpn() },
                    onBlockedProtocol = { feedback = "Enable this server protocol in Settings before connecting." },
                    onStartConfigured = { dnsAddress, routingMode ->
                        requestVpnConnection(configuredXrayJson(savedConfig, dnsAddress, routingMode))
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
                    onSelectPresetProfile = ::selectPresetProfile,
                )
            }
        }
        handleIncomingIntent(intent)
        // Automatically request location permission or resolve user location on launch
        requestAutomaticUserLocation(autoTriggered = true)
    }

    override fun onDestroy() {
        locationCancelSignal?.cancel()
        locationCancelSignal = null
        if (receiverRegistered) {
            unregisterReceiver(vpnStatusReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestAutomaticUserLocation(autoTriggered: Boolean) {
        val fineGranted = hasFineLocationPermission()
        val coarseGranted = hasCoarseLocationPermission()
        if (fineGranted || coarseGranted) {
            resolveDeviceLocation(fineGranted = fineGranted)
        } else {
            userLocationBusy = true
            userLocationSource = "Requesting location permission…"
            if (!autoTriggered) {
                feedback = ""
            }
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveDeviceLocation(fineGranted: Boolean) {
        userLocationBusy = true
        userLocationSource = if (fineGranted) "Acquiring exact GPS location…" else "Acquiring approximate location…"
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            applyApproximateRegionFallback("Approximate region (Location service unavailable)")
            return
        }

        val candidateProviders = buildList {
            if (fineGranted) {
                add("fused")
                add(LocationManager.GPS_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()

        var bestLastKnown: Location? = null
        for (provider in candidateProviders) {
            val loc = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            } ?: continue
            if (bestLastKnown == null || isBetterLocation(loc, bestLastKnown)) {
                bestLastKnown = loc
            }
        }

        if (bestLastKnown != null) {
            applyResolvedLocation(bestLastKnown, fineGranted)
            // If cached fix is recent (< 5 minutes), no need to block on a new fix
            val ageMs = System.currentTimeMillis() - bestLastKnown.time
            if (ageMs in 0..300_000L) {
                userLocationBusy = false
                return
            }
        }

        val activeProvider = candidateProviders.firstOrNull { provider ->
            try {
                locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }

        if (activeProvider == null) {
            if (bestLastKnown != null) {
                userLocationBusy = false
            } else {
                applyApproximateRegionFallback(
                    if (fineGranted) "Approximate region · Enable device GPS for exact fix"
                    else "Approximate region · Coarse location active",
                )
            }
            return
        }

        locationCancelSignal?.cancel()
        val signal = CancellationSignal()
        locationCancelSignal = signal
        try {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                activeProvider,
                signal,
                ContextCompat.getMainExecutor(this),
            ) { location ->
                userLocationBusy = false
                if (location != null) {
                    applyResolvedLocation(location, fineGranted)
                } else if (bestLastKnown == null) {
                    applyApproximateRegionFallback(
                        if (fineGranted) "Approximate region · GPS signal indoors/unavailable"
                        else "Approximate region · Network location",
                    )
                }
            }
        } catch (_: Exception) {
            userLocationBusy = false
            if (bestLastKnown == null) {
                applyApproximateRegionFallback("Approximate region · Network fallback")
            }
        }
    }

    private fun isBetterLocation(candidate: Location, currentBest: Location): Boolean {
        val timeDelta = candidate.time - currentBest.time
        if (timeDelta > 120_000L) return true
        if (timeDelta < -120_000L) return false
        return candidate.accuracy <= currentBest.accuracy
    }

    private fun applyResolvedLocation(location: Location, fineGranted: Boolean) {
        val lat = location.latitude
        val lon = location.longitude
        val isExact = fineGranted && (location.provider == LocationManager.GPS_PROVIDER || (location.hasAccuracy() && location.accuracy <= 100f))
        val accuracyText = if (location.hasAccuracy()) " (±${location.accuracy.toInt()}m)" else ""
        val sourceLabel = if (isExact) {
            "Exact GPS$accuracyText"
        } else {
            "Approximate Network$accuracyText"
        }
        persistUserLocation(lat, lon, sourceLabel)
    }

    private fun applyApproximateRegionFallback(sourceLabel: String) {
        userLocationBusy = false
        val (lat, lon) = approximateCoordinatesFromTimeZone()
        persistUserLocation(lat, lon, sourceLabel)
    }

    private fun persistUserLocation(lat: Double, lon: Double, sourceLabel: String) {
        userLatitude = lat
        userLongitude = lon
        userLocationSource = sourceLabel
        preferences.edit()
            .putString("user_latitude", lat.toString())
            .putString("user_longitude", lon.toString())
            .putString("user_location_source", sourceLabel)
            .apply()
    }

    private fun approximateCoordinatesFromTimeZone(): Pair<Double, Double> {
        val tzId = TimeZone.getDefault().id.uppercase(Locale.ROOT)
        return when {
            tzId.contains("SINGAPORE") || tzId.contains("KUALA_LUMPUR") -> 1.3521 to 103.8198
            tzId.contains("TOKYO") -> 35.6762 to 139.6503
            tzId.contains("SEOUL") -> 37.5665 to 126.9780
            tzId.contains("HONG_KONG") || tzId.contains("TAIPEI") -> 22.3193 to 114.1694
            tzId.contains("KOLKATA") || tzId.contains("CALCUTTA") -> 19.0760 to 72.8777
            tzId.contains("DUBAI") -> 25.2048 to 55.2708
            tzId.contains("SYDNEY") || tzId.contains("MELBOURNE") -> -33.8688 to 151.2093
            tzId.contains("LONDON") -> 51.5072 to -0.1276
            tzId.contains("PARIS") -> 48.8566 to 2.3522
            tzId.contains("BERLIN") || tzId.contains("FRANKFURT") -> 50.1109 to 8.6821
            tzId.contains("AMSTERDAM") -> 52.3676 to 4.9041
            tzId.contains("STOCKHOLM") -> 59.3293 to 18.0686
            tzId.contains("HELSINKI") -> 60.1699 to 24.9384
            tzId.contains("ISTANBUL") -> 41.0082 to 28.9784
            tzId.contains("TORONTO") -> 43.6532 to -79.3832
            tzId.contains("LOS_ANGELES") || tzId.contains("VANCOUVER") -> 34.0522 to -118.2437
            tzId.contains("CHICAGO") -> 41.8781 to -87.6298
            tzId.contains("NEW_YORK") || tzId.contains("AMERICA/") -> 39.0438 to -77.4874
            else -> 1.3521 to 103.8198
        }
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

    private fun refreshLicenseStatus(): NetconLicenseManager.LicenseStatus {
        val current = NetconLicenseManager.evaluateStatus(this)
        licenseStatus = current
        if (!current.isAccessAllowed) {
            val st = NetconVpnService.getStatus().state
            if (st == "connected" || st == "connecting") {
                disconnectVpn()
                feedback = "3-day free trial expired. Purchase and activate a license key to connect."
            }
        }
        return current
    }

    private fun activateLicenseKey(
        accountIdentifier: String,
        rawKey: String,
    ): NetconLicenseManager.ActivationResult {
        val result = NetconLicenseManager.activateLicenseKey(
            context = this,
            rawKey = rawKey,
            accountIdentifier = accountIdentifier,
        )
        licenseStatus = result.status
        feedback = result.message
        return result
    }

    private fun simulateTrialExpired(expired: Boolean) {
        licenseStatus = NetconLicenseManager.setSimulatedTrialExpired(this, expired)
        if (expired) {
            disconnectVpn()
            feedback = "3-day free trial expired. APK frozen until a valid license key is activated."
        } else {
            feedback = "Trial state restored."
        }
    }

    private fun resetTrialWindow() {
        licenseStatus = NetconLicenseManager.resetTrialWindow(this)
        feedback = "3-day (72h) free trial reset."
    }

    private fun importConfiguration(raw: String): Boolean {
        if (!refreshLicenseStatus().isAccessAllowed) {
            feedback = "3-day free trial expired. Activate a license key to import or connect to servers."
            return false
        }
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
        if (!refreshLicenseStatus().isAccessAllowed) {
            feedback = "3-day free trial expired. Activate a license key to switch or connect to servers."
            return
        }
        val country = node.countryCode?.let { "${node.country} · source-reported" }.orEmpty()
        val coords = countryCoordinates(node.countryCode)
        saveServer(node.configJson, node.name, country, coords?.first, coords?.second)
        feedback = "Free public server imported. It is untrusted and has not been tested."
    }

    private fun selectPresetProfile(preset: NativeXrayConfig.ServerProfilePreset) {
        if (!refreshLicenseStatus().isAccessAllowed) {
            feedback = "3-day free trial expired. Activate a license key to switch or connect to servers."
            return
        }
        val locationLabel = "${preset.cityLabel}, ${preset.countryName}"
        saveServer(preset.configJson, preset.name, locationLabel, preset.latitude, preset.longitude)
        feedback = "Activated ${preset.name} (${preset.transportSummary})."
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
        if (!refreshLicenseStatus().isAccessAllowed) {
            disconnectVpn()
            feedback = "3-day free trial expired. Purchase and activate a license key to connect to any server."
            return
        }
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

    private fun requestVpnConnection(config: String) {
        if (!refreshLicenseStatus().isAccessAllowed) {
            disconnectVpn()
            feedback = "3-day free trial expired. Purchase and activate a license key to connect to any server."
            return
        }
        if (config.isBlank()) {
            feedback = "Import a server configuration before connecting."
            return
        }
        try {
            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent == null) {
                startVpn(config)
            } else {
                pendingConfig = config
                feedback = "Waiting for Android VPN permission…"
                vpnPermissionLauncher.launch(permissionIntent)
            }
        } catch (error: Exception) {
            pendingConfig = ""
            feedback = error.message ?: "Unable to request Android VPN permission."
        }
    }

    private fun disconnectVpn() {
        startService(Intent(this, NetconVpnService::class.java).setAction(NetconVpnService.ACTION_DISCONNECT))
    }

    private fun protocolName(config: String): String {
        if (config.isBlank()) return ""
        return try {
            val root = JSONObject(config)
            val metaProto = root.optJSONObject("netconMeta")?.optString("displayProtocol", "").orEmpty()
            if (metaProto.isNotBlank()) return metaProto.uppercase(Locale.ROOT)
            val firstOb = root.optJSONArray("outbounds")?.optJSONObject(0)
            val profileProto = firstOb?.optJSONObject("settings")?.optString("profileProtocol", "").orEmpty()
            if (profileProto.isNotBlank()) return profileProto.uppercase(Locale.ROOT)
            firstOb?.optString("protocol")?.uppercase(Locale.ROOT) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun configuredXrayJson(config: String, dnsAddress: String, routingMode: String): String {
        val root = JSONObject(config)
        root.put("dns", JSONObject().put("servers", JSONArray().put(dnsAddress).put("8.8.8.8")))
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
        routing.put("domainStrategy", "IPIfNonMatch")
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
            val root = JSONObject(configText)
            val meta = root.optJSONObject("netconMeta")
            if (meta != null) {
                val host = meta.optString("endpointHost", "")
                val port = meta.optInt("endpointPort", 443)
                if (host.isNotBlank()) return host to port
            }
            val outbound = root.optJSONArray("outbounds")?.optJSONObject(0) ?: return null
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