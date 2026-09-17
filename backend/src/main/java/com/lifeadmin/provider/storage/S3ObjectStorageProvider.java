package com.lifeadmin.provider.storage;

import java.net.URI;
import java.time.Duration;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-compatible {@link ObjectStorageProvider} (MinIO locally). Uses path-style access for MinIO and
 * issues short-lived pre-signed GET URLs. Active only when {@code lifeadmin.storage.endpoint} is
 * set, so tests can supply an in-memory stub instead.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class S3ObjectStorageProvider implements ObjectStorageProvider {

    private final StorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ObjectStorageProvider(final StorageProperties properties) {
        this.properties = properties;
        final var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        final var endpoint = URI.create(properties.getEndpoint());
        final var region = Region.of(properties.getRegion());
        final var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccess())
                .build();
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
    }

    /** Ensure the bucket exists on startup (idempotent). */
    @PostConstruct
    void ensureBucket() {
        final var bucket = properties.getBucket();
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (final NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (final Exception e) {
            // Bucket may already exist / be created out-of-band (compose minio-init). Ignore.
        }
    }

    @Override
    public String put(final String key, final byte[] content, final String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        return key;
    }

    @Override
    public byte[] get(final String key) {
        return client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build())
                .asByteArray();
    }

    @Override
    public URI presignedGet(final String key) {
        final var getRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();
        final var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.getPresignTtlSeconds()))
                .getObjectRequest(getRequest)
                .build();
        return URI.create(presigner.presignGetObject(presignRequest).url().toString());
    }

    @Override
    public void delete(final String key) {
        client.deleteObject(
                DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
    }
}
