package com.trekking.app;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemSettingsIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String SETTINGS = "/api/v1/admin/settings";
    private long admin;
    private long guide;

    @BeforeEach
    void fixture() {
        admin = account("admin@example.invalid", "admin");
        guide = account("guide@example.invalid", "guide");
    }

    @Test
    void newSettingVersionKeepsOldVersionsIntactWithAuditWhileGuideIsDenied() throws Exception {
        mvc.perform(get(SETTINGS)).andExpect(status().isUnauthorized());
        var guideToken = session(guide);
        authGet(SETTINGS, guideToken).andExpect(status().isForbidden());
        authPost(SETTINGS, Map.of("settingKey", "hold_timeout_minutes", "value", "30",
                "effectiveFrom", "2026-01-01T00:00:00Z", "reason", "Initial"), guideToken)
                .andExpect(status().isForbidden());

        var adminToken = session(admin);
        authPost(SETTINGS, Map.of("settingKey", "hold_timeout_minutes", "value", "30",
                "effectiveFrom", "2026-01-01T00:00:00Z", "reason", "Initial hold timeout"), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.value").value("30"))
                .andExpect(jsonPath("$.changedBy").value(admin));
        authPost(SETTINGS, Map.of("settingKey", "hold_timeout_minutes", "value", "45",
                "effectiveFrom", "2026-02-01T00:00:00Z", "reason", "Extend hold window"), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.value").value("45"));
        authPost(SETTINGS, Map.of("settingKey", "hold_timeout_minutes", "value", "10",
                "effectiveFrom", "2026-01-15T00:00:00Z", "reason", "Out of order"), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_EFFECTIVE_FROM_OUT_OF_ORDER"));
        authPost(SETTINGS, Map.of("settingKey", "Bad Key", "value", "1",
                "effectiveFrom", "2026-03-01T00:00:00Z", "reason", "Invalid key"), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTING_KEY"));
        authPost(SETTINGS, Map.of("settingKey", "hold_timeout_minutes", "value", "60",
                "effectiveFrom", "2026-03-01T00:00:00Z"), adminToken)
                .andExpect(status().isBadRequest());

        authGet(SETTINGS, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.settingKey=='hold_timeout_minutes')].value",
                        org.hamcrest.Matchers.hasItem("45")))
                .andExpect(jsonPath("$[?(@.settingKey=='hold_timeout_minutes')].versionNumber",
                        org.hamcrest.Matchers.hasItem(2)));
        authGet(SETTINGS + "/hold_timeout_minutes/versions", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1))
                .andExpect(jsonPath("$[1].value").value("30"))
                .andExpect(jsonPath("$[1].changeReason").value("Initial hold timeout"));
        assertThat(jdbc.queryForObject(
                "select setting_value from trekking.system_settings where version_number = 1",
                String.class)).isEqualTo("30");
        assertThat(jdbc.queryForObject(
                "select count(*) from trekking.audit_logs where entity_type = 'system_setting'"
                        + " and action = 'SETTING_VERSION_PUBLISHED'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() ->
                jdbc.update("update trekking.system_settings set setting_value = 'hacked' where version_number = 1"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
