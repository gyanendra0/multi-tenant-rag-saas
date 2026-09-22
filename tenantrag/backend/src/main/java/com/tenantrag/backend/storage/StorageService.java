package com.tenantrag.backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * Thin wrapper over the S3 API for storing and deleting document bytes.
 *
 * <p>Keys are tenant-prefixed by the caller (e.g. {@code <tenantId>/<uuid>/file})
 * so that objects are grouped per tenant in the bucket. Note that object storage
 * has no RLS; tenant isolation for files relies on the app never generating or
 * accepting a key outside the caller's tenant prefix.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3;
    private final StorageProperties props;

    public StorageService(S3Client s3, StorageProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    /**
     * Streams {@code content} to the bucket under {@code key}.
     *
     * @throws StorageException if the upload fails
     */
    public void put(String key, String contentType, long sizeBytes, InputStream content) {
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength(sizeBytes)
                            .build(),
                    RequestBody.fromInputStream(content, sizeBytes));
        } catch (Exception e) {
            throw new StorageException("Failed to store object: " + key, e);
        }
    }

    /**
     * Best-effort delete. Used both for normal deletes and to compensate when a
     * DB insert fails after the object was already uploaded. Never throws — a
     * failure here is logged, not propagated, so it cannot mask the real error.
     */
    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete object '{}' (leaving orphan): {}",
                    key, e.getMessage());
        }
    }
}
