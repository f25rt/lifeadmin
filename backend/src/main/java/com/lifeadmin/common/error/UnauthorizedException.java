package com.lifeadmin.common.error;

import org.springframework.http.HttpStatus;

/** Invalid credentials or refresh token → HTTP 401. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(final String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }
}
