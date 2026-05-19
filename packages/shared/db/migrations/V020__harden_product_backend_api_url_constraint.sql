-- Harden backend API URL constraints to reduce SSRF risk from local/private targets.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_backend_api_url_not_private_addr'
    ) THEN
        ALTER TABLE product
            ADD CONSTRAINT product_backend_api_url_not_private_addr
            CHECK (
                backend_api_url IS NULL
                OR (
                    -- localhost / loopback
                    backend_api_url !~* '^https?://localhost(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https?://127\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https?://\\[::1\\](:[0-9]+)?(/|$)'
                    -- unspecified and link-local IPv4
                    AND backend_api_url !~* '^https?://0\\.0\\.0\\.0(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https?://169\\.254\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    -- private IPv4 ranges
                    AND backend_api_url !~* '^https?://10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https?://192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https?://172\\.(1[6-9]|2[0-9]|3[0-1])\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                )
            );
    END IF;
END $$;
