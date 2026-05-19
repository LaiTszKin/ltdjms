-- Flyway Baseline Migration
-- This migration establishes the initial database schema for LTDJ Management System.
-- Version: V001 (Baseline)

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
    default_tokens_per_play BIGINT NOT NULL DEFAULT 1,
    reward_per_dice_value BIGINT NOT NULL DEFAULT 250000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure tokens values are never negative
    CONSTRAINT dice_game1_min_tokens_non_negative CHECK (min_tokens_per_play >= 0),
    CONSTRAINT dice_game1_max_tokens_non_negative CHECK (max_tokens_per_play >= 0),
    CONSTRAINT dice_game1_default_tokens_non_negative CHECK (default_tokens_per_play >= 0),
    CONSTRAINT dice_game1_reward_non_negative CHECK (reward_per_dice_value >= 0),
    CONSTRAINT dice_game1_min_max_order CHECK (min_tokens_per_play <= max_tokens_per_play),
    CONSTRAINT dice_game1_default_in_range CHECK (
        default_tokens_per_play >= min_tokens_per_play AND
        default_tokens_per_play <= max_tokens_per_play
    )
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
    default_tokens_per_play BIGINT NOT NULL DEFAULT 5,
    straight_multiplier BIGINT NOT NULL DEFAULT 100000,
    base_multiplier BIGINT NOT NULL DEFAULT 20000,
    triple_low_bonus BIGINT NOT NULL DEFAULT 1500000,
    triple_high_bonus BIGINT NOT NULL DEFAULT 2500000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure tokens values are never negative
    CONSTRAINT dice_game2_min_tokens_non_negative CHECK (min_tokens_per_play >= 0),
    CONSTRAINT dice_game2_max_tokens_non_negative CHECK (max_tokens_per_play >= 0),
    CONSTRAINT dice_game2_default_tokens_non_negative CHECK (default_tokens_per_play >= 0),
    CONSTRAINT dice_game2_straight_multiplier_non_negative CHECK (straight_multiplier >= 0),
    CONSTRAINT dice_game2_base_multiplier_non_negative CHECK (base_multiplier >= 0),
    CONSTRAINT dice_game2_triple_low_bonus_non_negative CHECK (triple_low_bonus >= 0),
    CONSTRAINT dice_game2_triple_high_bonus_non_negative CHECK (triple_high_bonus >= 0),
    CONSTRAINT dice_game2_min_max_order CHECK (min_tokens_per_play <= max_tokens_per_play),
    CONSTRAINT dice_game2_default_in_range CHECK (
        default_tokens_per_play >= min_tokens_per_play AND
        default_tokens_per_play <= max_tokens_per_play
    )
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
