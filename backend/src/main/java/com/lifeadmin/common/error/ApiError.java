package com.lifeadmin.common.error;

import java.time.Instant;

/** Standard error envelope returned by all endpoints (API_SPEC §Conventions). */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId) {
}
