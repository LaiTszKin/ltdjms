-- Global membership tier tracking per Discord user

CREATE TABLE global_member_membership (
  discord_user_id         BIGINT PRIMARY KEY,
  current_tier            VARCHAR(16) NOT NULL DEFAULT 'NONE',
  earliest_guild_join_at  TIMESTAMPTZ,
  settlement_day_of_month SMALLINT,
  last_settlement_at      TIMESTAMPTZ,
  next_settlement_at      TIMESTAMPTZ,
  has_qualifying_bronze_order BOOLEAN NOT NULL DEFAULT FALSE,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_gmm_current_tier CHECK (
    current_tier IN ('NONE', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'BLACK')
  ),
  CONSTRAINT chk_gmm_settlement_day CHECK (
    settlement_day_of_month IS NULL
    OR (settlement_day_of_month >= 1 AND settlement_day_of_month <= 28)
  )
);

CREATE INDEX idx_gmm_next_settlement ON global_member_membership (next_settlement_at)
  WHERE next_settlement_at IS NOT NULL;

DROP TRIGGER IF EXISTS update_global_member_membership_updated_at
  ON global_member_membership;
CREATE TRIGGER update_global_member_membership_updated_at
  BEFORE UPDATE ON global_member_membership
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE global_member_membership IS
  'Global membership tier state keyed by Discord user ID.';
COMMENT ON COLUMN global_member_membership.settlement_day_of_month IS
  'Personal settlement day (1-28); days 29-31 map to 28 at application layer.';
