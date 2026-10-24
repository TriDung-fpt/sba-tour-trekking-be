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
    void testIngestAndSearchRagDocuments() {
        System.out.println("Starting ingestion of RAG documents...");
        int chunksIndexed = ingestionService.ingestRagDocuments();
        System.out.println("Successfully indexed chunks: " + chunksIndexed);
        assertTrue(chunksIndexed > 0, "At least one chunk should be indexed from RAGdocument");

        // Verify similarity search retrieval on policy
        List<Document> searchResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Quy định thuê thiết bị trekking và đặt cọc")
                        .topK(3)
                        .similarityThreshold(0.2)
                        .build()
        );

        System.out.println("Similarity search returned " + searchResults.size() + " chunk(s):");
        for (Document doc : searchResults) {
            System.out.println("Title: " + doc.getMetadata().get("title"));
            System.out.println("Source: " + doc.getMetadata().get("source"));
            System.out.println("Excerpt: " + doc.getText().substring(0, Math.min(150, doc.getText().length())) + "...");
        }
        assertFalse(searchResults.isEmpty(), "Should retrieve chunks from the embedded policy document");

        // Verify RAG chat query
        ChatResponse chatResponse = ragService.ask("Chính sách thuê thiết bị trekking quy định như thế nào?", "test-session");
        System.out.println("AI Answer: " + chatResponse.answer());
        System.out.println("Sources count: " + chatResponse.sources().size());
        for (var source : chatResponse.sources()) {
            System.out.println("Source: " + source.title() + " (score=" + source.score() + ")");
        }
    }
}
