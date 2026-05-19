-- AI Channel Restriction
-- 管理員可以設定 AI 功能僅在特定頻道使用

-- 建立資料表
CREATE TABLE IF NOT EXISTS ai_channel_restriction (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    channel_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, channel_id)
);

-- 索引：按伺服器查詢優化
CREATE INDEX IF NOT EXISTS idx_ai_channel_restriction_guild_id ON ai_channel_restriction(guild_id);

-- 觸發器：自動更新 updated_at
DROP TRIGGER IF EXISTS update_ai_channel_restriction_updated_at ON ai_channel_restriction;
CREATE TRIGGER update_ai_channel_restriction_updated_at
    BEFORE UPDATE ON ai_channel_restriction
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 註解
COMMENT ON TABLE ai_channel_restriction IS 'AI 頻道限制設定：管理員可以限制 AI 功能僅在特定頻道使用';
COMMENT ON COLUMN ai_channel_restriction.guild_id IS 'Discord 伺服器 ID';
COMMENT ON COLUMN ai_channel_restriction.channel_id IS 'Discord 頻道 ID';
COMMENT ON COLUMN ai_channel_restriction.channel_name IS '頻道名稱（冗餘儲存以便顯示）';
COMMENT ON COLUMN ai_channel_restriction.created_at IS '建立時間';
COMMENT ON COLUMN ai_channel_restriction.updated_at IS '更新時間';
