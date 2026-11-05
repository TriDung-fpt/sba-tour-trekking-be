package com.trekking.app;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TourItineraryIntegrationTests extends AccountAuthorizationTestSupport {
    @Test
    void savesDaysAndActivitiesAtomicallyAndPreservesExistingDataOnFailure() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        long tour = jdbc.queryForObject("insert into trekking.tours(title,duration_days,status) values ('Test',2,'draft') returning id", Long.class);
        String path = "/api/v1/tours/" + tour + "/itinerary";
        var day = Map.of("dayNumber", 1, "title", "Hà Nội – Xím Vàng", "meals", "Tối", "overnight", "Nhà nghỉ",
                "activities", List.of(Map.of("startTime", "19:30", "title", "Khởi hành", "description", "Gặp hướng dẫn viên")));
        authPut(path, Map.of("days", List.of(day)), token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Hà Nội – Xím Vàng"))
                .andExpect(jsonPath("$[0].activities[0].title").value("Khởi hành"));
        authPut(path, Map.of("days", List.of(day, day)), token).andExpect(status().isBadRequest());
        var invalid = Map.of("dayNumber", 1, "title", "Replacement",
                "activities", List.of(Map.of("title", "Invalid location", "locationId", 99999999)));
        authPut(path, Map.of("days", List.of(invalid)), token).andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("select title from trekking.tour_itinerary_days where tour_id=?", String.class, tour)).isEqualTo("Hà Nội – Xím Vàng");
        assertThat(jdbc.queryForObject("select title from trekking.tour_activities where tour_id=?", String.class, tour)).isEqualTo("Khởi hành");
        authGet(path, token).andExpect(status().isOk()).andExpect(jsonPath("$[0].meals").value("Tối"));
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void existingActivitiesRemainVisibleAndCustomersCannotEdit() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long customer = account("customer@example.invalid", "customer");
        long policy = jdbc.queryForObject("insert into trekking.policies(code,title,kind,created_by) values ('CANCEL','Cancel','cancellation',?) returning id", Long.class, admin);
        long version = jdbc.queryForObject("insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) values (?,1,'Terms',current_timestamp,?,current_timestamp) returning id", Long.class, policy, admin);
        long tour = jdbc.queryForObject("insert into trekking.tours(title,duration_days,status,cancellation_policy_version_id,reviewed_by,reviewed_at) values ('Test',2,'published',?,?,current_timestamp) returning id", Long.class, version, admin);
        jdbc.update("insert into trekking.tour_activities(tour_id,day_number,display_order,title) values (?,1,0,'Legacy activity')", tour);
        String path = "/api/v1/tours/" + tour + "/itinerary";
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Ngày 1"))
                .andExpect(jsonPath("$[0].activities[0].title").value("Legacy activity"));
        authPut(path, Map.of("days", List.of()), session(customer)).andExpect(status().isForbidden());
    }
}
