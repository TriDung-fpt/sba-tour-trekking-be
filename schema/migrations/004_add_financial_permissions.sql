-- Apply after 003, before starting the application. No financial rights are
-- backfilled: each accountant must receive an explicit audited grant.
BEGIN;

ALTER TABLE trekking.app_accounts
    ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_accounts_authorization_version CHECK (authorization_version >= 0);

CREATE TABLE trekking.account_financial_permissions (
    account_id BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    permission TEXT NOT NULL CHECK (permission IN (
        'FINANCE_READ', 'PAYMENT_RECORD', 'REFUND_APPROVE', 'REFUND_RECORD',
        'SETTLEMENT_PREPARE', 'SETTLEMENT_APPROVE', 'PAYOUT_RECORD')),
    granted_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, permission)
);

CREATE FUNCTION trekking.validate_financial_permission() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    -- Serialize grants with each other and with account administration.
    PERFORM 1 FROM app_accounts WHERE id = NEW.account_id FOR UPDATE;
    IF NOT EXISTS (SELECT 1 FROM account_roles WHERE account_id = NEW.account_id AND role = 'accountant') THEN
        RAISE EXCEPTION 'Financial permission requires accountant role' USING ERRCODE = '23514';
    END IF;
    IF NEW.permission IN ('SETTLEMENT_PREPARE', 'SETTLEMENT_APPROVE', 'PAYOUT_RECORD')
       AND EXISTS (SELECT 1 FROM account_financial_permissions WHERE account_id = NEW.account_id
           AND permission IN ('SETTLEMENT_PREPARE', 'SETTLEMENT_APPROVE', 'PAYOUT_RECORD')
           AND permission <> NEW.permission) THEN
        RAISE EXCEPTION 'Settlement prepare, approve and payout duties must be separated' USING ERRCODE = '23514';
    END IF;
    IF NEW.permission IN ('REFUND_APPROVE', 'REFUND_RECORD')
       AND EXISTS (SELECT 1 FROM account_financial_permissions WHERE account_id = NEW.account_id
           AND permission IN ('REFUND_APPROVE', 'REFUND_RECORD') AND permission <> NEW.permission) THEN
        RAISE EXCEPTION 'Refund approval and recording duties must be separated' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_financial_permission_guard BEFORE INSERT OR UPDATE
ON trekking.account_financial_permissions FOR EACH ROW EXECUTE FUNCTION trekking.validate_financial_permission();

CREATE FUNCTION trekking.invalidate_role_challenges() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE app_accounts SET authorization_version = authorization_version + 1 WHERE id = OLD.account_id;
        IF OLD.role = 'accountant' THEN
            DELETE FROM account_financial_permissions WHERE account_id = OLD.account_id;
        END IF;
        RETURN OLD;
    END IF;
    UPDATE app_accounts SET authorization_version = authorization_version + 1 WHERE id = NEW.account_id;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.account_id <> NEW.account_id THEN
            UPDATE app_accounts SET authorization_version = authorization_version + 1 WHERE id = OLD.account_id;
        END IF;
        IF OLD.role = 'accountant' AND (NEW.role <> 'accountant' OR NEW.account_id <> OLD.account_id) THEN
            DELETE FROM account_financial_permissions WHERE account_id = OLD.account_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_account_role_challenges AFTER INSERT OR UPDATE OR DELETE
ON trekking.account_roles FOR EACH ROW EXECUTE FUNCTION trekking.invalidate_role_challenges();

COMMIT;
