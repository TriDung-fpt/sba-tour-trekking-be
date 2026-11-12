package com.trekking.app.features.integrateai.service.impl;

import com.trekking.app.features.integrateai.dto.response.IntegrateAiChatResponse;
import com.trekking.app.features.integrateai.config.IntegrateAiProperties;
import com.trekking.app.features.integrateai.service.ConversationStore;
import com.trekking.app.features.integrateai.service.IntegrateAiProvider;
import com.trekking.app.features.integrateai.service.IntegrateAiService;
import com.trekking.app.features.integrateai.service.TourConsultationTools;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntegrateAiServiceImpl implements IntegrateAiService {
    private static final Logger log = LoggerFactory.getLogger(IntegrateAiServiceImpl.class);
    private final IntegrateAiProperties props;
    private final IntegrateAiProvider provider;
    private final TourConsultationTools tools;
    private final ConversationStore conversations;

    public IntegrateAiServiceImpl(IntegrateAiProperties props, IntegrateAiProvider provider,
                                  TourConsultationTools tools, ConversationStore conversations) {
        this.props = props; this.provider = provider; this.tools = tools; this.conversations = conversations;
    }

    @Override
    public IntegrateAiChatResponse chat(String question, String sessionId, String clientKey) {
        if (!props.enabled() || props.gemini().apiKey() == null || props.gemini().apiKey().isBlank()) {
            return new IntegrateAiChatResponse(sessionId, "Trợ lý tư vấn hiện chưa được cấu hình. Vui lòng thử lại sau.", List.of());
        }
        String normalized = question == null ? "" : question.strip();
        if (normalized.isEmpty() || normalized.length() > props.limits().maxMessageChars()) {
            return new IntegrateAiChatResponse(sessionId, "Tin nhắn chưa hợp lệ hoặc dài hơn giới hạn cho phép.", List.of());
        }
        try {
            if (conversations.incrementRate(clientKey) > props.limits().requestsPerWindow()) {
                return new IntegrateAiChatResponse(sessionId, "Bạn gửi yêu cầu hơi nhanh. Vui lòng đợi một chút rồi thử lại.", List.of());
            }
        } catch (RuntimeException ex) {
            return new IntegrateAiChatResponse(sessionId, "Dịch vụ tư vấn đang bận. Vui lòng thử lại sau.", List.of());
        }
        String owner = owner();
        ConversationStore.Conversation conversation;
        try { conversation = conversations.loadOrCreate(sessionId, owner); }
        catch (RuntimeException ex) { return new IntegrateAiChatResponse(null, "Phiên tư vấn không hợp lệ. Hãy bắt đầu cuộc trò chuyện mới.", List.of()); }

        try {
            var catalog = tools.catalog();
            var history = new ArrayList<Map<String, Object>>();
            conversation.turns().forEach(t -> history.add(Map.of("role", t.role(), "content", t.content())));
            if (!conversation.recentTourIds().isEmpty()) {
                history.add(Map.of("role", "assistant", "content", "Mã tour vừa được gợi ý, chỉ dùng để phân giải câu hỏi tiếp nối: "
                        + conversation.recentTourIds()));
            }
            Map<String, Object> selection = provider.select(normalized, history, catalog);
            boolean clarify = Boolean.TRUE.equals(selection.get("needsClarification"));
            String clarification = text(selection.get("clarification"));
            String maxBudget = text(selection.get("maxBudgetVnd"));
            String minBudget = text(selection.get("minBudgetVnd"));
            String difficulty = text(selection.get("difficulty"));
            if ("ANY".equalsIgnoreCase(difficulty)) difficulty = "";
            String departureDate = text(selection.get("departureDate"));
            String explicitDate = explicitDate(normalized);
            if (explicitDate != null) departureDate = explicitDate;
            String location = text(selection.get("locationQuery"));
            int partySize = partySize(selection.get("partySize"), normalized);
            try {
                validateExtractedCriteria(maxBudget, minBudget, departureDate);
            } catch (IllegalArgumentException ex) {
                return new IntegrateAiChatResponse(conversation.id(),
                        "Bạn cho mình xin ngân sách không âm và ngày đi theo định dạng ngày/tháng/năm nhé.", List.of());
            }
            var unusualBudget = isUnusual(maxBudget) || isUnusual(minBudget);
            String lowerQuestion = normalized.toLowerCase(Locale.ROOT);
            boolean budgetUnitStated = List.of("mỗi người", "mỗi khách", "mỗi suất", "tổng đoàn", "cả đoàn", "cho cả nhóm")
                    .stream().anyMatch(lowerQuestion::contains);
            if (unusualBudget && !budgetUnitStated && !clarify) {
                clarify = true;
                clarification = "Mức ngân sách này khá lớn. Bạn muốn tính ngân sách cho mỗi người hay cho cả đoàn?";
            }
            if (!clarify && lowerQuestion.matches(".*(vô hạn|vô cùng).*")) {
                clarify = true;
                clarification = "Bạn muốn đặt khoảng ngân sách cụ thể cho mỗi người hay chỉ muốn xem các tour phù hợp mà không giới hạn giá?";
            }
            List<?> ids = selection.get("selectedTourIds") instanceof List<?> selected ? selected : List.of();
            var recommendations = clarify ? List.<com.trekking.app.features.integrateai.dto.response.TourRecommendation>of()
                    : tools.validateRecommendations(ids, maxBudget, minBudget, difficulty, departureDate, location, partySize);
            String answer;
            if (clarify && !clarification.isBlank()) answer = clarification;
            else if (recommendations.isEmpty()) answer = "Hiện chưa tìm thấy tour đã công bố có lịch và thông tin phù hợp trong danh mục. Bạn có thể nới ngày đi, ngân sách hoặc độ khó để mình tìm lại.";
            else answer = "Mình tìm được " + recommendations.size() + " tour phù hợp trong danh mục hiện tại. Giá, lịch khởi hành và số chỗ bên dưới được lấy từ dữ liệu hệ thống.";
            var recentIds = recommendations.stream().map(com.trekking.app.features.integrateai.dto.response.TourRecommendation::tourId).toList();
            var turns = new ArrayList<>(conversation.turns());
            turns.add(new ConversationStore.Turn("user", normalized));
            String contextualAnswer = answer + (recentIds.isEmpty() ? "" : " Tour ID đã gợi ý: " + recentIds);
            turns.add(new ConversationStore.Turn("assistant", contextualAnswer));
            int maxTurns = Math.max(1, props.session().maxTurns()) * 2;
            if (turns.size() > maxTurns) turns = new ArrayList<>(turns.subList(turns.size() - maxTurns, turns.size()));
            conversations.save(new ConversationStore.Conversation(conversation.id(), owner, List.copyOf(turns), recentIds));
            return new IntegrateAiChatResponse(conversation.id(), answer, recommendations);
        } catch (RuntimeException ex) {
            Throwable cause = ex;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            log.warn("AI consultation failed: {}: {}", cause.getClass().getSimpleName(), cause.getMessage());
            return new IntegrateAiChatResponse(conversation.id(), "Mình chưa thể tra cứu chính xác lúc này. Vui lòng thử lại sau.", List.of());
        }
    }

    private void validateExtractedCriteria(String max, String min, String date) {
        BigDecimal maxValue = budget(max), minValue = budget(min);
        if (maxValue != null && minValue != null && minValue.compareTo(maxValue) > 0) throw new IllegalArgumentException("Budget range invalid");
        if (date != null && !date.isBlank()) java.time.LocalDate.parse(date);
    }

    private BigDecimal budget(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("\\d{1," + props.budget().maxDigits() + "}")) throw new IllegalArgumentException("Budget invalid");
        var number = new BigDecimal(value);
        return number;
    }

    private boolean isUnusual(String value) {
        BigDecimal amount = budget(value);
        return amount != null && amount.compareTo(props.budget().unusualThresholdVnd()) >= 0;
    }

    private String explicitDate(String question) {
        var vietnamDate = java.util.regex.Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})(?!\\d)")
                .matcher(question);
        if (vietnamDate.find()) {
            try {
                return java.time.LocalDate.of(Integer.parseInt(vietnamDate.group(3)), Integer.parseInt(vietnamDate.group(2)),
                        Integer.parseInt(vietnamDate.group(1))).toString();
            } catch (java.time.DateTimeException ex) {
                return null;
            }
        }
        var isoDate = java.util.regex.Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)").matcher(question);
        return isoDate.find() ? java.time.LocalDate.parse(isoDate.group(1)).toString() : null;
    }

    private String text(Object value) { return value instanceof String s ? s.strip() : ""; }

    private int partySize(Object suggestedSize, String question) {
        var explicit = java.util.regex.Pattern.compile("(?iu)(?<!\\d)(\\d{1,3})\\s*(?:người|khách|thành viên)")
                .matcher(question);
        if (explicit.find()) {
            try {
                int parsed = Integer.parseInt(explicit.group(1));
                if (parsed >= 1 && parsed <= 100) return parsed;
            } catch (NumberFormatException ignored) { }
        }
        if (suggestedSize instanceof Number number) {
            int parsed = number.intValue();
            if (parsed >= 1 && parsed <= 100) return parsed;
        }
        return 1;
    }

    private String owner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())
                ? "anonymous" : "account:" + authentication.getName();
    }
}
