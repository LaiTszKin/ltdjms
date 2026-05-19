-- Flyway Migration V007
-- This migration adds quantity field to redemption_code table.
-- Version: V007

-- Add quantity column with default value 1 for backward compatibility
ALTER TABLE redemption_code
ADD COLUMN IF NOT EXISTS quantity INT NOT NULL DEFAULT 1;

-- Add check constraint to ensure quantity is positive (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'redemption_code_quantity_positive'
    ) THEN
        ALTER TABLE redemption_code
        ADD CONSTRAINT redemption_code_quantity_positive CHECK (quantity > 0);
    END IF;
END $$;

-- Add check constraint to ensure reasonable maximum (1000) (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'redemption_code_quantity_max'
    ) THEN
        ALTER TABLE redemption_code
        ADD CONSTRAINT redemption_code_quantity_max CHECK (quantity <= 1000);
    END IF;
END $$;
