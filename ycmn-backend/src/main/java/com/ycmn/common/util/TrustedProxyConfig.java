package com.ycmn.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 启动时按配置注入可信代理网段（security.trusted-proxy-cidrs）。
 * 默认覆盖 docker 内网私有网段；多级代理环境用 TRUSTED_PROXY_CIDRS 环境变量覆盖。
 */
@Component
public class TrustedProxyConfig {

    public TrustedProxyConfig(@Value("${security.trusted-proxy-cidrs:10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}") String cidrs) {
        ClientIpUtils.configureTrustedCidrs(cidrs);
    }
}
