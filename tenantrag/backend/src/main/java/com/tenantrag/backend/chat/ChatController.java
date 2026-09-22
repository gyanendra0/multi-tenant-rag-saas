package com.tenantrag.backend.chat;

import com.tenantrag.backend.auth.CurrentUser;
import com.tenantrag.backend.chat.dto.ChatRequest;
import com.tenantrag.backend.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 8 (step 2) — RAG chat endpoint.
 *
 * <p>{@code POST /api/chat} with {@code {"question": "...", "conversationId": ?}}.
 * Tenant comes from the JWT; the answer is grounded in the tenant's own
 * documents (RLS-scoped retrieval) and persisted with citations.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        UUID userId = CurrentUser.requireId();
        return chatService.chat(request.question(), request.conversationId(), userId);
    }
}
