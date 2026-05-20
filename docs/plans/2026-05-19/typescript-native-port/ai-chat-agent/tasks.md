# Tasks: AI Chat and Agent

- Date: 2026-05-19
- Feature: AI Chat and Agent

## **Task 1: 型別定義與 AI 服務配置**

Purpose: 定義 aichat / aiagent / markdown 模組需要的所有型別、介面、enum
Requirements: R2, R5, R9, R14
Scope: `packages/ai/src/types/`, `packages/ai/src/aichat/types/`, `packages/ai/src/aiagent/types/`, `packages/ai/src/markdown/types/`
Out of scope: 實作邏輯（僅定義型別）

- T1.1 [ ] **`packages/ai/src/aichat/types.ts`** — 定義 AIServiceConfig type（對應 Java AIServiceConfig record）
  - fields: baseUrl, apiKey, model, temperature, timeoutSeconds, showReasoning, enableMarkdownValidation, streamingBypassValidation
  - `AIServiceConfig.from(env: EnvironmentConfig)` 靜態工廠方法
  - `validate(): Result<void, DomainError>` 驗證方法
  - Verify: `tsc --noEmit` 通過，型別與 Java record 欄位完全一致

- T1.2 [ ] **`packages/ai/src/aichat/types.ts`** — 定義 AllowedChannel、AllowedCategory、AIChannelRestriction 型別
  - AllowedChannel: { guildId: string, channelId: string, channelName: string }
  - AllowedCategory: { guildId: string, categoryId: string, categoryName: string }
  - AIChannelRestriction: { channels: AllowedChannel[], categories: AllowedCategory[] }
  - Verify: 型別與 Java domain objects 欄位一致

- T1.3 [ ] **`packages/ai/src/aichat/types.ts`** — 定義 StreamingResponseHandler 介面與 ChunkType enum
  - ChunkType: REASONING / TOOL_INTENT / CONTENT
  - `onChunk(chunk: string, isComplete: boolean, error: DomainError | null, type: ChunkType): void`
  - 保留向後相容的三參數 overload `onChunk(chunk: string, isComplete: boolean, error: DomainError | null)`
  - Verify: 介面簽名與 Java StreamingResponseHandler 一致

- T1.4 [ ] **`packages/ai/src/aichat/types.ts`** — 定義 SystemPrompt、PromptSection 型別
  - PromptSection: { name: string, content: string }
  - SystemPrompt: sections: PromptSection[]; `toCombinedString(): string`; `static empty(): SystemPrompt`
  - Verify: `SystemPrompt.empty().toCombinedString()` 回傳空字串

- T1.5 [ ] **`packages/ai/src/aiagent/types.ts`** — 定義 ToolDefinition、ToolParameter、PermissionSetting、ModifyPermissionSetting、RoleCreateInfo 型別
  - ToolDefinition: { name, description, parameters: ToolParameter[] }
  - ToolParameter: { name, type: 'STRING' | 'NUMBER' | 'ARRAY', description, required, defaultValue }
  - 必須與 Java AIAgentTools.java 中的 17 個工具定義完全一致
  - Verify: 逐一比對每個 ToolDefinition 的 name、description、parameters

- T1.6 [ ] **`packages/ai/src/aiagent/types.ts`** — 定義 AgentConversation、ConversationMessage、MessageRole、ToolCallInfo、ToolExecutionResult 型別
  - ConversationMessage: { role: MessageRole, content, timestamp?, toolCall?, reasoningContent? }
  - MessageRole: USER / AI / SYSTEM / TOOL
  - Verify: 欄位與 Java domain objects 一致

- T1.7 [ ] **`packages/ai/src/markdown/types.ts`** — 定義 ErrorType enum、MarkdownError、ValidationResult
  - ErrorType: HEADING_LEVEL_EXCEEDED / HEADING_FORMAT / HEADING_CONTAINS_LIST_MARKER / MALFORMED_LIST / MALFORMED_NESTED_LIST / UNCLOSED_CODE_BLOCK / DISCORD_RENDER_ISSUE / INLINE_HEADING
  - MarkdownError: { errorType, line, column, context (max 50 chars), suggestion }
  - ValidationResult = Valid { content: string } | Invalid { errors: MarkdownError[] }
  - Verify: enum 值與 Java ErrorType 完全一致

- T1.8 [ ] **`packages/ai/src/index.ts`** — 從各子模組 re-export 所有 public types
  - Verify: `import { AIServiceConfig, ChunkType, ToolDefinition, ValidationResult } from '@ltdjms/ai'` 可正確解析

## **Task 2: AI 頻道限制管理與持久化**

Purpose: 實作 AIChannelRestrictionService、AIChannelRestrictionRepository 及其 Drizzle schema
Requirements: R2
Scope: `packages/ai/src/aichat/services/`, `packages/ai/src/aichat/persistence/`
Out of scope: 路由決策（Task 3）、Admin panel 設定 UI（administration spec）

- T2.1 [ ] **`packages/ai/src/aichat/persistence/schema.ts`** — 定義 Drizzle schema: `aiAllowedChannels`、`aiAllowedCategories` 資料表
  - 欄位與現有 Flyway migration SQL 一致
  - unique constraint on (guildId, channelId) / (guildId, categoryId)
  - Verify: `drizzle-kit generate` 產出的 SQL 與 Java migration 建立的表格一致

- T2.2 [ ] **`packages/ai/src/aichat/persistence/AIChannelRestrictionRepository.ts`** — Drizzle ORM 實作
  - `findByGuildId(guildId)` → AllowedChannel[]
  - `findRestrictionByGuildId(guildId)` → AIChannelRestriction
  - `findAllowedCategories(guildId)` → AllowedCategory[]
  - `addChannel(guildId, channel)` → Result<AllowedChannel>
  - `addCategory(guildId, category)` → Result<AllowedCategory>
  - `removeChannel(guildId, channelId)` → Result<void>
  - `removeCategory(guildId, categoryId)` → Result<void>
  - `deleteRemovedChannels(guildId, validChannelIds)` → void
  - Verify: 整合測試對真實 PostgreSQL 執行所有 CRUD 操作，確認 unique constraint 正確觸發

- T2.3 [ ] **`packages/ai/src/aichat/services/AIChannelRestrictionService.ts`** — 介面定義
  - `isChannelAllowed(guildId, channelId, categoryId?): boolean`
  - `getAllowedChannels(guildId): Result<AllowedChannel[]>`
  - `getAllowedCategories(guildId): Result<AllowedCategory[]>`
  - `addAllowedChannel(guildId, channel): Result<AllowedChannel>`
  - `addAllowedCategory(guildId, category): Result<AllowedCategory>`
  - `removeAllowedChannel(guildId, channelId): Result<void>`
  - `removeAllowedCategory(guildId, categoryId): Result<void>`
  - Verify: 介面簽名與 Java AIChannelRestrictionService.java 一致

- T2.4 [ ] **`packages/ai/src/aichat/services/DefaultAIChannelRestrictionService.ts`** — 預設實作
  - isChannelAllowed: 先查頻道白名單，未命中再查分類白名單
  - 空白名單 → 回傳 false（預設拒絕）
  - 重複新增/移除不存在項目 → DomainError
  - Verify: 單元測試覆蓋所有分支（含空白名單、頻道命中、分類命中、重複、不存在）

## **Task 3: AI 提及路由決策**

Purpose: 實作三層優先級路由矩陣
Requirements: R1
Scope: `packages/ai/src/aichat/routing/`
Out of scope: MessageListener（Task 4）、Agent config（Task 7，此處僅介面依賴）

- T3.1 [ ] **`packages/ai/src/aichat/routing/AIChatMentionRoutingDecision.ts`** — 路由決策邏輯
  - `decide(guildId, channelId, restrictionChannelId, categoryId): Decision`
  - Decision: { route: Route, source: Source, detail?: string }
  - Route enum: AGENT_ROUTE / AI_CHAT_ROUTE / DENY
  - Source enum: AGENT_CONFIG / CHANNEL_ALLOWLIST / CATEGORY_ALLOWLIST / AGENT_CONFIG_UNAVAILABLE / NO_ALLOWLIST
  - 優先級: AGENT_ROUTE > AI_CHAT_ROUTE > DENY
  - Thread 繼承父頻道: 傳入 restrictionChannelId 已由 listener 解析（resolveRestrictionChannelId）
  - Verify: 參數化單元測試覆蓋所有 3x5=15 種決策組合

- T3.2 [ ] **`packages/ai/src/aichat/routing/utils.ts`** — 輔助函數
  - `resolveRestrictionChannelId(channel)` — Thread → 父頻道 ID
  - `resolveCategoryId(channel, guild)` — 取得頻道所屬分類 ID（Thread → 父頻道 → 分類）
  - Verify: 單元測試使用 mock channel objects（Thread / Text / Category）

## **Task 4: AI 提及監聽器與串流回應顯示**

Purpose: 實作 AIChatMentionListener — @bot 提及的完整處理流程
Requirements: R3, R4
Scope: `packages/ai/src/aichat/listener/`
Out of scope: AIChatService 實作（Task 5）、Agent 工具實作（Task 8）

- T4.1 [ ] **`packages/ai/src/aichat/listener/ReasoningMessageTracker.ts`** — 追蹤並刪除所有 reasoning 訊息
  - setInitialMessage(message) / addReasoningMessage(message) / deleteAll(callback)
  - 支援 `deletionRequested` flag 避免 race condition
  - deleteAll 使用 Promise.all 等待所有刪除完成後呼叫 callback
  - Verify: 單元測試模擬 message.delete() 成功/失敗情境

- T4.2 [ ] **`packages/ai/src/aichat/listener/AIChatMentionListener.ts`** — 主監聽器
  - `onMessageCreate(message)` — discord.js event handler
  - 過濾: bot 訊息、DM、非 @bot 提及
  - @bot 提及但無其他文字 → 使用「你好」預設訊息
  - 取得 routing decision → DENY 時 silent return（debug log）
  - AGENT_ROUTE → handleAgentStreamingResponse()
  - AI_CHAT_ROUTE → handleChatStreamingResponse()
  - 處理三種 chunk types: REASONING（spolier + `-# ` 前綴）、TOOL_INTENT（直接發送）、CONTENT
  - 錯誤處理: DomainError category → 本地化繁體中文錯誤訊息
  - showReasoning 控制 reasoning 顯示
  - streamingBypassValidation 控制輸出策略（緩衝 vs 即時）
  - Verify: 單元測試 mock AIChatService、RoutingDecision；模擬各種 chunk 順序情境

- T4.3 [ ] **`packages/ai/src/aichat/listener/AIChatMentionListener.ts`** — handleAgentStreamingResponse 方法
  - 緩衝 CONTENT chunk（pending array）
  - 發送 TOOL_INTENT chunk（獨立訊息）
  - Reasoning 追蹤並在完成後刪除
  - 串流完成後：刪除 reasoning → 發送最終內容
  - Verify: 單元測試驗證 chunk 緩衝和 reasoning 刪除順序

- T4.4 [ ] **`packages/ai/src/aichat/listener/AIChatMentionListener.ts`** — handleChatStreamingResponse 方法
  - 發送「:thought_balloon: AI 正在思考...」初始訊息
  - 第一個 CONTENT chunk 編輯 thinking message
  - 後續 CHUNK 發送新訊息
  - streamingBypassValidation: buffer mode → 緩衝完整內容後一次發送
  - Verify: 單元測試驗證 non-Agent 路徑的訊息編輯和發送邏輯

- T4.5 [ ] **`packages/ai/src/aichat/services/MessageSplitter.ts`** — 訊息分割工具
  - `split(content: string): string[]`
  - MAX_MESSAGE_LENGTH = 1980（預留 20 字元）
  - 分割策略: 段落（\n\n）→ 句子（。！？）→ 固定長度
  - Verify: 參數化單元測試（長度邊界、段落邊界、句子邊界、中文字元）

## **Task 5: LangChain.js AI 聊天服務核心**

Purpose: 實作 LangChainAIChatService — AI API 串流呼叫、工具系統整合
Requirements: R3, R4, R14
Scope: `packages/ai/src/aichat/services/`
Out of scope: 工具實作（Task 8）、Markdown 驗證裝飾器（Task 13）

- T5.1 [ ] **`packages/ai/src/aichat/services/AIChatService.ts`** — AIChatService 介面
  - `generateResponse(guildId, channelId, userId, userMessage): Result<string[]>`
  - `generateStreamingResponse(guildId, channelId, userId, userMessage, handler): void`
  - `generateStreamingResponse(guildId, channelId, userId, userMessage, messageId, handler): void`
  - `generateWithHistory(guildId, channelId, userId, history, handler): void`
  - Verify: 介面簽名與 Java AIChatService.java 一致

- T5.2 [ ] **`packages/ai/src/aichat/services/LangChainAIChatService.ts`** — 核心服務實作（不含工具註冊）
  - 從 AIServiceConfig 建立 ChatOpenAI instance（baseUrl, apiKey, model, temperature, timeout）
  - `generateResponse()`: 呼叫 generateStreamingResponse 並收集結果
  - `generateStreamingResponse()`: 串流回應主流程
  - 串流 callback: onPartialThinking (REASONING) → onPartialResponse (CONTENT) → onToolCall (Agent 模式) → onCompleteResponse → onError
  - onError: 映射例外 → DomainError (LangChainExceptionMapper)
  - 發布 AIMessageEvent（onCompleteResponse）
  - Verify: 整合測試使用 mock AI API endpoint（nock 或 msw）

- T5.3 [ ] **`packages/ai/src/aichat/services/LangChainExceptionMapper.ts`** — 例外到 DomainError 映射
  - Error cause 401 → AI_SERVICE_AUTH_FAILED
  - Error cause 429 → AI_SERVICE_RATE_LIMITED
  - Timeout error → AI_SERVICE_TIMEOUT
  - 5xx → AI_SERVICE_UNAVAILABLE
  - Empty response → AI_RESPONSE_EMPTY
  - JSON parse error → AI_RESPONSE_INVALID
  - 其他 → UNEXPECTED_FAILURE
  - Verify: 參數化單元測試每種例外類型的映射

- T5.4 [ ] **`packages/ai/src/aichat/services/LangChainAIChatService.ts`** — AgentServiceFactory 模式
  - `AgentServiceFactory: (agentToolsEnabled: boolean, systemPrompt: string) => AiService`
  - 條件式註冊工具（agentToolsEnabled=true 時才註冊 17 個工具）
  - DefaultAgentServiceFactory 預設註冊所有工具
  - Verify: 單元測試驗證 tool enabled/disabled 兩種路徑的 AiService 建立

- T5.5 [ ] **`packages/ai/src/aichat/services/MessageChunkAccumulator.ts`** — Chunk 累加器
  - 累積 partial response chunks 直到完整
  - 支援 flush() 取得當前累積內容
  - Verify: 單元測試驗證累加和 flush 行為

## **Task 6: 系統提示詞載入器**

Purpose: 從檔案系統載入 prompts/ 目錄的 .md 檔案
Requirements: R15
Scope: `packages/ai/src/aichat/prompts/`
Out of scope: prompts/ 目錄中的實際 .md 檔案內容（與 Java 專案共用）

- T6.1 [ ] **`packages/ai/src/aichat/prompts/PromptLoader.ts`** — PromptLoader 介面與實作
  - `loadPrompts(agentEnabled: boolean): Result<SystemPrompt, DomainError>`
  - 從 `prompts/` 目錄讀取所有 .md 檔案（使用 fs.readdirSync + fs.readFileSync）
  - agentEnabled=true 時額外載入 `prompts/agent/` 目錄
  - 每個 .md 檔案的內容對應一個 PromptSection（name=檔名, content=內容）
  - 合併為 SystemPrompt（toCombinedString: 用 \n\n 串接所有 section）
  - 目錄不存在 → 回傳 SystemPrompt.empty() + log warning
  - 檔案讀取失敗 → DomainError(PROMPT_LOAD_FAILED)
  - Verify: 單元測試使用 tmp 目錄建立測試用 .md 檔案（含正常、空目錄、agent 子目錄情境）

## **Task 7: Agent 頻道配置服務**

Purpose: 實作 AIAgentChannelConfigService + Redis 快取 + 資料持久化
Requirements: R7
Scope: `packages/ai/src/aiagent/config/`
Out of scope: Admin panel Agent toggle 命令（administration spec）

- T7.1 [ ] **`packages/ai/src/aiagent/config/schema.ts`** — Drizzle schema: `aiAgentChannelConfig` 資料表
  - 欄位: guildId, channelId, enabled, updatedAt
  - unique constraint on (guildId, channelId)
  - Verify: SQL 與 Java migration 一致

- T7.2 [ ] **`packages/ai/src/aiagent/config/AIAgentChannelConfigRepository.ts`** — Repository 介面與 Drizzle 實作
  - `findByGuildAndChannel(guildId, channelId): Result<AIAgentChannelConfig | null>`
  - `upsert(guildId, channelId, enabled): Result<AIAgentChannelConfig>`
  - `findEnabledByGuild(guildId): Result<string[]>` (channelIds)
  - `remove(guildId, channelId): Result<void>`
  - Verify: 整合測試對真實 PostgreSQL 執行 CRUD

- T7.3 [ ] **`packages/ai/src/aiagent/config/AIAgentChannelConfigService.ts`** — 介面定義
  - `isAgentEnabled(guildId, channelId): boolean`
  - `setAgentEnabled(guildId, channelId, enabled): Result<void>`
  - `toggleAgentMode(guildId, channelId): Result<boolean>` (回傳新狀態)
  - `getEnabledChannels(guildId): Result<string[]>`
  - `removeChannel(guildId, channelId): Result<void>`
  - Verify: 與 Java 介面簽名一致

- T7.4 [ ] **`packages/ai/src/aiagent/config/DefaultAIAgentChannelConfigService.ts`** — 預設實作（含 Redis 快取）
  - isAgentEnabled: Redis.get(`agent:config:{guildId}:{channelId}`) → miss → DB query → Redis.put(TTL 3600s)
  - isAgentEnabled: Thread channel → resolveParentChannelId → 以父頻道 ID 查詢
  - setAgentEnabled: DB upsert → Redis.invalidate → EventPublisher.publish(AgentConfigUpdatedEvent)
  - Redis 不可用時優雅降級（直接查 DB）
  - DB 不可用時 isAgentEnabled 回傳 false
  - Verify: 單元測試 mock Redis + DB；整合測試驗證 cache hit/miss/invalidation

- T7.5 [ ] **`packages/ai/src/aiagent/config/AgentConfigCacheInvalidationListener.ts`** — 快取失效監聽器
  - 監聽 AgentConfigUpdatedEvent
  - 清除對應 Redis key: `agent:config:{guildId}:{channelId}`
  - Verify: 單元測試驗證 listener 正確清除指定 key

- T7.6 [ ] **`packages/ai/src/aiagent/config/ConversationIdBuilder.ts`** — 會話 ID 建構器
  - `build(guildId, channelId, threadId, userId, messageId): string`
  - 格式: Thread → `guildId:threadId:userId`；非 Thread → `guildId:channelId:userId:messageId`
  - `parseStrategy(conversationId): ConversationIdStrategy` (THREAD_LEVEL / MESSAGE_LEVEL)
  - `isThreadLevel(conversationId): boolean`
  - Verify: 參數化單元測試各種 ID 組合

## **Task 8: 17 個 Discord 管理工具**

Purpose: 實作所有 AI Agent 工具（每個工具為獨立 injectable class）
Requirements: R5, R6
Scope: `packages/ai/src/aiagent/tools/`
Out of scope: 工具與 LangChain.js 的整合（在 Task 5 的 AgentServiceFactory 中完成）

- T8.1 [ ] **`packages/ai/src/aiagent/tools/ToolCallerAuthorizationGuard.ts`** — 授權檢查
  - `validateAdministrator(context: ToolExecutionContext, guild: Guild, logger: Logger, toolName: string): string | null`
  - context 不可為 null、userId 不可缺失
  - 解析 member: `guild.members.cache.get(userId)` → `guild.members.fetch(userId)`
  - 檢查: `guild.ownerId === userId` OR `member.permissions.has(PermissionFlagsBits.Administrator)`
  - 失敗回傳繁體中文錯誤訊息字串（不拋例外）
  - Verify: 單元測試模擬 Guild、GuildMember、Permission（owner / admin / regular user / member not found 四種情境）

- T8.2 [ ] **`packages/ai/src/aiagent/tools/ToolExecutionContext.ts`** — 工具執行上下文（AsyncLocalStorage）
  - `setContext(guildId, channelId, userId): void`
  - `getContext(): { guildId, channelId, userId }`
  - `clearContext(): void`
  - Verify: 單元測試驗證 AsyncLocalStorage 隔離（兩個並行請求不互相污染）

- T8.3 [ ] **`packages/ai/src/aiagent/tools/CreateChannelTool.ts`** — 創建文字頻道
  - Tool name: `create_channel`
  - Zod schema: { name: z.string().min(1).max(100), permissions: z.array(PermissionSettingSchema).optional() }
  - 實現: guild.channels.create({ name, type: ChannelType.GuildText, permissionOverwrites })
  - Verify: 單元測試 mock GuildChannelManager.create

- T8.4 [ ] **`packages/ai/src/aiagent/tools/CreateCategoryTool.ts`** — 創建分類
  - Tool name: `create_category`
  - Zod schema: { name: z.string().min(1).max(100), permissions: z.array(PermissionSettingSchema).optional() }
  - Verify: 單元測試

- T8.5 [ ] **`packages/ai/src/aiagent/tools/CreateRoleTool.ts`** — 創建身分組
  - Tool name: `create_role`
  - Zod schema: { name: z.string(), color: z.string().optional(), permissions: z.array(PermissionSettingSchema).optional() }
  - Verify: 單元測試

- T8.6 [ ] **`packages/ai/src/aiagent/tools/ListChannelsTool.ts`** — 列出頻道 (對應 Java ListChannelsTool)
  - Tool name: `list_channels`
  - Zod schema: { type: z.enum(['text','voice','category','forum','media','stage']).optional() }
  - 回傳 JSON: [{ id, name, type, parentId, position }]
  - Verify: 單元測試驗證篩選邏輯

- T8.7 [ ] **`packages/ai/src/aiagent/tools/ListCategoriesTool.ts`** — 列出分類
  - Tool name: `list_categories`
  - 無參數
  - 回傳 JSON: [{ id, name, position }]
  - Verify: 單元測試

- T8.8 [ ] **`packages/ai/src/aiagent/tools/ListRolesTool.ts`** — 列出身分組
  - Tool name: `list_roles`
  - 無參數
  - 回傳 JSON: [{ id, name, color, position, permissions }]
  - Verify: 單元測試

- T8.9 [ ] **`packages/ai/src/aiagent/tools/GetChannelPermissionsTool.ts`** — 取得頻道權限
  - Tool name: `get_channel_permissions`
  - Zod schema: { channelId: z.string() }
  - 回傳 permissionOverwrites 列表
  - Verify: 單元測試

- T8.10 [ ] **`packages/ai/src/aiagent/tools/GetCategoryPermissionsTool.ts`** — 取得分類權限
  - Tool name: `get_category_permissions`
  - Zod schema: { categoryId: z.string() }
  - Verify: 單元測試

- T8.11 [ ] **`packages/ai/src/aiagent/tools/GetRolePermissionsTool.ts`** — 取得身分組權限
  - Tool name: `get_role_permissions`
  - Zod schema: { roleId: z.string() }
  - Verify: 單元測試

- T8.12 [ ] **`packages/ai/src/aiagent/tools/ModifyChannelPermissionsTool.ts`** — 修改頻道權限
  - Tool name: `modify_channel_permissions`
  - Zod schema: { channelId, permissions: z.array(ModifyPermissionSettingSchema) }
  - Verify: 單元測試

- T8.13 [ ] **`packages/ai/src/aiagent/tools/ModifyCategoryPermissionsTool.ts`** — 修改分類權限
  - Tool name: `modify_category_permissions`
  - Zod schema: { categoryId, permissions: z.array(ModifyPermissionSettingSchema) }
  - Verify: 單元測試

- T8.14 [ ] **`packages/ai/src/aiagent/tools/ModifyRolePermissionsTool.ts`** — 修改身分組權限
  - Tool name: `modify_role_permissions`
  - Zod schema: { roleId, permissions }
  - Verify: 單元測試

- T8.15 [ ] **`packages/ai/src/aiagent/tools/SendMessagesTool.ts`** — 發送訊息
  - Tool name: `send_messages`
  - Zod schema: { channelIds?: string[], message?: string, messages?: string[] }
  - 支援單頻道單訊息、多頻道、多訊息
  - Verify: 單元測試

- T8.16 [ ] **`packages/ai/src/aiagent/tools/SearchMessagesTool.ts`** — 搜尋訊息
  - Tool name: `search_messages`
  - Zod schema: { keywords, channelIds?, maxResultsPerChannel?, maxMessagesToScan? }
  - 回傳結果以 REDACTED 模式標記（跨回合記憶隔離）
  - Verify: 單元測試 mock message.fetch + 驗證 REDACTED flag

- T8.17 [ ] **`packages/ai/src/aiagent/tools/ManageMessageTool.ts`** — 訊息管理
  - Tool name: `manage_message`
  - Zod schema: { messageId, action: z.enum(['pin','delete','edit']), channelId?, newContent?, editMode?: z.enum(['replace','append','prepend']) }
  - Verify: 單元測試每種 action

- T8.18 [ ] **`packages/ai/src/aiagent/tools/MoveChannelTool.ts`** — 移動頻道
  - Tool name: `move_channel`
  - Zod schema: { channelId, targetCategoryId }
  - 實現: channel.setParent(targetCategoryId)
  - Verify: 單元測試

- T8.19 [ ] **`packages/ai/src/aiagent/tools/DeleteDiscordResourceTool.ts`** — 刪除資源
  - Tool name: `delete_discord_resource`
  - Zod schema: { resourceType: z.enum(['channel','category','role']), resourceId }
  - 實現: channel.delete() / role.delete()
  - Verify: 單元測試每種 resourceType

- T8.20 [ ] **`packages/ai/src/aiagent/tools/PermissionParser.ts`** — 權限解析工具
  - 將 PermissionSetting[] 轉換為 discord.js PermissionOverwriteOptions[]
  - 支援舊版 permissionSet 和 allowSet/denySet 兩種格式
  - Verify: 單元測試各種權限格式

## **Task 9: 工具執行攔截與審計**

Purpose: 實作 ToolExecutionInterceptor、InMemoryToolCallHistory
Requirements: R5 (部分), R8 (部分)
Scope: `packages/ai/src/aiagent/services/`
Out of scope: 工具實作本身（Task 8）

- T9.1 [ ] **`packages/ai/src/aiagent/services/ToolExecutionInterceptor.ts`** — 工具執行審計攔截器
  - `onToolExecutionStarted(toolName: string, params: Record<string, unknown>): void`
  - `onToolExecutionCompleted(result: string): void`
  - `onToolExecutionFailed(error: string): void`
  - 記錄 INFO 級別審計日誌（含 timestamp、toolName、params size、success/failure、duration）
  - Verify: 單元測試驗證 log 輸出內容

- T9.2 [ ] **`packages/ai/src/aiagent/services/InMemoryToolCallHistory.ts`** — 記憶體工具調用歷史
  - Map<string, ToolCallEntry[]>（key = `threadId:userId`）
  - MAX_HISTORY_PER_CONVERSATION = 50
  - `addToolCall(threadId, userId, entry)` — FIFO 移除最舊記錄
  - `getToolCallMessages(threadId, userId)` — 轉換為 ChatMessage[]（僅回傳 safe summary）
  - `getAuditEntries(threadId, userId)` — 原始審計條目
  - `clearHistory(threadId, userId)` / `clearAll()`
  - ToolCallEntry: { timestamp, toolName, parameters, success, memorySummary, redactionMode }
  - RedactionMode: NONE / REDACTED / OMITTED
  - Verify: 單元測試驗證 FIFO、容量上限、RedactionMode 過濾

## **Task 10: 對話記憶管理**

Purpose: 實作 SimplifiedChatMemoryProvider、DiscordThreadHistoryProvider
Requirements: R8
Scope: `packages/ai/src/aiagent/memory/`
Out of scope: InMemoryToolCallHistory（Task 9）

- T10.1 [ ] **`packages/ai/src/aiagent/memory/DiscordThreadHistoryProvider.ts`** — Discord Thread 歷史提供者
  - `getThreadHistory(guildId, threadId, userId, botUserId): ChatMessage[]`
  - 從 threadChannel.messages.fetch({ limit: 100 }) 取得歷史
  - 過濾: 僅包含該 userId 的訊息 + bot 回覆；排除其他使用者的訊息（隱私隔離）
  - 訊息映射為 ChatMessage（USER / AI role）
  - fetch 失敗 → 回傳空陣列（不拋例外）
  - Verify: 單元測試 mock ThreadChannel messages.fetch

- T10.2 [ ] **`packages/ai/src/aiagent/memory/SimplifiedChatMemoryProvider.ts`** — 簡化記憶提供者
  - `get(memoryId: string): BaseChatMessageHistory`
  - 解析 conversationId → 判斷 THREAD_LEVEL 或 MESSAGE_LEVEL
  - THREAD_LEVEL: Thread 歷史 (100則) + ToolCall 歷史 → MessageWindowChatMemory
  - MESSAGE_LEVEL: 空記憶體 (maxMessages=10)
  - 整合 DiscordThreadHistoryProvider + InMemoryToolCallHistory
  - botUserId 從 DiscordRuntimeGateway 取得
  - Verify: 單元測試模擬所有依賴，驗證不同 conversationId 格式的記憶體建構

- T10.3 [ ] **`packages/ai/src/aiagent/memory/TokenEstimator.ts`** — Token 估算工具
  - `estimateTokens(text: string): number` — 簡單 char/4 估算或使用 tiktoken
  - 用於 Debug log（記錄歷史訊息 token 數）
  - Verify: 單元測試已知字串的 token 估算值

## **Task 11: CommonMark Validator (Markdown 驗證器)**

Purpose: 實作 CommonMarkValidator — 8 種 ErrorType 的完整驗證規則
Requirements: R9
Scope: `packages/ai/src/markdown/validation/`
Out of scope: AutoFixer（Task 12）、Sanitizer（Task 12）、Paginator（Task 12）

- T11.1 [ ] **`packages/ai/src/markdown/validation/MarkdownValidator.ts`** — 驗證器介面
  - `validate(markdown: string): ValidationResult`
  - Verify: 介面定義與 Java 一致

- T11.2 [ ] **`packages/ai/src/markdown/validation/CommonMarkValidator.ts`** — 核心驗證實作
  - 使用 marked.lexer() 或 remark.parse() 解析 Markdown
  - 實作所有驗證規則（對應 Java CommonMarkValidator.java）：
    - checkHeadingLevels: # 後缺少空格、超過 H6、標題含列表標記
    - checkInlineHeadings: 非行首的 ## 標記
    - checkListFormat: 列表標記後缺少空格、巢狀列表縮排非 4n
    - checkInlineListItems: 同一行多個列表標記
    - checkCodeBlocks: 未閉合的 code fence
    - checkDiscordUnsupportedSyntax: 水平線、底線粗體、Task List
    - checkDiscordUnsupportedFeatures: 表格（AST/Tokens 遍歷）
  - 追蹤 code block 狀態避免檢查 block 內的內容
  - 辨識純強調語法（*bold*）並跳過（不誤判為列表）
  - 辨識水平線（非列表標記）
  - Verify: 參數化單元測試——每個規則至少 3 個測試案例（合法、違規、邊界）

- T11.3 [ ] **`packages/ai/src/markdown/validation/MarkdownErrorFormatter.ts`** — 錯誤格式化器
  - `format(errors: MarkdownError[], originalContent: string): string`
  - 為每個錯誤產生行號、列號、上下文、建議
  - Verify: 單元測試

## **Task 12: Markdown 自動修正、清理、分頁**

Purpose: 實作 RegexBasedAutoFixer、DiscordMarkdownSanitizer、DiscordMarkdownPaginator
Requirements: R10, R11, R12
Scope: `packages/ai/src/markdown/autofix/`, `packages/ai/src/markdown/services/`
Out of scope: Validator（Task 11）、StreamProcessor + ValidatingDecorator（Task 13）

- T12.1 [ ] **`packages/ai/src/markdown/autofix/MarkdownAutoFixer.ts`** — AutoFixer 介面
  - `autoFix(markdown: string): string`
  - Verify: 介面定義

- T12.2 [ ] **`packages/ai/src/markdown/autofix/RegexBasedAutoFixer.ts`** — 正規表達式自動修正 (對應 Java RegexBasedAutoFixer.java)
  - 實作 14 步修正管線（順序不可變）：
    1. fixUnclosedCodeBlocks — 偵測並閉合未閉合的程式碼區塊
    2. fixHeadingLevelExceeded — 截斷 ####### → ######
    3. fixInlineHeadings — 文字## → 文字\n##
    4. fixHeadingFormat — #文字 → # 文字
    5. fixHeadingContainsListMarker — ### - 標題 → ### 標題
    6. fixHeadingInlineListItems — 標題內的列表標記換行
    7. fixEmbeddedLists — 正文中的列表項轉換
    8. fixInlineListMarkersInListLines — 列表行中的多個列表標記
    9. fixListFormat — -item → - item, 1.item → 1. item
    10. normalizeUnorderedListMarkers — * + → -
    11. fixNestedListIndentation — 4n 縮排校正
    12. fixDiscordUnderlineBold — __text__ → **text**
    13. fixTaskList — - [x] item → - item
    14. fixHorizontalRules — 移除 ---, ***, ___
  - 所有步驟使用 protectCodeBlocks / restoreCodeBlocks 保護程式碼區塊
  - fixListFormat 跳過水平線和純強調語法
  - fixUnclosedCodeBlocks 偵測非程式碼內容行並提前閉合
  - Verify: 每個 fix 方法至少 3 個參數化測試案例

- T12.3 [ ] **`packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts`** — Discord 清理器 (對應 Java DiscordMarkdownSanitizer.java)
  - `sanitize(markdown: string): string`
  - 移除 HTML 註解 (\<!-- ... -->)
  - 移除 HTML 標籸 (\<...>)
  - 壓平巢狀 blockquote (>> → >)
  - 表格轉為 ```text 程式碼區塊
  - protectCodeBlocks / restoreCodeBlocks
  - Verify: 參數化測試每種清理類型

- T12.4 [ ] **`packages/ai/src/markdown/services/DiscordMarkdownPaginator.ts`** — 分頁器 (對應 Java DiscordMarkdownPaginator.java)
  - `paginate(content: string): string[]`
  - MAX_MESSAGE_LENGTH = 1900
  - 在標題行前優先斷頁
  - 程式碼區塊不跨頁
  - 保留 4 字元用於 code fence 閉合
  - 跨頁時自動閉合/重新開啟 code fence
  - Verify: 參數化測試（長內容、標題邊界、code block 跨頁、空內容）

## **Task 13: Markdown 驗證裝飾器與串流處理**

Purpose: 實作 MarkdownValidatingAIChatService 和 DiscordMarkdownStreamProcessor
Requirements: R13
Scope: `packages/ai/src/markdown/services/`
Out of scope: Validator / AutoFixer / Sanitizer / Paginator（Task 11-12）

- T13.1 [ ] **`packages/ai/src/markdown/services/DiscordMarkdownStreamProcessor.ts`** — 串流處理器 (對應 Java DiscordMarkdownStreamProcessor.java)
  - `onChunk(chunk: string): string[]` — 增量累積並在可輸出時回傳分頁
  - `flush(): string[]` — 取得剩餘內容的分頁
  - 內部管線: Segmenter → Sanitizer → AutoFixer → Validator → Paginator
  - MarkdownHeadingSegmenter 在完整標題段落處觸發輸出
  - Verify: 單元測試模擬多次 onChunk → flush 循環

- T13.2 [ ] **`packages/ai/src/markdown/services/MarkdownHeadingSegmenter.ts`** — 標題分段器 (對應 Java MarkdownHeadingSegmenter.java)
  - 偵測完整的 heading line（`^#{1,6} .+`）作為分段點
  - Verify: 單元測試

- T13.3 [ ] **`packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts`** — 裝飾器 (對應 Java MarkdownValidatingAIChatService.java)
  - 實作 AIChatService 介面，委派給 delegate
  - enabled=false 或 streamingBypassValidation=true → 直接委派
  - 串流模式(enabled && !streamingBypassValidation):
    - REASONING / TOOL_INTENT chunk → 直接透傳不處理
    - CONTENT chunk → StreamProcessor.onChunk → 分頁發送
    - isComplete → StreamProcessor.flush → 最終頁面發送
  - 非串流模式: 收集完整回應 → Sanitize → AutoFix → Validate → Paginate
  - generateWithHistory: 提取最後一條 user message → 走相同管線
  - Verify: 單元測試模擬 delegate，驗證 enabled/bypass/user message replacement 三種路徑

## **Task 14: DI 註冊與整合**

Purpose: 將所有服務、工具、管線組件註冊到 tsyringe DI 容器
Requirements: 所有
Scope: `packages/ai/src/di/`
Out of scope: 其他 package 的 DI（shared, economy, shop, dispatch, admin）

- T14.1 [ ] **`packages/ai/src/di/aiModule.ts`** — AI 模組的 tsyringe 註冊
  - AIServiceConfig (from EnvironmentConfig)
  - PromptLoader → DefaultPromptLoader (singleton)
  - AIChannelRestrictionRepository (Drizzle) (singleton)
  - AIChannelRestrictionService → DefaultAIChannelRestrictionService (singleton)
  - AIAgentChannelConfigRepository (Drizzle) (singleton)
  - AIAgentChannelConfigService → DefaultAIAgentChannelConfigService (singleton)
  - ToolCallerAuthorizationGuard (singleton)
  - ToolExecutionInterceptor (singleton)
  - InMemoryToolCallHistory (singleton)
  - DiscordThreadHistoryProvider (singleton)
  - SimplifiedChatMemoryProvider → SimplifiedChatMemoryProvider (singleton)
  - LangChain streaming chat model (依 AIServiceConfig 建立)
  - AgentServiceFactory → DefaultAgentServiceFactory (singleton)
  - MarkdownValidator → CommonMarkValidator (singleton)
  - MarkdownAutoFixer → RegexBasedAutoFixer (singleton)
  - DiscordMarkdownSanitizer (singleton)
  - DiscordMarkdownPaginator (singleton)
  - AIChatService → LangChainAIChatService (singleton) → 被 MarkdownValidatingAIChatService 裝飾
  - 17 個 tool classes (每個 singleton)
  - AgentConfigCacheInvalidationListener → register on EventPublisher (singleton)
  - AIChatMentionListener → register on discord.js messageCreate event (singleton)
  - Verify: DI 容器 resolve 所有 token 不拋錯（dry-run 啟動測試）

- T14.2 [ ] **`packages/ai/src/di/aiModule.ts`** — 條件式 AIChatService 裝飾器註冊
  - 若 AIServiceConfig.enableMarkdownValidation → AIChatService token 被 MarkdownValidatingAIChatService 包裝
  - 否則 AIChatService token 直接為 LangChainAIChatService
  - Verify: 測試兩種 config 下的 resolve 結果型別

- T14.3 [ ] **`packages/ai/package.json`** — Package 設定
  - name: `@ltdjms/ai`
  - dependencies: `@ltdjms/shared`, `langchain`, `@langchain/openai`, `marked` (或 `remark`), `zod`, `tsyringe`, `discord.js`
  - exports: `./src/index.ts` (public API)
  - Verify: `pnpm install` 無錯誤

## **Task 15: 單元測試覆蓋**

Purpose: 確保所有模組達到完整單元測試覆蓋
Requirements: 所有
Scope: `packages/ai/src/**/__tests__/`
Out of scope: 整合測試（Task 16）、E2E 測試（由整合驗證 checkpoint CP5 處理）

- T15.1 [ ] **aichat 單元測試** — AIChannelRestrictionService、AIChatMentionRoutingDecision、AIChatMentionListener、MessageSplitter、LangChainAIChatService、PromptLoader
  - 對應 Java 測試: AIChatMentionListenerTest.java、AIChatMentionRoutingDecisionTest.java、DefaultAIChannelRestrictionServiceTest.java 等
  - Verify: `vitest run --coverage` 所有 aichat 測試通過

- T15.2 [ ] **aiagent 單元測試** — ToolCallerAuthorizationGuard、17 個 tools、InMemoryToolCallHistory、AIAgentChannelConfigService、SimplifiedChatMemoryProvider
  - 對應 Java 測試: ToolCallerAuthorizationGuardTest.java、AIAgentServicesTest.java 等
  - Verify: `vitest run --coverage` 所有 aiagent 測試通過

- T15.3 [ ] **markdown 單元測試** — CommonMarkValidator、RegexBasedAutoFixer、DiscordMarkdownSanitizer、DiscordMarkdownPaginator
  - 對應 Java 測試: CommonMarkValidator 相關測試、MarkdownAutoFixerTest.java、DiscordMarkdownPaginatorTest.java
  - Verify: `vitest run --coverage` 所有 markdown 測試通過

- T15.4 [ ] **MarkdownValidatingAIChatService 單元測試** — 裝飾器委派、管線整合、enabled/disabled 路徑
  - 對應 Java 測試: MarkdownValidatingAIChatServiceTest_SuccessFirstTry.java、MarkdownValidatingAIChatServiceTest_Reformat.java、MarkdownValidatingAIChatServiceTest_WithHistory.java
  - Verify: `vitest run --coverage` 所有 decorator 測試通過

## **Task 16: 整合測試**

Purpose: 整合測試覆蓋關鍵流程路徑
Requirements: R1-R15
Scope: `packages/ai/src/**/__tests__/integration/`
Out of scope: 跨 package E2E（checkpoint CP5）

- T16.1 [ ] **AI Channel Restriction 整合測試** — 端對端 DB CRUD + 路由決策
  - 對應 Java 測試: AIChannelRestrictionIntegrationTest.java
  - Verify: 使用真實 PostgreSQL test container 或 Docker compose

- T16.2 [ ] **LangChainAIChatService 整合測試** — 串流回應 + 工具調用
  - Mock AI API endpoint (nock / msw)
  - 測試純聊天路徑、Agent 工具路徑、錯誤處理
  - Verify: 整合測試通過

- T16.3 [ ] **AIAgentChannelConfig 整合測試** — Redis cache + DB fallback
  - 測試 cache hit/miss/invalidation
  - Redis 不可用 → DB fallback
  - Verify: 整合測試使用 Redis test container

- T16.4 [ ] **Markdown Validation 整合測試** — 完整管線
  - 對應 Java 測試: MarkdownValidationIntegrationTest.java
  - 測試: 合法 Markdown、有錯誤 → 自動修正 → 再驗證 → 清理 → 分頁
  - Verify: 參數化測試多種 Markdown 輸入

- T16.5 [ ] **Tool 整合測試** — 全部 17 個工具的授權 + 執行
  - Mock discord.js API
  - 測試授權成功、授權失敗、Discord API 錯誤
  - Verify: 集成測試所有工具

- T16.6 [ ] **Conversation Memory 整合測試** — Thread history + ToolCallHistory
  - 模擬 Thread message fetch
  - 驗證 memory 建構: Thread history + tool call history 合併
  - Verify: 整合測試
