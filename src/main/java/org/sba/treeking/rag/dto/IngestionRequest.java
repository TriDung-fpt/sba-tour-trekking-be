package org.sba.treeking.rag.dto;

/**
 * Request body for the document ingestion endpoint.
 *
 * @param tourId Database ID of the tour to ingest (optional – ingest all if absent)
 * @param type   Target type to ingest: "all", "tours", "documents" (optional)
 */
public record IngestionRequest(Long tourId, String type) {
    public IngestionRequest(Long tourId) {
        this(tourId, null);
    }
}
