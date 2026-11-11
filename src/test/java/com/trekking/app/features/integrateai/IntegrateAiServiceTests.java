package com.trekking.app.features.integrateai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.trekking.app.features.integrateai.config.IntegrateAiProperties;
import com.trekking.app.features.integrateai.config.GeminiProperties;
import com.trekking.app.features.integrateai.config.SessionProperties;
import com.trekking.app.features.integrateai.config.AiRequestLimitsProperties;
import com.trekking.app.features.integrateai.config.TourCatalogProperties;
import com.trekking.app.features.integrateai.config.BudgetProperties;
import com.trekking.app.features.integrateai.dto.response.IntegrateAiChatResponse;
import com.trekking.app.features.integrateai.service.*;
import com.trekking.app.features.integrateai.service.impl.IntegrateAiServiceImpl;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrateAiServiceTests {
    private IntegrateAiProvider provider;
    private TourConsultationTools tools;
    private ConversationStore store;
    private IntegrateAiServiceImpl service;

    @BeforeEach
    void setUp() {
        provider = mock(IntegrateAiProvider.class);
        tools = mock(TourConsultationTools.class);
        store = mock(ConversationStore.class);
        var props = new IntegrateAiProperties(true,
                new GeminiProperties("unit-test-key", "https://generativelanguage.googleapis.com", "test", 0.1, 256, Duration.ofSeconds(2)),
                new SessionProperties(Duration.ofMinutes(30), 6),
                new AiRequestLimitsProperties(2000, 12, Duration.ofMinutes(1)),
                new TourCatalogProperties(20, 6, 3),
                new BudgetProperties(new BigDecimal("1000000000"), 100));
        service = new IntegrateAiServiceImpl(props, provider, tools, store);
        when(store.incrementRate(anyString())).thenReturn(1L);
        when(store.loadOrCreate(isNull(), eq("anonymous")))
                .thenReturn(new ConversationStore.Conversation("8b632cec-f69a-4c71-8f9d-0e2ee77d04cb", "anonymous", List.of(), List.of()));
        when(tools.catalog()).thenReturn(List.of(Map.of("tourId", 5L)));
        when(tools.validateRecommendations(anyList(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void serverUsesExplicitVietnameseDateInsteadOfModelDate() {
        when(provider.select(anyString(), anyList(), anyList())).thenReturn(selection(
                "2026-11-15", "", "", "", List.of(5L), false));

        IntegrateAiChatResponse response = service.chat("Tôi muốn đi ngày 16/11/2026", null, "127.0.0.1");

        assertNotNull(response.sessionId());
        verify(tools).validateRecommendations(List.of(5L), "", "", "", "2026-11-16", "", 1);
    }

    @Test
    void ambiguousHundredBillionBudgetAsksInsteadOfSelectingAnExpensiveTour() {
        when(provider.select(anyString(), anyList(), anyList())).thenReturn(selection(
                "", "100000000000", "", "", List.of(5L), false));

        IntegrateAiChatResponse response = service.chat("Tôi có 100 tỷ", null, "127.0.0.1");

        assertTrue(response.answer().contains("cho mỗi người hay cho cả đoàn"));
        assertTrue(response.recommendations().isEmpty());
        verify(tools, never()).validateRecommendations(anyList(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void providerFailureReturnsSafeMessageWithoutLeakingTechnicalDetails() {
        when(provider.select(anyString(), anyList(), anyList())).thenThrow(new IllegalStateException("private api error"));

        IntegrateAiChatResponse response = service.chat("Gợi ý tour", null, "127.0.0.1");

        assertEquals("Mình chưa thể tra cứu chính xác lúc này. Vui lòng thử lại sau.", response.answer());
        assertFalse(response.answer().contains("private api error"));
    }

    private Map<String, Object> selection(String date, String maxBudget, String minBudget, String difficulty,
                                          List<Long> ids, boolean clarify) {
        return Map.of("departureDate", date, "maxBudgetVnd", maxBudget, "minBudgetVnd", minBudget,
                "difficulty", difficulty, "partySize", 1, "selectedTourIds", ids, "needsClarification", clarify,
                "clarification", "");
    }
}
