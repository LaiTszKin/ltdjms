# Code Review Report

- **Spec**: TypeScript Native Port (typescript-native-port)
- **Date**: 2026-05-21
- **Reviewer**: QA Agent (6-dimension automated review)
- **Scope**: 6 模組 (shared-infrastructure, guild-economy, shop-payment, escort-dispatch, ai-chat-agent, administration)
- **Files Reviewed**: ~250 TypeScript source files across `packages/*/src/`

---

## 發現的問題

### P0 — 嚴重缺陷（影響功能正確性、安全性或資料完整性）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **兌換碼生成後不持久化到資料庫**：`handleGenerateCodes()` 在記憶體中生成兌換碼、發布事件、顯示給使用者，但從未調用 `redemptionCodeRepo` 的 save 方法。 | 兌換碼功能完全失效 | `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` | L470-518 |
| 2 | **GameRewardService 不更新餘額快取**：`creditReward()` 直接調用 `accountRepository.adjustBalance()` 繞過 `BalanceAdjustmentService`，不會更新 `cache:balance:{guildId}:{userId}`。玩家骰子遊戲後查詢 `/balance` 會顯示遊戲前的舊餘額（快取 TTL 300 秒）。 | 骰子遊戲後餘額顯示錯誤 | `packages/economy/src/dice/services/game-reward-service.ts` | L36-82 |
| 3 | **ECPay fetch() agent 選項對 undici 無效**：Node.js 原生 `fetch()` 基於 undici，不支援 `http.Agent` 的 `agent` 選項（被靜默忽略）。`keepAlive`、`maxSockets` 等連線池設定全部失效。 | ECPay API 每次建立新連線，可能導致連線耗盡 | `packages/shop/src/services/ecpay-cvs-payment.service.ts`、`packages/shop/src/services/ecpay-trade-query.service.ts` | L100-108, L60-68 |
| 4 | **ECPay timeout 錯誤類型不符**：`AbortSignal.timeout()` 產生的 DOMException 名稱是 `'TimeoutError'` 而非 `'AbortError'`。Timeout 錯誤永遠不會被特定捕獲，落入 generic catch 塊。 | ECPay 逾時錯誤分類錯誤，重試邏輯異常 | `packages/shop/src/services/ecpay-cvs-payment.service.ts`、`packages/shop/src/services/ecpay-trade-query.service.ts` | L167, L98 |
| 5 | **Shop buy select menu 無 25 選項上限**：`showBuySelection()` 載入全部商品（最多 100 個）後沒有截斷到 25 個選項。若商店超過 25 個商品，Discord API 會直接拒絕此 interaction。 | 商店購買功能在商品 >25 時完全失效 | `packages/shop/src/commands/shop-handler.ts` | L285-324 |
| 6 | **DiscordInteraction 介面缺少 `getChannelId()`**：Spec T7.1 要求 interface 包含該方法，但實際定義中沒有。Mock 實作有但生產程式碼 `DiscordJsInteraction` 無法呼叫。 | 生產程式碼無法從 interaction 取得 channelId | `packages/shared/src/discord/domain/discord-interaction.ts` | L5-41 |
| 7 | **AI_SERVICE_API_KEY 靜默預設空字串**：Zod schema `.default('')` 在缺少環境變數時靜默給空字串。Java 版本在 key 為 null/empty 時拋出 `IllegalStateException` 阻止啟動。 | 缺少 API key 時不快速失敗，以 401 延遲報錯 | `packages/shared/src/infra/config/schema.ts` | L54 |
| 8 | **BotErrorHandler 使用 `as never` 傳遞純物件給 `editEmbed`**：傳遞 `{ description, color, title }` 而非 `EmbedBuilder` 實例。 | 錯誤訊息可能無法送達使用者 | `packages/admin/src/commands/infra/BotErrorHandler.ts` | L46-50 |
| 9 | **Blockquote sanitize 吃掉必需空白**：Regex 替換將多層 `>` 替換為單一 `>` 時不保留後隨空白。`> text` 變成 `>text`，`>> some text` 變成 `>some text`。 | Markdown blockquote 格式錯誤 | `packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts` | L31-36 |
| 10 | **Streaming 雙回調 onChunk/onChunkWithType 競爭**：服務層在 stream 完成時同時調用兩者傳遞相同內容。Listener 用 `chunkTypeUsed` 互斥旗標防止重複處理，只是 workaround。 | 架構設計缺陷，可能導致顯示抖動與回調競爭 | `packages/ai/src/services/LangChainAIChatService.ts`、`packages/ai/src/commands/ai-chat-mention-listener.ts` | L321-328, L307-348 |

### P1 — 重要問題（影響功能完整性或邊界情況）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **管理面板即時更新按鈕切片錯誤**：`buildMainPanelEmbed()` 中 `buttons.slice(i, i + 5)` 應為 `i + 3`，導致第一列取 5 個、第二列重疊 index 3-4。 | 即時更新後管理面板按鈕佈局錯亂 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L213-218 |
| 2 | **AdminPanelUpdateListener 缺少 4 種事件監聽**：缺少 `ai_channel_config_changed`、`dispatch_after_sales_config_changed`、`escort_pricing_changed`、`escort_catalog_changed`。 | 上述設定變更後管理面板不會即時更新 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L231-244 |
| 3 | **產品面板 view state 從未正確設定**：`showProductDetail()` 從未調用 `setViewState(PRODUCT_DETAIL)`，`handleGenerateCodes()` 從未調用 `setViewState(PRODUCT_CODE_LIST)`。 | 即時更新監聽器無法區分當前視圖 | `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` | L74, L520, L470 |
| 4 | **產品 Modal 缺少圖片 URL 欄位**：`buildCreateProductModal()` 缺少 `imageUrl` 欄位。`zh-TW.ts` 已定義對應 i18n 字串但從未使用。 | 產品圖片 URL 功能缺失 | `packages/admin/src/panel/admin/product/AdminProductPanelModalFactory.ts` | L19-32 |
| 5 | **tryAdjustTokens 不記錄交易**：`tryAdjustTokens()` 只更新 cache 和發布事件，不記錄 `GameTokenTransaction`。交易記錄由 handler 在外部手動執行，缺乏原子性。 | 代幣變更與交易記錄可能不一致 | `packages/economy/src/token/services/game-token-service.ts` | L79-124 |
| 6 | **GameRewardService 缺少 CacheService 注入**：constructor 無 `CacheService` 和 `CacheKeyGenerator` 依賴。即使要修復 P0-2 也無法做到。 | DI 缺少必要依賴 | `packages/economy/src/di/economy-module.ts` | L136-141 |
| 7 | **Stale claim 無清理機制**：`claimFulfillmentProcessing` 設定後若 worker crash，`release` 不會執行。查詢條件為 `fulfillment_processing_at IS NULL`，訂單永久卡住。 | Worker crash 後訂單永久無法被處理 | `packages/shop/src/services/fiat-order-post-payment-worker.ts` | L86-168 |
| 8 | **ECPay callback server 缺少 EADDRINUSE 錯誤處理**：`app.listen()` 若 port 被佔用沒有 `.on('error', ...)` 監聽器。 | Callback server port 衝突時可能 process crash | `packages/shop/src/web/ecpay-callback-server.ts` | L93-98 |
| 9 | **排程 setInterval 可能重疊執行**：若 `processPendingOrders()` 執行超過 10 秒，`setInterval` 會觸發新的並行執行。對帳排程（60 秒間隔）有同樣問題。 | Worker 可能重疊執行浪費資源 | `packages/shop/src/services/fiat-order-processing-scheduler.ts` | L24-43 |
| 10 | **Escort confirmOrder 條件 UPDATE 偏離 spec**：repo 使用 `WHERE status='PENDING_CONFIRMATION' AND escort_user_id=?` 條件式 UPDATE，比 Java 和 spec 指定更嚴格。 | 行為與 Java 不完全一致（但這是安全性改善） | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L182-197 |
| 11 | **Repo 繞過 domain 轉換函數**：`assignEscort`、`claimAfterSales`、`confirmOrder`、`closeAfterSales` 等 repo 方法直接 SET 資料庫欄位，繞過 domain model 轉換函數。 | 領域狀態轉換邏輯散落在 persistence 層 | `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | L145-234 |
| 12 | **Escort confirmOrder 不清除未來狀態時間戳**：repo `confirmOrder` 只 SET `confirmedAt`、`status`、`updatedAt`，不清除 `completionRequestedAt`、`completedAt` 等 stale 時間戳。 | 狀態轉換後殘留 stale 時間戳 | `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | L196-201 |
| 13 | **AI Agent 缺少 per-tool 執行逾時**：`executeTool()` 沒有 abort controller 或 timeout wrapper。 | 單一慢速工具可永久阻塞 Agent 迴圈 | `packages/ai/src/services/LangChainAIChatService.ts` | L347-435 |
| 14 | **SearchMessagesTool 無頻道數量限制**：`channelIds` 未指定時遍歷所有文字頻道，每個頻道 `fetch({ limit: 100 })`。 | 大型伺服器可能觸發數百次 API 請求 | `packages/ai/src/tools/SearchMessagesTool.ts` | L59-85 |
| 15 | **AgentServiceFactory 和 MessageChunkAccumulator 完全未被使用**：factory 的 `createAgent()` 從未被呼叫，accumulator 被 import 但從未實例化。 | DI 註冊死碼，增加維護負擔 | `packages/ai/src/services/AgentServiceFactory.ts`、`packages/ai/src/services/MessageChunkAccumulator.ts` | L34-66 |
| 16 | **Callback body 不必要的 JSON round-trip**：Express middleware 已解析 body 為物件，handler 再 `JSON.stringify`。`parseFormBody()` 永遠不會被執行。 | 原始 form-urlencoded body 丟失 | `packages/shop/src/web/ecpay-callback-server.ts`、`packages/shop/src/services/fiat-payment-callback.service.ts` | L57, L158-221 |
| 17 | **dispatchCount 永遠為 0**：主面板直接設 `dispatchCount = 0` 從不查詢 dispatch service。 | 管理面板不顯示活躍護航訂單數 | `packages/admin/src/panel/admin/AdminPanelCommand.ts` | L45 |

### P2 — 一般問題（影響可維護性或程式碼品質）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **ECPay URL 編碼 `*` 字元與 Java 可能不一致**：Java `URLEncoder` 保留 `*` 原樣，`javaUrlEncode` 將其編碼為 `%2A`。需 golden-data cross-check 驗證。 | ECPay 加密結果可能與 Java 不一致 | `packages/shop/src/crypto/url-encoder.ts` | L5-17 |
| 2 | **Auto-completion 後無通知**：`ensureTimeoutCompletion` 成功後無 DM。`buildOrderTimedOutEmbed` 存在但從未被呼叫。 | 24h 超時自動完成後無人得知 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L477-498 |
| 3 | **Schema missing compound index**：缺少 spec T1.1 要求的 `(guildId, createdAt DESC)` 複合索引。 | `findRecentByGuildId` 查詢效能下降 | `packages/dispatch/src/schema/escort-dispatch-order.sql.ts` | L44-65 |
| 4 | **Notification 服務用 `as` 斷言存取 discord.js 內部**：違反 contract.md「不直接依賴 discord.js」約定。 | 抽象層邊界被破壞 | `packages/dispatch/src/notification/DispatchNotificationService.ts` | L251-257, L297-301 |
| 5 | **tryAdjustBalanceTo 重複查詢**：先 `findOrCreate` 再委派給 `tryAdjustBalance`（其內部又 `findOrCreate` 一次）。 | 不必要的資料庫往返 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L150, L59 |
| 6 | **DomainEventPublisher 同步 for-loop**：`publish()` 同步迭代 listeners，慢速 listener 會阻塞後續 listener。 | 同步分發瓶頸（但符合 spec 設計） | `packages/shared/src/infra/events/domain-event-publisher.ts` | L71-95 |
| 7 | **Session cleanup interval 無法停止**：interval ID 未被保存，應用關閉時無 `clearInterval`。 | 測試中可能 process hang | `packages/admin/src/session/AdminPanelSessionManager.ts` | L220-223 |
| 8 | **產品詳情缺少獨立「設定法幣價格」按鈕**：法幣價格只能透過通用編輯 Modal 修改。 | 功能不完整 | `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` | L549-575 |
| 9 | **Escort pricing/catalog 取消按鈕（_back）未被處理**：customId `admin_escortprice_back` / `admin_escortcatalog_back` 無 handler。 | 取消按鈕無效，落入 fallback | `packages/admin/src/panel/admin/handlers/EscortPricingHandler.ts`、`EscortCatalogHandler.ts` | L223, L329 |
| 10 | **雙重工具授權檢查**：工具內部 + `executeTool()` 各檢查一次 `validateAdministrator()`。 | 不必要的重複授權檢查 | `packages/ai/src/services/LangChainAIChatService.ts` | L383-391 |
| 11 | **DI 模組中 ChatOpenAI 被建立兩次**：`LangChainAIChatService` constructor 自建 + DI 註冊 singleton。兩個 HTTP agent。 | 資源浪費 | `packages/ai/src/di/ai-module.ts` | L292-301, L337-347 |
| 12 | **FiatOrder schema 註解聲稱 36 欄位但實際只有 31**：註解與實作不一致，需確認是否遺漏。 | 若缺失業務關鍵欄位可能影響功能 | `packages/shop/src/persistence/schema.ts` | L3-4 |

### P3 — 建議改善（影響程式碼風格或可讀性）

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **DomainError 分類數量文檔不一致**：Spec 說 27 個，實際和 Java 都是 28 個。 | 文件不一致（無功能影響） | `packages/shared/src/types/domain-error.ts` | (全檔) |
| 2 | **DB_POOL_MAX_SIZE 預設值**：Code 用 10（與 Java 一致），contract.md 建議 5。 | 可忽略的組態差異 | `packages/shared/src/infra/config/schema.ts` | L43 |
| 3 | **Redis retry 無限重試**：`maxRetriesPerRequest: null` 而非 contract 建議的 3。 | Redis 瞬斷時可能長時間重試 | `packages/shared/src/infra/cache/redis-cache-service.ts` | L17-22 |
| 4 | **adjustTokens 為 dead code**：throwing 路徑從未被呼叫。 | 未使用程式碼 | `packages/economy/src/token/services/game-token-service.ts` | L223-247 |
| 5 | **Dice 結果顯示不用 emoji**：使用數字而非 `:one:` ~ `:six:`。 | UX 與 Java 不一致 | `packages/economy/src/commands/dice-game-1-handler.ts` | L109 |
| 6 | **錯誤型別名稱與 spec 不同**：拋出 `DatabaseConnectionException` 而非 `SchemaMigrationException`（實際上是更好的設計）。 | 錯誤型別名稱差異 | `packages/shared/src/infra/database/connection.ts` | L47 |
| 7 | **OrderNumberGenerator 不接受 injectable Random**：直接使用 `crypto.randomInt()`，Java 版接受 `SecureRandom`。 | 測試無法控制訂單編號生成 | `packages/dispatch/src/domain/order-number-generator.ts` | L31-38 |
| 8 | **Callback server graceful shutdown 不等待現有連線**：`server.close()` 不等待。 | 正在處理的 callback 可能被中斷 | `packages/shop/src/web/ecpay-callback-server.ts` | L103-110 |
| 9 | **main.ts 位置與 spec 不同**：Spec 指定 `packages/shared/src/main.ts`，實際在 `apps/bot/src/main.ts`。 | 合理的 monorepo 決策但屬 spec 偏移 | `apps/bot/src/main.ts` | (全檔) |

---

## 解決方案

### P0 修復

#### P0-1: 兌換碼生成後不持久化

- **涉及檔案**：`packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts` > `handleGenerateCodes()`（L470-518）
- **根因**：生成兌換碼陣列後僅發布事件並回覆使用者，遺漏了 `redemptionCodeRepo.save()` 呼叫。
- **修復方案**：
  ```typescript
  // 在發布事件之前加入持久化（L491 之後）
  const codes: Array<{ code: string; redeemed: boolean }> = [];
  for (let i = 0; i < count; i++) {
    const code = this.codeGenerator.generate();
    codes.push({ code, redeemed: false });
  }
  await this.redemptionCodeRepo.saveBatch(productId, codes, note);  // 新增
  this.eventPublisher.publish({ ... } as RedemptionCodesGeneratedEvent);
  ```
- **驗證方式**：整合測試——生成兌換碼後調用 redemption service 的 redeem 方法確認可成功兌換。

#### P0-2: GameRewardService 不更新餘額快取

- **涉及檔案**：`packages/economy/src/dice/services/game-reward-service.ts`（L36-82）、`packages/economy/src/di/economy-module.ts`（L136-141）
- **根因**：`GameRewardService` 繞過 `BalanceAdjustmentService` 直接調用 repository，且未注入 `CacheService`。
- **修復方案**：
  ```typescript
  // 1. DI 模組加入 CacheService 和 CacheKeyGenerator 注入
  const gameRewardService = new GameRewardService(
    currencyAccountRepo, currencyTransactionService, eventPublisher,
    cacheService, cacheKeyGenerator,  // 新增
  );

  // 2. creditReward() 在 adjustBalance 成功後更新快取
  const cacheKey = this.cacheKeyGenerator.balanceKey(guildId, userId);
  await this.cacheService.put(cacheKey, result.balance, 300);
  ```
- **驗證方式**：單元測試——mock `CacheService`，驗證 `creditReward()` 完成後 `put()` 被呼叫且 key/value 正確。

#### P0-3/P0-4: ECPay fetch 問題（agent 無效 + timeout 錯誤類型）

- **涉及檔案**：`packages/shop/src/services/ecpay-cvs-payment.service.ts`、`packages/shop/src/services/ecpay-trade-query.service.ts`
- **P0-3 修復方案**：
  ```typescript
  // 使用 undici Dispatcher 替代 http.Agent
  import { Agent as UndiciAgent } from 'undici';
  const keepAliveDispatcher = new UndiciAgent({
    keepAlive: true, keepAliveMsecs: 30000, timeout: 15000,
    maxSockets: 10, maxFreeSockets: 5,
  });
  const response = await fetch(endpoint, { ..., dispatcher: keepAliveDispatcher });
  ```
- **P0-4 修復方案**：
  ```typescript
  } catch (e: any) {
    if (e.name === 'TimeoutError' || e.name === 'AbortError') { ... }
  }
  ```

#### P0-5: Shop buy select menu 無 25 選項上限

- **涉及檔案**：`packages/shop/src/commands/shop-handler.ts` > `showBuySelection()`（L285-324）
- **修復方案**：在 options 建構前加入 `.slice(0, 25)` 或使用 `@ltdjms/shared` 的 `splitSelectMenus` 分頁。

#### P0-6: DiscordInteraction 介面缺少 getChannelId()

- **涉及檔案**：`packages/shared/src/discord/domain/discord-interaction.ts`（L5-41）
- **修復方案**：在 interface 中新增 `getChannelId(): string;`，並在 `DiscordJsInteraction` 實作中加入對應方法。

#### P0-7: AI_SERVICE_API_KEY 靜默預設空字串

- **涉及檔案**：`packages/shared/src/infra/config/schema.ts`（L54）
- **修復方案**：將 `.default('')` 改為 `.min(1, 'AI_SERVICE_API_KEY is required')` 或在 `EnvironmentConfig.parse()` 中做 post-parse refinement 拒絕空字串。

#### P0-8: BotErrorHandler editEmbed 使用 as never

- **涉及檔案**：`packages/admin/src/commands/infra/BotErrorHandler.ts`（L46-50）
- **修復方案**：使用 `new EmbedBuilder().setTitle('錯誤').setDescription(message).setColor(0xED4245)` 建構正確的 EmbedBuilder 實例。

#### P0-9: Blockquote sanitize 吃掉空白

- **涉及檔案**：`packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts`（L31-36）
- **修復方案**：修正 regex 使其保留 `>` 後的空白，`> text` 保持為 `> text`，`>> some text` 變成 `> some text`。

#### P0-10: Streaming 雙回調競爭

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts`、`packages/ai/src/commands/ai-chat-mention-listener.ts`
- **修復方案**：合併 `onChunk` 和 `onChunkWithType` 為單一回調簽名，加入可選的 `chunkType` 參數，移除重複的完成回調。

### P1 修復

#### P1-1: 管理面板即時更新按鈕切片錯誤

- **涉及檔案**：`packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`（L213-218）
- **修復方案**：將 `buttons.slice(i, i + 5)` 改為 `buttons.slice(i, i + 3)`，與 `AdminPanelCommand.ts` L80-86 一致。

#### P1-2: AdminPanelUpdateListener 缺少 4 種事件監聽

- **涉及檔案**：`packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts`（L231-244）
- **修復方案**：在 `isAdminRelevantEvent()` 的 Set 中加入 4 種缺失的事件類型。

#### P1-3: 產品面板 view state 追蹤

- **涉及檔案**：`packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts`
- **修復方案**：在 `showProductDetail()` 中調用 `setViewState(PRODUCT_DETAIL)`，在 `handleGenerateCodes()` 中調用 `setViewState(PRODUCT_CODE_LIST)`。

#### P1-5: tryAdjustTokens 不記錄交易

- **涉及檔案**：`packages/economy/src/token/services/game-token-service.ts`（L79-124）
- **修復方案**：在 `tryAdjustTokens()` 內部加入 `gameTokenTransactionService.recordTransaction()` 呼叫，確保代幣變更與交易記錄的原子性。

#### P1-7: Stale claim 無清理機制

- **涉及檔案**：`packages/shop/src/services/fiat-order-post-payment-worker.ts`（L86-168）
- **修復方案**：在 `findOrdersPendingPostPayment()` 查詢中加入 `OR fulfillment_processing_at < now() - interval '5 minutes'`。

#### P1-10: Escort confirmOrder 條件 UPDATE 偏離 spec

- **涉及檔案**：`packages/dispatch/src/service/escort-dispatch-order.service.ts`（L182-197）
- **修復方案**：確認 spec 意圖——若需 100% Java 行為對齊則改用 `repo.update()`；若接受改良則更新 spec 記錄此決策。

#### P1-11: Repo 繞過 domain 轉換函數

- **涉及檔案**：`packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts`（L145-234）
- **修復方案**：重構 repo 使其接受 domain object。Service 層先調用 domain transition function 產生新狀態，再傳入 `repo.update(order)`。

#### P1-13/P1-14: AI Agent 逾時與頻道限制

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts`（L347-435）、`packages/ai/src/tools/SearchMessagesTool.ts`（L59-85）
- **修復方案**：`executeTool()` 加入 `AbortSignal.timeout(30000)`；`SearchMessagesTool` 加入最大頻道數上限。

### P2 修復

- **P2-2**：在 `ensureTimeoutCompletion` 成功後調用 `buildOrderTimedOutEmbed` 發送 DM 通知。
- **P2-3**：加入 `index('idx_guild_created').on(table.guildId, table.createdAt.desc())` 複合索引。
- **P2-7**：保存 cleanup interval ID 並在應用關閉時 `clearInterval()`。
- **P2-11**：讓 `LangChainAIChatService` 接受 DI 注入的 `sharedChatModel` 而非自行建立。
- **P2-12**：比對 Java `FiatOrder.java` 36 欄位確認 Drizzle schema 是否遺漏。

### P3 改善

- **P3-5**：將 dice 結果從數字改為 Discord emoji（`:one:` ~ `:six:`）匹配 Java UX。
- **P3-7**：為 `OrderNumberGenerator` 加入 injectable random function 參數提高可測試性。
- **P3-8**：為 callback server 加入 graceful shutdown。
- **P3-9**：確認 `main.ts` 位置從 `packages/shared/src/` 改為 `apps/bot/src/` 的架構決策並更新 spec。

---

## 統計摘要

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|----|----|----|----|------|
| shared-infrastructure | 2 | 0 | 1 | 3 | 6 |
| guild-economy | 1 | 2 | 1 | 2 | 6 |
| shop-payment | 3 | 4 | 2 | 2 | 11 |
| escort-dispatch | 0 | 3 | 3 | 1 | 7 |
| ai-chat-agent | 2 | 4 | 2 | 1 | 9 |
| administration | 2 | 4 | 3 | 0 | 9 |
| **總計** | **10** | **17** | **12** | **9** | **48** |

### P0 缺陷優先處理順序

1. **P0-1** — 兌換碼不持久化（功能完全失效，使用者影響最大）
2. **P0-2** — 骰子遊戲後餘額快取不更新（使用者可見的餘額顯示錯誤）
3. **P0-5** — Shop buy select menu 無 25 上限（商品 >25 時功能失效）
4. **P0-3/P0-4** — ECPay fetch 連線問題（影響法幣付款穩定度）
5. **P0-9** — Blockquote sanitize bug（AI 輸出格式錯誤）
6. **P0-6** — DiscordInteraction 缺 getChannelId()（基礎 API 不完整）
7. **P0-7** — AI API key 靜默預設（啟動時不快速失敗）
8. **P0-8** — BotErrorHandler as never（錯誤回覆可能失敗）
9. **P0-10** — Streaming 雙回調競爭（架構設計缺陷）
