-- Membership spend ledger for escort fiat payments (catalog list price M)

CREATE TABLE membership_spend_entry (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discord_user_id     BIGINT NOT NULL,
  guild_id            BIGINT NOT NULL,
  list_price_twd      BIGINT NOT NULL,
  escort_option_code  VARCHAR(64),
  source_type         VARCHAR(32) NOT NULL DEFAULT 'FIAT_ORDER',
  source_reference    VARCHAR(128) NOT NULL,
  paid_at             TIMESTAMPTZ NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (source_type, source_reference)
);

CREATE INDEX idx_mse_user_paid ON membership_spend_entry (discord_user_id, paid_at);

COMMENT ON TABLE membership_spend_entry IS
  'Global escort spend ledger keyed by payment source; list_price_twd is catalog M (not discounted).';

ALTER TABLE fiat_order ADD COLUMN list_price_twd BIGINT;
ALTER TABLE fiat_order ADD COLUMN charged_amount_twd BIGINT;

COMMENT ON COLUMN fiat_order.list_price_twd IS
  'Catalog list price M at order time; populated by payment-discount flow.';
COMMENT ON COLUMN fiat_order.charged_amount_twd IS
  'Discounted amount charged; populated by payment-discount flow.';
