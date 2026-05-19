-- Flyway Migration V005
-- This migration updates the product deletion behavior to handle associated redemption codes.
-- When a product is deleted, its redemption codes will be marked as invalidated instead of blocking deletion.
-- Version: V005

-- Add invalidated_at column to redemption_code table
ALTER TABLE redemption_code
ADD COLUMN IF NOT EXISTS invalidated_at TIMESTAMP WITH TIME ZONE;

-- Drop the existing foreign key constraint
ALTER TABLE redemption_code
DROP CONSTRAINT IF EXISTS redemption_code_product_fk;

-- Recreate the foreign key constraint with ON DELETE SET NULL
ALTER TABLE redemption_code
ADD CONSTRAINT redemption_code_product_fk
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE SET NULL;

-- Create index for querying invalidated codes
CREATE INDEX IF NOT EXISTS idx_redemption_code_invalidated
    ON redemption_code (invalidated_at) WHERE invalidated_at IS NOT NULL;
