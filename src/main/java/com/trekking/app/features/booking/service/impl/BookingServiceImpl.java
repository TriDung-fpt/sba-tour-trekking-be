package com.trekking.app.features.booking.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.request.RentalItemSelectionRequest;
import com.trekking.app.features.booking.dto.request.RentalSelectionRequest;
import com.trekking.app.features.booking.dto.request.TravelerRequest;
import com.trekking.app.features.booking.dto.response.BookingContactResponse;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingPaymentContext;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import com.trekking.app.features.booking.dto.response.BookingTravelerResponse;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.enums.RentalMode;
import com.trekking.app.features.booking.exception.BookingException;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.BookingService;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.policy.repository.PolicyVersionRepository;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourEquipmentItem;
import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.repository.TourEquipmentItemRepository;
import com.trekking.app.features.tour.repository.TourEquipmentPackageRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    private static final int MAX_PARTICIPANTS = 1_000_000;
    private static final int EXPIRY_BATCH_SIZE = 100;

    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final TourScheduleRepository schedules;
    private final TourRepository tours;
    private final TourEquipmentPackageRepository equipmentPackages;
    private final TourEquipmentItemRepository equipmentItems;
    private final PolicyVersionRepository policyVersions;
    private final AppAccountRepository accounts;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final EntityManager entityManager;

    public BookingServiceImpl(BookingRepository bookings, ParticipantRepository participants,
            TourScheduleRepository schedules, TourRepository tours,
            TourEquipmentPackageRepository equipmentPackages, TourEquipmentItemRepository equipmentItems,
            PolicyVersionRepository policyVersions, AppAccountRepository accounts,
            AuthorizationService authorization, Clock clock, EntityManager entityManager) {
        this.bookings = bookings;
        this.participants = participants;
        this.schedules = schedules;
        this.tours = tours;
        this.equipmentPackages = equipmentPackages;
        this.equipmentItems = equipmentItems;
        this.policyVersions = policyVersions;
        this.accounts = accounts;
        this.authorization = authorization;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public BookingDetailResponse create(CreateBookingRequest request) {
        long customerId = requireCustomer();
        int participantCount = request.travelers().size();
        if (participantCount > MAX_PARTICIPANTS) {
            throw badRequest("INVALID_PARTICIPANTS", "Too many travelers for one booking");
        }
        request.travelers().forEach(BookingServiceImpl::validateTraveler);

        OffsetDateTime now = OffsetDateTime.now(clock);
        TourSchedule schedule = schedules.findById(request.scheduleId())
                .orElseThrow(BookingServiceImpl::scheduleNotFound);
        Tour tour = tours.findById(schedule.getTourId()).orElseThrow(BookingServiceImpl::scheduleNotFound);
        if (tour.getStatus() != TourStatus.PUBLISHED || schedule.getStatus() != ScheduleStatus.SCHEDULED
                || !schedule.getStartsAt().isAfter(now)) {
            throw conflict("SCHEDULE_NOT_BOOKABLE", "This tour schedule is not open for booking");
        }
        expireScheduleHolds(schedule, now);
        int availableSeats = schedule.getCapacity() - schedule.getHeldSeats() - schedule.getConfirmedSeats();
        if (participantCount > availableSeats) {
            throw conflict("SCHEDULE_FULL", "Not enough seats are available for this booking");
        }
        if (tour.getCancellationPolicyVersionId() == null) {
            throw conflict("CANCELLATION_POLICY_UNAVAILABLE", "This tour has no active cancellation policy");
        }

        RentalPrice rental = priceRental(request.rental(), tour.getId());
        long baseTotal;
        long total;
        try {
            baseTotal = Math.multiplyExact(schedule.getBasePriceVnd(), participantCount);
            total = Math.addExact(baseTotal, rental.totalVnd());
        } catch (ArithmeticException overflow) {
            throw badRequest("PRICE_OUT_OF_RANGE", "The booking total is outside the supported range");
        }
        if (total < 0) throw badRequest("PRICE_OUT_OF_RANGE", "The booking total is outside the supported range");
        if (total == 0) throw badRequest("INVALID_BOOKING_TOTAL", "The booking total must be greater than zero");

        PolicyVersion policy = policyVersions.findById(tour.getCancellationPolicyVersionId())
                .orElseThrow(() -> conflict("CANCELLATION_POLICY_UNAVAILABLE", "This tour has no active cancellation policy"));

        Booking booking = new Booking();
        booking.setBookingCode("BK-" + UUID.randomUUID().toString().replace("-", ""));
        booking.setIdempotencyKey(UUID.randomUUID().toString());
        booking.setCustomerId(customerId);
        booking.setScheduleId(schedule.getId());
        booking.setParticipantCount(participantCount);
        booking.setIncludesEquipment(rental.selected());
        booking.setBasePriceVnd(schedule.getBasePriceVnd());
        // Preserve the legacy per-person fee column; new bookings use the group-priced column.
        booking.setEquipmentFeeVnd(0L);
        booking.setGroupRentalFeeVnd(rental.totalVnd());
        booking.setDepositPercent(schedule.getDepositPercent());
        booking.setCancellationPolicyVersionId(policy.getId());
        booking.setPolicySnapshot(policySnapshot(policy));
        booking.setEquipmentSnapshot(rental.snapshot());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(now.plusMinutes(15));
        booking.setBalanceDueAt(schedule.getBalanceDueAt());

        schedule.setHeldSeats(schedule.getHeldSeats() + participantCount);
        Booking saved = bookings.saveAndFlush(booking);
        List<Participant> rows = request.travelers().stream().map(traveler -> {
            Participant participant = new Participant();
            participant.setBookingId(saved.getId());
            participant.setFullName(traveler.fullName().trim());
            participant.setEmail(blankToNull(traveler.email()));
            participant.setPhone(blankToNull(traveler.phone()));
            return participant;
        }).toList();
        participants.saveAll(rows);

        AppAccount customer = accounts.findById(customerId).orElseThrow(BookingServiceImpl::notFound);
        return detail(saved, schedule, tour, customer, participants.findAllByBookingIdOrderByIdAsc(saved.getId()));
    }

    @Override
    public Page<BookingSummaryResponse> myBookings(int page, int size) {
        long customerId = requireCustomer();
        return bookings.findAllByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size))
                .map(this::summary);
    }

    @Override
    public BookingDetailResponse get(long bookingId) {
        long customerId = requireCustomer();
        Booking booking = bookings.findByIdAndCustomerId(bookingId, customerId).orElseThrow(BookingServiceImpl::notFound);
        TourSchedule schedule = booking.getSchedule();
        Tour tour = schedule.getTour();
        AppAccount customer = accounts.findById(customerId).orElseThrow(BookingServiceImpl::notFound);
        return detail(booking, schedule, tour, customer, participants.findAllByBookingIdOrderByIdAsc(bookingId));
    }

    @Override
    @Transactional
    public BookingDetailResponse cancel(long bookingId, CancelBookingRequest request) {
        long customerId = requireCustomer();
        Booking booking = bookings.findOwnedForUpdate(bookingId, customerId).orElseThrow(BookingServiceImpl::notFound);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw conflict("BOOKING_NOT_CANCELLABLE", "Only a booking awaiting payment can be cancelled");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        TourSchedule schedule = schedules.findById(booking.getScheduleId()).orElseThrow(BookingServiceImpl::scheduleNotFound);
        if (!booking.getHoldExpiresAt().isAfter(now)) {
            expire(booking, schedule);
        } else {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(now);
            booking.setCancellationReason(request.reason().trim());
            booking.setCancelledBy(customerId);
            releaseSeats(schedule, booking.getParticipantCount());
        }

        Tour tour = tours.findById(schedule.getTourId()).orElseThrow(BookingServiceImpl::scheduleNotFound);
        AppAccount customer = accounts.findById(customerId).orElseThrow(BookingServiceImpl::notFound);
        return detail(booking, schedule, tour, customer, participants.findAllByBookingIdOrderByIdAsc(bookingId));
    }

    @Override
    @Transactional
    public int expirePendingBookings() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Booking> expired = bookings.findExpiredPendingPayments(now, PageRequest.of(0, EXPIRY_BATCH_SIZE));
        for (Booking booking : expired) {
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || booking.getHoldExpiresAt().isAfter(now)) continue;
            TourSchedule schedule = schedules.findById(booking.getScheduleId()).orElseThrow(BookingServiceImpl::scheduleNotFound);
            expire(booking, schedule);
        }
        return expired.size();
    }

    private void expireScheduleHolds(TourSchedule schedule, OffsetDateTime now) {
        while (true) {
            List<Booking> expired = bookings.findExpiredPendingPaymentsForSchedule(schedule.getId(), now,
                    PageRequest.of(0, EXPIRY_BATCH_SIZE));
            for (Booking booking : expired) expire(booking, schedule);
            if (expired.size() < EXPIRY_BATCH_SIZE) return;
        }
    }

    @Override
    public BookingPaymentContext getPaymentContext(long bookingId, long customerId) {
        Booking booking = bookings.findByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(BookingServiceImpl::notFound);
        return new BookingPaymentContext(booking.getId(), booking.getCustomerId(), booking.getStatus(),
                booking.getTotalPriceVnd(), booking.getDepositPercent(), depositDueVnd(booking),
                booking.getHoldExpiresAt(), booking.getBalanceDueAt());
    }

    @Override
    @Transactional
    public BookingStatus confirmDeposit(long bookingId, long verifiedPaidTotalVnd) {
        if (verifiedPaidTotalVnd < 0) throw badRequest("INVALID_PAYMENT_TOTAL", "Verified payment total must not be negative");
        Booking booking = bookings.findForUpdate(bookingId).orElseThrow(BookingServiceImpl::notFound);
        // Payment inserts can lock the snapshot through a database trigger in the caller's transaction.
        entityManager.flush();
        entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) return booking.getStatus();

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!booking.getHoldExpiresAt().isAfter(now)) {
            TourSchedule schedule = schedules.findById(booking.getScheduleId()).orElseThrow(BookingServiceImpl::scheduleNotFound);
            expire(booking, schedule);
            return booking.getStatus();
        }
        if (verifiedPaidTotalVnd < depositDueVnd(booking)) return booking.getStatus();

        TourSchedule schedule = schedules.findById(booking.getScheduleId()).orElseThrow(BookingServiceImpl::scheduleNotFound);
        releaseSeats(schedule, booking.getParticipantCount());
        schedule.setConfirmedSeats(schedule.getConfirmedSeats() + booking.getParticipantCount());
        booking.setStatus(BookingStatus.CONFIRMED);
        if (booking.getPriceLockedAt() == null) booking.setPriceLockedAt(now);
        return booking.getStatus();
    }

    private static long depositDueVnd(Booking booking) {
        return Math.max(1L, BigDecimal.valueOf(booking.getTotalPriceVnd())
                .multiply(booking.getDepositPercent()).movePointLeft(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private long requireCustomer() {
        long accountId = authorization.currentAccountId();
        if (!authorization.currentRoles().contains(RoleType.CUSTOMER)) {
            throw new BookingException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Customer role is required");
        }
        return accountId;
    }

    private RentalPrice priceRental(RentalSelectionRequest selection, long tourId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", selection.mode().name());
        if (selection.mode() == RentalMode.NONE) {
            if (selection.packageId() != null || selection.quantity() != null || hasItems(selection)) {
                throw badRequest("INVALID_RENTAL_SELECTION", "NONE rental mode cannot contain rental details");
            }
            return new RentalPrice(0L, false, snapshot);
        }

        TourEquipmentPackage tourPackage = equipmentPackages.findByTourId(tourId).orElse(null);
        if (tourPackage == null) throw invalidRental();

        if (selection.mode() == RentalMode.PACKAGE) {
            if (selection.packageId() == null || selection.quantity() == null || selection.quantity() <= 0 || hasItems(selection)
                    || !selection.packageId().equals(tourPackage.getId()) || tourPackage.getRentalPriceVnd() == null) {
                throw invalidRental();
            }
            long amount = multiplyPrice(tourPackage.getRentalPriceVnd(), selection.quantity());
            snapshot.put("packageId", tourPackage.getId());
            snapshot.put("title", tourPackage.getTitle());
            snapshot.put("quantity", selection.quantity());
            snapshot.put("unitPriceVnd", tourPackage.getRentalPriceVnd());
            snapshot.put("totalVnd", amount);
            return new RentalPrice(amount, true, snapshot);
        }

        if (selection.packageId() != null || selection.quantity() != null || selection.items() == null
                || selection.items().isEmpty()) throw invalidRental();
        Map<Long, TourEquipmentItem> tourItems = equipmentItems.findAllByPackageIdOrderByIdAsc(tourPackage.getId()).stream()
                .collect(Collectors.toMap(TourEquipmentItem::getId, item -> item));
        if (selection.items().stream().map(RentalItemSelectionRequest::itemId).distinct().count() != selection.items().size()) {
            throw badRequest("INVALID_RENTAL_SELECTION", "An item can only be selected once");
        }
        List<Map<String, Object>> snapshotItems = new ArrayList<>();
        long total = 0L;
        for (RentalItemSelectionRequest selected : selection.items()) {
            TourEquipmentItem item = tourItems.get(selected.itemId());
            if (item == null || item.getIndividualRentalPriceVnd() == null) throw invalidRental();
            long lineTotal = multiplyPrice(item.getIndividualRentalPriceVnd(), selected.quantity());
            try {
                total = Math.addExact(total, lineTotal);
            } catch (ArithmeticException overflow) {
                throw badRequest("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemId", item.getId());
            line.put("equipmentId", item.getEquipmentId());
            line.put("name", item.getEquipment().getName());
            line.put("quantity", selected.quantity());
            line.put("unitPriceVnd", item.getIndividualRentalPriceVnd());
            line.put("totalVnd", lineTotal);
            snapshotItems.add(line);
        }
        if (total > 1_000_000_000_000L) {
            throw badRequest("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
        }
        snapshot.put("items", snapshotItems);
        snapshot.put("totalVnd", total);
        return new RentalPrice(total, true, snapshot);
    }

    private static long multiplyPrice(Long unitPrice, int quantity) {
        try {
            long amount = Math.multiplyExact(unitPrice, quantity);
            if (unitPrice < 0 || amount < 0 || amount > 1_000_000_000_000L) throw invalidRental();
            return amount;
        } catch (ArithmeticException overflow) {
            throw badRequest("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
        }
    }

    private Map<String, Object> policySnapshot(PolicyVersion policy) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", policy.getId());
        snapshot.put("policyId", policy.getPolicyId());
        snapshot.put("versionNumber", policy.getVersionNumber());
        snapshot.put("content", policy.getContent());
        snapshot.put("effectiveFrom", policy.getEffectiveFrom());
        snapshot.put("approvedBy", policy.getApprovedBy());
        snapshot.put("approvedAt", policy.getApprovedAt());
        return snapshot;
    }

    private void expire(Booking booking, TourSchedule schedule) {
        booking.setStatus(BookingStatus.EXPIRED);
        releaseSeats(schedule, booking.getParticipantCount());
    }

    private static void releaseSeats(TourSchedule schedule, int count) {
        if (schedule.getHeldSeats() < count) throw new IllegalStateException("Booking seat hold counter is inconsistent");
        schedule.setHeldSeats(schedule.getHeldSeats() - count);
    }

    private BookingDetailResponse detail(Booking booking, TourSchedule schedule, Tour tour, AppAccount customer,
            List<Participant> travelerRows) {
        long rentalFee = booking.getGroupRentalFeeVnd() == null
                ? booking.getEquipmentFeeVnd() * booking.getParticipantCount()
                : booking.getGroupRentalFeeVnd();
        return new BookingDetailResponse(booking.getId(), booking.getBookingCode(), booking.getStatus(),
                new BookingContactResponse(customer.getFullName(), customer.getEmail(), customer.getPhone()),
                tour.getId(), tour.getTitle(), schedule.getId(), schedule.getStartsAt(), schedule.getEndsAt(),
                booking.getParticipantCount(), booking.getBasePriceVnd(), rentalFee, booking.getTotalPriceVnd(),
                booking.getDepositPercent(), depositDueVnd(booking), booking.getBalanceDueAt(), booking.getHoldExpiresAt(),
                booking.getEquipmentSnapshot(), travelerRows.stream().map(this::traveler).toList(),
                booking.getCreatedAt(), booking.getCancelledAt(), booking.getCancellationReason());
    }

    private BookingSummaryResponse summary(Booking booking) {
        TourSchedule schedule = booking.getSchedule();
        Tour tour = schedule.getTour();
        return new BookingSummaryResponse(booking.getId(), booking.getBookingCode(), booking.getStatus(),
                tour.getId(), tour.getTitle(), schedule.getId(), schedule.getStartsAt(), schedule.getEndsAt(),
                booking.getParticipantCount(), booking.getTotalPriceVnd(), booking.getHoldExpiresAt(), booking.getCreatedAt());
    }

    private BookingTravelerResponse traveler(Participant participant) {
        return new BookingTravelerResponse(participant.getId(), participant.getFullName(), participant.getEmail(), participant.getPhone());
    }

    private static boolean hasItems(RentalSelectionRequest selection) {
        return selection.items() != null && !selection.items().isEmpty();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static void validateTraveler(TravelerRequest traveler) {
        String phone = traveler.phone();
        if (phone == null || phone.isBlank()) return;
        long digits = phone.chars().filter(Character::isDigit).count();
        if (digits < 7 || digits > 15) {
            throw badRequest("INVALID_TRAVELER", "Traveler phone must contain between 7 and 15 digits");
        }
    }

    private static BookingException invalidRental() {
        return badRequest("INVALID_RENTAL_SELECTION", "Rental selection is not available for this tour");
    }

    private static BookingException scheduleNotFound() {
        return new BookingException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour schedule was not found");
    }

    private static BookingException notFound() {
        return new BookingException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Booking was not found");
    }

    private static BookingException badRequest(String code, String message) {
        return new BookingException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static BookingException conflict(String code, String message) {
        return new BookingException(HttpStatus.CONFLICT, code, message);
    }

    private record RentalPrice(long totalVnd, boolean selected, Map<String, Object> snapshot) {}
}
