package com.ashraf.rexvpn;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Converts supported VLESS links into sing-box JSON.
 * This class builds configuration only. It does not start a VPN tunnel.
 */
public final class SingBoxConfigBuilder {

    private SingBoxConfigBuilder() {}

    public static JSONObject fromJson(String json)
            throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration cannot be empty.");
        }

        JSONObject config = new JSONObject(json);
        JSONArray inbounds = config.optJSONArray("inbounds");
        JSONArray outbounds = config.optJSONArray("outbounds");

        if (inbounds == null || inbounds.length() == 0) {
            throw new IllegalArgumentException(
                    "Configuration must contain inbounds.");
        }

        if (outbounds == null || outbounds.length() == 0) {
            throw new IllegalArgumentException(
                    "Configuration must contain outbounds.");
        }

        return config;
    }

    public static String detectProtocol(String link) {
        if (link == null || link.trim().isEmpty()) return "unknown";

        String value = link.trim().toLowerCase(Locale.ROOT);

        if (value.startsWith("vless://")) return "vless";
        if (value.startsWith("vmess://")) return "vmess";
        if (value.startsWith("trojan://")) return "trojan";
        if (value.startsWith("ss://")) return "shadowsocks";
        if (value.startsWith("hysteria2://")
                || value.startsWith("hy2://")) return "hysteria2";
        if (value.startsWith("tuic://")) return "tuic";
        if (value.startsWith("anytls://")) return "anytls";
        if (value.startsWith("socks://")
                || value.startsWith("socks5://")) return "socks";
        if (value.startsWith("http://")
                || value.startsWith("https://")) return "http";

        return "unknown";
    }

    public static String getServerHost(String link) {
        try {
            String host = Uri.parse(link.trim()).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static int getServerPort(String link) {
        try {
            int port = Uri.parse(link.trim()).getPort();
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static boolean isSupportedLink(String link) {
        return !"unknown".equals(detectProtocol(link));
    }

    /**
     * Converts VLESS links using TCP, WebSocket or gRPC transport.
     * Reality links require the pbk public key.
     */
    public static JSONObject fromVlessLink(String link)
            throws JSONException {

        if (link == null
                || !link.trim().toLowerCase(Locale.ROOT)
                .startsWith("vless://")) {
            throw new IllegalArgumentException("الرابط ليس VLESS.");
        }

        Uri uri = Uri.parse(link.trim());
        String uuid = uri.getUserInfo();
        String host = uri.getHost();
        int port = uri.getPort();

        if (uuid == null || uuid.trim().isEmpty()
                || host == null || host.trim().isEmpty()
                || port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "رابط VLESS ناقص: تأكد من UUID وعنوان السيرفر والمنفذ.");
        }

        String security = query(uri, "security", "none")
                .toLowerCase(Locale.ROOT);
        String transportType = query(uri, "type", "tcp")
                .toLowerCase(Locale.ROOT);

        JSONObject outbound = new JSONObject();
        outbound.put("type", "vless");
        outbound.put("tag", "proxy");
        outbound.put("server", host);
        outbound.put("server_port", port);
        outbound.put("uuid", uuid);

        if ("tls".equals(security) || "reality".equals(security)) {
            JSONObject tls = new JSONObject();
            tls.put("enabled", true);
            tls.put("server_name",
                    query(uri, "sni", query(uri, "servername", host)));

            String fingerprint = query(uri, "fp", "");
            if (!fingerprint.isEmpty()) {
                JSONObject utls = new JSONObject();
                utls.put("enabled", true);
                utls.put("fingerprint", fingerprint);
                tls.put("utls", utls);
            }

            if ("reality".equals(security)) {
                String publicKey = query(uri, "pbk", "");
                if (publicKey.isEmpty()) {
                    throw new IllegalArgumentException(
                            "رابط Reality ناقص المفتاح العام pbk.");
                }

                JSONObject reality = new JSONObject();
                reality.put("enabled", true);
                reality.put("public_key", publicKey);

                String shortId = query(uri, "sid", "");
                if (!shortId.isEmpty()) {
                    reality.put("short_id", shortId);
                }

                tls.put("reality", reality);
            }

            outbound.put("tls", tls);

        } else if (!"none".equals(security)
                && !security.isEmpty()) {
            throw new IllegalArgumentException(
                    "نوع تشفير VLESS غير مدعوم: " + security);
        }

        if ("ws".equals(transportType)) {
            JSONObject transport = new JSONObject();
            transport.put("type", "ws");
            transport.put("path", query(uri, "path", "/"));

            String wsHost = query(uri, "host", "");
            if (!wsHost.isEmpty()) {
                JSONObject headers = new JSONObject();
                headers.put("Host", wsHost);
                transport.put("headers", headers);
            }

            outbound.put("transport", transport);

        } else if ("grpc".equals(transportType)) {
            JSONObject transport = new JSONObject();
            transport.put("type", "grpc");

            String serviceName = query(
                    uri, "serviceName",
                    query(uri, "service_name", ""));

            if (!serviceName.isEmpty()) {
                transport.put("service_name", serviceName);
            }

            outbound.put("transport", transport);

        } else if (!"tcp".equals(transportType)
                && !"http".equals(transportType)) {
            throw new IllegalArgumentException(
                    "نقل VLESS غير مدعوم حاليًا: " + transportType);
        }

        JSONObject tun = new JSONObject();
        tun.put("type", "tun");
        tun.put("tag", "tun-in");
        tun.put("interface_name", "rex0");
        tun.put("address", new JSONArray()
                .put("172.19.0.1/30")
                .put("fdfe:dcba:9876::1/126"));
        tun.put("auto_route", true);
        tun.put("strict_route", true);
        tun.put("stack", "system");

        JSONObject dnsServer = new JSONObject();
        dnsServer.put("tag", "dns-remote");
        dnsServer.put("address", "1.1.1.1");
        dnsServer.put("detour", "proxy");

        JSONObject dns = new JSONObject();
        dns.put("servers", new JSONArray().put(dnsServer));
        dns.put("final", "dns-remote");

        JSONObject route = new JSONObject();
        route.put("auto_detect_interface", true);
        route.put("final", "proxy");

        JSONArray outbounds = new JSONArray();
        outbounds.put(outbound);
        outbounds.put(new JSONObject()
                .put("type", "direct")
                .put("tag", "direct"));
        outbounds.put(new JSONObject()
                .put("type", "dns")
                .put("tag", "dns-out"));

        JSONObject config = new JSONObject();
        config.put("log", new JSONObject().put("level", "info"));
        config.put("dns", dns);
        config.put("inbounds", new JSONArray().put(tun));
        config.put("outbounds", outbounds);
        config.put("route", route);

        return config;
    }

    private static String query(
            Uri uri, String key, String fallback) {
        String value = uri.getQueryParameter(key);
        return value == null || value.isEmpty()
                ? fallback : value;
    }
}
