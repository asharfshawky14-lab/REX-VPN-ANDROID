package com.ashraf.rexvpn;

import android.net.IpPrefix;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.lang.reflect.Method;
import java.net.InetAddress;

public final class SingBoxPlatform {

    private SingBoxPlatform() {
        // Utility class.
    }

    public static ParcelFileDescriptor establishTun(
            VpnService service,
            Object options,
            String sessionName
    ) throws Exception {

        if (service == null) {
            throw new IllegalArgumentException(
                    "خدمة VPN غير متاحة."
            );
        }

        if (options == null) {
            throw new IllegalArgumentException(
                    "إعدادات TUN من Sing-box غير متاحة."
            );
        }

        if (service.prepare(service) != null) {
            throw new IllegalStateException(
                    "إذن VPN غير ممنوح."
            );
        }

        int mtu = ((Number) invoke(options, "getMtu")).intValue();

        if (mtu < 1280 || mtu > 9000) {
            mtu = 1500;
        }

        String session = sessionName == null
                || sessionName.trim().isEmpty()
                ? "REX VPN"
                : sessionName.trim();

        VpnService.Builder builder = service.new Builder()
                .setSession(session)
                .setMtu(mtu);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        addAddresses(
                builder,
                invoke(options, "getInet4Address")
        );

        addAddresses(
                builder,
                invoke(options, "getInet6Address")
        );

        Object autoRoute = invoke(options, "getAutoRoute");

        if (Boolean.TRUE.equals(autoRoute)) {
            addDnsServers(
                    builder,
                    invoke(options, "getDnsServerAddress")
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addCidrRoutes(
                        builder,
                        invoke(options, "getInet4RouteAddress"),
                        false
                );

                addCidrRoutes(
                        builder,
                        invoke(options, "getInet6RouteAddress"),
                        false
                );

                addCidrRoutes(
                        builder,
                        invoke(options, "getInet4RouteExcludeAddress"),
                        true
                );

                addCidrRoutes(
                        builder,
                        invoke(options, "getInet6RouteExcludeAddress"),
                        true
                );
            } else {
                addNetworkRoutes(
                        builder,
                        invoke(options, "getInet4RouteRange")
                );

                addNetworkRoutes(
                        builder,
                        invoke(options, "getInet6RouteRange")
                );
            }
        }

        ParcelFileDescriptor descriptor = builder.establish();

        if (descriptor == null) {
            throw new IllegalStateException(
                    "تعذر على أندرويد إنشاء نفق TUN."
            );
        }

        return descriptor;
    }

    private static void addAddresses(
            VpnService.Builder builder,
            Object iterator
    ) throws Exception {

        while (hasNext(iterator)) {
            Object address = next(iterator);

            String ip = String.valueOf(
                    invoke(address, "address")
            );

            int prefix = ((Number) invoke(
                    address,
                    "prefix"
            )).intValue();

            builder.addAddress(ip, prefix);
        }
    }

    private static void addDnsServers(
            VpnService.Builder builder,
            Object iterator
    ) throws Exception {

        while (hasNext(iterator)) {
            Object value = next(iterator);

            if (value == null) {
                continue;
            }

            String dns = value.toString().trim();

            if (!dns.isEmpty()) {
                builder.addDnsServer(dns);
            }
        }
    }

    private static void addNetworkRoutes(
            VpnService.Builder builder,
            Object iterator
    ) throws Exception {

        while (hasNext(iterator)) {
            Object route = next(iterator);

            String ip = String.valueOf(
                    invoke(route, "address")
            );

            int prefix = ((Number) invoke(
                    route,
                    "prefix"
            )).intValue();

            builder.addRoute(ip, prefix);
        }
    }

    private static void addCidrRoutes(
            VpnService.Builder builder,
            Object iterator,
            boolean exclude
    ) throws Exception {

        while (hasNext(iterator)) {
            Object value = next(iterator);

            if (value == null) {
                continue;
            }

            String cidr = value.toString().trim();
            String[] parts = cidr.split("/", -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "مسار شبكة غير صالح: " + cidr
                );
            }

            int prefix;

            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "قناع شبكة غير صالح: " + cidr,
                        e
                );
            }

            InetAddress address = InetAddress.getByName(
                    parts[0]
            );

            IpPrefix ipPrefix = new IpPrefix(
                    address,
                    prefix
            );

            if (exclude) {
                builder.excludeRoute(ipPrefix);
            } else {
                builder.addRoute(
                        ipPrefix.getAddress(),
                        ipPrefix.getPrefixLength()
                );
            }
        }
    }

    private static Object invoke(
            Object target,
            String methodName
    ) throws Exception {

        if (target == null) {
            throw new IllegalArgumentException(
                    "قيمة ناقصة من إعدادات TUN: " + methodName
            );
        }

        Method method = target.getClass().getMethod(methodName);

        return method.invoke(target);
    }

    private static boolean hasNext(Object iterator)
            throws Exception {

        return iterator != null
                && Boolean.TRUE.equals(
                        invoke(iterator, "hasNext")
                );
    }

    private static Object next(Object iterator)
            throws Exception {

        return invoke(iterator, "next");
    }
}
