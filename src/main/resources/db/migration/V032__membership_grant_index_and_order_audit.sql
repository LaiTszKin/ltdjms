-- Membership tier follow-up: drop redundant grant index and add order audit column

DROP INDEX IF EXISTS idx_mtgl_user_period;

ALTER TABLE fiat_order ADD COLUMN IF NOT EXISTS membership_tier_at_order VARCHAR(16);

COMMENT ON COLUMN fiat_order.membership_tier_at_order IS
  'Membership tier applied at order creation for audit; populated by payment-discount flow.';
