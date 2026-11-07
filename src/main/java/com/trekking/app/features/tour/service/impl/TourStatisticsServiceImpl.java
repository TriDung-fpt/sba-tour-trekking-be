package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.tour.dto.response.TourStatisticsResponse;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.TourStatisticsService;
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
                .map(row -> new TourStatisticsResponse.Equipment(((Number) row[0]).longValue(),
                        (String) row[1], ((Number) row[2]).longValue())).toList();
        return new TourStatisticsResponse(schedules.countByTourId(tourId), equipment);
    }
}
