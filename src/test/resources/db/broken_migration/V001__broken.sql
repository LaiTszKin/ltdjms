-- This migration is intentionally broken for testing purposes
CREATE TABLE broken_table (
    id BIGINT PRIMARY KEY,
    INVALID SYNTAX HERE
);
