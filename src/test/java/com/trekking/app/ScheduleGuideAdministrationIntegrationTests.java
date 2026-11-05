package com.trekking.app;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleGuideAdministrationIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String GUIDES = "/api/v1/schedules/%d/guides";

    @Test
    void adminCanReplaceAndClearScheduleGuideAssignments() throws Exception {
        long admin = account("schedule-admin@example.invalid", "admin");
        long guideAccount = account("schedule-guide@example.invalid", "guide");
        long guideId = internalGuide(guideAccount);
        long scheduleId = internalSchedule();
        var token = session(admin);

        authPost(GUIDES.formatted(scheduleId), Map.of("guides", List.of(Map.of("guideId", guideId, "isLead", true))), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guideId").value(guideId))
                .andExpect(jsonPath("$[0].isLead").value(true));
        authGet(GUIDES.formatted(scheduleId), token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guideId").value(guideId));

        authPost(GUIDES.formatted(scheduleId), Map.of("guides", List.of()), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void assignmentRejectsDuplicateGuideAndRequiresOneLead() throws Exception {
        long admin = account("schedule-admin@example.invalid", "admin");
        long guideAccount = account("schedule-guide@example.invalid", "guide");
        long guideId = internalGuide(guideAccount);
        long scheduleId = internalSchedule();
        var token = session(admin);

        authPost(GUIDES.formatted(scheduleId), Map.of("guides", List.of(
                Map.of("guideId", guideId, "isLead", true), Map.of("guideId", guideId, "isLead", false))), token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_GUIDE"));
        authPost(GUIDES.formatted(scheduleId), Map.of("guides", List.of(Map.of("guideId", guideId, "isLead", false))), token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LEAD_GUIDE"));
    }

    @Test
    void nonAdminCannotAssignGuides() throws Exception {
        long customer = account("schedule-customer@example.invalid", "customer");
        long scheduleId = internalSchedule();

        authPost(GUIDES.formatted(scheduleId), Map.of("guides", List.of()), session(customer))
                .andExpect(status().isForbidden());
    }
}
