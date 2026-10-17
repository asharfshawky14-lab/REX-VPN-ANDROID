package com.ashraf.rexvpn;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class SingBoxConfigBuilder {

    private SingBoxConfigBuilder() {
    }

    public static JSONObject fromJson(String json)
            throws JSONException {

        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ملف الإعدادات فارغ."
            );
        }

        JSONObject config = new JSONObject(json);

        JSONArray inbounds = config.optJSONArray("inbounds");
        JSONArray outbounds = config.optJSONArray("outbounds");

        if (inbounds == null || inbounds.length() == 0) {
            throw new IllegalArgumentException(
                    "إعدادات Sing-box لازم تحتوي على inbounds."
            );
        }

        if (outbounds == null || outbounds.length() == 0) {
            throw new IllegalArgumentException(
                    "إعدادات Sing-box لازم تحتوي على outbounds."
            );
        }

        return config;
    }

    public static String detectProtocol(String link) {
        if (link == null || link.trim().isEmpty()) {
            return "unknown";
        }

        String value = link.trim().toLowerCase(Locale.ROOT);

        if (value.startsWith("vless://")) return "vless";
        if (value.startsWith("vmess://")) return "vmess";
        if (value.startsWith("trojan://")) return "trojan";
        if (value.startsWith("ss://")) return "shadowsocks";

        if (value.startsWith("hysteria2://")
                || value.startsWith("hy2://")) {
            return "hysteria2";
        }

        if (value.startsWith("tuic://")) return "tuic";
        if (value.startsWith("anytls://")) return "anytls";

        if (value.startsWith("socks://")
                || value.startsWith("socks5://")) {
            return "socks";
        }

        if (value.startsWith("http://")
                || value.startsWith("https://")) {
            return "http";
        }

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
        return "vless".equals(detectProtocol(link));
    }

    public static JSONObject fromVlessLink(String link)
            throws JSONException {

        if (link == null
                || !"vless".equals(detectProtocol(link))) {
            throw new IllegalArgumentException(
                    "الرابط مش رابط VLESS صالح."
            );
        }

        Uri uri = Uri.parse(link.trim());

        String uuid = uri.getUserInfo();
        String host = uri.getHost();
        int port = uri.getPort();

        if (uuid == null || uuid.trim().isEmpty()
                || host == null || host.trim().isEmpty()
                || port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "رابط VLESS ناقص UUID أو عنوان السيرفر أو المنفذ."
            );
        }

        String security = query(uri, "security", "none")
                .toLowerCase(Locale.ROOT);

        String transportType = query(uri, "type", "tcp")
                .toLowerCase(Locale.ROOT);

        String encryption = query(uri, "encryption", "none");

        if (!"none".equalsIgnoreCase(encryption)) {
            throw new IllegalArgumentException(
                    "تشفير VLESS المحدد غير مدعوم."
            );
        }

        if (!"none".equals(security)
                && !"tls".equals(security)
                && !"reality".equals(security)) {
            throw new IllegalArgumentException(
                    "نوع أمان VLESS غير مدعوم: " + security
            );
        }

        if (!"tcp".equals(transportType)
                && !"ws".equals(transportType)
                && !"grpc".equals(transportType)
                && !"http".equals(transportType)) {
            throw new IllegalArgumentException(
                    "نوع النقل غير مدعوم: " + transportType
            );
        }

        JSONObject outbound = new JSONObject();
        outbound.put("type", "vless");
        outbound.put("tag", "proxy");
        outbound.put("server", host);
        outbound.put("server_port", port);
        outbound.put("uuid", uuid);

        String flow = query(uri, "flow", "");
        if (!flow.isEmpty()) {
            if (!"xtls-rprx-vision".equals(flow)) {
                throw new IllegalArgumentException(
                        "قيمة VLESS flow غير مدعومة: " + flow
                );
            }

            outbound.put("flow", flow);
        }

        if ("tls".equals(security) || "reality".equals(security)) {
            JSONObject tls = new JSONObject();
            tls.put("enabled", true);

            String serverName = query(
                    uri,
                    "sni",
                    query(uri, "servername", host)
            );

            tls.put("server_name", serverName);

            String alpn = query(uri, "alpn", "");
            if (!alpn.isEmpty()) {
                JSONArray alpnList = new JSONArray();

                for (String item : alpn.split(",")) {
                    String value = item.trim();
                    if (!value.isEmpty()) {
                        alpnList.put(value);
                    }
                }

                if (alpnList.length() > 0) {
                    tls.put("alpn", alpnList);
                }
            }

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
                            "رابط Reality ناقص المفتاح العام pbk."
                    );
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
        }

        if ("ws".equals(transportType)) {
            JSONObject transport = new JSONObject();
            transport.put("type", "ws");
            transport.put(
                    "path",
                    query(uri, "path", "/")
            );

            String wsHost = query(uri, "host", "");
            if (!wsHost.isEmpty()) {
                JSONObject headers = new JSONObject();
                headers.put("Host", wsHost);
                transport.put("headers", headers);
            }

            String earlyData = query(uri, "ed", "");
            if (!earlyData.isEmpty()) {
                try {
                    int value = Integer.parseInt(earlyData);
                    if (value > 0) {
                        transport.put("max_early_data", value);
                        transport.put(
                                "early_data_header_name",
                                query(
                                        uri,
                                        "eh",
                                        "Sec-WebSocket-Protocol"
                                )
                        );
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore an invalid optional early-data value.
                }
            }

            outbound.put("transport", transport);

        } else if ("grpc".equals(transportType)) {
            JSONObject transport = new JSONObject();
            transport.put("type", "grpc");

            String serviceName = query(
                    uri,
                    "serviceName",
                    query(uri, "service_name", "")
            );

            if (!serviceName.isEmpty()) {
                transport.put("service_name", serviceName);
            }

            outbound.put("transport", transport);

        } else if ("http".equals(transportType)) {
            JSONObject transport = new JSONObject();
            transport.put("type", "http");
            transport.put(
                    "path",
                    query(uri, "path", "/")
            );

            String httpHost = query(uri, "host", "");
            if (!httpHost.isEmpty()) {
                transport.put(
                        "host",
                        new JSONArray().put(httpHost)
                );
            }

            outbound.put("transport", transport);
        }

        JSONObject tun = new JSONObject();
        tun.put("type", "tun");
        tun.put("tag", "tun-in");
        tun.put("interface_name", "rex0");
        tun.put(
                "address",
                new JSONArray()
                        .put("172.19.0.1/30")
                        .put("fdfe:dcba:9876::1/126")
        );
        tun.put("auto_route", true);
        tun.put("strict_route", true);
        tun.put("stack", "system");

        JSONObject dnsServer = new JSONObject();
        dnsServer.put("type", "udp");
        dnsServer.put("tag", "dns-remote");
        dnsServer.put("server", "1.1.1.1");
        dnsServer.put("server_port", 53);
        dnsServer.put("detour", "proxy");

        JSONObject dns = new JSONObject();
        dns.put(
                "servers",
                new JSONArray().put(dnsServer)
        );
        dns.put("final", "dns-remote");

        JSONArray outbounds = new JSONArray();
        outbounds.put(outbound);
        outbounds.put(
                new JSONObject()
                        .put("type", "direct")
                        .put("tag", "direct")
        );
        outbounds.put(
                new JSONObject()
                        .put("type", "dns")
                        .put("tag", "dns-out")
        );

        JSONObject route = new JSONObject();
        route.put("auto_detect_interface", true);
        route.put("final", "proxy");

        JSONObject config = new JSONObject();
        config.put(
                "log",
                new JSONObject().put("level", "info")
        );
        config.put("dns", dns);
        config.put(
                "inbounds",
                new JSONArray().put(tun)
        );
        config.put("outbounds", outbounds);
        config.put("route", route);

        return config;
    }

    private static String query(
            Uri uri,
            String key,
            String fallback) {

        String value = uri.getQueryParameter(key);

        return value == null || value.isEmpty()
                ? fallback
                : value;
    }
}
