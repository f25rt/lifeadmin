package com.lifeadmin.provider.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.lifeadmin.common.error.ResourceNotFoundException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-filesystem {@link ObjectStorageProvider} for development when no S3/MinIO endpoint is
 * available. Objects are written under a base directory keyed by their storage key. "Pre-signed"
 * downloads are served as {@code file://} URLs (dev only — a real deployment uses S3/MinIO).
 *
 * <p>Active only when {@code lifeadmin.storage.enabled=false} (S3 provider is off). This keeps the
 * app fully runnable locally without object-storage infrastructure while the production path stays
 * S3-based and swappable.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.storage", name = "enabled", havingValue = "false")
public class FilesystemObjectStorageProvider implements ObjectStorageProvider {

    private final Path base;

    public FilesystemObjectStorageProvider() {
        this.base = Paths.get(System.getProperty("java.io.tmpdir"), "lifeadmin-storage");
    }

    @Override
    public String put(final String key, final byte[] content, final String contentType) {
        try {
            final var target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to store object " + key, e);
        }
    }

    @Override
    public byte[] get(final String key) {
        try {
            final var target = resolve(key);
            if (!Files.exists(target)) {
                throw new ResourceNotFoundException("Object", key);
            }
            return Files.readAllBytes(target);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read object " + key, e);
        }
    }

    @Override
    public URI presignedGet(final String key) {
        // Dev-only stand-in for a pre-signed URL. Include a random token so the shape resembles one.
        return resolve(key).toUri();
    }

    @Override
    public void delete(final String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to delete object " + key, e);
        }
    }

    /** Maps a storage key to a safe path under the base dir. */
    private Path resolve(final String key) {
        final var safe = key.replace("..", "_");
        return base.resolve(safe).normalize();
    }
}
