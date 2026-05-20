# Code Review Report: TypeScript Native Port

- **審查日期**: 2026-05-20 (第二次全新審查)
- **Spec 基準**: `docs/plans/2026-05-19/typescript-native-port/` (7 spec sets: preparation + 6 feature modules)
- **審查範圍**: Root infrastructure + `packages/shared/`, `packages/economy/`, `packages/shop/`, `packages/dispatch/`, `packages/ai/`, `packages/admin/`
- **審查方法**: 對每個模組的每個 TypeScript 原始碼檔案對照 spec requirements，從 6 個維度進行完整審查：
  1. **幻覺代碼** — 實作了 spec/Java 原版中不存在的功能，或引用不存在的 API
  2. **冗餘代碼** — 重複程式碼、死碼、未使用的 import / 常數
  3. **Spec 偏移** — 實作行為與 spec 要求或 Java 原版不一致
  4. **Spec 遺漏** — spec 中定義的需求完全沒有對應實作
  5. **架構瑕疵** — 錯誤的依賴方向、循環依賴、DI 設定錯誤、抽象層洩漏
  6. **性能隱患** — 缺少連線池、快取未命中、記憶體洩漏、阻塞操作

---

## 彙總統計

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|----|----|----|----|------|
| preparation（根基礎設施） | 0 | 2 | 2 | 2 | 6 |
| shared-infrastructure | 1 | 7 | 10 | 7 | 25 |
| guild-economy | 0 | 3 | 10 | 4 | 17 |
| shop-payment | 0 | 4 | 7 | 8 | 19 |
| escort-dispatch | 0 | 8 | 19 | 9 | 36 |
| ai-chat-agent | 0 | 6 | 8 | 6 | 20 |
| administration | 4 | 18 | 9 | 0 | 31 |
| **合計** | **5** | **48** | **65** | **36** | **154** |

---

## 整體評估

### 業務滿足度判定

| 業務需求 | 狀態 | 說明 |
|----------|------|------|
| 貨幣/代幣/骰子遊戲功能與 Java 100% 一致 | **部分通過** | DiceGame1/2 核心演算法存在，但 config 缺少 auto-create 預設行為、emoji 驗證為 no-op、SeededRandom LCG 與 Java 不同導致 golden-value 測試無法運作 |
| 商店瀏覽、貨幣購買、兌換碼功能 | **部分通過** | ECPay crypto 已修正為 javaUrlEncode，但 domain model 缺少 3 個處理欄位、redemptionCodeId 硬編碼為 0、express.raw() 使 json/urlencoded parser 失效 |
| ECPay 付款流程與 Java 一致 | **通過** | AES/CBC 加密、CheckMacValue 演算法已修正以匹配 Java URLEncoder 行為 |
| 護航派單 7 狀態機完整 | **部分通過** | 所有合法轉換正確實作，但 DM 通知缺少互動按鈕（整個 DM 互動流程無法運作）、權限檢查缺少 guild owner、指令入口回傳純文字而非 embed |
| AI 聊天路由與 Agent 工具 | **部分通過** | Agent 工具執行循環已實作，但 Source enum 命名與 spec 不符、tool call history 使用空白 threadId、CommonMarkValidator 使用 regex 而非 AST parser、DynamicStructuredTool stubs schema 與實際工具不一致 |
| 管理面板互動功能 | **未通過** | SlashCommandListener 未連接到 discord.js client、自動 defer 破壞所有 Modal 流程、7 個 handler 子操作全為 TODO stubs、即時更新僅有 console.log、成員選擇使用佔位符而非真正的 UserSelectMenu |
| Discord 用戶可見輸出一致 | **部分通過** | 管理面板顯示 `Guild ${guildId}` 而非實際伺服器名稱、交易記錄分頁使用 pageSize 5 而非 spec 要求的 10 |

### 關鍵阻斷點

1. **administration (P0)**: SlashCommandListener 未連接到 discord.js `interactionCreate` 事件 — 整個 bot 無法接收任何 slash command 或互動
2. **administration (P0)**: 自動 deferReply 破壞所有 Modal 顯示流程（7 條路徑全部失效）
3. **administration (P0)**: 管理面板 9 個功能按鈕中 7 個的子操作全為 TODO stubs，僅能顯示唯讀概覽
4. **administration (P0)**: 即時更新監聽器僅有 `console.log`，從未實際更新 embed
5. **shared-infrastructure (P0)**: `main.ts` 放在 shared package 內並動態 import 所有業務模組，造成 shared 依賴所有業務模組的架構反轉

---

## P0 — 阻斷性問題 (必須在整合前修正)

### P0-1: SlashCommandListener 未連接到 discord.js Client 的 interactionCreate 事件
- **模組**: administration
- **維度**: 架構瑕疵
- **檔案**: `packages/admin/src/commands/infra/SlashCommandListener.ts:19-168`
- **Spec 參考**: R14.1 — "SlashCommandListener 為 discord.js Client 的 interactionCreate 事件監聽器"
- **描述**: `SlashCommandListener` 實作了 `onInteraction()` 方法但從未被任何程式碼透過 `client.on('interactionCreate', ...)` 註冊。DI 模組建立了監聽器並註冊了 handler，但缺少將監聽器連接到 discord.js Client 的關鍵步驟。這意味著沒有任何 slash command 或 button 互動能被處理。
- **修正**: 在 DI 設定中加入 `client.on('interactionCreate', async (interaction) => { await listener.onInteraction(interaction); })`

### P0-2: SlashCommandListener 自動 deferReply 破壞所有 Modal 顯示流程
- **模組**: administration
- **維度**: 架構瑕疵
- **檔案**: `packages/admin/src/commands/infra/SlashCommandListener.ts:83-85`
- **Spec 參考**: R1.5, R2.2, R3.2, R11.6
- **描述**: `onInteraction()` 對所有互動類型自動執行 `deferReply()`。但 Discord 要求在顯示 Modal 時直接以 `showModal()` 回應原始互動，不能先 `deferReply()`。受影響的 customId: `admin_balance_modal_add/deduct/set`、`admin_token_modal_add/deduct/set`、`user_redeem_code` 等 7 條路徑全部失效。
- **修正**: 移除自動 defer，讓每個 handler 自行管理回應類型；或偵測 Modal 意圖的 customId 時跳過 defer

### P0-3: 管理面板子操作互動全為 TODO stubs
- **模組**: administration
- **維度**: 幻覺代碼 / Spec 遺漏
- **檔案**: `GameSettingsHandler.ts:55-60`, `AIChannelConfigHandler.ts:55-73`, `AIAgentConfigHandler.ts:55-67`, `DispatchAfterSalesHandler.ts:55-62`, `EscortPricingHandler.ts:54-63`, `EscortCatalogHandler.ts:55-57`, `AdminProductPanelHandler.ts:56-61`
- **Spec 參考**: R4-R10
- **描述**: 在 9 個功能按鈕 handler 中，7 個的子操作僅顯示唯讀概覽。遊戲設定無法儲存編輯、AI 頻道/Agent 管理僅顯示清單、售後管理無法新增/移除人員、護航定價無法編輯/重設、護航目錄無法建立項目、產品管理僅顯示清單（無建立/編輯/刪除/分頁/兌換碼功能）。
- **修正**: 為每個 TODO 區塊實作完整的互動流程

### P0-4: 即時更新監聽器僅有 console.log，從未實際更新 embed
- **模組**: administration
- **維度**: Spec 遺漏
- **檔案**: `AdminPanelUpdateListener.ts:68-79`, `UserPanelUpdateListener.ts:86-98`
- **Spec 參考**: R12.1-R12.5
- **描述**: 兩個監聽器都確認事件並檢查哪些 session 應更新，但只做 `console.log()` 和已註解的 TODO 程式碼。從未注入 Discord Client、從未執行 `channel.messages.fetch()` 或 `message.edit()`。此功能名存實亡。
- **修正**: 注入 Discord Client，將 TODO 區塊取代為實際的 embed 更新邏輯

### P0-5: shared package 依賴所有業務模組（架構反轉）
- **模組**: shared-infrastructure
- **維度**: 架構瑕疵
- **檔案**: `packages/shared/src/main.ts:135-226`, `packages/shared/src/types/module-declarations.d.ts:7-44`
- **Spec 參考**: Spec "Out of Scope: any business logic"
- **描述**: `main.ts` 放在 shared package 內並透過動態 `import()` 載入所有業務模組 (`@ltdjms/economy`, `@ltdjms/dispatch`, `@ltdjms/shop`, `@ltdjms/ai`, `@ltdjms/admin`)。`module-declarations.d.ts` 包含所有業務模組的 ambient declarations。這造成 shared package 反向依賴所有業務模組，違反乾淨架構。
- **修正**: 將 `main.ts` 和啟動邏輯移至獨立 entry package（如 `apps/bot` 或 `packages/bootstrap`）

---

## P1 — 嚴重問題 (整合前應修正)

### preparation（根基礎設施）

- **P1-1**: `packages/shop/tsconfig.json` 缺少 `composite: true` — 所有其他 package 皆有設定，shop 缺少會導致 `tsc -b` 建置失敗
- **P1-2**: `packages/admin/tsconfig.json` 缺少 `references` 陣列 — admin 依賴 shared/economy/shop/dispatch/ai 但 tsconfig 未宣告，`tsc -b` 可能以錯誤順序建置

### shared-infrastructure

- **P1-3**: `CacheService.exists()` 方法不在 spec 或 Java 中 — `cache-service.ts:17` 憑空新增 API surface
- **P1-4**: `DomainEventPublisher.publish()` 是 async，spec 說同步分發 — `domain-event-publisher.ts:46`
- **P1-5**: Logger 硬編碼為 `'info'` 忽略 spec R7.3 的 dev=debug 規則 — `main.ts:28`
- **P1-6**: `embed-pagination.ts` 直接 import discord.js `EmbedBuilder` — 抽象層洩漏，mock 路徑也被迫依賴 discord.js
- **P1-7**: `DiscordRuntimeGateway` 缺少 `findThreadChannel()` — spec T7.4 和 Java 皆有此方法
- **P1-8**: `DiscordContext` 缺少 `getOptionAsUser()` — Java 有此方法
- **P1-9**: `DomainEventPublisher.registerAsync()` 不在 spec 中 — spec 只定義同步 listener

### guild-economy

- **P1-10**: DiceGame1/2 Service 吸收了 command-handler 邏輯（config 查詢、token 扣除、交易記錄）— 與 Java 分層不同；config 不存在時回傳錯誤而非 auto-create 預設值
- **P1-11**: Emoji 驗證是 dead no-op — `currency-config-service.ts:185-200` 內部條件永遠為 false，任何字串都通過驗證
- **P1-12**: Dice handlers 錯誤處理扁平化 — 所有錯誤以相同方式顯示，失去 Java 中針對不同錯誤類別的差異化訊息（INSUFFICIENT_TOKENS vs INVALID_INPUT）

### shop-payment

- **P1-13**: `redemptionCodeId` 硬編碼為 0 — `drizzle-redemption-transaction-service.ts:24`，破壞參照完整性
- **P1-14**: FiatOrder domain type 缺少 3 個處理欄位 (`fulfillmentProcessingAt`, `adminNotificationProcessingAt`, `reconciliationProcessingAt`) — spec 要求 36 欄位
- **P1-15**: `express.raw({ type: '*/*' })` 消耗 request body 使 `express.json()` 和 `express.urlencoded()` 失效 — `ecpay-callback-server.ts:49-58`
- **P1-16**: `FiatOrderService.createFiatOnlyOrder` 的 catch 區塊將所有錯誤包裝為 `invalidInput`，失去錯誤區分能力 — `fiat-order.service.ts:130-138`

### escort-dispatch

- **P1-17**: 權限檢查缺少 guild owner — `DispatchPanelCommandHandler.ts:24-28`，僅檢查 ADMINISTRATOR bit
- **P1-18**: `/dispatch-panel` 指令回傳純文字而非 embed — `DispatchPanelCommandHandler.ts:34`，spec 要求 ephemeral embed
- **P1-19**: DM 通知完全缺少互動按鈕 — 所有 `notify*` 方法僅發送純 embed，無 confirm/complete/claim/close 按鈕，整個 DM 互動流程無法運作
- **P1-20**: `ensureTimeoutCompletion` 的 DB update 缺少 status guard — 在 race condition 下可能錯誤更新非 PENDING_CUSTOMER_CONFIRMATION 的訂單
- **P1-21**: schema 缺少 `orderNumber` 的 unique index
- **P1-22**: schema 缺少 `(guildId, status, escortUserId)` compound index
- **P1-23**: 售後通知缺少 claim button ID，售後人員無法從 DM 接手案件
- **P1-24**: `notifyAfterSalesRequested` 缺少按鈕，售後人員無法互動

### ai-chat-agent

- **P1-25**: Routing Source enum 值與 spec 不符 — `AGENT_ENABLED` vs spec 的 `AGENT_CONFIG`，`AI_ALLOWLIST_DENIED` vs spec 的 `NO_ALLOWLIST`
- **P1-26**: Agent 模式同時透過 `onChunkWithType` 和 `onChunk` 發送內容，造成潛在的重複 Discord 訊息
- **P1-27**: Tool call history 使用空白 threadId (`''`) — 所有對話的工具呼叫歷史合併到同一個 key `:userId`
- **P1-28**: Routing source `CATEGORY_ALLOWLIST` 從 `categoryId` 存在性推導，而非實際 allowlist 匹配結果
- **P1-29**: `CommonMarkValidator` 使用逐行 regex 而非 spec 要求的 `marked.lexer()` / `remark.parse()` AST parser
- **P1-30**: DynamicStructuredTool stubs 的 Zod schema 與實際工具不一致（`manage_message` channelId required vs optional、`search_messages` keywords 缺少 min(1) 等）

### administration

- **P1-31**: `CurrencyManagementFacade` 在成功操作時未發布 `BalanceChangedEvent` — 對比 `GameTokenManagementFacade` 正確實作
- **P1-32**: 交易記錄分頁使用 pageSize 5 而非 spec 要求的 10
- **P1-33**: 兌換碼 Modal minLength 為 1 而非 spec 要求的 16
- **P1-34**: `AdminPanelRouter` 永遠無法被到達（dead code）— 所有 `admin_*` customId 已被更具體的 handler 前綴匹配
- **P1-35**: `MemberInfoFacade` 包含原始 SQL 查詢繞過服務層 — 直接查 `product_redemption_transaction` 表
- **P1-36**: `GameSettingsHandler` 未實作編輯/儲存流程 — 僅顯示唯讀設定
- **P1-37**: Balance/Token handler 中熱路徑上的動態 `await import('discord.js')` — 每個請求重複 import
- **P1-38**: 產品管理完全缺乏 CRUD、分頁、詳情和兌換碼功能 — `AdminProductPanelHandler.ts`
- **P1-39**: 使用者面板交易記錄缺少 Previous/Next 分頁按鈕
- **P1-40**: `GameConfigManagementFacade` 的 `DiceGameConfigChangedEvent` 遺漏 `oldConfig` 和 `newConfig` 欄位
- **P1-41**: `SlashCommandRegistrar` 內聯所有 command 定義而非從各 package import
- **P1-42**: `Colors` 常數定義但從未被使用 — 所有 handler 使用硬編碼 hex 值
- **P1-43**: 管理面板顯示 `Guild ${guildId}` 而非實際伺服器名稱 — `AdminPanelCommand.ts:51`
- **P1-44**: `BotErrorHandler` 使用 duck-typing 檢查而非 `instanceof DiscordAPIError`
- **P1-45**: Session manager 使用獨立 `Map` 而非注入 `DiscordSessionManager`（spec 要求 Redis-backed session）
- **P1-46**: `AdminProductPanelHandler` 注入 `ShopService` 而非 spec 要求的 `ProductService` + `RedemptionCodeRepository`
- **P1-47**: 成員選擇使用硬編碼佔位符 `StringSelectMenu` 而非真正的 `UserSelectMenu`
- **P1-48**: Handler 錯誤路徑使用內聯錯誤處理而非 `BotErrorHandler`

---

## P2 — 次要問題（摘要）

### preparation
- admin/tsconfig.json 未 extends 根 tsconfig，重複宣告所有 compilerOptions
- Makefile `build` target 使用 `pnpm -r exec tsc` 而非 `tsc -b`

### shared-infrastructure
- `splitSelectMenusGeneric<T>()` 和 `buildSelectRows()` 未在 spec/tasks 中定義
- `getOption()` 和 `getOptionAsString()` 功能重疊
- `paginateEmbedView` 建立 `EmbedBuilder` 後立即 `.toJSON()`，可改為直接建構 `APIEmbed`
- `embed-view.ts` 缺少 spec T7.5 要求的 `toDiscordJsEmbed()` 和 `toDiscordJsButton()`
- Mock 拒絕 `userId === '0'` 但真實實作以此為 fallback
- DI container 中 `Logger` 和 `DatabasePool` token 缺少 fallback 註冊
- `migration-runner` 每次啟動執行 2 個 `information_schema` 查詢
- `interaction as any` cast 繞過型別安全

### guild-economy
- `MAX_ADJUSTMENT_AMOUNT` 使用 `Number.MAX_SAFE_INTEGER` 而非 Java 的 `Long.MAX_VALUE`
- `GameTokenService.getBalance()` auto-create DB record（Java 不會）
- Dice handlers 硬編碼 "貨幣" 而非使用 guild currency name/icon
- `updatedAt` 在 upsert 時未更新
- `findOrCreate` 使用 3 次 DB round-trip（可最佳化為使用 RETURNING）
- Overflow 檢測與 Java 不同（僅檢查正向溢出）
- 缺少 dice config 的 `findOrCreateDefault`
- `SeededRandom` LCG 與 Java `java.util.Random` 不同，golden-value 測試無法運作
- `DiceGame1Result`/`DiceGame2Result` 缺少 `currencyName`/`currencyIcon`

### shop-payment
- `releaseFulfillmentProcessing` 和 `releaseAdminNotificationProcessing` 跳過 `updatedAt`
- Shop handler 的 `showBuySelection` 僅顯示第一個產品而非 select menu
- DI module 中 `as unknown as` cast 完全繞過型別安全
- `CurrencyPurchaseService` 要求外部 reward service 回傳 `formatReward`（UI 邏輯洩漏）
- `RedemptionCodeRepository.markAsRedeemedIfAvailable` 加入額外 SQL 條件（與 spec 不一致但增加安全性）
- 多個檔案重複定義相同的外部服務介面

### escort-dispatch
- `sourceEscortOptionCode` schema 長度 120 vs spec 50
- `update()` 方法多了 `expectedStatus` 參數（未在 Java 介面中）
- Stub catalog repo `existsByCode` 永遠回傳 true（跳過所有驗證）
- `normalizeLimit` 對 pending-assignment 使用錯誤的預設值（10 而非 5）
- DM-only 按鈕檢查使用不安全的型別轉換 (`as unknown as { inGuild?: boolean }`)
- History 使用 limit 20 而非 spec 要求的 10
- DM handlers 發送新訊息而非編輯原始 DM 訊息
- Notification 方法簽名與 spec T5.1 不同
- 缺少 `withAssignedEscort` domain transition function
- Session state 使用 plain `Map` 無 TTL
- `filterOnlineStaff` 逐個進行 API 呼叫而非平行處理

### ai-chat-agent
- 17 個 AGENT_TOOL_DEFINITIONS stubs 的 `func` 永遠不被執行（死碼）
- 6 個 integration test suites（T16.1-T16.6）未實作
- Agent 模式每次迭代建立新的 `ChatOpenAI` 實例（非重用）
- `AgentConfigUpdatedEvent` local type 定義但從未被使用
- `MessageChunkAccumulator` 被 import 但從未被實例化或呼叫
- `MarkdownHeadingSegmenter` 未提取為獨立 class（邏輯嵌入在 StreamProcessor 中）
- `marked` 是 dependency 但從未被任何檔案 import

### administration
- `BalanceAdjustMode` enum 標記 @deprecated 且未使用
- `AdminPanelModalFactory` 和 `AdminProductPanelModalFactory` 之間 Modal builder 重複
- `AdminPanelViewFactory` 和 `AdminProductPanelViewFactory` 之間產品清單 embed builder 重複
- `AdminPanelModalFactory` 缺少 `buildFiatValueModal`
- `AdminPanelCommand.hasAdminPermission` 與 `BaseAdminHandler.checkAdminPermission` 程式碼重複
- Handler 錯誤路徑使用內聯錯誤處理而非 `BotErrorHandler`
- `AdminPanelUpdateListener` 遺漏 4 個 spec 要求的事件類型
- `DispatchAfterSalesHandler` 未解析成員名稱，僅顯示 `<@id>`

---

## P3 — Cosmetic（摘要）

### preparation
- CI workflow 步驟順序與 spec 不同（tsc 在 eslint 之前）
- `packages/shared/package.json` 和 `packages/shop/package.json` 缺少 `version` 和 `private` 欄位

### shared-infrastructure
- `DomainError.cause` 在不同檔案中型別不一致（`Error` vs `unknown`）
- `publish()` async 行為有完整的同步 dispatch 合約文件但未更新
- 連線失敗拋出正確的 `DatabaseConnectionException`（tasks.md 文字有誤）

### guild-economy
- 兩個 localization strings 有 TODO markers 未被使用
- `InsufficientBalanceError` / `InsufficientTokensError` 被 export 但外部從未使用
- `updateConfig`（非 Result 變體）拋出 generic `Error` 而非 typed error
- `creditReward(0)` + `creditReward(reward)` 造成 2 次 DB round trip（已知行為匹配）

### shop-payment
- `createPendingSimple` helper 未被 production code 使用
- 註解掉的 dead regex (`MERCHANT_TRADE_NO_TIME_FORMAT`)
- `javaUrlEncode` 比 spec 寫的 `encodeURIComponent` 更正確（spec 文件需更新）
- `fetch` options 中使用 `as any` cast for `keepAliveAgent`
- `save()` 中 processing 欄位硬編碼為 null
- `MerchantTradeNoGenerator` 在 cluster mode 下不安全
- reconciliation 使用 fresh `Date()` 而非 captured `now`

### escort-dispatch
- `DispatchPanelCommandHandler` 中有未使用的 imports
- `optionPriceToDisplayLine` 被 export 但從未被呼叫
- `DispatchSessionState` 中 `selectedCustomerId`/`selectedEscortUserId` 被宣告但從未被讀取
- `showCreateMode` 使用文字輸入指令而非 select menu
- `findBySourceIdentity` 的 partial unique index 無法防範空字串 sourceReference
- Stub catalog repo 使定價服務完全無法運作（`findByCode` 永遠回傳 null）
- 缺少 4 個測試檔案（repo integration、notification、panel interaction、message factory）

### ai-chat-agent
- `ToolExecutionInterceptor` 中的 stale TODO 說未 wired（實際上已 wired）
- `MarkdownValidatingAIChatService` 中的 stale TODO 說 StreamProcessor 未 wired（實際上管線已完整）
- `MessageChunkAccumulator.flush()` 中的 stale TODO
- `ModifyRolePermissionsParamsSchema` 缺少 `id`/`type` 欄位（與其他 permission schema 不一致）
- `okVoid<DomainError>() as unknown as Result<void, DomainError>` 模式重複約 7 次
- `DrizzleAIAgentChannelConfigRepository.remove()` 中有 dead branch

---

## 維度分析摘要

| 維度 | P0 | P1 | P2 | P3 | 主要發現 |
|------|----|----|----|----|----------|
| 幻覺代碼 | 1 | 6 | 6 | 6 | Stale TODO、未使用的型別/方法、與 spec 不符的 API |
| 冗餘代碼 | 0 | 1 | 8 | 12 | 重複的 factory、dead imports、未使用常數 |
| Spec 偏移 | 2 | 23 | 23 | 8 | 行為與 Java/spec 不一致、型別不符、enum 命名差異 |
| Spec 遺漏 | 2 | 17 | 18 | 0 | 子操作未實作、測試缺失、事件未發布、監聽器不通 |
| 架構瑕疵 | 3 | 10 | 7 | 6 | 依賴反轉、抽象層洩漏、DI 錯誤、as any cast |
| 性能隱患 | 0 | 0 | 5 | 4 | 不必要的 DB 查詢、連接未重用、無 TTL 清理 |

---

## 修正優先級建議

### 第一優先 (P0 — 阻斷整合) × 5
1. **P0-1** — 將 SlashCommandListener 連接到 discord.js Client → administration
2. **P0-2** — 修正自動 deferReply 破壞 Modal 流程 → administration
3. **P0-3** — 實作 7 個 admin handler 的子操作互動 → administration
4. **P0-4** — 實作即時更新 embed 推送（非僅 console.log） → administration
5. **P0-5** — 將 `main.ts` 啟動邏輯從 shared 移至獨立 entry package → shared-infrastructure

### 第二優先 (P1 — 整合前) × 48
主要集中在：
- **administration** (18): Facade 事件發布、Handler CRUD 完整流程、分頁按鈕、Modal 驗證、成員選擇、DI wiring 修正
- **escort-dispatch** (8): DM 按鈕、權限檢查、schema index、embed 回覆
- **shared-infrastructure** (7): API 簡化、async/sync 一致性、logger level、abstraction leak 修正
- **ai-chat-agent** (6): Source enum 命名、AST parser、tool schema 一致性、threadId 傳播
- **shop-payment** (4): Domain model 完整性、middleware 順序、錯誤分類
- **guild-economy** (3): DiceGame auto-create、emoji 驗證、錯誤處理差異化
- **preparation** (2): tsconfig composite/references

### 第三優先 (P2+P3 — 可平行修正) × 101
大部分為程式碼品質改進、測試覆蓋補完、重構、文件對齊、型別安全強化
