-- Spend retry queue for best-effort membership ledger writes
CREATE TABLE membership_spend_retry (
    order_number   VARCHAR(128) PRIMARY KEY,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count  INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_msr_pending ON membership_spend_retry (status, created_at)
    WHERE status = 'PENDING';

COMMENT ON TABLE membership_spend_retry IS
    'Retry queue when membership spend recording fails during fiat fulfillment';

-- Grant saga audit idempotency flag
ALTER TABLE membership_token_grant_log
  ADD COLUMN IF NOT EXISTS audit_recorded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE membership_token_grant_log
SET audit_recorded = TRUE
WHERE status = 'COMPLETED' AND audit_recorded = FALSE;

COMMENT ON COLUMN membership_token_grant_log.audit_recorded IS
    'True when game token transaction audit row was persisted for this grant';
