-- AI Category Restriction
-- 允許以「類別」層級設定可使用 AI 的範圍

CREATE TABLE IF NOT EXISTS ai_category_restriction (
    guild_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    category_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_category_restriction_guild_id
    ON ai_category_restriction(guild_id);

-- 觸發器：自動更新 updated_at
DROP TRIGGER IF EXISTS update_ai_category_restriction_updated_at ON ai_category_restriction;
CREATE TRIGGER update_ai_category_restriction_updated_at
    BEFORE UPDATE ON ai_category_restriction
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE ai_category_restriction IS 'AI 類別限制設定：管理員可允許整個類別使用 AI/Agent 功能';
COMMENT ON COLUMN ai_category_restriction.guild_id IS 'Discord 伺服器 ID';
COMMENT ON COLUMN ai_category_restriction.category_id IS 'Discord 類別 ID';
COMMENT ON COLUMN ai_category_restriction.category_name IS '類別名稱（冗餘儲存以便顯示）';
COMMENT ON COLUMN ai_category_restriction.created_at IS '建立時間';
COMMENT ON COLUMN ai_category_restriction.updated_at IS '更新時間';
