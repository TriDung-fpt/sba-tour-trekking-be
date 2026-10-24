package org.sba.treeking.rag.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Loads tour data from the existing `trekking.tour_packages` table and converts
 * each row into a Spring AI {@link Document} with rich metadata.
 *
 * <p>The text content is a human-readable description combining the most
 * meaningful fields, which is what gets embedded and stored in pgvector.
 *
 * <p>One document per tour. For larger content (e.g. PDFs), use a
 * {@link org.springframework.ai.document.DocumentTransformer} to split into chunks.
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
                COALESCE(ts.base_price_vnd, 0) AS price_per_person,
                COALESCE(ts.capacity, 0) AS max_participants,
                COALESCE(t.difficulty, 'moderate') AS difficulty_level,
                COALESCE(pv.content, 'Standard cancellation policy') AS cancellation_policy,
                CASE WHEN t.included_services IS NOT NULL AND t.included_services != '' THEN true ELSE false END AS includes_equipment,
                c.name AS company_name
            FROM trekking.tours t
            JOIN trekking.companies c ON c.id = t.company_id
            LEFT JOIN trekking.policy_versions pv ON pv.id = t.cancellation_policy_version_id
            LEFT JOIN LATERAL (
                SELECT base_price_vnd, capacity
                FROM trekking.tour_schedules s
                WHERE s.tour_id = t.id AND s.status = 'scheduled'
                ORDER BY s.starts_at ASC
                LIMIT 1
            ) ts ON true
            WHERE t.status IN ('published', 'pending', 'draft')
            ORDER BY t.id
            """;

    private static final String LOAD_BY_ID_SQL = LOAD_ALL_SQL.replace(
            "WHERE t.status IN ('published', 'pending', 'draft')",
            "WHERE t.id = ?");

    /**
     * Load all active tours as documents.
     */
    public List<Document> loadAll() {
        log.info("Loading all active tours from database for ingestion");
        return jdbcTemplate.query(LOAD_ALL_SQL, (rs, rowNum) -> toDocument(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("duration_days"),
                rs.getLong("price_per_person"),
                rs.getInt("max_participants"),
                rs.getString("difficulty_level"),
                rs.getString("cancellation_policy"),
                rs.getBoolean("includes_equipment"),
                rs.getString("company_name")
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
                rs.getLong("price_per_person"),
                rs.getInt("max_participants"),
                rs.getString("difficulty_level"),
                rs.getString("cancellation_policy"),
                rs.getBoolean("includes_equipment"),
                rs.getString("company_name")
        ), tourId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Document toDocument(long id, String title, String description,
                                 int durationDays, long pricePerPerson,
                                 int maxParticipants, String difficultyLevel,
                                 String cancellationPolicy, boolean includesEquipment,
                                 String companyName) {

        // Build a rich text block – this is what the embedding model will process
        String content = buildContent(title, description, durationDays, pricePerPerson,
                maxParticipants, difficultyLevel, cancellationPolicy,
                includesEquipment, companyName);

        // Metadata is stored alongside the vector for filtering and display
        Map<String, Object> metadata = Map.of(
                "tour_id",          id,
                "title",            title != null ? title : "",
                "company",          companyName != null ? companyName : "",
                "difficulty",       difficultyLevel != null ? difficultyLevel : "",
                "duration_days",    durationDays,
                "price_per_person", pricePerPerson,
                "source",           "db:trekking.tour_packages"
        );

        return new Document(content, metadata);
    }

    private String buildContent(String title, String description, int durationDays,
                                 long pricePerPerson, int maxParticipants,
                                 String difficultyLevel, String cancellationPolicy,
                                 boolean includesEquipment, String companyName) {
        return """
                Tour: %s
                Offered by: %s
                Duration: %d days
                Price: %,d VND per person
                Max participants: %d
                Difficulty: %s
                Equipment included: %s
                Cancellation policy: %s
                
                Description:
                %s
                """.formatted(
                title,
                companyName,
                durationDays,
                pricePerPerson,
                maxParticipants,
                difficultyLevel,
                includesEquipment ? "Yes" : "No",
                cancellationPolicy != null ? cancellationPolicy : "See tour details",
                description != null ? description : ""
        );
    }
}
