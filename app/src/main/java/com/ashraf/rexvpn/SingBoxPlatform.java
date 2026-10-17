
package com.ashraf.rexvpn;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public final class SingBoxPlatform {

    private SingBoxPlatform() {
    }

    public static ParcelFileDescriptor establishTun(
            VpnService service,
            String sessionName
    ) {
        VpnService.Builder builder = service.new Builder();

        builder.setSession(
                sessionName == null ? "REX VPN" : sessionName
        );

        builder.setMtu(1500);
        builder.addAddress("172.19.0.1", 30);
        builder.addRoute("0.0.0.0", 0);
        builder.addDnsServer("1.1.1.1");

        return builder.establish();
    }
}
