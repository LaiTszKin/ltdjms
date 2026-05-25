-- Grant saga status tracking and pending-grant scan index

ALTER TABLE membership_token_grant_log
  ADD COLUMN IF NOT EXISTS status VARCHAR(16),
  ADD COLUMN IF NOT EXISTS tokens_adjusted BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE membership_token_grant_log
SET status = 'COMPLETED', tokens_adjusted = TRUE
WHERE status IS NULL;

ALTER TABLE membership_token_grant_log
  ALTER COLUMN status SET NOT NULL,
  ALTER COLUMN status SET DEFAULT 'CLAIMED';

CREATE INDEX IF NOT EXISTS idx_gmm_pending_grant_scan
  ON global_member_membership (last_settlement_at)
  WHERE last_settlement_at IS NOT NULL AND current_tier <> 'NONE';

COMMENT ON COLUMN membership_token_grant_log.status IS
  'CLAIMED=in progress, COMPLETED=grant finished, FAILED=retryable partial failure';
COMMENT ON COLUMN membership_token_grant_log.tokens_adjusted IS
  'True when game tokens were credited before a downstream audit failure';
