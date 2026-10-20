package org.sba.treeking.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sba.treeking.rag.config.RagProperties;
import org.sba.treeking.rag.dto.ChatResponse;
import org.sba.treeking.rag.dto.ChatResponse.DocumentSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core RAG service.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li><b>Retrieve</b> – embed the user question, similarity-search pgvector</li>
 *   <li><b>Augment</b> – inject retrieved chunks into the system prompt as {context}</li>
 *   <li><b>Generate</b> – send the augmented prompt to the LLM (LM Studio)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore   vectorStore;
    private final ChatClient    ragChatClient;
    private final RagProperties ragProperties;

    /**
     * Ask a question and get a grounded answer from the RAG pipeline.
     *
     * @param question  user's natural-language question
     * @param sessionId optional session ID (echoed back)
     * @return answer + source documents used
     */
    public ChatResponse ask(String question, String sessionId) {
        log.debug("RAG query: '{}'", question);

        // ── 1. Retrieve ────────────────────────────────────────────────────
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(ragProperties.topK())
                        .similarityThreshold(ragProperties.similarityThreshold())
                        .build()
        );

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for query: '{}'", question);
            return new ChatResponse(
                    "I'm sorry, I couldn't find any relevant tour information for your question.",
                    List.of(),
                    sessionId
            );
        }

        log.debug("Retrieved {} relevant document chunks", relevantDocs.size());

        // ── 2. Augment ─────────────────────────────────────────────────────
        String context = buildContext(relevantDocs);

        // ── 3. Generate ────────────────────────────────────────────────────
        String answer = ragChatClient.prompt()
                .system(sp -> sp.param("context", context))
                .user(question)
                .call()
                .content();

        // ── 4. Build response with source attribution ──────────────────────
        List<DocumentSource> sources = relevantDocs.stream()
                .map(this::toDocumentSource)
                .toList();

        return new ChatResponse(answer, sources, sessionId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("--- Source ").append(i + 1).append(" ---\n");
            sb.append(docs.get(i).getFormattedContent()).append("\n\n");
        }
        return sb.toString();
    }

    private DocumentSource toDocumentSource(Document doc) {
        String title   = String.valueOf(doc.getMetadata().getOrDefault("title", "Unknown Tour"));
        String excerpt = truncate(doc.getText(), 200);
        double score   = doc.getScore() != null ? doc.getScore() : 0.0;
        return new DocumentSource(title, excerpt, score);
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}
