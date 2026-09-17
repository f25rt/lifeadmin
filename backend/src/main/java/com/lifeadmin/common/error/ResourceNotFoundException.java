package com.lifeadmin.common.error;

import org.springframework.http.HttpStatus;

/** A requested resource does not exist (or is not visible to the caller) → HTTP 404. */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(final String resource, final Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found: " + id);
    }
}
