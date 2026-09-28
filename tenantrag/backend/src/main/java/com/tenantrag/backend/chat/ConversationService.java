package com.tenantrag.backend.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenantrag.backend.chat.dto.Citation;
import com.tenantrag.backend.chat.dto.ConversationSummary;
import com.tenantrag.backend.chat.dto.MessageResponse;
import com.tenantrag.backend.tenant.TenantContext;
import com.tenantrag.backend.tenant.TenantTransactionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 9 (step 3) — read/delete chat history.
 *
 * <p>Two layers of scoping: <b>RLS</b> restricts to the caller's tenant, and an
 * explicit {@code user_id = ?} restricts to the caller's own conversations
 * (chat history is private per user, even inside a tenant).
 */
@Service
public class ConversationService {

    private static final TypeReference<List<Citation>> CITATIONS = new TypeReference<>() {};

    private final TenantTransactionService tx;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConversationService(TenantTransactionService tx, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.tx = tx;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<ConversationSummary> list(UUID userId, int limit, int offset) {
        UUID tenantId = TenantContext.requireTenantId();
        return tx.execute(tenantId, () -> jdbc.query(
                "SELECT id, title, created_at, updated_at FROM conversation " +
                        "WHERE user_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new ConversationSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)),
                userId, limit, offset));
    }

    public List<MessageResponse> messages(UUID conversationId, UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        return tx.execute(tenantId, () -> {
            requireOwned(conversationId, userId);
            return jdbc.query(
                    "SELECT id, role, content, citations::text AS citations, created_at " +
                            "FROM chat_message WHERE conversation_id = ? ORDER BY created_at, role DESC",
                    (rs, i) -> new MessageResponse(
                            rs.getObject("id", UUID.class),
                            rs.getString("role"),
                            rs.getString("content"),
                            parseCitations(rs.getString("citations")),
                            rs.getObject("created_at", OffsetDateTime.class)),
                    conversationId);
        });
    }

    public void delete(UUID conversationId, UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        tx.execute(tenantId, () -> {
            requireOwned(conversationId, userId);
            // chat_message rows go via ON DELETE CASCADE.
            jdbc.update("DELETE FROM conversation WHERE id = ?", conversationId);
            return null;
        });
    }

    /**
     * Must be called inside a tenant transaction. Throws 404 if the conversation
     * isn't visible (other tenant via RLS) or belongs to another user.
     */
    void requireOwned(UUID conversationId, UUID userId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM conversation WHERE id = ? AND user_id = ?",
                Integer.class, conversationId, userId);
        if (n == null || n == 0) {
            throw new ConversationNotFoundException(conversationId);
        }
    }

    private List<Citation> parseCitations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, CITATIONS);
        } catch (Exception e) {
            return List.of(); // never fail history rendering over a bad JSONB row
        }
    }
}
