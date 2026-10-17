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
            return result(
                    Engine.UNKNOWN,
                    "غير معروف",
                    "اختار ملفًا أو الصق رابط الكونفيج أولًا."
            );
        }

        String value = input.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if ((lower.contains("[interface]")
                && lower.contains("[peer]"))
                || lower.startsWith("wireguard://")) {
            return result(
                    Engine.WIREGUARD,
                    "WireGuard",
                    "تم التعرف على صيغة WireGuard؛ يلزم التحقق من الملف."
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

        if (lower.startsWith("anytls://")) {
            return singBox("AnyTLS");
        }

        if (lower.startsWith("socks://")
                || lower.startsWith("socks5://")) {
            return singBox("SOCKS");
        }

        if (lower.startsWith("http://")
                || lower.startsWith("https://")) {
            return result(
                    Engine.UNKNOWN,
                    "HTTP/HTTPS",
                    "الرابط قد يكون موقعًا عاديًا أو بروكسي؛ "
                            + "لا يمكن تحديد النوع من البداية وحدها."
            );
        }

        if (lower.contains("<openvpn")
                || (lower.contains("client")
                && lower.contains("remote ")
                && lower.contains("dev tun"))) {
            return result(
                    Engine.OPENVPN,
                    "OpenVPN",
                    "تم التعرف مبدئيًا على OpenVPN؛ "
                            + "يلزم محركه للتحقق والتشغيل."
            );
        }

        if (lower.contains("ikev2")
                || lower.contains("ipsec")) {
            return result(
                    Engine.IKEV2,
                    "IKEv2/IPsec",
                    "قد تكون إعدادات IKEv2/IPsec؛ "
                            + "تحتاج إلى التحقق من الصيغة."
            );
        }

        return result(
                Engine.UNKNOWN,
                "غير معروف",
                "لم يتم التعرف على نوع الكونفيج."
        );
    }

    private static Detection singBox(String protocol) {
        return result(
                Engine.SING_BOX,
                protocol,
                "تم التعرف على النوع فقط؛ "
                        + "التشغيل يتطلب محركًا مدمجًا وإعدادًا صالحًا."
        );
    }

    private static Detection result(
            Engine engine,
            String protocol,
            String message) {
        return new Detection(engine, protocol, message);
    }
}
