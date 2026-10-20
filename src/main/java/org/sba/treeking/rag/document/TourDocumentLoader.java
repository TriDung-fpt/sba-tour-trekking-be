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
                tp.id,
                tp.title,
                tp.description,
                tp.duration_days,
                tp.price_per_person,
                tp.max_participants,
                tp.difficulty_level,
                tp.status,
                tp.cancellation_policy,
                tp.includes_equipment,
                c.name AS company_name
            FROM trekking.tour_packages tp
            JOIN trekking.companies c ON c.id = tp.company_id
            WHERE tp.status = 'active'
            ORDER BY tp.id
            """;

    private static final String LOAD_BY_ID_SQL = LOAD_ALL_SQL.replace(
            "WHERE tp.status = 'active'",
            "WHERE tp.status = 'active' AND tp.id = ?");

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
