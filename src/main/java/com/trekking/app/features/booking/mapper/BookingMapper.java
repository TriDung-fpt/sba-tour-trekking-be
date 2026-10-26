package com.trekking.app.features.booking.mapper;

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
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
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

    public String toJson(Map<String, ?> value) {
        return json.writeValueAsString(value);
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
}
