ALTER TABLE fiat_order
    ADD COLUMN IF NOT EXISTS fulfillment_processing_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS admin_notification_processing_at TIMESTAMPTZ;
