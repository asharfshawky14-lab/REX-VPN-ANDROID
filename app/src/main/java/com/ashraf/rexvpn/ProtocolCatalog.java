package com.ashraf.rexvpn;

import java.util.Locale;

public final class ProtocolCatalog {

    public enum Engine {
        SING_BOX,
        WIREGUARD,
        OPENVPN,
        IKEV2,
        UNKNOWN
    }

    public static final class Detection {
        public final Engine engine;
        public final String protocol;
        public final String message;

        Detection(
                Engine engine,
                String protocol,
                String message) {
            this.engine = engine;
            this.protocol = protocol;
            this.message = message;
        }
    }

    private ProtocolCatalog() {
    }

    public static Detection detect(String input) {
        if (input == null || input.trim().isEmpty()) {
            return result(
                    Engine.UNKNOWN,
                    "غير معروف",
                    "اختار ملف أو الصق رابط الكونفيج الأول."
            );
        }

        String value = input.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        // WireGuard configuration
        if ((lower.contains("[interface]")
                && lower.contains("[peer]"))
                || lower.startsWith("wireguard://")) {
            return result(
                    Engine.WIREGUARD,
                    "WireGuard",
                    "تم التعرف على صيغة WireGuard."
            );
        }

        // Sing-box protocols
        if (lower.startsWith("vless://")) {
            return singBox("VLESS");
        }

        if (lower.startsWith("vmess://")) {
            return singBox("VMess");
        }

        if (lower.startsWith("trojan://")) {
            return singBox("Trojan");
        }

        if (lower.startsWith("ss://")) {
            return singBox("Shadowsocks");
        }

        if (lower.startsWith("hysteria2://")
                || lower.startsWith("hy2://")) {
            return singBox("Hysteria 2");
        }

        if (lower.startsWith("tuic://")) {
            return singBox("TUIC");
        }

        if (lower.startsWith("anytls://")) {
            return singBox("AnyTLS");
        }

        if (lower.startsWith("socks://")
                || lower.startsWith("socks5://")) {
            return singBox("SOCKS");
        }

        // An HTTP/HTTPS URL is not necessarily a proxy.
        if (lower.startsWith("http://")
                || lower.startsWith("https://")) {
            return result(
                    Engine.UNKNOWN,
                    "HTTP/HTTPS",
                    "الرابط ممكن يكون موقعًا عاديًا أو بروكسي HTTP؛ "
                            + "لازم نتحقق من نوعه."
            );
        }

        // OpenVPN configuration
        if (lower.contains("<openvpn")
                || (lower.contains("client")
                && lower.contains("remote ")
                && lower.contains("dev tun"))) {
            return result(
                    Engine.OPENVPN,
                    "OpenVPN",
                    "تم التعرف مبدئيًا على OpenVPN، "
                            + "لكن محرك تشغيله غير مدمج حتى الآن."
            );
        }

        // IKEv2/IPsec configuration
        if (lower.contains("ikev2")
                || lower.contains("ipsec")) {
            return result(
                    Engine.IKEV2,
                    "IKEv2/IPsec",
                    "تم التعرف مبدئيًا على IKEv2/IPsec؛ "
                            + "يلزم دعم محركه وإعداداته."
            );
        }

        return result(
                Engine.UNKNOWN,
                "غير معروف",
                "لم نتمكن من التعرف على نوع الكونفيج."
        );
    }

    private static Detection singBox(String protocol) {
        return result(
                Engine.SING_BOX,
                protocol,
                "تم التعرف على نوع البروتوكول؛ "
                        + "التشغيل يعتمد على دعم التحويل "
                        + "وصحة إعدادات الاتصال."
        );
    }

    private static Detection result(
            Engine engine,
            String protocol,
            String message) {
        return new Detection(engine, protocol, message);
    }
}
