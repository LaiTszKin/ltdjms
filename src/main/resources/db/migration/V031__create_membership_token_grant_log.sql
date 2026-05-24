-- Idempotent log for monthly membership token grants at settlement

CREATE TABLE membership_token_grant_log (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discord_user_id       BIGINT NOT NULL,
  settlement_period_end TIMESTAMPTZ NOT NULL,
  tier                  VARCHAR(16) NOT NULL,
  tokens_granted        INT NOT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (discord_user_id, settlement_period_end)
);

CREATE INDEX idx_mtgl_user_period ON membership_token_grant_log (discord_user_id, settlement_period_end);

COMMENT ON TABLE membership_token_grant_log IS
  'Settlement token grant audit; UNIQUE (user, period_end) ensures idempotent monthly grants.';
