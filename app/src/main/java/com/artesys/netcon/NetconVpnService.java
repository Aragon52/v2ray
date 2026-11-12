package com.artesys.netcon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import go.Seq;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public final class NetconVpnService extends VpnService {
    public static final String ACTION_CONNECT = "com.artesys.netcon.CONNECT";
    public static final String ACTION_DISCONNECT = "com.artesys.netcon.DISCONNECT";
    public static final String ACTION_STATUS = "com.artesys.netcon.VPN_STATUS";
    public static final String EXTRA_CONFIG = "xrayConfig";
    private static final String EXTRA_STATE = "state";
    private static final String EXTRA_ERROR = "error";
    private static final String EXTRA_ELAPSED = "elapsedSeconds";
    private static final String EXTRA_RX = "receivedBytes";
    private static final String EXTRA_TX = "sentBytes";
    private static final String EXTRA_DOWN_BPS = "downloadBps";
    private static final String EXTRA_UP_BPS = "uploadBps";

    private static final String CHANNEL_ID = "netcon-vpn";
    private static final int NOTIFICATION_ID = 4182;
    private static final Object LOCK = new Object();
    private static volatile Context appContext;
    private static volatile String state = "disconnected";
    private static volatile String errorMessage = "";
    private static volatile String sessionId = "";
    private static volatile long startedAt;
    private static volatile long cachedElapsedSeconds;
    private static volatile long receivedBytes;
    private static volatile long sentBytes;
    private static volatile double downloadBytesPerSecond;
    private static volatile double uploadBytesPerSecond;
    private static volatile long previousSampleAt;
    private static volatile long previousCoreReceivedBytes;
    private static volatile long previousCoreSentBytes;
    private static volatile String primaryOutboundTag = "proxy";
    private static CoreController coreController;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ParcelFileDescriptor vpnInterface;
    private final Runnable healthCheck = new Runnable() {
        @Override
        public void run() {
            boolean failed = false;
            synchronized (LOCK) {
                if ("connected".equals(state) && (coreController == null || !coreController.getIsRunning())) {
                    setStateLocked("error", "Xray stopped unexpectedly. Traffic remains blocked until you disconnect.");
                    failed = true;
                }
            }
            if (failed) {
                updateNotification("VPN blocked: Xray stopped");
            }
            broadcastStatus(NetconVpnService.this);
            if (!isFinishing()) {
                mainHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        appContext = getApplicationContext();
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            mainHandler.removeCallbacks(healthCheck);
            worker.execute(() -> {
                stopTunnel();
                setState("disconnected", "");
                broadcastStatus(NetconVpnService.this);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            });
            return START_NOT_STICKY;
        }

        String config = intent == null ? null : intent.getStringExtra(EXTRA_CONFIG);
        if (config == null || config.trim().isEmpty()) {
            setState("error", "No saved VPN configuration is available.");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        setState("connecting", "");
        try {
            startForegroundCompat();
        } catch (RuntimeException error) {
            setState("error", safeMessage(error));
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        worker.execute(() -> {
            stopTunnel();
            setState("connecting", "");
            startTunnel(config);
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onRevoke() {
        mainHandler.removeCallbacks(healthCheck);
        worker.execute(() -> {
            stopTunnel();
            setState("disconnected", "");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(healthCheck);
        worker.shutdown();
        stopTunnel();
        super.onDestroy();
    }

    private void startTunnel(String configText) {
        try {
            Network underlyingNetwork = getUnderlyingNetwork();
            ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
            LinkProperties linkProperties = connectivity == null ? null : connectivity.getLinkProperties(underlyingNetwork);
            boolean useIpv6 = hasGlobalIpv6(linkProperties);

            JSONObject config = new JSONObject(configText);
            sanitizeConfigForLocalCore(config, getFilesDir());
            String dnsAddress = firstDnsAddress(config);
            String trackedTag = firstOutboundTag(config);

            JSONArray gateway = new JSONArray().put("10.64.0.1/30");
            if (useIpv6) {
                gateway.put("fd64:64::1/126");
            }

            JSONObject tunSettings = new JSONObject()
                .put("name", "netcon0")
                .put("MTU", 1500)
                .put("gateway", gateway)
                .put("DNS", new JSONArray().put(dnsAddress));

            JSONObject sniffing = new JSONObject()
                .put("enabled", true)
                .put("destOverride", new JSONArray().put("http").put("tls").put("quic"));

            JSONArray inbounds = new JSONArray().put(new JSONObject()
                .put("tag", "tun")
                .put("port", 0)
                .put("protocol", "tun")
                .put("settings", tunSettings)
                .put("sniffing", sniffing));
            config.put("inbounds", inbounds);
            config.put("stats", new JSONObject());
            config.put("policy", new JSONObject()
                .put("levels", new JSONObject().put("0", new JSONObject()
                    .put("statsUserUplink", true)
                    .put("statsUserDownlink", true)))
                .put("system", new JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true)));

            Builder builder = new Builder()
                .setSession("Netcon")
                .setMtu(1500)
                .addAddress("10.64.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsAddress);
            if (useIpv6) {
                builder.addAddress("fd64:64::1", 126)
                    .addRoute("::", 0);
            }
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                throw new IllegalStateException("Android could not establish the VPN interface.");
            }
            setUnderlyingNetworks(new Network[] { underlyingNetwork });

            Seq.setContext(getApplicationContext());
            Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "");
            CoreController controller = Libv2ray.newCoreController(new CoreCallbackHandler() {
                @Override public long startup() { return 0; }
                @Override public long shutdown() { return 0; }
                @Override public long onEmitStatus(long statusCode, String message) { return 0; }
            });
            synchronized (LOCK) {
                coreController = controller;
                primaryOutboundTag = trackedTag;
            }
            controller.startLoop(config.toString(), vpnInterface.getFd());
            if (!controller.getIsRunning()) {
                throw new IllegalStateException("Xray did not start.");
            }
            synchronized (LOCK) {
                sessionId = UUID.randomUUID().toString();
                startedAt = SystemClock.elapsedRealtime();
                cachedElapsedSeconds = 0;
                receivedBytes = 0;
                sentBytes = 0;
                downloadBytesPerSecond = 0;
                uploadBytesPerSecond = 0;
                previousSampleAt = startedAt;
                previousCoreReceivedBytes = 0;
                previousCoreSentBytes = 0;
                state = "connected";
                errorMessage = "";
            }
            broadcastStatus(this);
            updateNotification("VPN active");
            mainHandler.removeCallbacks(healthCheck);
            mainHandler.postDelayed(healthCheck, 1000);
        } catch (Exception error) {
            android.util.Log.e("NetconVpnService", "VPN startup error", error);
            stopTunnel();
            String message = safeMessage(error);
            setState("error", message);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private Network getUnderlyingNetwork() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            throw new IllegalStateException("ConnectivityManager is unavailable.");
        }
        Network active = connectivity.getActiveNetwork();
        if (isPhysicalInternetNetwork(connectivity, active)) {
            return active;
        }
        Network[] networks = connectivity.getAllNetworks();
        if (networks != null) {
            for (Network candidate : networks) {
                if (isPhysicalInternetNetwork(connectivity, candidate)) {
                    return candidate;
                }
            }
        }
        if (active != null) {
            return active;
        }
        throw new IllegalStateException("Connect to Wi-Fi or mobile data before starting the VPN.");
    }

    private static boolean isPhysicalInternetNetwork(ConnectivityManager connectivity, Network network) {
        if (network == null) return false;
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
        return caps != null
            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    private static boolean hasGlobalIpv6(LinkProperties props) {
        if (props == null) return false;
        for (LinkAddress addr : props.getLinkAddresses()) {
            InetAddress inet = addr.getAddress();
            if (inet instanceof Inet6Address && !inet.isLinkLocalAddress() && !inet.isLoopbackAddress()) {
                return true;
            }
        }
        return false;
    }

    private static void sanitizeConfigForLocalCore(JSONObject config, File filesDir) throws Exception {
        boolean hasGeoIp = new File(filesDir, "geoip.dat").exists();
        boolean hasGeoSite = new File(filesDir, "geosite.dat").exists();
        JSONObject routing = config.optJSONObject("routing");
        if (routing == null) return;
        JSONArray rules = routing.optJSONArray("rules");
        if (rules == null) return;
        JSONArray cleaned = new JSONArray();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) continue;
            if (!hasGeoIp && rule.has("ip")) {
                JSONArray ips = rule.optJSONArray("ip");
                if (ips != null) {
                    JSONArray replacedIps = new JSONArray();
                    for (int j = 0; j < ips.length(); j++) {
                        String entry = ips.optString(j, "");
                        if ("geoip:private".equalsIgnoreCase(entry)) {
                            for (String cidr : new String[] {
                                "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
                                "127.0.0.0/8", "169.254.0.0/16", "::1/128", "fc00::/7", "fe80::/10"
                            }) {
                                replacedIps.put(cidr);
                            }
                        } else if (!entry.startsWith("geoip:")) {
                            replacedIps.put(entry);
                        }
                    }
                    if (replacedIps.length() == 0) {
                        rule.remove("ip");
                    } else {
                        rule.put("ip", replacedIps);
                    }
                }
            }
            if (!hasGeoSite && rule.has("domain")) {
                JSONArray domains = rule.optJSONArray("domain");
                if (domains != null) {
                    JSONArray filtered = new JSONArray();
                    for (int j = 0; j < domains.length(); j++) {
                        String entry = domains.optString(j, "");
                        if (!entry.startsWith("geosite:")) {
                            filtered.put(entry);
                        }
                    }
                    if (filtered.length() == 0) {
                        rule.remove("domain");
                    } else {
                        rule.put("domain", filtered);
                    }
                }
            }
            if (rule.has("ip") || rule.has("domain") || rule.has("port") || rule.has("network") || rule.has("protocol")) {
                cleaned.put(rule);
            }
        }
        routing.put("rules", cleaned);
    }

    private static String firstOutboundTag(JSONObject config) {
        JSONArray outbounds = config.optJSONArray("outbounds");
        if (outbounds != null && outbounds.length() > 0) {
            JSONObject first = outbounds.optJSONObject(0);
            if (first != null) {
                String tag = first.optString("tag", "");
                if (!tag.isEmpty()) return tag;
            }
        }
        return "proxy";
    }

    private static String firstDnsAddress(JSONObject config) throws Exception {
        JSONArray servers = config.optJSONObject("dns") == null ? null : config.getJSONObject("dns").optJSONArray("servers");
        String address = "1.1.1.1";
        if (servers != null && servers.length() > 0) {
            Object entry = servers.opt(0);
            if (entry instanceof JSONObject) {
                address = ((JSONObject) entry).optString("address", "1.1.1.1");
            } else if (entry != null) {
                address = String.valueOf(entry);
            }
        }
        if (!address.matches("[0-9.]+|[0-9a-fA-F:]+")) {
            address = "1.1.1.1";
        }
        InetAddress parsed = InetAddress.getByName(address);
        if (!(parsed instanceof Inet4Address) && !(parsed instanceof Inet6Address)) {
            return "1.1.1.1";
        }
        return parsed.getHostAddress();
    }

    private void stopTunnel() {
        synchronized (LOCK) {
            if (coreController != null) {
                try {
                    coreController.stopLoop();
                } catch (Exception ignored) {
                }
                coreController = null;
            }
            if (vpnInterface != null) {
                try {
                    vpnInterface.close();
                } catch (Exception ignored) {
                }
                vpnInterface = null;
            }
            if (!"error".equals(state)) {
                state = "disconnected";
                errorMessage = "";
            }
            sessionId = "";
            startedAt = 0;
            receivedBytes = 0;
            sentBytes = 0;
            downloadBytesPerSecond = 0;
            uploadBytesPerSecond = 0;
            previousSampleAt = 0;
            previousCoreReceivedBytes = 0;
            previousCoreSentBytes = 0;
        }
    }

    private static void setState(String nextState, String message) {
        synchronized (LOCK) {
            setStateLocked(nextState, message);
        }
        Context ctx = appContext;
        if (ctx != null) {
            broadcastStatus(ctx);
        }
    }

    private static void setStateLocked(String nextState, String message) {
        state = nextState;
        errorMessage = message;
        sessionId = "";
        startedAt = 0;
        cachedElapsedSeconds = 0;
        receivedBytes = 0;
        sentBytes = 0;
        downloadBytesPerSecond = 0;
        uploadBytesPerSecond = 0;
        previousSampleAt = 0;
        previousCoreReceivedBytes = 0;
        previousCoreSentBytes = 0;
    }

    private static void broadcastStatus(Context context) {
        Status current = getStatus();
        Intent statusIntent = new Intent(ACTION_STATUS)
            .setPackage(context.getPackageName())
            .putExtra(EXTRA_STATE, current.state)
            .putExtra(EXTRA_ERROR, current.error)
            .putExtra(EXTRA_ELAPSED, current.elapsedSeconds)
            .putExtra(EXTRA_RX, current.receivedBytes)
            .putExtra(EXTRA_TX, current.sentBytes)
            .putExtra(EXTRA_DOWN_BPS, current.downloadBytesPerSecond)
            .putExtra(EXTRA_UP_BPS, current.uploadBytesPerSecond);
        context.sendBroadcast(statusIntent);
    }

    public static void applyStatusBroadcast(Intent intent) {
        if (intent == null) return;
        synchronized (LOCK) {
            String nextState = intent.getStringExtra(EXTRA_STATE);
            if (nextState != null) state = nextState;
            String nextError = intent.getStringExtra(EXTRA_ERROR);
            errorMessage = nextError == null ? "" : nextError;
            startedAt = 0;
            cachedElapsedSeconds = Math.max(0L, intent.getLongExtra(EXTRA_ELAPSED, 0L));
            receivedBytes = Math.max(0L, intent.getLongExtra(EXTRA_RX, 0L));
            sentBytes = Math.max(0L, intent.getLongExtra(EXTRA_TX, 0L));
            downloadBytesPerSecond = Math.max(0.0, intent.getDoubleExtra(EXTRA_DOWN_BPS, 0.0));
            uploadBytesPerSecond = Math.max(0.0, intent.getDoubleExtra(EXTRA_UP_BPS, 0.0));
        }
    }

    public static Status getStatus() {
        synchronized (LOCK) {
            sampleTraffic();
            long elapsed = startedAt == 0
                ? cachedElapsedSeconds
                : Math.max(0L, (SystemClock.elapsedRealtime() - startedAt) / 1000);
            return new Status(
                state,
                errorMessage,
                elapsed,
                receivedBytes,
                sentBytes,
                downloadBytesPerSecond,
                uploadBytesPerSecond
            );
        }
    }

    public static final class Status {
        public final String state;
        public final String error;
        public final long elapsedSeconds;
        public final long receivedBytes;
        public final long sentBytes;
        public final double downloadBytesPerSecond;
        public final double uploadBytesPerSecond;

        private Status(String state, String error, long elapsedSeconds, long receivedBytes, long sentBytes,
                       double downloadBytesPerSecond, double uploadBytesPerSecond) {
            this.state = state;
            this.error = error;
            this.elapsedSeconds = elapsedSeconds;
            this.receivedBytes = receivedBytes;
            this.sentBytes = sentBytes;
            this.downloadBytesPerSecond = downloadBytesPerSecond;
            this.uploadBytesPerSecond = uploadBytesPerSecond;
        }
    }

    private static void sampleTraffic() {
        if (coreController == null || !"connected".equals(state)) {
            return;
        }
        long downloaded = 0;
        long uploaded = 0;
        String sample = coreController.queryAllOutboundTrafficStats();
        if (sample == null || sample.isEmpty()) {
            return;
        }
        String tag = primaryOutboundTag;
        for (String item : sample.split(";")) {
            String[] fields = item.split(",");
            if (fields.length != 3 || !tag.equals(fields[0])) {
                continue;
            }
            try {
                long value = Math.max(0, Long.parseLong(fields[2]));
                if ("downlink".equals(fields[1])) downloaded += value;
                if ("uplink".equals(fields[1])) uploaded += value;
            } catch (NumberFormatException ignored) {
            }
        }
        long now = SystemClock.elapsedRealtime();
        long interval = Math.max(1, now - previousSampleAt);
        long receivedDelta = downloaded >= previousCoreReceivedBytes
            ? downloaded - previousCoreReceivedBytes : downloaded;
        long sentDelta = uploaded >= previousCoreSentBytes
            ? uploaded - previousCoreSentBytes : uploaded;
        downloadBytesPerSecond = receivedDelta * 1000d / interval;
        uploadBytesPerSecond = sentDelta * 1000d / interval;
        receivedBytes += receivedDelta;
        sentBytes += sentDelta;
        previousCoreReceivedBytes = downloaded;
        previousCoreSentBytes = uploaded;
        previousSampleAt = now;
    }

    private void startForegroundCompat() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Netcon VPN", NotificationManager.IMPORTANCE_LOW));
        }
        updateNotification("Starting VPN");
        Notification notification = buildNotification("Starting VPN");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private boolean isFinishing() {
        synchronized (LOCK) {
            return !"connected".equals(state) && !"error".equals(state);
        }
    }

    private Notification buildNotification(String text) {
        Intent disconnect = new Intent(this, NetconVpnService.class).setAction(ACTION_DISCONNECT);
        PendingIntent pendingDisconnect = PendingIntent.getService(this, 1, disconnect, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Netcon VPN")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Disconnect", pendingDisconnect)
            .build();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "The Android VPN tunnel failed to start.";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}