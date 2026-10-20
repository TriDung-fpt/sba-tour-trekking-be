package org.sba.treeking.rag.dto;

/**
 * Request body for the document ingestion endpoint.
 *
 * @param tourId   Database ID of the tour to ingest (optional – ingest all if absent)
 */
public record IngestionRequest(Long tourId) {}
