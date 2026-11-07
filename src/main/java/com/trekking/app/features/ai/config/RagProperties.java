package com.trekking.app.features.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed RAG configuration.
 * Bound from `app.ai.rag.*` in application.properties.
 */
@ConfigurationProperties(prefix = "app.ai.rag")
public record RagProperties(
        // Number of similar documents to retrieve
        int topK,
        // Similarity threshold (0.0 – 1.0)
        double similarityThreshold,
        // System prompt template for the RAG chain
        String systemPrompt,
        // Embedding vector dimensions (matches model, e.g., 768 for nomic-embed, 1536 for text-embedding-3-small)
        int embeddingDimensions
) {
    public RagProperties {
        if (topK <= 0) topK = 5;
        if (similarityThreshold < 0 || similarityThreshold > 1) similarityThreshold = 0.3;
        if (embeddingDimensions <= 0) embeddingDimensions = 768;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = """
                    You are a friendly and knowledgeable trekking tour assistant.
                    Use ONLY the following context to answer the user question.
                    If the answer is not contained in the context, say you don't know.

                    Context:
                    {context}
                    """;
        }
    }
}
