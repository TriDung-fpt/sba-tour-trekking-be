package com.trekking.app.features.booking.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.booking.dto.response.AdminBookingDetailResponse;
import com.trekking.app.features.booking.dto.response.AdminBookingSummaryResponse;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.service.AdminBookingService;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingServiceImpl implements AdminBookingService {
    private final BookingRepository bookings;
    private final AuthorizationService authorization;

    public AdminBookingServiceImpl(BookingRepository bookings, AuthorizationService authorization) {
        this.bookings = bookings;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Page<AdminBookingSummaryResponse> list(String status, String query, int page, int size) {
        authorization.require(Permission.BOOKING_MANAGE);
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        String term = query == null || query.isBlank() ? ""
                : query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return bookings.search(parseStatus(status), term, PageRequest.of(page, size))
                .map(AdminBookingServiceImpl::summary);
    }

    @Transactional(readOnly = true)
    public AdminBookingDetailResponse get(Long id) {
        authorization.require(Permission.BOOKING_MANAGE);
        Booking booking = bookings.findById(id).orElseThrow(AdminBookingServiceImpl::notFound);
        return new AdminBookingDetailResponse(booking.getId(), booking.getBookingCode(), booking.getCustomerId(),
                booking.getScheduleId(), booking.getStatus().getValue(), booking.getParticipantCount(),
                Boolean.TRUE.equals(booking.getIncludesEquipment()), booking.getBasePriceVnd(),
                booking.getEquipmentFeeVnd(), booking.getTotalPriceVnd(), booking.getDepositPercent(),
                booking.getHoldExpiresAt(), booking.getBalanceDueAt(), booking.getCancelledAt(),
                booking.getCancellationReason(), booking.getCancelledBy(),
                bookings.acceptedPaymentTotalVnd(booking.getId()), bookings.hasReview(booking.getId()),
                booking.getCustomer().getFullName(), booking.getCustomer().getEmail(), booking.getCreatedAt());
    }

    private static AdminBookingSummaryResponse summary(Booking booking) {
        return new AdminBookingSummaryResponse(booking.getId(), booking.getBookingCode(), booking.getCustomerId(),
                booking.getScheduleId(), booking.getStatus().getValue(), booking.getParticipantCount(),
                booking.getBasePriceVnd(), booking.getEquipmentFeeVnd(), booking.getTotalPriceVnd(),
                booking.getHoldExpiresAt(), booking.getBalanceDueAt(), booking.getCancelledAt(),
                booking.getCancellationReason());
    }

    private static BookingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (BookingStatus status : BookingStatus.values()) {
            if (status.name().equalsIgnoreCase(raw) || status.getValue().equalsIgnoreCase(raw)) return status;
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid status filter");
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found");
    }
}
