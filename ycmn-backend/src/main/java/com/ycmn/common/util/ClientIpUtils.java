package com.ycmn.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 取址（登录限流/审计用）。
 *
 * 只信任可信代理（nginx）无条件覆盖的 X-Real-IP：仅当直连 IP 属于可信代理
 * 网段时才采用 X-Real-IP，否则用直连 IP。客户端可伪造的 X-Forwarded-For
 * 一律忽略，防止伪造头绕过登录限流或消耗他人登录名额。
 */
public final class ClientIpUtils {

    private ClientIpUtils() {}

    /** 可信代理网段（CIDR 列表，默认私有网段；可经 security.trusted-proxy-cidrs 覆盖） */
    private static volatile String[] trustedCidrs = new String[]{
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"
    };

    /** 由 TrustedProxyConfig 在启动时按配置覆盖 */
    public static void configureTrustedCidrs(String cidrs) {
        if (cidrs == null || cidrs.isBlank()) return;
        trustedCidrs = cidrs.split(",");
    }

    /** 取客户端真实 IP：直连 IP 属可信代理网段时采用 X-Real-IP，否则用直连 IP */
    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }
        if (isTrustedProxy(remoteAddr)) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    /** 直连 IP 是否属于可信代理网段 */
    public static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) return false;
        long addr = ipToLong(ip);
        if (addr == -1) return false;
        for (String cidr : trustedCidrs) {
            if (cidr == null || cidr.isBlank()) continue;
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) continue;
            long network = ipToLong(parts[0]);
            if (network == -1) continue;
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (prefix < 0 || prefix > 32) continue;
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            if ((addr & mask) == (network & mask)) return true;
        }
        return false;
    }

    private static long ipToLong(String ip) {
        try {
            String[] parts = ip.trim().split("\\.");
            if (parts.length != 4) return -1;
            long result = 0;
            for (String p : parts) {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) return -1;
                result = (result << 8) | v;
            }
            return result;
        } catch (Exception e) {
            return -1;
        }
    }
}
