package com.trekking.app.features.booking.enums;

/** Derived only from accepted receipts and actual refund transactions. */
public enum BookingPaymentState {
    UNPAID,
    DEPOSIT_PAID,
    FULLY_PAID,
    PARTIALLY_REFUNDED,
    FULLY_REFUNDED
}
