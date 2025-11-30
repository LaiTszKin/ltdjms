-- Test schema v2: adds a non-destructive column with default value.
CREATE TABLE IF NOT EXISTS test_accounts_v1 (
    id BIGINT PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0
);

