package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.SaveItineraryRequest;
import com.trekking.app.features.tour.dto.request.SaveTourTermsRequest;
import com.trekking.app.features.tour.dto.response.ItineraryDayResponse;
import com.trekking.app.features.tour.dto.response.TourTermResponse;
import com.trekking.app.features.tour.entity.TourActivity;
import com.trekking.app.features.tour.entity.TourItineraryDay;
import com.trekking.app.features.tour.entity.TourTerm;
import com.trekking.app.features.tour.mapper.TourActivityMapper;
import com.trekking.app.features.tour.repository.TourActivityRepository;
import com.trekking.app.features.tour.repository.TourItineraryDayRepository;
import com.trekking.app.features.tour.repository.TourTermRepository;
import com.trekking.app.features.policy.repository.PolicyVersionRepository;
import com.trekking.app.features.policy.enums.PolicyKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.dto.request.CreateScheduleRequest;
import com.trekking.app.features.tour.dto.request.CreateTourRequest;
import com.trekking.app.features.tour.dto.request.UpdateScheduleRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourRequest;
import com.trekking.app.features.tour.dto.response.ScheduleDetailResponse;
import com.trekking.app.features.tour.dto.response.ScheduleSummaryResponse;
import com.trekking.app.features.tour.dto.response.TourDetailResponse;
import com.trekking.app.features.tour.dto.response.TourScheduleRangeResponse;
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
import com.trekking.app.features.tour.repository.TourScheduleDefaultsRepository;
import com.trekking.app.features.tour.entity.TourScheduleDefaults;
import com.trekking.app.features.tour.dto.request.SaveScheduleDefaultsRequest;
import com.trekking.app.features.tour.dto.response.ScheduleDefaultsResponse;
import com.trekking.app.features.tour.service.TourService;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final TourScheduleDefaultsRepository scheduleDefaults;
    private final TourMapper mapper;
    private final TourActivityMapper activityMapper;
    private final TourActivityRepository activities;
    private final TourItineraryDayRepository days;
    private final TourTermRepository terms;
    private final PolicyVersionRepository versions;
    private final Clock clock;
    private final com.trekking.app.features.policy.service.PolicyService policyService;
    private final com.trekking.app.core.security.authorization.AuthorizationService authorization;

    public TourServiceImpl(TourRepository tours, TourMediaRepository media, TourScheduleRepository schedules, TourScheduleDefaultsRepository scheduleDefaults, TourMapper mapper, TourActivityMapper activityMapper, TourActivityRepository activities, TourItineraryDayRepository days, TourTermRepository terms, PolicyVersionRepository versions, Clock clock,
                           com.trekking.app.features.policy.service.PolicyService policyService,
                           com.trekking.app.core.security.authorization.AuthorizationService authorization) {
        this.tours = tours;
        this.media = media;
        this.schedules = schedules;
        this.scheduleDefaults = scheduleDefaults;
        this.mapper = mapper;
        this.activityMapper = activityMapper;
        this.activities = activities;
        this.days = days;
        this.terms = terms;
        this.versions = versions;
        this.clock = clock;
        this.policyService = policyService;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TourScheduleRangeResponse> listSchedulesInRange(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_DATES", "End must be after start");
        var departures = schedules.findOverlappingSchedules(start, end);
        if (departures.isEmpty()) return List.of();
        var tourIds = departures.stream().map(TourSchedule::getTourId).distinct().toList();
        var counts = schedules.countSchedulesByTourIds(tourIds).stream()
                .collect(java.util.stream.Collectors.toMap(TourScheduleRepository.TourScheduleCount::getTourId,
                        TourScheduleRepository.TourScheduleCount::getScheduleCount));
        return departures.stream().map(s -> new TourScheduleRangeResponse(
                s.getId(), s.getTourId(), s.getTour().getTitle(), s.getStartsAt(), s.getEndsAt(),
                s.getStatus(), s.getTour().getStatus(), counts.getOrDefault(s.getTourId(), 0L),
                s.getCapacity(), s.getConfirmedSeats(), s.getHeldSeats())).toList();
    }

    @Override
    public TourDetailResponse create(CreateTourRequest request) {
        var tour = mapper.toTour(request);
        return detail(tours.saveAndFlush(tour));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(int page, int size) {
        return list(null, null, null, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(String query, DifficultyLevel difficulty, Integer durationDays,
                                         TourStatus status, int page, int size) {
        return listFiltered(query, difficulty, durationDays, status, null, page, size);
    }

    private Page<TourSummaryResponse> listFiltered(String query, DifficultyLevel difficulty, Integer durationDays,
                                                   TourStatus status, LocalDate departureDate, int page, int size) {
        var pageable = pageRequest(page, size);
        if (durationDays != null && durationDays < 1) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid duration",
                    Map.of("durationDays", "Must be positive"));
        }
        Specification<Tour> filter = (root, q, cb) -> cb.conjunction();
        if (status == null) filter = filter.and((root, q, cb) -> cb.notEqual(root.get("status"), TourStatus.ARCHIVED));
        if (query != null && !query.isBlank()) {
            var term = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filter = filter.and((root, q, cb) -> cb.like(cb.lower(root.get("title")), "%" + term + "%", '\\'));
        }
        if (difficulty != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("difficulty"), difficulty));
        if (durationDays != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("durationDays"), durationDays));
        if (status != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("status"), status));
        if (departureDate != null) {
            var zone = ZoneId.of("Asia/Ho_Chi_Minh");
            var dayStart = departureDate.atStartOfDay(zone).toOffsetDateTime();
            var dayEnd = departureDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
            var now = OffsetDateTime.now(clock);
            filter = filter.and((root, q, cb) -> {
                var matchingSchedule = q.subquery(Long.class);
                var schedule = matchingSchedule.from(TourSchedule.class);
                matchingSchedule.select(schedule.get("tourId")).where(
                        cb.equal(schedule.get("tourId"), root.get("id")),
                        cb.equal(schedule.get("status"), ScheduleStatus.SCHEDULED),
                        cb.greaterThanOrEqualTo(schedule.<OffsetDateTime>get("startsAt"), now),
                        cb.lessThan(schedule.<OffsetDateTime>get("startsAt"), dayEnd),
                        cb.greaterThan(schedule.<OffsetDateTime>get("endsAt"), dayStart));
                return cb.exists(matchingSchedule);
            });
        }
        return summaries(tours.findAll(filter, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> listPublic(String query, DifficultyLevel difficulty, Integer durationDays,
                                                int page, int size) {
        return listPublic(query, difficulty, durationDays, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> listPublic(String query, DifficultyLevel difficulty, Integer durationDays,
                                                LocalDate departureDate, int page, int size) {
        return listFiltered(query, difficulty, durationDays, TourStatus.PUBLISHED, departureDate, page, size);
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
    @Transactional(readOnly = true)
    public TourDetailResponse readPublic(long tourId) {
        return detail(requirePublishedTour(tourId));
    }

    @Override
    public TourDetailResponse update(long tourId, UpdateTourRequest request) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        if (request.cancellationPolicyVersionId() != null) {
            policyService.requireEffectiveCancellationVersion(request.cancellationPolicyVersionId());
            tour.setCancellationPolicyVersionId(request.cancellationPolicyVersionId());
        }
        if (request.status() == TourStatus.PUBLISHED && tour.getStatus() != TourStatus.PUBLISHED) {
            if (tour.getCancellationPolicyVersionId() == null) {
                throw new TourException(HttpStatus.CONFLICT, "CANCELLATION_POLICY_UNAVAILABLE", "Select a cancellation policy before publishing");
            }
            policyService.requireEffectiveCancellationVersion(tour.getCancellationPolicyVersionId());
            tour.setReviewedBy(authorization.currentAccountId());
            tour.setReviewedAt(OffsetDateTime.now());
        }
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
        tour.setStatus(TourStatus.ARCHIVED);
        tours.saveAndFlush(tour);
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
    public java.util.Optional<ScheduleDefaultsResponse> readScheduleDefaults(long tourId) {
        authorization.require(com.trekking.app.core.security.authorization.Permission.SCHEDULE_MANAGE);
        requireTour(tourId);
        return scheduleDefaults.findById(tourId).map(TourServiceImpl::scheduleDefaultsResponse);
    }

    @Override
    public ScheduleDefaultsResponse saveScheduleDefaults(long tourId, SaveScheduleDefaultsRequest request) {
        authorization.require(com.trekking.app.core.security.authorization.Permission.SCHEDULE_MANAGE);
        tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        var value = scheduleDefaults.findById(tourId).orElseGet(TourScheduleDefaults::new);
        value.setTourId(tourId); value.setCapacity(request.capacity()); value.setBasePriceVnd(request.basePriceVnd());
        value.setEquipmentFeeVnd(request.equipmentFeeVnd()); value.setDepositPercent(request.depositPercent());
        value.setMeetingPoint(request.meetingPoint()); value.setContactPhone(request.contactPhone());
        return scheduleDefaultsResponse(scheduleDefaults.saveAndFlush(value));
    }

    private static ScheduleDefaultsResponse scheduleDefaultsResponse(TourScheduleDefaults value) {
        return new ScheduleDefaultsResponse(value.getCapacity(), value.getBasePriceVnd(), value.getEquipmentFeeVnd(),
                value.getDepositPercent(), value.getMeetingPoint(), value.getContactPhone());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<ScheduleDetailResponse> latestSchedule(long tourId) {
        authorization.require(com.trekking.app.core.security.authorization.Permission.SCHEDULE_MANAGE);
        requireTour(tourId);
        return schedules.findFirstByTourIdOrderByDetailsUpdatedAtDescIdDesc(tourId).map(mapper::toDetailResponse);
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
    public Page<ScheduleSummaryResponse> listPublicUpcomingSchedules(long tourId, int page, int size) {
        requirePublishedTour(tourId);
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
    @Transactional(readOnly = true)
    public ScheduleDetailResponse readPublicSchedule(long tourId, long scheduleId) {
        requirePublishedTour(tourId);
        var schedule = schedules.findByIdAndTourId(scheduleId, tourId)
                .filter(value -> value.getStatus() == ScheduleStatus.SCHEDULED
                        && value.getStartsAt().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> notFound("Schedule"));
        return mapper.toDetailResponse(schedule);
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

    @Override
    @Transactional(readOnly = true)
    public List<ItineraryDayResponse> readItinerary(long tourId, boolean publicAccess) {
        Tour tour = tours.findById(tourId).orElseThrow(() -> notFound("Tour"));
        if (publicAccess && tour.getStatus() != TourStatus.PUBLISHED) throw notFound("Tour");
        return readDays(tourId);
    }

    private List<ItineraryDayResponse> readDays(long tourId) {
        var grouped = new TreeMap<Integer, List<com.trekking.app.features.tour.dto.response.ActivityResponse>>();
        activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAscIdAsc(tourId)
                .forEach(a -> grouped.computeIfAbsent(a.getDayNumber(), ignored -> new ArrayList<>()).add(activityMapper.toResponse(a)));
        var metadata = new HashMap<Integer, TourItineraryDay>();
        days.findAllByTourIdOrderByDayNumberAsc(tourId).forEach(d -> { metadata.put(d.getDayNumber(), d); grouped.putIfAbsent(d.getDayNumber(), new ArrayList<>()); });
        return grouped.entrySet().stream().map(e -> {
            var d = metadata.get(e.getKey());
            return new ItineraryDayResponse(e.getKey(), d == null ? "Ngày " + e.getKey() : d.getTitle(),
                    d == null ? null : d.getMeals(), d == null ? null : d.getOvernight(), e.getValue());
        }).toList();
    }

    @Override
    public List<ItineraryDayResponse> saveItinerary(long tourId, SaveItineraryRequest request) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
        var numbers = new HashSet<Integer>();
        for (var day : request.days()) {
            if (!numbers.add(day.dayNumber()) || (tour.getDurationDays() != null && day.dayNumber() > tour.getDurationDays())) {
                throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_ITINERARY_DAY", "Day numbers must be unique and within the tour duration");
            }
        }
        activities.deleteAllInBatch(activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAscIdAsc(tourId));
        days.deleteAllByTourId(tourId);
        days.flush();
        for (var input : request.days()) {
            var day = new TourItineraryDay();
            day.setTourId(tourId); day.setDayNumber(input.dayNumber()); day.setTitle(input.title().trim());
            day.setMeals(input.meals()); day.setOvernight(input.overnight()); days.save(day);
            int order = 0;
            for (var item : input.activities()) {
                var activity = new TourActivity();
                activity.setTourId(tourId); activity.setDayNumber(input.dayNumber()); activity.setDisplayOrder(order++);
                activity.setStartTime(item.startTime()); activity.setTitle(item.title().trim());
                activity.setDescription(item.description()); activity.setLocationId(item.locationId()); activities.save(activity);
            }
        }
        activities.flush(); days.flush();
        return readDays(tourId);
    }


    @Override @Transactional(readOnly = true)
    public List<TourTermResponse> readTerms(long tourId, boolean publicAccess) {
        var tour = tours.findById(tourId).orElseThrow(() -> notFound("Tour or term"));
        if (publicAccess && tour.getStatus() != TourStatus.PUBLISHED) throw notFound("Tour or term");
        var result = new java.util.ArrayList<TourTermResponse>();
        if (tour.getCancellationPolicyVersionId() != null) {
            var v = versions.findById(tour.getCancellationPolicyVersionId()).orElseThrow(() -> notFound("Tour or term"));
            if (!publicAccess || isAvailable(v))
                result.add(new TourTermResponse(v.getId(), v.getPolicy().getTitle(), v.getContent(), v.getVersionNumber(), -1, true));
        }
        result.addAll(responses(tourId, publicAccess));
        return result;
    }
    @Override
    public List<TourTermResponse> saveTerms(long tourId, SaveTourTermsRequest request) {
        var tour = tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour or term"));
        if (new HashSet<>(request.versionIds()).size() != request.versionIds().size())
            throw new TourException(HttpStatus.BAD_REQUEST, "DUPLICATE_TERM", "Terms must be unique");
        var now = OffsetDateTime.now(clock);
        if (Boolean.TRUE.equals(request.removeCancellationPolicy())) {
            if (request.cancellationPolicyVersionId() != null)
                throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_CANCELLATION_POLICY", "Cannot select and remove a cancellation policy simultaneously");
            if (tour.getStatus() == TourStatus.PUBLISHED)
                throw new TourException(HttpStatus.CONFLICT, "CANCELLATION_POLICY_REQUIRED", "Published tours require a cancellation policy");
            tour.setCancellationPolicyVersionId(null);
        }
        if (request.cancellationPolicyVersionId() != null && !request.cancellationPolicyVersionId().equals(tour.getCancellationPolicyVersionId())) {
            var cancellation = versions.findById(request.cancellationPolicyVersionId()).orElseThrow(() -> notFound("Tour or term"));
            if (cancellation.getPolicy().getKind() != PolicyKind.CANCELLATION || !isAvailable(cancellation))
                throw new TourException(HttpStatus.CONFLICT, "CANCELLATION_POLICY_UNAVAILABLE", "Select an effective cancellation policy");
            tour.setCancellationPolicyVersionId(cancellation.getId());
        }
        var policies = new HashSet<Long>();
        for (long id : request.versionIds()) {
            var v = versions.findById(id).orElseThrow(() -> notFound("Tour or term"));
            if (!policies.add(v.getPolicyId()))
                throw new TourException(HttpStatus.BAD_REQUEST, "DUPLICATE_TERM", "Only one version of each term can be selected");
            if (v.getPolicy().getKind() == PolicyKind.CANCELLATION)
                throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_TERM_KIND", "Cancellation policy is selected separately for booking");
            if (!Boolean.TRUE.equals(v.getPolicy().getActive()) || v.getApprovedAt() == null || v.getApprovedAt().isAfter(now)
                    || v.getEffectiveFrom().isAfter(now) || v.getRevocation() != null)
                throw new TourException(HttpStatus.CONFLICT, "TERM_UNAVAILABLE", "Selected term is not effective");
        }
        terms.deleteAllByTourId(tourId); terms.flush();
        int order = 0;
        for (long id : request.versionIds()) { var t = new TourTerm(); t.setTourId(tourId); t.setPolicyVersionId(id); t.setDisplayOrder(order++); terms.save(t); }
        terms.flush(); return responses(tourId, false);
    }
    private List<TourTermResponse> responses(long tourId, boolean publicAccess) {
        return terms.findAllByTourIdOrderByDisplayOrderAsc(tourId).stream().filter(t ->
                !publicAccess || isAvailable(versions.findById(t.getPolicyVersionId()).orElseThrow(() -> notFound("Tour or term")))).map(t -> {
            var v = versions.findById(t.getPolicyVersionId()).orElseThrow(() -> notFound("Tour or term"));
            return new TourTermResponse(v.getId(), v.getPolicy().getTitle(), v.getContent(), v.getVersionNumber(), t.getDisplayOrder(), false);
        }).toList();
    }
    private boolean isAvailable(com.trekking.app.features.policy.entity.PolicyVersion version) {
        var now = OffsetDateTime.now(clock);
        return Boolean.TRUE.equals(version.getPolicy().getActive()) && version.getRevocation() == null
                && version.getApprovedAt() != null && !version.getApprovedAt().isAfter(now)
                && !version.getEffectiveFrom().isAfter(now);
    }

    private Tour requireTour(long tourId) {
        return tours.findById(tourId).orElseThrow(() -> notFound("Tour"));
    }

    private Tour requirePublishedTour(long tourId) {
        var tour = requireTour(tourId);
        if (tour.getStatus() != TourStatus.PUBLISHED) {
            throw notFound("Tour");
        }
        return tour;
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
