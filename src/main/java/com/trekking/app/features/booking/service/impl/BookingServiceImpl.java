package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.booking.dto.request.BookingParticipantRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.response.CreatedBookingResponse;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.BookingService;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.enums.PolicyKind;
import com.trekking.app.features.company.repository.CompanyAgreementRepository;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.repository.TourPolicyVersionRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.repository.TourEquipmentPackageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

@Service
public class BookingServiceImpl implements BookingService {
    private static final int HOLD_MINUTES = 15;
    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final TourScheduleRepository schedules;
    private final TourPolicyVersionRepository policies;
    private final CompanyAgreementRepository agreements;
    private final AuditLogRepository auditLogs;
    private final ObjectMapper mapper;
    private final TourEquipmentPackageRepository equipmentPackages;
    private final EntityManager entityManager;

    public BookingServiceImpl(BookingRepository bookings, ParticipantRepository participants,
            TourScheduleRepository schedules, TourPolicyVersionRepository policies,
            CompanyAgreementRepository agreements, AuditLogRepository auditLogs, ObjectMapper mapper,
            TourEquipmentPackageRepository equipmentPackages, EntityManager entityManager) {
        this.bookings = bookings;
        this.participants = participants;
        this.schedules = schedules;
        this.policies = policies;
        this.agreements = agreements;
        this.auditLogs = auditLogs;
        this.mapper = mapper;
        this.equipmentPackages = equipmentPackages;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public CreatedBookingResponse create(long accountId, String clientKey, CreateBookingRequest request) {
        if (request.participantCount() == null || request.participantCount() < 1 || request.participantCount() > 20
                || request.participants().size() != request.participantCount()) {
            throw error(HttpStatus.BAD_REQUEST, "PARTICIPANT_COUNT", "Participant count must match the participant list (1–20)");
        }
        long linkedAccounts = request.participants().stream().filter(p -> p.accountId() != null).count();
        if (linkedAccounts > 1 || request.participants().stream().anyMatch(p -> p.accountId() != null && p.accountId() != accountId)) {
            throw error(HttpStatus.BAD_REQUEST, "PARTICIPANT_ACCOUNT", "Only the signed-in customer account may be linked to a participant");
        }
        String key = scopedKey(accountId, clientKey);
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", key).getSingleResult();
        var previous = bookings.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Booking b = previous.get();
            if (!b.getCustomerId().equals(accountId) || !matches(b, request))
                throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used for a different booking");
            return response(b, participants.findAllByBookingIdOrderByIdAsc(b.getId()));
        }

        OffsetDateTime now = OffsetDateTime.now();
        var schedule = schedules.findByIdForBooking(request.scheduleId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Departure not found"));
        previous = bookings.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Booking b = previous.get();
            if (!b.getCustomerId().equals(accountId) || !matches(b, request))
                throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used for a different booking");
            return response(b, participants.findAllByBookingIdOrderByIdAsc(b.getId()));
        }
        var tour = schedule.getTour();
        if (tour.getStatus() != TourStatus.PUBLISHED || tour.getCompany().getStatus() != CompanyStatus.APPROVED)
            throw error(HttpStatus.NOT_FOUND, "DEPARTURE_UNAVAILABLE", "Departure is not available for booking");
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED || !schedule.getStartsAt().isAfter(now))
            throw error(HttpStatus.CONFLICT, "DEPARTURE_CLOSED", "Departure is no longer open for booking");
        var packageOption = equipmentPackages.findByTourId(tour.getId());
        if (Boolean.TRUE.equals(request.includesEquipment()) && packageOption.isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "EQUIPMENT_UNAVAILABLE", "Equipment is not available for this departure");
        if ((long) schedule.getHeldSeats() + schedule.getConfirmedSeats() + request.participantCount() > schedule.getCapacity())
            throw error(HttpStatus.CONFLICT, "NO_SEATS", "Not enough seats remain for this departure");
        Long policyId = tour.getCancellationPolicyVersionId();
        var policy = policyId == null ? null : policies.findById(policyId).orElse(null);
        if (policy == null || !policies.isValidCancellationPolicy(policyId, schedule.getCompanyId(), PolicyKind.CANCELLATION, now))
            throw error(HttpStatus.CONFLICT, "POLICY_UNAVAILABLE", "Departure cancellation policy is not available");

        Booking booking = new Booking();
        booking.setBookingCode("WT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        booking.setIdempotencyKey(key);
        booking.setCustomerId(accountId);
        booking.setScheduleId(schedule.getId());
        booking.setCompanyId(schedule.getCompanyId());
        booking.setParticipantCount(request.participantCount());
        booking.setIncludesEquipment(request.includesEquipment());
        booking.setBasePriceVnd(schedule.getBasePriceVnd());
        booking.setEquipmentFeeVnd(Boolean.TRUE.equals(request.includesEquipment()) ? schedule.getEquipmentFeeVnd() : 0L);
        booking.setDepositPercent(schedule.getDepositPercent());
        var agreement = agreements.findFirstByCompanyIdOrderByAcceptedAtDesc(schedule.getCompanyId()).orElse(null);
        booking.setPlatformFeePercent(agreement == null ? new BigDecimal("10.00") : agreement.getPlatformFeePercent());
        booking.setCompanyAgreementId(agreement == null ? null : agreement.getId());
        booking.setCancellationPolicyVersionId(policyId);
        booking.setPolicySnapshot(json(Map.of("policyVersionId", policyId, "title", policy.getPolicy().getTitle(),
                "versionNumber", policy.getVersionNumber(), "content", policy.getContent(),
                "refundRules", parseJson(tour.getCancellationRefundRules()))));
        booking.setEquipmentSnapshot(json(Boolean.TRUE.equals(request.includesEquipment())
                ? Map.of("includesEquipment", true, "unitFeeVnd", schedule.getEquipmentFeeVnd(),
                    "packageTitle", packageOption.orElseThrow().getTitle(),
                    "includedItems", packageOption.orElseThrow().getIncludedItems())
                : Map.of("includesEquipment", false, "unitFeeVnd", 0L)));
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(now.plusMinutes(HOLD_MINUTES));
        booking.setBalanceDueAt(schedule.getBalanceDueAt());
        booking = bookings.saveAndFlush(booking);

        List<Participant> saved = new ArrayList<>();
        for (BookingParticipantRequest requested : request.participants()) {
            Participant p = new Participant();
            p.setBookingId(booking.getId());
            p.setAccountId(requested.accountId());
            p.setFullName(requested.fullName().trim());
            p.setEmail(blankToNull(requested.email()));
            p.setPhone(blankToNull(requested.phone()));
            saved.add(participants.save(p));
        }
        schedule.setHeldSeats(schedule.getHeldSeats() + request.participantCount());
        schedules.save(schedule);
        audit(accountId, "BOOKING_CREATED", "booking", booking.getId(), null);
        return response(booking, saved);
    }

    private boolean matches(Booking b, CreateBookingRequest r) {
        List<Participant> existing = participants.findAllByBookingIdOrderByIdAsc(b.getId());
        if (!b.getScheduleId().equals(r.scheduleId()) || !b.getParticipantCount().equals(r.participantCount())
                || !b.getIncludesEquipment().equals(r.includesEquipment()) || existing.size() != r.participants().size()) return false;
        for (int i = 0; i < existing.size(); i++) {
            Participant saved = existing.get(i);
            BookingParticipantRequest requested = r.participants().get(i);
            if (!saved.getFullName().equals(requested.fullName().trim())
                    || !java.util.Objects.equals(saved.getEmail(), blankToNull(requested.email()))
                    || !java.util.Objects.equals(saved.getPhone(), blankToNull(requested.phone()))
                    || !java.util.Objects.equals(saved.getAccountId(), requested.accountId())) return false;
        }
        return true;
    }

    private CreatedBookingResponse response(Booking b, List<Participant> people) {
        long total = b.getParticipantCount().longValue() * (b.getBasePriceVnd() + b.getEquipmentFeeVnd());
        long deposit = BigDecimal.valueOf(total).multiply(b.getDepositPercent()).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValue();
        return new CreatedBookingResponse(b.getId(), b.getBookingCode(), b.getStatus().getValue(), b.getHoldExpiresAt(),
                b.getParticipantCount(), total, deposit, b.getBalanceDueAt(), people.stream()
                .map(p -> new CreatedBookingResponse.Participant(p.getId(), p.getFullName(), p.getEmail(), p.getPhone())).toList());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Unable to serialize booking snapshot", ex); }
    }

    private Object parseJson(String value) {
        try { return mapper.readTree(value == null || value.isBlank() ? "[]" : value); }
        catch (Exception ex) { return List.of(); }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String scopedKey(long accountId, String clientKey) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((accountId + ":" + clientKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void audit(long actorId, String action, String entityType, long entityId, String reason) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId); log.setAction(action); log.setEntityType(entityType);
        log.setEntityId(entityId); log.setReason(reason); log.setDetails("{}"); log.setOccurredAt(OffsetDateTime.now());
        auditLogs.save(log);
    }

    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + ": " + message);
    }
}
