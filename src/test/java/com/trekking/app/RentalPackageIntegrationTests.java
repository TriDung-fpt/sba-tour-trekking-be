package com.trekking.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RentalPackageIntegrationTests extends AccountAuthorizationTestSupport {
    @Test
    void updatingPackageCanRetainExistingEquipment() throws Exception {
        long admin = account("package-update@example.invalid", "admin");
        long equipmentId = jdbc.queryForObject("insert into trekking.equipment(name) values ('Test tent') returning id", Long.class);
        long packageId = jdbc.queryForObject("insert into trekking.rental_package_templates(title,rental_price_vnd) values ('Camping',300000) returning id", Long.class);
        jdbc.update("insert into trekking.rental_package_template_items(package_template_id,equipment_id,quantity_in_package) values (?,?,1)", packageId, equipmentId);
        var body = Map.of("title", "Camping updated", "rentalPriceVnd", 320000,
                "items", List.of(Map.of("equipmentId", equipmentId, "quantityInPackage", 2)));
        authPut("/api/v1/tours/rental-packages/" + packageId, body, session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantityInPackage").value(2));
        assertThat(jdbc.queryForObject("select count(*) from trekking.rental_package_template_items where package_template_id=? and equipment_id=?", Integer.class, packageId, equipmentId)).isEqualTo(1);
    }

    @Test
    void tourEquipmentSupportsMultiplePackagesAndIndividualItemsWithOverlapAndAtomicValidation() throws Exception {
        long admin = account("tour-equipment-admin@example.invalid", "admin");
        var adminSession = session(admin);

        long tent = jdbc.queryForObject(
                "insert into trekking.equipment(name, description, individual_rental_price_vnd) values ('Tent 2P', 'Waterproof tent', 150000) returning id",
                Long.class);
        long sleepingBag = jdbc.queryForObject(
                "insert into trekking.equipment(name, description, individual_rental_price_vnd) values ('Sleeping Bag', 'Warm bag', 80000) returning id",
                Long.class);
        long headlamp = jdbc.queryForObject(
                "insert into trekking.equipment(name, description, individual_rental_price_vnd) values ('Headlamp', 'LED lamp', 40000) returning id",
                Long.class);
        long stoveOnlyInCombo = jdbc.queryForObject(
                "insert into trekking.equipment(name, description, individual_rental_price_vnd) values ('Camp Stove', 'Combo only stove', null) returning id",
                Long.class);

        long campingCombo = jdbc.queryForObject(
                "insert into trekking.rental_package_templates(title, rental_price_vnd) values ('Camping Combo', 300000) returning id",
                Long.class);
        jdbc.update("insert into trekking.rental_package_template_items(package_template_id, equipment_id, quantity_in_package) values (?, ?, 1), (?, ?, 2)",
                campingCombo, tent, campingCombo, sleepingBag);

        long cookingCombo = jdbc.queryForObject(
                "insert into trekking.rental_package_templates(title, rental_price_vnd) values ('Cooking & Light Combo', 120000) returning id",
                Long.class);
        jdbc.update("insert into trekking.rental_package_template_items(package_template_id, equipment_id, quantity_in_package) values (?, ?, 1), (?, ?, 1)",
                cookingCombo, stoveOnlyInCombo, cookingCombo, headlamp);

        long tourId = jdbc.queryForObject(
                "insert into trekking.tours(title, status) values ('Multi Combo Trek', 'draft') returning id",
                Long.class);
        String path = "/api/v1/tours/" + tourId + "/equipment";

        // Initial GET returns version 0 and empty lists
        authGet(path, adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.packages.length()").value(0))
                .andExpect(jsonPath("$.individualItems.length()").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        // 1. Attach multiple combos AND multiple individual items (including tent & headlamp which are already in the selected combos)
        var assignMultiple = Map.of(
                "version", 0,
                "packageTemplateIds", List.of(campingCombo, cookingCombo),
                "individualEquipmentIds", List.of(tent, headlamp, sleepingBag));
        authPut(path, assignMultiple, adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.packages.length()").value(2))
                .andExpect(jsonPath("$.packages[0].packageTemplateId").value(campingCombo))
                .andExpect(jsonPath("$.packages[0].title").value("Camping Combo"))
                .andExpect(jsonPath("$.packages[0].rentalPriceVnd").value(300000))
                .andExpect(jsonPath("$.packages[0].items.length()").value(2))
                .andExpect(jsonPath("$.packages[1].packageTemplateId").value(cookingCombo))
                .andExpect(jsonPath("$.packages[1].title").value("Cooking & Light Combo"))
                .andExpect(jsonPath("$.packages[1].rentalPriceVnd").value(120000))
                .andExpect(jsonPath("$.packages[1].items.length()").value(2))
                .andExpect(jsonPath("$.individualItems.length()").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].equipmentId").value(tent))
                .andExpect(jsonPath("$.items[0].individualRentalPriceVnd").value(150000))
                .andExpect(jsonPath("$.items[1].equipmentId").value(headlamp))
                .andExpect(jsonPath("$.items[2].equipmentId").value(sleepingBag));

        // Verify GET returns the saved multi-combo and individual item configuration with version 1
        authGet(path, adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.packages.length()").value(2))
                .andExpect(jsonPath("$.individualItems.length()").value(3))
                .andExpect(jsonPath("$.items.length()").value(3));

        // 2. Reject duplicate combo IDs, duplicate individual IDs, unknown IDs, or non-rentable individual items without corrupting saved state
        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(campingCombo, campingCombo), "individualEquipmentIds", List.of(tent)), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EQUIPMENT_SELECTION"));

        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(campingCombo), "individualEquipmentIds", List.of(tent, tent)), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EQUIPMENT_SELECTION"));

        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(999999L), "individualEquipmentIds", List.of(tent)), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EQUIPMENT_SELECTION"));

        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(campingCombo), "individualEquipmentIds", List.of(999999L)), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EQUIPMENT_SELECTION"));

        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(campingCombo), "individualEquipmentIds", List.of(stoveOnlyInCombo)), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EQUIPMENT_SELECTION"));

        // Saved state and version remain intact after failed requests
        authGet(path, adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.packages.length()").value(2))
                .andExpect(jsonPath("$.items.length()").value(3));

        // 3. Partial removal: keep 1 combo and 1 overlapping individual item
        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(cookingCombo), "individualEquipmentIds", List.of(headlamp)), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.packages.length()").value(1))
                .andExpect(jsonPath("$.packages[0].packageTemplateId").value(cookingCombo))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].equipmentId").value(headlamp));

        // 4. Unlink all combos and individual items from the tour
        authPut(path, Map.of("version", 2, "packageTemplateIds", List.of(), "individualEquipmentIds", List.of()), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.packages.length()").value(0))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.equipmentPackage").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from trekking.tour_equipment_packages where tour_id=? and active = true", Integer.class, tourId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from trekking.tour_equipment_items where tour_id=?", Integer.class, tourId)).isZero();
    }

    @Test
    void concurrentAdminSessionsDetectStaleEquipmentVersionAndPreventOverwrite() throws Exception {
        long adminA = account("admin-a-concurrency@example.invalid", "admin");
        long adminB = account("admin-b-concurrency@example.invalid", "admin");
        var sessionA = session(adminA);
        var sessionB = session(adminB);

        long tent = jdbc.queryForObject(
                "insert into trekking.equipment(name, individual_rental_price_vnd) values ('Concurrency Tent', 150000) returning id",
                Long.class);
        long pole = jdbc.queryForObject(
                "insert into trekking.equipment(name, individual_rental_price_vnd) values ('Concurrency Pole', 50000) returning id",
                Long.class);
        long comboA = jdbc.queryForObject(
                "insert into trekking.rental_package_templates(title, rental_price_vnd) values ('Combo A', 200000) returning id",
                Long.class);
        jdbc.update("insert into trekking.rental_package_template_items(package_template_id, equipment_id, quantity_in_package) values (?, ?, 1)", comboA, tent);
        long comboB = jdbc.queryForObject(
                "insert into trekking.rental_package_templates(title, rental_price_vnd) values ('Combo B', 100000) returning id",
                Long.class);
        jdbc.update("insert into trekking.rental_package_template_items(package_template_id, equipment_id, quantity_in_package) values (?, ?, 2)", comboB, pole);

        long tourId = jdbc.queryForObject(
                "insert into trekking.tours(title, status) values ('Concurrency Tour', 'draft') returning id",
                Long.class);
        String path = "/api/v1/tours/" + tourId + "/equipment";

        // Both Admin A and Admin B open the page and read version 0
        authGet(path, sessionA).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        authGet(path, sessionB).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));

        // Admin A saves first with version 0 -> succeeds and advances version to 1
        authPut(path, Map.of("version", 0, "packageTemplateIds", List.of(comboA), "individualEquipmentIds", List.of(tent)), sessionA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.packages.length()").value(1))
                .andExpect(jsonPath("$.packages[0].packageTemplateId").value(comboA))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].equipmentId").value(tent));

        // Admin B attempts to save using stale version 0 -> rejected with 409 EQUIPMENT_CONFIG_CONFLICT
        authPut(path, Map.of("version", 0, "packageTemplateIds", List.of(comboB), "individualEquipmentIds", List.of(pole)), sessionB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CONFIG_CONFLICT"));

        // A legacy client omitting version when version > 0 is also rejected with 409 EQUIPMENT_CONFIG_CONFLICT
        authPut(path, Map.of("packageTemplateIds", List.of(comboB), "individualEquipmentIds", List.of(pole)), sessionB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CONFIG_CONFLICT"));

        // Verify Admin A's saved configuration was NOT overwritten
        authGet(path, sessionB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.packages.length()").value(1))
                .andExpect(jsonPath("$.packages[0].packageTemplateId").value(comboA))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].equipmentId").value(tent));

        // Admin B reloads version 1 and saves with version 1 -> succeeds and advances version to 2
        authPut(path, Map.of("version", 1, "packageTemplateIds", List.of(comboA, comboB), "individualEquipmentIds", List.of(tent, pole)), sessionB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.packages.length()").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void migrationAndTourReconfigurationPreserveLegacyConfigurationsAndHistoricalReservations() throws Exception {
        String database = "eq_migration_" + UUID.randomUUID().toString().replace("-", "");
        String baseUrl = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/";
        try (Connection adminConn = DriverManager.getConnection(baseUrl + "auth_test", "auth_test", "auth_test")) {
            try (var stmt = adminConn.createStatement()) {
                stmt.execute("create database " + database);
            }
            try (Connection testConn = DriverManager.getConnection(baseUrl + database, "auth_test", "auth_test")) {
                try (var stmt = testConn.createStatement()) {
                    stmt.execute(Files.readString(Path.of("schema/schema.sql")));
                    for (String migration : List.of(
                            "001_add_google_subject.sql",
                            "002_add_account_session_version.sql",
                            "003_add_cooperation_policy_terms.sql",
                            "004_add_financial_permissions.sql",
                            "005_single_operator_foundation.sql",
                            "006_remove_company_domain.sql",
                            "007_add_tour_equipment_catalog.sql",
                            "008_add_tour_cloudinary_public_id.sql",
                            "009_add_rental_package_templates.sql",
                            "010_abstract_tour_media_storage_key.sql",
                            "011_add_equipment_catalog_images.sql",
                            "012_define_rental_price_units.sql",
                            "013_add_payment_checkout_url.sql",
                            "014_equipment_inventory_operations.sql",
                            "015_policy_revocations.sql",
                            "016_tour_itinerary_days.sql",
                            "017_tour_terms.sql",
                            "018_policy_deleted.sql",
                            "019_schedule_details_updated.sql",
                            "020_tour_schedule_defaults.sql")) {
                        stmt.execute(Files.readString(Path.of("schema/migrations", migration)));
                    }

                    // Seed pre-021 legacy data across 4 tours:
                    // Tour 1: Unambiguous combo package (template unchanged since attachment) + piggybacked individual item + historical reservation & handover.
                    // Tour 2: Synthetic "Thuê dụng cụ lẻ" package WITH a historical reservation referencing package_id = 2.
                    // Tour 3: Combo package whose template WAS MODIFIED after being attached to Tour 3 (ambiguous legacy data - must NOT guess, must set needs_review = true).
                    // Tour 4: Synthetic "Thuê dụng cụ lẻ" package WITHOUT any reservation (wrapper row can be safely deleted after moving items).
                    stmt.execute("""
                            insert into trekking.app_accounts(email, password_hash, full_name) values
                                ('admin@example.invalid', 'hash', 'Admin'),
                                ('customer@example.invalid', 'hash', 'Customer');
                            insert into trekking.account_roles(account_id, role) values (1, 'admin'), (2, 'customer');
                            insert into trekking.policies(code, title, kind, created_by) values ('CANCEL', 'Cancel', 'cancellation', 1);
                            insert into trekking.policy_versions(policy_id, version_number, content, effective_from, approved_by, approved_at)
                                values (1, 1, 'Cancel policy', current_timestamp, 1, current_timestamp);
                            insert into trekking.tours(title, status, cancellation_policy_version_id, reviewed_by, reviewed_at)
                                values ('Tour 1 Unambiguous Combo + Individual', 'published', 1, 1, current_timestamp),
                                       ('Tour 2 Individual Only With Reservation', 'published', 1, 1, current_timestamp),
                                       ('Tour 3 Combo Template Modified Later', 'published', 1, 1, current_timestamp),
                                       ('Tour 4 Individual Only No Reservation', 'published', 1, 1, current_timestamp);
                            insert into trekking.tour_schedules(tour_id, starts_at, ends_at, capacity, base_price_vnd, deposit_percent, balance_due_at)
                                values (1, '2027-02-01T00:00:00Z', '2027-02-03T00:00:00Z', 10, 1000000, 50, '2027-01-25T00:00:00Z'),
                                       (2, '2027-02-05T00:00:00Z', '2027-02-07T00:00:00Z', 10, 1000000, 50, '2027-01-28T00:00:00Z');
                            insert into trekking.equipment(name, individual_rental_price_vnd) values
                                ('Legacy Tent', 150000),
                                ('Legacy Trekking Pole', 50000),
                                ('Legacy Sleeping Mat', 40000);

                            -- Template 1 (unchanged since Tour 1 attached it)
                            insert into trekking.rental_package_templates(title, rental_price_vnd, created_at, updated_at)
                                values ('Legacy Combo', 250000, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');
                            insert into trekking.rental_package_template_items(package_template_id, equipment_id, quantity_in_package, created_at, updated_at)
                                values (1, 1, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');

                            -- Template 2 (modified AFTER Tour 3 attached it: originally had Tent + Pole, later edited on 2026-06-01 to only have Tent)
                            insert into trekking.rental_package_templates(title, rental_price_vnd, created_at, updated_at)
                                values ('Evolving Combo', 280000, '2026-01-01T00:00:00Z', '2026-06-01T00:00:00Z');
                            insert into trekking.rental_package_template_items(package_template_id, equipment_id, quantity_in_package, created_at, updated_at)
                                values (2, 1, 1, '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z');

                            insert into trekking.tour_equipment_packages(tour_id, package_template_id, title, included_items, rental_price_vnd, created_at, updated_at)
                                values (1, 1, 'Legacy Combo', 'Legacy Tent × 1', 250000, '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
                                       (2, null, 'Thuê dụng cụ lẻ', '', null, '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
                                       (3, 2, 'Evolving Combo', 'Legacy Tent × 1, Legacy Trekking Pole × 1', 260000, '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
                                       (4, null, 'Thuê dụng cụ lẻ', '', null, '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z');

                            insert into trekking.tour_equipment_items(package_id, equipment_id, quantity_in_package, individual_rental_price_vnd)
                                values (1, 1, 1, 150000),
                                       (1, 2, 1, 50000),
                                       (2, 2, 1, 50000),
                                       (3, 1, 1, 150000),
                                       (3, 2, 1, 50000),
                                       (4, 3, 1, 40000);

                            insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id, participant_count,
                                includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent, cancellation_policy_version_id,
                                policy_snapshot, hold_expires_at, balance_due_at)
                                values ('BK-LEGACY-1', 'KEY-1', 2, 1, 2, true, 1000000, 250000, 50, 1, '{}', '2027-01-20T00:00:00Z', '2027-01-25T00:00:00Z'),
                                       ('BK-LEGACY-2', 'KEY-2', 2, 2, 1, true, 1000000, 50000, 50, 1, '{}', '2027-01-20T00:00:00Z', '2027-01-28T00:00:00Z');

                            insert into trekking.equipment_reservations(booking_id, schedule_id, package_id, selection_key, status,
                                participant_count, rental_fee_vnd, hold_expires_at, equipment_snapshot)
                                values (1, 1, 1, 'sel-1', 'returned', 2, 250000, '2027-01-20T00:00:00Z', '{"mode":"PACKAGE"}'::jsonb),
                                       (2, 2, 2, 'sel-2', 'confirmed', 1, 50000, '2027-01-20T00:00:00Z', '{"mode":"ITEMS"}'::jsonb);
                            insert into trekking.equipment_reservation_items(reservation_id, equipment_id, quantity, issued_quantity, returned_quantity)
                                values (1, 1, 1, 1, 1),
                                       (2, 2, 1, 0, 0);
                            insert into trekking.equipment_handover_records(reservation_id, action, actor_id, note)
                                values (1, 'issued', 1, 'Issued tent'),
                                       (1, 'returned', 1, 'Returned tent in good condition');
                            insert into trekking.equipment_handover_items(handover_record_id, equipment_id, quantity, condition)
                                values (1, 1, 1, 'good'),
                                       (2, 1, 1, 'good');
                            """);

                    // Apply migration 021
                    stmt.execute(Files.readString(Path.of("schema/migrations/021_multiple_tour_equipment_packages_and_items.sql")));

                    // 1. Verify Tour 1 (unambiguous): combo item stays in package 1, individual item separated to package_id IS NULL, needs_review = false
                    try (var rs = stmt.executeQuery("select active, needs_review from trekking.tour_equipment_packages where tour_id = 1 and id = 1")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getBoolean(1)).isTrue();
                        assertThat(rs.getBoolean(2)).isFalse();
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_items where tour_id = 1 and package_id = 1 and equipment_id = 1")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(1L);
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_items where tour_id = 1 and package_id is null and equipment_id = 2")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(1L);
                    }

                    // 2. Verify Tour 2 (synthetic wrapper referenced by reservation 2):
                    // Item moved to standalone (package_id IS NULL), wrapper row preserved with active = false so reservation 2's package_id = 2 stays intact!
                    try (var rs = stmt.executeQuery("select active, needs_review from trekking.tour_equipment_packages where tour_id = 2 and id = 2")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getBoolean(1)).isFalse();
                        assertThat(rs.getBoolean(2)).isFalse();
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_items where tour_id = 2 and package_id is null and equipment_id = 2")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(1L);
                    }
                    try (var rs = stmt.executeQuery("select package_id, status, rental_fee_vnd from trekking.equipment_reservations where id = 2")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(2L);
                        assertThat(rs.getString(2)).isEqualTo("confirmed");
                        assertThat(rs.getLong(3)).isEqualTo(50000L);
                    }

                    // 3. Verify Tour 3 (template modified after attachment -> ambiguous):
                    // Migration does NOT guess; both items remain under package_id = 3, rental_price_vnd = 260000 preserved, and needs_review = true!
                    try (var rs = stmt.executeQuery("select active, needs_review, rental_price_vnd from trekking.tour_equipment_packages where tour_id = 3 and id = 3")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getBoolean(1)).isTrue();
                        assertThat(rs.getBoolean(2)).isTrue();
                        assertThat(rs.getLong(3)).isEqualTo(260000L);
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_items where tour_id = 3 and package_id = 3")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(2L);
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_items where tour_id = 3 and package_id is null")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isZero();
                    }

                    // 4. Verify Tour 4 (synthetic wrapper without reservation):
                    // Wrapper row deleted cleanly, item preserved as standalone individual item
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_packages where tour_id = 4")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isZero();
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.tour_equipment_items where tour_id = 4 and package_id is null and equipment_id = 3")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(1L);
                    }

                    // 5. Verify historical reservation 1 and handover records remain 100% intact
                    try (var rs = stmt.executeQuery("select package_id, status, rental_fee_vnd from trekking.equipment_reservations where id = 1")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(1L);
                        assertThat(rs.getString(2)).isEqualTo("returned");
                        assertThat(rs.getLong(3)).isEqualTo(250000L);
                    }
                    try (var rs = stmt.executeQuery("select count(*) from trekking.equipment_handover_records where reservation_id = 1")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(2L);
                    }
                }
            } finally {
                try (var stmt = adminConn.createStatement()) {
                    stmt.execute("drop database " + database);
                }
            }
        }
    }
}
