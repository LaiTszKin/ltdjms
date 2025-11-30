-- Destructive-change test schema v1: table with two columns.
CREATE TABLE IF NOT EXISTS test_accounts_v2 (
    id BIGINT PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0
);

