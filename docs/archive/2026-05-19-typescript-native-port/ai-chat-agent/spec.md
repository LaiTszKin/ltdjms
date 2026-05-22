# Spec: AI Chat and Agent

- Date: 2026-05-19
- Feature: AI Chat and Agent
- Owner: [To be filled]

## Goal

移植 AI 聊天、AI Agent（17 個 Discord 管理工具）與 Markdown 驗證處理管線，使成員能在允許頻道透過 @提及 機器人取得 AI 回應，管理員可在已啟用 Agent 的頻道讓 AI 代表他們操作 Discord 資源，同時所有 AI 輸出自動通過 Discord Markdown 相容性驗證與自動修正。

## Scope

### In Scope

- **AI 聊天** (`packages/ai/src/aichat/`)：AIChatMentionListener（@bot 提及監聽）、AIChatMentionRoutingDecision（路由決策矩陣）、AIChannelRestrictionService（頻道/分類白名單管理）、AIServiceConfig（AI 模型配置）、PromptLoader（檔案系統系統提示詞載入器）
- **AI Agent** (`packages/ai/src/aiagent/`)：17 個 Discord 管理工具、LangChain AIChatService（使用 LangChain.js/Vercel AI SDK 實作串流回應與工具調用）、SimplifiedChatMemoryProvider（Discord Thread 歷史 + 記憶體工具調用歷史）、ToolExecutionInterceptor（工具審計日誌）、InMemoryToolCallHistory（每會話最多 50 條記錄）、AIAgentChannelConfigService（頻道層級 Agent 模式開關，含 Redis 快取）
- **Markdown 驗證** (`packages/ai/src/markdown/`)：MarkdownValidatingAIChatService（裝飾器模式）、CommonMarkValidator（Markdown 語法驗證）、RegexBasedAutoFixer（自動修正）、DiscordMarkdownSanitizer（HTML 移除、blockquote 壓平、表格轉換）、DiscordMarkdownPaginator（標題/程式碼邊界分頁，1900 字元安全邊界）
- **DI 與配置**：所有服務的 tsyringe 註冊、外掛點（AgentServiceFactory、自訂系統提示詞路徑）
- **測試**：所有 BDD 場景的單元測試、整合測試、工具授權測試、Markdown 驗證規則測試

### Out of Scope

- 管理面板中的 AI 設定 UI（屬於 administration spec）
- slash command 註冊（`/agent mode`、`/ai allowlist` 等命令處理屬於 administration spec）
- 貨幣/代幣/商店/派單相關的業務邏輯（AI 模組的工具僅操作 Discord 資源，不操作其他 bot 系統）
- 新的 AI 功能開發（如 RAG、function calling 以外的 agent 模式）
- AI model provider 切換邏輯（僅支援 OpenAI-compatible API）

## Functional Behaviors (BDD)

### Requirement 1: AI 提及路由決策
**GIVEN** 使用者在 guild 文字頻道中 @提及 機器人
**AND** 該頻道可能已設定 Agent 模式（AIAgentChannelConfigService）
**AND** 該頻道或其所屬分類可能已在 AI 白名單中（AIChannelRestrictionService）
**WHEN** AIChatMentionRoutingDecision.decide() 被呼叫
**THEN** 路由決策依以下優先級判定：
1. 若頻道（或父頻道，若為 Thread）的 Agent 模式已啟用 → `AGENT_ROUTE`
2. 否則，若頻道或其所屬分類在白名單中 → `AI_CHAT_ROUTE`
3. 否則 → `DENY`
**AND** 決策結果包含 `route`（enum）和 `source`（enum，說明決策來源，如 `AGENT_CONFIG`、`CHANNEL_ALLOWLIST`、`CATEGORY_ALLOWLIST`、`AGENT_CONFIG_UNAVAILABLE`、`NO_ALLOWLIST`）
**AND** Thread 頻道繼承其父頻道的 Agent 設定和白名單資格

**Requirements**:
- [ ] R1.1 AIChatMentionRoutingDecision 實作三層優先級路由矩陣：AGENT_ROUTE > AI_CHAT_ROUTE > DENY
- [ ] R1.2 Thread 頻道解析父頻道 ID 用於路由查詢（resolveRestrictionChannelId）
- [ ] R1.3 決策結果包含 route（enum: AGENT_ROUTE / AI_CHAT_ROUTE / DENY）和 source（enum: AGENT_CONFIG / CHANNEL_ALLOWLIST / CATEGORY_ALLOWLIST / AGENT_CONFIG_UNAVAILABLE / NO_ALLOWLIST）
- [ ] R1.4 AGENT_CONFIG_UNAVAILABLE 時（Redis 查詢失敗等）不當作 AGENT_ROUTE 啟用；若無白名單則 DENY
- [ ] R1.5 決策結果附帶 detail 字串用於 log 除錯

### Requirement 2: AI 頻道限制管理
**GIVEN** 一個 guild
**WHEN** 管理員透過 AIChannelRestrictionService 操作白名單
**THEN** 支援新增/移除允許頻道和允許分類
**AND** `isChannelAllowed(guildId, channelId, categoryId)` 檢查頻道是否在白名單中（先查頻道層級，再查分類層級）
**AND** 空白名單代表預設拒絕（無白名單 = 無頻道可用 AI）
**AND** 移除不存在的頻道或分類時回傳錯誤
**AND** 新增重複頻道或分類時回傳錯誤

**Requirements**:
- [ ] R2.1 AIChannelRestrictionService 介面提供：`isChannelAllowed()`、`getAllowedChannels()`、`getAllowedCategories()`、`addAllowedChannel()`、`addAllowedCategory()`、`removeAllowedChannel()`、`removeAllowedCategory()`
- [ ] R2.2 AIChannelRestrictionRepository 持久化頻道/分類白名單至 `ai_allowed_channels` 和 `ai_allowed_categories` 資料表
- [ ] R2.3 `isChannelAllowed()` 先檢查頻道是否在頻道白名單中，未命中時再檢查分類白名單
- [ ] R2.4 空白名單（無任何允許目標）時 `isChannelAllowed()` 回傳 false（預設拒絕）
- [ ] R2.5 `deleteRemovedChannels()` 清理已在 Discord 中刪除的頻道記錄

### Requirement 3: AI 聊天串流回應（非 Agent 路徑）
**GIVEN** 使用者在允許的頻道中 @提及 機器人
**AND** 路由決策為 AI_CHAT_ROUTE（非 Agent 模式）
**WHEN** AIChatMentionListener 收到訊息
**THEN** 發送「:thought_balloon: AI 正在思考...」初始訊息
**AND** 呼叫 AIChatService.generateStreamingResponse() 取得串流回應
**AND** CONTENT 類型 chunk 即時編輯初始訊息或發送新訊息
**AND** REASONING 類型 chunk 在前綴 `-# ` 後以 spoiler 格式顯示（取決於 showReasoning 設定）
**AND** 串流完成後若無內容則顯示「:question: AI 沒有產生回應」
**AND** 長訊息使用 MessageSplitter 分割（1980 字元邊界，在段落或句子邊界處分割）

**Requirements**:
- [ ] R3.1 AIChatMentionListener 過濾非 bot 提及的訊息、bot 自身訊息、DM 訊息
- [ ] R3.2 預設問候語：使用者 @提及 但無其他文字時，使用「你好」作為預設訊息
- [ ] R3.3 支援兩種輸出策略：streamingBypassValidation 時緩衝完整內容後一次輸出；否則逐 chunk 即時輸出
- [ ] R3.4 REASONING chunk 以 `-# ` 前綴格式化為 Discord spoiler 字體
- [ ] R3.5 showReasoning=false 時完全忽略 REASONING chunk
- [ ] R3.6 串流錯誤時將錯誤訊息分類映射為本地化使用者提示（AI_SERVICE_AUTH_FAILED → 認證失敗、AI_SERVICE_RATE_LIMITED → 忙碌、AI_SERVICE_TIMEOUT → 逾時 等）
- [ ] R3.7 MessageSplitter 在段落（\\n\\n）或句子邊界（。！？）處分割，預留 20 字元緩衝（1980）

### Requirement 4: AI Agent 串流回應（Agent 路徑）
**GIVEN** 使用者在已啟用 Agent 模式的頻道中 @提及 機器人
**AND** 路由決策為 AGENT_ROUTE
**WHEN** AIChatMentionListener 收到訊息
**THEN** LangChain4jAIChatService 檢查頻道的 Agent 設定（AIAgentChannelConfigService）
**AND** 建立 LangChain4jAgentService 時依設定決定是否註冊 17 個工具
**AND** 串流回應處理三種 chunk 類型：REASONING（spolier 顯示）、TOOL_INTENT（工具呼叫前的說明文字）、CONTENT（最終回應）
**AND** TOOL_INTENT chunk 在工具開始執行前立即發送
**AND** Agent 模式下的 CONTENT chunk 在工具呼叫完成後才收集並一次發送完整內容
**AND** 串流完成後先刪除所有 reasoning 訊息，再發送最終內容

**Requirements**:
- [ ] R4.1 Agent 模式下 CONTENT chunk 先緩衝（pendingContent），待 onCompleteResponse 時一次發送
- [ ] R4.2 TOOL_INTENT chunk 在 beforeToolExecution 回調中發送
- [ ] R4.3 Reasoning 訊息在串流完成後全部刪除後才顯示最終內容
- [ ] R4.4 Agent 模式下 streamingBypassValidation 決定是否透過 Markdown 驗證管線處理最終內容
- [ ] R4.5 Agent 最多進行 5 次迭代（多輪工具調用），由 LangChain.js maxIterations 控制

### Requirement 5: AI Agent 17 個 Discord 管理工具
**GIVEN** 使用者在已啟用 Agent 模式的頻道中提出 Discord 管理請求
**AND** AI 模型決定調用工具
**WHEN** 工具被執行
**THEN** 所有工具必須先通過 ToolCallerAuthorizationGuard.validateAdministrator() 授權檢查
**AND** 授權檢查驗證呼叫者具備 ADMINISTRATOR 權限或是 guild 擁有者
**AND** 工具執行前後透過 ToolExecutionInterceptor 記錄審計日誌
**AND** 17 個工具的定義（名稱、描述、參數）與 Java 版完全一致

**Requirements**:
- [ ] R5.1 17 個工具全部實作，對應 LangChain.js tool 定義：

  **頻道操作（4 個）**：
  - `create_channel` — 創建文字頻道，參數：name (string, required)、permissions (array, optional)
  - `list_channels` — 列出所有頻道資訊，參數：type (string, optional: text/voice/category/forum/media/stage)
  - `move_channel` — 移動頻道至指定分類，參數：channelId (string, required)、targetCategoryId (string, required)
  - `delete_discord_resource` — 刪除 Discord 資源，參數：resourceType (string, required: channel/category/role)、resourceId (string, required)

  **分類操作（1 個）**：
  - `create_category` — 創建分類，參數：name (string, required)、permissions (array, optional)

  **角色操作（1 個）**：
  - `create_role` — 創建身分組，參數：name (string, required)、color (string, optional)、permissions (array, optional)

  **權限操作（6 個）**：
  - `get_channel_permissions` — 獲取頻道權限設定
  - `get_category_permissions` — 獲取分類權限設定
  - `get_role_permissions` — 獲取身分組權限設定
  - `modify_channel_permissions` — 修改頻道權限
  - `modify_category_permissions` — 修改分類權限
  - `modify_role_permissions` — 修改身分組權限設定

  **列出操作（3 個）**：
  - `list_channels` — （同上）
  - `list_categories` — 列出所有分類資訊
  - `list_roles` — 列出所有身分組資訊

  **訊息操作（3 個）**：
  - `send_messages` — 發送訊息至指定頻道，參數：channelIds (array, optional)、message (string, optional)、messages (array, optional)
  - `search_messages` — 搜尋歷史訊息，參數：keywords (string, required)、channelIds (array, optional)、maxResultsPerChannel (number, optional)、maxMessagesToScan (number, optional)
  - `manage_message` — 管理訊息（pin/delete/edit），參數：messageId (string, required)、action (string, required)、channelId (string, optional)、newContent (string, optional)、editMode (string, optional: replace/append/prepend)

- [ ] R5.2 ToolCallerAuthorizationGuard.validateAdministrator() 驗證邏輯：
  - InvocationParameters 不可為 null
  - userId 不可為 null/undefined
  - 呼叫者必須是 guild 成員且存在
  - 呼叫者必須具備 ADMINISTRATOR 權限或是 guild owner

- [ ] R5.3 所有工具透過 ToolExecutionContext (AsyncLocalStorage) 取得 guildId、channelId、userId
- [ ] R5.4 ToolExecutionInterceptor 在工具執行前後記錄審計日誌
- [ ] R5.5 SearchMessages 工具結果和含 Discord URL 的回應完全從跨回合記憶中移除（Redacted）
- [ ] R5.6 工具執行結果以安全摘要（memorySummary）形式保留在跨回合記憶中
- [ ] R5.7 每個工具獨立實作為可注入的 class，不與 LangChain.js 深度耦合

### Requirement 6: 工具調用授權檢查
**GIVEN** AI 模型決定執行某個工具
**WHEN** 工具方法被 LangChain.js 調用
**THEN** 工具首先從 ToolExecutionContext 取得調用者 userId
**AND** 透過 DiscordRuntimeGateway 取得 guild 物件和呼叫者 member
**AND** 檢查呼叫者是否為 guild owner 或具備 ADMINISTRATOR 權限
**AND** 若授權失敗，工具回傳明確的繁體中文錯誤訊息（而非拋出例外）
**AND** 以下情況亦回傳錯誤：InvocationParameters 為 null、userId 未設置、member 不存在

**Requirements**:
- [ ] R6.1 授權檢查邏輯封裝在 ToolCallerAuthorizationGuard 中，所有工具共用
- [ ] R6.2 非管理員呼叫時回傳繁體中文訊息：「你沒有權限使用此工具」
- [ ] R6.3 授權失敗時記錄 WARN 級別日誌（含 guildId、userId、toolName）
- [ ] R6.4 Guild owner 判斷：`guild.ownerId === userId`

### Requirement 7: AI Agent 頻道配置管理
**GIVEN** 一個 guild 中的頻道
**WHEN** 管理員切換 Agent 模式
**THEN** AIAgentChannelConfigService 將配置持久化至資料庫
**AND** 查詢結果快取至 Redis（TTL 1 小時）
**AND** Thread 頻道繼承其父頻道的 Agent 配置
**AND** Redis 快取失效時回退到資料庫查詢
**AND** Agent 配置變更時發布 AgentConfigUpdatedEvent，觸發 Redis 快取失效

**Requirements**:
- [ ] R7.1 AIAgentChannelConfigService 提供：`isAgentEnabled()`、`setAgentEnabled()`、`toggleAgentMode()`、`getEnabledChannels()`、`removeChannel()`
- [ ] R7.2 isAgentEnabled() 先查 Redis 快取，miss 時查 DB 並寫入快取
- [ ] R7.3 Redis 快取 key 格式：`agent:config:{guildId}:{channelId}`，TTL 3600 秒
- [ ] R7.4 資料表 `ai_agent_channel_config` 持久化（guild_id, channel_id, enabled）
- [ ] R7.5 AgentConfigCacheInvalidationListener 監聽 AgentConfigUpdatedEvent 並清除對應 Redis key
- [ ] R7.6 Thread 頻道查詢時自動遞歸至父頻道查詢（resolveParentChannelId）

### Requirement 8: 對話記憶管理
**GIVEN** AI 對話進行中
**WHEN** ChatMemoryProvider 被要求提供會話記憶
**THEN** Thread 級別會話（conversationId 格式: `guildId:threadId:userId`）：
  - 從 Discord Thread 擷取歷史訊息（最多 100 則）
  - 從 InMemoryToolCallHistory 取得工具調用歷史（最多 50 條）
  - 合併為完整 ChatMemory
**AND** 非 Thread 級別會話（`guildId:channelId:userId:messageId`）：返回空/限制記憶（最多 10 則訊息）
**AND** 工具調用歷史在應用程式生命週期內存於記憶體（ConcurrentHashMap），重啟後清空
**AND** 搜尋結果和 Discord URL 從跨回合記憶中完全移除（Redacted 模式）

**Requirements**:
- [ ] R8.1 SimplifiedChatMemoryProvider 根據 conversationId 格式判斷 Thread 級別 vs 訊息級別
- [ ] R8.2 Thread 級別會話從 DiscordThreadHistoryProvider 獲取 thread 歷史（最多 100 則）
- [ ] R8.3 非 Thread 級別會話使用 MessageWindowChatMemory（最多 10 則訊息）
- [ ] R8.4 InMemoryToolCallHistory 使用 `threadId:userId` 作為 key，每個會話最多 50 條記錄
- [ ] R8.5 工具調用條目包含：timestamp、toolName、parameters、success、memorySummary、redactionMode
- [ ] R8.6 超過 MAX_HISTORY_PER_CONVERSATION 時移除最舊記錄（FIFO）
- [ ] R8.7 已紅線化的工具結果僅保留安全摘要（不包含實際搜尋結果或 Discord URL）

### Requirement 9: Markdown 驗證規則
**GIVEN** AI 產生了一段 Markdown 格式的回應
**WHEN** CommonMarkValidator.validate() 被呼叫
**THEN** 檢測以下問題並回傳結構化錯誤報告：

  1. **Discord 不支援語法**（ErrorType: DISCORD_RENDER_ISSUE）：
     - 水平分隔線（---、***、___）
     - 底線粗體（__text__）——Discord 僅支援星號粗體
     - Task List（- [x]、- [ ]）
     - 表格（TableBlock AST 節點）
  2. **標題層級**：
     - 超過 H6（####### 以上）→ HEADING_LEVEL_EXCEEDED
     - # 後缺少空格 → HEADING_FORMAT
     - 標題內容包含列表標記 → HEADING_CONTAINS_LIST_MARKER
     - 行內標題（非行首的 ## 標記）→ HEADING_FORMAT
  3. **列表格式**：
     - 列表標記後缺少空格（-item、1.item）→ MALFORMED_LIST
     - 巢狀列表縮排非 4 空格倍數 → MALFORMED_NESTED_LIST
     - 同一行包含多個列表標記 → MALFORMED_LIST
  4. **程式碼區塊**：未閉合的 code fence → UNCLOSED_CODE_BLOCK

**AND** 驗證時追蹤程式碼區塊狀態（避免檢查 code block 內的內容）
**AND** 空字串或僅空白字串視為合法（回傳 Valid）

**Requirements**:
- [ ] R9.1 CommonMarkValidator 使用 marked 或 remark 解析 library
- [ ] R9.2 錯誤報告包含：ErrorType、行號、列號、上下文片段（最多 50 字元）、修復建議
- [ ] R9.3 支援 8 種 ErrorType：HEADING_LEVEL_EXCEEDED、HEADING_FORMAT、HEADING_CONTAINS_LIST_MARKER、MALFORMED_LIST、MALFORMED_NESTED_LIST、UNCLOSED_CODE_BLOCK、DISCORD_RENDER_ISSUE、INLINE_HEADING
- [ ] R9.4 驗證器辨識純強調語法（*bold*、**bold**）並跳過（不視為列表）
- [ ] R9.5 ValidationResult 為 discriminated union：Valid(content) | Invalid(errors)

### Requirement 10: Markdown 自動修正
**GIVEN** Markdown 驗證發現格式錯誤
**WHEN** RegexBasedAutoFixer.autoFix() 被呼叫
**THEN** 依序應用以下修正（順序不可變）：

  1. 修正未閉合的程式碼區塊（fixUnclosedCodeBlocks）
  2. 修正超過 H6 的標題 → 截斷為 ######（fixHeadingLevelExceeded）
  3. 修正行內標題（text## heading → text\n## heading）（fixInlineHeadings）
  4. 修正 # 後缺少空格（fixHeadingFormat）
  5. 移除標題中的列表標記（### - title → ### title）（fixHeadingContainsListMarker）
  6. 修正標題行內的列表標記（fixHeadingInlineListItems）
  7. 修正正文中嵌入的列表（fixEmbeddedLists）
  8. 修正列表行內的 inline 列表標記（fixInlineListMarkersInListLines）
  9. 修正列表標記後缺少空格（fixListFormat）
  10. 統一無序列表標記為 -（normalizeUnorderedListMarkers）
  11. 修正巢狀列表縮排（fixNestedListIndentation）
  12. 修正 __text__ → **text**（fixDiscordUnderlineBold）
  13. 移除 Task List 格式 → 普通列表（fixTaskList）
  14. 移除水平分隔線（fixHorizontalRules）

**AND** 在所有修正過程中保護程式碼區塊內容（protectCodeBlocks → restoreCodeBlocks）
**AND** 修正後再次驗證，最多重試 3 次（直到無錯誤或達上限）

**Requirements**:
- [ ] R10.1 所有修正操作保護程式碼區塊（用佔位符替換後再處理）
- [ ] R10.2 修正順序嚴格遵守（前面的修正可能影響後續規則的匹配）
- [ ] R10.3 fixListFormat 跳過分隔線行和純強調語法行（*bold*）
- [ ] R10.4 fixUnclosedCodeBlocks 偵測程式碼區塊中的普通英文句子並提前閉合
- [ ] R10.5 fixNestedListIndentation 將巢狀列表縮排校正為每層 4 個空格
- [ ] R10.6 fixEmbeddedLists 識別正文中的有序/無序列表並轉換為換行格式

### Requirement 11: Markdown Discord 清理
**GIVEN** AI 回應經過驗證和自動修正後
**WHEN** DiscordMarkdownSanitizer.sanitize() 被呼叫
**THEN** 依序執行：
  1. 移除所有 HTML 註解（\<!-- ... -->）
  2. 移除所有 HTML 標籤（\<...>）
  3. 壓平巢狀 blockquote（多層 > 合併為單層 >）
  4. 將表格轉換為 ```text 程式碼區塊（Discord 不支援表格）
**AND** 清理過程中保護程式碼區塊

**Requirements**:
- [ ] R11.1 HTML 註解和標籤完全移除
- [ ] R11.2 巢狀 blockquote（>> 或更深）壓平為單層（>）
- [ ] R11.3 表格（含 header separator 行）整塊轉為 ```text ... ``` 程式碼區塊
- [ ] R11.4 表格偵測使用 TABLE_SEPARATOR regex：`^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$`

### Requirement 12: Markdown 分頁
**GIVEN** 一份通過驗證、修正和清理的完整 Markdown 內容
**WHEN** DiscordMarkdownPaginator.paginate() 被呼叫
**THEN** 將內容分割為每段不超過 1900 字元的多頁
**AND** 分頁邊界優先設在：
  1. 標題行（`^#{1,6}\s+.+`）之前（保留邏輯段落）
  2. 程式碼區塊邊界（確保 code block 不分頁）
**AND** 程式碼區塊跨頁時在前一頁結尾閉合 ``` ，並在下一頁開頭重新開啟 ```language
**AND** 頁面結尾自動 strip 移除多餘空白
**AND** 內容長度 ≤ 1900 時回傳單頁（不進行分割）
**AND** null 或空字串回傳空陣列

**Requirements**:
- [ ] R12.1 最大訊息長度 MAX_MESSAGE_LENGTH = 1900（Discord 限制 2000，預留 100 字元安全邊界）
- [ ] R12.2 在標題行前優先斷頁（保留邏輯結構）
- [ ] R12.3 程式碼區塊不跨頁分割：必要時在 fence 前強制斷頁，回復時在新頁重新開啟 fence
- [ ] R12.4 保留 4 字元用於程式碼 fence 閉合（reservedCharsForCodeFence）
- [ ] R12.5 每行保留 trailing newline 以維持格式

### Requirement 13: Markdown 驗證裝飾器
**GIVEN** AIServiceConfig.enableMarkdownValidation === true
**WHEN** AIChatService 的任何方法被呼叫
**THEN** MarkdownValidatingAIChatService（裝飾器）攔截呼叫：
  - 串流模式：streamingBypassValidation=true 時直接委派，false 時收集所有 chunk → Sanitize → AutoFix → Validate → Paginate → 分成多頁發送
  - 非串流模式：先取得完整回應 → Sanitize → AutoFix → Validate → Paginate → 回傳分頁結果
**AND** REASONING 和 TOOL_INTENT chunk 直接透傳不經過驗證管線
**AND** enabled=false 時完全跳過驗證管線

**Requirements**:
- [ ] R13.1 MarkdownValidatingAIChatService 實作 AIChatService 介面（裝飾器模式）
- [ ] R13.2 串流模式管線順序：Sanitize → AutoFix → Validate → Paginate
- [ ] R13.3 REASONING / TOOL_INTENT chunk 直接透傳不處理
- [ ] R13.4 DiscordMarkdownStreamProcessor 管理串流緩衝狀態
- [ ] R13.5 generateWithHistory 時先提取最後一條使用者訊息，再走同樣管線

### Requirement 14: AI 服務配置
**GIVEN** 系統啟動
**WHEN** AIServiceConfig 從環境變數載入
**THEN** 包含以下設定項：
  - baseUrl（必要）— AI API endpoint（例如 https://api.openai.com/v1）
  - apiKey（必要）— API 金鑰
  - model（必要）— 模型名稱（例如 gpt-4o）
  - temperature（0.0-2.0）— 回應隨機性
  - timeoutSeconds（1-120）— 連線逾時
  - showReasoning（boolean，預設 false）— 是否顯示推理內容
  - enableMarkdownValidation（boolean，預設 true）— 是否啟用驗證
  - streamingBypassValidation（boolean，預設 false）— 串流模式是否跳過驗證
**AND** validate() 方法檢查必要欄位和範圍限制

**Requirements**:
- [ ] R14.1 AIServiceConfig 從 EnvironmentConfig 建立（AIServiceConfig.from(env)）
- [ ] R14.2 必要欄位為空白時 validate() 回傳 invalidInput DomainError
- [ ] R14.3 temperature < 0 或 > 2 時回傳 invalidInput
- [ ] R14.4 timeoutSeconds < 1 或 > 120 時回傳 invalidInput
- [ ] R14.5 設定值型別與 Java AIServiceConfig record 完全一致

### Requirement 15: 系統提示詞載入
**GIVEN** prompts/ 目錄中存在 .md 檔案
**WHEN** PromptLoader.loadPrompts(agentEnabled) 被呼叫
**THEN** 從 prompts/ 目錄載入所有 .md 檔案
**AND** agentEnabled=true 時額外載入 prompts/agent/ 目錄下的檔案
**AND** 合併所有檔案內容作為系統提示詞
**AND** 載入失敗時回傳 DomainError（PROMPT_LOAD_FAILED）
**AND** 檔案不存在或不可讀時優雅降級（log warning，使用空提示詞）

**Requirements**:
- [ ] R15.1 PromptLoader 介面提供 `loadPrompts(agentEnabled: boolean): Result<SystemPrompt, DomainError>`
- [ ] R15.2 prompt 檔案為 UTF-8 編碼 .md 檔案
- [ ] R15.3 agent 專用 prompt 存放於 prompts/agent/ 子目錄
- [ ] R15.4 SystemPrompt 值物件提供 `toCombinedString()` 和 `empty()` 工廠方法
- [ ] R15.5 PromptSection 代表單一 prompt 檔案內容

## Error and Edge Cases

- [ ] AI API 認證失敗（401/403）→ 對應 AI_SERVICE_AUTH_FAILED 錯誤
- [ ] AI API 速率限制（429）→ 對應 AI_SERVICE_RATE_LIMITED 錯誤，提示使用者稍後再試
- [ ] AI API 連線逾時（timeout）→ 對應 AI_SERVICE_TIMEOUT 錯誤
- [ ] AI API 不可用（5xx）→ 對應 AI_SERVICE_UNAVAILABLE 錯誤
- [ ] AI 回應為空 → 對應 AI_RESPONSE_EMPTY 錯誤
- [ ] AI 回應格式異常（JSON 解析失敗等）→ 對應 AI_RESPONSE_INVALID 錯誤
- [ ] Redis 不可用時 Agent 配置查詢 → 回退到資料庫查詢（不拋例外）
- [ ] Agent 配置查詢失敗（DB+Redis 均不可用）→ isAgentEnabled() 回傳 false 並切換為純聊天模式
- [ ] Thread 訊息擷取失敗時 → 返回空訊息記憶（不影響對話繼續）
- [ ] 工具執行時 guild 不存在或 member 已離開 → 工具回傳錯誤訊息而不是拋出例外
- [ ] 並發工具呼叫在同一 channel/user → ToolExecutionContext 使用 AsyncLocalStorage 隔離上下文
- [ ] 程式碼區塊內含 Discord 不支援的語法 → 不應被驗證器檢測（驗證器追蹤 code block 狀態）
- [ ] Markdown 內容非常長（> 10,000 字元）→ 分頁器正確處理（在合理時間內完成）
- [ ] prompt 目錄不存在 → loadPrompts 回傳空 SystemPrompt（不阻止 bot 啟動）
- [ ] showReasoning=false 時 reasoning_content 完全不出現在 Discord 訊息中（不會以空白訊息出現）
- [ ] 非管理員嘗試觸發工具 → 工具回傳「你沒有權限使用此工具」而不是拋出例外或讓 LLM 無回應

## Clarification Questions

None（所有需求基於現有 Java 程式碼的明確行為，以及 LangChain.js / Vercel AI SDK 的已知 API）

## References

- Official docs:
  - LangChain.js: https://js.langchain.com/docs/introduction
  - Vercel AI SDK: https://sdk.vercel.ai/docs
  - marked (Markdown parser): https://marked.js.org
  - remark (Markdown processor): https://remark.js.org
  - discord.js v14: https://discord.js.org/docs
- Related code files (Java):
  - `src/main/java/ltdjms/discord/aichat/commands/AIChatMentionListener.java`
  - `src/main/java/ltdjms/discord/aichat/services/AIChatService.java`
  - `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
  - `src/main/java/ltdjms/discord/aichat/services/StreamingResponseHandler.java`
  - `src/main/java/ltdjms/discord/aichat/services/AIChannelRestrictionService.java`
  - `src/main/java/ltdjms/discord/aichat/services/DefaultAIChannelRestrictionService.java`
  - `src/main/java/ltdjms/discord/aichat/services/PromptLoader.java`
  - `src/main/java/ltdjms/discord/aichat/services/MessageSplitter.java`
  - `src/main/java/ltdjms/discord/aichat/domain/AIServiceConfig.java`
  - `src/main/java/ltdjms/discord/aiagent/domain/AIAgentTools.java`
  - `src/main/java/ltdjms/discord/aiagent/services/LangChain4jAgentService.java`
  - `src/main/java/ltdjms/discord/aiagent/services/SimplifiedChatMemoryProvider.java`
  - `src/main/java/ltdjms/discord/aiagent/services/InMemoryToolCallHistory.java`
  - `src/main/java/ltdjms/discord/aiagent/services/AIAgentChannelConfigService.java`
  - `src/main/java/ltdjms/discord/aiagent/services/tools/ToolCallerAuthorizationGuard.java`
  - `src/main/java/ltdjms/discord/aiagent/domain/ConversationIdBuilder.java`
  - `src/main/java/ltdjms/discord/markdown/validation/CommonMarkValidator.java`
  - `src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java`
  - `src/main/java/ltdjms/discord/markdown/services/DiscordMarkdownSanitizer.java`
  - `src/main/java/ltdjms/discord/markdown/services/DiscordMarkdownPaginator.java`
  - `src/main/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatService.java`
  - `src/main/java/ltdjms/discord/markdown/services/DiscordMarkdownStreamProcessor.java`
  - `src/main/java/ltdjms/discord/shared/DomainError.java`（AI_SERVICE_*、PROMPT_*、CHANNEL_NOT_ALLOWED 等分類）
