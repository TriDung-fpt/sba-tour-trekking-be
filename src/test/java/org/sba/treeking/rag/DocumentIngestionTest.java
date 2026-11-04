package org.sba.treeking.rag;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.sba.treeking.rag.dto.ChatResponse;
import org.sba.treeking.rag.service.IngestionService;
import org.sba.treeking.rag.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class DocumentIngestionTest {

    static {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> registry.add(entry.getKey(), entry::getValue));
    }

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RagService ragService;

    @Test
    void testIngestAndSearchAllData() {
        System.out.println("==================================================");
        System.out.println("Starting full ingestion (DB Tours + Policy PDFs)...");
        System.out.println("==================================================");

        int totalChunks = ingestionService.ingestAll();
        System.out.println("Successfully indexed total chunks in pgvector: " + totalChunks);
        assertTrue(totalChunks > 0, "At least one chunk should be indexed");

        // 1. Verify similarity search on Database Tours (e.g., Fansipan or Tà Xùa)
        System.out.println("\n--- Testing similarity search for Tour: 'Tour Fansipan giá bao nhiêu?' ---");
        List<Document> tourResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Tour trekking Fansipan giá bao nhiêu và đi mấy ngày?")
                        .topK(3)
                        .similarityThreshold(0.2)
                        .build()
        );

        System.out.println("Found " + tourResults.size() + " relevant tour chunk(s):");
        for (Document doc : tourResults) {
            System.out.println(" -> Title: " + doc.getMetadata().get("title"));
            System.out.println("    Source: " + doc.getMetadata().get("source"));
            System.out.println("    Locations: " + doc.getMetadata().get("locations"));
            System.out.println("    Excerpt: " + doc.getText().substring(0, Math.min(180, doc.getText().length())) + "...");
        }
        assertFalse(tourResults.isEmpty(), "Should retrieve tour chunks from the database");

        // 2. Verify RAG Question-Answering on Database Tour
        System.out.println("\n--- Testing RAG LLM Generation for Database Tour ---");
        ChatResponse chatResponse = ragService.ask("Bên bạn có tour trekking nào ở Lào Cai hoặc Fansipan không? Giá và lịch trình thế nào?", "test-session-tour");
        System.out.println("\nAI Answer:\n" + chatResponse.answer());
        System.out.println("\nSources cited: " + chatResponse.sources().size());
        for (var source : chatResponse.sources()) {
            System.out.println(" -> " + source.title() + " (relevance score: " + source.score() + ")");
        }

        // 3. Verify RAG Question-Answering on Policy Document
        System.out.println("\n--- Testing RAG LLM Generation for Policy Document ---");
        ChatResponse policyResponse = ragService.ask("Chính sách hủy tour và hoàn tiền như thế nào nếu tôi bận việc đột xuất?", "test-session-policy");
        System.out.println("\nAI Policy Answer:\n" + policyResponse.answer());
        System.out.println("\nSources cited: " + policyResponse.sources().size());
        for (var source : policyResponse.sources()) {
            System.out.println(" -> " + source.title() + " (relevance score: " + source.score() + ")");
        }
    }
}
