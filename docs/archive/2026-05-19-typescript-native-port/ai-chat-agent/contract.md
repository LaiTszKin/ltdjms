# Contract: AI Chat and Agent

- Date: 2026-05-19
- Feature: AI Chat and Agent
- Change Name: ai-chat-agent

> **Purpose:** High-level external-dependency context for `tasks.md`: cite-backed facts, limits, failures, security—so integrations are not hallucinated. Not a runnable checklist; `tasks.md` executes wiring (files, calls, mocks, tests). Internal coupling intent stays in `design.md` (`INT-###`).

## Scope

- **External deps in this doc:** 4 (LangChain.js / Vercel AI SDK、OpenAI-compatible API、marked/remark、discord.js)

## Dependencies

### LangChain.js (or Vercel AI SDK)

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://js.langchain.com/docs/introduction | Tool calling (tool()), Streaming (.stream()), Chat models (ChatOpenAI), ChatMessageHistory, AgentExecutor / createReactAgent |
| https://sdk.vercel.ai/docs | streamText(), tool(), generateText(), CoreMessage |

**Version revision assumed:** LangChain.js `^0.3.0` 或 Vercel AI SDK `^4.0.0`（package.json 中 pin minor version）

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| OpenAI-compatible chat model 連接 (custom baseUrl) | LangChain.js: `new ChatOpenAI({ configuration: { baseURL } })` |
| Tool definition with name, description, schema (Zod) | LangChain.js: `tool(fn, { name, description, schema })`; Vercel AI SDK: `tool({ description, parameters: zodSchema, execute })` |
| Streaming with token callbacks (.stream()) | LangChain.js: `.stream()` returning `AsyncIterable<AIMessageChunk>` |
| Tool call interception (before/after tool execution) | LangChain.js: AgentExecutor callbacks / custom tool wrapper |
| Chat memory with message history provider | LangChain.js: `BaseChatMessageHistory` abstract class 或 `RunnableWithMessageHistory` |
| System prompt injection | LangChain.js: `ChatPromptTemplate.fromMessages([['system', prompt], ...])` 或 constructor `systemMessage` param |
| Invocation parameters passthrough to tools | LangChain.js: `RunnableConfig` (configurable fields) / Vercel AI SDK: `AbortSignal` + custom context |
| Reasoning content extraction (extended thinking) | LangChain.js / Vercel AI SDK: `reasoning_content` / `reasoning` field in delta chunks |
| Max iterations for agent tool calling | LangChain.js: `AgentExecutor({ maxIterations: 5 })` / `createReactAgent({ maxIterations: 5 })` |

#### Limits & failures

| Category | Doc fact | Meaning while executing `tasks.md` |
| -------- | -------- | ------------------------------------ |
| Tool schema: Zod validation required | LangChain.js `tool()` requires Zod schema | 每個工具的參數必須有對應 Zod schema 定義 |
| Stream token rate: no built-in throttling | Discord rate limit 5/5s per channel | 非 Agent 模式逐 token 發送可能觸發 rate limit → streamingBypassValidation 預設將完整緩衝後一次發送 |
| Agent max iterations: 5 | AgentExecutor default | 必須設定 `maxIterations: 5`，避免無限工具呼叫循環 |
| Reasoning content: provider-dependent | OpenAI o1/o3/gpt-5 等支援；其他 provider 可能不支援 | `showReasoning` 僅在支援的 model 且 AI API 確實回傳 reasoning_content 時有效 |
| LangChain.js 串流工具呼叫可在多個 chunk 中送達 | `.stream()` partial tool calls | Agent 模式的 CONTENT 必須緩衝直到 onCompleteResponse 才發送 |
| Vercel AI SDK `streamText` 自動處理 tool calling roundtrip | SDK docs: `maxSteps` for multi-step | 若採用 Vercel AI SDK，`maxSteps: 5` 替換 `maxIterations` |

#### Security & secrets

| Concern | Constraint |
| ------- | ---------- |
| AI API Key | 透過環境變數 `AI_SERVICE_API_KEY` 注入，絕不硬編碼或 commit |
| API Key 傳輸 | 使用 HTTPS baseUrl；API key 置於 `Authorization: Bearer` header |
| Base URL 驗證 | 只允許 `https://` URL，拒絕 `http://`（除非 localhost 開發） |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-001` | `ChatOpenAI({ model, temperature, configuration: { baseURL, apiKey } })` -- 建立 streaming chat model | model 名稱、temperature、timeout 來自 AIServiceConfig；必須使用 streaming mode | 不假設 model 一定支援 tool calling（某些便宜 model 不支援） |
| `EXT-002` | `tool(fn, { name, description, schema })` -- 每個工具的定義 | name、description 必須與 Java 版完全一致；schema 使用 Zod | 不假設 LangChain.js 工具系統與 LangChain4j 行為 100% 一致（需測試驗證） |
| `EXT-003` | `.stream()` -- 取得 async iterable 回應 | 必須處理 `reasoning_content` / `content` / `tool_calls` 三種 delta | 不假設所有 chunk 都包含 content（可能僅有 reasoning 或 tool call） |
| `EXT-004` | Agent 模式: 動態註冊/取消工具 | 依 `agentEnabled` 參數決定是否在 AiService builder 中註冊工具 | 不假設工具可以執行期動態增減（需在建立 AiService 時決定） |

#### Trace hooks (no task parroting)

- Spec IDs covered: R1, R3-R8 (所有涉及 AI API 互動的需求)
- Related `design.md` module keys / `INT-###`: `INT-003`, `INT-005`, `INT-006`, `INT-007`
- **Unknown / `TBD`:** reasoning_content 在非 OpenAI provider 的行為（需要測試實際採用的 model）；LangChain.js 對 `InvocationParameters`-style context passthrough 的確切支援範圍

---

### OpenAI-compatible API (AI Service Provider)

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://platform.openai.com/docs/api-reference/chat | Create chat completion (streaming), Response format (reasoning_tokens), Error codes |
| Provider-specific docs (as configured via baseUrl) | API compatibility with OpenAI chat completion format |

**Version revision assumed:** OpenAI API v1 (chat completions endpoint)，或任何相容 API（OpenRouter、DeepSeek、Groq 等）

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `POST /v1/chat/completions` with `stream: true` | OpenAI API reference - Chat completions |
| `model`, `messages`, `temperature`, `tools` parameters | OpenAI API reference - Request body |
| `reasoning_content` in streaming delta (extended thinking) | OpenAI o-series model docs |
| `tool_calls` in streaming delta (function calling) | OpenAI API reference - Function calling |
| Error responses: 401 (auth), 429 (rate limit), 5xx (server error) | OpenAI API reference - Error codes |
| `max_tokens` / `max_completion_tokens` for response length limit | OpenAI API reference - Request body |

#### Limits & failures

| Category | Doc fact | Meaning while executing `tasks.md` |
| -------- | -------- | ------------------------------------ |
| API timeout: 連線階段 timeout = `timeoutSeconds` | node-fetch / axios timeout config | 僅限連線建立，不限制推理時間（AI 思考由 model 端控制） |
| 401 Unauthorized | API key 無效或過期 | → DomainError `AI_SERVICE_AUTH_FAILED` → 提示使用者聯絡管理員 |
| 429 Rate Limited | 超出 API rate limit 或 token quota | → DomainError `AI_SERVICE_RATE_LIMITED` → 提示使用者稍後再試 |
| 5xx Server Error | API 暫時不可用 | → DomainError `AI_SERVICE_UNAVAILABLE` → 提示稍後再試 |
| 400 Bad Request (model 不支援 tool calling) | 某些 provider 的便宜 model | → DomainError `AI_RESPONSE_INVALID` → 記錄詳細錯誤 |
| Token context window exceeded | 輸入訊息超過 model 上限 | → ChatMemory 限制：非 Thread 10 則、Thread 100 則；DiscordThreadHistoryProvider 限制 100 則 |

#### Security & secrets

| Concern | Constraint |
| ------- | ---------- |
| API Key 存儲 | 透過 `AI_SERVICE_API_KEY` 環境變數，絕不出現在 source code 或 log 中 |
| API Key log redaction | LangChain4jExceptionMapper→LangChainExceptionMapper 在記錄錯誤時 redact Authorization header value |
| Base URL open redirect | `AIServiceConfig.validate()` 確認 baseUrl 以 `https://` 開頭（開發模式允許 `http://localhost`） |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-005` | `POST {baseUrl}/chat/completions` -- 串流聊天請求 | baseUrl、model、temperature、messages、stream=true 來自 AIServiceConfig；錯誤回應映射為 DomainError categories | 不假設 API 永遠返回 content（可能在 reasoning 後仍需 tool calling） |
| `EXT-006` | `stream` SSE events: `data: {"choices":[{"delta":{"content":"..."}}]}` | 每個 SSE event 對應一個 chunk；最後一個 event 包含 `[DONE]` | 不假設每個 delta 都有 content（可能僅有 reasoning_content 或 tool_calls） |

#### Trace hooks (no task parroting)

- Spec IDs covered: R3, R4 (所有 AI API 串流互動)
- Related `design.md` module keys / `INT-###`: `INT-003`
- **Unknown / `TBD`:** 實際採用的 AI model / provider 及其 token limit、定價；`reasoning_content` 的精確欄位名稱可能因 provider 而異

---

### marked / remark (Markdown Parser)

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://marked.js.org | marked.parse(), marked.lexer(), Token types (heading, list, code, table, hr, blockquote) |
| https://remark.js.org | remark.parse(), mdast AST types (heading, list, code, table, thematicBreak), unified processor pipeline |

**Version revision assumed:** marked `^15.0.0` 或 remark `^15.0.0`（選擇其一，package.json 中 pin minor）

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| Markdown 解析為 Token array (marked) 或 AST (remark) | marked: `marked.lexer(md)`; remark: `remark.parse(md)` |
| Token/AST node types: `heading` (depth), `list` (ordered, items), `code` (lang), `table`, `blockquote` | marked: Token.type enum; remark: mdast node types |
| Token/AST node source position (line/column) | marked: Token.start/end (部分支援); remark: `position.start.line` / `position.end.line` (完整支援) |
| GFM extension: table, task list | marked: `marked.use({ gfm: true })`; remark: `remark-gfm` plugin |
| Programmatic AST walk (for table detection, code block boundary check) | remark: `unist-util-visit`; marked: Token walker |

#### Limits & failures

| Category | Doc fact | Meaning while executing `tasks.md` |
| -------- | -------- | ------------------------------------ |
| marked `lexer()` 不支援完整 source position | marked docs - lexer tokens have limited position info | 若需要精確行號（CommonMarkValidator 需要），首選 remark（完整 position info） |
| remark ecosystem 較大，需要多個 plugin | remark + remark-gfm + remark-parse + unist-util-visit | 安裝依賴較多但功能完整 |
| 大文件解析效能 | marked 在 >50KB 時效能下降 | Discord 單則訊息最大 2000 字元，split 後每頁約 1900 字元，不會觸及效能極限 |
| 不支援的 Markdown variant | Discord 使用 "Discord-flavored Markdown"，與 CommonMark/GFM 有差異 | 驗證規則中已列舉 Discord 不支援的語法（分隔線、task list、表格、底線粗體等），這些在驗證器層級手動檢查 |

#### Security & secrets

無（Markdown parser 為純函數庫，不涉及網路或認證）

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-007` | `marked.lexer(md)` 或 `remark.parse(md)` -- Markdown 結構化解析 | parser 必須支援 GFM (table, task list) 以完整偵測 Discord 不支援的語法 | 不假設 parser 的 AST 結構與 CommonMark Java 的 Node hierarchy 完全一致 |
| `EXT-008` | AST walker -- 遍歷 parsed tokens/nodes | 主要用於偵測表格節點；必須能區分 code block 內外的節點 | 不假設 AST walk 能取代 regex-based 行級檢查（兩者互補） |

#### Trace hooks (no task parroting)

- Spec IDs covered: R9 (Markdown 驗證規則)
- Related `design.md` module keys / `INT-###`: `markdown/CommonMarkValidator`
- **Unknown / `TBD`:** 最終選擇 marked 還是 remark（取決於 position info 需求。remark 提供精確行號，marked 較輕量但行號較粗糙）

---

### discord.js v14 (Discord Client)

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://discord.js.org/docs/packages/discord.js/14.18.0 | Client Events (messageCreate), Message, TextChannel, ThreadChannel, Guild, GuildMember, Role, CategoryChannel, PermissionFlagsBits, ChannelManager, GuildChannelManager |

**Version revision assumed:** `^14.18.0`（與 shared-infrastructure 一致）

#### Facts we rely on (additional to shared-infrastructure contract)

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `client.on('messageCreate', handler)` -- 監聽訊息（含 @mention 偵測） | Client Events - messageCreate |
| `message.mentions.users.has(botId)` -- 檢查 bot 是否被提及 | Message.mentions property |
| `message.channel.isThread()` -- 判斷是否為 Thread | Channel.type / Channel.isThread() |
| `threadChannel.parentId` -- 取得 Thread 父頻道 | ThreadChannel.parentId |
| `textChannel.parentId` -- 取得頻道所屬分類 | GuildChannel.parentId |
| `guild.channels.create({ name, type, permissionOverwrites })` -- 創建頻道 | GuildChannelManager.create() |
| `guild.roles.create({ name, color, permissions })` -- 創建角色 | RoleManager.create() |
| `guild.channels.cache` / `guild.roles.cache` -- 查詢頻道/角色 | GuildChannelManager / RoleManager cache |
| `channel.permissionOverwrites` -- 頻道權限設定 | GuildChannel.permissionOverwrites |
| `channel.messages.fetch({ limit })` -- 擷取歷史訊息 | TextChannel.messages.fetch() |
| `threadChannel.messages.fetch({ limit })` -- Thread 歷史訊息 | ThreadChannel.messages.fetch() |
| `message.delete()` -- 刪除訊息（reasoning 清理） | Message.delete() |
| `message.edit(content)` -- 編輯訊息（串流更新 thinking 訊息） | Message.edit() |
| `message.pin()` / `message.unpin()` -- 訊息管理工具 | Message.pin() / Message.unpin() |
| `guild.ownerId` -- Guild owner ID | Guild.ownerId |
| `member.permissions.has(PermissionFlagsBits.Administrator)` -- 管理員權限檢查 | GuildMember.permissions |
| `message.content` -- 訊息文字內容 | Message.content |

#### Limits & failures (additional to shared-infrastructure contract)

| Category | Doc fact | Meaning while executing `tasks.md` |
| -------- | -------- | ------------------------------------ |
| Message content 最大 2000 字元 | Discord API limit | MessageSplitter 1980 字元邊界；DiscordMarkdownPaginator 1900 字元邊界 |
| Message edit 只能用於自己的訊息 | Discord API permission | thinkingMessage 由 bot 發送，可編輯；使用者訊息不可被 bot 編輯 |
| Message delete rate limit: 5/5s per channel | Discord API rate limit | ReasoningMessageTracker.deleteAll() 使用 queue() 非阻塞刪除，Discord 自動排程 |
| Thread message fetch limit: 100 per request | Discord API pagination | DiscordThreadHistoryProvider 每次請求上限 100 |
| Channel create name: 1-100 chars | Discord API validation | CreateChannelTool 在呼叫 discord.js 前先驗證名稱長度 |
| PermissionFlagsBits.Administrator 為最高權限 | Discord permission hierarchy | ToolCallerAuthorizationGuard 檢查此 flag 作為管理員判斷 |
| member.permissions.has() 需 cache member first | discord.js cache behavior | ToolCallerAuthorizationGuard 先 resolveCaller (guild.members.cache.get → guild.members.fetch) |

#### Security & secrets

| Concern | Constraint |
| ------- | ---------- |
| Bot Token | 已在 shared-infrastructure contract 設定，此處僅使用 DiscordRuntimeGateway |
| Tool actions (create/delete channels, roles, etc.) | 僅限 ADMINISTRATOR 或 guild owner 可觸發（通過 ToolCallerAuthorizationGuard） |
| Message content from users | 在傳給 AI API 之前不需要額外 sanitization（AI API provider 自行處理） |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-009` | `messageCreate` event handler -- 監聽所有 guild 文字訊息 | 必須過濾 bot 訊息、DM 訊息；透過 `message.mentions` 檢查 @bot | 不假設 `message.mentions.users` 一定包含 bot（某些 edge case 可能遺漏） |
| `EXT-010` | `guild.channels.create()` / `guild.roles.create()` / `channel.delete()` 等 -- 工具的操作 | 每個操作必須在 try/catch 中執行；錯誤轉換為繁體中文訊息回傳（不回傳 raw discord.js error） | 不假設 bot 一定有足夠權限（tool 執行時 Discord API 可能回傳 403 Forbidden） |
| `EXT-011` | `channel.messages.fetch({ limit })` -- 歷史訊息擷取 | 必須設定 limit（Thread: 100, 一般: 10）；過濾 bot 自身訊息用於 memory | 不假設訊息一定可以 fetch（channel 可能已被刪除或權限不足） |
| `EXT-012` | `message.edit()` / `message.delete()` -- 串流回應的動態更新 | 編輯時機：首次 content chunk（編輯 thinking message）→ 後續 chunk（發送新訊息）；delete 用於 reasoning 清理 | 不假設 message 永遠可編輯（可能已被 Discord 刪除或過期） |

#### Trace hooks (no task parroting)

- Spec IDs covered: R1-R4 (mention listener, streaming), R5 (tools), R8 (thread history)
- Related `design.md` module keys / `INT-###`: `INT-001`, `INT-005`, `INT-011`
- **Unknown / `TBD`:** `None`（discord.js API 穩定且行為明確）

---

**Doc-level ordering constraint:** shared-infrastructure (EXT-001~EXT-012 from shared contract) 必須先完成，因為 `discord.js`、`Redis`、`Database`、`Config`、`DI` 等基礎依賴在該 spec 定義。
