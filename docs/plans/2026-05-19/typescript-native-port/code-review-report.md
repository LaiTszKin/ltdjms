# Code Review Report

- **Spec**: TypeScript Native Port (6 modules: shared-infrastructure, guild-economy, shop-payment, escort-dispatch, ai-chat-agent, administration)
- **Date**: 2026-05-21
- **Reviewer**: Claude Opus 4.7 (via /qa skill)
- **Review Dimensions**: 幻覺代碼、冗余代碼、實作偏移、Spec 實作遺漏、架構瑕疵、性能隱患

---

## 審查範圍

本報告針對 `docs/plans/2026-05-19/typescript-native-port/` 定義的 6 個 TypeScript 模組進行全面審查，共涵蓋 619 個 `.ts` 檔案。審查基準為各模組的 spec 文件及 coordination.md 中的設計原則。

**編譯狀態**: 5/6 packages 編譯通過。shop 有 2 個既有編譯錯誤（Drizzle type mismatch TS2769, shop-view.ts TS2322）。
**測試狀態**: 47 個測試檔案中 45 個通過（650/655 tests pass）。2 個既有失敗（ecpay-crypto crosscheck 2 failures, balance-management-handler editEmbedCount 3 failures）。

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | DI 容器中 `TOKENS.DatabasePool` 註冊為 raw `pg.Pool`，但 economy/dispatch/ai 模組將其當作 `NodePgDatabase` 使用，呼叫 `.select()`/`.insert()` 等 Drizzle ORM 方法會在 runtime 拋出 `TypeError` | 所有透過 DI 容器的 economy/dispatch/ai 資料庫操作在 production 環境會 crash | `admin/src/main.ts`, `economy/src/di/economy-module.ts`, `dispatch/src/di/dispatch-module.ts`, `ai/src/di/ai-module.ts` | main.ts:75, economy-module.ts:77, dispatch-module.ts:59, ai-module.ts:137 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `userId` 在 DomainEvent 介面與多個 service 方法中宣告為 `number` 型別，Discord snowflake ID 可超過 `Number.MAX_SAFE_INTEGER`（2^53），導致大 ID 精度遺失 | 大型 Discord 伺服器用戶 ID 可能被截斷，導致事件路由錯誤或資料錯亂 | `economy/src/events/index.ts`, `shared/src/types/events/domain-event.ts` | economy/events/index.ts:3-8, domain-event.ts:8 |
| 2 | 搜尋結果 product select menu 使用 `products.slice(0, 25)` 截斷，而非 spec 要求的自動拆分為多個選單 | 搜尋結果超過 25 個商品時，用戶無法購買第 26+ 項商品 | `shop/src/view/shop-view.ts` | 395-403 |
| 3 | `DispatchNotificationService` 使用 `Promise.all(targetIds.map(...))` 對所有售後人員並行發送 DM，超過 10 人即觸發 Discord rate limit (5 DM/s) | 多售後人員的 guild 會收到 429 rate limit，導致通知遺失 | `dispatch/src/notification/DispatchNotificationService.ts` | 163-167, 246-258 |
| 4 | SIGTERM shutdown handler 未呼叫 `disposeAdminContainer()`，導致 DomainEventPublisher 保留對 listener 的引用，event emitter 未清理 | 熱重載（nodemon）時 listener 累積，造成記憶體洩漏與重複事件處理 | `admin/src/main.ts`, `admin/src/di/AdminModule.ts` | main.ts:157-166, AdminModule.ts:485-516 |
| 5 | `AdminPanelUpdateListener` 每個 domain event 對每個 active session 執行 3 次序向 Discord API 呼叫（fetch channel → fetch message → edit），30 sessions × 10 events = 900 API calls | 高事件頻率時觸發 Discord rate limit，面板更新延遲或失敗 | `admin/src/panel/listeners/AdminPanelUpdateListener.ts` | 106-154 |
| 6 | `PromptLoader` 在單一檔案讀取失敗（`PROMPT_READ_FAILED`）或檔案過大（`PROMPT_FILE_TOO_LARGE`）時向上傳播錯誤，spec 要求優雅降級（log warning + 空提示詞） | 個別 prompt 檔案損毀時阻止 AI 服務啟動 | `ai/src/prompts/prompt-loader.ts` | 83-84, 93-94 |
| 7 | `BalanceService.getBalance()` 回傳 `Promise<BalanceView>` 而非 spec R1.1 要求的 `Result<BalanceView, DomainError>`（Result 版本改名為 `tryGetBalance()`） | 呼叫端若依 spec 介面預期使用 Result 模式會收到 raw promise | `economy/src/currency/services/balance-service.ts` | 106 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Agent 模式辨識（CHAT/AGENT/HYBRID）未完整實作：`AIAgentChannelConfigRepository` 僅儲存 `enabled: boolean`，無 `mode` 欄位。`enableAgent()` 接受 `AgentMode` 參數但 fallback 到 AGENT | 未來擴充 Agent 模式需 DB migration + 三層改動 | `admin/src/facades/AIConfigManagementFacade.ts`, `ai/src/services/routing/agent-config-service.ts` | AIConfigManagementFacade.ts:119-127, agent-config-service.ts:88-107 |
| 2 | `splitSelectMenus` / `splitSelectMenusGeneric` 兩個匯出函數在所有 6 個 packages 中零引用，78 行程式碼完全無用 | 死碼增加維護混淆 | `shared/src/discord/services/select-menu-util.ts` | 19, 52 |
| 3 | `MessageChunkAccumulator` 類別在所有 packages 中零引用（含測試），35 行死碼 | 死碼增加維護混淆 | `ai/src/services/message-chunk-accumulator.ts` | 5 |
| 4 | `AIChannelConfigHandler.showCategoryConfig()` 與 `showChannelConfig()` 完全相同的實作（byte-for-byte identical） | 重複程式碼需雙倍維護、修改時易遺漏 | `admin/src/panel/admin/handlers/AIChannelConfigHandler.ts` | 220-282 |
| 5 | `DiceGame1Service.rollDice()` 與 `DiceGame2Service.rollDice()` 完全相同 | 重複邏輯可抽出共用工具函數 | `economy/src/dice/services/dice-game-1-service.ts`, `dice-game-2-service.ts` | dg1:95-101, dg2:102-108 |
| 6 | `AdminPanelUpdateListener` 監聽 13 種 event type vs spec R12.2 列舉的 9 種。多了 BALANCE_CHANGED、GAME_TOKEN_CHANGED 等 4 種 | 超越 spec 範圍的擴充未記錄 | `admin/src/panel/listeners/AdminPanelUpdateListener.ts` | 251-267 |
| 7 | 非 MAIN view 的面板更新為 no-op re-edit：listener 只能重建 MAIN panel embed（因僅注入 `CurrencyManagementFacade`） | 非主選單面板收到事件時觸發無效的 Discord API 呼叫 | `admin/src/panel/listeners/AdminPanelUpdateListener.ts` | 56-60 |
| 8 | MANUAL 來源訂單的 `createManualOpenOrder()` 儲存了 `sourceEscortOptionCode`，spec R1.4 說所有 source 欄位應為 null | 與 spec 行為不一致，雖有 code comment 解釋但應修正 spec 或 code | `dispatch/src/domain/escort-dispatch-order.ts` | 238-258 |
| 9 | 兌換碼成功訊息格式與 spec R11.10 不一致：多了多餘空行 | 用戶可見的訊息格式偏移 | `shop/src/services/redemption.service.ts` | 30-41 |
| 10 | 餘額不足錯誤訊息可能不包含當前餘額數值（spec edge case 要求顯示「目標用戶餘額不足，當前餘額：X」） | 管理員無法從錯誤訊息得知目標用戶實際餘額 | `admin/src/facades/CurrencyManagementFacade.ts` | 113-123 |
| 11 | `ToolCallerAuthorizationGuard` 使用 `console.warn()` 而非 pino structured logging，spec R6.3 要求 WARN 級別含 guildId/userId/toolName | 授權失敗事件無法被結構化日誌系統搜尋與告警 | `ai/src/tools/ToolCallerAuthorizationGuard.ts` | 52-59 |
| 12 | Domain event 發布使用 `as AIChannelConfigChangedEvent` type assertion，cast 到不同介面 | 型別斷言誤導開發者，增加維護風險 | `ai/src/services/routing/channel-restriction-service.ts` | 326-383 |
| 13 | `mapRowToDomain` 使用 `Record<string, unknown>` 而非 Drizzle `$inferSelect` 型別，喪失編譯期型別安全 | Schema 欄位改名時 TypeScript 無法標記失效的 mapping | `dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | 293-319 |
| 14 | transaction repository 查詢方法無 LIMIT，用戶交易記錄達數千筆時全表傳輸 | 活躍用戶查詢歷史時資料庫與網路開銷過大 | `economy/src/currency/repositories/currency-tx-repo.ts`, `economy/src/token/repositories/token-tx-repo.ts` | tx-repo:44,66,82 |
| 15 | `UserPanelUpdateListener` 使用 `Array.includes()` 在 O(n) loop 內做 O(n) 比對 → O(n^2)，200 sessions = 40,000 次字串比較 | 大量活躍 sessions 時事件處理延遲增加 | `admin/src/panel/listeners/UserPanelUpdateListener.ts` | 83 |
| 16 | `lastUpdateTimestamps` Map 僅每 50 次呼叫清理一次，無最大容量上限 | 1000 guilds × 13 event types = 13,000 entries 持續保留在記憶體中 | `admin/src/panel/listeners/AdminPanelUpdateListener.ts`, `UserPanelUpdateListener.ts` | AL:65,330-337, UL:31,172-178 |
| 17 | Escort option catalog `findAll()` 無 LIMIT，且每次 validation error 都做全表掃描來建構錯誤訊息 | 頻繁的 validation error（惡意請求）造成不必要的 DB 負載 | `dispatch/src/repo/drizzle-escort-option-catalog.repo.ts`, `dispatch/src/service/escort-option-pricing.service.ts` | catalog:16-30, pricing:100-104 |
| 18 | Redis circuit breaker 在任合 Redis 錯誤後拒絕所有快取操作 30 秒，造成 cache-stampede 全部落到 PostgreSQL | Redis 瞬斷時資料庫承擔所有快取查詢負載 | `shared/src/infra/cache/redis-cache-service.ts` | 42-47 |
| 19 | Panel Session Manager 僅使用 in-memory Map 儲存 session（fire-and-forget 寫 Redis 但不讀回），session 在 process restart 後全部遺失 | 多實例水平擴展無法實現；process restart 所有用戶須重新開啟面板 | `admin/src/session/PanelSessionManager.ts`, `admin/src/session/AdminPanelSessionManager.ts` | PM:84-87, AM:94-96 |
| 20 | 骰子遊戲每次擲骰做兩次 DB round-trip 查詢同一帳戶：一次 `tryGetBalance`（顯示用）+ 一次 `creditReward` 內部的 `findOrCreate` | 每 1000 次擲骰產生 1000 次多餘查詢 | `economy/src/dice/services/dice-game-1-service.ts`, `dice-game-2-service.ts` | dg1:70-71, dg2:72-73 |
| 21 | Economy Drizzle schema 定義放在 `domain/schema.ts` 而非 `persistence/` 目錄 | ORM 定義與純 domain type 混在同一目錄，分層邊界模糊 | `economy/src/domain/schema.ts` | 全檔 178 行 |
| 22 | `EscortOptionCatalogRepository` 介面定義在 service 檔案而非 `repo/` 目錄 | Repository 介面從 service barrel export 匯出，違反依賴方向 | `dispatch/src/service/escort-option-pricing.service.ts` | 18-25 |
| 23 | `BaseAccountRepository` 使用大量 `any` 型別 escape（`table: any`、`as any` 共 4 處），規避 Drizzle 泛型約束 | 喪失 TypeScript 編譯期安全檢查 | `economy/src/common/base-account-repo.ts` | 17-18, 125, 160, 217 |
| 24 | Shop package.json 未宣告 `@ltdjms/economy` 依賴，spec 要求 shop 依賴 economy 的介面 | 依賴關係僅在 code 層面的 local interface 複製，未在 package.json 反映 | `shop/package.json` | - |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 全專案 100+ 檔案包含 "Matches Java Xxx" 註解，這些 Java 類別在 TypeScript 專案中不存在 | 對不熟悉 Java→TS 移植歷史的開發者造成混淆 | 所有 packages | - |
| 2 | `SlashCommandListener` 使用 discord.js v14 已棄用的 `isStringSelectMenu()` API | deprecation warning | `admin/src/commands/infra/SlashCommandListener.ts` | 110-114 |
| 3 | `MockDiscordEmbedBuilder` 在所有 packages 中零引用（含測試） | 未使用的 mock 增加維護負擔 | `shared/src/discord/mock/mock-discord-embed-builder.ts` | 11 |
| 4 | dispatch 與 admin 各自定義相同的 Discord embed 顏色常數（0x57F287/0xFEE75C/0xED4245） | 重複定義可能隨時間漂移 | `dispatch/src/constants.ts`, `admin/src/constants/colors.ts` | dispatch:2-4, admin:9-38 |
| 5 | Modal 建構程式碼在 4+ handler 中重複（`BalanceManagementHandler`、`EscortCatalogHandler`、`EscortPricingHandler` 等） | boilerplate 增加行數與審查負擔 | `admin/src/panel/admin/handlers/*.ts` | 多處 |
| 6 | `NodePgDatabase` 在多個檔案中以 value import 而非 `import type`（與 `verbatimModuleSyntax` 不相容） | 嚴格 ESM 模式下編譯失敗 | `economy/`, `dispatch/`, `shop/`, `ai/` 多個檔案 | 各檔案 L1-2 |
| 7 | Admin barrel export 匯出多個僅內部使用的型別（`AdminPanelViewState`、`PanelSessionData` 等） | 內部實作細節洩漏到公開 API | `admin/src/index.ts` | - |
| 8 | `AIChannelConfigChangedEvent` 定義在 `ai/src/events/index.ts` 但未從 package 主 `index.ts` 匯出 | 其他 package 無法 import 此 event type | `ai/src/events/index.ts`, `ai/src/index.ts` | events:21 |
| 9 | Admin panel handler 直接 import discord.js（`EmbedBuilder`、`ActionRowBuilder` 等）而非使用 `@ltdjms/shared` 的 `DiscordEmbedBuilder` 抽象層 | 展示層與 discord.js 耦合，升級 library 成本較高 | `admin/src/panel/admin/handlers/*.ts` | 多處 |
| 10 | `ecpay-checkmac.ts` + `url-encoder.ts` 的 URL 編碼做 15 次序向 regex replace | 每次 CheckMacValue 計算多餘的字串掃描 | `shop/src/crypto/ecpay-checkmac.ts`, `shop/src/crypto/url-encoder.ts` | checkmac:37-50, url:8-15 |
| 11 | ECPay AES 加解密使用 synchronous `crypto.createCipheriv()` 等 API，阻塞 event loop | 高併發 payment callback 時 event loop 延遲累積 | `shop/src/crypto/ecpay-aes.ts`, `shop/src/crypto/ecpay-checkmac.ts` | aes:34-44, checkmac:53 |
| 12 | `InMemoryToolCallHistory` 每 1 小時才清理過期 entries，過期資料最多保留 55 分鐘才被回收 | 長時間執行的 bot 記憶體使用偏高 | `ai/src/services/memory/tool-call-history.ts` | 98-105 |
| 13 | 客戶確認超時檢查使用 `>=` 而非 spec 描述的 `>`（邊界差異，實際上可忽略） | 在精確的 24h 邊界點行為稍有不同 | `dispatch/src/domain/escort-dispatch-order.ts` | 531 |
| 14 | 搜尋關鍵字 `decodeKeyword` 對損壞的 Base64 輸入無錯誤處理，`Buffer.from(invalid, 'base64')` 靜默產生亂碼 | 被篡改的 customId 產生無意義的搜尋結果 | `shop/src/view/shop-view.ts` | 27-28 |
| 15 | `FiatOrder` 使用 Zod schema `.refine()` runtime 驗證而非 compact constructor 模式（行為等價，機制不同） | 與 Java 原版的 constructor 驗證模式不同 | `shop/src/domain/fiat-order.ts` | 16-143 |

---

## 解決方案

### P0 修復

#### P0-1: DI 容器 DatabasePool 型別不匹配 — economy/dispatch/ai 模組 runtime crash

- **涉及檔案**：`admin/src/main.ts` > `main()`（L75）, `economy/src/di/economy-module.ts` > `configureEconomyContainer()`（L77）, `dispatch/src/di/dispatch-module.ts` > `configureDispatchContainer()`（L59）, `ai/src/di/ai-module.ts` > `initializeAIModule()`（L137）
- **根因**：`main.ts` L49 `createDatabasePool()` 回傳 raw `pg.Pool`，L57 `drizzle(pool)` 建立 Drizzle wrapper `db`，但 L75 傳入 `initializeContainer({ databasePool: pool })` 的是 raw Pool。Shop module 透過參數直接拿到正確的 `db`（L110-111），但 economy/dispatch/ai 模組透過 `container.resolve(TOKENS.DatabasePool)` 拿到的是 raw Pool，缺少 `.select()`、`.insert()`、`.update()`、`.delete()` 等 Drizzle 方法。
- **修復方案**：在 `main.ts` 中改為將 drizzle-wrapped `db` 註冊到 DI 容器：

  ```typescript
  // main.ts L75 — 改為傳入 drizzle-wrapped db
  initializeContainer({
    // ... other options
    databasePool: db as unknown as Pool,  // 或修改 TOKENS 型別
  });
  ```

  或更新 `TokenMap.DatabasePool` 的型別從 `Pool` 改為能同時相容 raw Pool 與 Drizzle wrapper 的型別。推薦方案：在各模組的 DI setup function 中自行建立 drizzle wrapper：

  ```typescript
  // economy-module.ts L77 — 改為
  import { drizzle } from 'drizzle-orm/node-postgres';
  const rawPool = container.resolve<Pool>(TOKENS.DatabasePool);
  const db = drizzle(rawPool);
  ```

- **驗證方式**：啟動應用程式，執行 economy/dispatch/ai 相關的整合測試（目前 650 個測試中未覆蓋此 DI 路徑），確認 `this.db.select()` 等 Drizzle 操作不回傳 TypeError。

### P1 修復

#### P1-1: userId 型別為 number 導致 Discord snowflake 精度遺失

- **涉及檔案**：`economy/src/events/index.ts` > event interfaces（L3-8）, `shared/src/types/events/domain-event.ts` > `DomainEvent`（L8）, 以及所有使用 `userId: number` 的 service 方法簽名
- **根因**：Discord snowflake ID 是 64-bit 整數，JavaScript `number`（IEEE 754 double）僅能精確表示 ≤ 2^53 的整數。Discord 用戶 ID 可達 ~9.2×10^18，超出安全範圍。
- **修復方案**：將所有 `userId` 欄位型別從 `number` 改為 `string`，與 `guildId` 保持一致。需修改：DomainEvent 基底介面、所有具體 event 介面（BalanceChangedEvent, GameTokenChangedEvent 等）、service 方法簽名、repository 參數型別。
- **驗證方式**：使用大於 `2^53` 的測試 snowflake（如 `"9999999999999999999"`）驗證所有 CRUD 操作和事件發布。

#### P1-2: 搜尋結果 select menu 截斷而非自動拆分

- **涉及檔案**：`shop/src/view/shop-view.ts` > `buildSearchModal` / select menu building（L395-403）
- **根因**：使用 `products.slice(0, 25)` 只取前 25 個商品建立單一 select menu，未實作 `splitSelectMenus` 或同等邏輯。
- **修復方案**：使用 `splitSelectMenus`（已存在於 `@ltdjms/shared`）或同等邏輯，當產品超過 25 個時建立多個 select menu：

  ```typescript
  const menus = splitSelectMenus(
    products.map(p => new StringSelectMenuOptionBuilder()
      .setLabel(p.name)
      .setValue(`product_${p.id}`)),
    'shop_search_buy_select',
    '選擇商品'
  );
  // 每個 menu 加入獨立的 ActionRow
  ```

- **驗證方式**：建立 26+ 個商品的測試場景，驗證搜尋結果顯示多個 select menu。

#### P1-3: 無限制並行 DM 發送觸發 Discord rate limit

- **涉及檔案**：`dispatch/src/notification/DispatchNotificationService.ts` > `notifyAfterSalesRequested()`（L163-167）, `filterOnlineStaff()`（L246-258）
- **根因**：使用 `Promise.all(targetIds.map(...))` 同時對所有售後人員發送 DM，無並行上限。Discord DM rate limit 約 5 messages/second/bot。
- **修復方案**：使用已存在的 `processWithConcurrencyLimit`（`@ltdjms/shared`）限制並行度為 3-5：

  ```typescript
  await processWithConcurrencyLimit(targetIds, 3, async (staffId) => {
    await this.sendDMEmbed(staffId, embed);
  });
  ```

- **驗證方式**：模擬 20+ 售後人員的 guild 觸發售後通知，確認不觸發 429 rate limit。

#### P1-4: SIGTERM 未清理 event listener 導致記憶體洩漏

- **涉及檔案**：`admin/src/main.ts` > SIGTERM handler（L157-166）, `admin/src/di/AdminModule.ts` > `disposeAdminContainer()`（L485-516）
- **根因**：SIGTERM handler 關閉 scheduler/cache/client/pool 但未呼叫 `disposeAdminContainer()`，該函數負責 `removeAllListeners()` 清理 event publisher。每次 nodemon restart listener 累積。
- **修復方案**：在 SIGTERM handler 中加入 `disposeAdminContainer()` 呼叫：

  ```typescript
  process.on('SIGTERM', async () => {
    // ... existing cleanup
    disposeAdminContainer();  // unregister listeners, stop intervals
    // ... pool.end(), process.exit()
  });
  ```

- **驗證方式**：多次觸發 SIGTERM 後檢查 `DomainEventPublisher.listenerCount()` 不增加。

#### P1-5: AdminPanelUpdateListener 每個事件做 O(3×sessions) Discord API 呼叫

- **涉及檔案**：`admin/src/panel/listeners/AdminPanelUpdateListener.ts` > `handleEvent()`（L106-154）
- **根因**：每個 domain event 對所有 active session 序向呼叫 `fetch channel → fetch message → edit`，無批次處理或合併。
- **修復方案**：
  1. 按 channelId 分組 sessions，每個 channel 只 fetch 一次
  2. 加入 debounce accumulator（key: `guildId:channelId:eventType`），快速連續事件合併成單次更新
  3. 使用 `Promise.allSettled` + concurrency limit 進行並行更新
- **驗證方式**：模擬 30 個 active sessions + 10 個快速連續事件，確認 Discord API 呼叫數顯著減少。

#### P1-6: PromptLoader 檔案讀取錯誤未優雅降級

- **涉及檔案**：`ai/src/prompts/prompt-loader.ts` > `loadPrompts()`（L83-84, 93-94）
- **根因**：目錄不存在時正確降級，但個別檔案讀取失敗或過大時 `throw` 錯誤向上傳播。spec R15.5 要求所有檔案讀取問題都優雅降級。
- **修復方案**：將 `PROMPT_READ_FAILED` 和 `PROMPT_FILE_TOO_LARGE` 的處理改為 `catch` block 中的 warn log + skip：

  ```typescript
  try {
    const content = await fs.readFile(filePath, 'utf-8');
    // ...
  } catch (e) {
    this.log.warn({ file: entry.name, error: e }, 'Failed to read prompt file, skipping');
    continue;
  }
  ```

- **驗證方式**：在 prompts 目錄中放置一個權限為 000 的檔案，確認 bot 正常啟動且 log 中有 warning。

#### P1-7: BalanceService.getBalance() 回傳型別與 spec 不符

- **涉及檔案**：`economy/src/currency/services/balance-service.ts` > `getBalance()`（L106）
- **根因**：spec R1.1 要求回傳 `Result<BalanceView, DomainError>`，但實作回傳 raw `Promise<BalanceView>`（Result 版本改名為 `tryGetBalance()`）。
- **修復方案**：有兩個方向 — (1) 將 `getBalance` 改名並讓新 `getBalance` 回傳 Result，或 (2) 更新 spec 承認兩個方法名稱。推薦方案 (1)：將現有 `getBalance` 改名為 `getBalanceUnchecked`，`tryGetBalance` 改名為 `getBalance`。
- **驗證方式**：更新所有 `getBalance` 呼叫端，確認型別檢查通過。

### P2 修復

#### P2-1: Agent 模式欄位未持久化

- **涉及檔案**：`ai/src/services/routing/agent-config-service.ts` > `AIAgentChannelConfigRepository` 介面（L17-32）, `AIAgentChannelConfigService.upsert()`（L88-107）
- **根因**：Repository 介面僅接受 `enabled: boolean`，DB schema `ai_agent_channel_config` 無 mode 欄位。
- **修復方案**：此項目需要 DB migration + 三層改動（repository interface + implementation + service），建議先記錄為已知限制，待 Agent 模式需求明確後再實作。
- **驗證方式**：DB migration 後檢查 schema，整合測試驗證 mode 欄位正確讀寫。

#### P2-2: splitSelectMenus / splitSelectMenusGeneric 死碼

- **涉及檔案**：`shared/src/discord/services/select-menu-util.ts`（全檔 78 行）
- **根因**：從 Java 移植的工具函數，在 TypeScript 版本中無消費者（所有 select menu 拆分已內嵌在各自的 handler 中）。
- **修復方案**：從 `shared/src/discord/index.ts` 的 barrel export 中移除，或保留以備未來使用（如 P1-2 修復時可能會用到）。建議保留並在 P1-2 修復中實際使用它。
- **驗證方式**：確認移除後全專案編譯通過。

#### P2-3: MessageChunkAccumulator 死碼

- **涉及檔案**：`ai/src/services/message-chunk-accumulator.ts`（全檔 35 行）
- **根因**：重構後留下的孤立類別，從未被 import。
- **修復方案**：刪除此檔案。
- **驗證方式**：確認刪除後全專案編譯通過。

#### P2-4: AIChannelConfigHandler 重複方法

- **涉及檔案**：`admin/src/panel/admin/handlers/AIChannelConfigHandler.ts` > `showCategoryConfig()`（L220-251）, `showChannelConfig()`（L253-282）
- **根因**：兩個方法邏輯完全相同（fetch allowedChannels + allowedCategories，build embed，send）。
- **修復方案**：合併為單一方法，透過參數區分顯示模式；或刪除其中一個，讓所有 caller 使用另一個。
- **驗證方式**：測試 AI 頻道設定面板的 channel 和 category 顯示功能。

#### P2-5～P2-24: 其他 P2 項目

其餘 P2 項目的修復方案詳見上方表格中的「問題描述」與「影響」欄位。關鍵修復方向：

- **P2-5** (DiceGame rollDice 重複): 提取為共用 `rollDice` 工具函數
- **P2-12** (Domain event as type assertion): 使用 properly-typed event construction
- **P2-13** (mapRowToDomain Record 型別): 改用 `EscortDispatchOrderSelect`
- **P2-14** (交易查詢無 LIMIT): 加入 `.limit(100)` + cursor-based pagination
- **P2-15** (Array.includes O(n^2)): 改用 `Set` 替代 `Array`
- **P2-18** (Redis circuit breaker 30s): 縮短為 5s 或移除 circuit breaker（ioredis 有內建 retry）
- **P2-19** (Session in-memory only): 改為 async `getSession()` + cache-aside read from Redis
- **P2-20** (雙重 DB round-trip): 讓 `tryBatchAdjust` 回傳 previous balance
- **P2-21** (Economy domain/schema.ts): 移動到 `persistence/` 目錄

### P3 改善

所有 P3 項目的改善方案詳見上方表格。優先級較高的改善：

- **P3-10** (15 次序向 regex): 合併為單次 `replace` with alternation + dictionary
- **P3-11** (Synchronous crypto): 改用 `crypto.webcrypto.subtle` 非同步 API
- **P3-12** (ToolCallHistory 1h eviction): 改為每 60 秒清理一次
- **P3-6** (NodePgDatabase value import): 改為 `import type`

---

## 總結

### 評分摘要

| 維度 | 評級 | 說明 |
|------|------|------|
| 功能完整性 | **良** | 核心功能齊全——31/31 DomainError categories、13/12+ DomainEvent types、17/17 AI tools、14/14 Markdown autofix steps、9/9 admin panel handlers、7/7 Facades、3/3 Notification services。P0 DI 問題除外。 |
| 規格符合度 | **良** | 主要偏差已在 P1/P2 中記錄——多數為可修復的型別/命名/錯誤處理差異，不影響核心業務邏輯。 |
| 架構品質 | **優** | Package 依賴圖嚴格遵守分層邊界。Facade 模式一致使用。DI 容器程式化註冊。無循環依賴。 |
| 程式碼品質 | **良** | 有部分死碼（~150 行）和重複邏輯（~100 行），不影響功能但增加維護負擔。 |
| 性能考量 | **良** | 有關鍵優化（request coalescing、concurrency limit、DB indexes），但存在數個 P1/P2 效能問題需在 production 前修復。 |
| 安全性 | **優** | 所有 AI tools 有 ADMINISTRATOR 授權檢查。ECPay 金鑰驗證。兌換碼 concurrent redeem 防護。無明顯安全漏洞。 |

### 建議行動優先級

1. **立即修復（P0）**：DI container DatabasePool 型別不匹配 — 阻止 production 部署
2. **本週修復（P1）**：userId number→string、搜尋 select menu 拆分、DM rate limit、SIGTERM listener cleanup、面板 API 呼叫優化、PromptLoader 降級、BalanceService 型別
3. **本迭代修復（P2 重點）**：Agent 模式欄位、死碼清理、交易查詢 LIMIT、O(n^2) Set 優化、session Redis cache-aside、Redis circuit breaker 調優
4. **後續改善（P3）**：Regex 合併、synchronous crypto 改用 async、註解清理、常數統一等
