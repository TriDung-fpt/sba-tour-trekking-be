package com.trekking.app;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String POLICIES = "/api/v1/admin/policies";

    @Test
    void deleteHidesPolicyAndPreservesReferencedVersions() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long customer = account("customer@example.invalid", "customer");
        long policy = policy(admin, "cancellation", true);
        long version = jdbc.queryForObject("insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) values (?,1,'Terms',current_timestamp,?,current_timestamp) returning id", Long.class, policy, admin);
        jdbc.update("insert into trekking.tours(title,status,cancellation_policy_version_id,reviewed_by,reviewed_at) values ('Test','published',?,?,current_timestamp)", version, admin);
        mvc.perform(delete(POLICIES + "/" + policy).header("Authorization", "Bearer " + access(session(customer)))).andExpect(status().isForbidden());
        mvc.perform(delete(POLICIES + "/" + policy).header("Authorization", "Bearer " + access(session(admin)))).andExpect(status().isNoContent());
        authGet(POLICIES, session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("select active from trekking.policies where id=?", Boolean.class, policy)).isFalse();
        assertThat(jdbc.queryForObject("select content from trekking.policy_versions where id=?", String.class, version)).isEqualTo("Terms");
        assertThat(jdbc.queryForObject("select cancellation_policy_version_id from trekking.tours where title='Test'", Long.class)).isEqualTo(version);
    }

    @Test
    void scheduledVersionCanBeRevokedAndReplacedBeforeOriginalEffectiveDate() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        long policy = policy(admin, "cancellation", true);
        var scheduled = authPost(POLICIES + "/" + policy + "/versions", publish("Wrong future terms", OffsetDateTime.now().plusDays(10)), token)
                .andExpect(status().isCreated()).andReturn();
        long version = json.readTree(scheduled.getResponse().getContentAsString()).get("id").asLong();
        authPost(POLICIES + "/" + policy + "/versions/" + version + "/revoke", Map.of("reason", "Replace scheduled terms"), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.revokedAt").isNotEmpty());
        authPost(POLICIES + "/" + policy + "/versions", publish("Replacement terms", OffsetDateTime.now().minusMinutes(1)), token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2));
        authGet(POLICIES + "/" + policy + "/versions", token).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].revocationReason").value("Replace scheduled terms"));
        authGet(POLICIES + "/cancellation/available", token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Replacement terms"));
        assertThat(jdbc.queryForObject("select content from trekking.policy_versions where id=?", String.class, version))
                .isEqualTo("Wrong future terms");
    }

    @Test
    void createWithVersionRollsBackPolicyWhenInitialVersionAuditFails() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        jdbc.execute("alter table trekking.audit_logs add constraint qa_atomic_policy check (action <> 'POLICY_VERSION_PUBLISHED')");
        try {
            authPost(POLICIES + "/with-version", Map.of("policy", Map.of("code", "ATOMIC", "title", "Atomic policy", "kind", "cancellation"),
                            "version", publish("Terms", OffsetDateTime.now())), session(admin))
                    .andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("select count(*) from trekking.policies where code='ATOMIC'", Integer.class)).isZero();
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_atomic_policy");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancellation", "payment", "general"})
    void adminCreatesCustomerPolicyAndPublishesImmutableVersion(String kind) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        var created = authPost(POLICIES, Map.of("code", " CUSTOMER_TERMS ", "title", " Customer terms ", "kind", kind,
                        "createdBy", 999999, "active", false), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CUSTOMER_TERMS")).andExpect(jsonPath("$.kind").value(kind))
                .andExpect(jsonPath("$.active").value(true)).andReturn();
        long policy = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        OffsetDateTime effective = OffsetDateTime.now().plusDays(1);
        var published = authPost(POLICIES + "/" + policy + "/versions", publish("Customer terms version 1", effective), token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.approvedBy").value(admin)).andExpect(jsonPath("$.platformFeePercent").doesNotExist())
                .andExpect(jsonPath("$.payoutWaitHours").doesNotExist()).andReturn();
        long version = json.readTree(published.getResponse().getContentAsString()).get("id").asLong();
        authGet(POLICIES, token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        authGet(POLICIES + "/" + policy, token).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Customer terms"));
        authGet(POLICIES + "/" + policy + "/versions", token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(version));
        assertThat(jdbc.queryForObject("select created_by from trekking.policies where id=?", Long.class, policy)).isEqualTo(admin);
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs where entity_type='policy'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("update trekking.policy_versions set content='changed' where id=?", version))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from trekking.policy_versions where id=?", version)).isInstanceOf(DataAccessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"guide", "customer"})
    void nonAdminsCannotReadOrManagePolicies(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        var token = session(caller);
        authPost(POLICIES, Map.of("code", "terms", "title", "Terms", "kind", "general"), token).andExpect(status().isForbidden());
        authGet(POLICIES, token).andExpect(status().isForbidden());
        authGet(POLICIES + "/999", token).andExpect(status().isForbidden());
        authGet(POLICIES + "/999/versions", token).andExpect(status().isForbidden());
        authPost(POLICIES + "/999/versions", publish("Terms", OffsetDateTime.now()), token).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from trekking.policies", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cooperation", "GENERAL", "unknown"})
    void creationRejectsRemovedOrUnknownPolicyKinds(String kind) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        authPost(POLICIES, Map.of("code", "terms", "title", "Terms", "kind", kind), session(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void versionEffectiveDatesMustIncreaseAndPublishingNeverRewritesPriorVersion() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        long policy = policy(admin, "general", true);
        OffsetDateTime first = OffsetDateTime.now().plusDays(2);
        authPost(POLICIES + "/" + policy + "/versions", publish("First", first), token).andExpect(status().isCreated());
        for (OffsetDateTime invalid : List.of(first, first.minusDays(1))) {
            authPost(POLICIES + "/" + policy + "/versions", publish("Out of order", invalid), token)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_EFFECTIVE_FROM_OUT_OF_ORDER"));
        }
        authPost(POLICIES + "/" + policy + "/versions", publish("Second", first.plusDays(1)), token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2));
        assertThat(jdbc.queryForList("select content from trekking.policy_versions where policy_id=? order by version_number", String.class, policy))
                .containsExactly("First", "Second");
    }

    @Test
    void publicationUsesDatabaseMicrosecondPrecisionBeforeComparingEffectiveDates() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        long policy = policy(admin, "general", true);
        OffsetDateTime first = OffsetDateTime.now().plusDays(2).withNano(123456100);
        authPost(POLICIES + "/" + policy + "/versions", publish("First", first), token).andExpect(status().isCreated());
        for (OffsetDateTime sameMicrosecond : List.of(first, first.plusNanos(800))) {
            authPost(POLICIES + "/" + policy + "/versions", publish("Same stored time", sameMicrosecond), token)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_EFFECTIVE_FROM_OUT_OF_ORDER"));
        }
        authPost(POLICIES + "/" + policy + "/versions", publish("Next microsecond", first.plusNanos(1000)), token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2));
        assertThat(jdbc.queryForObject("select count(*) from trekking.policy_versions where policy_id=?", Integer.class, policy)).isEqualTo(2);
    }

    @Test
    void inactivePoliciesRemainReadableButCannotPublishAndUnknownPoliciesReturnNotFound() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        long inactive = policy(admin, "general", false);
        policy(admin, "general", true);
        authGet(POLICIES, token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        authGet(POLICIES + "/" + inactive, token).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        authGet(POLICIES + "/999999", token).andExpect(status().isNotFound());
        authGet(POLICIES + "/999999/versions", token).andExpect(status().isNotFound());
        authPost(POLICIES + "/999999/versions", publish("Unknown policy", OffsetDateTime.now()), token)
                .andExpect(status().isNotFound());
        authPost(POLICIES + "/" + inactive + "/versions", publish("Inactive policy", OffsetDateTime.now()), token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_NOT_PUBLISHABLE"));
        assertThat(jdbc.queryForObject("select count(*) from trekking.policy_versions", Long.class)).isZero();
    }

    @Test
    void publicationAuditFailureRollsBackNewVersion() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long policy = policy(admin, "general", true);
        var token = session(admin);
        jdbc.execute("alter table trekking.audit_logs add constraint qa_policy_audit check (action <> 'POLICY_VERSION_PUBLISHED')");
        try {
            authPost(POLICIES + "/" + policy + "/versions", publish("Terms", OffsetDateTime.now()), token)
                    .andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("select count(*) from trekking.policy_versions", Integer.class)).isZero();
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_policy_audit");
        }
    }

    @Test
    void concurrentPublicationSerializesVersionAndEffectiveDateOrdering() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long policy = policy(admin, "general", true);
        var token = session(admin);
        var body = publish("Concurrent terms", OffsetDateTime.now().plusDays(1));
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> publish = () -> authPost(POLICIES + "/" + policy + "/versions", body, token).andReturn().getResponse().getStatus();
            var results = pool.invokeAll(List.of(publish, publish));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from trekking.policy_versions where policy_id=?", Integer.class, policy)).isEqualTo(1);
    }

    private Map<String, Object> publish(String content, OffsetDateTime effective) {
        return Map.of("content", content, "effectiveFrom", effective.toString());
    }

    private long policy(long admin, String kind, boolean active) {
        return jdbc.queryForObject("""
                insert into trekking.policies(code,title,kind,created_by,active)
                values (?, 'Customer terms', ?, ?, ?) returning id
                """, Long.class, "terms-" + kind + "-" + active, kind, admin, active);
    }
}
