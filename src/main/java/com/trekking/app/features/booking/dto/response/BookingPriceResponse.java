package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingPaymentState;
import java.math.BigDecimal;

public record BookingPriceResponse(
        int participantCount,
        long basePriceVnd,
        long equipmentFeeVnd,
        long totalPriceVnd,
        BigDecimal depositPercent,
        long depositAmountVnd,
        long paidAmountVnd,
        long refundedAmountVnd,
        long netCollectedAmountVnd,
        long remainingAmountVnd,
        BookingPaymentState paymentState
) {}
