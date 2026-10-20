package org.sba.treeking.rag.dto;

import java.util.List;

/**
 * Response sent back to the client after RAG retrieval + generation.
 *
 * @param answer    The AI-generated answer
 * @param sources   Titles / excerpts of documents used as context (for transparency)
 * @param sessionId Echoed back for client-side tracking
 */
public record ChatResponse(
        String answer,
        List<DocumentSource> sources,
        String sessionId
) {
    /**
     * Represents a single retrieved document used to build the context.
     *
     * @param title   Document/tour title (from metadata)
     * @param excerpt Short excerpt of the matched chunk
     * @param score   Cosine similarity score (0.0 – 1.0)
     */
    public record DocumentSource(
            String title,
            String excerpt,
            double score
    ) {}
}
