# Code Review Report

- **Spec**: TypeScript Native Port (6 modules)
- **Date**: 2026-05-21
- **Reviewer**: QA Agent (multi-agent review)
- **審查範圍**: `packages/shared/`, `packages/economy/`, `packages/shop/`, `packages/dispatch/`, `packages/ai/`, `packages/admin/`
- **審查基準**: `docs/plans/2026-05-19/typescript-native-port/` 下 coordination.md + 6 組 spec 文件
- **審查維度**: 幻覺代碼、冗余代碼、Spec 偏移、Spec 遺漏、架構瑕疵、性能隱患

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | DiceGame2 `findStraights()` 使用排序去重後的 value-based 偵測，Java 原版使用 position-based 偵測。相同骰子序列產生完全不同的順子判定與獎勵。 | 遊戲結果與 Java 版本完全不一致，違反「功能 100% 一致」核心目標 | `packages/economy/src/dice/services/dice-game-2-service.ts` | L178-226 |
| 2 | `GameRewardService.creditReward()` 非零獎勵路徑缺少 `findOrCreate` 呼叫。若帳戶不存在，SQL UPDATE 匹配 0 行導致獎勵發放失敗。 | 首次獲得遊戲獎勵的用戶可能無法收到獎勵 | `packages/economy/src/dice/services/game-reward-service.ts` | L36-56 |
| 3 | `PAGE_SIZE = 10`，但 Spec R1.2 明確要求 `PAGE_SIZE = 5`（每頁最多 5 個商品）。直接影響商店 embed 分頁、按鈕 customId 頁碼計算、select menu 分割邏輯。 | 商店 UI 行為與 Java 版本不一致 | `packages/shop/src/services/shop.service.ts` | L28 |
| 4 | ECPay crypto（AES 加解密、CheckMacValue）缺少與 Java 原版的 byte-by-byte golden value 驗證測試。無法證明輸出與 Java 逐 byte 一致。 | 若 URL 編碼或 AES padding 有任何細微差異，法幣付款將完全無法運作 | `packages/shop/src/crypto/ecpay-aes.ts`, `ecpay-checkmac.ts` | 全部 |
| 5 | 所有貨幣/代幣值（`balance`, `amount`, `tokens`）使用 JS `number` (53-bit)，Java 原版使用 `long` (64-bit)。超過 `Number.MAX_SAFE_INTEGER` 的值會靜默精度損失。 | 大量獎勵累積時貨幣計算不正確 | `packages/economy/src/domain/types.ts` | L40,50,98,108 |
| 6 | Drizzle schema `bigint('column', { mode: 'number' })` 將 PostgreSQL 64-bit BIGINT 映射為 JS 53-bit number。讀取大數值時失真。 | 即使資料庫正確儲存大數值，讀回 JS 後精度損失 | `packages/economy/src/domain/schema.ts` | L19,33,53,80,101,125-144 |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `confirmOrder` 原子 UPDATE 的 WHERE 條件僅檢查 `id AND status`，未檢查 `escortUserId`。高併發下存在競爭窗口。 | 兩個護航者可能同時確認同一訂單 | `packages/dispatch/src/service/escort-dispatch-order.service.ts`, `drizzle-escort-dispatch-order.repo.ts` | L162-187, L47-77 |
| 2 | Drizzle schema 缺少 Java Flyway 定義的 CHECK 約束：dice game config 表缺 4+7 項約束（min/max/order）、transaction 表缺 `balance_after >= 0`。 | 資料完整性最後防線缺失 | `packages/economy/src/domain/schema.ts` | L124-147, L50-70, L97-118 |
| 3 | `javaUrlEncode` 缺少 `*` → `%2A` 編碼。Java `URLEncoder.encode` 會編碼 `*` 但 JS `encodeURIComponent` 不會。雖然功能上 ECPay 替代步驟會反轉此差異，但違反「逐 byte 一致」要求。 | AES ciphertext 與 Java 版本不完全一致 | `packages/shop/src/crypto/url-encoder.ts` | L5-16 |
| 4 | `DomainEvent.guildId` base interface 宣告為 `string`，但部分具體 event 重宣告不一致，且 domain model 使用 `number`。型別不一致。 | 事件發布/消費時需要型別轉換 | `packages/shared/src/types/events/domain-event.ts` | L8,17,23,30 |
| 5 | `createButtonView` 工廠函數和 `splitSelectMenusGeneric` 已實作但未從任何 barrel 匯出。外部模組無法透過 `@ltdjms/shared` 使用。 | 其他模組無法使用已實作功能，需重複開發 | `packages/shared/src/discord/domain/embed-view.ts`, `select-menu-util.ts`, `discord/index.ts`, `src/index.ts` | barrel exports |
| 6 | DI 容器的 `databasePool` 參數型別為 `unknown`，實際在 line 138 以 `as` 斷言轉為 `Pool`。完全繞過型別檢查。 | 傳入錯誤型別時只在 runtime 才發現 | `packages/shared/src/infra/di/container.ts` | L33, L138 |
| 7 | DI 容器 `TOKENS.EventListeners` 已在 `tokens.ts` 定義但從未被 `container.ts` 實際註冊。Event listeners 只能透過 `initializeContainer` options 手動傳入，無法透過 DI 動態註冊。 | 其他模組無法透過標準 DI 機制新增 listener | `packages/shared/src/infra/di/container.ts`, `tokens.ts` | — |
| 8 | `tryAdjustBalanceTo` 直接呼叫 `accountRepository.tryAdjustBalance()` 繞過 `tryAdjustBalance`，重複交易記錄/事件/快取邏輯。缺少對 delta 的 `isValidAdjustmentAmount` 檢查。 | 重複邏輯且繞過驗證 | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L135-216 |
| 9 | `GameTokenService.deductTokens()`（拋例外版本）不記錄 `GameTokenTransaction`。若任何程式碼直接呼叫此方法會丟失交易記錄。 | 審計記錄不完整 | `packages/economy/src/token/services/game-token-service.ts` | L193-212 |
| 10 | Redis 連線錯誤時操作可能 hang：ioredis `maxRetriesPerRequest: null` 設定下，`get()`/`put()` 若 Redis 無法連線會無限期等待。缺少操作級別超時。 | Redis 不可用時請求可能 hang 而非快速降級 | `packages/shared/src/infra/cache/redis-cache-service.ts` | L17-19 |
| 11 | Agent 模式中 `onChunk` 和 `onChunkWithType` 皆有內容累積邏輯但無互斥保護。若兩者同時被呼叫會導致內容重複。 | Agent 回應內容可能重複 | `packages/ai/src/commands/ai-chat-mention-listener.ts` | L174-277 |
| 12 | `DomainEventPublisher.publish()` 內有冗餘 try/catch。`register()` 已包裝 listener 含完整錯誤處理，外層 try/catch 在正常流程中永遠不會觸發。 | 防禦性程式碼但可能隱藏問題 | `packages/shared/src/infra/events/domain-event-publisher.ts` | L79-88 |
| 13 | `MockDiscordInteraction._channelId` 建構子參數被接收但從未儲存或使用。呼叫方傳入的 channelId 被默默忽略。 | Mock 行為不符合預期 | `packages/shared/src/discord/mock/mock-discord-interaction.ts` | L24 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `DispatchPanelInteractionHandler` 六個通知處理方法與對應的面板按鈕處理方法邏輯幾乎完全相同，僅 UI embed 構建不同。 | 程式碼重複約 200 行 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L617-775 |
| 2 | `AdminPanelSessionManager` 與 `PanelSessionManager` 大量重複（TTL 過期、Map 儲存、CRUD、cleanup），僅 session data 型別不同。 | 程式碼重複約 150 行 | `packages/admin/src/session/AdminPanelSessionManager.ts`, `PanelSessionManager.ts` | 全部 |
| 3 | `MarkdownValidatingAIChatService.onChunk` 和 `onChunkWithType` 內容累積與 flush 邏輯重複。 | 維護性降低 | `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` | L153-237 |
| 4 | `DiscordMarkdownStreamProcessor` 的 pipeline 與 `MarkdownValidatingAIChatService.applyPipeline` 重複相同的 Sanitize→AutoFix→Validate→Paginate 步驟。 | 管線邏輯重複 | `packages/ai/src/markdown/services/DiscordMarkdownStreamProcessor.ts`, `MarkdownValidatingAIChatService.ts` | L52-71, L244-263 |
| 5 | `AdminPanelUpdateListener.buildMainPanelEmbed` 中 `dispatchCount` 硬編碼為 0（TODO P1-37）。管理面板主畫面永不顯示實際待派單數量。 | 管理面板資訊不完整 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L177 |
| 6 | `GameTokenManagementFacade.adjustTokens` 丟棄 `reason` 和 `actorId` 審計資訊（TODO P1-34）。 | 審計追蹤不完整 | `packages/admin/src/facades/GameTokenManagementFacade.ts` | L54-65 |
| 7 | `AIConfigManagementFacade.enableAgent` 忽略 `mode` 參數（TODO P1-36, P2-8），實際上只執行 boolean 開關。 | Agent 模式選擇被丟棄 | `packages/admin/src/facades/AIConfigManagementFacade.ts` | L120-125 |
| 8 | Spec 記載 FiatOrder 有 "36 columns" 但實際 schema 僅定義 31 欄位（Zod schema 也是 31）。需確認是 spec 筆誤還是遺漏。 | 不確定性——需與 Java 核對 | `packages/shop/src/domain/fiat-order.ts` | L16-58 |
| 9 | `paymentMessage` (varchar 512) 和 `terminalReason` (varchar 128) 缺少 Zod 長度驗證。依賴 DB constraint 防護。 | ECPay 回傳超長訊息時 DB insert 失敗 | `packages/shop/src/domain/fiat-order.ts` | L41, L45 |
| 10 | `DomainError` 建構子驗證不一致：`category` 檢查 `=== null || === undefined`，`message` 使用 `!message`（含空字串）。 | 驗證風格不統一 | `packages/shared/src/types/domain-error.ts` | L47-52 |
| 11 | `EmbedLimits` 介面已實作但未從 barrel 匯出。 | 外部模組無法使用 | `packages/shared/src/discord/services/embed-pagination.ts` | barrel exports |
| 12 | `EscortDispatchOrderService` 建構子內 TypeScript `private readonly` 自動賦值後又手動重複賦值。 | 雙重賦值 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L50-53 |
| 13 | `showBuySelection` 僅從第一頁載入商品。若商店超過 PAGE_SIZE 個商品，使用者無法從購買選單中選擇後面頁面的商品。 | 購買選單不完整 | `packages/shop/src/commands/shop-handler.ts` | L289 |
| 14 | `checkAdminPermission` 方法僅調用 `isAdministrator()`，完全忽略傳入的 `context`/`guildId` 參數。Spec R14.1 提到也應檢查 guild owner。 | 權限檢查可能不完整 | `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` | L781-788 |
| 15 | Callback server 路由處理器未傳遞實際 `Content-Type` header 給 callback service（傳入 `null`）。Service 內部依賴 body 內容模式判斷格式。 | 非標準格式 JSON 可能誤判 | `packages/shop/src/web/ecpay-callback-server.ts` | L57-58 |
| 16 | `validateRequiredTimestamps` 對 `AFTER_SALES_CLOSED` 狀態缺少 `afterSalesRequestedAt` 檢查。 | 防禦性驗證不完整 | `packages/dispatch/src/domain/escort-dispatch-order.ts` | L133-136 |
| 17 | `AdminPanelUpdateListener` 建構子僅注入 `CurrencyManagementFacade`，但對非 MAIN 視圖狀態的更新實際上只做 no-op 編輯而不刷新資料。 | 即時更新對非主面板視圖無效 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L49-55, L111-117 |
| 18 | Transaction 記錄順序與 Java 不同（Java: cache→event→transaction，TS: transaction→event→cache）。TS 順序更安全但與 Java 行為不一致。 | 行為差異（合理改進） | `packages/economy/src/currency/services/balance-adjustment-service.ts` | L86-109 |
| 19 | DiceGameConfig handler 中 `maxTokens <= minTokens` 拒絕了合法配置 `maxTokens == minTokens`（應允許固定代幣數）。 | 拒絕合法配置 | `packages/economy/src/commands/dice-config-handlers.ts` | L49, L144 |
| 20 | `EscortDispatchOrderService.repo/update()` 的 SET 子句包含不變欄位（`escortUserId`, `assignedByUserId`）。狀態轉換時這些欄位不應改變。 | 不必要的資料庫寫入 | `packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts` | L57-68 |
| 21 | 測試檔案 `payment-callback.test.ts` 以三個參數呼叫 `handleCallback(requestBody, contentType, extraArg)`，但實際簽名僅接受兩個參數。 | 測試型別檢查可能被繞過 | `packages/shop/src/__tests__/payment-callback.test.ts` | L29,39,48,58-63 |
| 22 | `MarkdownAutoFixer` 介面僅有單一實作 `RegexBasedAutoFixer`，但消費端直接依賴具體類別而非介面。 | 介面層失去價值 | `packages/ai/src/markdown/autofix/` | — |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `readFileSync` 同步讀取 `.env`。僅啟動時執行一次，影響極小。 | 網路磁碟部署時啟動延遲 | `packages/shared/src/infra/config/env-loader.ts` | L14 |
| 2 | pino import 模式在整個 shared 模組中不一致（Pattern A vs Pattern B vs namespace 存取）。 | 程式碼風格不統一 | `packages/shared/src/infra/` 多個檔案 | — |
| 3 | `as Logger` 冗余型別斷言（`pino({ level: 'silent' })` 已經是 `Logger` 型別）。 | 微小整潔度問題 | `packages/shared/src/infra/config/environment-config.ts`, `redis-cache-service.ts`, `migration-runner.ts` | L24, L16, L19 |
| 4 | `requireField` 函數錯誤訊息缺少當前 status 值（`"${name} must not be null for status"`）。 | 不利除錯 | `packages/dispatch/src/domain/escort-dispatch-order.ts` | L140-143 |
| 5 | MerchantTradeNo 序號計數器為靜態類別屬性，僅在單一 Node.js 進程內保證不重複。多 worker 部署時可能重複。 | 水平擴展時有風險 | `packages/shop/src/services/ecpay-cvs-payment.service.ts` | L209-233 |
| 6 | `notifyGuildAdmins` 遍歷全部 guild members cache 尋找 ADMINISTRATOR 權限者。大型 guild 可能耗時。 | 大型 guild 效能（fire-and-forget 不阻塞） | `packages/shop/src/services/shop-admin-notification.service.ts` | L72-85 |
| 7 | `AdminPanelUpdateListener.onEvent` 對每個活躍 session 執行 Discord API 呼叫（fetch channel/message）。高事件頻率可能觸發 rate limit。 | 大量事件時的 rate limit 風險 | `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` | L61-151 |
| 8 | `findRecentOrders` 對每個 `PENDING_CUSTOMER_CONFIRMATION` 訂單觸發獨立 DB UPDATE。多個超時訂單時產生 N 次 UPDATE。 | 批次處理缺失 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L398-416 |
| 9 | `CommonMarkValidator.regexFormatPass` 對每行執行 5+ 個 regex 測試。長篇 AI 回應可能消耗可觀 CPU。 | 高頻 AI 請求場景的 CPU 瓶頸 | `packages/ai/src/markdown/validation/CommonMarkValidator.ts` | L326-465 |
| 10 | `DiscordMarkdownPaginator` 每次分頁都執行全頁行尾空白 regex 清除。多頁內容時重複執行。 | 應移至 sanitize 階段只執行一次 | `packages/ai/src/markdown/services/DiscordMarkdownPaginator.ts` | L193 |
| 11 | `SlashCommandMetrics` ring buffer 預先填充 1000 個 0。動態 command 場景下記憶體浪費。 | 記憶體效率 | `packages/admin/src/commands/infra/SlashCommandMetrics.ts` | L42-43 |
| 12 | `CurrencyConfigService.validateName/validateIcon` 使用 `throw new Error()` 而非 `DomainError.invalidInput()`。 | 錯誤型別不一致 | `packages/economy/src/currency/services/currency-config-service.ts` | L148, L168 |
| 13 | `DrizzleProductRepository` 從 `di/shop-module.js` 匯入 `ProductRepository` 介面型別。語意上 persistence 應依賴 domain 而非 DI。 | 依賴方向語意不清 | `packages/shop/src/persistence/drizzle-product-repository.ts` | L5 |
| 14 | `FiatOrderResult.fulfillmentWarning` 欄位恆為 null，無任何邏輯填充。 | 無用欄位 | `packages/shop/src/services/fiat-order.service.ts` | L131 |
| 15 | `ShopPageHelper.hasPreviousPage/hasNextPage` 已定義但 handler 中未使用，handler 自行內聯分頁邏輯。 | 無用 helper | `packages/shop/src/services/shop.service.ts` | L14-19 |
| 16 | `GameTokenService.getBalance()` 不會自動建立帳戶（使用 `findByGuildIdAndUserId` 而非 `findOrCreate`），但 Spec R4.1 要求與貨幣系統「對稱」。 | 行為與 spec 描述不一致 | `packages/economy/src/token/services/game-token-service.ts` | L67-80 |
| 17 | `BalanceHandler` 使用 `Number(interaction.getGuildId())` 轉換 Discord snowflake。超大 ID 可能失去精度。 | 精度風險（通用問題） | `packages/economy/src/commands/balance-handler.ts` | L21-22 |
| 18 | Handler 和 Service 層都對 tokenCount 進行範圍驗證（dice-game-1/2）。合理的 defense-in-depth 但造成重複。 | 輕微重複 | `packages/economy/src/commands/dice-game-1-handler.ts`, `dice-game-1-service.ts` | 多處 |
| 19 | `BotErrorHandler.toUserMessage` 中 `instanceof DiscordAPIError` 檢查和 `isDiscordApiError` 型別守衛檢查的錯誤碼查找邏輯完全相同。 | 可合併 | `packages/admin/src/commands/infra/BotErrorHandler.ts` | L63-118 |
| 20 | `ConversationMessage` 和 `ProductRedemptionTransaction` 仍帶有 `// TODO: fill fields once the corresponding module is ported` 註解。若對應模組已完成移植，應解決這些 TODO。 | 可能已過時的 TODO | `packages/shared/src/types/events/domain-event.ts` | L78, L115 |
| 21 | `DomainEventPublisher` 預設 logger level 為 `'warn'`，但其他 shared 組件使用 `'silent'`。預設 level 策略不一致。 | 行為不一致 | `packages/shared/src/infra/events/domain-event-publisher.ts` | L22 |
| 22 | `findStraights` 排序去重後標記使用 `break`（只標記第一個匹配的原始骰子），遺漏重複值的後續出現。 | 與已報告 P0-3 相關——若修正為 position-based 則此問題自然解決 | `packages/economy/src/dice/services/dice-game-2-service.ts` | L213-218 |

---

## 解決方案

### P0 修復

#### P0-1: DiceGame2 Straight 偵測邏輯錯誤

- **涉及檔案**：`packages/economy/src/dice/services/dice-game-2-service.ts` > `findStraights()` (L178-226)
- **根因**：TypeScript 實作將骰子值排序並去重後檢查數值連續遞增（value-based），但 Java 原版是基於原始擲骰位置順序檢查相鄰位置的值是否遞增 1（position-based）。兩種演算法在相同輸入下產出完全不同的順子判定。
- **修復方案**：將 `findStraights` 改為 position-based 邏輯：
  1. 移除排序和去重步驟
  2. 直接在原始 `diceRolls` 陣列上按索引掃描，檢查 `diceRolls[i+1] === diceRolls[i] + 1`
  3. 長度 ≥3 的連續遞增序列即為 straight
  4. 標記對應索引的 `usedInStraight`
- **驗證方式**：
  1. 使用固定 seed (e.g., `seed=42`) 產生骰子序列，比對 TypeScript 和 Java 的獎勵輸出
  2. 測試以下邊界案例: `[3,5,1,2,4,6]`（正確應無順子）、`[1,2,3,5,6]`（straight=3）、`[1,1,2,3,4]`（straight=4, 僅標記一個1）

#### P0-2: GameRewardService.creditReward() 缺少帳戶建立

- **涉及檔案**：`packages/economy/src/dice/services/game-reward-service.ts` > `creditReward()` (L36-56)
- **根因**：Java 版在非零獎勵路徑中先透過 `findOrCreate` 確保帳戶存在。TypeScript 版缺少此呼叫，直接進入 `adjustBalance`（conditional UPDATE），若帳戶不存在 SQL UPDATE WHERE 匹配 0 行。
- **修復方案**：在非零獎勵路徑的 `applyRewardToAccount` 呼叫前加入 `await this.accountRepository.findOrCreate(guildId, userId)`。
- **驗證方式**：單元測試——對不存在帳戶的用戶直接呼叫 `creditReward(guildId, userId, 1000, source)`，驗證帳戶被自動建立且獎勵正確入帳。

#### P0-3: PAGE_SIZE 與 Spec 不符

- **涉及檔案**：`packages/shop/src/services/shop.service.ts` > `PAGE_SIZE` (L28)
- **根因**：程式碼設定 `PAGE_SIZE = 10`，Spec R1.2 明確要求 `PAGE_SIZE = 5`。
- **修復方案**：將 `export const PAGE_SIZE = 10` 改為 `export const PAGE_SIZE = 5`。
- **驗證方式**：確認商店 embed 每頁最多顯示 5 個商品，翻頁按鈕 customId 的頁碼計算與 Java 一致。

#### P0-4: ECPay crypto 缺少 golden value 驗證

- **涉及檔案**：`packages/shop/src/crypto/ecpay-aes.ts`, `ecpay-checkmac.ts` 及對應測試
- **根因**：測試僅驗證 roundtrip 和格式，缺少使用 Java 原版已知輸入/輸出 pair 的 byte-by-byte 比對。
- **修復方案**：
  1. 從 Java 測試（`FiatPaymentCallbackServiceTest`、`EcpayTradeQueryServiceTest`）擷取已知的 (input, encrypted, decrypted) golden pair
  2. 在 `ecpay-crypto.test.ts` 中加入 byte-by-byte assertion：`expect(encryptAES(knownPlain, key, iv)).toBe(knownEncrypted)`
  3. 加入 CheckMacValue golden value assertion: `expect(buildCheckMacValue(knownParams, key, iv)).toBe(knownCheckMacValue)`
- **驗證方式**：所有 golden value 測試通過。

#### P0-5: JS number 精度限制（貨幣值）

- **涉及檔案**：`packages/economy/src/domain/types.ts` (L40,50,98,108), `schema.ts` (L19,33,53,80,101,125-144)
- **根因**：JS `number` 是 IEEE 754 double (53-bit mantissa)，Java `long` 是 64-bit。超過 `2^53` 的數值會靜默精度損失。
- **修復方案**：
  1. 短期：在所有算術操作中加入 `Number.isSafeInteger()` 檢查，超過安全範圍時拒絕操作並回傳 `DomainError`
  2. 長期：將所有 monetary 值改為 `bigint`，Drizzle schema 改用 `mode: 'bigint'`
- **驗證方式**：測試大額交易（超過 `Number.MAX_SAFE_INTEGER`）被正確拒絕。

#### P0-6: Drizzle bigint mode: 'number' 精度損失

- **涉及檔案**：`packages/economy/src/domain/schema.ts` (L19,33,53,80,101,125-144)
- **根因**：`bigint('column', { mode: 'number' })` 將 PostgreSQL 64-bit BIGINT 對映為 JS 53-bit number。
- **修復方案**：改用 `mode: 'bigint'` 讓 Drizzle 回傳 `bigint` 型別，需連同 P0-5 一起處理（統一使用 `bigint`）。
- **驗證方式**：整合測試——向 DB 寫入大於 `Number.MAX_SAFE_INTEGER` 的值，讀回後值完全一致。

### P1 修復

#### P1-1: confirmOrder 原子 UPDATE 缺少 escortUserId 條件

- **涉及檔案**：`packages/dispatch/src/service/escort-dispatch-order.service.ts` > `confirmOrder()` (L162-187), `drizzle-escort-dispatch-order.repo.ts` > `update()` (L47-77)
- **根因**：`repository.update()` 的 WHERE 僅檢查 `id=X AND status=expectedStatus`，未加 `escortUserId` 條件。
- **修復方案**：為 `confirmOrder`、`requestCompletion` 等操作加入專用原子 update 方法，WHERE 條件加上 `AND escort_user_id = ?`（或讓 update 方法接受額外的 WHERE 條件參數）。
- **驗證方式**：並發測試——兩個不同的護航者同時嘗試確認同一訂單，只有一人成功。

#### P1-2: Drizzle Schema 缺少 CHECK 約束

- **涉及檔案**：`packages/economy/src/domain/schema.ts` (L124-147, L50-70, L97-118)
- **根因**：Java Flyway migration 定義的 CHECK constraints 在 Drizzle schema 中未宣告。
- **修復方案**：使用 Drizzle `check()` API 加入所有對應約束：
  - `diceGame1Config`: `check('min_non_negative', sql`min_tokens_per_play >= 0`)`, 等 4 項
  - `diceGame2Config`: 對應 7 項
  - `currencyTransaction`: `check('balance_non_negative', sql`balance_after >= 0`)`
  - `gameTokenTransaction`: 同上
- **驗證方式**：`pnpm drizzle-kit generate` 確認生成的 migration SQL 包含所有 CHECK constraints。

#### P1-3: javaUrlEncode 缺少 * 編碼

- **涉及檔案**：`packages/shop/src/crypto/url-encoder.ts` > `javaUrlEncode()` (L5-16)
- **根因**：Java `URLEncoder.encode` 會將 `*` 編碼為 `%2A`，但 JS `encodeURIComponent` 將 `*` 視為 unreserved 字元不編碼。雖然功能上 ECPay 替代步驟會反轉此差異，但產生的中間 ciphertext 與 Java 版本不一致。
- **修復方案**：在 `javaUrlEncode` 中加入 `.replace(/\*/g, '%2A')`。
- **驗證方式**：`ecpay-crypto.test.ts` L129 的測試 `expect(javaUrlEncode('test *A*')).toBe('test+%2AA%2A')` 通過。

#### P1-4: DomainEvent guildId 型別不一致

- **涉及檔案**：`packages/shared/src/types/events/domain-event.ts` (L8,17,23,30)
- **根因**：base interface `guildId: string`，但 domain model 各處使用 `number`。
- **修復方案**：統一為 `string`（符合 discord.js snowflake 慣例）或 `bigint`（與 Java long 對齊）。全專案一次性批量修改。
- **驗證方式**：TypeScript 編譯無型別錯誤。

#### P1-5: createButtonView 和 splitSelectMenusGeneric 未匯出

- **涉及檔案**：`packages/shared/src/discord/domain/embed-view.ts`, `select-menu-util.ts`, `discord/index.ts`, `src/index.ts`
- **根因**：barrel export 檔案遺漏了這兩個公開函數。
- **修復方案**：在 `discord/index.ts` 和 `src/index.ts` 中加入 `createButtonView` 和 `splitSelectMenusGeneric` 的匯出。
- **驗證方式**：`import { createButtonView, splitSelectMenusGeneric } from '@ltdjms/shared'` 成功。

#### P1-6: DI container databasePool 型別為 unknown

- **涉及檔案**：`packages/shared/src/infra/di/container.ts` (L33, L138)
- **根因**：避免循環依賴或型別推斷問題？但使用 `unknown` + `as` 斷言繞過型別安全。
- **修復方案**：將 `databasePool` 參數型別改為 `Pool`（從 `pg` import）。
- **驗證方式**：TypeScript 編譯無型別錯誤。

#### P1-7: DI 容器未實作 TOKENS.EventListeners multi-registration

- **涉及檔案**：`packages/shared/src/infra/di/container.ts`, `tokens.ts`
- **根因**：`TOKENS.EventListeners` 已定義但 `container.ts` 未實際使用它進行動態 listener 註冊。
- **修復方案**：在 `initializeContainer` 中加入對 `TOKENS.EventListeners` 的 resolve + register 邏輯；或從 tokens.ts 移除未使用的 token 並在 spec 記錄替代方案。
- **驗證方式**：外部模組可透過 `container.registerInstance(TOKENS.EventListeners, [listener1, listener2])` 註冊 listener。

#### P1-8: tryAdjustBalanceTo 重複邏輯繞過驗證

- **涉及檔案**：`packages/economy/src/currency/services/balance-adjustment-service.ts` (L135-216)
- **根因**：`tryAdjustBalanceTo` 自行呼叫 repository 層並重複交易記錄/事件/快取邏輯，繞過 `tryAdjustBalance` 的 `isValidAdjustmentAmount` 檢查。
- **修復方案**：計算 delta 後委託給 `tryAdjustBalance`，或在計算 delta 後手動加入 `isValidAdjustmentAmount(delta)` 檢查。
- **驗證方式**：單元測試——`tryAdjustBalanceTo` 的 delta 超過 `MAX_ADJUSTMENT_AMOUNT` 時被正確拒絕。

#### P1-9: GameTokenService.deductTokens() 不記錄交易

- **涉及檔案**：`packages/economy/src/token/services/game-token-service.ts` (L193-212)
- **根因**：拋例外版本的 `deductTokens()` 未呼叫 transaction repository 記錄交易。
- **修復方案**：加入交易記錄呼叫；或將此方法標記為 `@deprecated` 確保所有呼叫者使用 `tryDeductTokens`。
- **驗證方式**：呼叫 `deductTokens()` 後驗證 `GameTokenTransaction` 已寫入資料庫。

#### P1-10: Redis 連線 hang 風險

- **涉及檔案**：`packages/shared/src/infra/cache/redis-cache-service.ts` (L17-19)
- **根因**：ioredis `maxRetriesPerRequest: null` 設定下操作會無限期重試。缺少連線和操作級別超時。
- **修復方案**：
  1. 加入 `connectTimeout: 5000`
  2. 設定 `enableOfflineQueue: false` 讓操作在 Redis 不可用時快速失敗
  3. 或在 `get()`/`put()` 中加入 `Promise.race` 超時包裝
- **驗證方式**：模擬 Redis 不可用情境，驗證 `get()` 在 5 秒內回傳 `null` 而非 hang。

#### P1-11: Agent chunk 處理 onChunk/onChunkWithType 互斥缺失

- **涉及檔案**：`packages/ai/src/commands/ai-chat-mention-listener.ts` (L174-277)
- **根因**：兩個 handler 都累積內容但無互斥保護，若被同時呼叫會重複內容。
- **修復方案**：加入互斥旗標，或明確文件說明 LangChain 實作只會呼叫其中一個。
- **驗證方式**：確認 `LangChainAIChatService` 在 agent 模式下始終使用 `onChunkWithType`。

#### P1-12/13: 冗余 try/catch 和 Mock 參數忽略

- **涉及檔案**：`packages/shared/src/infra/events/domain-event-publisher.ts` (L79-88), `packages/shared/src/discord/mock/mock-discord-interaction.ts` (L24)
- **修復方案**：P1-12 加入註解說明防禦性角色或移除。P1-13 儲存 `_channelId` 並在 getter 回傳。
- **驗證方式**：對應的單元測試。

### P2 修復

#### P2-1~4: 程式碼去重（通知處理、SessionManager、Markdown pipeline）

- **涉及檔案**：
  - `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` (L617-775)
  - `packages/admin/src/session/AdminPanelSessionManager.ts`, `PanelSessionManager.ts`
  - `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts` (L153-237)
  - `packages/ai/src/markdown/services/DiscordMarkdownStreamProcessor.ts` (L52-71)
- **建議改善**：
  - Dispatch: 將六個通知處理方法與面板按鈕處理方法共用核心業務邏輯
  - Admin: 抽取泛型基底類別 `SessionManager<T>`
  - AI: 將 Markdown pipeline 抽取到共用工具類別

#### P2-5~7: 已知 TODO 功能缺口

- **涉及檔案**：
  - `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` (L177): `dispatchCount` 硬編碼為 0
  - `packages/admin/src/facades/GameTokenManagementFacade.ts` (L54-65): 丟棄 reason/actorId
  - `packages/admin/src/facades/AIConfigManagementFacade.ts` (L120-125): 忽略 mode 參數
- **建議改善**：補上對應的 service 呼叫或資料注入。

#### P2-8~22: 其他一般問題

- 各項問題的具體修復建議見上表「影響」欄。多數為程式碼整潔度、型別安全、barrel export 完整性改善。

### P3 改善

- 共 22 項建議改善，多為程式碼風格統一、效能優化、防禦性程式設計增強。詳見上表。
- 優先處理的 P3 項目：
  - P3-5: 若計劃水平擴展，MerchantTradeNo 序號計數器需改為跨進程安全方案
  - P3-7: 高事件頻率場景的 panel 更新去抖動
  - P3-9: 長篇 AI 回應的 regex 效能優化
  - P3-20: 解決已過時的 TODO 註解

---

## 總結統計

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|-----|-----|-----|-----|------|
| shared-infrastructure | 0 | 5 | 3 | 4 | 12 |
| guild-economy | 2 | 3 | 2 | 5 | 12 |
| shop-payment | 2 | 1 | 5 | 6 | 14 |
| escort-dispatch | 0 | 1 | 5 | 2 | 8 |
| ai-chat-agent | 0 | 1 | 4 | 3 | 8 |
| administration | 0 | 0 | 3 | 2 | 5 |
| 跨模組 (number精度) | 2 | 2 | 0 | 0 | 4 |
| **合計** | **6** | **13** | **22** | **22** | **63** |

### 正面發現

1. **核心領域模型完整正確**: Result/Option 型別、DomainError 27 分類、FiatOrder 31 欄位 Zod schema 含 9 項 refine、EscortDispatchOrder 7 狀態機均正確實作
2. **Claim/Release 冪等模式**: Post-payment worker 四步驟管線、對帳 conditional UPDATE、兌換碼 `WHERE redeemed_by IS NULL` 競爭保護
3. **ECPay crypto 工具**: AES-CBC 動態金鑰長度選擇、PKCS7/PKCS5 相容、CheckMacValue 五步驟演算法、Java URLEncoder 差異處理
4. **模組隔離**: shared → economy → shop → dispatch/ai → admin 依賴方向清晰，無循環依賴
5. **不可變狀態轉換**: `withConfirmed`、`withCompleted` 等函數回傳新物件，符合 DDD
6. **17 個 AI Agent 工具全部實作**、**14 步驟 Markdown 修正順序嚴格遵守**
7. **zh-TW 在地化**: 所有按鈕、訊息、錯誤對映均使用繁體中文
