package com.tenantrag.backend.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Phase 8 — thin HTTP client to the internal rag-service.
 *
 * <p>The rag-service owns the embedding model (and later the LLM). The backend
 * calls it to turn a user's question into a query vector, then does the actual
 * vector search itself so PostgreSQL RLS enforces tenant isolation on retrieval.
 */
@Component
public class RagServiceClient {

    private final RestClient restClient;

    public RagServiceClient(@Value("${app.rag.service-url}") String baseUrl) {
        // Use the plain HttpURLConnection-based factory. The default JDK
        // HttpClient sends an "Expect: 100-continue" header that uvicorn/h11
        // doesn't complete as expected, causing the POST body to be dropped
        // (FastAPI then returns 422 "body field required"). SimpleClientHttp...
        // sends the body directly and avoids that handshake.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    /**
     * Embed a single search query. The rag-service applies the bge query prefix
     * and returns a normalized 384-dim vector.
     */
    @SuppressWarnings("unchecked")
    public float[] embedQuery(String text) {
        Map<String, Object> body = restClient.post()
                .uri("/embed/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .body(Map.class);

        if (body == null || !(body.get("embedding") instanceof List<?> raw)) {
            throw new RagServiceException("rag-service returned no embedding");
        }

        float[] vector = new float[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            vector[i] = ((Number) raw.get(i)).floatValue();
        }
        return vector;
    }

    /**
     * Generate a grounded answer. The backend supplies the system instructions
     * and the user prompt (question + retrieved context); the rag-service calls
     * the configured LLM and returns the answer text.
     */
    @SuppressWarnings("unchecked")
    public String generate(String system, String prompt) {
        Map<String, Object> body = restClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("system", system, "prompt", prompt))
                .retrieve()
                .body(Map.class);

        if (body == null || !(body.get("answer") instanceof String answer)) {
            throw new RagServiceException("rag-service returned no answer");
        }
        return answer;
    }
}
