-- Pending invitations stay visible to admins as disabled until the invitee activates the account.
UPDATE trekking.app_accounts account
SET status = 'disabled', updated_at = CURRENT_TIMESTAMP
WHERE account.status = 'active'
  AND account.email_verified_at IS NULL
  AND account.must_change_password = TRUE
  AND EXISTS (
      SELECT 1
      FROM trekking.audit_logs created
      WHERE created.entity_type = 'invitation'
        AND created.entity_id = account.id
        AND created.action = 'INVITATION_CREATED'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM trekking.audit_logs activated
      WHERE activated.entity_type = 'invitation'
        AND activated.entity_id = account.id
        AND activated.action = 'INVITATION_ACTIVATED'
  );
