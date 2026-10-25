package com.trekking.app;

import com.trekking.app.features.auth.model.IssuedTokens;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.request.RentalItemSelectionRequest;
import com.trekking.app.features.booking.dto.request.RentalSelectionRequest;
import com.trekking.app.features.booking.dto.request.TravelerRequest;
import com.trekking.app.features.booking.enums.RentalMode;
import com.trekking.app.features.booking.service.BookingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingIntegrationTests extends AccountAuthorizationTestSupport {
    @Autowired BookingService bookingService;

    private long customerId;
    private long otherCustomerId;
    private long scheduleId;
    private long packageId;
    private long firstItemId;
    private IssuedTokens customerSession;
    private IssuedTokens otherSession;

    @BeforeEach
    void prepareTourAndSchedule() {
        customerId = account("booking-customer@example.invalid", "customer");
        otherCustomerId = account("booking-other@example.invalid", "customer");
        long adminId = account("booking-admin@example.invalid", "admin");
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('booking-test-policy', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, adminId);
        long policyVersionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Cancellation terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        long tourId = jdbc.queryForObject("""
                insert into trekking.tours(title, status, cancellation_policy_version_id, reviewed_by, reviewed_at)
                values ('Booking integration tour', 'published', ?, ?, current_timestamp) returning id
                """, Long.class, policyVersionId, adminId);
        scheduleId = jdbc.queryForObject("""
                insert into trekking.tour_schedules(tour_id, starts_at, ends_at, capacity, base_price_vnd,
                    deposit_percent, balance_due_at)
                values (?, current_timestamp + interval '10 days', current_timestamp + interval '11 days',
                    20, 1000000, 30, current_timestamp + interval '9 days') returning id
                """, Long.class, tourId);
        packageId = jdbc.queryForObject("""
                insert into trekking.tour_equipment_packages(tour_id, title, included_items, rental_price_vnd)
                values (?, 'Group kit', 'Tent and poles', 200000) returning id
                """, Long.class, tourId);
        long equipmentId = jdbc.queryForObject("""
                insert into trekking.equipment(name, individual_rental_price_vnd)
                values (?, 50000) returning id
                """, Long.class, "Trekking pole " + java.util.UUID.randomUUID());
        firstItemId = jdbc.queryForObject("""
                insert into trekking.tour_equipment_items(package_id, equipment_id, quantity_in_package, individual_rental_price_vnd)
                values (?, ?, 1, 50000) returning id
                """, Long.class, packageId, equipmentId);
        customerSession = session(customerId);
        otherSession = session(otherCustomerId);
    }

    @Test
    void createsBookingWithGroupPackagePriceSnapshot() throws Exception {
        ResultActions result = authPost("/api/v1/bookings", request(2,
                new RentalSelectionRequest(RentalMode.PACKAGE, packageId, 2, List.of())), customerSession);

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.basePriceVnd").value(1000000))
                .andExpect(jsonPath("$.rentalFeeVnd").value(400000))
                .andExpect(jsonPath("$.totalPriceVnd").value(2400000))
                .andExpect(jsonPath("$.rental.mode").value("PACKAGE"))
                .andExpect(jsonPath("$.rental.quantity").value(2));
        assertThat(jdbc.queryForObject("select held_seats from trekking.tour_schedules where id = ?", Integer.class, scheduleId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select equipment_fee_vnd from trekking.bookings order by id desc limit 1", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select group_rental_fee_vnd from trekking.bookings order by id desc limit 1", Long.class)).isEqualTo(400000L);
    }

    @Test
    void createsBookingWithIndividualItemPriceAndRejectsUnavailableItem() throws Exception {
        authPost("/api/v1/bookings", request(2, new RentalSelectionRequest(RentalMode.ITEMS, null, null,
                List.of(new RentalItemSelectionRequest(firstItemId, 3)))), customerSession)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rentalFeeVnd").value(150000))
                .andExpect(jsonPath("$.totalPriceVnd").value(2150000));

        authPost("/api/v1/bookings", request(1, new RentalSelectionRequest(RentalMode.ITEMS, null, null,
                List.of(new RentalItemSelectionRequest(firstItemId + 1000, 1)))), customerSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RENTAL_SELECTION"));
    }

    @Test
    void createsNoRentalBookingAndRejectsMoreTravelersThanAvailableSeats() throws Exception {
        authPost("/api/v1/bookings", request(1, new RentalSelectionRequest(RentalMode.NONE, null, null, List.of())), customerSession)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rentalFeeVnd").value(0))
                .andExpect(jsonPath("$.totalPriceVnd").value(1000000));

        jdbc.update("update trekking.tour_schedules set capacity = 1, held_seats = 0 where id = ?", scheduleId);
        authPost("/api/v1/bookings", request(2, new RentalSelectionRequest(RentalMode.NONE, null, null, List.of())), customerSession)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_FULL"));
    }

    @Test
    void cancellationAndExpiryReleaseHeldSeatsAndOwnerScopeIsEnforced() throws Exception {
        long bookingId = createBooking();
        assertThat(jdbc.queryForObject("select held_seats from trekking.tour_schedules where id = ?", Integer.class, scheduleId)).isEqualTo(2);

        authGet("/api/v1/bookings/" + bookingId, otherSession)
                .andExpect(status().isNotFound());
        authPost("/api/v1/bookings/" + bookingId + "/cancel", java.util.Map.of("reason", "Plans changed"), customerSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select held_seats from trekking.tour_schedules where id = ?", Integer.class, scheduleId)).isZero();

        long expiringId = createBooking();
        jdbc.update("update trekking.bookings set hold_expires_at = current_timestamp - interval '1 second' where id = ?", expiringId);
        assertThat(bookingService.expirePendingBookings()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from trekking.bookings where id = ?", String.class, expiringId)).isEqualTo("expired");
        assertThat(jdbc.queryForObject("select held_seats from trekking.tour_schedules where id = ?", Integer.class, scheduleId)).isZero();
    }

    private long createBooking() throws Exception {
        String response = authPost("/api/v1/bookings", request(2,
                new RentalSelectionRequest(RentalMode.NONE, null, null, List.of())), customerSession)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private CreateBookingRequest request(int travelerCount, RentalSelectionRequest rental) {
        return new CreateBookingRequest(scheduleId,
                java.util.stream.IntStream.range(0, travelerCount)
                        .mapToObj(index -> new TravelerRequest("Traveler " + index, "", ""))
                        .toList(), rental);
    }
}
