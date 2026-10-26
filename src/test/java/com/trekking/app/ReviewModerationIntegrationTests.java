package com.trekking.app;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewModerationIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String REVIEWS = "/api/v1/admin/reviews";
    private long admin;
    private long guide;
    private long reviewId;

    @BeforeEach
    void fixture() {
        long owner = account("owner@example.invalid", "customer");
        admin = account("admin@example.invalid", "admin");
        guide = account("guide@example.invalid", "guide");
        long scheduleId = internalSchedule();
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('review-moderation', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        long bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('REVIEW-MOD', 'review-mod-key', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, owner, scheduleId, versionId);
        jdbc.update("update trekking.bookings set status = 'completed' where id = ?", bookingId);
        jdbc.update("""
                update trekking.tour_schedules set status = 'completed',
                    actual_completed_at = current_timestamp - interval '1 hour', completed_by = ?,
                    starts_at = current_timestamp - interval '2 days', ends_at = current_timestamp - interval '1 day',
                    balance_due_at = current_timestamp - interval '3 days'
                where id = ?
                """, admin, scheduleId);
        long participantId = jdbc.queryForObject("""
                insert into trekking.participants(booking_id, account_id, full_name, checked_in_at, checked_in_by)
                values (?, ?, 'Reviewer', current_timestamp - interval '2 hours', ?) returning id
                """, Long.class, bookingId, owner, admin);
        reviewId = jdbc.queryForObject("""
                insert into trekking.reviews(booking_id, author_id, participant_id, tour_rating, service_rating, content)
                values (?, ?, ?, 5, 4, 'Great trek') returning id
                """, Long.class, bookingId, owner, participantId);
    }

    @Test
    void adminHidesAndRestoresReviewWithReasonPreservingRatingsWhileGuideIsDenied() throws Exception {
        authGet(REVIEWS, session(guide)).andExpect(status().isForbidden());
        authPost(REVIEWS + "/" + reviewId + "/hide", Map.of("reason", "Spam"), session(guide))
                .andExpect(status().isForbidden());

        var adminToken = session(admin);
        authGet(REVIEWS, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reviewId))
                .andExpect(jsonPath("$.content[0].status").value("visible"));
        authPost(REVIEWS + "/" + reviewId + "/hide", Map.of("reason", "  "), adminToken)
                .andExpect(status().isBadRequest());
        authPost(REVIEWS + "/" + reviewId + "/hide", Map.of("reason", "  Violation  "), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("hidden"))
                .andExpect(jsonPath("$.moderationReason").value("Violation"))
                .andExpect(jsonPath("$.moderatedBy").value(admin))
                .andExpect(jsonPath("$.tourRating").value(5))
                .andExpect(jsonPath("$.serviceRating").value(4));
        authPost(REVIEWS + "/" + reviewId + "/hide", Map.of("reason", "Again"), adminToken)
                .andExpect(status().isConflict());
        authGet(REVIEWS + "?status=hidden", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        assertThat(jdbc.queryForObject(
                "select tour_rating from trekking.reviews where id = ?", Short.class, reviewId)).isEqualTo((short) 5);
        assertThat(jdbc.queryForObject(
                "select action from trekking.audit_logs where entity_type = 'review' and entity_id = ?",
                String.class, reviewId)).isEqualTo("REVIEW_HIDDEN");

        authPost(REVIEWS + "/" + reviewId + "/restore", Map.of("reason", "Appeal accepted"), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("visible"))
                .andExpect(jsonPath("$.moderationReason").isEmpty())
                .andExpect(jsonPath("$.tourRating").value(5));
        assertThat(jdbc.queryForObject(
                "select count(*) from trekking.audit_logs where entity_type = 'review' and entity_id = ?",
                Integer.class, reviewId)).isEqualTo(2);
        authPost(REVIEWS + "/" + reviewId + "/restore", Map.of("reason", "Again"), adminToken)
                .andExpect(status().isConflict());
    }
}
