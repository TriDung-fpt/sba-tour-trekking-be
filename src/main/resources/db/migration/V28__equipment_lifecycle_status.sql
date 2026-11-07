-- Preserve catalog deletions and map previously inactive stock to maintenance.
-- A positive maintenance quantity alone does not mean all stock is in maintenance.
ALTER TABLE trekking.equipment ADD COLUMN status TEXT NOT NULL DEFAULT 'active';
UPDATE trekking.equipment e SET status = CASE
    WHEN e.deleted THEN 'deleted'
    WHEN EXISTS (SELECT 1 FROM trekking.equipment_inventory i WHERE i.equipment_id = e.id AND NOT i.is_active) THEN 'maintenance'
    ELSE 'active'
END;
ALTER TABLE trekking.equipment ADD CONSTRAINT ck_equipment_status CHECK (status IN ('active', 'maintenance', 'deleted'));
ALTER TABLE trekking.equipment DROP COLUMN deleted;
