package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.booking.dto.response.PartnerBookingDetail;
import com.trekking.app.features.booking.dto.response.PartnerBookingPage;
import com.trekking.app.features.booking.dto.request.CheckInRequest;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.response.BookingActionResponse;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.PartnerBookingService;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.repository.CompanyMemberRepository;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import com.trekking.app.features.tour.repository.TourCompanyMemberRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.repository.TourGuideRepository;
import com.trekking.app.features.tour.repository.TourScheduleGuideRepository;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PartnerBookingServiceImpl implements PartnerBookingService {
    private final BookingRepository bookings;
    private final TourCompanyMemberRepository members;
    private final ParticipantRepository participants;
    private final CustomerPaymentRepository payments;
    private final CustomerRefundRepository refunds;
    private final RefundRequestRepository refundRequests;
    private final TourScheduleRepository schedules;
    private final CompanyMemberRepository companyMembers;
    private final AuditLogRepository auditLogs;
    private final TourGuideRepository guides;
    private final TourScheduleGuideRepository scheduleGuides;

    public PartnerBookingServiceImpl(BookingRepository bookings, TourCompanyMemberRepository members,
            ParticipantRepository participants, CustomerPaymentRepository payments,
            CustomerRefundRepository refunds, RefundRequestRepository refundRequests,
            TourScheduleRepository schedules, CompanyMemberRepository companyMembers,
            AuditLogRepository auditLogs, TourGuideRepository guides,
            TourScheduleGuideRepository scheduleGuides) {
        this.bookings = bookings; this.members = members; this.participants = participants;
        this.payments = payments; this.refunds = refunds; this.refundRequests = refundRequests;
        this.schedules = schedules; this.companyMembers = companyMembers; this.auditLogs = auditLogs;
        this.guides = guides; this.scheduleGuides = scheduleGuides;
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerBookingPage list(long accountId, String rawStatus, OffsetDateTime fromDate,
                                   OffsetDateTime toDate, String rawQuery, int page, int size) {
        validatePaging(page, size);
        if (fromDate != null && toDate != null && !fromDate.isBefore(toDate))
            throw error(HttpStatus.BAD_REQUEST, "DATE_RANGE", "from must be earlier than to");
        BookingStatus status = parseStatus(rawStatus);
        boolean expiredOnly = status == BookingStatus.EXPIRED;
        BookingStatus queryStatus = expiredOnly ? BookingStatus.PENDING_PAYMENT : status;
        var companyIds = companyIds(accountId);
        if (companyIds.isEmpty()) return new PartnerBookingPage(List.of(), page, size, 0, 0);
        OffsetDateTime now = OffsetDateTime.now();
        var result = bookings.findPartnerBookings(companyIds, queryStatus, fromDate, toDate,
                rawQuery == null || rawQuery.isBlank() ? null : rawQuery.trim().toLowerCase(Locale.ROOT),
                expiredOnly, BookingStatus.PENDING_PAYMENT, now,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        var items = result.getContent().stream().map(b -> {
            long total = b.getParticipantCount().longValue() * (b.getBasePriceVnd() + b.getEquipmentFeeVnd());
            return new PartnerBookingPage.Item(b.getId(), b.getBookingCode(), displayStatus(b).getValue(), b.getCreatedAt(),
                    new PartnerBookingPage.Departure(b.getScheduleId(), b.getSchedule().getTourId(),
                            b.getSchedule().getTour().getTitle(), b.getSchedule().getStartsAt(), b.getSchedule().getEndsAt()),
                    b.getParticipantCount(), total, payments.acceptedTotal(b.getId()), payments.pendingReviewTotal(b.getId()),
                    refunds.refundedTotal(b.getId()));
        }).toList();
        return new PartnerBookingPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerBookingDetail detail(long accountId, long bookingId) {
        var ids = companyIds(accountId);
        Booking b = ids.isEmpty() ? null : bookings.findByIdAndCompanyIdIn(bookingId, ids).orElse(null);
        if (b == null) throw error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
        var schedule = b.getSchedule();
        boolean canSeeParticipants = b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED
                || b.getStatus() == BookingStatus.NO_SHOW;
        var people = canSeeParticipants ? participants.findAllByBookingIdOrderByIdAsc(bookingId).stream()
                .map(p -> new PartnerBookingDetail.Participant(p.getId(), p.getFullName(), p.getEmail(), p.getPhone(),
                        p.getCheckedInAt(), p.getCheckInNote())).toList() : List.<PartnerBookingDetail.Participant>of();
        long total = b.getParticipantCount().longValue() * (b.getBasePriceVnd() + b.getEquipmentFeeVnd());
        var customer = b.getCustomer();
        var customerResponse = canSeeParticipants
                ? new PartnerBookingDetail.Customer(customer.getFullName(), customer.getEmail(), customer.getPhone())
                : new PartnerBookingDetail.Customer(null, null, null);
        return new PartnerBookingDetail(b.getId(), b.getBookingCode(), displayStatus(b).getValue(), b.getCreatedAt(),
                new PartnerBookingDetail.Departure(schedule.getId(), schedule.getTourId(), schedule.getTour().getTitle(),
                        schedule.getStartsAt(), schedule.getEndsAt(), schedule.getMeetingPoint(), schedule.getContactPhone()),
                customerResponse, people,
                new PartnerBookingDetail.Money(total, payments.acceptedTotal(bookingId), payments.pendingReviewTotal(bookingId),
                        refunds.refundedTotal(bookingId),
                        b.getDepositPercent(), b.getBalanceDueAt()),
                refundRequests.findAllByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                        .map(r -> new PartnerBookingDetail.RefundRequest(r.getId(), r.getRequestedAmountVnd(),
                                r.getApprovedAmountVnd(), r.getStatus().getValue(), r.getReason(), r.getDecisionNote(), r.getCreatedAt())).toList());
    }

    @Override
    @Transactional
    public BookingActionResponse checkIn(long accountId, long bookingId, long participantId, CheckInRequest request) {
        Booking b = lockBookingOrAssignedGuide(accountId, bookingId);
        if (b.getStatus() != BookingStatus.CONFIRMED)
            throw error(HttpStatus.CONFLICT, "BOOKING_NOT_CONFIRMED", "Only confirmed bookings can be checked in");
        var schedule = b.getSchedule();
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED && schedule.getStatus() != ScheduleStatus.ONGOING)
            throw error(HttpStatus.CONFLICT, "DEPARTURE_CLOSED", "Check-in is not available for this departure");
        var person = participants.lockByBooking(participantId, bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND", "Participant not found"));
        if (person.getCheckedInAt() != null)
            return new BookingActionResponse("Participant already checked in", "checked_in");
        OffsetDateTime now = OffsetDateTime.now();
        person.setCheckedInAt(now); person.setCheckedInBy(accountId);
        person.setCheckInNote(request == null || request.note() == null ? null : request.note().trim());
        participants.save(person);
        audit(accountId, "PARTICIPANT_CHECKED_IN", "participant", participantId, person.getCheckInNote());
        return new BookingActionResponse("Participant checked in", "checked_in");
    }

    @Override
    @Transactional
    public BookingActionResponse undoCheckIn(long accountId, long bookingId, long participantId, String reason) {
        Booking b = lockBooking(accountId, bookingId);
        var member = companyMembers.findByCompanyIdAndAccountIdAndActiveTrue(b.getCompanyId(), accountId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        if (member.getMemberRole() != CompanyMemberRole.OWNER && member.getMemberRole() != CompanyMemberRole.MANAGER)
            throw error(HttpStatus.FORBIDDEN, "BOOKING_ACCESS", "Manager access is required");
        if (!b.getSchedule().getStartsAt().isAfter(OffsetDateTime.now()))
            throw error(HttpStatus.CONFLICT, "CHECK_IN_LOCKED", "Check-in can only be undone before departure starts");
        var person = participants.lockByBooking(participantId, bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND", "Participant not found"));
        if (person.getCheckedInAt() == null) return new BookingActionResponse("Participant is not checked in", "not_checked_in");
        person.setCheckedInAt(null); person.setCheckedInBy(null); person.setCheckInNote(null);
        participants.save(person);
        audit(accountId, "PARTICIPANT_CHECK_IN_UNDONE", "participant", participantId, reason.trim());
        return new BookingActionResponse("Check-in undone", "not_checked_in");
    }

    @Override
    @Transactional
    public BookingActionResponse cancel(long accountId, long bookingId, CancelBookingRequest request) {
        Booking b = lockBooking(accountId, bookingId);
        var member = companyMembers.findByCompanyIdAndAccountIdAndActiveTrue(b.getCompanyId(), accountId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        if (member.getMemberRole() != CompanyMemberRole.OWNER && member.getMemberRole() != CompanyMemberRole.MANAGER)
            throw error(HttpStatus.FORBIDDEN, "BOOKING_ACCESS", "Manager access is required");
        OffsetDateTime now = OffsetDateTime.now();
        if (!b.getSchedule().getStartsAt().isAfter(now))
            throw error(HttpStatus.CONFLICT, "DEPARTURE_STARTED", "A departure that has started cannot be cancelled here");
        if (b.getStatus() == BookingStatus.CANCELLED)
            return new BookingActionResponse("Booking already cancelled", "cancelled");
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT && b.getStatus() != BookingStatus.CONFIRMED)
            throw error(HttpStatus.CONFLICT, "BOOKING_STATE", "Booking cannot be cancelled in its current state");
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT && !b.getHoldExpiresAt().isAfter(now))
            throw error(HttpStatus.CONFLICT, "HOLD_EXPIRED", "The payment hold has expired");
        var schedule = schedules.findByIdForBooking(b.getScheduleId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Departure not found"));
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT)
            schedule.setHeldSeats(Math.max(0, schedule.getHeldSeats() - b.getParticipantCount()));
        else schedule.setConfirmedSeats(Math.max(0, schedule.getConfirmedSeats() - b.getParticipantCount()));
        b.setStatus(BookingStatus.CANCELLED); b.setCancelledAt(now); b.setCancelledBy(accountId);
        b.setCancellationReason(request.reason().trim());
        schedules.save(schedule); bookings.save(b);
        audit(accountId, "BOOKING_CANCELLED_BY_PARTNER", "booking", bookingId, request.reason().trim());
        return new BookingActionResponse("Booking cancelled. Any refund is handled separately.", "cancelled");
    }

    private Booking lockBooking(long accountId, long bookingId) {
        var ids = companyIds(accountId);
        if (ids.isEmpty()) throw error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
        return bookings.lockPartnerBooking(bookingId, ids)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
    }

    private Booking lockBookingOrAssignedGuide(long accountId, long bookingId) {
        var ids = companyIds(accountId);
        if (!ids.isEmpty()) {
            var owned = bookings.lockPartnerBooking(bookingId, ids);
            if (owned.isPresent()) return owned.get();
        }
        Booking candidate = bookings.lockById(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        var schedule = candidate.getSchedule();
        var guideId = guides.findActiveGuideId(accountId, candidate.getCompanyId(), AccountStatus.ACTIVE);
        if (schedule.getTour().getCompany().getStatus() != CompanyStatus.APPROVED || guideId.isEmpty()
                || !scheduleGuides.existsByScheduleIdAndGuideIdAndCompanyId(schedule.getId(), guideId.get(), candidate.getCompanyId()))
            throw error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
        return candidate;
    }

    private void audit(long actorId, String action, String entityType, long entityId, String reason) {
        AuditLog event = new AuditLog(); event.setActorId(actorId); event.setAction(action);
        event.setEntityType(entityType); event.setEntityId(entityId); event.setReason(reason);
        event.setDetails("{}"); event.setOccurredAt(OffsetDateTime.now()); auditLogs.save(event);
    }

    private List<Long> companyIds(long accountId) {
        return members.findAllByAccountIdAndActiveTrueAndMemberRoleInAndCompany_StatusOrderByCompanyIdAsc(
                accountId, List.of(CompanyMemberRole.OWNER, CompanyMemberRole.MANAGER), CompanyStatus.APPROVED)
                .stream().map(m -> m.getCompanyId()).toList();
    }
    private BookingStatus displayStatus(Booking b) {
        return b.getStatus() == BookingStatus.PENDING_PAYMENT && !b.getHoldExpiresAt().isAfter(OffsetDateTime.now())
                ? BookingStatus.EXPIRED : b.getStatus();
    }
    private BookingStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return BookingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST, "BOOKING_STATUS", "Invalid booking status"); }
    }
    private void validatePaging(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "PAGINATION", "Invalid page or size");
    }
    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + ": " + message);
    }
}
