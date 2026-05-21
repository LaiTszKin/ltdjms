# Code Review Report

- **Spec**: TypeScript Native Port (6 modules)
- **Date**: 2026-05-21
- **Reviewer**: Claude Code QA Agent

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | ECPay 加密／解密／CheckMacValue 缺少逐 byte 的 Java golden-data 交叉驗證測試 | 無法保證 TypeScript ECPay 加密輸出與 Java 版本逐 byte 一致，若存在靜默差異將導致綠界 API 呼叫失敗 | `packages/shop/src/__tests__/ecpay-crypto.test.ts` | 全檔 |
| 2 | Shop 排程服務（`FiatOrderProcessingScheduler`）與回呼伺服器（`EcpayCallbackHttpServer`）的 `start()` 從未被呼叫 | 付款後履約 worker 永不執行、ECPay 回呼永不監聽，整個付款後處理管線完全失效 | `packages/shop/src/di/shop-module.ts` | L206, L247 |
| 3 | `GameRewardService.creditReward(0)` 在 `rewardAmount === 0` 時直接 return 0 而不讀取資料庫 | DiceGame1Result/DiceGame2Result 的 `previousBalance` 永遠為 0（而非實際餘額），違反 Java 行為合約。測試以不真實的 mock 掩蓋此 bug | `packages/economy/src/dice/services/game-reward-service.ts` | L54-56 |
| 4 | `SeededRandom` 明確使用與 Java `java.util.Random` 不同的 LCG 演算法 | 相同 seed 產生不同的骰子序列，導致骰子遊戲獎勵與 Java 不一致，直接違反 spec R5.3「獎勵計算完全等價於 Java」 | `packages/economy/src/dice/services/dice-game-1-service.ts` | L43-55 |
| 5 | Dispatch 模組完全沒有任何測試 | 7 狀態機、24 小時超時、handoff 冪等、條件式 UPDATE 等關鍵邏輯無驗證，無法保證業務正確性 | `packages/dispatch/src/` | 缺少 `__tests__/` |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 所有 claim 操作增加了 spec 未指定的 5 分鐘 stale lock timeout（`OR col < now() - 5 minutes`） | 偏離 spec 指定的嚴格 `IS NULL` 條件，可能導致鎖定語義在預期外的情況下被打破 | `packages/shop/src/persistence/drizzle-fiat-order-repository.ts` | L334-338, L364-367, L394-397 |
| 2 | DomainError 分類實際有 31 個，spec 宣稱 27-28 個，三個 REDEEM_CODE_* 分類未在 spec 中定義 | Spec 與實作之間的分類不匹配，規格文件無法作為正確的參考來源 | `packages/shared/src/types/domain-error.ts` | L5-37 |
| 3 | Dispatch 訂單編號產生器使用 UTC 時間方法，但 spec 未指定時區 | 若 Java 原始版本使用系統本地時區（`Clock` 注入），則在非 UTC 時區的伺服器上將產生不同的日期部分 | `packages/dispatch/src/domain/order-number-generator.ts` | L23-26 |
| 4 | 4 個 command handler 繞過 service 層直接呼叫 repository，無 `DiceConfigService` 存在 | 違反分層架構原則；config 變更缺少 service 層級的集中驗證 | `packages/economy/src/commands/dice-game-1-handler.ts` 等 | L67, L58 等 |
| 5 | DiceGame2 的骰面倍率在 command handler 中被 hardcode 為 `[1,1,1,1,1,1]` | 資料庫 schema 和 repository 支援個別骰面倍率，但 command 無選項可設定，形成 dead code | `packages/economy/src/commands/dice-config-handlers.ts` | L172 |
| 6 | Dispatch `ensureTimeoutCompletion` 在自動完成時發送通知給客戶和護航者 | Spec 未規定超時自動完成需發送通知；可能導致非預期的 DM 干擾 | `packages/dispatch/src/service/escort-dispatch-order.service.ts` | L513-520 |
| 7 | Administration 模組測試覆蓋嚴重不足：handler 整合測試 0 個、listener 測試 0 個、view/modal factory 測試 0 個 | 管理面板所有互動流程（9+ handler × 多個互動分支）完全未經驗證 | `packages/admin/src/panel/` | 缺少測試 |
| 8 | ShopView 位於 `services/shop-view.ts` 而非 spec 指定的 `view/shop-view.ts` | 目錄結構偏離 spec，View 層與 Service 層混淆 | `packages/shop/src/services/shop-view.ts` | 全檔 |
| 9 | `AdminPanelRouter` 已標記 `@deprecated`，路由現由 `SlashCommandListener` prefix matching 處理，Router 僅回傳錯誤訊息 | 死碼殘留於 DI 註冊中 | `packages/admin/src/panel/admin/AdminPanelRouter.ts` | L27 |
| 10 | `AdminPanelViewFactory` 僅建構主面板 embed；spec 要求建構 11+ 種 embed 佈局，其餘全部內嵌在各自 handler 中 | View 建構邏輯分散在 handlers 中，缺乏集中管理，偏離 spec 的設計 | `packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts` | L9-11 |
| 11 | AI 模組缺少 `AgentServiceFactory` class／interface、`MessageChunkAccumulator` class、`MarkdownHeadingSegmenter` class（邏輯內嵌在其他檔案中） | Spec 指定的三個獨立類別不存在，偏離 spec 的模組化設計 | `packages/ai/src/` | 多個檔案 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Database migration runner 使用自定義 `_ltdjms_migrations` 追蹤表，而非 drizzle-kit 慣例 | 與 drizzle-kit 的 `__drizzle_migrations` 表可能產生雙重套用風險 | `packages/shared/src/infra/database/migration-runner.ts` | L10, L110 |
| 2 | Discord 介面大幅超出 spec：`DiscordInteraction` 22 方法（spec 10）、`DiscordRuntimeGateway` 10 方法（spec 7） | 實際介面範圍與 spec 不一致，跨模組使用時難以判斷哪些方法是合約的一部分 | `packages/shared/src/discord/domain/discord-interaction.ts` | L6-74 |
| 3 | DomainEvent 類型名稱偏離 spec：`LangChain4jToolExecutionStartedEvent` → `ToolExecutionStartedEvent`、`LangChain4jToolExecutedEvent` → `ToolExecutedEvent` | 命名不一致，若未來需要比對 Java 事件類型會造成混淆 | `packages/shared/src/types/events/domain-event.ts` | L148, L157 |
| 4 | Session manager 使用 `CacheService` 而非 spec 指定的 `DiscordSessionManager`，且不儲存 `InteractionHook`（改存 `channelId`/`messageId`） | Session 更新機制與 spec 設計不同，改用 channel fetch 而非 `editReply()` | `packages/admin/src/session/AdminPanelSessionManager.ts` | L1-10 |
| 5 | 第六個 Facade `DispatchManagementFacade` 不在 spec T4 範圍內 | Spec 指定 5 個 Facade，但實作有 6 個；handler 原本應直接注入 dispatch domain service | `packages/admin/src/facades/DispatchManagementFacade.ts` | 全檔 |
| 6 | `AdminPanelViewState` 有 12 個狀態（spec 僅 4 個：MAIN / PRODUCT_LIST / PRODUCT_DETAIL / PRODUCT_CODE_LIST），額外 8 個用於 view-state-aware 更新 | 偏離 spec 狀態機定義 | `packages/admin/src/session/types.ts` | L11-24 |
| 7 | `RedemptionCodeRepository` interface 有 16+ 方法（spec 僅 9 個），額外方法標記為 ADMIN 用途 | 介面膨脹超出 spec 定義範圍 | `packages/shop/src/domain/redemption-code-repository.ts` | 全檔 |
| 8 | `FiatOrderProcessingScheduler` 使用 `setTimeout` 遞迴而非 spec 的 `setInterval` | 變更了排程觸發方式（雖然避免了重疊執行，但偏離 spec） | `packages/shop/src/services/fiat-order-processing-scheduler.ts` | L68-69, L86-87 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 多個模組目錄結構偏離 spec（economy 合併 domain types、AI 無 aichat/aiagent 子目錄、repository 路徑不一致） | 降低 spec 文件作為導航參考的價值 | 多個檔案 | - |
| 2 | Repository 命名慣例不一致：spec 指定 `persistence/` + `-repository.ts`，實作使用 `repositories/` + `-repo.ts` | 降低 spec 文件作為導航參考的價值 | 多個檔案 | - |
| 3 | ECPay AES cipher 動態選擇 aes-128/192/256-cbc 而非 spec 指定的硬編碼 `aes-128-cbc` | 功能等價但偏離 spec | `packages/shop/src/crypto/ecpay-aes.ts` | L13-21 |
| 4 | ECPay 官方 stage 金鑰嵌入原始碼 | Stage 金鑰是公開的，風險低，但此模式不應複製到正式金鑰 | `packages/shop/src/services/ecpay-cvs-payment.service.ts` | L11-13 |
| 5 | `FiatOrder.toFulfillmentProduct()` 已存在但 `createFiatOnlyOrder` 手動重複建構 fulfillment snapshot | 程式碼重複 | `packages/shop/src/services/fiat-order.service.ts` | L115-128 |
| 6 | `EscortOrderBuyerNotificationService` 使用 `any` 型別而非 `DispatchOrderSnapshot` | 型別安全缺失 | `packages/shop/src/services/escort-order-buyer-notification.service.ts` | L14, L52, L72 |
| 7 | `AdminPanelModalFactory` 的 `buildDiceGame1SettingsModal` / `buildDiceGame2SettingsModal` 未被使用（handler 內嵌建構 Modal） | Dead code | `packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts` | - |
| 8 | `embed-pagination.ts` 不在任何 spec task 中，是從 builder 實作中提取的重構產物 | 未被文件化的輔助模組 | `packages/shared/src/discord/services/embed-pagination.ts` | 全檔 |
| 9 | 骰子遊戲測試使用超出範圍的骰子值（值 8，有效範圍 1-6） | 測試場景不代表真實遊戲狀態 | `packages/economy/src/__tests__/dice-game-2.test.ts` | L178 |
| 10 | AI 模組缺少 `AIMessageEvent` 的發布呼叫（`doStream()` 完成回調中無 `eventPublisher.publish()`） | 違反 spec INT-011 的事件發布要求 | `packages/ai/src/services/LangChainAIChatService.ts` | - |

---

## 解決方案

### P0 修復

#### P0-1: ECPay crypto 缺少 Java golden-data 交叉驗證測試

- **涉及檔案**：`packages/shop/src/__tests__/ecpay-crypto.test.ts` > 新增測試
- **根因**：現有測試僅包含 roundtrip（加密後解密回原值），未使用 Java 原版的已知輸入／輸出 pair 進行逐 byte 比對。Spec T1.4 明確要求此類測試。
- **修復方案**：
  1. 從 Java `FiatPaymentCallbackServiceTest` 和 `EcpayTradeQueryServiceTest` 擷取已知的 (明文, 密文, CheckMacValue) 三元組作為 fixture
  2. 建立 `ecpay-crypto.crosscheck.test.ts`，對每組 fixture 驗證：
     - `encryptData(plainJson, hashKey, hashIv)` 輸出與 Java 密文完全一致
     - `decryptData(encryptedBase64, hashKey, hashIv)` 輸出與原始明文完全一致
     - `buildCheckMacValue(params, hashKey, hashIv)` 輸出與 Java CheckMacValue 完全一致
  3. 涵蓋邊界案例：空參數、特殊字元、中文參數、邊界長度
- **驗證方式**：`pnpm --filter @ltdjms/shop test` 全部通過；逐 byte 比對無差異

#### P0-2: Shop 排程與回呼伺服器 lifecycle hooks 未呼叫

- **涉及檔案**：`packages/shop/src/di/shop-module.ts` > `configureShopContainer()`（L206, L247）
- **根因**：`FiatOrderProcessingScheduler` 和 `EcpayCallbackHttpServer` 實例在 DI 容器中被建立為 singleton，但其 `start()` 方法從未被任何程式碼呼叫。
- **修復方案**：
  1. 在 `configureShopContainer()` 結束前呼叫 `scheduler.start()` 和 `callbackServer.start()`
  2. 或者匯出一個 `startShopServices()` 函數供主程式（`main.ts`）呼叫，明確觸發 lifecycle
- **驗證方式**：bot 啟動後確認 log 出現 "Started fiat order processing scheduler" 和 callback server listening 訊息；手動觸發 ECPay callback 確認伺服器回應

#### P0-3: creditReward(0) 回傳 0 而非實際餘額

- **涉及檔案**：`packages/economy/src/dice/services/game-reward-service.ts` > `creditReward()`（L54-56）
- **根因**：`creditReward()` 在 `rewardAmount === 0` 時直接 `return 0`，未進行資料庫查詢。DiceGame1Service/DiceGame2Service 的 `play()` 方法依賴 `creditReward(0)` 來取得玩家當前餘額（作為 `previousBalance`），但實作永遠回傳 0。
- **修復方案**：移除 `rewardAmount === 0` 的 early return，改為：
  ```typescript
  if (rewardAmount === 0) {
    // Still need to read actual balance for result display
    const balanceResult = await this.balanceService.tryGetBalance(guildId, userId);
    return balanceResult.isOk() ? balanceResult.getValue().balance : 0;
  }
  ```
  同步修正 `dice-game-1.test.ts` L131 的 mock（應 mock `balanceService.tryGetBalance` 而非 `creditReward`）
- **驗證方式**：單元測試驗證 `previousBalance` 為實際資料庫餘額而非 0；整合測試確認骰子遊戲結果 embed 顯示正確的購買前餘額

#### P0-4: SeededRandom 與 Java java.util.Random 不一致

- **涉及檔案**：`packages/economy/src/dice/services/dice-game-1-service.ts` > `SeededRandom`（L43-55）
- **根因**：`SeededRandom.nextInt(bound)` 使用簡化 LCG（multiplier=1664525, addend=1013904223），而 Java `java.util.Random` 使用不同的 LCG（multiplier=25214903917, addend=11, mask=48 bits）。相同 seed 產生完全不同的序列，違反 spec R5.3。
- **修復方案**：
  1. 使用 Java 的 LCG 公式重新實作 `SeededRandom`：`seed = (seed * 25214903917 + 11) & ((1 << 48) - 1)`，`nextInt(bound)` 回傳 `(seed >> 16) % bound`（需處理負數）
  2. 或使用 `linear-congruential` npm 套件（確認演算法匹配）
  3. 建立 cross-check 測試：使用相同 seed，驗證 TypeScript 產生的 1000 個骰子序列與 Java 完全一致
- **驗證方式**：`pnpm --filter @ltdjms/economy test`；cross-check 測試通過（Java vs TypeScript 骰子序列逐值一致）

#### P0-5: Dispatch 模組完全無測試

- **涉及檔案**：`packages/dispatch/src/__tests__/`（不存在）
- **根因**：Spec T9 定義了 10 個測試任務（T9.1-T9.10），涵蓋 domain model、order number generator、service、handoff、pricing、staff、repository 整合、notification、panel interaction、message factory。全部未實作。
- **修復方案**：按優先級依序補齊測試：
  1. `domain/escort-dispatch-order.test.ts` — 7 狀態機轉換 + guard conditions + 驗證邏輯
  2. `domain/order-number-generator.test.ts` — 格式驗證、字元集、唯一性
  3. `service/escort-dispatch-order.service.test.ts` — 所有 create/assign/confirm/complete/after-sales 流程
  4. `service/escort-dispatch-handoff.service.test.ts` — handoff + idempotency + exception fallback
  5. `repo/drizzle-escort-dispatch-order.repo.test.ts` — 整合測試 conditional UPDATE
  6. 其他測試（pricing, staff, notification, panel）
- **驗證方式**：`pnpm --filter @ltdjms/dispatch test` 全部通過；覆蓋率 >= 80%

### P1 修復

#### P1-1: 未指定的 5 分鐘 stale lock timeout

- **涉及檔案**：`packages/shop/src/persistence/drizzle-fiat-order-repository.ts` > `claimFulfillmentProcessing()`、`claimAdminNotificationProcessing()`、`claimReconciliationProcessing()`、`findOrdersPendingPostPayment()`、`findOrdersPendingReconciliation()`
- **根因**：實作在 claim 的 WHERE 條件中加入了 `OR col < now() - interval '5 minutes'`，允許在 5 分鐘後自動 steal 鎖定。這是一項合理的維運保護，但 spec 未指定此行為。
- **修復方案**：兩種選擇：
  - 方案 A：移除 OR 條件，嚴格遵循 spec 的 `IS NULL` 語義（需配合外部 watchdog 清理僵死鎖）
  - 方案 B（建議）：保留此邏輯，但更新 spec tasks T4.3 以記錄此設計決策，並將 timeout 值設為可配置
- **驗證方式**：整合測試：模擬 crash 場景，確認 5 分鐘後鎖定被正確釋放

#### P1-2: DomainError 分類數量不一致

- **涉及檔案**：`packages/shared/src/types/domain-error.ts`（L5-37）、`docs/plans/.../shared-infrastructure/spec.md`（L59）、`docs/plans/.../shared-infrastructure/tasks.md`（L16）
- **根因**：實作了 31 個分類，但 spec 宣稱 27-28 個。額外的 `REDEEM_CODE_USED`、`REDEEM_CODE_EXPIRED`、`REDEEM_CODE_INVALID` 未在 spec 中定義。這是實作超前於 spec 的情況。
- **修復方案**：更新 spec R2.3 和 tasks.md T1.2，將分類數量修正為 31，並補列三個 REDEEM_CODE_* 分類
- **驗證方式**：Spec 文件中的分類清單與原始碼完全一致

#### P1-3: Dispatch 訂單編號使用 UTC 時間

- **涉及檔案**：`packages/dispatch/src/domain/order-number-generator.ts` > `generate()`（L23-26）
- **根因**：使用 `getUTCFullYear()` / `getUTCMonth()` / `getUTCDate()` 而非本地時間方法。若 Java 原始版本使用注入的 `Clock`（通常為系統本地時區），則日期部分可能不同。
- **修復方案**：確認 Java `EscortDispatchOrderNumberGenerator` 使用的時區。若 Java 使用系統時區，改用 `getFullYear()` / `getMonth()` / `getDate()`；若 Java 使用 UTC，則現有實作正確
- **驗證方式**：在同一伺服器上同時執行 Java 和 TypeScript 版本，比對同一天產生的訂單編號日期部分

#### P1-4: Handler 繞過 service 層直接呼叫 repository

- **涉及檔案**：`packages/economy/src/commands/dice-game-1-handler.ts`（L67, L102）、`dice-game-2-handler.ts`（L58, L93）、`dice-config-handlers.ts`（L64, L164）
- **根因**：Handler 直接注入並呼叫 `DiceConfigRepository` 和 `CurrencyConfigRepository`，繞過了 service 層。缺乏專用的 `DiceConfigService` 來集中管理 config 驗證和事件發布。
- **修復方案**：建立 `DiceConfigService`，將 config 查詢和更新邏輯從 handler 移至 service；handler 僅透過 service 操作
- **驗證方式**：Handler 不再直接依賴 repository；config 變更事件由 service 統一發布

#### P1-5: DiceGame2 骰面倍率 hardcode

- **涉及檔案**：`packages/economy/src/commands/dice-config-handlers.ts` > `DiceGame2ConfigHandler`（L172）
- **根因**：Command handler 在呼叫 `upsertDice2Config()` 時始終傳入 `faceMultipliers: [1, 1, 1, 1, 1, 1]`。雖然 schema 和 repository 支援個別倍率，但使用者無法透過 Discord command 設定。
- **修復方案**：在 `/dice-game-2-config` command 中增加 6 個 optional number options（`face-1` 至 `face-6`），預設值為 1；或在 Modal 中提供對應輸入欄位
- **驗證方式**：透過 Discord command 設定非預設骰面倍率後，查詢資料庫確認值已更新

#### P1-6: Dispatch 超時自動完成發送非預期通知

- **涉及檔案**：`packages/dispatch/src/service/escort-dispatch-order.service.ts` > `ensureTimeoutCompletion()`（L513-520）
- **根因**：`ensureTimeoutCompletion` 在自動完成後呼叫 `notificationService.notifyCustomerConfirmed(updated)`，發送 DM 給客戶和護航者。Spec R10 僅規定記錄 warn log，未指定發送通知。
- **修復方案**：移除自動完成路徑中的通知呼叫，僅保留 log；或將通知行為設為可配置選項
- **驗證方式**：模擬超時完成，確認不發送 DM

#### P1-7: Administration 測試覆蓋嚴重不足

- **涉及檔案**：`packages/admin/src/`（缺少 handler、listener、view factory 測試）
- **根因**：僅有 Facade（5 個）和 Session manager（2 個）的單元測試。Handler 整合測試 0 個、Listener 測試 0 個、View/Modal factory 測試 0 個、SlashCommandListener/Metrics 測試 0 個。Spec T13 要求全面測試覆蓋。
- **修復方案**：按優先級補齊測試：
  1. BotErrorHandler 測試（27 DomainError category × zh-TW 訊息）
  2. AdminPanelUpdateListener 測試（13 event × 多 view state × 有效/過期 session）
  3. UserPanelUpdateListener 測試（3 event × 有效/過期 session）
  4. Handler 整合測試（使用 MockDiscordInteraction）
- **驗證方式**：`pnpm --filter @ltdjms/admin test` 全部通過；整體覆蓋率 >= 85%

#### P1-8: ShopView 目錄位置偏離 spec

- **涉及檔案**：`packages/shop/src/services/shop-view.ts`
- **根因**：Spec T11.2 指定檔案路徑為 `packages/shop/src/view/shop-view.ts`，實作置於 `services/` 目錄下
- **修復方案**：將 `shop-view.ts` 移至 `packages/shop/src/view/` 目錄，更新所有 import 路徑
- **驗證方式**：TypeScript 編譯通過；所有測試通過

#### P1-9: AdminPanelRouter 死碼

- **涉及檔案**：`packages/admin/src/panel/admin/AdminPanelRouter.ts`（L27）、`packages/admin/src/di/AdminModule.ts`
- **根因**：AdminPanelRouter 已標記 `@deprecated`，路由改由 SlashCommandListener prefix matching 處理。Router 的 `execute()` 僅回傳錯誤訊息，但仍在 DI 中註冊。
- **修復方案**：從 DI 註冊中移除 AdminPanelRouter，從 `AdminPanelRouter.ts` 檔案中移除或保留僅作文件參考
- **驗證方式**：bot 啟動無錯誤；所有 admin panel 按鈕互動仍正常路由

#### P1-10: AdminPanelViewFactory 未實作所有 embed 佈局

- **涉及檔案**：`packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts`（L9-11）、各 handler 檔案
- **根因**：Spec T6.2 要求 ViewFactory 集中建構所有 11+ 種 embed 佈局。實作僅建構主面板 embed，其餘全部內嵌在各自 handler 中。
- **修復方案**：將各 handler 中的 embed 建構邏輯提取到 ViewFactory 對應方法中，或更新 spec 反映實際架構決策
- **驗證方式**：所有 embed 建構邏輯集中在 ViewFactory；handler 僅呼叫 ViewFactory 方法

#### P1-11: AI 模組缺少三個獨立的 class 實作

- **涉及檔案**：`packages/ai/src/` — 缺少 `AgentServiceFactory`、`MessageChunkAccumulator`、`MarkdownHeadingSegmenter` 獨立檔案
- **根因**：這些類別的邏輯被內嵌在其他檔案中（`AgentServiceFactory` → `LangChainAIChatService.ts` 的 module-level functions、`MessageChunkAccumulator` → 內嵌在 `MarkdownValidatingAIChatService`、`MarkdownHeadingSegmenter` → 內嵌在 `DiscordMarkdownStreamProcessor.findSegmentPoint()`）
- **修復方案**：將邏輯提取為獨立檔案：
  1. `packages/ai/src/services/agent-service-factory.ts` — `AgentServiceFactory` interface + `DefaultAgentServiceFactory` class
  2. `packages/ai/src/services/message-chunk-accumulator.ts` — `MessageChunkAccumulator` class
  3. `packages/ai/src/markdown/services/markdown-heading-segmenter.ts` — `MarkdownHeadingSegmenter` class
- **驗證方式**：TypeScript 編譯通過；所有現有測試通過；新 class 有獨立單元測試

### P2 修復

#### P2-1: Migration runner 自定義追蹤表

- **涉及檔案**：`packages/shared/src/infra/database/migration-runner.ts`（L10, L110）
- **根因**：使用自定義 `_ltdjms_migrations` 表進行 baseline 處理，同時在 L110 呼叫 drizzle-kit 的 `migrate()`。兩套追蹤機制可能衝突。
- **修復方案**：統一使用 drizzle-kit 的 migration 機制，移除自定義追蹤表；或保留自定義機制但移除 `migrate()` 呼叫以避免雙重套用
- **驗證方式**：整合測試：對空資料庫執行 migration → 確認所有表正確建立；對已 migration 的資料庫再次執行 → no-op

#### P2-2: Discord 介面超出 spec 定義

- **涉及檔案**：`packages/shared/src/discord/domain/discord-interaction.ts`（L6-74）、`discord-runtime-gateway.ts`（L6-40）
- **根因**：實作擴充了 spec 未指定的方法（如 `showModal()`、`getSelectedValues()`、`sendDM()`、`isMemberOnline()` 等）
- **修復方案**：更新 spec T7.1 和 T7.4 以反映實際介面範圍；或將超出 spec 的方法標記為內部實作細節
- **驗證方式**：Spec 文件中的介面定義與原始碼一致

#### P2-3: DomainEvent 類型命名偏離 spec

- **涉及檔案**：`packages/shared/src/types/events/domain-event.ts`（L148, L157）
- **根因**：Java 原始名稱包含 `LangChain4j` 前綴（`LangChain4jToolExecutionStartedEvent`），TypeScript 版本移除此前綴
- **修復方案**：決定命名策略：若全面移除 `LangChain4j` 前綴，則更新 spec 以保持一致；否則恢復原名稱
- **驗證方式**：Spec T6.1 的事件名稱清單與原始碼完全一致

#### P2-4: Session manager 未使用 DiscordSessionManager

- **涉及檔案**：`packages/admin/src/session/AdminPanelSessionManager.ts`（L1-10）、`PanelSessionManager.ts`
- **根因**：Spec T5.1/T5.2 指定注入 `DiscordSessionManager`（from shared），實作改為直接注入 `CacheService` 並以 in-memory Map + optional Redis backup 管理 session。Session 不儲存 `InteractionHook` 而改儲存 `channelId`/`messageId`。
- **修復方案**：兩種選擇：
  - 方案 A：實作 shared 的 `DiscordSessionManager`，讓 session manager 依賴它
  - 方案 B（建議）：更新 spec 記錄此架構決策（in-memory Map + CacheService），因為這避免了 Discord InteractionHook 過期無法使用的問題
- **驗證方式**：Session 建立／查詢／TTL 過期／清理功能正常

#### P2-5: 第六個 Facade DispatchManagementFacade

- **涉及檔案**：`packages/admin/src/facades/DispatchManagementFacade.ts`
- **根因**：Spec T4 定義 5 個 Facade，T6.10-T6.12 指定 handler 直接注入 dispatch domain service。實作新增了第六個 Facade 統一管理 dispatch 操作。
- **修復方案**：更新 spec T4 以包含 `DispatchManagementFacade`；或移除 facade 改回 handler 直接注入（若 spec 架構意圖是 handler 直接使用 domain service）
- **驗證方式**：所有 dispatch 相關 handler 的依賴注入一致（全部透過 facade 或全部直接注入）

#### P2-6: AdminPanelViewState 超出 spec 定義

- **涉及檔案**：`packages/admin/src/session/types.ts`（L11-24）
- **根因**：Spec 僅定義 4 個狀態（MAIN / PRODUCT_LIST / PRODUCT_DETAIL / PRODUCT_CODE_LIST），實作增加 8 個（BALANCE、TOKEN、GAME_CONFIG、AI_CHANNEL、AI_AGENT、DISPATCH_STAFF、ESCORT_PRICING、ESCORT_CATALOG）
- **修復方案**：更新 spec T5.3 以包含所有實際使用的 view state
- **驗證方式**：Spec 中的狀態定義與原始碼一致

#### P2-7: RedemptionCodeRepository 介面膨脹

- **涉及檔案**：`packages/shop/src/domain/redemption-code-repository.ts`
- **根因**：Spec 定義 9 個方法，實作有 16+ 個方法（額外方法標記為 ADMIN 用途）。這些方法為管理面板提供支援。
- **修復方案**：將 ADMIN 方法分離到獨立的 `RedemptionCodeAdminRepository` interface，或更新 spec 包含所有方法
- **驗證方式**：TypeScript 編譯通過；管理面板 redemption 功能正常

#### P2-8: setTimeout 遞迴 vs setInterval

- **涉及檔案**：`packages/shop/src/services/fiat-order-processing-scheduler.ts`（L68-69, L86-87）
- **根因**：Spec T12.1 指定使用 `setInterval`，實作使用 `setTimeout` 遞迴（等待前次執行完成後才排程下一次）。這避免了重疊執行的風險，但偏離 spec。
- **修復方案**：保留 setTimeout 遞迴（因為它解決了 setInterval 的潛在問題），更新 spec T12.1 以記錄此設計決策
- **驗證方式**：整合測試驗證排程間隔正確；連續兩次 processPendingOrders 不重疊

### P3 改善

#### P3-1: 目錄結構偏離 spec

- **涉及檔案**：多個模組
- **根因**：Economy 的 domain types 合併為單一檔案、AI 模組無 aichat/aiagent 子目錄、repository 使用 `repositories/` 而非 `persistence/`
- **修復方案**：更新各 spec 的 tasks.md 以反映實際目錄結構
- **驗證方式**：Spec 文件中的檔案路徑與實際一致

#### P3-2: Repository 命名慣例不一致

- **涉及檔案**：Economy、Dispatch、Shop 模組的 repository 檔案
- **根因**：Spec 指定 `-repository.ts` 後綴，實作使用 `-repo.ts`；spec 指定 `persistence/` 目錄，實作使用 `repositories/`
- **修復方案**：統一命名慣例（建議採用 `-repo.ts` + `repositories/`，因其簡潔）；更新 spec
- **驗證方式**：所有模組的 repository 命名一致

#### P3-3: ECPay cipher 動態選擇

- **涉及檔案**：`packages/shop/src/crypto/ecpay-aes.ts`（L13-21）
- **根因**：Spec 指定硬編碼 `aes-128-cbc`，實作根據 key 長度動態選擇。對 16-byte 標準 key 結果等價。
- **修復方案**：保留動態選擇（更靈活），更新 spec T1.1
- **驗證方式**：使用 16-byte key 時 cipher 為 `aes-128-cbc`

#### P3-4: ECPay stage key 嵌入原始碼

- **涉及檔案**：`packages/shop/src/services/ecpay-cvs-payment.service.ts`（L11-13）
- **根因**：官方 stage 金鑰以常數形式存在原始碼中。這些是公開的測試金鑰，風險低。
- **修復方案**：將 stage 金鑰移至 `.env.example` 作為文件化的預設值，原始碼中僅保留金鑰檢測邏輯（比對 config 值與 stage 金鑰）
- **驗證方式**：`ECPAY_STAGE_MODE=false` 時仍能正確拒絕 stage 金鑰

#### P3-5: 重複的 fulfillment snapshot 建構

- **涉及檔案**：`packages/shop/src/services/fiat-order.service.ts`（L115-128）
- **根因**：`createFiatOnlyOrder` 手動建構與 `toFulfillmentProduct()` 相同的物件
- **修復方案**：改用 `order.toFulfillmentProduct()` 取代手動建構
- **驗證方式**：TypeScript 編譯通過；測試通過

#### P3-6: EscortOrderBuyerNotification 使用 any 型別

- **涉及檔案**：`packages/shop/src/services/escort-order-buyer-notification.service.ts`（L14, L52, L72）
- **根因**：`order` 參數型別為 `any`，而非具體的 dispatch order 型別
- **修復方案**：定義或 import `DispatchOrderSnapshot` interface，用於方法簽名
- **驗證方式**：TypeScript strict mode 編譯通過

#### P3-7: AdminPanelModalFactory 未使用的方法

- **涉及檔案**：`packages/admin/src/panel/admin/views/AdminPanelModalFactory.ts`
- **根因**：`buildDiceGame1SettingsModal` / `buildDiceGame2SettingsModal` 存在於 ModalFactory，但 `GameSettingsHandler` 內嵌建構 Modal
- **修復方案**：讓 GameSettingsHandler 使用 ModalFactory 的方法，或從 ModalFactory 移除未使用的方法
- **驗證方式**：Modal 建構邏輯不重複

#### P3-8: embed-pagination.ts 未在 spec 中記錄

- **涉及檔案**：`packages/shared/src/discord/services/embed-pagination.ts`
- **根因**：此檔案是從 DiscordJsEmbedBuilder 和 MockDiscordEmbedBuilder 中提取共用分頁邏輯的重構產物，未在 spec 任何 task 中提及
- **修復方案**：在 shared-infrastructure spec 中記錄此檔案的存在
- **驗證方式**：Spec 文件反映實際檔案清單

#### P3-9: 測試使用超出範圍的骰子值

- **涉及檔案**：`packages/economy/src/__tests__/dice-game-2.test.ts`（L178）
- **根因**：測試使用 `rolls = [1, 2, 4, 6, 8]`，值 8 超出 1-6 的有效範圍
- **修復方案**：將測試值改為有效範圍內的數值，如 `[1, 2, 4, 6, 2]`
- **驗證方式**：所有骰子值在 [1, 6] 範圍內

#### P3-10: AIMessageEvent 未被發布

- **涉及檔案**：`packages/ai/src/services/LangChainAIChatService.ts` > `doStream()`
- **根因**：Spec INT-011 要求在串流完成時發布 `AIMessageEvent`，但 `doStream` 的 `onCompleteResponse` 回調中未呼叫 `eventPublisher.publish()`
- **修復方案**：在串流成功完成後發布 `AIMessageEvent`（含 guildId、channelId、userId、messageLength、model 等資訊）
- **驗證方式**：整合測試確認事件在每次 AI 回應後正確發布
