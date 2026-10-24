package org.sba.treeking.rag.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG infrastructure wiring:
 *  - PgVector store (schema: trekking, table: tour_embeddings)
 *  - ChatClient pre-configured with the RAG system prompt
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    /**
     * PgVectorStore pointing to the existing trekking schema.
     * The table `tour_embeddings` will be auto-initialised on first run.
     *
     * Dimensions must match the embedding model:
     *   text-embedding-3-small → 1536
     */
    @Bean
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate,
                                     EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("trekking")
                .vectorTableName("tour_embeddings")
                .dimensions(768)                          // nomic-embed-text-v1.5 (local LM Studio)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(true)                   // create table + index if absent
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
