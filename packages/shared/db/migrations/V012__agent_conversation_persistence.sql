-- AI Agent 會話持久化功能
-- Migration: V012__agent_conversation_persistence.sql
-- Description: 建立會話與訊息持久化儲存結構，支援 Redis 快取與 PostgreSQL 混合存儲

-- AI Agent 會話表
-- 支援兩種會話範圍：訊息級別（一般頻道）與討論串級別（Discord Thread）
CREATE TABLE IF NOT EXISTS agent_conversation (
    conversation_id VARCHAR(255) PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    thread_id BIGINT,
    user_id BIGINT NOT NULL,
    original_message_id BIGINT NOT NULL,
    iteration_count INT NOT NULL DEFAULT 0,
    last_activity TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_iteration_count CHECK (iteration_count >= 0)
);

-- 索引：加速按伺服器查詢
CREATE INDEX IF NOT EXISTS idx_conversation_guild ON agent_conversation(guild_id);

-- 索引：加速按頻道查詢
CREATE INDEX IF NOT EXISTS idx_conversation_channel ON agent_conversation(channel_id);

-- 索引：加速按討論串查詢（僅索引非 NULL 值）
CREATE INDEX IF NOT EXISTS idx_conversation_thread ON agent_conversation(thread_id) WHERE thread_id IS NOT NULL;

-- 索引：加速按用戶查詢
CREATE INDEX IF NOT EXISTS idx_conversation_user ON agent_conversation(user_id);

-- 索引：加速按活動時間倒序查詢（最新優先）
CREATE INDEX IF NOT EXISTS idx_conversation_last_activity ON agent_conversation(last_activity DESC);

-- 複合索引：加速查詢用戶在特定頻道的活躍會話
CREATE INDEX IF NOT EXISTS idx_conversation_channel_user_activity
    ON agent_conversation(channel_id, user_id, last_activity DESC);

-- 觸發器：自動更新 updated_at 欄位
DROP TRIGGER IF EXISTS update_agent_conversation_updated_at ON agent_conversation;
CREATE TRIGGER update_agent_conversation_updated_at
    BEFORE UPDATE ON agent_conversation
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 註解：說明表用途
COMMENT ON TABLE agent_conversation IS 'AI Agent 會話表，支援頻道訊息級別和討論串級別的會話';
COMMENT ON COLUMN agent_conversation.thread_id IS '討論串 ID，NULL 表示一般頻道訊息（每則訊息獨立會話）';
COMMENT ON COLUMN agent_conversation.iteration_count IS '當前工具調用迭代次數';
COMMENT ON COLUMN agent_conversation.last_activity IS '最後活動時間，用於會話過期判斷';

-- AI 會話訊息表
-- 儲存會話中的所有對話訊息（用戶、AI 回應、工具執行結果）
CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL REFERENCES agent_conversation(conversation_id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tool_name VARCHAR(100),
    tool_parameters JSONB,
    tool_success BOOLEAN,
    tool_result TEXT,

    CONSTRAINT chk_role CHECK (role IN ('USER', 'ASSISTANT', 'TOOL')),
    CONSTRAINT chk_tool_info CHECK (
        role = 'TOOL' OR (tool_name IS NULL AND tool_parameters IS NULL AND tool_success IS NULL AND tool_result IS NULL)
    )
);

-- 索引：加速按會話查詢
CREATE INDEX IF NOT EXISTS idx_message_conversation ON agent_conversation_message(conversation_id);

-- 索引：加速按會話與時間順序查詢
CREATE INDEX IF NOT EXISTS idx_message_conversation_timestamp
    ON agent_conversation_message(conversation_id, timestamp ASC);

-- 索引：加速按角色查詢
CREATE INDEX IF NOT EXISTS idx_message_role ON agent_conversation_message(role);

-- 註解：說明表用途
COMMENT ON TABLE agent_conversation_message IS 'AI 會話訊息表，儲存會話中的所有對話訊息';
COMMENT ON COLUMN agent_conversation_message.role IS '訊息角色：USER（用戶）、ASSISTANT（AI 回應）、TOOL（工具執行結果）';
COMMENT ON COLUMN agent_conversation_message.tool_name IS '工具名稱，僅當 role=TOOL 時有值';
COMMENT ON COLUMN agent_conversation_message.tool_parameters IS '工具參數（JSONB 格式），僅當 role=TOOL 時有值';
COMMENT ON COLUMN agent_conversation_message.tool_success IS '工具執行是否成功，僅當 role=TOOL 時有值';
COMMENT ON COLUMN agent_conversation_message.tool_result IS '工具執行結果或錯誤訊息，僅當 role=TOOL 時有值';
