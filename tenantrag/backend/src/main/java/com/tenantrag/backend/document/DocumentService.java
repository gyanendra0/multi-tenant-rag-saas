package com.tenantrag.backend.document;

import com.tenantrag.backend.ingest.IngestMessage;
import com.tenantrag.backend.ingest.IngestPublisher;
import com.tenantrag.backend.storage.StorageService;
import com.tenantrag.backend.tenant.TenantContext;
import com.tenantrag.backend.tenant.TenantTransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final TenantTransactionService tenantTransactionService;
    private final StorageService storageService;
    private final IngestPublisher ingestPublisher;

    public DocumentService(
            DocumentRepository documentRepository,
            TenantTransactionService tenantTransactionService,
            StorageService storageService,
            IngestPublisher ingestPublisher) {

        this.documentRepository = documentRepository;
        this.tenantTransactionService = tenantTransactionService;
        this.storageService = storageService;
        this.ingestPublisher = ingestPublisher;
    }

    /**
     * Phase 6 — upload a file: store the bytes in object storage, then record a
     * {@code document} row (RLS-scoped) with status {@code PENDING} so the
     * future ingestion worker can pick it up.
     *
     * <p>Ordering matters: we upload to storage <em>first</em>, then insert the
     * DB row. If the DB insert fails, we delete the just-uploaded object so we
     * never leave the object store and DB out of sync (a "compensating action").
     *
     * @param file       the multipart file from the request
     * @param title      optional human title; falls back to the filename
     * @param uploadedBy the authenticated user's id (from the JWT)
     */
    public Document upload(MultipartFile file, String title, UUID uploadedBy) {

        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("No file was provided.");
        }

        UUID tenantId = TenantContext.requireTenantId();

        String originalName = file.getOriginalFilename();
        String filename = (originalName == null || originalName.isBlank())
                ? "upload" : sanitize(originalName);
        String effectiveTitle = (title == null || title.isBlank())
                ? filename : title.trim();
        String mimeType = (file.getContentType() == null || file.getContentType().isBlank())
                ? "application/octet-stream" : file.getContentType();
        long sizeBytes = file.getSize();

        // Tenant-prefixed, unguessable key: <tenantId>/<uuid>/<filename>
        UUID documentId = UUID.randomUUID();
        String storageKey = tenantId + "/" + documentId + "/" + filename;

        // 1) Upload bytes to object storage.
        try (var in = file.getInputStream()) {
            storageService.put(storageKey, mimeType, sizeBytes, in);
        } catch (IOException e) {
            throw new StorageReadException("Could not read uploaded file.", e);
        }

        // 2) Insert the DB row (RLS-scoped). Compensate by deleting the object
        //    if the insert fails, so storage and DB never drift apart.
        Document saved;
        try {
            saved = tenantTransactionService.execute(tenantId, () -> {
                Document doc = new Document();
                doc.setId(documentId);
                doc.setTenantId(tenantId);
                doc.setUploadedBy(uploadedBy);
                doc.setTitle(effectiveTitle);
                doc.setFilename(filename);
                doc.setMimeType(mimeType);
                doc.setSizeBytes(sizeBytes);
                doc.setStorageKey(storageKey);
                doc.setStatus("PENDING");
                doc.setChunkCount(0);
                OffsetDateTime now = OffsetDateTime.now();
                doc.setCreatedAt(now);
                doc.setUpdatedAt(now);
                return documentRepository.save(doc);
            });
        } catch (RuntimeException dbError) {
            storageService.deleteQuietly(storageKey);
            throw dbError;
        }

        // 3) Publish the ingestion job AFTER the DB commit, so we never queue a
        //    job for a row that rolled back. The worker will move the document
        //    PENDING → PROCESSING → READY (or FAILED). A publish failure must not
        //    fail the upload — the row exists and can be re-queued — so we log
        //    and swallow it here.
        try {
            ingestPublisher.publish(new IngestMessage(
                    documentId.toString(),
                    tenantId.toString(),
                    storageKey,
                    mimeType,
                    filename));
        } catch (RuntimeException publishError) {
            // Intentionally non-fatal: the document is safely stored + recorded.
            org.slf4j.LoggerFactory.getLogger(DocumentService.class)
                    .error("Failed to publish ingest job for document {} (it can be re-queued)",
                            documentId, publishError);
        }

        return saved;
    }

    /** Strip any path components a client might sneak into the filename. */
    private static String sanitize(String name) {
        String base = name.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        return (slash >= 0) ? base.substring(slash + 1) : base;
    }

    public List<Document> findAll() {

        UUID tenantId = TenantContext.requireTenantId();

        return tenantTransactionService.execute(
                tenantId,
                documentRepository::findAll
        );
    }

    /**
     * Phase 5 — paginated list for the current tenant, newest first.
     */
    public Page<Document> list(Pageable pageable) {

        UUID tenantId = TenantContext.requireTenantId();

        return tenantTransactionService.execute(
                tenantId,
                () -> documentRepository.findAllByOrderByCreatedAtDesc(pageable)
        );
    }

    /**
     * Phase 5 — fetch a single document by id, scoped to the current tenant.
     * RLS makes a cross-tenant id indistinguishable from a missing one.
     */
    public Document get(UUID id) {

        UUID tenantId = TenantContext.requireTenantId();

        return tenantTransactionService.execute(
                tenantId,
                () -> documentRepository.findById(id)
                        .orElseThrow(() -> new DocumentNotFoundException(id))
        );
    }

    /**
     * Phase 5 + 6 — delete a document (and, via ON DELETE CASCADE, its chunks)
     * for the current tenant, then remove its object from storage. Throws if it
     * does not exist for this tenant.
     *
     * <p>We delete the DB row first (inside the tenant transaction) and only
     * remove the object after that commits, so a storage failure cannot leave a
     * DB row pointing at a missing object. A leftover object (if storage delete
     * fails) is harmless and logged.
     */
    public void delete(UUID id) {

        UUID tenantId = TenantContext.requireTenantId();

        String storageKey = tenantTransactionService.execute(tenantId, () -> {
            Document doc = documentRepository.findById(id)
                    .orElseThrow(() -> new DocumentNotFoundException(id));
            String key = doc.getStorageKey();
            documentRepository.delete(doc);
            return key;
        });

        storageService.deleteQuietly(storageKey);
    }
}