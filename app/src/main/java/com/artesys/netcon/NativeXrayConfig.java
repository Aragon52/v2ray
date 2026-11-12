package com.artesys.netcon;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

final class NativeXrayConfig {
    private NativeXrayConfig() {}

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
            if (!line.trim().isEmpty()) {
                if (firstLink.isEmpty()) firstLink = line.trim();
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
        throw new IllegalArgumentException("Use a vmess://, vless://, trojan://, ss:// link, or Xray JSON.");
    }

    private static ImportedServer fromVmess(String encoded) throws Exception {
        JSONObject data = new JSONObject(decodeBase64(encoded));
        String address = data.optString("add");
        int port = integer(data.optString("port"), 0);
        String id = data.optString("id");
        requireEndpoint(address, id, port);
        String name = data.optString("ps", "VMess server");
        String network = data.optString("net", "tcp");
        String security = data.optString("tls", "none");

        JSONObject params = new JSONObject();
        for (String key : new String[] {"path", "host", "sni", "alpn", "fp", "aid", "scy", "type"}) {
            if (data.has(key) && !data.isNull(key)) {
                params.put(key, String.valueOf(data.get(key)));
            }
        }
        JSONObject stream = streamSettings(network, security, address, params);
        JSONObject user = new JSONObject()
            .put("id", id)
            .put("alterId", integer(params.optString("aid", "0"), 0))
            .put("security", params.optString("scy", "auto"));
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
        String protocol = "vless".equals(uri.getScheme()) ? "vless" : "trojan";
        String address = uri.getHost();
        int port = uri.getPort() < 1 ? 443 : uri.getPort();
        String credential = Uri.decode(uri.getUserInfo() == null ? "" : uri.getUserInfo());
        String network = query(uri, "type", "tcp");
        String security = query(uri, "security", "trojan".equals(protocol) ? "tls" : "none");
        String fragment = uri.getFragment();
        String name = fragment == null || fragment.trim().isEmpty()
            ? protocol.toUpperCase(Locale.ROOT) + " server"
            : Uri.decode(fragment);

        requireEndpoint(address, credential, port);
        JSONObject params = new JSONObject();
        for (String key : new String[] {"sni", "fp", "alpn", "flow", "pbk", "sid", "spx", "path", "host", "serviceName", "mode", "type"}) {
            String value = query(uri, key, null);
            if (value != null) params.put(key, value);
        }
        JSONObject stream = streamSettings(network, security, address, params);
        JSONObject user;
        JSONObject server;
        if ("vless".equals(protocol)) {
            user = new JSONObject().put("id", credential).put("encryption", "none");
            if (params.has("flow")) user.put("flow", params.getString("flow"));
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
        String name = fragmentSplit.length == 2 ? Uri.decode(fragmentSplit[1]) : "Shadowsocks server";
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
        String method = Uri.decode(auth.substring(0, separator));
        String password = Uri.decode(auth.substring(separator + 1));
        if (!Arrays.asList(
            "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305",
            "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
            "2022-blake3-chacha20-poly1305"
        ).contains(method)) {
            throw new IllegalArgumentException("Unsupported Shadowsocks cipher.");
        }
        if (body.contains("?plugin=")) {
            throw new IllegalArgumentException("Shadowsocks plugins are not supported.");
        }
        Uri hostUri = Uri.parse("https://" + endpoint);
        String address = hostUri.getHost();
        int port = hostUri.getPort() < 1 ? 443 : hostUri.getPort();
        requireEndpoint(address, password, port);
        JSONObject server = new JSONObject().put("servers", new JSONArray().put(new JSONObject()
            .put("address", address)
            .put("port", port)
            .put("password", password)
            .put("method", method)));
        JSONObject outbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", server)
            .put("streamSettings", new JSONObject().put("network", "tcp").put("security", "none"));
        return new ImportedServer(baseConfig(outbound), name);
    }

    private static JSONObject streamSettings(String network, String security, String address, JSONObject params) throws Exception {
        if (!Arrays.asList("tcp", "ws", "grpc", "http", "httpupgrade", "xhttp", "kcp").contains(network)) {
            throw new IllegalArgumentException("Unsupported transport: " + network);
        }
        if (!Arrays.asList("none", "tls", "reality").contains(security)) {
            throw new IllegalArgumentException("Unsupported security: " + security);
        }
        JSONObject stream = new JSONObject().put("network", network).put("security", security);
        if ("tls".equals(security)) {
            JSONObject tls = new JSONObject()
                .put("serverName", params.optString("sni", address))
                .put("fingerprint", params.optString("fp", "chrome"));
            if (params.has("alpn")) tls.put("alpn", new JSONArray(params.getString("alpn").split(",")));
            stream.put("tlsSettings", tls);
        } else if ("reality".equals(security)) {
            String publicKey = params.optString("pbk");
            if (publicKey.trim().isEmpty()) throw new IllegalArgumentException("Reality requires a public key (pbk).");
            stream.put("realitySettings", new JSONObject()
                .put("serverName", params.optString("sni", address))
                .put("fingerprint", params.optString("fp", "chrome"))
                .put("publicKey", publicKey)
                .put("shortId", params.optString("sid", ""))
                .put("spiderX", params.optString("spx", "/")));
        }
        if ("ws".equals(network)) {
            JSONObject ws = new JSONObject().put("path", params.optString("path", "/"));
            if (params.has("host")) ws.put("headers", new JSONObject().put("Host", params.getString("host")));
            stream.put("wsSettings", ws);
        } else if ("grpc".equals(network)) {
            stream.put("grpcSettings", new JSONObject()
                .put("serviceName", params.optString("serviceName", params.optString("path", "")))
                .put("multiMode", "multi".equals(params.optString("mode"))));
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
            stream.put("kcpSettings", new JSONObject().put("header", new JSONObject().put("type", params.optString("type", "none"))));
        }
        return stream;
    }

    private static JSONObject baseConfig(JSONObject proxy) throws Exception {
        return new JSONObject()
            .put("log", new JSONObject().put("loglevel", "none"))
            .put("dns", new JSONObject().put("servers", new JSONArray().put("1.1.1.1")))
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
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
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
