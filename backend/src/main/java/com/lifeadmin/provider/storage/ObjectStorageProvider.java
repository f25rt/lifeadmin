package com.lifeadmin.provider.storage;

import java.net.URI;

/**
 * Abstraction over the object store (spec §17/§36). Keeps the storage SDK out of the domain so it
 * can be swapped (MinIO ↔ S3 ↔ R2 ↔ GCS) without touching business code. Concrete impl is chosen by
 * Spring profile/config.
 */
public interface ObjectStorageProvider {

    /** Stores bytes under {@code key} with the given content type. Returns the stored key. */
    String put(String key, byte[] content, String contentType);

    /** Fetches the object bytes for {@code key}. */
    byte[] get(String key);

    /** Issues a short-lived pre-signed GET URL for {@code key} (private objects, G10). */
    URI presignedGet(String key);

    /** Deletes the object for {@code key} (no-op if absent). Used by hard-delete (G5). */
    void delete(String key);
}
