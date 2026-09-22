package com.tenantrag.backend.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 8 (step 1) — TEMPORARY endpoint to test retrieval in isolation, before
 * the LLM answer step exists. Embeds the question and returns the top-K matching
 * chunks (RLS-scoped) with similarity scores.
 *
 * <p>Remove this once {@code POST /api/chat} is in place — it's a debugging aid.
 */
@RestController
@RequestMapping("/api/rag")
public class RetrievalTestController {

    private final RetrievalService retrievalService;

    public RetrievalTestController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    public record SearchRequest(String question) {
    }

    @PostMapping("/search")
    public List<RetrievedChunk> search(
            @RequestBody SearchRequest request,
            @RequestParam(value = "topK", required = false, defaultValue = "5") int topK) {
        return retrievalService.retrieve(request.question(), topK);
    }
}
