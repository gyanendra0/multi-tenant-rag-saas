package com.tenantrag.backend.chat;

import com.tenantrag.backend.rag.RetrievedChunk;

import java.util.List;

/**
 * Builds the grounded RAG prompt from retrieved chunks.
 *
 * <p>Grounding matters: we instruct the model to answer ONLY from the provided
 * context and to say it doesn't know otherwise, which curbs hallucination. Each
 * context block is numbered so the model (and our citations) can reference them.
 */
final class PromptBuilder {

    private PromptBuilder() {
    }

    static final String SYSTEM = """
            You are a helpful assistant for a document question-answering system.
            Answer the user's question using ONLY the information in the provided
            context. If the answer is not contained in the context, say you don't
            know based on the available documents — do not make things up. Be
            concise and, where useful, cite sources as [1], [2] matching the
            numbered context blocks.
            """;

    /** Build the user prompt: numbered context blocks followed by the question. */
    static String buildUserPrompt(String question, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context:\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append('[').append(i + 1).append("] ")
              .append("(source: ").append(c.title()).append(")\n")
              .append(c.content().strip())
              .append("\n\n");
        }
        sb.append("Question: ").append(question).append('\n');
        sb.append("Answer:");
        return sb.toString();
    }
}
