package com.trekking.app.features.localai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG infrastructure wiring:
 *  - PgVector store (schema: trekking, table: tour_embeddings)
 *  - ChatClient pre-configured with the RAG system prompt
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.local", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    /**
     * PgVectorStore pointing to the existing trekking schema.
     * The table `tour_embeddings` will be auto-initialised on first run.
     * Dimensions are dynamically loaded from `app.ai.rag.embedding-dimensions` (or AI_EMBEDDING_DIMENSIONS).
     */
    @Bean
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate,
                                     EmbeddingModel embeddingModel,
                                     RagProperties props) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("trekking")
                .vectorTableName("tour_embeddings")
                .dimensions(props.embeddingDimensions())
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    /**
     * Pre-configured ChatClient used by RagService.
     * The system prompt contains the {context} placeholder filled at runtime.
     */
    @Bean
    public ChatClient ragChatClient(ChatModel chatModel,
                                    RagProperties props) {
        return ChatClient.builder(chatModel)
                .defaultSystem(props.systemPrompt())
                .build();
    }
}
