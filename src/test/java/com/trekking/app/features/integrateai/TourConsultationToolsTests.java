package com.trekking.app.features.integrateai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.trekking.app.features.booking.dto.response.ScheduleAvailabilityResponse;
import com.trekking.app.features.booking.service.BookingService;
import com.trekking.app.features.integrateai.config.IntegrateAiProperties;
import com.trekking.app.features.integrateai.config.GeminiProperties;
import com.trekking.app.features.integrateai.config.SessionProperties;
import com.trekking.app.features.integrateai.config.AiRequestLimitsProperties;
import com.trekking.app.features.integrateai.config.TourCatalogProperties;
import com.trekking.app.features.integrateai.config.BudgetProperties;
import com.trekking.app.features.integrateai.service.impl.TourConsultationToolsImpl;
import com.trekking.app.features.tour.dto.response.*;
import com.trekking.app.features.tour.enums.*;
import com.trekking.app.features.tour.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class TourConsultationToolsTests {
    private static final long TOUR_ID = 42L;
    private static final OffsetDateTime DAY_16 = OffsetDateTime.parse("2026-11-16T08:00:00+07:00");
    private TourService tours;
    private BookingService bookings;
    private TourConsultationToolsImpl tools;

    @BeforeEach
    void setUp() {
        tours = mock(TourService.class);
        var locations = mock(TourLocationService.class);
        bookings = mock(BookingService.class);
        var props = new IntegrateAiProperties(true,
                new GeminiProperties("", "https://generativelanguage.googleapis.com", "test", 0.1, 256, Duration.ofSeconds(5)),
                new SessionProperties(Duration.ofMinutes(30), 6),
                new AiRequestLimitsProperties(2000, 10, Duration.ofMinutes(1)),
                new TourCatalogProperties(20, 6, 3),
                new BudgetProperties(new BigDecimal("1000000000"), 30));
        tools = new TourConsultationToolsImpl(tours, locations, bookings, props);

        var summary = new TourSummaryResponse(TOUR_ID, "Tour thử nghiệm", DifficultyLevel.MODERATE, 2,
                TourStatus.PUBLISHED, "https://example.test/tour.jpg");
        when(tours.listPublic(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(summary)));
        when(tours.listPublicUpcomingSchedules(eq(TOUR_ID), anyInt(), anyInt())).thenReturn(schedulePage(
                schedule(100L, DAY_16, 2_000_000L), schedule(101L, DAY_16.plusDays(1), 3_000_000L)));
        when(locations.listPublicForTour(TOUR_ID)).thenReturn(List.of());
        when(bookings.availability(100L)).thenReturn(availability(100L, DAY_16, true, 7));
        when(bookings.availability(101L)).thenReturn(availability(101L, DAY_16.plusDays(1), true, 4));
    }

    @Test
    void filtersByExactDepartureDateAndBudgetUsingCurrentScheduleFacts() {
        var recommendations = tools.validateRecommendations(List.of(TOUR_ID), "2500000", null,
                "MODERATE", "2026-11-16", "", 1);

        assertEquals(1, recommendations.size());
        assertEquals(TOUR_ID, recommendations.getFirst().tourId());
        assertEquals(1, recommendations.getFirst().departures().size());
        assertEquals(DAY_16, recommendations.getFirst().departures().getFirst().startsAt());
        assertEquals(new BigDecimal("2000000"), recommendations.getFirst().priceFromVnd());
    }

    @Test
    void dropsForgedTourIdsInsteadOfInventingTours() {
        assertTrue(tools.validateRecommendations(List.of(999999L), null, null, null, null, null, 1).isEmpty());
    }

    @Test
    void acceptsVeryLargeBudgetsWithoutOverflowOrUpselling() {
        var recommendations = tools.validateRecommendations(List.of(TOUR_ID), "100000000000", null,
                null, null, null, 1);

        assertEquals(new BigDecimal("2000000"), recommendations.getFirst().priceFromVnd());
    }

    @Test
    void rejectsNegativeBudgetSafely() {
        assertThrows(IllegalArgumentException.class,
                () -> tools.validateRecommendations(List.of(TOUR_ID), "-10", null, null, null, null, 1));
    }

    private org.springframework.data.domain.Page<ScheduleSummaryResponse> schedulePage(ScheduleSummaryResponse... schedules) {
        return new PageImpl<>(List.of(schedules));
    }

    private ScheduleSummaryResponse schedule(long id, OffsetDateTime start, long price) {
        return new ScheduleSummaryResponse(id, start, start.plusDays(2), 15, 2, 3, price, 0L, ScheduleStatus.SCHEDULED);
    }

    private ScheduleAvailabilityResponse availability(long id, OffsetDateTime start, boolean bookable, int seats) {
        return new ScheduleAvailabilityResponse(id, TOUR_ID, start, 15, 2, 3, seats, bookable);
    }
}
