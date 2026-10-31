package com.handoff.executor;

import java.net.InetAddress;

/** Conservative global-unicast allowlist for the DNS answers actually used by the HTTP client. */
final class PublicAddressPolicy {
    private PublicAddressPolicy() { }
    static boolean allowed(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        int a = b[0] & 255, c = b[1] & 255;
        if (b.length == 4) {
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && c >= 64 && c <= 127)
                    && !(a == 169 && c == 254) && !(a == 172 && c >= 16 && c <= 31)
                    && !(a == 192 && (c == 0 || c == 168 || (c == 88 && (b[2] & 255) == 99)))
                    && !(a == 198 && (c == 18 || c == 19 || (c == 51 && (b[2] & 255) == 100)))
                    && !(a == 203 && c == 0 && (b[2] & 255) == 113);
        }
        if (b.length != 16 || (a & 0xe0) != 0x20) return false;
        // Special-purpose 2001::/23, documentation and IPv4 tunnelling are not short-link servers.
        return !(a == 0x20 && c == 1 && (b[2] & 0xfe) == 0)
                && !(a == 0x20 && c == 1 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)
                && !(a == 0x20 && c == 2) && !(a == 0x3f && (c & 0xf0) == 0xf0);
    }
}
