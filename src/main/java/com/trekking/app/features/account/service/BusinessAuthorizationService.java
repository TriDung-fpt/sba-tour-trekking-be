package com.trekking.app.features.account.service;

/**
 * Shared customer authorization for consuming payment and review services.
 * Every call requires an existing write transaction and locks the owned booking.
 * The caller must perform its mutation in that same transaction. All changes to
 * payments, completion, check-in and reviews must follow the same booking-lock
 * order, then recheck their own monetary/idempotency or uniqueness invariants.
 * Neither method authorizes payment webhooks or accepts actor/state from clients.
 */
public interface BusinessAuthorizationService {
    void requirePayment(long bookingId);
    void requireReview(long bookingId);
}
