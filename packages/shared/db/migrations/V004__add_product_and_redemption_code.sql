-- Flyway Migration V004
-- This migration adds product and redemption code tables for the gift system.
-- Version: V004

-- Product table
-- Stores product definitions that can be redeemed with codes
CREATE TABLE IF NOT EXISTS product (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    reward_type VARCHAR(20),  -- NULL, 'CURRENCY', 'TOKEN'
    reward_amount BIGINT,     -- Reward amount, can be NULL if no automatic reward
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Each guild can have only one product with the same name
    CONSTRAINT product_guild_name_unique UNIQUE (guild_id, name),

    -- Ensure reward_amount is non-negative when specified
    CONSTRAINT product_reward_amount_non_negative CHECK (
        reward_amount IS NULL OR reward_amount >= 0
    ),

    -- Ensure reward_type is valid when specified
    CONSTRAINT product_reward_type_valid CHECK (
        reward_type IS NULL OR reward_type IN ('CURRENCY', 'TOKEN')
    ),

    -- If reward_type is specified, reward_amount must also be specified
    CONSTRAINT product_reward_consistency CHECK (
        (reward_type IS NULL AND reward_amount IS NULL) OR
        (reward_type IS NOT NULL AND reward_amount IS NOT NULL)
    )
);

-- Index for looking up products by guild
CREATE INDEX IF NOT EXISTS idx_product_guild
    ON product (guild_id);

-- Trigger for product updated_at
DROP TRIGGER IF EXISTS update_product_updated_at ON product;
CREATE TRIGGER update_product_updated_at
    BEFORE UPDATE ON product
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Redemption code table
-- Stores redemption codes for products
CREATE TABLE IF NOT EXISTS redemption_code (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    guild_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,  -- NULL means never expires
    redeemed_by BIGINT,                   -- User ID who redeemed, NULL if not redeemed
    redeemed_at TIMESTAMP WITH TIME ZONE, -- Redemption time, NULL if not redeemed
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Code must be globally unique to prevent cross-guild code guessing
    CONSTRAINT redemption_code_unique UNIQUE (code),

    -- Foreign key to product
    CONSTRAINT redemption_code_product_fk
        FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,

    -- Ensure redeemed_by and redeemed_at are both set or both null
    CONSTRAINT redemption_code_redemption_consistency CHECK (
        (redeemed_by IS NULL AND redeemed_at IS NULL) OR
        (redeemed_by IS NOT NULL AND redeemed_at IS NOT NULL)
    )
);

-- Index for looking up codes by guild
CREATE INDEX IF NOT EXISTS idx_redemption_code_guild
    ON redemption_code (guild_id);

-- Index for looking up codes by product
CREATE INDEX IF NOT EXISTS idx_redemption_code_product
    ON redemption_code (product_id);

-- Index for code lookup (most common operation during redemption)
CREATE INDEX IF NOT EXISTS idx_redemption_code_code
    ON redemption_code (code);

-- Index for finding unused codes by product (for admin view)
CREATE INDEX IF NOT EXISTS idx_redemption_code_product_unused
    ON redemption_code (product_id, redeemed_by) WHERE redeemed_by IS NULL;
