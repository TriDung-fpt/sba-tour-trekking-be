package com.trekking.app;

import com.trekking.app.features.auth.model.IssuedTokens;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TourCodeIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String CODES = "/api/v1/admin/tour-codes";

    long admin;
    long tour;
    long otherTour;

    @BeforeEach
    void fixtures() {
        admin = account("tour-code-admin@example.invalid", "admin");
        tour = jdbc.queryForObject("insert into trekking.tours(title) values ('Fansipan') returning id", Long.class);
        otherTour = jdbc.queryForObject("insert into trekking.tours(title) values ('Ta Xua') returning id", Long.class);
    }

    @Test
    void adminCreatesRenamesAndSoftDeletesCodesWithAuditTrail() throws Exception {
        var session = session(admin);
        var created = authPost(CODES, Map.of("tourId", tour, "code", "fan01"), session)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FAN01"))
                .andExpect(jsonPath("$.tourTitle").value("Fansipan"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        long firstId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // One active code per tour; codes are unique across tours.
        authPost(CODES, Map.of("tourId", tour, "code", "FAN02"), session)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOUR_CODE_ALREADY_ASSIGNED"));
        authPost(CODES, Map.of("tourId", otherTour, "code", "FAN01"), session)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOUR_CODE_EXISTS"));
        authPost(CODES, Map.of("tourId", otherTour, "code", "bad code!"), session)
                .andExpect(status().isBadRequest());

        var renamed = authPut(CODES + "/" + firstId, Map.of("code", "FAN02", "reason", "Rebrand"), session)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("FAN02"))
                .andReturn();
        long secondId = json.readTree(renamed.getResponse().getContentAsString()).get("id").asLong();
        authGet(CODES + "/" + firstId, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.deleteReason").value("Rebrand"));

        // Delete requires a reason, is soft, and the code can never be reused.
        deleteCode(secondId, Map.of(), session).andExpect(status().isBadRequest());
        deleteCode(secondId, Map.of("reason", "Tour discontinued"), session).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        deleteCode(secondId, Map.of("reason", "Again"), session)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOUR_CODE_DELETED"));
        authPost(CODES, Map.of("tourId", otherTour, "code", "FAN02"), session)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOUR_CODE_EXISTS"));
        assertThat(jdbc.queryForObject("select count(*) from trekking.tour_codes where tour_id = ?", Long.class, tour))
                .isEqualTo(2L);

        authGet(CODES + "?tourId=" + tour, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        authGet(CODES + "?tourId=" + tour + "&includeDeleted=true", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        authGet(CODES + "?keyword=fan0&includeDeleted=true", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        authGet(CODES + "?keyword=zzz&includeDeleted=true", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        authGet(CODES + "/tours/" + tour + "/logs", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].action").value("TOUR_CODE_DELETED"))
                .andExpect(jsonPath("$[0].reason").value("Tour discontinued"))
                .andExpect(jsonPath("$[0].actorEmail").value("tour-code-admin@example.invalid"));
    }

    @Test
    void customersCannotManageTourCodes() throws Exception {
        var customer = session(account("tour-code-customer@example.invalid", "customer"));
        authGet(CODES, customer).andExpect(status().isForbidden());
        authPost(CODES, Map.of("tourId", tour, "code", "FAN01"), customer).andExpect(status().isForbidden());
    }

    private ResultActions deleteCode(long id, Object body, IssuedTokens tokens) throws Exception {
        return mvc.perform(delete(CODES + "/" + id).header("Authorization", "Bearer " + access(tokens))
                .contentType("application/json").content(json.writeValueAsString(body)));
    }
}
