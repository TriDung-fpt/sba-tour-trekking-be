package com.trekking.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TourAttendanceIntegrationTests extends AccountAuthorizationTestSupport {
    @Test
    void assignedGuideChecksOutCheckedInParticipantOnce() throws Exception {
        long guide = account("attendance-guide@example.invalid", "guide");
        long otherGuide = account("other-guide@example.invalid", "guide");
        long customer = account("attendance-customer@example.invalid", "customer");
        long admin = account("attendance-admin@example.invalid", "admin");
        long schedule = internalSchedule();
        long profile = internalGuide(guide);
        internalGuide(otherGuide);
        jdbc.update("insert into trekking.schedule_guides(schedule_id, guide_id) values (?, ?)", schedule, profile);
        long policy = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('attendance-policy', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long version = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policy);
        long booking = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at, status)
                values ('ATTENDANCE', 'attendance-key', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days', 'confirmed') returning id
                """, Long.class, customer, schedule, version);
        long participant = jdbc.queryForObject("""
                insert into trekking.participants(booking_id, full_name) values (?, 'Traveler') returning id
                """, Long.class, booking);
        String path = "/api/v1/schedules/" + schedule + "/bookings/" + booking + "/participants/" + participant;
        var guideToken = session(guide);
        var otherToken = session(otherGuide);

        authPost(path + "/check-out", Map.of("note", "done"), otherToken).andExpect(status().isNotFound());
        authPost(path + "/check-out", Map.of("note", "done"), guideToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_CHECKED_IN"));
        authPost(path + "/check-in", Map.of("note", "present"), guideToken).andExpect(status().isOk());
        authPost(path + "/check-out", Map.of("note", "done"), guideToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedOutBy").value(guide)).andExpect(jsonPath("$.note").value("done"));
        authPost(path + "/check-out", Map.of("note", "again"), guideToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("done"));
        assertThat(jdbc.queryForObject("select count(*) from trekking.tour_participant_checkouts where participant_id = ?",
                Integer.class, participant)).isEqualTo(1);
    }
}
