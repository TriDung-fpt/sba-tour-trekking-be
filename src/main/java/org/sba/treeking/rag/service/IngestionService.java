package org.sba.treeking.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sba.treeking.rag.document.RagResourceDocumentLoader;
import org.sba.treeking.rag.document.TourDocumentLoader;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles the ETL pipeline:
 * <ol>
 *   <li><b>Extract</b> – load tour rows from the database or documents from resources/RAGdocument</li>
 *   <li><b>Transform</b> – split long descriptions/documents into overlapping token chunks</li>
 *   <li><b>Load</b> – embed and upsert chunks into pgvector</li>
 * </ol>
 *
 * <p>Call this manually via the admin API, or schedule it with a cron job
 * whenever tour data or policy documents are updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final TourDocumentLoader tourDocumentLoader;
    private final RagResourceDocumentLoader ragResourceDocumentLoader;
    private final VectorStore vectorStore;

    /**
     * Chunk size in tokens. 512 tokens ≈ ~380 words – a good balance between
     * context richness and retrieval precision for tour descriptions and policies.
     */
    private static final int CHUNK_SIZE_TOKENS    = 512;
    private static final int MIN_CHUNK_SIZE_CHARS  = 100;

    /**
     * Ingest ALL data (both active tours and resources/RAGdocument files) into the vector store.
     *
     * @return total number of chunks written
     */
    public int ingestAll() {
        log.info("Starting full ingestion (tours + RAG documents) into pgvector");
        int tourChunks = ingestAllTours();
        int docChunks = ingestRagDocuments();
        return tourChunks + docChunks;
    }

    /**
     * Ingest all active tours from the database into the vector store.
     *
     * @return number of tour chunks written
     */
    public int ingestAllTours() {
        log.info("Starting full tour ingestion into pgvector");
        List<Document> raw = tourDocumentLoader.loadAll();
        return embed(raw);
    }

    /**
     * Ingest all document files in resources/RAGdocument (e.g. PDF policies) into the vector store.
     *
     * @return number of document chunks written
     */
    public int ingestRagDocuments() {
        log.info("Starting ingestion of documents in resources/RAGdocument into pgvector");
        List<Document> raw = ragResourceDocumentLoader.loadAll();
        return embed(raw);
    }

    /**
     * Ingest a single tour (useful for incremental updates).
     *
     * @param tourId the tour_packages.id to ingest
     * @return number of chunks written
     */
    public int ingestTour(Long tourId) {
        log.info("Starting ingestion for tour id={}", tourId);
        List<Document> raw = tourDocumentLoader.loadById(tourId);
        if (raw.isEmpty()) {
            log.warn("No active tour found with id={}", tourId);
            return 0;
        }
        return embed(raw);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private int embed(List<Document> rawDocs) {
        if (rawDocs.isEmpty()) {
            log.info("No documents to ingest");
            return 0;
        }

        // Split into chunks so large descriptions don't exceed embedding token limits
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE_TOKENS)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = splitter.apply(rawDocs);
        log.info("Ingesting {} chunks from {} tours", chunks.size(), rawDocs.size());

        // VectorStore.add() embeds and upserts in one step
        vectorStore.add(chunks);
        log.info("Ingestion complete – {} chunks stored in pgvector", chunks.size());
        return chunks.size();
    }
}
