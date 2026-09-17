package com.lifeadmin.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code lifeadmin.jwt.*}. The secret must be at least 32 bytes for HS256 and is supplied via
 * an environment variable in every real environment (never hard-coded).
 */
@ConfigurationProperties(prefix = "lifeadmin.jwt")
public class JwtProperties {

    private String secret;
    private long accessTtlMinutes = 30;
    private long refreshTtlDays = 14;
    private String issuer = "lifeadmin";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTtlMinutes() { return accessTtlMinutes; }
    public void setAccessTtlMinutes(long accessTtlMinutes) { this.accessTtlMinutes = accessTtlMinutes; }

    public long getRefreshTtlDays() { return refreshTtlDays; }
    public void setRefreshTtlDays(long refreshTtlDays) { this.refreshTtlDays = refreshTtlDays; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
