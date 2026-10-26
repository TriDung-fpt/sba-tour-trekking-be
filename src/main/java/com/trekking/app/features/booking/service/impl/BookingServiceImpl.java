package com.trekking.app.features.booking.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.dto.response.AuthorizationResponse;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.booking.dto.request.BookingPassengerRequest;
import com.trekking.app.features.booking.dto.request.BookingSearchCriteria;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.request.ReplacePassengersRequest;
import com.trekking.app.features.booking.dto.request.UpdatePassengerRequest;
import com.trekking.app.features.booking.dto.response.BookingCancellationResponse;
import com.trekking.app.features.booking.dto.response.BookingCreateResult;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import com.trekking.app.features.booking.dto.response.ScheduleAvailabilityResponse;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingEquipmentChoice;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.exception.BookingErrorCode;
import com.trekking.app.features.booking.exception.BookingException;
import com.trekking.app.features.booking.mapper.BookingMapper;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.BookingRepository.MoneyTotalsView;
import com.trekking.app.features.booking.repository.BookingScheduleRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.BookingHoldService;
import com.trekking.app.features.booking.service.BookingService;
import com.trekking.app.features.policy.entity.Policy;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.policy.enums.PolicyKind;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingServiceImpl implements BookingService {
    private static final Duration HOLD_DURATION = Duration.ofMinutes(15);
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final BookingScheduleRepository schedules;
    private final BookingHoldService holds;
    private final BookingMapper mapper;
    private final AuthorizationService authorization;
    private final Clock clock;

    public BookingServiceImpl(BookingRepository bookings, ParticipantRepository participants,
                              BookingScheduleRepository schedules, BookingHoldService holds, BookingMapper mapper,
                              AuthorizationService authorization, Clock clock) {
        this.bookings = bookings;
        this.participants = participants;
        this.schedules = schedules;
        this.holds = holds;
        this.mapper = mapper;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    public BookingCreateResult create(CreateBookingRequest request) {
        long customerId = requireCustomer();
        requireAtMostOneBookingCustomer(request.passengers());

        bookings.lockIdempotencyKey(request.idempotencyKey());
        Optional<Booking> existing = bookings.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Booking booking = existing.get();
            if (booking.getCustomerId() != customerId || !booking.getScheduleId().equals(request.scheduleId())) {
                throw new BookingException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new BookingCreateResult(false, detail(booking, booking.getSchedule()));
        }

        // Runs in its own transaction before the schedule lock to keep the Booking -> Schedule lock order.
        holds.expireDueHolds(request.scheduleId());

        OffsetDateTime now = OffsetDateTime.now(clock);
        TourSchedule schedule = schedules.findForUpdate(request.scheduleId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        PolicyVersion policy = requireBookable(schedule, now);
        int seats = request.passengers().size();
        int available = BookingMapper.availableSeats(schedule);
        if (available < seats) throw BookingException.insufficientSeats(available);
        if (request.equipmentChoice() == BookingEquipmentChoice.TOUR_PACKAGE) {
            // No equipment inventory adapter exists yet; never record a fake package reservation.
            throw new BookingException(BookingErrorCode.EQUIPMENT_SERVICE_UNAVAILABLE);
        }

        Booking booking = new Booking();
        booking.setBookingCode(newBookingCode());
        booking.setIdempotencyKey(request.idempotencyKey());
        booking.setCustomerId(customerId);
        booking.setScheduleId(schedule.getId());
        booking.setParticipantCount(seats);
        booking.setIncludesEquipment(false);
        booking.setBasePriceVnd(schedule.getBasePriceVnd());
        booking.setEquipmentFeeVnd(0L);
        booking.setDepositPercent(schedule.getDepositPercent());
        booking.setCancellationPolicyVersionId(policy.getId());
        booking.setPolicySnapshot(mapper.toJson(policySnapshot(policy)));
        booking.setEquipmentSnapshot(mapper.toJson(Map.of("choice", BookingEquipmentChoice.SELF_PROVIDED.name())));
        booking.setHoldExpiresAt(now.plus(HOLD_DURATION));
        booking.setBalanceDueAt(schedule.getBalanceDueAt());
        booking = bookings.saveAndFlush(booking);

        saveParticipants(booking.getId(), customerId, request.passengers());
        schedule.setHeldSeats(schedule.getHeldSeats() + seats);
        participants.flush();
        return new BookingCreateResult(true, detail(booking, schedule));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingSummaryResponse> listMine(BookingStatus status, int page, int size) {
        long customerId = requireCustomer();
        Specification<Booking> filter = (root, q, cb) -> cb.equal(root.get("customerId"), customerId);
        if (status != null) filter = filter.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return summaries(bookings.findAll(filter, pageRequest(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingSummaryResponse> search(BookingSearchCriteria criteria, int page, int size) {
        authorization.require(Permission.BOOKING_MANAGE);
        Specification<Booking> filter = (root, q, cb) -> cb.conjunction();
        if (criteria.status() != null) {
            filter = filter.and((root, q, cb) -> cb.equal(root.get("status"), criteria.status()));
        }
        if (criteria.scheduleId() != null) {
            filter = filter.and((root, q, cb) -> cb.equal(root.get("scheduleId"), criteria.scheduleId()));
        }
        if (criteria.tourId() != null) {
            filter = filter.and((root, q, cb) -> cb.equal(root.get("schedule").get("tourId"), criteria.tourId()));
        }
        if (criteria.customerId() != null) {
            filter = filter.and((root, q, cb) -> cb.equal(root.get("customerId"), criteria.customerId()));
        }
        if (criteria.createdFrom() != null) {
            filter = filter.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            filter = filter.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), criteria.createdTo()));
        }
        return summaries(bookings.findAll(filter, pageRequest(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse get(long bookingId) {
        // Foreign and unknown bookings both return 404 so IDs cannot be probed.
        Optional<Booking> booking = canManageBookings()
                ? bookings.findWithScheduleById(bookingId)
                : bookings.findWithScheduleByIdAndCustomerId(bookingId, authorization.currentAccountId());
        Booking found = booking.orElseThrow(BookingException::notFound);
        return detail(found, found.getSchedule());
    }

    @Override
    public BookingDetailResponse replacePassengers(long bookingId, ReplacePassengersRequest request) {
        long customerId = requireCustomer();
        requireAtMostOneBookingCustomer(request.passengers());
        OffsetDateTime now = OffsetDateTime.now(clock);
        Booking booking = bookings.findOwnedForUpdate(bookingId, customerId).orElseThrow(BookingException::notFound);
        requireLiveUnpaidHold(booking, now);

        TourSchedule schedule = schedules.findForUpdate(booking.getScheduleId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        int seats = request.passengers().size();
        int delta = seats - booking.getParticipantCount();
        int available = BookingMapper.availableSeats(schedule);
        if (delta > 0 && available < delta) throw BookingException.insufficientSeats(available);
        schedule.setHeldSeats(schedule.getHeldSeats() + delta);

        participants.deleteAllByBookingId(bookingId);
        // Hibernate runs inserts before deletes; flush so the (booking_id, account_id) key is free again.
        participants.flush();
        saveParticipants(bookingId, customerId, request.passengers());
        booking.setParticipantCount(seats);
        participants.flush();
        return detail(booking, schedule);
    }

    @Override
    public BookingDetailResponse updatePassenger(long bookingId, long participantId, UpdatePassengerRequest request) {
        long customerId = requireCustomer();
        OffsetDateTime now = OffsetDateTime.now(clock);
        Booking booking = bookings.findOwnedForUpdate(bookingId, customerId).orElseThrow(BookingException::notFound);
        BookingStatus status = booking.getStatus();
        if ((status != BookingStatus.PENDING_PAYMENT && status != BookingStatus.CONFIRMED)
                || !booking.getSchedule().getStartsAt().isAfter(now)) {
            throw new BookingException(BookingErrorCode.BOOKING_NOT_EDITABLE);
        }
        Participant passenger = participants.findByIdAndBookingId(participantId, bookingId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND, "Passenger not found"));
        passenger.setFullName(request.fullName().trim());
        passenger.setPhone(blankToNull(request.phone()));
        passenger.setEmail(blankToNull(request.email()));
        participants.flush();
        return detail(booking, booking.getSchedule());
    }

    @Override
    public BookingCancellationResponse cancel(long bookingId, CancelBookingRequest request) {
        long customerId = requireCustomer();
        Booking booking = bookings.findOwnedForUpdate(bookingId, customerId).orElseThrow(BookingException::notFound);
        if (booking.getStatus() == BookingStatus.CANCELLED) return cancellation(booking);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingException(BookingErrorCode.INVALID_BOOKING_TRANSITION);
        }

        TourSchedule schedule = schedules.findForUpdate(booking.getScheduleId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        int seats = booking.getParticipantCount();
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            schedule.setHeldSeats(schedule.getHeldSeats() - seats);
        } else {
            schedule.setConfirmedSeats(schedule.getConfirmedSeats() - seats);
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(OffsetDateTime.now(clock));
        booking.setCancellationReason(request.reason().trim());
        booking.setCancelledBy(customerId);
        bookings.flush();
        return cancellation(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleAvailabilityResponse availability(long scheduleId) {
        TourSchedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        boolean published = schedule.getTour().getStatus() == TourStatus.PUBLISHED;
        if (!published && !canManageBookings()) throw new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND);
        boolean bookable = published
                && schedule.getStatus() == ScheduleStatus.SCHEDULED
                && schedule.getStartsAt().isAfter(OffsetDateTime.now(clock))
                && BookingMapper.availableSeats(schedule) > 0;
        return mapper.toAvailability(schedule, bookable);
    }

    private PolicyVersion requireBookable(TourSchedule schedule, OffsetDateTime now) {
        Tour tour = schedule.getTour();
        if (tour.getStatus() != TourStatus.PUBLISHED || schedule.getStatus() != ScheduleStatus.SCHEDULED
                || !schedule.getStartsAt().isAfter(now)) {
            throw new BookingException(BookingErrorCode.BOOKING_NOT_AVAILABLE);
        }
        // Use exactly the version assigned to the tour; never substitute a newer one.
        PolicyVersion version = tour.getCancellationPolicyVersion();
        if (version == null || version.getApprovedAt() == null || version.getEffectiveFrom().isAfter(now)
                || version.getPolicy().getKind() != PolicyKind.CANCELLATION) {
            throw new BookingException(BookingErrorCode.BOOKING_NOT_AVAILABLE,
                    "Tour has no effective cancellation policy");
        }
        return version;
    }

    private void requireLiveUnpaidHold(Booking booking, OffsetDateTime now) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || booking.getPriceLockedAt() != null) {
            throw new BookingException(BookingErrorCode.BOOKING_NOT_EDITABLE);
        }
        if (!booking.getHoldExpiresAt().isAfter(now)) {
            throw new BookingException(BookingErrorCode.BOOKING_HOLD_EXPIRED);
        }
    }

    private void saveParticipants(long bookingId, long customerId, List<BookingPassengerRequest> passengers) {
        participants.saveAll(passengers.stream().map(request -> {
            Participant participant = new Participant();
            participant.setBookingId(bookingId);
            participant.setAccountId(request.isBookingCustomer() ? customerId : null);
            participant.setFullName(request.fullName().trim());
            participant.setPhone(blankToNull(request.phone()));
            participant.setEmail(blankToNull(request.email()));
            return participant;
        }).toList());
    }

    private static void requireAtMostOneBookingCustomer(List<BookingPassengerRequest> passengers) {
        if (passengers.stream().filter(BookingPassengerRequest::isBookingCustomer).count() > 1) {
            throw new BookingException(BookingErrorCode.MULTIPLE_BOOKING_CUSTOMERS);
        }
    }

    private static Map<String, Object> policySnapshot(PolicyVersion version) {
        Policy policy = version.getPolicy();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyId", policy.getId());
        snapshot.put("policyCode", policy.getCode());
        snapshot.put("title", policy.getTitle());
        snapshot.put("versionId", version.getId());
        snapshot.put("versionNumber", version.getVersionNumber());
        snapshot.put("content", version.getContent());
        snapshot.put("effectiveFrom", version.getEffectiveFrom().toString());
        return snapshot;
    }

    private BookingDetailResponse detail(Booking booking, TourSchedule schedule) {
        long id = booking.getId();
        return mapper.toDetail(booking, schedule, participants.findAllByBookingIdOrderByIdAsc(id),
                toLong(bookings.acceptedPaymentTotalVnd(id)), toLong(bookings.refundedTotalVnd(id)));
    }

    private BookingCancellationResponse cancellation(Booking booking) {
        long id = booking.getId();
        return mapper.toCancellation(booking,
                toLong(bookings.acceptedPaymentTotalVnd(id)), toLong(bookings.refundedTotalVnd(id)));
    }

    private Page<BookingSummaryResponse> summaries(Page<Booking> page) {
        if (page.isEmpty()) return page.map(booking -> mapper.toSummary(booking, 0, 0));
        Map<Long, MoneyTotalsView> totals = bookings.moneyTotals(page.map(Booking::getId).getContent()).stream()
                .collect(Collectors.toMap(MoneyTotalsView::getBookingId, Function.identity()));
        return page.map(booking -> {
            MoneyTotalsView money = totals.get(booking.getId());
            return money == null ? mapper.toSummary(booking, 0, 0)
                    : mapper.toSummary(booking, money.getPaidVnd(), money.getRefundedVnd());
        });
    }

    private long requireCustomer() {
        AuthorizationResponse current = authorization.authorization();
        if (!current.roles().contains(RoleType.CUSTOMER.getValue())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Action is not permitted");
        }
        return current.accountId();
    }

    private boolean canManageBookings() {
        return authorization.authorization().permissions().contains(Permission.BOOKING_MANAGE.name());
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private static String newBookingCode() {
        StringBuilder code = new StringBuilder("BK");
        for (int i = 0; i < CODE_LENGTH; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long toLong(BigDecimal value) {
        return value == null ? 0 : value.longValueExact();
    }
}
