package com.lifeadmin.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit knobs (Phase 5 hardening). A fixed window per client IP protects the abuse-prone
 * endpoints: authentication (credential stuffing) and upload/process (expensive OCR/AI work). All
 * env-overridable; {@code enabled=false} turns it off (used in tests).
 */
@ConfigurationProperties(prefix = "lifeadmin.rate-limit")
public class RateLimitProperties {

    /** Master switch. */
    private boolean enabled = true;

    /** Window length in seconds. */
    private int windowSeconds = 60;

    /** Max auth requests (login/register/refresh) per IP per window. */
    private int authPerWindow = 20;

    /** Max upload/process requests per IP per window. */
    private int uploadPerWindow = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(final int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getAuthPerWindow() {
        return authPerWindow;
    }

    public void setAuthPerWindow(final int authPerWindow) {
        this.authPerWindow = authPerWindow;
    }

    public int getUploadPerWindow() {
        return uploadPerWindow;
    }

    public void setUploadPerWindow(final int uploadPerWindow) {
        this.uploadPerWindow = uploadPerWindow;
    }
}
