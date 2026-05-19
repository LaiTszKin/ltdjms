-- AI Agent Tools Integration
-- Migration: V011__ai_agent_tools.sql
-- Description: 建立頻道 Agent 配置表與工具執行日誌表

-- AI Agent 頻道配置表
-- 儲存哪些頻道啟用了 AI Agent 模式
CREATE TABLE IF NOT EXISTS ai_agent_channel_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL UNIQUE,
    agent_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_agent_enabled
        CHECK (agent_enabled IS NOT NULL)
);

-- 索引：加速按伺服器查詢
CREATE INDEX IF NOT EXISTS idx_agent_config_guild ON ai_agent_channel_config(guild_id);

-- 索引：加速按頻道查詢
CREATE INDEX IF NOT EXISTS idx_agent_config_channel ON ai_agent_channel_config(channel_id);

-- 索引：加速查詢啟用狀態
CREATE INDEX IF NOT EXISTS idx_agent_config_enabled ON ai_agent_channel_config(agent_enabled);

-- 複合索引：加速查詢伺服器中啟用的 Agent 頻道
CREATE INDEX IF NOT EXISTS idx_agent_config_guild_enabled
    ON ai_agent_channel_config(guild_id, agent_enabled);

-- 註解：說明表用途
COMMENT ON TABLE ai_agent_channel_config IS 'AI Agent 頻道配置表，儲存哪些頻道啟用了 AI Agent 模式';
COMMENT ON COLUMN ai_agent_channel_config.agent_enabled IS '是否啟用 AI Agent 模式';

-- 工具執行日誌表
-- 記錄所有 AI 工具調用的詳細資訊，用於審計和除錯
CREATE TABLE IF NOT EXISTS ai_tool_execution_log (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    trigger_user_id BIGINT NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    parameters JSONB,
    execution_result TEXT,
    error_message TEXT,
    status VARCHAR(20) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tool_status
        CHECK (status IN ('SUCCESS', 'FAILED')),

    CONSTRAINT chk_tool_name_length
        CHECK (LENGTH(tool_name) <= 100)
);

-- 索引：加速按伺服器查詢
CREATE INDEX IF NOT EXISTS idx_tool_log_guild ON ai_tool_execution_log(guild_id);

-- 索引：加速按頻道查詢
CREATE INDEX IF NOT EXISTS idx_tool_log_channel ON ai_tool_execution_log(channel_id);

-- 索引：加速按用戶查詢
CREATE INDEX IF NOT EXISTS idx_tool_log_user ON ai_tool_execution_log(trigger_user_id);

-- 索引：加速按時間倒序查詢（最新優先）
CREATE INDEX IF NOT EXISTS idx_tool_log_time ON ai_tool_execution_log(executed_at DESC);

-- 索引：加速按狀態查詢
CREATE INDEX IF NOT EXISTS idx_tool_log_status ON ai_tool_execution_log(status);

-- 複合索引：管理面板查詢（按伺服器與時間）
CREATE INDEX IF NOT EXISTS idx_tool_log_guild_time
    ON ai_tool_execution_log(guild_id, executed_at DESC);

-- 註解：說明表用途
COMMENT ON TABLE ai_tool_execution_log IS 'AI 工具執行日誌表，記錄所有工具調用的詳細資訊';
COMMENT ON COLUMN ai_tool_execution_log.parameters IS '工具參數（JSONB 格式）';
COMMENT ON COLUMN ai_tool_execution_log.status IS '執行狀態：SUCCESS 或 FAILED';
