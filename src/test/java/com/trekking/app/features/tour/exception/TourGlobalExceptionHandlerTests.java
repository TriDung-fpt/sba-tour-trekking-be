package com.trekking.app.features.tour.exception;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class TourGlobalExceptionHandlerTests {
    @ParameterizedTest
    @CsvSource({
            "tours_check, TOUR_CANNOT_BE_PUBLISHED, status",
            "tour_activities_tour_id_day_number_display_order_key, ACTIVITY_ORDER_CONFLICT, displayOrder",
            "tour_activities_location_id_fkey, INVALID_LOCATION, locationId",
            "uq_tour_cover, TOUR_COVER_CONFLICT, mediaId",
            "uq_tour_media_cloudinary_public_id, TOUR_IMAGE_CONFLICT, file",
            "tours_cancellation_policy_version_id_fkey, INVALID_CANCELLATION_POLICY, cancellationPolicyVersionId",
            "tours_reviewed_by_fkey, INVALID_TOUR_REVIEWER, reviewedBy"
    })
    void knownConstraintsReturnSafeFieldErrors(String constraint, String code, String field) {
        var cause = new ConstraintViolationException("private SQL", new SQLException("private record data"), constraint);
        var response = new TourGlobalExceptionHandler()
                .conflict(new DataIntegrityViolationException("private database details", cause));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().fieldErrors()).containsKey(field);
        assertThat(response.getBody().toString()).doesNotContain("private", constraint);
    }
}
