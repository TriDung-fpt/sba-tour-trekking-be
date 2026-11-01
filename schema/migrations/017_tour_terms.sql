BEGIN;
CREATE TABLE trekking.tour_terms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES trekking.tours(id) ON DELETE CASCADE,
    policy_version_id BIGINT NOT NULL REFERENCES trekking.policy_versions(id),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    UNIQUE(tour_id, policy_version_id),
    UNIQUE(tour_id, display_order)
);
COMMIT;
