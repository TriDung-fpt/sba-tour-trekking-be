package org.sba.treeking.rag.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads published tour data from the `trekking.tours` table along with its
 * schedules, locations, equipment packages, and policy version, then converts
 * each row into a Spring AI {@link Document} with rich metadata.
 *
 * <p>Compatible with the single-operator schema (no companies table).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourDocumentLoader {

    private final JdbcTemplate jdbcTemplate;

    private static final String LOAD_ALL_SQL = """
            SELECT
                t.id,
                t.title,
                t.description,
                t.duration_days,
                COALESCE(t.difficulty, 'moderate') AS difficulty_level,
                t.required_experience,
                t.included_services,
                t.excluded_services,
                COALESCE(ts.base_price_vnd, 0) AS price_per_person,
                COALESCE(ts.equipment_fee_vnd, 0) AS equipment_fee_vnd,
                COALESCE(ts.capacity, 0) AS max_participants,
                COALESCE(pv.content, 'Standard cancellation policy') AS cancellation_policy,
                COALESCE(locs.locations_summary, '') AS locations_summary,
                eq.title AS equipment_package_title,
                eq.included_items AS equipment_included,
                eq.personal_items_to_bring AS equipment_to_bring
            FROM trekking.tours t
            LEFT JOIN trekking.policy_versions pv ON pv.id = t.cancellation_policy_version_id
            LEFT JOIN LATERAL (
                SELECT base_price_vnd, equipment_fee_vnd, capacity
                FROM trekking.tour_schedules s
                WHERE s.tour_id = t.id AND s.status = 'scheduled' AND s.starts_at >= CURRENT_TIMESTAMP
                ORDER BY s.starts_at ASC
                LIMIT 1
            ) ts ON true
            LEFT JOIN LATERAL (
                SELECT string_agg(l.name || ' (' || l.province || ')', ', ' ORDER BY tl.stop_order) AS locations_summary
                FROM trekking.tour_locations tl
                JOIN trekking.locations l ON l.id = tl.location_id
                WHERE tl.tour_id = t.id
            ) locs ON true
            LEFT JOIN trekking.tour_equipment_packages eq ON eq.tour_id = t.id
            WHERE t.status = 'published'
            ORDER BY t.id
            """;

    private static final String LOAD_BY_ID_SQL = LOAD_ALL_SQL.replace(
            "WHERE t.status = 'published'",
            "WHERE t.id = ?");

    /**
     * Load all active published tours as documents.
     */
    public List<Document> loadAll() {
        log.info("Loading all published tours from database for ingestion");
        return jdbcTemplate.query(LOAD_ALL_SQL, (rs, rowNum) -> toDocument(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("duration_days"),
                rs.getString("difficulty_level"),
                rs.getString("required_experience"),
                rs.getString("included_services"),
                rs.getString("excluded_services"),
                rs.getLong("price_per_person"),
                rs.getLong("equipment_fee_vnd"),
                rs.getInt("max_participants"),
                rs.getString("cancellation_policy"),
                rs.getString("locations_summary"),
                rs.getString("equipment_package_title"),
                rs.getString("equipment_included"),
                rs.getString("equipment_to_bring")
        ));
    }

    /**
     * Load a single tour by its database ID.
     */
    public List<Document> loadById(Long tourId) {
        log.info("Loading tour {} from database for ingestion", tourId);
        return jdbcTemplate.query(LOAD_BY_ID_SQL, (rs, rowNum) -> toDocument(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("duration_days"),
                rs.getString("difficulty_level"),
                rs.getString("required_experience"),
                rs.getString("included_services"),
                rs.getString("excluded_services"),
                rs.getLong("price_per_person"),
                rs.getLong("equipment_fee_vnd"),
                rs.getInt("max_participants"),
                rs.getString("cancellation_policy"),
                rs.getString("locations_summary"),
                rs.getString("equipment_package_title"),
                rs.getString("equipment_included"),
                rs.getString("equipment_to_bring")
        ), tourId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Document toDocument(long id, String title, String description,
                                 int durationDays, String difficultyLevel,
                                 String requiredExperience, String includedServices,
                                 String excludedServices, long pricePerPerson,
                                 long equipmentFeeVnd, int maxParticipants,
                                 String cancellationPolicy, String locationsSummary,
                                 String equipmentPackageTitle, String equipmentIncluded,
                                 String equipmentToBring) {

        String content = buildContent(title, description, durationDays, difficultyLevel,
                requiredExperience, includedServices, excludedServices,
                pricePerPerson, equipmentFeeVnd, maxParticipants,
                cancellationPolicy, locationsSummary, equipmentPackageTitle,
                equipmentIncluded, equipmentToBring);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tour_id", id);
        metadata.put("title", title != null ? title : "");
        metadata.put("difficulty", difficultyLevel != null ? difficultyLevel : "");
        metadata.put("duration_days", durationDays);
        metadata.put("price_per_person", pricePerPerson);
        metadata.put("equipment_fee_vnd", equipmentFeeVnd);
        metadata.put("locations", locationsSummary != null ? locationsSummary : "");
        metadata.put("source", "db:trekking.tours");

        return new Document(content, metadata);
    }

    private String buildContent(String title, String description, int durationDays,
                                 String difficultyLevel, String requiredExperience,
                                 String includedServices, String excludedServices,
                                 long pricePerPerson, long equipmentFeeVnd,
                                 int maxParticipants, String cancellationPolicy,
                                 String locationsSummary, String equipmentPackageTitle,
                                 String equipmentIncluded, String equipmentToBring) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tour: ").append(title != null ? title : "").append("\n");
        if (locationsSummary != null && !locationsSummary.isBlank()) {
            sb.append("Locations / Route: ").append(locationsSummary).append("\n");
        }
        sb.append("Duration: ").append(durationDays).append(" days\n");
        if (pricePerPerson > 0) {
            sb.append(String.format("Price: %,d VND per person\n", pricePerPerson));
        }
        if (equipmentFeeVnd > 0) {
            sb.append(String.format("Equipment rental fee: %,d VND\n", equipmentFeeVnd));
        }
        if (maxParticipants > 0) {
            sb.append("Max participants: ").append(maxParticipants).append("\n");
        }
        sb.append("Difficulty: ").append(difficultyLevel != null ? difficultyLevel : "Moderate").append("\n");
        if (requiredExperience != null && !requiredExperience.isBlank()) {
            sb.append("Required experience: ").append(requiredExperience).append("\n");
        }
        if (includedServices != null && !includedServices.isBlank()) {
            sb.append("Included services: ").append(includedServices).append("\n");
        }
        if (excludedServices != null && !excludedServices.isBlank()) {
            sb.append("Excluded services: ").append(excludedServices).append("\n");
        }
        if (equipmentIncluded != null && !equipmentIncluded.isBlank()) {
            sb.append("Equipment provided: ").append(equipmentIncluded).append("\n");
        }
        if (equipmentToBring != null && !equipmentToBring.isBlank()) {
            sb.append("Personal items to bring: ").append(equipmentToBring).append("\n");
        }
        if (cancellationPolicy != null && !cancellationPolicy.isBlank()) {
            sb.append("Cancellation policy: ").append(cancellationPolicy).append("\n");
        }
        if (description != null && !description.isBlank()) {
            sb.append("\nDescription:\n").append(description).append("\n");
        }

        return sb.toString();
    }
}
