package com.aigicrm.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpUtilsTest {

    private HttpServletRequest req(String remoteAddr, String xRealIp, String xff) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRemoteAddr()).thenReturn(remoteAddr);
        when(r.getHeader("X-Real-IP")).thenReturn(xRealIp);
        return r;
    }

    @Test
    void trustsXRealIp_whenDirectIpIsTrustedProxy() {
        // 直连 IP 为 docker 内网（可信代理）时采用 nginx 覆盖的 X-Real-IP
        assertEquals("203.0.113.7", ClientIpUtils.getClientIp(req("172.17.0.2", "203.0.113.7", "1.2.3.4")));
    }

    @Test
    void ignoresForgedHeaders_whenDirectIpIsPublic() {
        // 公网直连（如 8080 直连攻击者）：伪造的 X-Real-IP 不被信任，用直连 IP
        assertEquals("203.0.113.99", ClientIpUtils.getClientIp(req("203.0.113.99", "1.2.3.4", "5.6.7.8")));
    }

    @Test
    void ignoresXForwardedFor_completely() {
        // X-Forwarded-For 可被客户端伪造，一律不采用
        assertEquals("203.0.113.7", ClientIpUtils.getClientIp(req("172.17.0.2", "203.0.113.7", "6.6.6.6, 7.7.7.7")));
    }

    @Test
    void fallsBackToRemoteAddr_whenNoXRealIp() {
        assertEquals("172.17.0.2", ClientIpUtils.getClientIp(req("172.17.0.2", null, "1.2.3.4")));
    }

    @Test
    void cidrMatching_coversPrivateRangesOnly() {
        assertTrue(ClientIpUtils.isTrustedProxy("10.1.2.3"));
        assertTrue(ClientIpUtils.isTrustedProxy("172.16.0.1"));
        assertTrue(ClientIpUtils.isTrustedProxy("172.31.255.255"));
        assertTrue(ClientIpUtils.isTrustedProxy("192.168.0.1"));
        assertFalse(ClientIpUtils.isTrustedProxy("8.8.8.8"));
        assertFalse(ClientIpUtils.isTrustedProxy("172.15.255.255"));
        assertFalse(ClientIpUtils.isTrustedProxy("172.32.0.1"));
        assertFalse(ClientIpUtils.isTrustedProxy("invalid"));
        assertFalse(ClientIpUtils.isTrustedProxy(null));
    }
}
