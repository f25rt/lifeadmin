package com.lifeadmin.common.error;

import org.springframework.http.HttpStatus;

/** Plan limit reached (e.g. free-tier document count) → HTTP 403 with code {@code QUOTA_EXCEEDED}. */
public class QuotaExceededException extends ApiException {
    public QuotaExceededException(final String message) {
        super(HttpStatus.FORBIDDEN, "QUOTA_EXCEEDED", message);
    }
}
