-- Flyway Migration V008
-- This migration creates the product_redemption_transaction table for recording all product redemption history.
-- Version: V008

-- Create product_redemption_transaction table
CREATE TABLE IF NOT EXISTS product_redemption_transaction (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    redemption_code VARCHAR(32) NOT NULL,
    quantity INT NOT NULL,
    reward_type VARCHAR(20),
    reward_amount BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create index for user-guild-created queries (for user transaction history with pagination)
CREATE INDEX IF NOT EXISTS idx_user_guild_created
ON product_redemption_transaction (user_id, guild_id, created_at DESC);

-- Create index for product queries (for admin product statistics)
CREATE INDEX IF NOT EXISTS idx_product
ON product_redemption_transaction (product_id);

-- Add check constraint to ensure quantity is positive
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_redemption_transaction_quantity_positive'
    ) THEN
        ALTER TABLE product_redemption_transaction
        ADD CONSTRAINT product_redemption_transaction_quantity_positive CHECK (quantity > 0);
    END IF;
END $$;

-- Add check constraint to ensure reasonable maximum (1000)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_redemption_transaction_quantity_max'
    ) THEN
        ALTER TABLE product_redemption_transaction
        ADD CONSTRAINT product_redemption_transaction_quantity_max CHECK (quantity <= 1000);
    END IF;
END $$;

-- Add comment for documentation
COMMENT ON TABLE product_redemption_transaction IS 'Records all product redemption transactions for user history and tracking';
COMMENT ON COLUMN product_redemption_transaction.reward_type IS 'Optional: CURRENCY or TOKEN, null if no automatic reward';
COMMENT ON COLUMN product_redemption_transaction.reward_amount IS 'Optional: Total amount rewarded (quantity * product reward amount)';
