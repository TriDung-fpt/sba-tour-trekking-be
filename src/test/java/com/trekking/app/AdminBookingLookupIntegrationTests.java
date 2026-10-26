package com.trekking.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBookingLookupIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String BOOKINGS = "/api/v1/admin/bookings";
    private long admin;
    private long guide;
    private long bookingId;

    @BeforeEach
    void fixture() {
        long customer = account("customer@example.invalid", "customer");
        admin = account("admin@example.invalid", "admin");
        guide = account("guide@example.invalid", "guide");
        long scheduleId = internalSchedule();
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('admin-booking', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('LOOKUP-1', 'lookup-key-1', ?, ?, 2, true, 100000, 20000, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, customer, scheduleId, versionId);
    }

    @Test
    void adminLooksUpBookingsByStatusAndDetailWhileGuideIsDenied() throws Exception {
        mvc.perform(get(BOOKINGS)).andExpect(status().isUnauthorized());
        authGet(BOOKINGS, session(guide)).andExpect(status().isForbidden());
        authGet(BOOKINGS + "/" + bookingId, session(guide)).andExpect(status().isForbidden());

        var adminToken = session(admin);
        authGet(BOOKINGS, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(bookingId))
                .andExpect(jsonPath("$.content[0].bookingCode").value("LOOKUP-1"))
                .andExpect(jsonPath("$.content[0].status").value("pending_payment"));
        authGet(BOOKINGS + "?status=completed", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        authGet(BOOKINGS + "?status=pending_payment&query=lookup", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        authGet(BOOKINGS + "/" + bookingId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.customerEmail").value("customer@example.invalid"))
                .andExpect(jsonPath("$.acceptedPaymentTotalVnd").value(0))
                .andExpect(jsonPath("$.hasReview").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        authGet(BOOKINGS + "/999999", adminToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }
}
