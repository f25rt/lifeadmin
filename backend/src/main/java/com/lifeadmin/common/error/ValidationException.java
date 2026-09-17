package com.lifeadmin.common.error;

import org.springframework.http.HttpStatus;

/** A bad request that isn't bean-validation (e.g. unsupported/oversized/malformed upload). */
public class ValidationException extends ApiException {
    public ValidationException(final String code, final String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
