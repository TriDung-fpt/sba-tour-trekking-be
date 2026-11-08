package com.trekking.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TourCalendarEquipmentIntegrationTests extends AccountAuthorizationTestSupport {
    @Test
    void scheduleOverviewIncludesEmptyToursAndAllDepartureStatusesWithPagination() throws Exception {
        long admin = account("overview-admin@example.invalid", "admin");
        long customer = account("overview-customer@example.invalid", "customer");
        long tour = jdbc.queryForObject("insert into trekking.tours(title) values ('Overview Trek') returning id", Long.class);
        jdbc.update("""
                insert into trekking.tour_schedules(tour_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at,status,cancellation_reason)
                values (?,current_timestamp + interval '10 days',current_timestamp + interval '11 days',10,100000,30,current_timestamp + interval '9 days','scheduled',null),
                       (?,current_timestamp + interval '20 days',current_timestamp + interval '21 days',10,100000,30,current_timestamp + interval '19 days','cancelled','Cancelled')
                """, tour, tour);
        long emptyTour = jdbc.queryForObject("insert into trekking.tours(title) values ('Overview Empty') returning id", Long.class);
        jdbc.update("insert into trekking.tours(title,status) values ('Overview Archived','archived')");
        var token = session(admin);
        mvc.perform(get("/api/v1/admin/schedules/overview")).andExpect(status().isUnauthorized());
        authGet("/api/v1/admin/schedules/overview", session(customer)).andExpect(status().isForbidden());
        authGet("/api/v1/admin/schedules/overview?query=OVERVIEW&size=1&page=0", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].tourId").value(emptyTour))
                .andExpect(jsonPath("$.content[0].scheduleCount").value(0));
        authGet("/api/v1/admin/schedules/overview?query=overview&size=1&page=1", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].tourId").value(tour))
                .andExpect(jsonPath("$.content[0].scheduleCount").value(2));
        authGet("/api/v1/admin/schedules/overview?size=101", token).andExpect(status().isBadRequest());
    }

    @Test
    void calendarCountsAllDeparturesPerTourAndShowsSeatsForEachDeparture() throws Exception {
        long admin = account("calendar-admin@example.invalid", "admin");
        long schedule = internalSchedule();
        long tour = jdbc.queryForObject("select tour_id from trekking.tour_schedules where id=?", Long.class, schedule);
        jdbc.update("update trekking.tour_schedules set confirmed_seats=3, held_seats=2 where id=?", schedule);
        jdbc.update("""
                insert into trekking.tour_schedules(tour_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at,status,cancellation_reason)
                values (?,current_timestamp + interval '20 days',current_timestamp + interval '21 days',10,100000,30,
                        current_timestamp + interval '19 days','cancelled','Test cancellation')
                """, tour);
        long otherSchedule = internalSchedule();
        String start = Instant.now().toString();
        String end = Instant.now().plus(15, ChronoUnit.DAYS).toString();
        var result = authGet("/api/v1/tours/schedules?start=" + start + "&end=" + end, session(admin))
                .andExpect(status().isOk()).andReturn();
        var rows = json.readTree(result.getResponse().getContentAsString());
        var selected = rows.findValues("id").stream();
        assertThat(selected.map(node -> node.asLong())).contains(schedule, otherSchedule);
        for (var row : rows) {
            if (row.get("id").asLong() == schedule) {
                assertThat(row.get("scheduleCount").asLong()).isEqualTo(2);
                assertThat(row.get("capacity").asInt()).isEqualTo(10);
                assertThat(row.get("confirmedSeats").asInt()).isEqualTo(3);
                assertThat(row.get("heldSeats").asInt()).isEqualTo(2);
            }
            if (row.get("id").asLong() == otherSchedule) assertThat(row.get("scheduleCount").asLong()).isEqualTo(1);
        }
        authGet("/api/v1/tours/schedules?start=2000-01-01T00:00:00Z&end=2000-01-02T00:00:00Z", session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void bookingDetailAndTourStatisticsShowRentedItemsWithoutExposingOtherBookings() throws Exception {
        long admin = account("equipment-admin@example.invalid", "admin");
        long customer = account("equipment-owner@example.invalid", "customer");
        long other = account("equipment-other@example.invalid", "customer");
        long schedule = internalSchedule();
        long tour = jdbc.queryForObject("select tour_id from trekking.tour_schedules where id=?", Long.class, schedule);
        long secondSchedule = jdbc.queryForObject("""
                insert into trekking.tour_schedules(tour_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at)
                values (?,current_timestamp + interval '20 days',current_timestamp + interval '21 days',10,100000,30,
                        current_timestamp + interval '19 days') returning id
                """, Long.class, tour);
        long policy = jdbc.queryForObject("insert into trekking.policies(code,title,kind,created_by) values ('equipment-view','Cancellation','cancellation',?) returning id", Long.class, admin);
        long version = jdbc.queryForObject("insert into trekking.policy_versions(policy_id,version_number,content,effective_from) values (?,1,'terms',current_timestamp) returning id", Long.class, policy);
        long first = booking(customer, schedule, version, "EQUIPMENT-1");
        long held = booking(customer, schedule, version, "EQUIPMENT-2");
        long noEquipment = booking(customer, schedule, version, "EQUIPMENT-3");
        long otherDeparture = booking(customer, secondSchedule, version, "EQUIPMENT-4");
        jdbc.update("insert into trekking.participants(booking_id,full_name) values (?,?)", first, "Traveller");
        long item = jdbc.queryForObject("insert into trekking.equipment(name,status) values (?,'deleted') returning id", Long.class, "Tent " + UUID.randomUUID());
        reserve(first, schedule, item, "confirmed", 2);
        reserve(held, schedule, item, "held", 7);
        reserve(otherDeparture, secondSchedule, item, "confirmed", 3);
        var owner = session(customer);
        authGet("/api/v1/bookings/" + first, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.passengers[0].fullName").value("Traveller"))
                .andExpect(jsonPath("$.equipmentReservation.status").value("confirmed"))
                .andExpect(jsonPath("$.equipmentReservation.items[0].equipmentId").value(item))
                .andExpect(jsonPath("$.equipmentReservation.items[0].quantity").value(2));
        authGet("/api/v1/bookings/" + noEquipment, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentReservation").doesNotExist());
        authGet("/api/v1/bookings/" + first, session(other)).andExpect(status().isNotFound());
        authGet("/api/v1/bookings/" + first + "/equipment/reservation", session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        authGet("/api/v1/tours/" + tour + "/statistics", session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleCount").value(2))
                .andExpect(jsonPath("$.equipment[0].equipmentId").value(item))
                .andExpect(jsonPath("$.equipment[0].quantity").value(5));
    }

    private long booking(long customer, long schedule, long version, String code) {
        return jdbc.queryForObject("""
                insert into trekking.bookings(booking_code,idempotency_key,customer_id,schedule_id,participant_count,
                    includes_equipment,base_price_vnd,equipment_fee_vnd,deposit_percent,cancellation_policy_version_id,
                    policy_snapshot,hold_expires_at,balance_due_at)
                values (?,?,?, ?,1,true,100000,0,30,?,'{}',current_timestamp + interval '1 hour',current_timestamp + interval '9 days') returning id
                """, Long.class, code, code, customer, schedule, version);
    }

    private void reserve(long booking, long schedule, long equipment, String state, int quantity) {
        long reservation = jdbc.queryForObject("""
                insert into trekking.equipment_reservations(booking_id,schedule_id,selection_key,status,participant_count,hold_expires_at)
                values (?,?,'selected',?,1,current_timestamp + interval '1 hour') returning id
                """, Long.class, booking, schedule, state);
        jdbc.update("insert into trekking.equipment_reservation_items(reservation_id,equipment_id,quantity) values (?,?,?)", reservation, equipment, quantity);
    }
}
