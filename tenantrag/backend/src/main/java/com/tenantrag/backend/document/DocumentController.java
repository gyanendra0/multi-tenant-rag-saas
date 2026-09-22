package com.tenantrag.backend.document;

import com.tenantrag.backend.auth.CurrentUser;
import com.tenantrag.backend.document.dto.DocumentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Phase 3.20 + Phase 5 — Authenticated document APIs.
 *
 * <p>No {@code X-Tenant-Id} header: the tenant is resolved from the validated
 * JWT by {@code TenantContextFilter}, and PostgreSQL RLS enforces isolation as a
 * second line of defense.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Phase 6 + 7 — upload a document. Multipart form with a {@code file} part
     * and an optional {@code title} field. Tenant comes from the JWT; the
     * uploader is stamped from the JWT {@code sub}.
     *
     * <p>Returns <b>202 Accepted</b>: the file is stored and recorded
     * ({@code status=PENDING}) and an ingestion job is queued. The document
     * becomes {@code READY} asynchronously once the worker finishes.
     *
     * <p>Example:
     * {@code curl -F file=@a.pdf -F title=Report -H "Authorization: Bearer $T"
     *   http://localhost:8080/api/documents}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {

        UUID uploadedBy = CurrentUser.requireId();
        Document created = documentService.upload(file, title, uploadedBy);
        return DocumentResponse.from(created);
    }

    /**
     * Paginated list of the current tenant's documents, newest first.
     * Example: {@code GET /api/documents?page=0&size=20}
     */
    @GetMapping
    public Page<DocumentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return documentService.list(pageable).map(DocumentResponse::from);
    }

    /** Fetch a single document by id (404 if not in the current tenant). */
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.get(id));
    }

    /** Delete a document (and its chunks via cascade). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
