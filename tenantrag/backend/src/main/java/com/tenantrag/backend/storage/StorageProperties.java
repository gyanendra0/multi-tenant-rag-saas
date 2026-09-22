package com.tenantrag.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.storage.*} settings from {@code application.yml}.
 *
 * <p>Object storage is S3-compatible: MinIO in dev, AWS S3 in prod. Only the
 * endpoint/credentials/path-style flag differ between environments.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccess,
        long maxFileSizeBytes
) {
}
