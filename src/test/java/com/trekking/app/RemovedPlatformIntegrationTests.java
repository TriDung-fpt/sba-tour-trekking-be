package com.trekking.app;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RemovedPlatformIntegrationTests extends AccountAuthorizationTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"admin", "guide", "customer"})
    void removedCompanyAgreementAndSettlementRoutesAreAbsentForEveryAuthenticatedRole(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        var token = session(caller);
        for (String path : List.of("/api/v1/companies", "/api/v1/companies/999",
                "/api/v1/companies/999/license", "/api/v1/admin/companies", "/api/v1/admin/companies/999",
                "/api/v1/companies/999/agreement-policies", "/api/v1/companies/999/agreements",
                "/api/v1/companies/999/agreements/999", "/api/v1/agreements",
                "/api/v1/partner/statements", "/api/v1/admin/statements")) {
            authGet(path, token).andExpect(status().isNotFound());
        }
        for (String path : List.of("/api/v1/companies", "/api/v1/companies/999/resubmit",
                "/api/v1/companies/999/license", "/api/v1/admin/companies/999/decision",
                "/api/v1/companies/999/guides/invitations", "/api/v1/companies/999/guides/invitations/999/resend",
                "/api/v1/companies/999/agreements/otp", "/api/v1/companies/999/agreements",
                "/api/v1/admin/statements", "/api/v1/admin/statements/999/approve",
                "/api/v1/admin/statements/999/transfers")) {
            authPost(path, Map.of(), token).andExpect(status().isNotFound());
        }
        authPut("/api/v1/companies/999", Map.of(), token).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs", Long.class)).isZero();
    }

    @Test
    void removedRoutesDoNotBypassTheAuthenticatedApiBoundary() throws Exception {
        mvc.perform(get("/api/v1/companies")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/companies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/statements")).andExpect(status().isUnauthorized());
    }

    @Test
    void activeDatabaseContainsOnlyTheSingleOperatorModel() {
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'trekking' and table_name in
                    ('companies', 'company_members', 'company_agreements', 'company_statements',
                     'company_statement_items', 'company_transfers', 'account_financial_permissions',
                     'legacy_account_roles', 'legacy_account_financial_permissions')
                """, String.class)).isEmpty();
        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'trekking' and (column_name like '%company%'
                    or column_name in ('platform_fee_percent', 'payout_wait_hours', 'payout_terms_snapshot', 'statement_id'))
                """, String.class)).isEmpty();
    }
}
