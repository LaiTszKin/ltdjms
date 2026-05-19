-- Add backend fulfillment integration fields and escort option config to product table.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'product' AND column_name = 'backend_api_url'
    ) THEN
        ALTER TABLE product ADD COLUMN backend_api_url VARCHAR(500);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'product' AND column_name = 'auto_create_escort_order'
    ) THEN
        ALTER TABLE product ADD COLUMN auto_create_escort_order BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'product' AND column_name = 'escort_option_code'
    ) THEN
        ALTER TABLE product ADD COLUMN escort_option_code VARCHAR(120);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_backend_api_url_http'
    ) THEN
        ALTER TABLE product
            ADD CONSTRAINT product_backend_api_url_http
            CHECK (
                backend_api_url IS NULL
                OR backend_api_url ~* '^https?://'
            );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_escort_option_requires_auto'
    ) THEN
        ALTER TABLE product
            ADD CONSTRAINT product_escort_option_requires_auto
            CHECK (
                (auto_create_escort_order = FALSE AND escort_option_code IS NULL)
                OR (auto_create_escort_order = TRUE AND escort_option_code IS NOT NULL)
            );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_auto_escort_requires_backend_api'
    ) THEN
        ALTER TABLE product
            ADD CONSTRAINT product_auto_escort_requires_backend_api
            CHECK (
                auto_create_escort_order = FALSE
                OR (backend_api_url IS NOT NULL AND length(trim(backend_api_url)) > 0)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_product_auto_escort_order
    ON product (guild_id, auto_create_escort_order)
    WHERE auto_create_escort_order = TRUE;

COMMENT ON COLUMN product.backend_api_url IS
    'Optional backend API URL called after product purchase for fulfillment.';
COMMENT ON COLUMN product.auto_create_escort_order IS
    'Whether purchase should trigger escort order creation through backend API.';
COMMENT ON COLUMN product.escort_option_code IS
    'Escort option code from built-in escort pricing catalog.';
