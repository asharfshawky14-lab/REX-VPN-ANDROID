
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

        Detection(Engine engine, String protocol, String message) {
            this.engine = engine;
            this.protocol = protocol;
            this.message = message;
        }
    }

    private ProtocolCatalog() {
    }

    public static Detection detect(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new Detection(
                    Engine.UNKNOWN,
                    "غير معروف",
                    "اختار ملفًا أو الصق رابط كونفيج أولًا."
            );
        }

        String value = input.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.startsWith("wireguard://")
                || lower.contains("[interface]")
                && lower.contains("[peer]")) {
            return new Detection(
                    Engine.WIREGUARD,
                    "WireGuard",
                    "تم التعرف على صيغة WireGuard."
            );
        }

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

        if (lower.startsWith("socks://")
                || lower.startsWith("socks5://")
                || lower.startsWith("http://")
                || lower.startsWith("https://")) {
            return singBox("Proxy");
        }

        if (lower.startsWith("ssh://")) {
            return singBox("SSH");
        }

        if (lower.startsWith("anytls://")) {
            return singBox("AnyTLS");
        }

        if (lower.contains("<openvpn")
                || lower.contains("client")
                && lower.contains("remote ")
                && lower.contains("dev tun")) {
            return new Detection(
                    Engine.OPENVPN,
                    "OpenVPN",
                    "يبدو أنه ملف OpenVPN؛ يحتاج إلى محرك OpenVPN."
            );
        }

        if (lower.contains("ikev2")
                || lower.contains("ipsec")) {
            return new Detection(
                    Engine.IKEV2,
                    "IKEv2/IPsec",
                    "قد يكون إعداد IKEv2/IPsec؛ يلزم التحقق من صيغة الإعداد."
            );
        }

        if (lower.startsWith("https://")
                && (lower.contains("clash")
                || lower.contains("sing-box"))) {
            return singBox("اشتراك أو إعداد sing-box");
        }

        return new Detection(
                Engine.UNKNOWN,
                "غير معروف",
                "لم يتم التعرف على الصيغة تلقائيًا."
        );
    }

    private static Detection singBox(String protocol) {
        return new Detection(
                Engine.SING_BOX,
                protocol,
                "تم التعرف على النوع مبدئيًا؛ يجب التحقق من الإعداد وتشغيل المحرك."
        );
    }
}
