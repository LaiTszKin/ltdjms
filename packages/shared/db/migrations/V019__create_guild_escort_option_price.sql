-- Guild-level escort option pricing overrides for dynamic order pricing.

CREATE TABLE IF NOT EXISTS guild_escort_option_price (
    guild_id BIGINT NOT NULL,
    option_code VARCHAR(120) NOT NULL,
    price_twd BIGINT NOT NULL,
    updated_by_user_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT guild_escort_option_price_pk PRIMARY KEY (guild_id, option_code),
    CONSTRAINT guild_escort_option_price_positive CHECK (price_twd > 0)
);

CREATE INDEX IF NOT EXISTS idx_guild_escort_option_price_guild
    ON guild_escort_option_price(guild_id);

DROP TRIGGER IF EXISTS update_guild_escort_option_price_updated_at
    ON guild_escort_option_price;
CREATE TRIGGER update_guild_escort_option_price_updated_at
    BEFORE UPDATE ON guild_escort_option_price
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE guild_escort_option_price IS
    'Guild-level override prices for escort order option codes.';
COMMENT ON COLUMN guild_escort_option_price.option_code IS
    'Escort option code from EscortOrderOptionCatalog.';
COMMENT ON COLUMN guild_escort_option_price.price_twd IS
    'Actual configured price in TWD.';
