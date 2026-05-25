-- LTDJ management system Database Schema
-- Version: 1.0.0

-- Guild currency configuration table
CREATE TABLE IF NOT EXISTS guild_currency_config (
    guild_id BIGINT PRIMARY KEY,
    currency_name VARCHAR(50) NOT NULL DEFAULT 'Coins',
    currency_icon VARCHAR(64) NOT NULL DEFAULT '🪙',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Member currency account table
CREATE TABLE IF NOT EXISTS member_currency_account (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (guild_id, user_id),

    -- Ensure balance is never negative
    CONSTRAINT balance_non_negative CHECK (balance >= 0)
);

-- Index for looking up all accounts in a guild (useful for future features like leaderboards)
CREATE INDEX IF NOT EXISTS idx_member_currency_account_guild
    ON member_currency_account (guild_id);

-- Function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for guild_currency_config
DROP TRIGGER IF EXISTS update_guild_currency_config_updated_at ON guild_currency_config;
CREATE TRIGGER update_guild_currency_config_updated_at
    BEFORE UPDATE ON guild_currency_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for member_currency_account
DROP TRIGGER IF EXISTS update_member_currency_account_updated_at ON member_currency_account;
CREATE TRIGGER update_member_currency_account_updated_at
    BEFORE UPDATE ON member_currency_account
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Game token account table
CREATE TABLE IF NOT EXISTS game_token_account (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    tokens BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (guild_id, user_id),

    -- Ensure tokens is never negative
    CONSTRAINT tokens_non_negative CHECK (tokens >= 0)
);

-- Index for looking up all token accounts in a guild
CREATE INDEX IF NOT EXISTS idx_game_token_account_guild
    ON game_token_account (guild_id);

-- Trigger for game_token_account
DROP TRIGGER IF EXISTS update_game_token_account_updated_at ON game_token_account;
CREATE TRIGGER update_game_token_account_updated_at
    BEFORE UPDATE ON game_token_account
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Dice game 1 configuration table
CREATE TABLE IF NOT EXISTS dice_game1_config (
    guild_id BIGINT PRIMARY KEY,
    min_tokens_per_play BIGINT NOT NULL DEFAULT 1,
    max_tokens_per_play BIGINT NOT NULL DEFAULT 10,
    reward_per_dice_value BIGINT NOT NULL DEFAULT 250000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure tokens values are never negative
    CONSTRAINT dice_game1_min_tokens_non_negative CHECK (min_tokens_per_play >= 0),
    CONSTRAINT dice_game1_max_tokens_non_negative CHECK (max_tokens_per_play >= 0),
    CONSTRAINT dice_game1_reward_non_negative CHECK (reward_per_dice_value >= 0),
    CONSTRAINT dice_game1_min_max_order CHECK (min_tokens_per_play <= max_tokens_per_play)
);

-- Trigger for dice_game1_config
DROP TRIGGER IF EXISTS update_dice_game1_config_updated_at ON dice_game1_config;
CREATE TRIGGER update_dice_game1_config_updated_at
    BEFORE UPDATE ON dice_game1_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Dice game 2 configuration table
CREATE TABLE IF NOT EXISTS dice_game2_config (
    guild_id BIGINT PRIMARY KEY,
    min_tokens_per_play BIGINT NOT NULL DEFAULT 5,
    max_tokens_per_play BIGINT NOT NULL DEFAULT 50,
    straight_multiplier BIGINT NOT NULL DEFAULT 100000,
    base_multiplier BIGINT NOT NULL DEFAULT 20000,
    triple_low_bonus BIGINT NOT NULL DEFAULT 1500000,
    triple_high_bonus BIGINT NOT NULL DEFAULT 2500000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure tokens values are never negative
    CONSTRAINT dice_game2_min_tokens_non_negative CHECK (min_tokens_per_play >= 0),
    CONSTRAINT dice_game2_max_tokens_non_negative CHECK (max_tokens_per_play >= 0),
    CONSTRAINT dice_game2_straight_multiplier_non_negative CHECK (straight_multiplier >= 0),
    CONSTRAINT dice_game2_base_multiplier_non_negative CHECK (base_multiplier >= 0),
    CONSTRAINT dice_game2_triple_low_bonus_non_negative CHECK (triple_low_bonus >= 0),
    CONSTRAINT dice_game2_triple_high_bonus_non_negative CHECK (triple_high_bonus >= 0),
    CONSTRAINT dice_game2_min_max_order CHECK (min_tokens_per_play <= max_tokens_per_play)
);

-- Trigger for dice_game2_config
DROP TRIGGER IF EXISTS update_dice_game2_config_updated_at ON dice_game2_config;
CREATE TRIGGER update_dice_game2_config_updated_at
    BEFORE UPDATE ON dice_game2_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Game token transaction history table
CREATE TABLE IF NOT EXISTS game_token_transaction (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    source VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure balance_after is never negative
    CONSTRAINT game_token_transaction_balance_non_negative CHECK (balance_after >= 0)
);

-- Index for looking up transactions by user in a guild (most common query)
CREATE INDEX IF NOT EXISTS idx_game_token_transaction_guild_user
    ON game_token_transaction (guild_id, user_id, created_at DESC);

-- Index for looking up all transactions in a guild
CREATE INDEX IF NOT EXISTS idx_game_token_transaction_guild
    ON game_token_transaction (guild_id, created_at DESC);

-- Currency transaction history table
CREATE TABLE IF NOT EXISTS currency_transaction (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    source VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure balance_after is never negative
    CONSTRAINT currency_transaction_balance_non_negative CHECK (balance_after >= 0)
);

-- Index for looking up currency transactions by user in a guild (most common query)
CREATE INDEX IF NOT EXISTS idx_currency_transaction_guild_user
    ON currency_transaction (guild_id, user_id, created_at DESC);

-- Index for looking up all currency transactions in a guild
CREATE INDEX IF NOT EXISTS idx_currency_transaction_guild
    ON currency_transaction (guild_id, created_at DESC);

-- Global membership tier tracking per Discord user
CREATE TABLE IF NOT EXISTS global_member_membership (
    discord_user_id         BIGINT PRIMARY KEY,
    current_tier            VARCHAR(16) NOT NULL DEFAULT 'NONE',
    earliest_guild_join_at  TIMESTAMPTZ,
    settlement_day_of_month SMALLINT,
    last_settlement_at      TIMESTAMPTZ,
    next_settlement_at      TIMESTAMPTZ,
    has_qualifying_bronze_order BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_gmm_current_tier CHECK (
        current_tier IN ('NONE', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'BLACK')
    ),
    CONSTRAINT chk_gmm_settlement_day CHECK (
        settlement_day_of_month IS NULL
        OR (settlement_day_of_month >= 1 AND settlement_day_of_month <= 28)
    )
);

CREATE INDEX IF NOT EXISTS idx_gmm_next_settlement ON global_member_membership (next_settlement_at)
    WHERE next_settlement_at IS NOT NULL;

DROP TRIGGER IF EXISTS update_global_member_membership_updated_at
    ON global_member_membership;
CREATE TRIGGER update_global_member_membership_updated_at
    BEFORE UPDATE ON global_member_membership
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Membership spend ledger for escort fiat payments (catalog list price M)
CREATE TABLE IF NOT EXISTS membership_spend_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discord_user_id     BIGINT NOT NULL,
    guild_id            BIGINT NOT NULL,
    list_price_twd      BIGINT NOT NULL,
    escort_option_code  VARCHAR(64),
    source_type         VARCHAR(32) NOT NULL DEFAULT 'FIAT_ORDER',
    source_reference    VARCHAR(128) NOT NULL,
    paid_at             TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_type, source_reference)
);

CREATE INDEX IF NOT EXISTS idx_mse_user_paid ON membership_spend_entry (discord_user_id, paid_at);

CREATE TABLE IF NOT EXISTS membership_spend_retry (
    order_number            VARCHAR(128) PRIMARY KEY,
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count           INT NOT NULL DEFAULT 0,
    last_attempt_at         TIMESTAMPTZ,
    buyer_user_id           BIGINT,
    guild_id                BIGINT,
    paid_at                 TIMESTAMPTZ,
    order_list_price_twd    BIGINT,
    escort_option_code      VARCHAR(64),
    product_fiat_price_twd  BIGINT,
    escort_linked           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_msr_pending ON membership_spend_retry (status, created_at)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS membership_scheduler_lease (
    lock_name    VARCHAR(64) PRIMARY KEY,
    locked_until TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01T00:00:00Z',
    locked_by    VARCHAR(128) NOT NULL DEFAULT ''
);

-- Idempotent log for monthly membership token grants at settlement
CREATE TABLE IF NOT EXISTS membership_token_grant_log (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discord_user_id       BIGINT NOT NULL,
    settlement_period_end TIMESTAMPTZ NOT NULL,
    tier                  VARCHAR(16) NOT NULL,
    tokens_granted        INT NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'CLAIMED',
    tokens_adjusted       BOOLEAN NOT NULL DEFAULT FALSE,
    audit_recorded        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (discord_user_id, settlement_period_end)
);

CREATE INDEX IF NOT EXISTS idx_gmm_pending_grant_scan
    ON global_member_membership (last_settlement_at)
    WHERE last_settlement_at IS NOT NULL AND current_tier <> 'NONE';

-- fiat_order table with membership pricing audit columns (V030/V032)
CREATE TABLE IF NOT EXISTS fiat_order (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    payment_no VARCHAR(32) NOT NULL,
    amount_twd BIGINT NOT NULL,
    list_price_twd BIGINT,
    charged_amount_twd BIGINT,
    membership_tier_at_order VARCHAR(16),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    trade_status VARCHAR(32),
    payment_message VARCHAR(255),
    paid_at TIMESTAMPTZ,
    fulfilled_at TIMESTAMPTZ,
    admin_notified_at TIMESTAMPTZ,
    last_callback_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

