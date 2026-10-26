package com.trekking.app;

import com.trekking.app.features.auth.model.IssuedTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingApiIntegrationTests extends AccountAuthorizationTestSupport {
    private static final boolean PRINT = Boolean.getBoolean("booking.print");

    @Test
    void customerBookingLifecycleOverHttp() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long customer = account("customer@example.invalid", "customer");
        long stranger = account("stranger@example.invalid", "customer");
        long scheduleId = publishedSchedule(admin, 5);
        IssuedTokens customerSession = session(customer);
        IssuedTokens strangerSession = session(stranger);
        IssuedTokens adminSession = session(admin);

        show("Availability before booking", "GET", "/api/v1/schedules/" + scheduleId + "/availability", null,
                authGet("/api/v1/schedules/" + scheduleId + "/availability", customerSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.availableSeats").value(5))
                        .andExpect(jsonPath("$.bookable").value(true)));

        Map<String, Object> create = Map.of(
                "idempotencyKey", "demo-key-1",
                "scheduleId", scheduleId,
                "equipmentChoice", "SELF_PROVIDED",
                "passengers", List.of(
                        Map.of("fullName", "Nguyen Van A", "phone", "0901234567",
                                "email", "a@example.invalid", "bookingCustomer", true),
                        Map.of("fullName", "Tran Thi B", "phone", "0907654321")));
        MvcResult created = show("Create booking", "POST", "/api/v1/bookings", create,
                authPost("/api/v1/bookings", create, customerSession)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                        .andExpect(jsonPath("$.price.totalPriceVnd").value(4_000_000))
                        .andExpect(jsonPath("$.price.depositAmountVnd").value(1_200_000))
                        .andExpect(jsonPath("$.price.paymentState").value("UNPAID")));
        long bookingId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String bookingPath = "/api/v1/bookings/" + bookingId;

        show("Retry with the same idempotency key", "POST", "/api/v1/bookings", create,
                authPost("/api/v1/bookings", create, customerSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(bookingId)));

        show("Availability after booking", "GET", "/api/v1/schedules/" + scheduleId + "/availability", null,
                authGet("/api/v1/schedules/" + scheduleId + "/availability", customerSession)
                        .andExpect(jsonPath("$.heldSeats").value(2))
                        .andExpect(jsonPath("$.availableSeats").value(3)));

        show("My bookings", "GET", "/api/v1/bookings/my", null,
                authGet("/api/v1/bookings/my", customerSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content.length()").value(1)));

        show("Booking detail", "GET", bookingPath, null,
                authGet(bookingPath, customerSession).andExpect(status().isOk()));

        Map<String, Object> replace = Map.of("passengers", List.of(
                Map.of("fullName", "Nguyen Van A", "bookingCustomer", true),
                Map.of("fullName", "Tran Thi B"),
                Map.of("fullName", "Le Van C")));
        MvcResult replaced = show("Replace passengers (2 -> 3)", "PUT", bookingPath + "/passengers", replace,
                authPut(bookingPath + "/passengers", replace, customerSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.price.participantCount").value(3))
                        .andExpect(jsonPath("$.price.totalPriceVnd").value(6_000_000)));
        long participantId = json.readTree(replaced.getResponse().getContentAsString())
                .get("passengers").get(2).get("id").asLong();

        Map<String, String> contact = Map.of("fullName", "Le Van Cuong", "phone", "0911111111", "email", "c@example.invalid");
        show("Correct one passenger's contact", "PATCH", bookingPath + "/passengers/" + participantId, contact,
                authPatch(bookingPath + "/passengers/" + participantId, contact, customerSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.passengers[2].fullName").value("Le Van Cuong")));

        Map<String, Object> tooMany = Map.of("idempotencyKey", "demo-key-2", "scheduleId", scheduleId,
                "equipmentChoice", "SELF_PROVIDED",
                "passengers", List.of(Map.of("fullName", "P1"), Map.of("fullName", "P2"), Map.of("fullName", "P3")));
        show("Overbooking is rejected", "POST", "/api/v1/bookings", tooMany,
                authPost("/api/v1/bookings", tooMany, customerSession)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SEATS")));

        Map<String, Object> withPackage = Map.of("idempotencyKey", "demo-key-3", "scheduleId", scheduleId,
                "equipmentChoice", "TOUR_PACKAGE", "passengers", List.of(Map.of("fullName", "P1")));
        show("Equipment package is not available yet", "POST", "/api/v1/bookings", withPackage,
                authPost("/api/v1/bookings", withPackage, customerSession)
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("EQUIPMENT_SERVICE_UNAVAILABLE")));

        show("Another customer cannot see the booking", "GET", bookingPath, null,
                authGet(bookingPath, strangerSession)
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND")));

        show("Customer cannot use the operator list", "GET", "/api/v1/bookings", null,
                authGet("/api/v1/bookings", customerSession).andExpect(status().isForbidden()));

        show("Operator list filtered by schedule", "GET", "/api/v1/bookings?scheduleId=" + scheduleId, null,
                authGet("/api/v1/bookings?scheduleId=" + scheduleId, adminSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].id").value(bookingId)));

        Map<String, String> cancel = Map.of("reason", "Change of plans");
        show("Cancel booking", "POST", bookingPath + "/cancel", cancel,
                authPost(bookingPath + "/cancel", cancel, customerSession)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CANCELLED"))
                        .andExpect(jsonPath("$.refundRequired").value(false)));

        show("Availability after cancel", "GET", "/api/v1/schedules/" + scheduleId + "/availability", null,
                authGet("/api/v1/schedules/" + scheduleId + "/availability", customerSession)
                        .andExpect(jsonPath("$.heldSeats").value(0))
                        .andExpect(jsonPath("$.availableSeats").value(5)));
    }

    private ResultActions authPatch(String path, Object body, IssuedTokens tokens) throws Exception {
        return mvc.perform(patch(path).header("Authorization", "Bearer " + access(tokens))
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private long publishedSchedule(long adminId, int capacity) {
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('CANCEL-STD', 'Standard cancellation', 'cancellation', ?) returning id
                """, Long.class, adminId);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from,
                    approved_by, approved_at)
                values (?, 1, 'Full refund up to 7 days before departure.', current_timestamp - interval '1 day',
                    ?, current_timestamp - interval '1 day') returning id
                """, Long.class, policyId, adminId);
        long tourId = jdbc.queryForObject("""
                insert into trekking.tours(title, status, cancellation_policy_version_id, reviewed_by, reviewed_at)
                values ('Fansipan 2 days', 'published', ?, ?, current_timestamp) returning id
                """, Long.class, versionId, adminId);
        return jdbc.queryForObject("""
                insert into trekking.tour_schedules(tour_id, starts_at, ends_at, capacity,
                    base_price_vnd, deposit_percent, balance_due_at, meeting_point)
                values (?, current_timestamp + interval '10 days', current_timestamp + interval '11 days',
                    ?, 2000000, 30, current_timestamp + interval '9 days', 'Sa Pa square') returning id
                """, Long.class, tourId, capacity);
    }

    private MvcResult show(String title, String method, String path, Object body, ResultActions actions)
            throws Exception {
        MvcResult result = actions.andReturn();
        if (PRINT) {
            StringBuilder out = new StringBuilder("\n===== ").append(title).append(" =====\n")
                    .append(method).append(' ').append(path).append('\n');
            if (body != null) out.append(pretty(json.writeValueAsString(body))).append('\n');
            out.append("--> ").append(result.getResponse().getStatus()).append('\n');
            String response = result.getResponse().getContentAsString();
            if (!response.isBlank()) out.append(pretty(response)).append('\n');
            System.out.println(out);
        }
        return result;
    }

    private String pretty(String raw) {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(raw));
    }
}
