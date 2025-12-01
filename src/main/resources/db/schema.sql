-- Discord Currency Bot Database Schema
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
    tokens_per_play BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure tokens_per_play is never negative
    CONSTRAINT tokens_per_play_non_negative CHECK (tokens_per_play >= 0)
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
    tokens_per_play BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure tokens_per_play is never negative
    CONSTRAINT dice_game2_tokens_per_play_non_negative CHECK (tokens_per_play >= 0)
);

-- Trigger for dice_game2_config
DROP TRIGGER IF EXISTS update_dice_game2_config_updated_at ON dice_game2_config;
CREATE TRIGGER update_dice_game2_config_updated_at
    BEFORE UPDATE ON dice_game2_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
