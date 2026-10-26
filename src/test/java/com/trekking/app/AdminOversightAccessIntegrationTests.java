package com.trekking.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminOversightAccessIntegrationTests extends AccountAuthorizationTestSupport {
    private long admin;
    private long guide;

    @BeforeEach
    void fixture() {
        admin = account("admin@example.invalid", "admin");
        guide = account("guide@example.invalid", "guide");
        jdbc.update("insert into trekking.tours(title) values ('Draft Trek')");
        jdbc.update("insert into trekking.tours(title) values ('Archived Trek')");
        jdbc.update("update trekking.tours set status = 'archived' where title = 'Archived Trek'");
    }

    @Test
    void guideIsDeniedEquipmentCatalogWhileAdminOverseesCatalogAndToursByStatus() throws Exception {
        mvc.perform(get("/api/v1/tours/equipment-catalog")).andExpect(status().isUnauthorized());
        var guideToken = session(guide);
        authGet("/api/v1/tours/equipment-catalog", guideToken).andExpect(status().isForbidden());
        authGet("/api/v1/tours/rental-packages", guideToken).andExpect(status().isForbidden());

        var adminToken = session(admin);
        authGet("/api/v1/tours/equipment-catalog", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        authGet("/api/v1/tours/rental-packages", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        authGet("/api/v1/tours?status=ARCHIVED", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Archived Trek"))
                .andExpect(jsonPath("$.content[0].status").value("ARCHIVED"));
        authGet("/api/v1/tours?status=DRAFT", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Draft Trek"));
        authGet("/api/v1/tours", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        authGet("/api/v1/tours/999999", adminToken).andExpect(status().isNotFound());
    }
}
