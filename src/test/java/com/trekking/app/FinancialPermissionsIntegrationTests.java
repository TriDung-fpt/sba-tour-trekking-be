package com.trekking.app;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FinancialPermissionsIntegrationTests.PermissionProbe.class)
class FinancialPermissionsIntegrationTests extends AccountAuthorizationTestSupport {
    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {"FINANCE_READ", "PAYMENT_RECORD", "REFUND_APPROVE", "REFUND_RECORD",
            "TOUR_READ", "TOUR_MANAGE", "TOUR_COMPOSE", "TOUR_PUBLISH", "SCHEDULE_MANAGE", "BOOKING_MANAGE", "GUIDE_MANAGE"})
    void adminHasInternalPermissionsWithoutGrantsAndRevocationTakesEffectOnSameJwt(Permission permission) throws Exception {
        long admin = account("admin@example.invalid", "admin", "customer");
        var token = session(admin);
        authGet(probe(permission), token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'admin'", admin);
        authGet(probe(permission), token).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {"FINANCE_READ", "PAYMENT_RECORD", "REFUND_APPROVE", "REFUND_RECORD",
            "TOUR_MANAGE", "TOUR_COMPOSE", "TOUR_PUBLISH", "SCHEDULE_MANAGE", "BOOKING_MANAGE", "GUIDE_MANAGE"})
    void customerAndGuideRolesDoNotGrantInternalManagementPermissions(Permission permission) throws Exception {
        long customer = account("customer@example.invalid", "customer");
        long guide = account("guide@example.invalid", "guide");
        long combined = account("combined@example.invalid", "customer", "guide");
        for (long caller : List.of(customer, guide, combined)) {
            authGet(probe(permission), session(caller)).andExpect(status().isForbidden());
        }
    }

    @Test
    void removedGrantEndpointsRequireAuthenticationAndAreAbsentForEveryRole() throws Exception {
        long target = account("target@example.invalid", "customer");
        mvc.perform(get(path(target))).andExpect(status().isUnauthorized());
        for (String role : List.of("admin", "guide", "customer")) {
            long caller = account(role + "@example.invalid", role);
            var token = session(caller);
            for (long id : List.of(target, caller, 999999L)) {
                authGet(path(id), token).andExpect(status().isNotFound());
                authPut(path(id), Map.of("permissions", List.of("FINANCE_READ"), "reason", "Removed grant model"), token)
                        .andExpect(status().isNotFound());
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs", Long.class)).isZero();
    }

    private String path(long id) { return "/api/v1/admin/accounts/" + id + "/financial-permissions"; }
    private String probe(Permission permission) { return "/api/v1/test-financial-permission/" + permission.name(); }

    @RestController
    @TestComponent
    static class PermissionProbe {
        private final AuthorizationService authorization;
        PermissionProbe(AuthorizationService authorization) { this.authorization = authorization; }

        @GetMapping("/api/v1/test-financial-permission/{permission}")
        Map<String, Boolean> require(@PathVariable Permission permission) {
            authorization.require(permission);
            return Map.of("allowed", true);
        }
    }
}
