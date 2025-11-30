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
