package com.tenantrag.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenantrag.backend.chat.dto.ChatResponse;
import com.tenantrag.backend.chat.dto.Citation;
import com.tenantrag.backend.rag.RagServiceClient;
import com.tenantrag.backend.rag.RetrievalService;
import com.tenantrag.backend.rag.RetrievedChunk;
import com.tenantrag.backend.tenant.TenantContext;
import com.tenantrag.backend.tenant.TenantTransactionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Phase 8 (step 2) — the RAG chat orchestrator.
 *
 * <p>Flow: retrieve top-K chunks (RLS-scoped) → build a grounded prompt → ask
 * the LLM (via rag-service) → persist the user + assistant messages (with
 * citations) → return the answer. All DB writes run inside the tenant
 * transaction so RLS tags every row with the caller's tenant.
 */
@Service
public class ChatService {

    private final RetrievalService retrievalService;
    private final RagServiceClient ragServiceClient;
    private final TenantTransactionService tenantTransactionService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatService(
            RetrievalService retrievalService,
            RagServiceClient ragServiceClient,
            TenantTransactionService tenantTransactionService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {

        this.retrievalService = retrievalService;
        this.ragServiceClient = ragServiceClient;
        this.tenantTransactionService = tenantTransactionService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(String question, UUID conversationId, UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();

        // 1) Retrieve relevant context (embeds question + RLS-scoped search).
        List<RetrievedChunk> chunks = retrievalService.retrieve(question);

        // 2) Build the grounded prompt + call the LLM.
        String answer;
        if (chunks.isEmpty()) {
            // Nothing to ground on — don't even call the LLM; answer honestly.
            answer = "I couldn't find anything relevant in your documents to "
                    + "answer that.";
        } else {
            String userPrompt = PromptBuilder.buildUserPrompt(question, chunks);
            answer = ragServiceClient.generate(PromptBuilder.SYSTEM, userPrompt);
        }

        // 3) Build citations from the retrieved chunks.
        List<Citation> citations = chunks.stream()
                .map(c -> new Citation(c.documentId(), c.chunkId(),
                        c.chunkIndex(), c.title(), c.score()))
                .toList();

        // 4) Persist conversation + messages (RLS-scoped), return the response.
        final String finalAnswer = answer;
        return tenantTransactionService.execute(tenantId, () ->
                persist(tenantId, userId, conversationId, question, finalAnswer, citations));
    }

    private ChatResponse persist(UUID tenantId, UUID userId, UUID conversationId,
                                 String question, String answer,
                                 List<Citation> citations) {
        // Reuse the given conversation (must belong to this tenant — RLS enforces
        // it) or create a new one titled from the first question.
        UUID convId = conversationId;
        if (convId == null) {
            convId = UUID.randomUUID();
            String title = question.length() > 60
                    ? question.substring(0, 60) : question;
            jdbcTemplate.update(
                    "INSERT INTO conversation (id, tenant_id, user_id, title) " +
                            "VALUES (?, ?, ?, ?)",
                    convId, tenantId, userId, title);
        }

        // User message.
        jdbcTemplate.update(
                "INSERT INTO chat_message " +
                        "(conversation_id, tenant_id, user_id, role, content) " +
                        "VALUES (?, ?, ?, 'user', ?)",
                convId, tenantId, userId, question);

        // Assistant message with citations (JSONB). We pass the JSON as a String
        // and cast it in SQL with ?::jsonb, so we don't need the postgresql
        // driver on the compile classpath (it's runtime-scoped).
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO chat_message " +
                        "(id, conversation_id, tenant_id, user_id, role, content, citations) " +
                        "VALUES (?, ?, ?, ?, 'assistant', ?, ?::jsonb)",
                messageId, convId, tenantId, userId, answer, toJson(citations));

        // Touch the conversation's updated_at.
        jdbcTemplate.update("UPDATE conversation SET updated_at = now() WHERE id = ?",
                convId);

        return new ChatResponse(convId, messageId, answer, citations);
    }

    /** Serialize citations to a JSON string (bound as ?::jsonb in SQL). */
    private String toJson(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize citations", e);
        }
    }
}
