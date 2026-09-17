package com.lifeadmin.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base for business exceptions that map to a specific HTTP status + machine-readable code
 * (see API_SPEC §Conventions). Subclasses set the status and code.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(final HttpStatus status, final String code, final String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
