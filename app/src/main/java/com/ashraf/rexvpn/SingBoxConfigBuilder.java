
package com.ashraf.rexvpn;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Builds and validates sing-box configuration JSON.
 *
 * This class prepares configuration only.
 * It does not start a VPN connection or create a TUN interface.
 */
public final class SingBoxConfigBuilder {

    private SingBoxConfigBuilder() {
        // Utility class.
    }

    /**
     * Accepts a complete sing-box JSON configuration.
     * The configuration must contain inbounds and outbounds.
     */
    public static JSONObject fromJson(String json)
            throws JSONException {

        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration cannot be empty."
            );
        }

        JSONObject config = new JSONObject(json);

        JSONArray inbounds = config.optJSONArray("inbounds");
        JSONArray outbounds = config.optJSONArray("outbounds");

        if (inbounds == null || inbounds.length() == 0) {
            throw new IllegalArgumentException(
                    "Configuration must contain inbounds."
            );
        }

        if (outbounds == null || outbounds.length() == 0) {
            throw new IllegalArgumentException(
                    "Configuration must contain outbounds."
            );
        }

        return config;
    }

    /**
     * Identifies common sing-box proxy links.
     * This method does not convert them into a working VPN configuration.
     */
    public static String detectProtocol(String link) {
        if (link == null || link.trim().isEmpty()) {
            return "unknown";
        }

        String value = link.trim().toLowerCase(Locale.ROOT);

        if (value.startsWith("vless://")) {
            return "vless";
        }

        if (value.startsWith("vmess://")) {
            return "vmess";
        }

        if (value.startsWith("trojan://")) {
            return "trojan";
        }

        if (value.startsWith("ss://")) {
            return "shadowsocks";
        }

        if (value.startsWith("hysteria2://")
                || value.startsWith("hy2://")) {
            return "hysteria2";
        }

        if (value.startsWith("tuic://")) {
            return "tuic";
        }

        if (value.startsWith("anytls://")) {
            return "anytls";
        }

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

    /**
     * Extracts the host from a proxy URI when available.
     */
    public static String getServerHost(String link) {
        if (link == null || link.trim().isEmpty()) {
            return "";
        }

        try {
            Uri uri = Uri.parse(link.trim());
            String host = uri.getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Extracts the port from a proxy URI.
     * Returns -1 when no valid explicit port is present.
     */
    public static int getServerPort(String link) {
        if (link == null || link.trim().isEmpty()) {
            return -1;
        }

        try {
            Uri uri = Uri.parse(link.trim());
            int port = uri.getPort();

            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (Exception ignored) {
            // Invalid URI.
        }

        return -1;
    }

    /**
     * Checks whether a string resembles a supported proxy URI.
     */
    public static boolean isSupportedLink(String link) {
        return !"unknown".equals(detectProtocol(link));
    }
}
