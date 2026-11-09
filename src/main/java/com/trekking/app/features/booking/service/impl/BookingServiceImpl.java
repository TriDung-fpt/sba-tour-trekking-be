package com.trekking.app.features.booking.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.dto.response.AuthorizationResponse;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.booking.config.BookingProperties;
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
import com.trekking.app.features.booking.repository.BookingScheduleRepository;
import com.trekking.app.features.booking.repository.BookingSpecifications;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.BookingHoldService;
import com.trekking.app.features.booking.service.BookingPaymentService;
import com.trekking.app.features.booking.service.BookingService;
import com.trekking.app.features.equipment.service.EquipmentReservationService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional
public class BookingServiceImpl implements BookingService {
    private static final Duration HOLD_DURATION = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final AppAccountRepository accounts;
    private final BookingPaymentService bookingPayments;
    private final TransactionTemplate conflictLookup;
    private final ParticipantRepository participants;
    private final BookingScheduleRepository schedules;
    private final BookingHoldService holds;
    private final BookingMapper mapper;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final EquipmentReservationService equipmentReservations;
    private final BookingProperties properties;

    public BookingServiceImpl(BookingRepository bookings, ParticipantRepository participants,
                              BookingScheduleRepository schedules, BookingHoldService holds, BookingMapper mapper,
                              AuthorizationService authorization, Clock clock, EquipmentReservationService equipmentReservations,
                              AppAccountRepository accounts, BookingPaymentService bookingPayments,
                              PlatformTransactionManager transactionManager, BookingProperties properties) {
        this.bookings = bookings;
        this.accounts = accounts;
        this.bookingPayments = bookingPayments;
        this.conflictLookup = new TransactionTemplate(transactionManager);
        this.conflictLookup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.conflictLookup.setReadOnly(true);
        this.participants = participants;
        this.schedules = schedules;
        this.holds = holds;
        this.mapper = mapper;
        this.authorization = authorization;
        this.clock = clock;
        this.equipmentReservations = equipmentReservations;
        this.properties = properties;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingCreateResult create(CreateBookingRequest request) {
        long customerId = requireCustomer();
        requireAtMostOneBookingCustomer(request.passengers());
        String requestHash = mapper.toRequestHash(request);

        // Serialize this customer's creates before checking retries or holds, across all schedules.
        accounts.lockById(customerId).orElseThrow(() ->
                new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Account was not found"));
        Optional<Booking> existing = bookings.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Booking booking = existing.get();
            if (booking.getCustomerId() != customerId || !booking.getScheduleId().equals(request.scheduleId())) {
                throw new BookingException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            String savedHash = mapper.readRequestHash(booking);
            if (savedHash != null && !requestHash.equals(savedHash)) {
                throw new BookingException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new BookingCreateResult(false, detail(booking, booking.getSchedule()));
        }

        // The account row lock also prevents two live holds from concurrent requests.
        List<BookingSummaryResponse> activeHolds = activeHoldSummaries(customerId, OffsetDateTime.now(clock));
        if (!activeHolds.isEmpty()) throw BookingException.activeBookingHolds(activeHolds);

        // Runs in its own transaction before the schedule lock to keep the Booking -> Schedule lock order.
        holds.expireDueHolds(request.scheduleId());

        OffsetDateTime now = OffsetDateTime.now(clock);
        TourSchedule schedule = schedules.findLockedById(request.scheduleId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        // Another customer may have committed this key while we waited for the schedule lock.
        if (bookings.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
            throw new BookingException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        PolicyVersion policy = requireBookable(schedule, now);
        int seats = request.passengers().size();
        int available = BookingMapper.availableSeats(schedule);
        if (available < seats) throw BookingException.insufficientSeats(available);
        boolean hasEquipment = (request.packageQuantities() != null && !request.packageQuantities().isEmpty())
                || (request.itemQuantities() != null && !request.itemQuantities().isEmpty());
        if (hasEquipment != (request.equipmentChoice() == BookingEquipmentChoice.TOUR_PACKAGE)) {
            throw new BookingException(BookingErrorCode.INVALID_EQUIPMENT_SELECTION,
                    "Choose equipment options or select self-provided equipment");
        }

        Booking booking = mapper.toBooking(request, customerId, schedule, policy, newBookingCode(),
                now.plus(HOLD_DURATION), requestHash);
        try {
            booking = bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException exception) {
            // A different customer can race for the same globally unique key.
            // Inspect the winner in a new transaction because this write has failed.
            boolean reused = Boolean.TRUE.equals(conflictLookup.execute(ignored ->
                    bookings.findByIdempotencyKey(request.idempotencyKey()).isPresent()));
            if (reused) throw new BookingException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
            throw exception;
        }

        var hold = equipmentReservations.hold(mapper.toEquipmentHoldRequest(booking, request));
        mapper.updateEquipment(booking, hold, request.equipmentChoice(), requestHash);
        booking = bookings.saveAndFlush(booking);

        participants.saveAll(mapper.toParticipants(booking.getId(), customerId, request.passengers()));
        schedule.setHeldSeats(schedule.getHeldSeats() + seats);
        participants.flush();
        return new BookingCreateResult(true, detail(booking, schedule));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingSummaryResponse> listMine(BookingStatus status, int page, int size) {
        long customerId = requireCustomer();
        PageRequest pageable = pageRequest(page, size);
        return summaries(status == null
                ? bookings.findByCustomerId(customerId, pageable)
                : bookings.findByCustomerIdAndStatus(customerId, status, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> activeHolds() {
        return activeHoldSummaries(requireCustomer(), OffsetDateTime.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingSummaryResponse> search(BookingSearchCriteria criteria, int page, int size) {
        authorization.require(Permission.BOOKING_MANAGE);
        return summaries(bookings.findAll(BookingSpecifications.matches(criteria), pageRequest(page, size)));
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
        Booking booking = bookings.findLockedByIdAndCustomerId(bookingId, customerId).orElseThrow(BookingException::notFound);
        requireLiveUnpaidHold(booking, now);

        TourSchedule schedule = schedules.findLockedById(booking.getScheduleId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        int seats = request.passengers().size();
        int delta = seats - booking.getParticipantCount();
        int available = BookingMapper.availableSeats(schedule);
        if (delta > 0 && available < delta) throw BookingException.insufficientSeats(available);
        schedule.setHeldSeats(schedule.getHeldSeats() + delta);

        participants.deleteAllByBookingId(bookingId);
        // Hibernate runs inserts before deletes; flush so the (booking_id, account_id) key is free again.
        participants.flush();
        participants.saveAll(mapper.toParticipants(bookingId, customerId, request.passengers()));
        booking.setParticipantCount(seats);
        participants.flush();
        return detail(booking, schedule);
    }

    @Override
    public BookingDetailResponse updatePassenger(long bookingId, long participantId, UpdatePassengerRequest request) {
        long customerId = requireCustomer();
        OffsetDateTime now = OffsetDateTime.now(clock);
        Booking booking = bookings.findLockedByIdAndCustomerId(bookingId, customerId).orElseThrow(BookingException::notFound);
        BookingStatus status = booking.getStatus();
        if ((status != BookingStatus.PENDING_PAYMENT && status != BookingStatus.CONFIRMED)
                || !booking.getSchedule().getStartsAt().isAfter(now)) {
            throw new BookingException(BookingErrorCode.BOOKING_NOT_EDITABLE);
        }
        Participant passenger = participants.findByIdAndBookingId(participantId, bookingId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.BOOKING_NOT_FOUND, "Passenger not found"));
        mapper.updatePassenger(passenger, request);
        participants.flush();
        return detail(booking, booking.getSchedule());
    }

    @Override
    public BookingCancellationResponse cancel(long bookingId, CancelBookingRequest request) {
        long customerId = requireCustomer();
        Booking booking = bookings.findLockedByIdAndCustomerId(bookingId, customerId).orElseThrow(BookingException::notFound);
        if (booking.getStatus() == BookingStatus.CANCELLED) return cancellation(booking);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingException(BookingErrorCode.INVALID_BOOKING_TRANSITION);
        }

        TourSchedule schedule = schedules.findLockedById(booking.getScheduleId())
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
        equipmentReservations.release(bookingId);
        bookings.flush();
        return cancellation(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleAvailabilityResponse availability(long scheduleId) {
        TourSchedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND));
        boolean published = schedule.getTour().getStatus() == TourStatus.PUBLISHED;
        PolicyVersion policy = schedule.getTour().getCancellationPolicyVersion();
        if (!published && !canManageBookings()) throw new BookingException(BookingErrorCode.SCHEDULE_NOT_FOUND);
        boolean bookable = published
                && policy != null && policy.getRevocation() == null && Boolean.TRUE.equals(policy.getPolicy().getActive())
                && policy.getApprovedAt() != null && !policy.getEffectiveFrom().isAfter(OffsetDateTime.now(clock))
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
        if (version == null || version.getRevocation() != null || !Boolean.TRUE.equals(version.getPolicy().getActive())
                || version.getApprovedAt() == null || version.getEffectiveFrom().isAfter(now)
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

    private static void requireAtMostOneBookingCustomer(List<BookingPassengerRequest> passengers) {
        if (passengers.stream().filter(BookingPassengerRequest::isBookingCustomer).count() > 1) {
            throw new BookingException(BookingErrorCode.MULTIPLE_BOOKING_CUSTOMERS);
        }
    }

    private BookingDetailResponse detail(Booking booking, TourSchedule schedule) {
        long id = booking.getId();
        return mapper.toDetail(booking, schedule, participants.findAllByBookingIdOrderByIdAsc(id),
                toLong(bookingPayments.acceptedPaymentTotalVnd(id)), toLong(bookingPayments.refundedTotalVnd(id)));
    }

    private BookingCancellationResponse cancellation(Booking booking) {
        long id = booking.getId();
        return mapper.toCancellation(booking,
                toLong(bookingPayments.acceptedPaymentTotalVnd(id)), toLong(bookingPayments.refundedTotalVnd(id)));
    }

    private Page<BookingSummaryResponse> summaries(Page<Booking> page) {
        List<Long> ids = page.map(Booking::getId).getContent();
        Map<Long, BigDecimal> paid = bookingPayments.acceptedPaymentTotals(ids);
        Map<Long, BigDecimal> refunded = bookingPayments.refundedTotals(ids);
        return mapper.toSummaries(page, paid, refunded);
    }

    private List<BookingSummaryResponse> activeHoldSummaries(long customerId, OffsetDateTime now) {
        List<Booking> active = bookings.findAllByCustomerIdAndStatusAndHoldExpiresAtAfterOrderByHoldExpiresAtAsc(
                customerId, BookingStatus.PENDING_PAYMENT, now);
        if (active.isEmpty()) return List.of();
        List<Long> ids = active.stream().map(Booking::getId).toList();
        Map<Long, BigDecimal> paid = bookingPayments.acceptedPaymentTotals(ids);
        Map<Long, BigDecimal> refunded = bookingPayments.refundedTotals(ids);
        return mapper.toSummaries(active, paid, refunded);
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

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, properties.maxPageSize()),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private String newBookingCode() {
        StringBuilder code = new StringBuilder("BK");
        String alphabet = properties.codeAlphabet();
        for (int i = 0; i < properties.codeLength(); i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private static long toLong(BigDecimal value) {
        return value == null ? 0 : value.longValueExact();
    }
}
