package org.sba.treeking.rag.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sba.treeking.rag.dto.ChatRequest;
import org.sba.treeking.rag.dto.ChatResponse;
import org.sba.treeking.rag.dto.IngestionRequest;
import org.sba.treeking.rag.service.IngestionService;
import org.sba.treeking.rag.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public-facing RAG endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/v1/ai/chat}    – ask a question about tours</li>
 *   <li>{@code POST /api/v1/ai/ingest}  – (admin) re-embed tours into pgvector</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI / RAG", description = "AI-powered tour assistant using Retrieval-Augmented Generation")
public class RagController {

    private final RagService      ragService;
    private final IngestionService ingestionService;

    // ── Chat ─────────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Ask the tour assistant",
            description = "Embeds the question, retrieves relevant tours from pgvector, and generates a grounded answer."
    )
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragService.ask(request.question(), request.sessionId());
        return ResponseEntity.ok(response);
    }

    // ── Ingestion (Admin-only) ────────────────────────────────────────────────

    @Operation(
            summary     = "Ingest data into the vector store",
            description = "Loads data, chunks and embeds it, then upserts into pgvector. " +
                          "Pass tourId to re-embed a single tour, type='documents' for RAG documents, or type='all' for everything."
    )
    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody(required = false) IngestionRequest request) {
        int chunks;
        if (request != null && request.tourId() != null) {
            chunks = ingestionService.ingestTour(request.tourId());
        } else if (request != null && "documents".equalsIgnoreCase(request.type())) {
            chunks = ingestionService.ingestRagDocuments();
        } else if (request != null && "tours".equalsIgnoreCase(request.type())) {
            chunks = ingestionService.ingestAllTours();
        } else {
            chunks = ingestionService.ingestAll();
        }
        return ResponseEntity.ok(Map.of(
                "status",        "completed",
                "chunksIndexed", chunks
        ));
    }

    @Operation(
            summary     = "Ingest resource documents into the vector store",
            description = "Loads policy/reference documents from resources/RAGdocument (e.g. PDF policies), chunks and embeds them, then upserts into pgvector."
    )
    @PostMapping("/ingest/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> ingestDocuments() {
        int chunks = ingestionService.ingestRagDocuments();
        return ResponseEntity.ok(Map.of(
                "status",        "completed",
                "chunksIndexed", chunks
        ));
    }
}
