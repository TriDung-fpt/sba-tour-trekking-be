package com.trekking.app;

import com.trekking.app.features.auth.service.OtpMailSender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractIntegrationTests extends AuthTestInfrastructure {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "GET /api/v1/auth/csrf",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/verify-email",
            "POST /api/v1/auth/resend-verification",
            "POST /api/v1/auth/forgot-password",
            "POST /api/v1/auth/reset-password",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/google",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout",
            "POST /api/v1/auth/activate-account");
    private static final Set<String> PROTECTED_OPERATIONS = Set.of(
            "GET /api/v1/auth/me",
            "GET /api/v1/accounts/me/authorization",
            "GET /api/v1/admin/accounts/{id}",
            "POST /api/v1/admin/accounts/{id}/block",
            "POST /api/v1/admin/accounts/{id}/unblock",
            "PUT /api/v1/admin/accounts/{id}/roles",
            "POST /api/v1/admin/policies",
            "GET /api/v1/admin/policies",
            "GET /api/v1/admin/policies/{policyId}",
            "POST /api/v1/admin/policies/{policyId}/versions",
            "GET /api/v1/admin/policies/{policyId}/versions",
            "POST /api/v1/admin/staff/invitations",
            "POST /api/v1/admin/staff/invitations/{accountId}/resend",
            "POST /api/v1/admin/guides/invitations",
            "POST /api/v1/admin/guides/invitations/{accountId}/resend");
    private static final Set<String> TOUR_OPERATIONS = Set.of(
            "GET /api/v1/tours",
            "GET /api/v1/tours/{tourId}",
            "POST /api/v1/tours",
            "PUT /api/v1/tours/{tourId}",
            "DELETE /api/v1/tours/{tourId}",
            "GET /api/v1/tours/{tourId}/schedules",
            "GET /api/v1/tours/{tourId}/schedules/{scheduleId}",
            "POST /api/v1/tours/{tourId}/schedules",
            "PUT /api/v1/tours/{tourId}/schedules/{scheduleId}",
            "DELETE /api/v1/tours/{tourId}/schedules/{scheduleId}",
            "GET /api/v1/tours/{tourId}/activities",
            "POST /api/v1/tours/{tourId}/activities",
            "PUT /api/v1/tours/{tourId}/activities/{activityId}",
            "DELETE /api/v1/tours/{tourId}/activities/{activityId}",
            "POST /api/v1/tours/{tourId}/media",
            "PATCH /api/v1/tours/{tourId}/media/{mediaId}",
            "PATCH /api/v1/tours/{tourId}/media/{mediaId}/cover",
            "PUT /api/v1/tours/{tourId}/media/order",
            "DELETE /api/v1/tours/{tourId}/media/{mediaId}");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean OtpMailSender mail;
    private JsonNode document;

    @BeforeEach
    void loadGeneratedDocument() throws Exception {
        var response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        document = json.readTree(response.getContentAsString());
    }

    @Test
    void documentsTheSingleOperatorApiAndExportsTheValidatedSnapshot() throws Exception {
        var actual = new TreeSet<String>();
        for (var path : document.path("paths").properties()) {
            // Framework documentation and error routes are outside the production API inventory.
            if (!path.getKey().startsWith("/api/v1/")) continue;
            for (var method : path.getValue().properties()) {
                if (HTTP_METHODS.contains(method.getKey())) {
                    actual.add(method.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey());
                }
            }
        }
        var expected = new HashSet<>(PUBLIC_OPERATIONS);
        expected.addAll(PROTECTED_OPERATIONS);
        expected.addAll(TOUR_OPERATIONS);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(document.path("paths").properties().stream()
                .filter(path -> path.getKey().startsWith("/api/v1/")).count())
                .isEqualTo(expected.stream().map(operation -> operation.substring(operation.indexOf(' ') + 1)).distinct().count());
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/p1-openapi.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(document));
    }

    @Test
    void removedPlatformAndFinancialGrantRoutesAreNotAdvertisedAsSupportedOperations() {
        assertThat(document.path("paths").properties()).noneSatisfy(path ->
                assertThat(path.getKey()).matches("/api/v1/(companies|admin/companies|agreements|partner/statements|admin/statements)(/.*)?"));
        assertThat(document.path("paths").properties()).noneSatisfy(path ->
                assertThat(path.getKey()).contains("financial-permissions"));
    }

    @Test
    void everyProtectedOperationRequiresBearerWithoutAnAnonymousAlternative() throws Exception {
        var bearer = json.readTree("[{\"bearerAuth\":[]}]");
        var protectedOperations = new HashSet<>(PROTECTED_OPERATIONS);
        protectedOperations.addAll(TOUR_OPERATIONS);
        for (String endpoint : protectedOperations) {
            assertThat(operation(endpoint).path("security")).as("Bearer requirement for %s", endpoint).isEqualTo(bearer);
        }
    }

    @Test
    void publicAuthAndActivationOperationsDoNotRequireBearer() {
        var globalSecurity = document.path("security");
        assertThat(globalSecurity.isMissingNode() || (globalSecurity.isArray() && globalSecurity.isEmpty()))
                .as("Public operations must not inherit a global security requirement").isTrue();
        for (String endpoint : PUBLIC_OPERATIONS) {
            var security = operation(endpoint).path("security");
            assertThat(security.isMissingNode() || (security.isArray() && security.isEmpty()))
                    .as("Public security contract for %s", endpoint).isTrue();
        }
    }

    @Test
    void declaresTheJwtHttpBearerSchemeUsedBySwaggerAuthorization() {
        var scheme = document.at("/components/securitySchemes/bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    private JsonNode operation(String endpoint) {
        int separator = endpoint.indexOf(' ');
        var method = endpoint.substring(0, separator).toLowerCase(Locale.ROOT);
        var path = endpoint.substring(separator + 1);
        var operation = document.path("paths").path(path).path(method);
        assertThat(operation.isObject()).as("Documented operation %s", endpoint).isTrue();
        return operation;
    }
}
