package com.trekking.app.features.booking.dto.request;

import com.trekking.app.features.payment.dto.request.RefundAccountRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code refundAccount} is required when the booking has a refundable payment under the cancellation policy. */
public record CancelBookingRequest(@NotBlank @Size(max = 1000) String reason,
                                   @Valid RefundAccountRequest refundAccount) {
    public CancelBookingRequest(String reason) {
        this(reason, null);
    }
}
