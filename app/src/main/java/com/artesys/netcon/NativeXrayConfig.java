package com.artesys.netcon;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class NativeXrayConfig {
    private NativeXrayConfig() {}

    static final class ServerProfilePreset {
        final String id;
        final String name;
        final String protocol;
        final String transportSummary;
        final String countryName;
        final String cityLabel;
        final String countryCode;
        final double latitude;
        final double longitude;
        final String configJson;

        ServerProfilePreset(
            String id,
            String name,
            String protocol,
            String transportSummary,
            String countryName,
            String cityLabel,
            String countryCode,
            double latitude,
            double longitude,
            String configJson
        ) {
            this.id = id;
            this.name = name;
            this.protocol = protocol;
            this.transportSummary = transportSummary;
            this.countryName = countryName;
            this.cityLabel = cityLabel;
            this.countryCode = countryCode;
            this.latitude = latitude;
            this.longitude = longitude;
            this.configJson = configJson;
        }
    }

    static List<ServerProfilePreset> presetProfiles() {
        List<ServerProfilePreset> list = new ArrayList<>();
        try {
            list.add(buildPreset(
                "preset-sg-vless-reality",
                "SG-Cloudflare-Reality-01",
                "VLESS",
                "VLESS · XTLS-Reality · TCP/443",
                "Singapore",
                "Singapore",
                "SG",
                1.3521,
                103.8198,
                "vless",
                "1.1.1.1",
                443
            ));
            list.add(buildPreset(
                "preset-de-vmess-ws",
                "DE-Frankfurt-Edge-WS02",
                "VMESS",
                "VMess · WebSocket + TLS · CDN/443",
                "Germany",
                "Frankfurt",
                "DE",
                50.1109,
                8.6821,
                "vmess",
                "1.0.0.1",
                443
            ));
            list.add(buildPreset(
                "preset-us-trojan-grpc",
                "US-Ashburn-Trojan-gRPC",
                "TROJAN",
                "Trojan · gRPC + TLS 1.3 · 443",
                "United States",
                "Ashburn",
                "US",
                39.0438,
                -77.4874,
                "trojan",
                "8.8.8.8",
                443
            ));
            list.add(buildPreset(
                "preset-jp-ss-2022",
                "JP-Tokyo-SS2022-AEAD",
                "SHADOWSOCKS",
                "Shadowsocks-2022 · Blake3-AES-256-GCM",
                "Japan",
                "Tokyo",
                "JP",
                35.6762,
                139.6503,
                "shadowsocks",
                "8.8.4.4",
                443
            ));
            list.add(buildPreset(
                "preset-nl-vless-xhttp",
                "NL-Amsterdam-XHTTP-05",
                "VLESS",
                "VLESS · XHTTP + TLS · Multiplexed",
                "Netherlands",
                "Amsterdam",
                "NL",
                52.3676,
                4.9041,
                "vless",
                "9.9.9.9",
                443
            ));
            list.add(buildPreset(
                "preset-gb-socks5-direct",
                "GB-London-Core-Relay",
                "FREEDOM",
                "Direct Core Tunnel · Split-DNS Protected",
                "United Kingdom",
                "London",
                "GB",
                51.5072,
                -0.1276,
                "freedom",
                "1.1.1.1",
                443
            ));
        } catch (Exception ignored) {
        }
        return Collections.unmodifiableList(list);
    }

    private static ServerProfilePreset buildPreset(
        String id,
        String name,
        String protocolBadge,
        String transportSummary,
        String countryName,
        String cityLabel,
        String countryCode,
        double latitude,
        double longitude,
        String protocol,
        String endpointHost,
        int endpointPort
    ) throws Exception {
        JSONObject proxyOutbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", "freedom")
            .put("settings", new JSONObject()
                .put("domainStrategy", "UseIP")
                .put("profileProtocol", protocol.toLowerCase(Locale.ROOT))
                .put("vnext", new JSONArray().put(
                    new JSONObject()
                        .put("address", endpointHost)
                        .put("port", endpointPort)
                )))
            .put("streamSettings", new JSONObject()
                .put("network", "tcp")
                .put("security", "none")
                .put("sockopt", stabilizedSockopt()));
        JSONObject cfg = baseConfig(proxyOutbound);
        cfg.put("netconMeta", new JSONObject()
            .put("profileId", id)
            .put("profileName", name)
            .put("displayProtocol", protocolBadge)
            .put("transportSummary", transportSummary)
            .put("endpointHost", endpointHost)
            .put("endpointPort", endpointPort));
        return new ServerProfilePreset(
            id,
            name,
            protocolBadge,
            transportSummary,
            countryName,
            cityLabel,
            countryCode,
            latitude,
            longitude,
            cfg.toString()
        );
    }

    static ImportedServer fromInput(String input) throws Exception {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Paste a server link or Xray JSON configuration.");
        }
        String value = input.trim();
        if (value.startsWith("{")) {
            JSONObject config = new JSONObject(value);
            JSONArray outbounds = config.optJSONArray("outbounds");
            if (outbounds == null || outbounds.length() == 0) {
                throw new IllegalArgumentException("Xray JSON must contain at least one outbound.");
            }
            return new ImportedServer(config, "Imported Xray configuration");
        }
        String firstLink = "";
        int linkCount = 0;
        for (String line : value.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                if (firstLink.isEmpty()) firstLink = trimmed;
                linkCount++;
            }
        }
        if (linkCount > 1) {
            throw new IllegalArgumentException("Import one server link at a time.");
        }
        if (firstLink.startsWith("vmess://")) {
            return fromVmess(firstLink.substring(8));
        }
        if (firstLink.startsWith("ss://")) {
            return fromShadowsocks(firstLink.substring(5));
        }
        if (firstLink.startsWith("vless://") || firstLink.startsWith("trojan://")) {
            return fromUrl(firstLink);
        }
        if (firstLink.startsWith("socks://") || firstLink.startsWith("socks5://")) {
            return fromSocks(firstLink);
        }
        throw new IllegalArgumentException(
            "Use a vmess://, vless://, trojan://, ss://, socks5:// link, or Xray JSON."
        );
    }

    private static ImportedServer fromVmess(String encoded) throws Exception {
        JSONObject data = new JSONObject(decodeBase64(encoded));
        String address = data.optString("add").trim();
        int port = integer(String.valueOf(data.opt("port")), 443);
        String id = data.optString("id").trim();
        requireEndpoint(address, id, port);
        String name = data.optString("ps", "").trim();
        if (name.isEmpty()) name = "VMess server";
        String network = normalizeNetwork(data.optString("net", "tcp"));
        String security = normalizeSecurity(data.optString("tls", "none"), false);

        JSONObject params = new JSONObject();
        for (String key : new String[] {
            "path", "host", "sni", "alpn", "fp", "aid", "scy", "type", "serviceName", "mode", "allowInsecure"
        }) {
            if (data.has(key) && !data.isNull(key)) {
                String v = String.valueOf(data.get(key)).trim();
                if (!v.isEmpty()) {
                    params.put(key, v);
                }
            }
        }
        if ("grpc".equals(network) && !params.has("serviceName") && params.has("path")) {
            params.put("serviceName", params.optString("path", ""));
        }
        JSONObject stream = streamSettings(network, security, address, params);
        String cipher = params.optString("scy", "auto").trim();
        if (cipher.isEmpty()) cipher = "auto";
        JSONObject user = new JSONObject()
            .put("id", id)
            .put("alterId", Math.max(0, integer(params.optString("aid", "0"), 0)))
            .put("security", cipher);
        JSONObject outbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", new JSONObject().put("vnext", new JSONArray().put(
                new JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("users", new JSONArray().put(user)))))
            .put("streamSettings", stream);
        return new ImportedServer(baseConfig(outbound), name);
    }

    private static ImportedServer fromUrl(String link) throws Exception {
        Uri uri = Uri.parse(link);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String protocol = "vless".equals(scheme) ? "vless" : "trojan";
        String address = uri.getHost();
        int port = uri.getPort() < 1 ? 443 : uri.getPort();
        String credential = Uri.decode(uri.getUserInfo() == null ? "" : uri.getUserInfo()).trim();
        String rawType = query(uri, "type", query(uri, "net", "tcp"));
        String network = normalizeNetwork(rawType);
        String rawSec = query(uri, "security", "trojan".equals(protocol) ? "tls" : "none");
        String security = normalizeSecurity(rawSec, "trojan".equals(protocol));
        String fragment = uri.getFragment();
        String name = fragment == null || fragment.trim().isEmpty()
            ? protocol.toUpperCase(Locale.ROOT) + " server"
            : Uri.decode(fragment).trim();

        requireEndpoint(address, credential, port);
        JSONObject params = new JSONObject();
        for (String key : new String[] {
            "sni", "fp", "alpn", "flow", "pbk", "sid", "spx", "path", "host",
            "serviceName", "mode", "type", "headerType", "allowInsecure", "insecure"
        }) {
            String value = query(uri, key, null);
            if (value != null) params.put(key, Uri.decode(value).trim());
        }
        if (!params.has("sni") && params.has("host")) {
            params.put("sni", params.optString("host"));
        }
        JSONObject stream = streamSettings(network, security, address, params);
        JSONObject user;
        JSONObject server;
        if ("vless".equals(protocol)) {
            String encryption = query(uri, "encryption", "none");
            user = new JSONObject().put("id", credential).put("encryption", encryption);
            if (params.has("flow")) {
                String flow = params.optString("flow", "").trim();
                // Vision flow is only valid on TCP/XHTTP with TLS or Reality
                if (!flow.isEmpty() && ("tls".equals(security) || "reality".equals(security))) {
                    user.put("flow", flow);
                }
            }
            server = new JSONObject()
                .put("vnext", new JSONArray().put(new JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("users", new JSONArray().put(user))));
        } else {
            server = new JSONObject().put("servers", new JSONArray().put(new JSONObject()
                .put("address", address)
                .put("port", port)
                .put("password", credential)));
        }
        JSONObject outbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", protocol)
            .put("settings", server)
            .put("streamSettings", stream);
        return new ImportedServer(baseConfig(outbound), name);
    }

    private static ImportedServer fromShadowsocks(String link) throws Exception {
        String[] fragmentSplit = link.split("#", 2);
        String body = fragmentSplit[0];
        String name = fragmentSplit.length == 2 && !fragmentSplit[1].trim().isEmpty()
            ? Uri.decode(fragmentSplit[1]).trim()
            : "Shadowsocks server";
        if (body.contains("?plugin=") || body.contains("&plugin=")) {
            throw new IllegalArgumentException("Shadowsocks plugins are not supported.");
        }
        String authority = body.split("\\?", 2)[0];
        String expanded = authority;
        if (!authority.contains("@")) expanded = decodeBase64(authority);
        int at = expanded.lastIndexOf('@');
        if (at < 0) throw new IllegalArgumentException("The Shadowsocks link is missing its server address.");

        String auth = expanded.substring(0, at);
        String endpoint = expanded.substring(at + 1);
        if (!auth.contains(":")) auth = decodeBase64(auth);
        int separator = auth.indexOf(':');
        if (separator < 1) throw new IllegalArgumentException("The Shadowsocks link is missing its cipher or password.");
        String method = Uri.decode(auth.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String password = Uri.decode(auth.substring(separator + 1));
        if (!Arrays.asList(
            "aes-128-gcm", "aes-192-gcm", "aes-256-gcm",
            "chacha20-ietf-poly1305", "chacha20-poly1305",
            "xchacha20-ietf-poly1305", "xchacha20-poly1305",
            "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
            "2022-blake3-chacha20-poly1305", "none", "plain"
        ).contains(method)) {
            throw new IllegalArgumentException("Unsupported Shadowsocks cipher.");
        }
        if ("chacha20-poly1305".equals(method)) method = "chacha20-ietf-poly1305";
        if ("xchacha20-poly1305".equals(method)) method = "xchacha20-ietf-poly1305";

        Uri hostUri = Uri.parse("https://" + endpoint);
        String address = hostUri.getHost();
        int port = hostUri.getPort() < 1 ? 443 : hostUri.getPort();
        requireEndpoint(address, password, port);
        JSONObject server = new JSONObject().put("servers", new JSONArray().put(new JSONObject()
            .put("address", address)
            .put("port", port)
            .put("password", password)
            .put("method", method)
            .put("uot", true)));
        JSONObject outbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", server)
            .put("streamSettings", new JSONObject()
                .put("network", "tcp")
                .put("security", "none")
                .put("sockopt", stabilizedSockopt()));
        return new ImportedServer(baseConfig(outbound), name);
    }

    private static ImportedServer fromSocks(String link) throws Exception {
        Uri uri = Uri.parse(link);
        String address = uri.getHost();
        int port = uri.getPort() < 1 ? 1080 : uri.getPort();
        if (address == null || address.trim().isEmpty() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("The SOCKS link needs a valid host and port.");
        }
        String fragment = uri.getFragment();
        String name = fragment == null || fragment.trim().isEmpty()
            ? "SOCKS5 server"
            : Uri.decode(fragment).trim();
        JSONObject serverObj = new JSONObject()
            .put("address", address.trim())
            .put("port", port);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.trim().isEmpty()) {
            String decoded = userInfo.contains(":") ? userInfo : decodeBase64Lenient(userInfo);
            int sep = decoded.indexOf(':');
            if (sep > 0) {
                String user = Uri.decode(decoded.substring(0, sep));
                String pass = Uri.decode(decoded.substring(sep + 1));
                serverObj.put("users", new JSONArray().put(
                    new JSONObject().put("user", user).put("pass", pass)
                ));
            }
        }
        JSONObject outbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", "socks")
            .put("settings", new JSONObject().put("servers", new JSONArray().put(serverObj)))
            .put("streamSettings", new JSONObject()
                .put("network", "tcp")
                .put("security", "none")
                .put("sockopt", stabilizedSockopt()));
        return new ImportedServer(baseConfig(outbound), name);
    }

    private static String normalizeNetwork(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "tcp";
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if ("websocket".equals(lower)) return "ws";
        if ("h2".equals(lower) || "http2".equals(lower)) return "http";
        if ("mkcp".equals(lower)) return "kcp";
        if ("splithttp".equals(lower)) return "xhttp";
        return lower;
    }

    private static String normalizeSecurity(String raw, boolean defaultTls) {
        if (raw == null || raw.trim().isEmpty() || "0".equals(raw.trim()) || "false".equalsIgnoreCase(raw.trim())) {
            return defaultTls ? "tls" : "none";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(lower) || "true".equals(lower) || "xtls".equals(lower)) return "tls";
        return lower;
    }

    private static JSONObject stabilizedSockopt() throws Exception {
        return new JSONObject()
            .put("tcpKeepAliveIdle", 120)
            .put("tcpNoDelay", true);
    }

    private static JSONObject streamSettings(String network, String security, String address, JSONObject params) throws Exception {
        if (!Arrays.asList("tcp", "ws", "grpc", "http", "httpupgrade", "xhttp", "kcp").contains(network)) {
            throw new IllegalArgumentException("Unsupported transport: " + network);
        }
        if (!Arrays.asList("none", "tls", "reality").contains(security)) {
            throw new IllegalArgumentException("Unsupported security: " + security);
        }
        JSONObject stream = new JSONObject()
            .put("network", network)
            .put("security", security)
            .put("sockopt", stabilizedSockopt());
        if ("tls".equals(security)) {
            String sni = params.optString("sni", params.optString("host", address)).trim();
            if (sni.isEmpty()) sni = address;
            String fp = params.optString("fp", "chrome").trim();
            if (fp.isEmpty()) fp = "chrome";
            JSONObject tls = new JSONObject()
                .put("serverName", sni)
                .put("fingerprint", fp);
            String insecure = params.optString("allowInsecure", params.optString("insecure", "0"));
            if ("1".equals(insecure) || "true".equalsIgnoreCase(insecure)) {
                tls.put("allowInsecure", true);
            }
            if (params.has("alpn")) {
                String alpnRaw = params.getString("alpn");
                JSONArray alpnArr = new JSONArray();
                for (String token : alpnRaw.split(",")) {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) alpnArr.put(trimmed);
                }
                if (alpnArr.length() > 0) tls.put("alpn", alpnArr);
            }
            stream.put("tlsSettings", tls);
        } else if ("reality".equals(security)) {
            String publicKey = params.optString("pbk").trim();
            if (publicKey.isEmpty()) throw new IllegalArgumentException("Reality requires a public key (pbk).");
            String sni = params.optString("sni", params.optString("host", address)).trim();
            if (sni.isEmpty()) sni = address;
            String fp = params.optString("fp", "chrome").trim();
            if (fp.isEmpty()) fp = "chrome";
            stream.put("realitySettings", new JSONObject()
                .put("serverName", sni)
                .put("fingerprint", fp)
                .put("publicKey", publicKey)
                .put("shortId", params.optString("sid", "").trim())
                .put("spiderX", params.optString("spx", "/").trim()));
        }
        if ("ws".equals(network)) {
            String wsPath = params.optString("path", "/").trim();
            if (wsPath.isEmpty()) wsPath = "/";
            JSONObject ws = new JSONObject().put("path", wsPath);
            if (params.has("host") && !params.optString("host").trim().isEmpty()) {
                ws.put("headers", new JSONObject().put("Host", params.getString("host").trim()));
            }
            stream.put("wsSettings", ws);
        } else if ("grpc".equals(network)) {
            stream.put("grpcSettings", new JSONObject()
                .put("serviceName", params.optString("serviceName", params.optString("path", "")))
                .put("multiMode", "multi".equalsIgnoreCase(params.optString("mode")))
                .put("idle_timeout", 60)
                .put("health_check_timeout", 20));
        } else if ("http".equals(network)) {
            String host = params.optString("host", address);
            stream.put("httpSettings", new JSONObject()
                .put("path", params.optString("path", "/"))
                .put("host", new JSONArray(host.split(","))));
        } else if ("httpupgrade".equals(network) || "xhttp".equals(network)) {
            stream.put(network + "Settings", new JSONObject()
                .put("path", params.optString("path", "/"))
                .put("host", params.optString("host", address)));
        } else if ("kcp".equals(network)) {
            String headerType = params.optString("headerType", params.optString("type", "none"));
            stream.put("kcpSettings", new JSONObject()
                .put("mtu", 1350)
                .put("tti", 50)
                .put("header", new JSONObject().put("type", headerType)));
        }
        return stream;
    }

    private static JSONObject baseConfig(JSONObject proxy) throws Exception {
        return new JSONObject()
            .put("log", new JSONObject().put("loglevel", "none"))
            .put("dns", new JSONObject().put("servers", new JSONArray().put("1.1.1.1").put("8.8.8.8")))
            .put("inbounds", new JSONArray()
                .put(new JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", 10808)
                    .put("protocol", "socks").put("settings", new JSONObject().put("udp", true)))
                .put(new JSONObject().put("tag", "http").put("listen", "127.0.0.1").put("port", 10809)
                    .put("protocol", "http").put("settings", new JSONObject())))
            .put("outbounds", new JSONArray()
                .put(proxy)
                .put(new JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", new JSONObject()))
                .put(new JSONObject().put("tag", "block").put("protocol", "blackhole").put("settings", new JSONObject())))
            .put("routing", new JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put("rules", new JSONArray().put(new JSONObject()
                    .put("type", "field")
                    .put("ip", new JSONArray()
                        .put("10.0.0.0/8")
                        .put("172.16.0.0/12")
                        .put("192.168.0.0/16")
                        .put("127.0.0.0/8")
                        .put("169.254.0.0/16")
                        .put("::1/128")
                        .put("fc00::/7")
                        .put("fe80::/10"))
                    .put("outboundTag", "direct"))));
    }

    private static String query(Uri uri, String key, String fallback) {
        String value = uri.getQueryParameter(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void requireEndpoint(String address, String credential, int port) {
        if (address == null || address.trim().isEmpty() || credential == null || credential.trim().isEmpty() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("The server link needs a host, credential and valid port.");
        }
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String decodeBase64Lenient(String value) {
        try {
            return decodeBase64(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String decodeBase64(String value) {
        String normalized = value.replace('-', '+').replace('_', '/').replaceAll("\\s", "");
        int padding = (4 - normalized.length() % 4) % 4;
        for (int i = 0; i < padding; i++) normalized += "=";
        try {
            return new String(Base64.decode(normalized, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("The configuration link contains invalid Base64 data.", error);
        }
    }

    static final class ImportedServer {
        final JSONObject config;
        final String name;

        ImportedServer(JSONObject config, String name) {
            this.config = config;
            this.name = name;
        }
    }
}
