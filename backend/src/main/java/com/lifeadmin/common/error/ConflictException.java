package com.lifeadmin.common.error;

import org.springframework.http.HttpStatus;

/** A business conflict (e.g. duplicate email) → HTTP 409. */
public class ConflictException extends ApiException {
    public ConflictException(final String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
