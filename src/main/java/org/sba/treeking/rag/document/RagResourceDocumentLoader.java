package org.sba.treeking.rag.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads documents from the classpath resource directory (e.g. classpath:RAGdocument/*).
 * Supports PDF documents as well as text / markdown files.
 */
@Slf4j
@Component
public class RagResourceDocumentLoader {

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    private static final String RESOURCE_LOCATION_PATTERN = "classpath*:RAGdocument/*";

    /**
     * Loads all documents found in the resources/RAGdocument folder.
     */
    public List<Document> loadAll() {
        List<Document> allDocs = new ArrayList<>();
        try {
            Resource[] resources = resourceResolver.getResources(RESOURCE_LOCATION_PATTERN);
            log.info("Found {} resource(s) in {}", resources.length, RESOURCE_LOCATION_PATTERN);

            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String filename = resource.getFilename();
                if (filename == null || filename.isBlank()) {
                    continue;
                }

                List<Document> docs = loadResource(resource);
                allDocs.addAll(docs);
            }
        } catch (IOException e) {
            log.error("Failed to load resources from {}", RESOURCE_LOCATION_PATTERN, e);
        }

        log.info("Loaded total {} document page(s)/section(s) from resources/RAGdocument", allDocs.size());
        return allDocs;
    }

    /**
     * Load a specific resource as Documents.
     */
    public List<Document> loadResource(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "document";
        }

        String lower = filename.toLowerCase();
        List<Document> rawDocs = new ArrayList<>();

        try {
            if (lower.endsWith(".pdf")) {
                PagePdfDocumentReader reader = new PagePdfDocumentReader(resource,
                        PdfDocumentReaderConfig.builder().build());
                rawDocs = reader.get();
            } else if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                rawDocs = List.of(new Document(content));
            } else {
                log.warn("Unsupported document format for resource: {}", filename);
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error reading document {}: {}", filename, e.getMessage(), e);
            return List.of();
        }

        String cleanTitle = deriveTitle(filename);

        List<Document> enrichedDocs = new ArrayList<>();
        for (int i = 0; i < rawDocs.size(); i++) {
            Document doc = rawDocs.get(i);
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("source", filename);
            metadata.put("file_name", filename);
            metadata.put("title", cleanTitle);
            metadata.put("doc_type", "policy_document");
            metadata.put("page_number", metadata.getOrDefault("page_number", i + 1));

            enrichedDocs.add(new Document(doc.getText(), metadata));
        }

        log.info("Extracted {} page/section document(s) from {}", enrichedDocs.size(), filename);
        return enrichedDocs;
    }

    private String deriveTitle(String filename) {
        String nameWithoutExt = filename.replaceFirst("\\.[^.]+$", "");
        if (nameWithoutExt.equalsIgnoreCase("chinh_sach_trekking_da_phe_duyet-1") ||
            nameWithoutExt.equalsIgnoreCase("chinh_sach_trekking_da_phe_duyet")) {
            return "Chính sách trekking đã phê duyệt";
        }
        return nameWithoutExt.replace('_', ' ').replace('-', ' ');
    }
}
