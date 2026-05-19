-- Enforce HTTPS-only backend fulfillment URLs and block additional non-public literal targets.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_backend_api_url_http'
    ) THEN
        ALTER TABLE product DROP CONSTRAINT product_backend_api_url_http;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_backend_api_url_not_private_addr'
    ) THEN
        ALTER TABLE product DROP CONSTRAINT product_backend_api_url_not_private_addr;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_backend_api_url_https'
    ) THEN
        ALTER TABLE product
            ADD CONSTRAINT product_backend_api_url_https
            CHECK (
                backend_api_url IS NULL
                OR backend_api_url ~* '^https://'
            );
    END IF;

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
                    backend_api_url !~* '^https://localhost(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://127\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://0\\.0\\.0\\.0(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://169\\.254\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://172\\.(1[6-9]|2[0-9]|3[0-1])\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://198\\.(18|19)\\.[0-9]{1,3}\\.[0-9]{1,3}(:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://\\[(::|0:0:0:0:0:0:0:0|::1|0:0:0:0:0:0:0:1)\\](:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://\\[(fc|fd)[0-9a-f:]*\\](:[0-9]+)?(/|$)'
                    AND backend_api_url !~* '^https://\\[fe[89ab][0-9a-f:]*\\](:[0-9]+)?(/|$)'
                )
            );
    END IF;
END $$;
