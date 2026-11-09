package com.trekking.app.features.booking.mapper;

import com.trekking.app.features.booking.dto.request.BookingPassengerRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.request.UpdatePassengerRequest;
import com.trekking.app.features.booking.dto.response.BookingCancellationResponse;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingPassengerResponse;
import com.trekking.app.features.booking.dto.response.BookingPriceResponse;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import com.trekking.app.features.booking.dto.response.ScheduleAvailabilityResponse;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingEquipmentChoice;
import com.trekking.app.features.booking.enums.BookingPaymentState;
import com.trekking.app.features.equipment.dto.request.EquipmentHoldRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentHoldResult;
import com.trekking.app.features.policy.entity.Policy;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class BookingMapper {
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final ObjectMapper json;

    public BookingMapper(ObjectMapper json) {
        this.json = json;
    }

    public Booking toBooking(CreateBookingRequest request, long customerId, TourSchedule schedule,
                             PolicyVersion policy, String bookingCode, OffsetDateTime holdExpiresAt,
                             String requestHash) {
        Booking booking = new Booking();
        booking.setBookingCode(bookingCode);
        booking.setIdempotencyKey(request.idempotencyKey());
        booking.setCustomerId(customerId);
        booking.setScheduleId(schedule.getId());
        booking.setParticipantCount(request.passengers().size());
        booking.setIncludesEquipment(false);
        booking.setBasePriceVnd(schedule.getBasePriceVnd());
        booking.setEquipmentFeeVnd(0L);
        booking.setDepositPercent(schedule.getDepositPercent());
        booking.setCancellationPolicyVersionId(policy.getId());
        booking.setPolicySnapshot(toPolicySnapshot(policy));
        booking.setEquipmentSnapshot(toEquipmentSnapshot(null, request.equipmentChoice(), requestHash));
        booking.setHoldExpiresAt(holdExpiresAt);
        booking.setBalanceDueAt(schedule.getBalanceDueAt());
        return booking;
    }

    public List<Participant> toParticipants(long bookingId, long customerId, List<BookingPassengerRequest> requests) {
        return requests.stream().map(request -> {
            Participant participant = new Participant();
            participant.setBookingId(bookingId);
            participant.setAccountId(request.isBookingCustomer() ? customerId : null);
            participant.setFullName(request.fullName().trim());
            participant.setPhone(blankToNull(request.phone()));
            participant.setEmail(blankToNull(request.email()));
            return participant;
        }).toList();
    }

    public void updatePassenger(Participant participant, UpdatePassengerRequest request) {
        participant.setFullName(request.fullName().trim());
        participant.setPhone(blankToNull(request.phone()));
        participant.setEmail(blankToNull(request.email()));
    }

    public EquipmentHoldRequest toEquipmentHoldRequest(Booking booking, CreateBookingRequest request) {
        return new EquipmentHoldRequest(booking.getId(), booking.getScheduleId(), booking.getHoldExpiresAt(),
                booking.getParticipantCount(),
                request.packageQuantities() == null ? Map.of() : request.packageQuantities(),
                request.itemQuantities() == null ? Map.of() : request.itemQuantities());
    }

    public void updateEquipment(Booking booking, EquipmentHoldResult hold, BookingEquipmentChoice choice,
                                String requestHash) {
        booking.setEquipmentFeeVnd(hold.rentalFeeVnd());
        booking.setIncludesEquipment(hold.includesEquipment());
        booking.setEquipmentSnapshot(toEquipmentSnapshot(hold.equipmentSnapshot(), choice, requestHash));
    }

    public String toRequestHash(CreateBookingRequest request) {
        String canonical = request.scheduleId() + "|" + request.equipmentChoice() + "|"
                + new TreeMap<>(request.packageQuantities() == null ? Map.of() : request.packageQuantities()) + "|"
                + new TreeMap<>(request.itemQuantities() == null ? Map.of() : request.itemQuantities()) + "|"
                + request.passengers().stream()
                        .map(p -> p.fullName() + "~" + p.phone() + "~" + p.email() + "~" + p.isBookingCustomer()).toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint booking request", exception);
        }
    }

    public String readRequestHash(Booking booking) {
        var savedHash = json.readTree(booking.getEquipmentSnapshot()).get("requestHash");
        return savedHash == null ? null : savedHash.asString();
    }

    public Page<BookingSummaryResponse> toSummaries(Page<Booking> bookings, Map<Long, BigDecimal> paid,
                                                   Map<Long, BigDecimal> refunded) {
        return bookings.map(booking -> toSummary(booking, toLong(paid.get(booking.getId())),
                toLong(refunded.get(booking.getId()))));
    }

    public List<BookingSummaryResponse> toSummaries(List<Booking> bookings, Map<Long, BigDecimal> paid,
                                                   Map<Long, BigDecimal> refunded) {
        return bookings.stream().map(booking -> toSummary(booking, toLong(paid.get(booking.getId())),
                toLong(refunded.get(booking.getId())))).toList();
    }

    public BookingDetailResponse toDetail(Booking booking, TourSchedule schedule, List<Participant> participants,
                                          long paidVnd, long refundedVnd) {
        Tour tour = schedule.getTour();
        return new BookingDetailResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCustomerId(),
                schedule.getId(),
                tour.getId(),
                tour.getTitle(),
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                schedule.getMeetingPoint(),
                equipmentChoice(booking),
                readJson(booking.getEquipmentSnapshot()),
                readJson(booking.getPolicySnapshot()),
                toPrice(booking, paidVnd, refundedVnd),
                participants.stream().map(p -> toPassenger(p, booking.getCustomerId())).toList(),
                booking.getHoldExpiresAt(),
                booking.getBalanceDueAt(),
                booking.getPriceLockedAt(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getCancelledBy(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    public BookingSummaryResponse toSummary(Booking booking, long paidVnd, long refundedVnd) {
        TourSchedule schedule = booking.getSchedule();
        return new BookingSummaryResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCustomerId(),
                schedule.getId(),
                schedule.getTourId(),
                schedule.getTour().getTitle(),
                schedule.getStartsAt(),
                booking.getParticipantCount(),
                booking.getTotalPriceVnd(),
                paidVnd,
                paymentState(booking.getTotalPriceVnd(), paidVnd, refundedVnd),
                booking.getHoldExpiresAt(),
                booking.getCreatedAt());
    }

    public BookingCancellationResponse toCancellation(Booking booking, long paidVnd, long refundedVnd) {
        return new BookingCancellationResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getCancelledBy(),
                paidVnd,
                refundedVnd,
                paidVnd > refundedVnd);
    }

    public ScheduleAvailabilityResponse toAvailability(TourSchedule schedule, boolean bookable) {
        return new ScheduleAvailabilityResponse(
                schedule.getId(),
                schedule.getTourId(),
                schedule.getStartsAt(),
                schedule.getCapacity(),
                schedule.getHeldSeats(),
                schedule.getConfirmedSeats(),
                availableSeats(schedule),
                bookable);
    }

    public static int availableSeats(TourSchedule schedule) {
        return Math.max(schedule.getCapacity() - schedule.getHeldSeats() - schedule.getConfirmedSeats(), 0);
    }

    private BookingPriceResponse toPrice(Booking booking, long paidVnd, long refundedVnd) {
        long total = booking.getTotalPriceVnd();
        // Deposit is rounded up to the next whole VND so a partial deposit never confirms a booking.
        long deposit = BigDecimal.valueOf(total).multiply(booking.getDepositPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).longValueExact();
        return new BookingPriceResponse(
                booking.getParticipantCount(),
                booking.getBasePriceVnd(),
                booking.getEquipmentFeeVnd(),
                total,
                booking.getDepositPercent(),
                deposit,
                paidVnd,
                refundedVnd,
                Math.max(paidVnd - refundedVnd, 0),
                Math.max(total - paidVnd, 0),
                paymentState(total, paidVnd, refundedVnd));
    }

    private static BookingPassengerResponse toPassenger(Participant participant, Long customerId) {
        return new BookingPassengerResponse(
                participant.getId(),
                participant.getFullName(),
                participant.getPhone(),
                participant.getEmail(),
                customerId.equals(participant.getAccountId()));
    }

    private static BookingEquipmentChoice equipmentChoice(Booking booking) {
        return Boolean.TRUE.equals(booking.getIncludesEquipment())
                ? BookingEquipmentChoice.TOUR_PACKAGE : BookingEquipmentChoice.SELF_PROVIDED;
    }

    private static BookingPaymentState paymentState(long totalVnd, long paidVnd, long refundedVnd) {
        if (refundedVnd > 0) {
            return refundedVnd >= paidVnd ? BookingPaymentState.FULLY_REFUNDED : BookingPaymentState.PARTIALLY_REFUNDED;
        }
        if (paidVnd <= 0) return BookingPaymentState.UNPAID;
        return paidVnd >= totalVnd ? BookingPaymentState.FULLY_PAID : BookingPaymentState.DEPOSIT_PAID;
    }

    private Map<String, Object> readJson(String value) {
        return value == null || value.isBlank() ? Map.of() : json.readValue(value, JSON_OBJECT);
    }

    private String toPolicySnapshot(PolicyVersion version) {
        Policy policy = version.getPolicy();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyId", policy.getId());
        snapshot.put("policyCode", policy.getCode());
        snapshot.put("title", policy.getTitle());
        snapshot.put("versionId", version.getId());
        snapshot.put("versionNumber", version.getVersionNumber());
        snapshot.put("content", version.getContent());
        snapshot.put("effectiveFrom", version.getEffectiveFrom().toString());
        return json.writeValueAsString(snapshot);
    }

    private String toEquipmentSnapshot(String value, BookingEquipmentChoice choice, String requestHash) {
        Map<String, Object> snapshot = new LinkedHashMap<>(readJson(value));
        snapshot.put("choice", choice.name());
        snapshot.put("requestHash", requestHash);
        return json.writeValueAsString(snapshot);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long toLong(BigDecimal value) {
        return value == null ? 0 : value.longValueExact();
    }
}
