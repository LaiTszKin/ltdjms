-- Flyway Migration V006
-- This migration fixes the product_id column to be nullable.
-- V005 added ON DELETE SET NULL but forgot to remove the NOT NULL constraint.
-- Version: V006

-- Make product_id nullable to support ON DELETE SET NULL behavior
ALTER TABLE redemption_code
ALTER COLUMN product_id DROP NOT NULL;
