package com.lifeadmin.support;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.lifeadmin.provider.storage.ObjectStorageProvider;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * In-memory {@link ObjectStorageProvider} for integration tests, so they don't need a MinIO
 * container. Registered as {@code @Primary} to override the S3 provider within test contexts.
 */
@TestConfiguration
public class InMemoryObjectStorageProvider {

    @Bean
    @Primary
    public ObjectStorageProvider inMemoryStorage() {
        return new ObjectStorageProvider() {
            private final Map<String, byte[]> store = new ConcurrentHashMap<>();

            @Override
            public String put(final String key, final byte[] content, final String contentType) {
                store.put(key, content);
                return key;
            }

            @Override
            public byte[] get(final String key) {
                return store.get(key);
            }

            @Override
            public URI presignedGet(final String key) {
                return URI.create("https://test-storage.local/" + key + "?signed=true");
            }

            @Override
            public void delete(final String key) {
                store.remove(key);
            }
        };
    }
}
