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
            summary     = "Ingest tours into the vector store",
            description = "Loads tour data from the database, chunks and embeds it, then upserts into pgvector. " +
                          "Pass a tourId to re-embed a single tour, or omit it to re-embed all active tours."
    )
    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody(required = false) IngestionRequest request) {
        int chunks;
        if (request != null && request.tourId() != null) {
            chunks = ingestionService.ingestTour(request.tourId());
        } else {
            chunks = ingestionService.ingestAll();
        }
        return ResponseEntity.ok(Map.of(
                "status",        "completed",
                "chunksIndexed", chunks
        ));
    }
}
