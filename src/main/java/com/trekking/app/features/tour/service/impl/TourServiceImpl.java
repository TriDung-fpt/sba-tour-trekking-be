package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.dto.request.ScheduleRequest;
import com.trekking.app.features.tour.dto.request.TourRequest;
import com.trekking.app.features.tour.dto.response.ScheduleResponse;
import com.trekking.app.features.tour.dto.response.TourResponse;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.mapper.TourMapper;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.TourService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TourServiceImpl implements TourService {
    private final TourRepository tours;
    private final TourScheduleRepository schedules;
    private final TourMapper mapper;

    public TourServiceImpl(TourRepository tours, TourScheduleRepository schedules, TourMapper mapper) {
        this.tours = tours;
        this.schedules = schedules;
        this.mapper = mapper;
    }

    @Override
    public TourResponse create(TourRequest request) {
        var tour = new Tour();
        mapper.updateTour(request, tour);
        return mapper.toResponse(tours.saveAndFlush(tour));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourResponse> list(int page, int size) {
        return tours.findAll(pageRequest(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TourResponse read(long tourId) {
        return mapper.toResponse(requireTour(tourId));
    }

    @Override
    public TourResponse update(long tourId, TourRequest request) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        mapper.updateTour(request, tour);
        return mapper.toResponse(tours.saveAndFlush(tour));
    }

    @Override
    public void delete(long tourId) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        if (tour.getStatus() != TourStatus.DRAFT || schedules.existsByTourId(tourId)) {
            throw conflict("TOUR_NOT_DELETABLE", "Only draft tours without schedules can be deleted");
        }
        try {
            tours.delete(tour);
            tours.flush();
        } catch (DataIntegrityViolationException ex) {
            throw conflict("TOUR_IN_USE", "Tour is referenced by other records and cannot be deleted");
        }
    }

    @Override
    public ScheduleResponse createSchedule(long tourId, ScheduleRequest request) {
        // Serialize with tour deletion so a schedule cannot be created for a deleted tour.
        tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        validateDates(request);
        var schedule = new TourSchedule();
        schedule.setTourId(tourId);
        mapper.updateSchedule(request, schedule);
        return mapper.toResponse(schedules.saveAndFlush(schedule));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleResponse> listSchedules(long tourId, int page, int size) {
        requireTour(tourId);
        return schedules.findAllByTourId(tourId, pageRequest(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse readSchedule(long tourId, long scheduleId) {
        return mapper.toResponse(schedules.findByIdAndTourId(scheduleId, tourId)
                .orElseThrow(() -> notFound("Schedule")));
    }

    @Override
    public ScheduleResponse updateSchedule(long tourId, long scheduleId, ScheduleRequest request) {
        var schedule = editableSchedule(tourId, scheduleId);
        validateDates(request);
        mapper.updateSchedule(request, schedule);
        return mapper.toResponse(schedules.saveAndFlush(schedule));
    }

    @Override
    public void deleteSchedule(long tourId, long scheduleId) {
        var schedule = editableSchedule(tourId, scheduleId);
        try {
            schedules.delete(schedule);
            schedules.flush();
        } catch (DataIntegrityViolationException ex) {
            throw conflict("SCHEDULE_IN_USE", "Schedule is referenced by other records and cannot be deleted");
        }
    }

    private Tour requireTour(long tourId) {
        return tours.findById(tourId).orElseThrow(() -> notFound("Tour"));
    }

    private TourSchedule editableSchedule(long tourId, long scheduleId) {
        var schedule = schedules.findLockedByIdAndTourId(scheduleId, tourId)
                .orElseThrow(() -> notFound("Schedule"));
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED
                || schedule.getHeldSeats() != 0 || schedule.getConfirmedSeats() != 0) {
            throw conflict("SCHEDULE_NOT_EDITABLE", "Only scheduled departures without held or confirmed seats can be changed");
        }
        return schedule;
    }

    private static void validateDates(ScheduleRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_DATES", "endsAt must be after startsAt");
        }
        if (request.balanceDueAt().isAfter(request.startsAt())) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_BALANCE_DUE_AT", "balanceDueAt must be on or before startsAt");
        }
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page must be >= 0 and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by("id"));
    }

    private static TourException notFound(String resource) {
        return new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " was not found");
    }

    private static TourException conflict(String code, String message) {
        return new TourException(HttpStatus.CONFLICT, code, message);
    }
}
