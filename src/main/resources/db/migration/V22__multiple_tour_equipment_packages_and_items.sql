-- Allow a tour to attach multiple rental package combos and multiple standalone individual equipment items.
-- Preserves existing tour equipment configurations, prices, and historical reservation/handover references without guessing.

SET LOCAL lock_timeout = '10s';

-- 1. Add optimistic concurrency version for tour equipment configuration on tours.
ALTER TABLE trekking.tours
    ADD COLUMN IF NOT EXISTS equipment_config_version BIGINT NOT NULL DEFAULT 0
        CHECK (equipment_config_version >= 0);

-- 2. Add active and needs_review flags on tour_equipment_packages:
--    - active = FALSE retains historical package rows referenced by equipment_reservations without violating ON DELETE RESTRICT.
--    - needs_review = TRUE marks legacy packages whose items could not be unambiguously separated because the template changed after attachment.
ALTER TABLE trekking.tour_equipment_packages
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS needs_review BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Link tour_equipment_items directly to tours so standalone individual items do not require a synthetic package row.
ALTER TABLE trekking.tour_equipment_items
    ADD COLUMN IF NOT EXISTS tour_id BIGINT REFERENCES trekking.tours(id) ON DELETE CASCADE;

UPDATE trekking.tour_equipment_items i
SET tour_id = p.tour_id
FROM trekking.tour_equipment_packages p
WHERE i.package_id = p.id
  AND i.tour_id IS NULL;

ALTER TABLE trekking.tour_equipment_items
    ALTER COLUMN tour_id SET NOT NULL;

ALTER TABLE trekking.tour_equipment_items
    ALTER COLUMN package_id DROP NOT NULL;

ALTER TABLE trekking.tour_equipment_items
    DROP CONSTRAINT IF EXISTS uq_tour_equipment_items_package_equipment;

-- 4. Unambiguously convert synthetic individual-only wrapper rows
--    (package_template_id IS NULL, rental_price_vnd IS NULL, title = 'Thuê dụng cụ lẻ', empty included_items).
UPDATE trekking.tour_equipment_items i
SET package_id = NULL
FROM trekking.tour_equipment_packages p
WHERE i.package_id = p.id
  AND p.package_template_id IS NULL
  AND p.rental_price_vnd IS NULL
  AND p.title = 'Thuê dụng cụ lẻ'
  AND btrim(p.included_items) = '';

-- Retain synthetic wrapper rows that are referenced by equipment_reservations (as active = FALSE) so historical package_id FKs stay intact.
UPDATE trekking.tour_equipment_packages p
SET active = FALSE
WHERE p.package_template_id IS NULL
  AND p.rental_price_vnd IS NULL
  AND p.title = 'Thuê dụng cụ lẻ'
  AND btrim(p.included_items) = ''
  AND EXISTS (
      SELECT 1
      FROM trekking.equipment_reservations r
      WHERE r.package_id = p.id
  );

DELETE FROM trekking.tour_equipment_packages p
WHERE p.package_template_id IS NULL
  AND p.rental_price_vnd IS NULL
  AND p.title = 'Thuê dụng cụ lẻ'
  AND btrim(p.included_items) = ''
  AND NOT EXISTS (
      SELECT 1
      FROM trekking.equipment_reservations r
      WHERE r.package_id = p.id
  );

-- 5. Separate extra individual items from template-backed packages ONLY when the template has NOT changed since
--    the package was attached to the tour (included_items snapshot matches current template items, timestamps are consistent,
--    all template items exist with matching quantities, and extra items are valid single-unit individual items).
WITH unambiguous_packages AS (
    SELECT p.id AS package_id, p.package_template_id
    FROM trekking.tour_equipment_packages p
    JOIN trekking.rental_package_templates t ON t.id = p.package_template_id
    WHERE p.active = TRUE
      AND p.package_template_id IS NOT NULL
      AND t.updated_at <= p.updated_at
      AND NOT EXISTS (
          SELECT 1
          FROM trekking.rental_package_template_items ti
          WHERE ti.package_template_id = p.package_template_id
            AND (ti.created_at > p.updated_at OR ti.updated_at > p.updated_at)
      )
      AND (
          SELECT COALESCE(string_agg(e.name || ' × ' || ti.quantity_in_package, ', ' ORDER BY ti.id), '')
          FROM trekking.rental_package_template_items ti
          JOIN trekking.equipment e ON e.id = ti.equipment_id
          WHERE ti.package_template_id = p.package_template_id
      ) = p.included_items
      AND NOT EXISTS (
          SELECT 1
          FROM trekking.rental_package_template_items ti
          WHERE ti.package_template_id = p.package_template_id
            AND NOT EXISTS (
                SELECT 1
                FROM trekking.tour_equipment_items i
                WHERE i.package_id = p.id
                  AND i.equipment_id = ti.equipment_id
                  AND i.quantity_in_package = ti.quantity_in_package
            )
      )
      AND NOT EXISTS (
          SELECT 1
          FROM trekking.tour_equipment_items i
          WHERE i.package_id = p.id
            AND NOT EXISTS (
                SELECT 1
                FROM trekking.rental_package_template_items ti
                WHERE ti.package_template_id = p.package_template_id
                  AND ti.equipment_id = i.equipment_id
            )
            AND (i.quantity_in_package <> 1 OR i.individual_rental_price_vnd IS NULL)
      )
)
UPDATE trekking.tour_equipment_items i
SET package_id = NULL
FROM unambiguous_packages up
WHERE i.package_id = up.package_id
  AND NOT EXISTS (
      SELECT 1
      FROM trekking.rental_package_template_items ti
      WHERE ti.package_template_id = up.package_template_id
        AND ti.equipment_id = i.equipment_id
  );

-- 6. For any remaining active package where the template was modified after attachment or items cannot be unambiguously
--    matched to a template, preserve all original tour_equipment_items under package_id and flag needs_review = TRUE.
UPDATE trekking.tour_equipment_packages p
SET needs_review = TRUE
WHERE p.active = TRUE
  AND (
      p.package_template_id IS NULL
      OR EXISTS (
          SELECT 1
          FROM trekking.rental_package_templates t
          WHERE t.id = p.package_template_id
            AND (
                t.updated_at > p.updated_at
                OR EXISTS (
                    SELECT 1
                    FROM trekking.rental_package_template_items ti
                    WHERE ti.package_template_id = p.package_template_id
                      AND (ti.created_at > p.updated_at OR ti.updated_at > p.updated_at)
                )
                OR (
                    SELECT COALESCE(string_agg(e.name || ' × ' || ti.quantity_in_package, ', ' ORDER BY ti.id), '')
                    FROM trekking.rental_package_template_items ti
                    JOIN trekking.equipment e ON e.id = ti.equipment_id
                    WHERE ti.package_template_id = p.package_template_id
                ) <> p.included_items
                OR EXISTS (
                    SELECT 1
                    FROM trekking.tour_equipment_items i
                    WHERE i.package_id = p.id
                      AND NOT EXISTS (
                          SELECT 1
                          FROM trekking.rental_package_template_items ti
                          WHERE ti.package_template_id = p.package_template_id
                            AND ti.equipment_id = i.equipment_id
                            AND ti.quantity_in_package = i.quantity_in_package
                      )
                )
                OR EXISTS (
                    SELECT 1
                    FROM trekking.rental_package_template_items ti
                    WHERE ti.package_template_id = p.package_template_id
                      AND NOT EXISTS (
                          SELECT 1
                          FROM trekking.tour_equipment_items i
                          WHERE i.package_id = p.id
                            AND i.equipment_id = ti.equipment_id
                            AND i.quantity_in_package = ti.quantity_in_package
                      )
                )
            )
      )
  );

-- Initialize equipment_config_version = 1 on tours that already have equipment configured.
UPDATE trekking.tours t
SET equipment_config_version = 1
WHERE t.equipment_config_version = 0
  AND (
      EXISTS (SELECT 1 FROM trekking.tour_equipment_packages p WHERE p.tour_id = t.id AND p.active = TRUE)
      OR EXISTS (SELECT 1 FROM trekking.tour_equipment_items i WHERE i.tour_id = t.id)
  );

-- 7. Allow multiple combo packages per tour while preventing duplicate active combo templates on the same tour.
ALTER TABLE trekking.tour_equipment_packages
    DROP CONSTRAINT IF EXISTS tour_equipment_packages_tour_id_key;

CREATE INDEX IF NOT EXISTS ix_tour_equipment_packages_tour_id
    ON trekking.tour_equipment_packages(tour_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tour_equipment_packages_tour_template
    ON trekking.tour_equipment_packages(tour_id, package_template_id)
    WHERE package_template_id IS NOT NULL AND active = TRUE;

-- 8. Enforce uniqueness per combo (package_id, equipment_id) and per tour's individual items (tour_id, equipment_id),
--    allowing the same equipment item to appear in combos AND as an individual rental option on the same tour.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tour_equipment_items_package_equipment
    ON trekking.tour_equipment_items(package_id, equipment_id)
    WHERE package_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tour_equipment_items_tour_individual_equipment
    ON trekking.tour_equipment_items(tour_id, equipment_id)
    WHERE package_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_tour_equipment_items_tour_id
    ON trekking.tour_equipment_items(tour_id);

CREATE INDEX IF NOT EXISTS ix_tour_equipment_items_package_id
    ON trekking.tour_equipment_items(package_id);

COMMENT ON COLUMN trekking.tours.equipment_config_version IS 'Phiên bản cấu hình thiết bị của tour để phát hiện ghi đè dữ liệu cũ (optimistic concurrency).';
COMMENT ON TABLE trekking.tour_equipment_packages IS 'Các gói combo dụng cụ gắn với tour; một tour có thể có nhiều gói combo.';
COMMENT ON COLUMN trekking.tour_equipment_packages.active IS 'TRUE nếu gói combo đang gắn với tour; FALSE nếu là bản ghi lịch sử được giữ lại cho equipment_reservations.';
COMMENT ON COLUMN trekking.tour_equipment_packages.needs_review IS 'TRUE nếu dữ liệu cũ trước migration chưa thể tự tách món lẻ do combo mẫu đã thay đổi và cần admin rà soát.';
COMMENT ON TABLE trekking.tour_equipment_items IS 'Các món trong gói combo của tour (package_id IS NOT NULL) hoặc món thuê lẻ trực tiếp của tour (package_id IS NULL).';
COMMENT ON COLUMN trekking.tour_equipment_items.tour_id IS 'Tour sở hữu dòng cấu hình thiết bị này.';
COMMENT ON COLUMN trekking.tour_equipment_items.package_id IS 'Gói combo của tour nếu dòng này thuộc combo; NULL nếu là thiết bị thuê lẻ của tour.';
