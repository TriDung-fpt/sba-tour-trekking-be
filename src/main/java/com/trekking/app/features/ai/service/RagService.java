package com.trekking.app.features.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.trekking.app.features.ai.config.RagProperties;
import com.trekking.app.features.ai.dto.ChatResponse;
import com.trekking.app.features.ai.dto.ChatResponse.DocumentSource;
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
 * <li><b>Retrieve</b> – embed the user question, similarity-search
 * pgvector</li>
 * <li><b>Augment</b> – inject retrieved chunks into the system prompt as
 * {context}</li>
 * <li><b>Generate</b> – send the augmented prompt to the LLM (LM Studio)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {
    private final QuestionScopeValidator scopeValidator;
    private final VectorStore vectorStore;
    private final ChatClient ragChatClient;
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
        if (!scopeValidator.isInScope(question)) {

            log.info("Rejected out-of-scope question: '{}'", question);

            return new ChatResponse(
                    "Xin lỗi, tôi là trợ lý tư vấn tour trekking của hệ thống. "
                            + "Tôi chỉ có thể hỗ trợ bạn về các tour trekking, "
                            + "thông tin chuyến đi và kinh nghiệm chuẩn bị cho "
                            + "hoạt động trekking. Bạn có muốn tìm hiểu hoặc "
                            + "lựa chọn một tour trekking không?",
                    List.of(),
                    sessionId);
        }
        // ── 1. Retrieve ────────────────────────────────────────────────────
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(ragProperties.topK())
                        .similarityThreshold(ragProperties.similarityThreshold())
                        .build());

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for query: '{}'", question);
            return new ChatResponse(
                    "Xin lỗi, hiện tại tôi chưa tìm thấy thông tin tour phù hợp với yêu cầu của bạn. Bạn có thể cho tôi biết thêm về sở thích (độ khó, địa điểm mong muốn, hoặc thời gian) để tôi hỗ trợ tốt hơn nhé!",
                    List.of(),
                    sessionId);
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

    /**
     * Ask a question and stream tokens back in real-time via Server-Sent Events
     * (SSE).
     *
     * @param question user's natural-language question
     * @return reactive Flux of string tokens
     */
    public reactor.core.publisher.Flux<String> askStream(String question) {
        log.debug("RAG streaming query: '{}'", question);
        if (!scopeValidator.isInScope(question)) {

            log.info("Rejected out-of-scope streaming question: '{}'", question);

            return reactor.core.publisher.Flux.just(
                    "Xin lỗi, tôi là trợ lý tư vấn tour trekking của hệ thống. "
                            + "Tôi chỉ có thể hỗ trợ bạn về các tour trekking, "
                            + "thông tin chuyến đi và kinh nghiệm chuẩn bị cho "
                            + "hoạt động trekking. Bạn có muốn tìm hiểu hoặc "
                            + "lựa chọn một tour trekking không?");
        }

        // ── 1. Retrieve ────────────────────────────────────────────────────
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(ragProperties.topK())
                        .similarityThreshold(ragProperties.similarityThreshold())
                        .build());

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for streaming query: '{}'", question);
            return reactor.core.publisher.Flux.just(
                    "Xin lỗi, hiện tại tôi chưa tìm thấy thông tin tour phù hợp với yêu cầu của bạn. Bạn có thể cho tôi biết thêm về sở thích (độ khó, địa điểm mong muốn, hoặc thời gian) để tôi hỗ trợ tốt hơn nhé!");
        }

        // ── 2. Augment ─────────────────────────────────────────────────────
        String context = buildContext(relevantDocs);

        // ── 3. Stream generated tokens ─────────────────────────────────────
        return ragChatClient.prompt()
                .system(sp -> sp.param("context", context))
                .user(question)
                .stream()
                .content();
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
        String title = String.valueOf(doc.getMetadata().getOrDefault("title", "Unknown Tour"));
        String excerpt = truncate(doc.getText(), 200);
        double score = doc.getScore() != null ? doc.getScore() : 0.0;
        return new DocumentSource(title, excerpt, score);
    }

    private String truncate(String text, int maxChars) {
        if (text == null)
            return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}
