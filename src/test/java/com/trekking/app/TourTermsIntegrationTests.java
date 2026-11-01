package com.trekking.app;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TourTermsIntegrationTests extends AccountAuthorizationTestSupport {
    @Test
    void tourCanUseMultipleSharedTermsAndExposeItsActualCancellationPolicy() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long cancel = version(admin, "CANCEL", "cancellation");
        long payment = version(admin, "PAY", "payment");
        long note = version(admin, "NOTE", "general");
        long tour = jdbc.queryForObject("insert into trekking.tours(title,status,cancellation_policy_version_id,reviewed_by,reviewed_at) values ('Tour','published',?,?,current_timestamp) returning id", Long.class, cancel, admin);
        String path = "/api/v1/tours/" + tour + "/terms";
        authPut(path, Map.of("versionIds", List.of(payment, note)), session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].primaryCancellation").value(true))
                .andExpect(jsonPath("$[1].title").value("PAY"))
                .andExpect(jsonPath("$[2].title").value("NOTE"));
        authPut(path, Map.of("versionIds", List.of(note, note)), session(admin)).andExpect(status().isBadRequest());
        long newer = jdbc.queryForObject("insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) select policy_id,2,'New terms',current_timestamp,?,current_timestamp from trekking.policy_versions where id=? returning id", Long.class, admin, note);
        authPut(path, Map.of("versionIds", List.of(note, newer)), session(admin)).andExpect(status().isBadRequest());
        authPut(path, Map.of("versionIds", List.of(cancel)), session(admin)).andExpect(status().isBadRequest());
        long replacement = version(admin, "CANCEL-NEW", "cancellation");
        authPut(path, Map.of("versionIds", List.of(note, note), "cancellationPolicyVersionId", replacement), session(admin)).andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select cancellation_policy_version_id from trekking.tours where id=?", Long.class, tour)).isEqualTo(cancel);
        authPut(path, Map.of("versionIds", List.of(payment, note), "cancellationPolicyVersionId", replacement), session(admin)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select cancellation_policy_version_id from trekking.tours where id=?", Long.class, tour)).isEqualTo(replacement);
        authPut(path, Map.of("versionIds", List.of(payment, note), "removeCancellationPolicy", true), session(admin)).andExpect(status().isConflict());
        jdbc.update("update trekking.tours set status='draft' where id=?", tour);
        authPut(path, Map.of("versionIds", List.of(payment, note), "removeCancellationPolicy", true), session(admin)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select cancellation_policy_version_id from trekking.tours where id=?", Long.class, tour)).isNull();
        jdbc.update("update trekking.tours set status='published', cancellation_policy_version_id=? where id=?", replacement, tour);
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        jdbc.update("update trekking.policies set active=false where id=(select policy_id from trekking.policy_versions where id=?)", note);
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
    private long version(long admin, String code, String kind) {
        long policy = jdbc.queryForObject("insert into trekking.policies(code,title,kind,created_by) values (?,?,?,?) returning id", Long.class, code, code, kind, admin);
        return jdbc.queryForObject("insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) values (?,1,'Terms',current_timestamp,?,current_timestamp) returning id", Long.class, policy, admin);
    }
}
