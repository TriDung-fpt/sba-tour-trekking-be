package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.dto.request.CreateScheduleRequest;
import com.trekking.app.features.tour.dto.request.CreateTourRequest;
import com.trekking.app.features.tour.dto.request.UpdateScheduleRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourRequest;
import com.trekking.app.features.tour.dto.response.ScheduleDetailResponse;
import com.trekking.app.features.tour.dto.response.ScheduleSummaryResponse;
import com.trekking.app.features.tour.dto.response.TourDetailResponse;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.entity.TourMedia;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.mapper.TourMapper;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourMediaRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.TourService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TourServiceImpl implements TourService {
    private final TourRepository tours;
    private final TourMediaRepository media;
    private final TourScheduleRepository schedules;
    private final TourMapper mapper;

    public TourServiceImpl(TourRepository tours, TourMediaRepository media, TourScheduleRepository schedules, TourMapper mapper) {
        this.tours = tours;
        this.media = media;
        this.schedules = schedules;
        this.mapper = mapper;
    }

    @Override
    public TourDetailResponse create(CreateTourRequest request) {
        var tour = mapper.toTour(request);
        return detail(tours.saveAndFlush(tour));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(int page, int size) {
        return summaries(tours.findAll(pageRequest(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(String query, DifficultyLevel difficulty, Integer durationDays,
                                         TourStatus status, int page, int size) {
        var pageable = pageRequest(page, size);
        if (durationDays != null && durationDays < 1) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid duration",
                    Map.of("durationDays", "Must be positive"));
        }
        Specification<Tour> filter = (root, q, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            var term = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filter = filter.and((root, q, cb) -> cb.like(cb.lower(root.get("title")), "%" + term + "%", '\\'));
        }
        if (difficulty != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("difficulty"), difficulty));
        if (durationDays != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("durationDays"), durationDays));
        if (status != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return summaries(tours.findAll(filter, pageable));
    }

    private Page<TourSummaryResponse> summaries(Page<Tour> pageOfTours) {
        var tourIds = pageOfTours.getContent().stream().map(Tour::getId).toList();
        var covers = media.findAllByTourIdInAndIsCoverTrue(tourIds).stream()
                .collect(java.util.stream.Collectors.toMap(TourMedia::getTourId, TourMedia::getUrl));
        return pageOfTours.map(tour -> mapper.toSummaryResponse(tour, covers.get(tour.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public TourDetailResponse read(long tourId) {
        return detail(requireTour(tourId));
    }

    @Override
    public TourDetailResponse update(long tourId, UpdateTourRequest request) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        validatePublication(tour, request.status() == null ? tour.getStatus() : request.status());
        mapper.updateTour(request, tour);
        if (request.status() != null) {
            tour.setStatus(request.status());
        }
        return detail(tours.saveAndFlush(tour));
    }

    @Override
    public void delete(long tourId) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        if (tour.getStatus() != TourStatus.DRAFT || schedules.existsByTourId(tourId)) {
            throw conflict("TOUR_NOT_DELETABLE", "Only draft tours without schedules can be deleted");
        }
        if (media.existsByTourIdAndCloudinaryPublicIdIsNotNull(tourId)) {
            throw conflict("TOUR_HAS_MEDIA", "Delete uploaded tour images before deleting the tour");
        }
        try {
            tours.delete(tour);
            tours.flush();
        } catch (DataIntegrityViolationException ex) {
            throw conflict("TOUR_IN_USE", "Tour is referenced by other records and cannot be deleted");
        }
    }

    @Override
    public ScheduleDetailResponse createSchedule(long tourId, CreateScheduleRequest request) {
        // Serialize with tour deletion so a schedule cannot be created for a deleted tour.
        tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        validateDates(request.startsAt(), request.endsAt(), request.balanceDueAt());
        var schedule = mapper.toSchedule(request);
        schedule.setTourId(tourId);
        return mapper.toDetailResponse(schedules.saveAndFlush(schedule));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleSummaryResponse> listSchedules(long tourId, int page, int size) {
        requireTour(tourId);
        return schedules.findAllByTourId(tourId, pageRequest(page, size)).map(mapper::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleSummaryResponse> listUpcomingSchedules(long tourId, int page, int size) {
        requireTour(tourId);
        var pageable = pageRequest(page, size);
        return schedules.findAllByTourIdAndStatusAndStartsAtAfterOrderByStartsAtAscIdAsc(
                tourId, ScheduleStatus.SCHEDULED, OffsetDateTime.now(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())).map(mapper::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleDetailResponse readSchedule(long tourId, long scheduleId) {
        return mapper.toDetailResponse(schedules.findByIdAndTourId(scheduleId, tourId)
                .orElseThrow(() -> notFound("Schedule")));
    }

    @Override
    public ScheduleDetailResponse updateSchedule(long tourId, long scheduleId, UpdateScheduleRequest request) {
        var schedule = editableSchedule(tourId, scheduleId);
        validateDates(request.startsAt(), request.endsAt(), request.balanceDueAt());
        mapper.updateSchedule(request, schedule);
        return mapper.toDetailResponse(schedules.saveAndFlush(schedule));
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

    private TourDetailResponse detail(Tour tour) {
        var tourMedia = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tour.getId()).stream()
                .map(mapper::toResponse)
                .toList();
        return mapper.toDetailResponse(tour, tourMedia);
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

    private static void validatePublication(Tour tour, TourStatus status) {
        if (status != TourStatus.PUBLISHED) {
            return;
        }
        // Match the existing database rule; review metadata is managed by the backend.
        var fields = new LinkedHashMap<String, String>();
        if (tour.getReviewedBy() == null) {
            fields.put("reviewedBy", "Tour must have a reviewer before publishing");
        }
        if (tour.getReviewedAt() == null) {
            fields.put("reviewedAt", "Tour must have a review date before publishing");
        }
        if (tour.getCancellationPolicyVersionId() == null) {
            fields.put("cancellationPolicyVersionId", "Tour must have a cancellation policy before publishing");
        }
        if (!fields.isEmpty()) {
            throw new TourException(HttpStatus.CONFLICT, "TOUR_CANNOT_BE_PUBLISHED",
                    "Tour must be reviewed and have a cancellation policy before publishing", fields);
        }
    }

    private static void validateDates(OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime balanceDueAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_DATES", "endsAt must be after startsAt",
                    Map.of("endsAt", "Must be after startsAt"));
        }
        if (balanceDueAt.isAfter(startsAt)) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_BALANCE_DUE_AT", "balanceDueAt must be on or before startsAt",
                    Map.of("balanceDueAt", "Must be on or before startsAt"));
        }
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            var fields = new LinkedHashMap<String, String>();
            if (page < 0) fields.put("page", "Must be greater than or equal to 0");
            if (size < 1 || size > 100) fields.put("size", "Must be between 1 and 100");
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must be >= 0 and size must be between 1 and 100", fields);
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
