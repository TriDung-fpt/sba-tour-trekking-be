package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.tour.dto.response.TourStatisticsResponse;
import com.trekking.app.features.tour.dto.response.TourScheduleOverviewResponse;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.TourStatisticsService;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TourStatisticsServiceImpl implements TourStatisticsService {
    private final TourRepository tours;
    private final TourScheduleRepository schedules;
    private final EquipmentReservationRepository reservations;
    private final AuthorizationService authorization;

    public TourStatisticsServiceImpl(TourRepository tours, TourScheduleRepository schedules,
            EquipmentReservationRepository reservations, AuthorizationService authorization) {
        this.tours = tours;
        this.schedules = schedules;
        this.reservations = reservations;
        this.authorization = authorization;
    }

    @Override
    public TourStatisticsResponse read(long tourId) {
        authorization.require(Permission.TOUR_READ);
        if (!tours.existsById(tourId)) {
            throw new TourException(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Tour not found");
        }
        var equipment = reservations.summarizeBookedEquipment(tourId).stream()
                .map(row -> new TourStatisticsResponse.Equipment(row.getEquipmentId(),
                        row.getEquipmentName(), row.getQuantity())).toList();
        return new TourStatisticsResponse(schedules.countByTourId(tourId), equipment);
    }

    @Override
    public Page<TourScheduleOverviewResponse> scheduleOverview(String query, int page, int size) {
        authorization.require(Permission.SCHEDULE_MANAGE);
        if (page < 0 || size < 1 || size > 100) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page must be >= 0 and size must be between 1 and 100");
        }
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (term.length() > 200) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Search text must not exceed 200 characters");
        }
        return tours.findScheduleOverview(term, PageRequest.of(page, size)).map(row ->
                new TourScheduleOverviewResponse(row.getTourId(), row.getTitle(), row.getStatus(), row.getScheduleCount()));
    }
}
