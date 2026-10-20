package org.sba.treeking.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed RAG configuration.
 * Bound from `app.rag.*` in application.properties.
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        // Number of similar documents to retrieve
        int topK,
        // Similarity threshold (0.0 – 1.0)
        double similarityThreshold,
        // System prompt template for the RAG chain
        String systemPrompt
) {
    public RagProperties {
        if (topK <= 0) topK = 5;
        if (similarityThreshold < 0 || similarityThreshold > 1) similarityThreshold = 0.7;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = """
                    You are a helpful trekking tour assistant for an online tour marketplace.
                    Use the following retrieved context to answer the user's question accurately and concisely.
                    If the answer is not in the context, say you don't know — do not make things up.
                    
                    Context:
                    {context}
                    """;
        }
    }
}
