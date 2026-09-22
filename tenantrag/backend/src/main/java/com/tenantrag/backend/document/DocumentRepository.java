package com.tenantrag.backend.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository
        extends JpaRepository<Document, UUID> {

    // Note: RLS scopes every query to the current tenant, so these standard
    // methods are automatically tenant-safe when run inside the tenant
    // transaction. No explicit tenant_id filter is required (defense in depth
    // is provided by PostgreSQL, not the query text).

    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);
}