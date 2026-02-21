-- This migration is used by integration tests to validate custom migration locations.
CREATE TABLE IF NOT EXISTS broken_table (
    id BIGINT PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
