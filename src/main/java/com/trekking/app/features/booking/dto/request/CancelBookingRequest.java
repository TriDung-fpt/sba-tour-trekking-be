package com.trekking.app.features.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.trekking.app.features.payment.dto.request.RefundSubmissionRequest;

public record CancelBookingRequest(@NotBlank @Size(max = 1000) String reason,
                                   @jakarta.validation.Valid RefundSubmissionRequest refundRequest) {
    public CancelBookingRequest(String reason) { this(reason, null); }
}
