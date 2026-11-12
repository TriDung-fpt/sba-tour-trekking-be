
package com.trekking.app.features.localai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Locale;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai.local", name = "enabled", havingValue = "true")
public class QuestionScopeValidator {

    private final ChatClient classifierClient;

    public QuestionScopeValidator(ChatModel chatModel) {
        this.classifierClient = ChatClient.builder(chatModel).build();
    }

    private static final String CLASSIFICATION_PROMPT = """
            Bạn là bộ lọc phạm vi câu hỏi cho hệ thống tư vấn tour trekking du lịch.
            Nhiệm vụ: Xác định câu hỏi của người dùng có thuộc phạm vi liên quan đến:
            - Tour trekking, leo núi, hiking, du lịch khám phá, địa điểm/cung đường
            - Giá tour, lịch trình, ngày đi, độ khó, thời gian
            - Tư vấn lựa chọn tour, gợi ý tour phù hợp
            - Chuẩn bị đồ đạc, trang thiết bị, thể lực, kinh nghiệm leo núi
            - Chính sách đặt tour, hoàn hủy, an toàn, hoặc lời chào hỏi giao tiếp cơ bản

            Quy tắc trả về:
            - Nếu câu hỏi LIÊN QUAN đến các chủ đề trên (hoặc là lời chào, hỏi thăm): Trả về chính xác chữ IN_SCOPE.
            - Nếu câu hỏi HOÀN TOÀN KHÔNG LIÊN QUAN (lập trình, giải toán, lịch sử, văn học, chính trị, viết văn/thơ không liên quan, kiến thức phổ thông): Trả về chính xác chữ OUT_OF_SCOPE.

            CHỈ trả về đúng 1 từ duy nhất: IN_SCOPE hoặc OUT_OF_SCOPE. Không giải thích, không viết thêm bất kỳ từ nào khác.
            """;

    // Fast-path keywords for obvious trekking/travel queries
    private static final java.util.List<String> IN_SCOPE_KEYWORDS = java.util.List.of(
            "tour", "trekking", "trek", "leo núi", "hiking", "hike", "cung đường", "địa điểm",
            "giá", "bao nhiêu tiền", "chi phí", "lịch trình", "ngày đi", "thời gian", "độ khó",
            "thể lực", "chuẩn bị", "đồ dùng", "trang thiết bị", "pha luông", "tà năng", "phan dũng",
            "bidoup", "núi bà", "tà chì nhù", "hoàng su phì", "hà giang", "fansipan", "tà xùa",
            "chính sách", "hủy tour", "hoàn tiền", "đặt tour", "gợi ý", "tư vấn", "xin chào", "hello", "hi"
    );

    // Obvious out-of-scope triggers
    private static final java.util.List<String> OUT_OF_SCOPE_KEYWORDS = java.util.List.of(
            "lập trình", "viết code", "java", "spring boot", "python", "javascript", "react",
            "chí phèo", "nam cao", "thủ đô của", "tổng thống", "toán học", "giải phương trình",
            "viết thơ", "viết bài văn", "ai là tác giả"
    );

    public boolean isInScope(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }

        String normalized = question.toLowerCase(Locale.ROOT).trim();

        // 1. Fast check for obvious out-of-scope
        for (String bad : OUT_OF_SCOPE_KEYWORDS) {
            if (normalized.contains(bad)) {
                log.info("Fast-path rejected out-of-scope question by keyword '{}': '{}'", bad, question);
                return false;
            }
        }

        // 2. Fast check for obvious in-scope trekking keywords
        for (String good : IN_SCOPE_KEYWORDS) {
            if (normalized.contains(good)) {
                log.debug("Fast-path accepted in-scope question by keyword '{}': '{}'", good, question);
                return true;
            }
        }

        // 3. Fallback to LLM classification for ambiguous questions
        try {
            String result = classifierClient.prompt()
                    .system(CLASSIFICATION_PROMPT)
                    .user(question)
                    .call()
                    .content();

            if (result == null) {
                log.warn("Question classifier returned null");
                return true; // Default to allow so user gets an answer via RAG context
            }

            String classification = result.trim().toUpperCase(Locale.ROOT);
            log.debug("Question classification: {}", classification);

            if (classification.contains("IN_SCOPE")) {
                return true;
            }

            if (classification.contains("OUT_OF_SCOPE")) {
                return false;
            }

            log.warn("Unexpected classifier response '{}', defaulting to IN_SCOPE", result);
            return true;

        } catch (Exception e) {
            log.error("Question classification failed, defaulting to IN_SCOPE", e);
            return true;
        }
    }
}
