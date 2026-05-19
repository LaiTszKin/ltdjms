-- 護航選項目錄：取代硬編碼的 EscortOrderOptionCatalog

CREATE TABLE IF NOT EXISTS escort_option_catalog (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(120) NOT NULL,
    type        VARCHAR(64)  NOT NULL,
    level       VARCHAR(64)  NOT NULL,
    map_scope   VARCHAR(256) NOT NULL,
    target      VARCHAR(256) NOT NULL,
    price_twd   BIGINT       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_escort_option_catalog_code UNIQUE (code),
    CONSTRAINT chk_escort_option_catalog_price_positive CHECK (price_twd > 0)
);

INSERT INTO escort_option_catalog (code, type, level, map_scope, target, price_twd)
VALUES
    -- 包本單
    ('CONF_DAM_300W', '包本單', '機密護', '機密大壩', '300 萬目標', 500),
    ('CONF_DAM_600W', '包本單', '機密護', '機密大壩', '600 萬目標', 1100),
    ('CONF_SPACE_300W', '包本單', '機密護', '機密航天', '300 萬目標', 600),
    ('CONF_SPACE_500W', '包本單', '機密護', '機密航天', '500 萬目標', 1200),
    ('SECRET_SPACE_400W', '包本單', '絕密護', '絕密航天', '400 萬目標', 800),
    ('SECRET_SPACE_700W', '包本單', '絕密護', '絕密航天', '700 萬目標', 1500),
    -- 小時單
    ('CONF_HOURLY_1H', '小時單', '機密護', '機密大壩、機密航天', '1 小時', 600),
    ('CONF_HOURLY_2H', '小時單', '機密護', '機密大壩、機密航天', '2 小時', 1300),
    ('SECRET_HOURLY_1H', '小時單', '絕密護', '絕密航天、絕密巴克什、監獄', '1 小時', 800),
    ('SECRET_HOURLY_2H', '小時單', '絕密護', '絕密航天、絕密巴克什、監獄', '2 小時', 1800),
    -- 哈夫幣累積單
    ('HAVOC_3000W', '哈夫幣累積單', '不限', '不限', '3,000 萬哈夫幣', 3500),
    ('HAVOC_6000W', '哈夫幣累積單', '不限', '不限', '6,000 萬哈夫幣', 5500),
    ('HAVOC_8000W', '哈夫幣累積單', '不限', '不限', '8,000 萬哈夫幣', 10000),
    -- 特殊單 / 指定大紅
    ('SPECIAL_SAFEBOX50', '特殊條件', '不限', '不限', '不開夠 50 個保險箱不結單', 4888),
    ('RED_MANDEL_UNIT', '指定大紅', '不限', '不限', '曼德爾超算單元', 6888),
    ('RED_PORTABLE_RADAR', '指定大紅', '不限', '不限', '便攜軍用雷達', 6888),
    ('RED_SECRET_SERVER', '指定大紅', '不限', '不限', '絕密伺服器', 7000),
    ('RED_AED', '指定大紅', '不限', '不限', '自動體外心臟去顫器', 7500),
    ('RED_ARMORED_BATTERY', '指定大紅', '不限', '不限', '裝甲車電池', 8000),
    ('RED_LAPTOP', '指定大紅', '不限', '不限', '筆記型電腦', 8200),
    ('RED_GOLDEN_TEAR_CROWN', '指定大紅', '不限', '不限', '萬金淚冠', 8500),
    ('RED_ZONGHENG', '指定大紅', '不限', '不限', '縱橫', 9000),
    ('RED_MICRO_REACTOR', '指定大紅', '不限', '不限', '微型反應爐', 10000),
    ('RED_RESPIRATOR', '指定大紅', '不限', '不限', '復甦呼吸機', 19999),
    ('RED_HEART_FOREVER', '指定大紅', '不限', '不限', '洲之所向，真心永傳', 30000),
    ('RED_TIDE_TEAR', '指定大紅', '不限', '不限', '人魚隱沒，潮汐之淚', 35000)
ON CONFLICT (code) DO NOTHING;

DROP TRIGGER IF EXISTS update_escort_option_catalog_updated_at
    ON escort_option_catalog;
CREATE TRIGGER update_escort_option_catalog_updated_at
    BEFORE UPDATE ON escort_option_catalog
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE escort_option_catalog IS
    '全域護航選項目錄，取代原本硬編碼的 EscortOrderOptionCatalog。管理員可在此新增、編輯、刪除護航項目。';
COMMENT ON COLUMN escort_option_catalog.code IS
    '護航選項代碼（唯一），例如 CONF_DAM_300W';
COMMENT ON COLUMN escort_option_catalog.type IS
    '訂單類型：包本單、小時單、哈夫幣累積單、特殊條件、指定大紅';
COMMENT ON COLUMN escort_option_catalog.level IS
    '服務範圍級別：機密護、絕密護、不限';
COMMENT ON COLUMN escort_option_catalog.map_scope IS
    '適用地圖範圍，例如機密大壩、絕密航天';
COMMENT ON COLUMN escort_option_catalog.target IS
    '目標說明，例如 300 萬目標、1 小時';
COMMENT ON COLUMN escort_option_catalog.price_twd IS
    '預設價格（新台幣），公會可透過 guild_escort_option_price 覆蓋';
