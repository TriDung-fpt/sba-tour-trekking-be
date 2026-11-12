package com.trekking.app;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end booking flow over HTTP: create, idempotency, holds, lists with money totals, cancel. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingLifecycleIntegrationTests extends AccountAuthorizationTestSupport {
    long admin;
    long tourId;
    long scheduleId;

    @BeforeEach
    void fixtures() {
        admin = account("booking-admin@example.invalid", "admin");
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('CANCEL-STD', 'Standard cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from,
                    approved_by, approved_at)
                values (?, 1, 'Full refund up to 7 days before departure.', current_timestamp - interval '1 day',
                    ?, current_timestamp - interval '1 day') returning id
                """, Long.class, policyId, admin);
        tourId = jdbc.queryForObject("""
                insert into trekking.tours(title, status, cancellation_policy_version_id, reviewed_by, reviewed_at)
                values ('Fansipan 2 days', 'published', ?, ?, current_timestamp) returning id
                """, Long.class, versionId, admin);
        scheduleId = jdbc.queryForObject("""
                insert into trekking.tour_schedules(tour_id, starts_at, ends_at, capacity,
                    base_price_vnd, deposit_percent, balance_due_at, meeting_point)
                values (?, current_timestamp + interval '10 days', current_timestamp + interval '11 days',
                    5, 2000000, 30, current_timestamp + interval '9 days', 'Sa Pa square') returning id
                """, Long.class, tourId);
    }

    @Test
    void customerCreatesListsAndCancelsUnpaidBooking() throws Exception {
        long customer = account("booking-customer@example.invalid", "customer");
        var session = session(customer);
        var create = createRequest("lifecycle-1", 2);

        var created = authPost("/api/v1/bookings", create, session)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.price.totalPriceVnd").value(4_000_000))
                .andExpect(jsonPath("$.price.depositAmountVnd").value(1_200_000))
                .andReturn();
        var body = json.readTree(created.getResponse().getContentAsString());
        long bookingId = body.get("id").asLong();
        // Tour has no code yet -> default prefix from BOOKING_CODE_PREFIX (BK).
        assertThat(body.get("bookingCode").asString()).matches("BK-[A-Z2-9]{10}");

        // Same key + same request = safe retry; same key + different request = rejected.
        authPost("/api/v1/bookings", create, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));
        authPost("/api/v1/bookings", createRequest("lifecycle-1", 3), session)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        authGet("/api/v1/schedules/" + scheduleId + "/availability", session)
                .andExpect(jsonPath("$.heldSeats").value(2)).andExpect(jsonPath("$.availableSeats").value(3));

        // Only one live hold per customer.
        authPost("/api/v1/bookings", createRequest("lifecycle-2", 1), session)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTIVE_BOOKING_HOLD_EXISTS"));

        // Money totals in lists come from accepted payments only.
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    allocation_status, received_at)
                values (?, 'payos', 'lifecycle-pending', 500000, 'pending_review', current_timestamp)
                """, bookingId);
        authGet("/api/v1/bookings/my", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].paidAmountVnd").value(0));
        authGet("/api/v1/bookings/my/active-holds", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        authGet("/api/v1/bookings?page=0&size=20", session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(bookingId));
        authGet("/api/v1/bookings/" + bookingId, session).andExpect(status().isOk());
        authGet("/api/v1/bookings/" + bookingId, session(account("stranger@example.invalid", "customer")))
                .andExpect(status().isNotFound());

        // Unpaid cancellation: no refund request, seats released, retry is idempotent.
        authGet("/api/v1/bookings/" + bookingId + "/refund-preview", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedRefundVnd").value(0))
                .andExpect(jsonPath("$.refundAccountRequired").value(false));
        authPost("/api/v1/bookings/" + bookingId + "/cancel", Map.of("reason", "Change of plans"), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundRequired").value(false))
                .andExpect(jsonPath("$.refund").doesNotExist());
        authPost("/api/v1/bookings/" + bookingId + "/cancel", Map.of("reason", "Again"), session)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        authGet("/api/v1/schedules/" + scheduleId + "/availability", session)
                .andExpect(jsonPath("$.heldSeats").value(0)).andExpect(jsonPath("$.availableSeats").value(5));
        assertThat(jdbc.queryForObject("select count(*) from trekking.refund_requests", Long.class)).isZero();
        authGet("/api/v1/bookings/" + bookingId + "/refund", session).andExpect(status().isNoContent());

        // After cancelling, the customer can hold a new booking again.
        authPost("/api/v1/bookings", createRequest("lifecycle-3", 1), session).andExpect(status().isCreated());
    }

    @Test
    void bookingCodeUsesTheActiveTourCode() throws Exception {
        authPost("/api/v1/admin/tour-codes", Map.of("tourId", tourId, "code", "fan01"), session(admin))
                .andExpect(status().isCreated());
        var created = authPost("/api/v1/bookings", createRequest("tour-code-1", 1),
                session(account("code-customer@example.invalid", "customer")))
                .andExpect(status().isCreated()).andReturn();
        assertThat(json.readTree(created.getResponse().getContentAsString()).get("bookingCode").asString())
                .matches("FAN01-[A-Z2-9]{10}");
    }

    private Map<String, Object> createRequest(String idempotencyKey, int passengers) {
        var people = new java.util.ArrayList<Map<String, Object>>();
        people.add(Map.of("fullName", "Nguyen Van A", "phone", "0901234567",
                "email", "a@example.invalid", "bookingCustomer", true));
        for (int i = 1; i < passengers; i++) people.add(Map.of("fullName", "Passenger " + i));
        return Map.of("idempotencyKey", idempotencyKey, "scheduleId", scheduleId,
                "equipmentChoice", "SELF_PROVIDED", "passengers", List.copyOf(people));
    }
}
