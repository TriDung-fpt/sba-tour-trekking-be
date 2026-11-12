package com.trekking.app.features.integrateai.service.impl;

import com.trekking.app.features.booking.service.BookingService;
import com.trekking.app.features.integrateai.dto.response.TourRecommendation;
import com.trekking.app.features.integrateai.config.IntegrateAiProperties;
import com.trekking.app.features.integrateai.service.TourConsultationTools;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.service.TourLocationService;
import com.trekking.app.features.tour.service.TourService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TourConsultationToolsImpl implements TourConsultationTools {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final TourService tours;
    private final TourLocationService locations;
    private final BookingService bookings;
    private final IntegrateAiProperties props;

    public TourConsultationToolsImpl(TourService tours, TourLocationService locations, BookingService bookings,
                                     IntegrateAiProperties props) {
        this.tours = tours; this.locations = locations;
        this.bookings = bookings; this.props = props;
    }

    @Override
    public List<TourSummaryResponse> publicTours() {
        var page = tours.listPublic(null, null, null, 0, props.catalog().maxTours());
        return page.getContent().stream().filter(t -> t.status() == TourStatus.PUBLISHED).toList();
    }

    @Override
    public List<Map<String, Object>> catalog() {
        return buildCatalog().facts();
    }

    private CatalogSnapshot buildCatalog() {
        var summaries = publicTours();
        var known = new LinkedHashMap<Long, TourSummaryResponse>();
        var result = new ArrayList<Map<String, Object>>();
        for (var tour : summaries) {
            known.put(tour.id(), tour);
            var tourLocations = locations.listPublicForTour(tour.id());
            var schedulePage = tours.listPublicUpcomingSchedules(tour.id(), 0, props.catalog().maxSchedulesPerTour());
            var schedules = new ArrayList<Map<String, Object>>();
            schedulePage.getContent().forEach(schedule -> {
                var available = bookings.availability(schedule.id());
                schedules.add(Map.of("scheduleId", schedule.id(), "startsAt", schedule.startsAt().toString(),
                        "availableSeats", available.availableSeats(), "bookable", available.bookable(),
                        "priceFromVnd", schedule.basePriceVnd() == null ? "" : schedule.basePriceVnd().toString(),
                        "equipmentFeeVnd", schedule.equipmentFeeVnd() == null ? "" : schedule.equipmentFeeVnd().toString()));
            });
            result.add(Map.ofEntries(Map.entry("tourId", tour.id()), Map.entry("title", tour.title()),
                    Map.entry("difficulty", tour.difficulty() == null ? "" : tour.difficulty().name()),
                    Map.entry("durationDays", tour.durationDays() == null ? 0 : tour.durationDays()),
                    Map.entry("coverImageUrl", tour.coverImageUrl() == null ? "" : tour.coverImageUrl()),
                    Map.entry("locations", tourLocations.stream().map(l -> Map.of("name", l.name(), "province", l.provinceName() == null ? "" : l.provinceName())).toList()),
                    Map.entry("schedules", schedules)));
        }
        return new CatalogSnapshot(List.copyOf(result), Map.copyOf(known));
    }

    @Override
    public List<TourRecommendation> validateRecommendations(List<?> ids, String maxBudgetVnd,
                                                            String minBudgetVnd, String difficulty,
                                                            String departureDate, String locationQuery,
                                                            int partySize) {
        if (ids == null) return List.of();
        var max = parseBudget(maxBudgetVnd);
        var min = parseBudget(minBudgetVnd);
        var requestedDate = departureDate == null || departureDate.isBlank() ? null : LocalDate.parse(departureDate);
        if (difficulty != null && !difficulty.isBlank()) {
            try { com.trekking.app.features.tour.enums.DifficultyLevel.valueOf(difficulty.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { difficulty = "INVALID"; }
        }
        var snapshot = buildCatalog(); // Re-fetch dynamic facts and confirm currently public IDs.
        var facts = new HashMap<Long, Map<String, Object>>();
        snapshot.facts().forEach(t -> facts.put(((Number) t.get("tourId")).longValue(), t));
        // Validate model-selected IDs against exact backend criteria and fresh facts.
        var output = new ArrayList<TourRecommendation>();
        for (Object raw : ids) {
            Long parsedId = exactId(raw);
            if (parsedId == null) continue;
            long id = parsedId;
            var fact = facts.get(id);
            var summary = snapshot.summaries().get(id);
            if (fact == null || summary == null) continue;
            if (difficulty != null && !difficulty.isBlank() && !difficulty.equals("INVALID")
                    && !difficulty.equalsIgnoreCase(String.valueOf(fact.get("difficulty")))) continue;
            if ("INVALID".equals(difficulty)) continue;
            var locationsData = (List<Map<String, Object>>) fact.get("locations");
            if (locationQuery != null && !locationQuery.isBlank() && locationsData.stream().noneMatch(loc ->
                    (String.valueOf(loc.get("name")) + " " + String.valueOf(loc.get("province")))
                            .toLowerCase(Locale.ROOT).contains(locationQuery.toLowerCase(Locale.ROOT)))) continue;
            var scheduleFacts = (List<Map<String, Object>>) fact.get("schedules");
            var departures = new ArrayList<TourRecommendation.Departure>();
            BigDecimal price = null;
            for (var schedule : scheduleFacts) {
                if (!Boolean.TRUE.equals(schedule.get("bookable"))) continue;
                var start = java.time.OffsetDateTime.parse((String) schedule.get("startsAt"));
                int seats = ((Number) schedule.get("availableSeats")).intValue();
                String priceText = String.valueOf(schedule.get("priceFromVnd"));
                BigDecimal schedulePrice = priceText.isBlank() ? null : new BigDecimal(priceText);
                if (requestedDate != null && !requestedDate.equals(start.atZoneSameInstant(VIETNAM).toLocalDate())) continue;
                if (max != null && (schedulePrice == null || schedulePrice.compareTo(max) > 0)) continue;
                if (min != null && (schedulePrice == null || schedulePrice.compareTo(min) < 0)) continue;
                if (seats <= 0) continue;
                String equipmentFeeText = String.valueOf(schedule.get("equipmentFeeVnd"));
                BigDecimal equipmentFee = equipmentFeeText.isBlank() ? null : new BigDecimal(equipmentFeeText);
                departures.add(new TourRecommendation.Departure(start, seats, schedulePrice, equipmentFee));
                if (!priceText.isBlank()) {
                    var candidatePrice = schedulePrice;
                    if (price == null || candidatePrice.compareTo(price) < 0) price = candidatePrice;
                }
            }
            if ((requestedDate != null || max != null || min != null) && departures.isEmpty()) continue;
            var placeNames = locationsData.stream()
                    .map(l -> String.valueOf(l.get("name")) + (String.valueOf(l.get("province")).isBlank() ? "" : ", " + l.get("province"))).toList();
            String groundedReason = requestedDate != null ? "Có lịch khởi hành đúng ngày bạn chọn."
                    : max != null || min != null ? "Có lịch với mức giá nằm trong ngân sách bạn đưa ra."
                    : difficulty != null && !difficulty.isBlank() ? "Độ khó khớp với mức bạn mong muốn."
                    : "Tour này đang được công bố trong danh mục WeTrek.";
            output.add(new TourRecommendation(id, summary.title(), String.valueOf(fact.get("difficulty")),
                    ((Number) fact.get("durationDays")).intValue(), (String) fact.get("coverImageUrl"),
                    price, partySize, price == null ? null : price.multiply(BigDecimal.valueOf(partySize)),
                    departures, placeNames, groundedReason));
        }
        return output.stream().limit(props.catalog().maxRecommendations()).toList();
    }

    private BigDecimal parseBudget(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("\\d{1," + props.budget().maxDigits() + "}")) throw new IllegalArgumentException("Invalid budget");
        var parsed = new BigDecimal(value);
        return parsed;
    }

    private Long exactId(Object value) {
        if (!(value instanceof Number number)) return null;
        try { return new BigDecimal(number.toString()).longValueExact(); }
        catch (NumberFormatException | ArithmeticException ex) { return null; }
    }

    private record CatalogSnapshot(List<Map<String, Object>> facts, Map<Long, TourSummaryResponse> summaries) {}
}
