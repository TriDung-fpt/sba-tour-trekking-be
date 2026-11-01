package com.trekking.app;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleDefaultsIntegrationTests extends AccountAuthorizationTestSupport {
    @Test
    void savesDefaultsWithoutCreatingBookableDeparture() throws Exception {
        long admin = account("schedule-defaults@example.invalid", "admin");
        long customer = account("schedule-defaults-customer@example.invalid", "customer");
        long tour = jdbc.queryForObject("insert into trekking.tours(title,status) values ('Unscheduled tour','draft') returning id", Long.class);
        String path = "/api/v1/tours/" + tour + "/schedule-defaults";
        authGet(path, session(admin)).andExpect(status().isNoContent());
        var details = Map.of("capacity", 15, "basePriceVnd", 2500000, "equipmentFeeVnd", 200000,
                "depositPercent", 30, "meetingPoint", "Hanoi", "contactPhone", "0900000000");
        authPut(path, details, session(customer)).andExpect(status().isForbidden());
        authPut(path, details, session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(15));
        authGet(path, session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.basePriceVnd").value(2500000));
        assertThat(jdbc.queryForObject("select count(*) from trekking.tour_schedules where tour_id=?", Integer.class, tour)).isZero();
    }
}
