package com.tenantrag.backend.rag;

import com.tenantrag.backend.tenant.TenantContext;
import com.tenantrag.backend.tenant.TenantTransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Phase 8 — retrieval: embed the question, then find the most similar chunks
 * <em>for the current tenant only</em>.
 *
 * <p>The vector search runs through {@link TenantTransactionService}, so
 * {@code app.current_tenant} is set and PostgreSQL RLS guarantees we can never
 * retrieve another tenant's chunks — even though this is raw SQL with no
 * explicit {@code WHERE tenant_id = ?}.
 */
@Service
public class RetrievalService {

    private final RagServiceClient ragServiceClient;
    private final TenantTransactionService tenantTransactionService;
    private final JdbcTemplate jdbcTemplate;
    private final int defaultTopK;

    public RetrievalService(
            RagServiceClient ragServiceClient,
            TenantTransactionService tenantTransactionService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.rag.top-k}") int defaultTopK) {

        this.ragServiceClient = ragServiceClient;
        this.tenantTransactionService = tenantTransactionService;
        this.jdbcTemplate = jdbcTemplate;
        this.defaultTopK = defaultTopK;
    }

    /** Retrieve the top-K most similar chunks to {@code question} for this tenant. */
    public List<RetrievedChunk> retrieve(String question) {
        return retrieve(question, defaultTopK);
    }

    public List<RetrievedChunk> retrieve(String question, int topK) {
        UUID tenantId = TenantContext.requireTenantId();

        // 1) Embed the question via the rag-service (bge query prefix applied there).
        float[] queryVector = ragServiceClient.embedQuery(question);
        String vectorLiteral = toVectorLiteral(queryVector);

        int limit = Math.min(Math.max(topK, 1), 20);

        // 2) Cosine search, RLS-scoped. `<=>` is cosine distance (0 = identical),
        //    so similarity = 1 - distance. The halfvec_cosine_ops HNSW index
        //    (V4) makes the ORDER BY ... LIMIT fast.
        return tenantTransactionService.execute(tenantId, () ->
                jdbcTemplate.query(
                        "SELECT c.document_id, c.id AS chunk_id, c.chunk_index, " +
                                "       d.title, c.content, " +
                                "       1 - (c.embedding <=> ?::halfvec) AS score " +
                                "FROM document_chunk c " +
                                "JOIN document d ON d.id = c.document_id " +
                                "ORDER BY c.embedding <=> ?::halfvec " +
                                "LIMIT ?",
                        (rs, rowNum) -> new RetrievedChunk(
                                rs.getObject("document_id", UUID.class),
                                rs.getObject("chunk_id", UUID.class),
                                rs.getInt("chunk_index"),
                                rs.getString("title"),
                                rs.getString("content"),
                                rs.getDouble("score")
                        ),
                        vectorLiteral, vectorLiteral, limit
                )
        );
    }

    /** Format a float[] as a pgvector text literal: {@code [0.1,0.2,0.3]}. */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
