-- Destructive-change test schema v2: same table but drops the balance column.
CREATE TABLE IF NOT EXISTS test_accounts_v2 (
    id BIGINT PRIMARY KEY
);

