package com.tenantrag.backend.chat;

import com.tenantrag.backend.auth.CurrentUser;
import com.tenantrag.backend.chat.dto.ConversationSummary;
import com.tenantrag.backend.chat.dto.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Phase 9 (step 3) — the caller's own chat history. */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final int MAX_LIMIT = 100;

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    /** Most recently active first. */
    @GetMapping
    public List<ConversationSummary> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return service.list(CurrentUser.requireId(), safeLimit, Math.max(offset, 0));
    }

    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@PathVariable UUID id) {
        return service.messages(id, CurrentUser.requireId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }
}
