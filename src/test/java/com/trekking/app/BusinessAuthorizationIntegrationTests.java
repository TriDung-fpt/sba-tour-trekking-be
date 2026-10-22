package com.trekking.app;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.SessionJwtAuthenticationConverter;
import com.trekking.app.features.account.service.BusinessAuthorizationService;
import com.trekking.app.features.auth.model.IssuedTokens;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BusinessAuthorizationIntegrationTests.BusinessProbeController.class)
class BusinessAuthorizationIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String PROBE = "/api/v1/test-business-authorization/";
    @Autowired BusinessAuthorizationService authorization;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired BusinessProbeController probe;
    @Autowired EntityManager entityManager;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired SessionJwtAuthenticationConverter jwtAuthentication;
    private long owner;
    private long admin;
    private long scheduleId;
    private long bookingId;
    private IssuedTokens token;

    @BeforeEach
    void fixture() {
        owner = account("owner@example.invalid", "customer");
        admin = account("admin@example.invalid", "admin");
        scheduleId = internalSchedule();
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('booking-scope', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Test cancellation terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('SCOPE-BOOKING', 'scope-key', ?, ?, 2, true, 100000, 20000, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, owner, scheduleId, versionId);
        token = session(owner);
        probe.resetLock();
    }

    @Test
    void guardsRequireExistingWriteTransaction() {
        assertThatThrownBy(() -> authorization.requirePayment(bookingId))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> authorization.requireReview(bookingId))
                .isInstanceOf(IllegalTransactionStateException.class);
        var readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(ignored -> authorization.requirePayment(bookingId)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("write transaction");
    }

    @Test
    void anonymousAndMalformedSessionsAreDenied() throws Exception {
        for (String action : new String[]{"payment", "review"}) {
            mvc.perform(get(PROBE + action + "/" + bookingId)).andExpect(status().isUnauthorized());
            mvc.perform(get(PROBE + action + "/" + bookingId).header("Authorization", "Bearer invalid"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void foreignAndUnknownBookingsAreIndistinguishableForBothGuards() throws Exception {
        long other = account("other@example.invalid", "customer");
        var otherToken = session(other);
        for (String action : new String[]{"payment", "review"}) {
            for (long id : new long[]{bookingId, 999999L}) {
                call(action, id, otherToken).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            }
        }
    }

    @Test
    void paymentRequiresCurrentCustomerRoleAndDoesNotAllowAdminOverride() throws Exception {
        payment().andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'customer'", owner);
        payment().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        call("payment", bookingId, session(admin)).andExpect(status().isForbidden());
        call("review", bookingId, session(admin)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"blocked", "unverified", "must_change_password", "logged_out"})
    void changedAccountOrSessionEligibilityDeniesBothGuardsImmediately(String state) throws Exception {
        switch (state) {
            case "blocked" -> jdbc.update("update trekking.app_accounts set status = 'blocked', blocked_reason = 'Violation' where id = ?", owner);
            case "unverified" -> jdbc.update("update trekking.app_accounts set email_verified_at = null where id = ?", owner);
            case "must_change_password" -> jdbc.update("update trekking.app_accounts set must_change_password = true where id = ?", owner);
            case "logged_out" -> auth.logout(token.refreshToken());
            default -> throw new AssertionError(state);
        }
        payment().andExpect(status().isUnauthorized());
        review().andExpect(status().isUnauthorized());
    }

    @Test
    void pendingPaymentRequiresLiveHoldButConfirmedBalanceDoesNotDependOnOldHold() throws Exception {
        payment().andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
        jdbc.update("update trekking.bookings set hold_expires_at = current_timestamp - interval '1 second' where id = ?", bookingId);
        paymentDenied();
        jdbc.update("update trekking.bookings set status = 'confirmed' where id = ?", bookingId);
        payment().andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "cancelled", "completed", "no_show"})
    void terminalBookingStatesCannotCreatePayment(String state) throws Exception {
        jdbc.update("""
                update trekking.bookings set status = ?, cancelled_at = current_timestamp,
                    cancellation_reason = 'Test cancellation' where id = ?
                """, state, bookingId);
        paymentDenied();
    }

    @Test
    void overdueBalanceCannotCreatePayment() throws Exception {
        jdbc.update("update trekking.bookings set balance_due_at = current_timestamp - interval '1 second' where id = ?", bookingId);
        paymentDenied();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ongoing", "completed", "cancelled"})
    void nonScheduledTripsCannotCreatePayment(String state) throws Exception {
        jdbc.update("""
                update trekking.tour_schedules set status = ?, actual_completed_at = current_timestamp,
                    completed_by = ?, cancellation_reason = 'Cancelled trip' where id = ?
                """, state, admin, scheduleId);
        paymentDenied();
    }

    @Test
    void departureInThePastCannotCreatePaymentEvenBeforeStatusJobRuns() throws Exception {
        jdbc.update("""
                update trekking.tour_schedules set starts_at = current_timestamp - interval '2 days',
                    ends_at = current_timestamp - interval '1 day', balance_due_at = current_timestamp - interval '3 days'
                where id = ?
                """, scheduleId);
        paymentDenied();
    }

    @Test
    void outstandingBalanceUsesGeneratedGroupPriceAndSumOfAcceptedReceipts() throws Exception {
        receipt(100000, "accepted", "first");
        receipt(139999, "accepted", "second");
        payment().andExpect(status().isOk());
        receipt(1, "accepted", "last");
        paymentDenied();
    }

    @ParameterizedTest
    @ValueSource(longs = {240000, 240001})
    void fullyPaidAndOverpaidBookingsCannotCreateMorePayments(long amount) throws Exception {
        receipt(amount, "accepted", "paid");
        paymentDenied();
    }

    @Test
    void browserAttemptAndUnallocatedReceiptsDoNotBecomeAcceptedMoney() throws Exception {
        jdbc.update("""
                insert into trekking.payment_attempts(booking_id, provider, merchant_reference, purpose,
                    requested_amount_vnd, status) values (?, 'payos', 'browser-success', 'full', 240000, 'succeeded')
                """, bookingId);
        receipt(240000, "pending_review", "not-yet-allocated");
        payment().andExpect(status().isOk());
        jdbc.update("update trekking.customer_payments set allocation_status = 'accepted' where booking_id = ?", bookingId);
        paymentDenied();
    }

    @Test
    void completedAttendingOwnerCanReviewAndRoleRevocationTakesEffectOnSameJwt() throws Exception {
        complete();
        participant(owner, true);
        review().andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'customer'", owner);
        review().andExpect(status().isForbidden());
    }

    @Test
    void beingBookingOwnerDoesNotProveAttendance() throws Exception {
        complete();
        reviewDenied();
        participant(owner, false);
        reviewDenied();
    }

    @Test
    void anotherParticipantCheckInDoesNotAuthorizeOwnerOrOtherParticipantToReview() throws Exception {
        complete();
        long other = account("participant@example.invalid", "customer");
        participant(other, true);
        reviewDenied();
        call("review", bookingId, session(other)).andExpect(status().isNotFound());
    }

    @Test
    void futureCheckInDoesNotProveAttendance() throws Exception {
        complete();
        participant(owner, true);
        jdbc.update("update trekking.participants set checked_in_at = current_timestamp + interval '1 day' where booking_id = ?", bookingId);
        reviewDenied();
    }

    @Test
    void scheduledEndPassingDoesNotReplaceActualCompletion() throws Exception {
        participant(owner, true);
        jdbc.update("""
                update trekking.tour_schedules set starts_at = current_timestamp - interval '2 days',
                    ends_at = current_timestamp - interval '1 day', balance_due_at = current_timestamp - interval '3 days'
                where id = ?
                """, scheduleId);
        jdbc.update("update trekking.bookings set status = 'completed' where id = ?", bookingId);
        reviewDenied();
    }

    @Test
    void completedScheduleDoesNotReplaceCompletedBooking() throws Exception {
        complete();
        participant(owner, true);
        jdbc.update("update trekking.bookings set status = 'confirmed' where id = ?", bookingId);
        reviewDenied();
    }

    @Test
    void futureActualCompletionCannotAuthorizeReview() throws Exception {
        complete();
        participant(owner, true);
        jdbc.update("update trekking.tour_schedules set actual_completed_at = current_timestamp + interval '1 day' where id = ?", scheduleId);
        reviewDenied();
    }

    @ParameterizedTest
    @ValueSource(strings = {"visible", "hidden"})
    void oneReviewPerBookingIncludesModeratedReviews(String state) throws Exception {
        complete();
        long participantId = participant(owner, true);
        jdbc.update("""
                insert into trekking.reviews(booking_id, author_id, participant_id, tour_rating, service_rating,
                    status, moderated_by, moderation_reason) values (?, ?, ?, 5, 4, ?, ?, 'Test moderation')
                """, bookingId, owner, participantId, state, admin);
        review().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "expired", "completed"})
    void paymentRefreshesBookingAlreadyManagedBeforeConcurrentStateChange(String state) {
        assertThatThrownBy(() -> authenticatedTransaction(() -> {
            var cached = entityManager.find(Booking.class, bookingId);
            assertThat(cached.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
            concurrentUpdate("""
                    update trekking.bookings set status = ?, cancelled_at = current_timestamp,
                        cancellation_reason = 'Concurrent cancellation' where id = ?
                    """, state, bookingId);
            assertThat(cached.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
            authorization.requirePayment(bookingId);
        })).isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "review"})
    void guardRefreshesScheduleAlreadyManagedBeforeConcurrentCancellation(String action) {
        if (action.equals("review")) {
            complete();
            participant(owner, true);
        }
        assertThatThrownBy(() -> authenticatedTransaction(() -> {
            entityManager.find(Booking.class, bookingId);
            var cached = entityManager.find(TourSchedule.class, scheduleId);
            var oldStatus = action.equals("payment") ? ScheduleStatus.SCHEDULED : ScheduleStatus.COMPLETED;
            assertThat(cached.getStatus()).isEqualTo(oldStatus);
            concurrentUpdate("""
                    update trekking.tour_schedules set status = 'cancelled',
                        cancellation_reason = 'Concurrent cancellation' where id = ?
                    """, scheduleId);
            assertThat(cached.getStatus()).isEqualTo(oldStatus);
            if (action.equals("payment")) authorization.requirePayment(bookingId);
            else authorization.requireReview(bookingId);
        })).isInstanceOfSatisfying(AuthException.class, ex ->
                assertThat(ex.getCode()).isEqualTo(action.equals("payment") ? "PAYMENT_NOT_ALLOWED" : "REVIEW_NOT_ALLOWED"));
    }

    @Test
    void reviewRefreshesParticipantAlreadyManagedBeforeConcurrentCheckInCorrection() {
        complete();
        long participantId = participant(owner, true);
        assertThatThrownBy(() -> authenticatedTransaction(() -> {
            var cached = entityManager.find(Participant.class, participantId);
            assertThat(cached.getCheckedInAt()).isNotNull();
            concurrentUpdate("""
                    update trekking.participants set checked_in_at = null, checked_in_by = null where id = ?
                    """, participantId);
            assertThat(cached.getCheckedInAt()).isNotNull();
            authorization.requireReview(bookingId);
        })).isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("REVIEW_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "review"})
    void authorizationLocksRemainHeldUntilConsumingTransactionCompletes(String action) throws Exception {
        Long participantId = null;
        if (action.equals("review")) {
            complete();
            participantId = participant(owner, true);
        }
        try (var executor = Executors.newSingleThreadExecutor()) {
            var request = executor.submit(() -> call("locked-" + action, bookingId, token).andExpect(status().isOk()));
            try {
                assertThat(probe.awaitLock()).isTrue();
                assertRowLocked("bookings", bookingId);
                assertRowLocked("tour_schedules", scheduleId);
                if (participantId != null) {
                    assertRowLocked("participants", participantId);
                }
            } finally {
                probe.releaseLock();
            }
            request.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select id from trekking.bookings where id = ? for update nowait", Long.class, bookingId))
                .isEqualTo(bookingId);
    }

    private void assertRowLocked(String table, long id) {
        assertThatThrownBy(() -> jdbc.queryForObject(
                "select id from trekking." + table + " where id = ? for update nowait", Long.class, id))
                .isInstanceOfSatisfying(DataAccessException.class, error ->
                        assertThat(error.getRootCause()).isInstanceOfSatisfying(SQLException.class,
                                sql -> assertThat(sql.getSQLState()).isEqualTo("55P03")));
    }

    private void authenticatedTransaction(Runnable action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(jwtAuthentication.convert(jwtDecoder.decode(access(token))));
        SecurityContextHolder.setContext(context);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> action.run());
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void concurrentUpdate(String sql, Object... arguments) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThat(executor.submit(() -> jdbc.update(sql, arguments)).get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent state change interrupted", ex);
        } catch (Exception ex) {
            throw new AssertionError("Concurrent state change failed", ex);
        }
    }

    private ResultActions call(String action, long id, IssuedTokens tokens) throws Exception {
        return authGet(PROBE + action + "/" + id, tokens);
    }

    private ResultActions payment() throws Exception { return call("payment", bookingId, token); }
    private ResultActions review() throws Exception { return call("review", bookingId, token); }

    private void paymentDenied() throws Exception {
        payment().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
    }

    private void reviewDenied() throws Exception {
        review().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
    }

    private void receipt(long amount, String allocation, String reference) {
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    allocation_status, received_at) values (?, 'payos', ?, ?, ?, current_timestamp)
                """, bookingId, reference, amount, allocation);
    }

    private void complete() {
        jdbc.update("update trekking.bookings set status = 'completed' where id = ?", bookingId);
        jdbc.update("""
                update trekking.tour_schedules set status = 'completed', actual_completed_at = current_timestamp - interval '1 hour',
                    completed_by = ?, starts_at = current_timestamp - interval '2 days',
                    ends_at = current_timestamp - interval '1 day', balance_due_at = current_timestamp - interval '3 days'
                where id = ?
                """, admin, scheduleId);
    }

    private long participant(long accountId, boolean checkedIn) {
        return jdbc.queryForObject("""
                insert into trekking.participants(booking_id, account_id, full_name, checked_in_at, checked_in_by)
                values (?, ?, 'Actual participant', case when ? then current_timestamp - interval '2 hours' else null end,
                    ?) returning id
                """, Long.class, bookingId, accountId, checkedIn, checkedIn ? admin : null);
    }

    /** Test-only consuming boundary; production business endpoints are supplied by M3/M4/M5. */
    @TestComponent
    @RestController
    static class BusinessProbeController {
        private final BusinessAuthorizationService authorization;
        private volatile CountDownLatch acquired = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        BusinessProbeController(BusinessAuthorizationService authorization) { this.authorization = authorization; }

        void resetLock() { acquired = new CountDownLatch(1); release = new CountDownLatch(1); }
        boolean awaitLock() throws InterruptedException { return acquired.await(10, TimeUnit.SECONDS); }
        void releaseLock() { release.countDown(); }

        @Transactional
        @GetMapping(PROBE + "payment/{bookingId}")
        public Map<String, Boolean> payment(@PathVariable long bookingId) {
            authorization.requirePayment(bookingId);
            return Map.of("allowed", true);
        }

        @Transactional
        @GetMapping(PROBE + "review/{bookingId}")
        public Map<String, Boolean> review(@PathVariable long bookingId) {
            authorization.requireReview(bookingId);
            return Map.of("allowed", true);
        }

        @Transactional
        @GetMapping(PROBE + "locked-payment/{bookingId}")
        public Map<String, Boolean> lockedPayment(@PathVariable long bookingId) throws InterruptedException {
            authorization.requirePayment(bookingId);
            acquired.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release booking lock");
            return Map.of("allowed", true);
        }

        @Transactional
        @GetMapping(PROBE + "locked-review/{bookingId}")
        public Map<String, Boolean> lockedReview(@PathVariable long bookingId) throws InterruptedException {
            authorization.requireReview(bookingId);
            acquired.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release booking lock");
            return Map.of("allowed", true);
        }
    }
}
