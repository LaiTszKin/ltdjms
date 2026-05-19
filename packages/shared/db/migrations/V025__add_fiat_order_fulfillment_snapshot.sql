ALTER TABLE fiat_order
    ADD COLUMN IF NOT EXISTS fulfillment_reward_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS fulfillment_reward_amount BIGINT,
    ADD COLUMN IF NOT EXISTS fulfillment_auto_create_escort_order BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fulfillment_escort_option_code VARCHAR(120);

COMMENT ON COLUMN fiat_order.product_name IS
    'Buyer-facing display snapshot captured when the order was created.';
COMMENT ON COLUMN fiat_order.fulfillment_reward_type IS
    'Fulfillment contract reward type snapshot captured when the order was created.';
COMMENT ON COLUMN fiat_order.fulfillment_reward_amount IS
    'Fulfillment contract reward amount snapshot captured when the order was created.';
COMMENT ON COLUMN fiat_order.fulfillment_auto_create_escort_order IS
    'Fulfillment contract escort handoff flag captured when the order was created.';
COMMENT ON COLUMN fiat_order.fulfillment_escort_option_code IS
    'Fulfillment contract escort option snapshot captured when the order was created.';
