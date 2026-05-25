# Spec: ai-chat-java-parity

- Date: 2026-05-24
- Feature: ai-chat-java-parity
- Owner: laitszkin

## Goal

將 TypeScript `@ltdjms/ai` 的 AI Chat 路徑（@mention 監聽、路由決策、串流輸出、Markdown 驗證管線、頻道白名單、PromptLoader）1:1 對齊 Java bot，以 Java 測試與 fixtures 為 oracle。

## Scope

### In Scope
- **AIChatMentionListener**：過濾規則、預設問候、streaming vs bypass validation、reasoning 清理、錯誤本地化
- **AIChatMentionRoutingDecision**：Agent 優先 → 白名單 → DENY；Thread 父頻道解析；`Source` enum 對齊 Java log 語意
- **MessageChunkAccumulator + MessageSplitter**：串流 chunk 累積與 1980 邊界分割
- **LangChainAIChatService（非 Agent 路徑）**：REASONING/CONTENT chunk 處理；錯誤映射
- **Markdown 管線**：`DiscordMarkdownStreamProcessor`、`MarkdownHeadingSegmenter`（或 proven equivalent）；CommonMarkValidator、RegexBasedAutoFixer、Sanitizer、Paginator、MarkdownValidatingAIChatService decorator
- **AIChannelRestrictionService**：白名單 CRUD 與 `isChannelAllowed` 行為
- **PromptLoader / AIServiceConfig / LangChain4jExceptionMapper 等價物**
- **Parity tests**：port Java aichat + markdown 單元測試（不含 17 agent tools）

### Out of Scope
- 17 Discord Agent 工具實作與 tool_execution_log（ai-agent-java-parity）
- LangGraph checkpoint 持久化（ai-agent-java-parity + external-deps）
- 修改 Java bot
- Admin AI 設定 slash command（administration 已有 TS handler；本 spec 僅確保 channel allowlist 後端 parity）

## Functional Behaviors (BDD)

### Requirement 1: 路由決策 1:1
**GIVEN** 使用者在 guild 頻道 @提及 bot
**WHEN** `decideRouting(guildId, channelId, categoryId)` 執行
**THEN** 優先級：Agent 啟用 → AI 白名單（頻道或分類）→ DENY
**AND** Thread 繼承父頻道設定
**AND** AGENT_CONFIG 查詢失敗時不誤判為 AGENT_ROUTE

**Requirements**:
- [x] R1.1 Route enum：`AGENT_ROUTE` | `AI_CHAT_ROUTE` | `DENY`
- [x] R1.2 Source enum 對齊 Java（含 `AGENT_CONFIG_UNAVAILABLE` 等價）
- [x] R1.3 Port `AIChatMentionRoutingDecisionTest.java`

### Requirement 2: 頻道白名單 1:1
**GIVEN** guild AI channel restriction 設定
**WHEN** 管理 allowlist
**THEN** 空白名單 = 預設拒絕；頻道優先於分類檢查

**Requirements**:
- [x] R2.1 Port `DefaultAIChannelRestrictionServiceTest.java`
- [x] R2.2 Integration test 對齊 `AIChannelRestrictionIntegrationTest.java`（若環境允許）

### Requirement 3: Mention listener 串流 UX 1:1
**GIVEN** 路由為 AI_CHAT_ROUTE
**WHEN** 收到 @mention 訊息
**THEN** 初始 `:thought_balloon: AI 正在思考...`；CONTENT/REASONING chunk 格式化對齊 Java

**Requirements**:
- [x] R3.1 過濾 bot 自身、DM、非 mention
- [x] R3.2 空 mention 使用「你好」
- [x] R3.3 REASONING 以 `-# ` + spoiler；showReasoning=false 時忽略
- [x] R3.4 錯誤映射 AI_SERVICE_* → 繁中提示
- [x] R3.5 Port `AIChatMentionListenerTest.java` 核心案例

### Requirement 4: MessageChunkAccumulator 1:1
**GIVEN** 串流 CONTENT chunks
**WHEN** 累積至段落/1980 邊界
**THEN** flush 行為對齊 Java

**Requirements**:
- [x] R4.1 Port `MessageChunkAccumulatorTest.java`
- [x] R4.2 Port `MessageSplitterTest.java`（若尚未完整）

### Requirement 5: Markdown 驗證規則 1:1
**GIVEN** AI 產出 Markdown
**WHEN** `CommonMarkValidator.validate()`
**THEN** 8 種 ErrorType 與 Java 一致

**Requirements**:
- [x] R5.1 擴充現有 `markdown-validator.test.ts` 覆蓋 Java oracle fixtures
- [x] R5.2 Port `CommonMarkValidatorTest_*` 案例

### Requirement 6: Markdown 自動修正 1:1
**GIVEN** 驗證失敗
**WHEN** `RegexBasedAutoFixer.autoFix()`
**THEN** 14 步修正順序與 code block 保護對齊 Java

**Requirements**:
- [x] R6.1 Port `MarkdownAutoFixerTest.java`
- [x] R6.2 最多 3 次 retry until valid

### Requirement 7: Sanitizer + Paginator 1:1
**GIVEN** 修正後 Markdown
**WHEN** sanitize → paginate
**THEN** HTML 移除、blockquote 壓平、表格轉 code block；1900 字元分頁邊界

**Requirements**:
- [x] R7.1 Port `DiscordMarkdownPaginatorTest.java` 剩餘案例
- [x] R7.2 Sanitizer 行為測試對齊 Java

### Requirement 8: Stream markdown processor 1:1
**GIVEN** streamingBypassValidation=false
**WHEN** 串流 CONTENT 經 decorator
**THEN** `DiscordMarkdownStreamProcessor` 增量處理對齊 Java（非僅 end-of-stream batch）

**Requirements**:
- [x] R8.1 實作 `DiscordMarkdownStreamProcessor.ts` + `MarkdownHeadingSegmenter.ts`
- [x] R8.2 Port `MarkdownValidatingAIChatServiceTest_*` streaming 案例

### Requirement 9: AIServiceConfig + PromptLoader 1:1
**GIVEN** 環境變數與 prompts/ 目錄
**WHEN** 啟動或載入 prompt
**THEN** 驗證規則與 agentEnabled 目錄選擇對齊 Java record

**Requirements**:
- [x] R9.1 Port `PromptLoaderTest.java`
- [x] R9.2 AIServiceConfig validate() 邊界測試

## Error and Edge Cases
- [x] AI API 401/429/timeout/5xx — 本地化訊息
- [x] 空 AI 回應 — `:question: AI 沒有產生回應`
- [x] prompt 目錄缺失 — 優雅降級
- [x] Thread history 擷取失敗 — 空記憶繼續對話
- [x] Markdown >10000 字元 — paginator 正確完成

## Clarification Questions
None

## References
- Java: `AIChatMentionListener.java`, `AIChatMentionRoutingDecision.java`, `LangChain4jAIChatService.java`, `MessageChunkAccumulator.java`, `markdown/**`
- Java tests: `src/test/java/ltdjms/discord/aichat/**`, `src/test/java/ltdjms/discord/markdown/**`
- TS: `packages/ai/src/commands/ai-chat-mention-listener.ts`, `packages/ai/src/markdown/**`
- Fixtures: `fixtures/java-routing-oracle.json`, `java-markdown-oracle.json`, `java-streaming-oracle.json`
