package com.tongue.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tongue.auth")
public class AuthProperties {

    private String jwtSecret = "change-me-to-a-production-secret-with-at-least-32-characters";
    private long tokenTtlSeconds = 604800L;
    private long smsTtlSeconds = 300L;
    private String devSmsCode = "123456";
    private boolean exposeDevSmsCode = true;
    private boolean allowDevUserId = true;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public long getSmsTtlSeconds() {
        return smsTtlSeconds;
    }

    public void setSmsTtlSeconds(long smsTtlSeconds) {
        this.smsTtlSeconds = smsTtlSeconds;
    }

    public String getDevSmsCode() {
        return devSmsCode;
    }

    public void setDevSmsCode(String devSmsCode) {
        this.devSmsCode = devSmsCode;
    }

    public boolean isExposeDevSmsCode() {
        return exposeDevSmsCode;
    }

    public void setExposeDevSmsCode(boolean exposeDevSmsCode) {
        this.exposeDevSmsCode = exposeDevSmsCode;
    }

    public boolean isAllowDevUserId() {
        return allowDevUserId;
    }

    public void setAllowDevUserId(boolean allowDevUserId) {
        this.allowDevUserId = allowDevUserId;
    }
}
