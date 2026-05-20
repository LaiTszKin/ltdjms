# Code Review Report: TypeScript Native Port

- **審查日期**: 2026-05-20
- **Spec 基準**: `docs/plans/2026-05-19/typescript-native-port/` (6 modules, 6 個 spec)
- **審查範圍**: `packages/shared/`, `packages/economy/`, `packages/shop/`, `packages/dispatch/`, `packages/ai/`, `packages/admin/`
- **審查方法**: 對每個模組的每個 TypeScript 原始碼檔案對照 spec requirements，從 6 個維度進行完整審查：
  1. **幻覺代碼** — 實作了 spec/Java 原版中不存在的功能
  2. **冗餘代碼** — 重複程式碼、死碼、未使用的 import
  3. **Spec 偏移** — 實作行為與 spec 要求不一致
  4. **Spec 遺漏** — spec 中定義的需求完全沒有對應實作
  5. **架構瑕疵** — 錯誤的依賴方向、循環依賴、DI 設定錯誤
  6. **性能隱患** — 缺少連線池、快取、阻塞操作

---

## 彙總統計

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|----|----|----|----|------|
| shared-infrastructure | 3 | 6 | 7 | 5 | 21 |
| guild-economy | 2 | 2 | 5 | 2 | 11 |
| shop-payment | 4 | 5 | 11 | 6 | 26 |
| escort-dispatch | 8 | 9 | 8 | 4 | 29 |
| ai-chat-agent | 4 | 10 | 10 | 7 | 31 |
| administration | 5 | 7 | 11 | 5 | 28 |
| **合計** | **26** | **39** | **52** | **29** | **146** |

---

## P0 — 阻斷性問題 (必須在整合前修正)

### shared-infrastructure

#### P0-1: 幻覺代碼 — 四個不存在於 Java 中的事件型別 (R6.3)
- **檔案**: `packages/shared/src/types/events/domain-event.ts` L146-174
- **描述**: 四個事件型別 (`AIChannelConfigChangedEvent`, `DispatchAfterSalesConfigChangedEvent`, `EscortPricingChangedEvent`, `EscortCatalogChangedEvent`) 在 Java `DomainEvent.java` sealed interface 中完全不存在。這些事件被匯出到 `AnyDomainEvent` union type，下游程式碼可能依賴根本不會被發布的事件型別。
- **建議**: 移除此四個事件型別；或若是 future requirement，在 spec 中明確標註為 planned。

#### P0-2: Spec 偏移 — DI 容器忽略傳入的已解析 Config instance (R9.2, R3.5)
- **檔案**: `packages/shared/src/infra/di/container.ts` L29; `packages/shared/src/main.ts` L26-70
- **描述**: `main.ts` 建立 `config = new EnvironmentConfig()` 並呼叫 `config.parse()` 後傳入 `initializeContainer({ config, ... })`，但 `container.ts` 完全忽略 `options?.config`，只執行 `registerSingleton(EnvironmentConfig)`。DI 容器解析時會建立一個全新的、未經 parse() 驗證的 instance。
- **建議**: 若 `options?.config` 存在，使用 `container.registerInstance()`；否則才用 `registerSingleton`。

#### P0-3: Spec 遺漏 — 缺少 DB_USERNAME / DB_PASSWORD 替代環境變數支援 (R3.2)
- **檔案**: `packages/shared/src/infra/config/schema.ts` L21-72
- **描述**: Java `EnvironmentConfig` 同時支援 `DB_USERNAME` 和 `DATABASE_USER`（以及 `DB_PASSWORD` / `DATABASE_PASSWORD`）。TypeScript Zod schema 僅定義了 `DATABASE_USER` 和 `DATABASE_PASSWORD`。使用 Java 相容 `.env` 的部署會失敗。
- **建議**: 加入 `DB_USERNAME` / `DB_PASSWORD` 作為可選欄位，並實作 fallback 邏輯。

### guild-economy

#### P0-4: Spec 偏移 — insufficient balance 回傳錯誤的 error category (R2.2)
- **檔案**: `packages/economy/src/currency/services/balance-adjustment-service.ts` L71-77
- **描述**: `tryAdjustBalance` 在餘額不足時回傳 `DomainError.invalidInput(...)` 而非 `DomainError.insufficientBalance(...)`。下游按 `INSUFFICIENT_BALANCE` 過濾的程式碼永遠抓不到此錯誤。
- **建議**: 將錯誤工廠呼叫改為 `DomainError.insufficientBalance(...)` 或移除 pre-check，讓 repository 層回傳正確型別。

#### P0-5: Spec 偏移 — `GameTokenService.getBalance` 錯誤地自動建立帳戶 (R4.1)
- **檔案**: `packages/economy/src/token/services/game-token-service.ts` L33-45
- **描述**: Java `GameTokenService.getBalance` 是唯讀操作（找不到帳戶時回傳 0）。TypeScript 版本呼叫 `findOrCreate()`，對每個首次查詢餘額的使用者寫入一筆新列。
- **建議**: 改用 `findByGuildIdAndUserId`，找不到時回傳 0。

### shop-payment

#### P0-6: `FiatPaymentCallbackService.parseDecryptedData()` 拋出 plain Error 而非 `InvalidCallbackPayloadException` (R5.10)
- **檔案**: `packages/shop/src/services/fiat-payment-callback.service.ts` L237
- **描述**: 缺少 ECPAY config 時拋出 `new Error(...)`，但 catch block 只捕捉 `InvalidCallbackPayloadException`。plain Error 落入通用 catch 並錯誤地回傳 HTTP 500 而非 spec 要求的 HTTP 400。
- **建議**: 改為 `throw new InvalidCallbackPayloadException(...)`。

#### P0-7: `ShopCommandHandler` 在 `deferReply()` 後使用 `replyEmbed()` (Discord.js contract)
- **檔案**: `packages/shop/src/commands/shop-handler.ts` L39 + L51
- **描述**: `deferReply()` 後再呼叫 `replyEmbed()` 會觸發 runtime error（interaction already replied）。
- **建議**: 改為 `editReply({ embeds: [...] })` 或移除 defer。

#### P0-8: `handleCallback()` sync 方法為死碼，永遠回傳 500 (R5.1)
- **檔案**: `packages/shop/src/services/fiat-payment-callback.service.ts` L40-77, L118-130
- **描述**: `handleCallback()` 委派給 `processWithOrder()`，後者無條件回傳 `CallbackResult.fail(500)`。Express server 實際使用 `handleCallbackAsync()`，但若有 caller 呼叫 spec 定義的 `handleCallback()` 將永遠失敗。
- **建議**: 移除 `handleCallback()` 和 `processWithOrder()`，將 `handleCallbackAsync` 改名為 `handleCallback`。

#### P0-9: 貨幣購買乘積 overflow 檢測無效 (R11.8)
- **檔案**: `packages/shop/src/services/redemption.service.ts` L316-329
- **描述**: JavaScript 乘法永不拋出例外，只會產生 `Infinity`。try-catch 完全無效。
- **建議**: 使用 `BigInt` 乘法或檢查 `product.rewardAmount! > Number.MAX_SAFE_INTEGER / code.quantity`。

### escort-dispatch

#### P0-10: Panel `checkAdminPermission` 永遠回傳 `true` (R14.1)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L522-540
- **描述**: 不管 `ADMINISTRATOR` 檢查結果如何都 `return true`，任何人可操作面板。
- **建議**: 無 `ADMINISTRATOR` 權限時 `return false` 並回覆無權限訊息。

#### P0-11: Panel handler 呼叫不存在的 `findByOrderNumber` 方法 (R15)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L499
- **描述**: `dispatchOrderService.findByOrderNumber(orderNumber)` — service 沒有公開此方法，只有 private `findOrder`。執行時會拋出 `TypeError`。
- **建議**: 將 `findOrder` 改為 public 或新增公開的 `findByOrderNumber`。

#### P0-12: 面板訊息完全沒有附加 Discord 互動元件 (R14)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` — 所有 handler 方法
- **描述**: 所有回應都只用 `interaction.reply(text)` 或 `interaction.replyEmbed(embed)`，沒有附加任何按鈕 (ActionRow) 或 select menu。`buildPanelReplyPayload` 和 `buttonsToComponents` 存在但從未被呼叫。
- **建議**: 回應時使用 `buildPanelReplyPayload` 組裝 embed + components。

#### P0-13: Mode switch 不清除 session state (R14.3)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L135-141
- **描述**: 切換模式時只設 `session.mode`，不清除 `selectedOptionCode`、`selectedOrderNumber`、`selectedUserId`。
- **建議**: Mode switch 時完全清除 session。

#### P0-14: Back 按鈕未完全清除 session state (R14.4)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L150-152
- **描述**: `BUTTON_BACK_TO_MODE` 只設 `session.mode = null`，不清除其他殘留資料。
- **建議**: 呼叫 `clearSession(guildId, userId)` 完全清除。

#### P0-15: `createManualOpenOrder` 的護航品類驗證永遠不會執行 (R2.1)
- **檔案**: `packages/dispatch/src/di/dispatch-module.ts` L79-82
- **描述**: DI 建構 `EscortDispatchOrderService` 時未傳入 `catalogRepository`。`createManualOpenOrder` 在 `catalogRepository` 為 `undefined` 時跳過驗證。
- **建議**: 將 catalog repo 傳入 service 的第四個建構參數。

#### P0-16: 通知服務已註冊但從未被注入或呼叫 (R3-R9)
- **檔案**: `packages/dispatch/src/di/dispatch-module.ts` L116-123
- **描述**: `DispatchNotificationService` 被實例化但從未被注入到任何 handler/service。所有 8 個 notify 方法永遠不會被呼叫。
- **建議**: 將 `notificationService` 注入到 `DispatchPanelInteractionHandler` 和 `EscortDispatchOrderService`。

#### P0-17: 所有 DM-only 操作缺少 `!isFromGuild()` 檢查 (R4.3)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L155-175
- **描述**: 6 個 DM 操作都沒有檢查 interaction 是否來自 guild 頻道，在公開頻道中也可執行。
- **建議**: 每個 handler 開頭加入 guild 檢查，提示「請在機器人私訊中操作」。

### ai-chat-agent

#### P0-18: Agent 模式完全無法運作 — 無工具綁定、無 agent loop、無 agent prompt (R4.1, R4.2, R4.4, R4.5, R5.1-R5.7, R15.1)
- **檔案**: `packages/ai/src/services/LangChainAIChatService.ts` L149-205 (`doStream`), L211-244 (`buildMessages`)
- **描述**: `doStream()` 只做純聊天串流，從不綁定工具、不載入 agent prompt、無 agent 執行迴圈。`AGENT_TOOL_DEFINITIONS` 陣列存在但從未被接線到串流流程。17 個工具類別從未被任何執行路徑呼叫。
- **建議**: 加入 `agentEnabled` 參數；agent 模式下綁定工具；實作 `maxIterations=5` 的 agent loop；載入 agent system prompts。

#### P0-19: `generateStreamingResponseWithId` 完全忽略 `messageId` 參數 (R3)
- **檔案**: `packages/ai/src/services/LangChainAIChatService.ts` L119-128
- **描述**: 方法接受 `messageId` 但從不使用，直接轉發給 `doStream(guildId, userMessage, [], handler)`。
- **建議**: 傳遞 `messageId` 至 handler 或 `doStream()`。

#### P0-20: AGENT_TOOL_DEFINITIONS 參數名稱與實際 tool schema 不符 (R5.1)
- **檔案**: `packages/ai/src/services/LangChainAIChatService.ts` L346-361
- **描述**: `delete_discord_resource` 使用 `type`/`id` 而非 spec 定義的 `resourceType`/`resourceId`；`manage_message` 使用 `content` 而非 `newContent`，缺少 `editMode`；`move_channel` 使用 `categoryId` 而非 `targetCategoryId`。
- **建議**: 將 `AGENT_TOOL_DEFINITIONS` schemas 與實際 tool class schemas 對齊。

#### P0-21: `get_channel_permissions` / `get_category_permissions` tool 缺少必要的參數 (R5.1)
- **檔案**: `packages/ai/src/services/LangChainAIChatService.ts` L299-308
- **描述**: 這兩個 tool definition 缺少 `channelId`/`categoryId` 參數，無法被 LLM 正確調用。

### administration

#### P0-22: 9 個管理面板 Handler 全部為 Stub — 未實現任何互動流程 (R2-R10)
- **檔案**: `packages/admin/src/panel/admin/handlers/` (9 files)
- **描述**: 所有 Handler 的 `execute()` 僅回覆佔位符字串（`'貨幣管理功能'`、`'AI 頻道設定功能'` 等），未實作 member select menu、Modal、CRUD 操作、事件發布。
- **建議**: 逐一實作每個 Handler 的完整互動流程。

#### P0-23: `/admin-panel` 以純文字取代 Embed + 按鈕 (R1.4)
- **檔案**: `packages/admin/src/panel/admin/AdminPanelCommand.ts` L47-57
- **描述**: `AdminPanelViewFactory.buildMainPanelEmbed()` 正確產生了含 9 個按鈕的結構化資料，但 `AdminPanelCommand.execute()` 將其展平為純文字輸出。Discord 不會渲染為可點擊按鈕。
- **建議**: 使用 `ActionRowBuilder` + `ButtonBuilder` 將 buttons array 轉換為真正的 Discord 訊息組件。

#### P0-24: Facade 層 guildId/userId 型別全部為 `number` (snowflake 精度遺失) (R13)
- **檔案**: `packages/admin/src/facades/` (全部 5 個 facade)
- **描述**: Discord snowflake ID 超出 `Number.MAX_SAFE_INTEGER` 範圍。使用 `number` 型別導致 ID 精度遺失，所有資料庫查詢找不到正確記錄。
- **建議**: 全部改為 `string` 型別。

#### P0-25: 用戶面板 Handler 全部為 Stub (R11.3-R11.8)
- **檔案**: `packages/admin/src/panel/user/handlers/TransactionHistoryHandler.ts`, `RedemptionCodeHandler.ts`
- **描述**: 交易記錄和兌換碼功能完全未實作。
- **建議**: 實作分頁互動邏輯、Modal 建構、facade 呼叫。

#### P0-26: 面板更新監聽器未實際更新 Embed (R12.1-R12.5)
- **檔案**: `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts` L51-53
- **描述**: `AdminPanelUpdateListener` 僅輸出 `console.log`，`UserPanelUpdateListener` 僅呼叫 `getUserPanelView` 而不實際更新 embed。事件發生後已開啟的面板不會收到即時更新。
- **建議**: 在 session data 中儲存 interaction hook reference，事件觸發時呼叫 `editReply()` 更新。

---

## P1 — 高優先級問題 (功能不完整或偏離 spec)

### shared-infrastructure

#### P1-1: Spec 偏移 — `isEphemeral()` 永遠回傳 false (R8.1)
- **檔案**: `packages/shared/src/discord/services/discord-js-interaction.ts` L37-39
- **描述**: 硬編碼回傳 `false`，任何依賴 ephemeral 狀態的業務邏輯都會判錯。
- **建議**: 從 discord.js interaction 取得實際 ephemeral 狀態。

#### P1-2: Spec 偏移 — Embed builder 截斷時未 logged warning
- **檔案**: `packages/shared/src/discord/services/discord-js-embed-builder.ts` L166-169
- **描述**: Spec error handling 要求截斷時「logged warning」，但 `truncate()` 和所有 setter 中截斷發生時無任何 logging。
- **建議**: 截斷時發出 pino warning log。

#### P1-3: Spec 偏移 — `DiscordInteraction` 多了 Java 未定義的 `getChannelId()` (R8.1)
- **檔案**: `packages/shared/src/discord/domain/discord-interaction.ts` L13
- **描述**: Java `DiscordInteraction.java` 沒有此方法（應屬於 `DiscordContext`）。
- **建議**: 從 `DiscordInteraction` 移除或更新 spec。

#### P1-4: Spec 偏移 — `ProductOperationType` 名稱與 Java 不一致 (R6.3)
- **檔案**: `packages/shared/src/types/events/domain-event.ts` L45-48
- **描述**: Java 使用 `OperationType` enum，TypeScript 命名為 `ProductOperationType`。
- **建議**: 重命名為 `OperationType` 或記錄為有意調整。

#### P1-5: 架構瑕疵 — `main.ts` 將跨模組事件接線置於 shared 套件 (R9)
- **檔案**: `packages/shared/src/main.ts` L216-250
- **描述**: `initializeAllModules()` 在 shared 中直接處理 Discord `interactionCreate` 和 `messageCreate` 事件接線，用了大量 `as any` 型別斷言。事件接線應由各模組自身處理。
- **建議**: 將 Discord 事件接線移到各模組的初始化函數中。

#### P1-6: Spec 偏移 — Config Schema 缺少 Java Typesafe Config 中介層 (R3.2)
- **檔案**: `packages/shared/src/infra/config/schema.ts`
- **描述**: Java 使用 `application.properties`（packaged defaults）作為 fallback 層之一。TypeScript 版本缺少此層。
- **建議**: 若非必需，在 spec 中記錄此簡化。

### guild-economy

#### P1-7: Spec 偏移 — `getBalance` 不回傳 `Result<>` (R1.1)
- **檔案**: `packages/economy/src/currency/services/balance-service.ts` L37
- **描述**: Spec R1.1 要求回傳 `Result<BalanceView, DomainError>`，但 TS `getBalance` 回傳 `Promise<BalanceView>`。Java 同時有 `getBalance`（deprecated）和 `tryGetBalance`（Result），TS 的 `tryGetBalance` 匹配 Java 但方法名與 spec 不符。
- **建議**: 確認 spec R1.1 應指向 `tryGetBalance` 或改名 TS 方法。

#### P1-8: Spec 偏移 — 未使用 `isValidAdjustmentAmount` (R1.4)
- **檔案**: `packages/economy/src/currency/services/balance-adjustment-service.ts` L45-56
- **描述**: Java 使用 `MemberCurrencyAccount.isValidAdjustmentAmount(amount)` 檢查 `Math.abs(amount) <= MAX_ADJUSTMENT_AMOUNT`。TS 使用內聯檢查 `amount > Number.MAX_SAFE_INTEGER`，不使用已定義的 `MAX_ADJUSTMENT_AMOUNT` 常數。
- **建議**: 替換為使用 `MAX_ADJUSTMENT_AMOUNT` 的 `isValidAdjustmentAmount` 函數。

### shop-payment

#### P1-9: `findOrdersPendingPostPayment` 排序缺少 `NULLS LAST` (R7.8)
- **檔案**: `packages/shop/src/persistence/drizzle-fiat-order-repository.ts` L259
- **描述**: Drizzle 的 `asc()` 生成 `ORDER BY paid_at ASC` 而無 `NULLS LAST`。PostgreSQL 預設 ASC 下 `NULLS FIRST`。
- **建議**: 使用 `sql` template 加入 `NULLS LAST`。

#### P1-10: 通知介面型別不匹配 (R12.3)
- **檔案**: `packages/shop/src/di/shop-module.ts` L170-171
- **描述**: `AdminOrderNotifier` 介面期望 3 個參數，但 `ShopAdminNotificationService` 有不同方法簽章。型別強制轉換 (`as unknown`) 繞過檢查，runtime 呼叫會傳入錯誤參數，產生亂碼訊息。
- **建議**: 對齊方法簽章或建立正確的 adapter。

#### P1-11: `sanitizePayload` 截斷原始 body 而非解密後的 payload (R5.9)
- **檔案**: `packages/shop/src/services/fiat-payment-callback.service.ts` L87, L334-338
- **描述**: Java 版本儲存解密後的 JSON string 並截斷到 4000 字元。TS 版本截斷原始加密 body，截斷點可能在加密字串中間，使 payload 完全無用。
- **建議**: 將解密後的 JSON string 存入 `callbackPayload`，必要時截斷。

#### P1-12: `FiatOrderService.createFiatOnlyOrder` 多了額外參數 (R4.1)
- **檔案**: `packages/shop/src/services/fiat-order.service.ts` L59
- **描述**: Spec 定義 3 個參數，實作有 4 個 (`tradeDesc?`)。
- **建議**: 移除多餘參數或更新 spec。

#### P1-13: Search result pagination 按鈕 handler 未接線 (R2.3)
- **檔案**: `packages/shop/src/commands/shop-handler.ts`
- **描述**: `encodeKeyword()` / `decodeKeyword()` 和 `BUTTON_SEARCH_PREV` / `BUTTON_SEARCH_NEXT` 常數已存在，但 `handleInteraction()` 沒有對應的 handler。搜尋結果分頁完全無法運作。
- **建議**: 加入 prefix matching 的 handler，解碼關鍵字並呼叫 `shopService.searchProducts()`。

### escort-dispatch

#### P1-14: `claimAfterSales` 缺少售後人員身分驗證 (R8.1)
- **檔案**: `packages/dispatch/src/service/escort-dispatch-order.service.ts` L297-340
- **描述**: 完全沒有呼叫 `afterSalesStaffService.isAfterSalesStaff()`。任何人可點擊「承接售後」。
- **建議**: 加入 `isAfterSalesStaff` 檢查。

#### P1-15: `claimAfterSales` re-query 未區分「已被自己接手」vs「已被他人接手」(R8.3)
- **檔案**: `packages/dispatch/src/service/escort-dispatch-order.service.ts` L330-335
- **描述**: atomic UPDATE 失敗後的 re-query 只檢查 `isAfterSalesInProgress(latest)`，未檢查 `isAfterSalesAssignee(latest, userId)`。自己已接手的情況會顯示錯誤的錯誤訊息。
- **建議**: re-query 後加入 `isAfterSalesAssignee` 檢查。

#### P1-16: 售後通知策略不符 spec — 無線上/離線分層 (R7.4)
- **檔案**: `packages/dispatch/src/notification/DispatchNotificationService.ts` L58-80
- **描述**: 直接取得所有 staff IDs 並全數發送 DM，無線上優先邏輯。無「目前尚未設定售後人員」提示。
- **建議**: 加入 online status 查詢；分層通知策略。

#### P1-17: DM 發送失敗時完全靜默 (R3.4, error handling)
- **檔案**: `packages/dispatch/src/notification/DispatchNotificationService.ts` L100-131
- **描述**: catch block 完全是空的，無 `console.warn`、無失敗指標回傳、無提示管理員。
- **建議**: catch 中加入 warn log；回傳失敗指標讓呼叫方判斷是否需提示管理員。

#### P1-18: `normalizeLimit` 用錯誤的最大值限制 (R3.1)
- **檔案**: `packages/dispatch/src/service/escort-dispatch-order.service.ts` L451-456
- **描述**: 對所有查詢使用 `MAX_HISTORY_LIMIT` (20)，但 pending assignment 查詢應使用 `MAX_PENDING_ASSIGNMENT_LIMIT` (25)。
- **建議**: `normalizeLimit` 應接受 max limit 參數。

#### P1-19: `ensureTimeoutCompletion` 無狀態條件檢查 (R10.2)
- **檔案**: `packages/dispatch/src/service/escort-dispatch-order.service.ts` L432-448
- **描述**: `update` 方法是純 ID-based update，不檢查當前狀態。race condition 下可能覆蓋其他狀態轉換。
- **建議**: 使用條件式 UPDATE (`WHERE id=? AND status='PENDING_CUSTOMER_CONFIRMATION'`)。

#### P1-20: 歷史記錄 embed 缺來源摘要 (R15)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelMessageFactory.ts` L198-223
- **描述**: 只顯示訂單編號、狀態、護航者、客戶，沒有 `sourceType`、`sourceReference` 等來源資訊。
- **建議**: 加入來源類型標籤（`[手動]`、`[貨幣購買]`、`[法幣付款]`）。

#### P1-21: Session state 缺客戶/護航者獨立欄位 (R14.2)
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L54-60
- **描述**: 只有一個 `selectedUserId`，無法同時區分客戶和護航者（建立派單需要兩者）。
- **建議**: 新增 `selectedCustomerId` 和 `selectedEscortUserId` 欄位。

#### P1-22: `formatPanelText` 在兩個 class 中完全重複
- **檔案**: `packages/dispatch/src/panel/DispatchPanelCommandHandler.ts` L41-57; `DispatchPanelInteractionHandler.ts` L556-572
- **建議**: 提取到 shared utility。

### ai-chat-agent

#### P1-23: `buildMessages()` 永遠只載入 base prompts (R15.1, R15.3)
- **檔案**: `packages/ai/src/services/LangChainAIChatService.ts` L215
- **描述**: `agentEnabled` 參數預設為 `false`，所有 caller 都傳 false。agent prompt 永不載入。
- **建議**: 從 listener 傳播 `agentEnabled` → `generateStreamingResponse` → `doStream` → `buildMessages`。

#### P1-24: `buildMessageLevelMemory()` 回傳空陣列而非 10 則訊息 (R8.3)
- **檔案**: `packages/ai/src/services/memory/chat-memory-provider.ts` L143-147
- **描述**: Spec 要求 MessageWindowChatMemory（最多 10 則），實作回傳 `[]`。
- **建議**: 實作回傳最多 10 則近期訊息的 message window。

#### P1-25: `create_role` 工具完全忽略 `permissions` 參數 (R5.1)
- **檔案**: `packages/ai/src/tools/CreateRoleTool.ts` L36-56
- **描述**: 只設定 `name` 和 `color`，`permissions` 欄位被完全忽略。
- **建議**: 建立 role 後套用 `permissions`。

#### P1-26: `modify_role_permissions` 每次 setPermissions 覆蓋前一項 (R5.1)
- **檔案**: `packages/ai/src/tools/ModifyRolePermissionsTool.ts` L48-63
- **描述**: 每個權限條目呼叫 `role.setPermissions()` 一次，每次覆蓋前一次的結果。只有最後一條生效。
- **建議**: 累積所有 allow/deny bits 後一次呼叫 `setPermissions()`。

#### P1-27: Blockquote flattening 對深層巢狀不正確 (R11.2)
- **檔案**: `packages/ai/src/markdown/services/DiscordMarkdownSanitizer.ts` L31-35
- **描述**: Regex 只移除一個 `>`，對於 `>>> text` 會變成 `>>> text`（不變）。
- **建議**: 替換所有 `>` 為單一 `>`。

#### P1-28: `CommonMarkValidator` 未跳過純強調語法 (R9.4)
- **檔案**: `packages/ai/src/markdown/validation/CommonMarkValidator.ts` L165-216
- **描述**: `*bold*` 被誤判為 MALFORMED_LIST。雖然 auto-fixer 正確跳過，但 validator 產生 false positive。
- **建議**: 在 `checkListFormat` 中加入純強調語法的跳過檢查。

#### P1-29: `ToolExecutionInterceptor` 存在但從未被接線 (R5.4)
- **檔案**: `packages/ai/src/services/ToolExecutionInterceptor.ts` — 整個檔案
- **描述**: Interceptor 完整實作但沒有任何 tool class 參照或注入它。工具審計日誌完全不存在。
- **建議**: 注入到每個 tool class 或 agent loop。

#### P1-30: 缺少 `AgentConfigCacheInvalidationListener` (R7.5)
- **檔案**: 完全缺失
- **描述**: Spec 要求跨實例的 Redis cache 失效監聽器。目前只有 inline `invalidateCache`，多實例部署時其他實例的 cache 會 stale。
- **建議**: 建立監聽 `AgentConfigUpdatedEvent` 的 listener，清除對應 Redis key。

#### P1-31: `isAgentEnabled()` 同步版拋錯 (R7.1)
- **檔案**: `packages/ai/src/services/routing/agent-config-service.ts` L132-139
- **描述**: 同步 `isAgentEnabled()` 拋出 `Error('Use isAgentEnabledAsync')`。任何呼叫同步版的程式碼會 crash。
- **建議**: 實作同步版（使用 local in-memory cache）或移除同步介面。

#### P1-32: 缺少 `deleteRemovedChannels()` 公開方法 (R2.5)
- **檔案**: `packages/ai/src/services/routing/channel-restriction-service.ts` L163-191
- **描述**: Repository 有此方法，但 service 介面和實作都沒有暴露。
- **建議**: 加入 `deleteRemovedChannels()` 到介面和實作。

### administration

#### P1-33: `adjustBalance` 未驗證整數 (R2.2)
- **檔案**: `packages/admin/src/facades/CurrencyManagementFacade.ts` L114-124
- **描述**: `validateAdjustmentAmount` 只檢查 `!Number.isFinite(amount) || amount <= 0`，未檢查 `Number.isInteger(amount)`。
- **建議**: 加入 `!Number.isInteger(amount)` 檢查。

#### P1-34: `adjustTokens` 捨棄 `reason` 和 `actorId` 參數 (R3.1)
- **檔案**: `packages/admin/src/facades/GameTokenManagementFacade.ts` L42-93
- **描述**: 方法接受 `reason` 和 `actorId` 但不傳遞給 service 層。管理員操作記錄缺少稽核資訊。
- **建議**: 傳遞至 service 層。

#### P1-35: 護航目錄/定價 view 使用錯誤的欄位對映 (R10.2)
- **檔案**: `packages/admin/src/panel/admin/views/AdminPanelViewFactory.ts` L352-356
- **描述**: `entry.type` 對映至名稱、`entry.level` 對映至類別，與實際 `EscortOptionCatalogEntry` 結構不符。
- **建議**: 確認實際欄位後修正所有對映。

#### P1-36: `enableAgent` 忽略 `mode` 參數 (R7.2)
- **檔案**: `packages/admin/src/facades/AIConfigManagementFacade.ts` L128-134
- **描述**: `_mode: string` 以 `_` 前綴標記為未使用，無法指定 Agent 模式類型。
- **建議**: 傳遞 `mode` 至 service 層。

#### P1-37: `AdminPanelCommand` 硬編碼面板摘要資料 (R1.3)
- **檔案**: `packages/admin/src/panel/admin/AdminPanelCommand.ts` L40-43
- **描述**: 貨幣設定傳入 `null`、護航訂單數傳入 `0`（硬編碼），永遠不顯示實際數據。
- **建議**: 從 facade 查詢實際資料。

#### P1-38: `MemberInfoFacade` 繞過 Facade 直接執行原始 SQL (R13.5)
- **檔案**: `packages/admin/src/facades/MemberInfoFacade.ts` L175-179
- **描述**: 動態 import DI 容器後直接執行原始 SQL（含字串插值拼接，有 SQL injection 風險）。違反 Facade 層應透過 service 層操作的原則。
- **建議**: 透過 `RedemptionService` 提供分頁查詢方法。

#### P1-39: `SlashCommandRegistrar` 定義了不屬於 administration 模組的命令 (Scope)
- **檔案**: `packages/admin/src/commands/registration/SlashCommandRegistrar.ts` L23-136
- **描述**: 包含 `BalanceSlashCommand`、`DiceGame1SlashCommand`、`ShopSlashCommand` 等屬於 economy/shop/dispatch 模組的命令。
- **建議**: 移至對應模組。

---

## P2 — 中優先級問題

### shared-infrastructure (7 findings)

- **P2-1**: `ButtonView` 缺少長度驗證 — `packages/shared/src/discord/domain/embed-view.ts` L28-33
- **P2-2**: `TokenMap` 型別缺 3 個 token 條目 — `packages/shared/src/infra/di/tokens.ts` L19-25
- **P2-3**: `SelectMenuUtil` API 形狀與 Java 不一致 (R8.6) — `packages/shared/src/discord/services/select-menu-util.ts`
- **P2-4**: `DomainEventPublisher.publish()` 不支援 async listener (R6.1) — `packages/shared/src/infra/events/domain-event-publisher.ts`
- **P2-5**: Pino transport 開發環境也輸出 JSON (R7.3) — `packages/shared/src/infra/logger/logger.ts`
- **P2-6**: `MockDiscordEmbedBuilder.buildPaginated()` 與真實實作重複 (R8.4) — `packages/shared/src/discord/mock/mock-discord-embed-builder.ts`
- **P2-7**: `dotenv` dependency 未使用 — `packages/shared/package.json`

### guild-economy (5 findings)

- **P2-8**: 未使用的 import: `MemberCurrencyAccount` — `packages/economy/src/currency/services/balance-adjustment-service.ts` L14
- **P2-9**: 未使用的 import: `okVoid` — `packages/economy/src/currency/repositories/currency-account-repo.ts` L5
- **P2-10**: 死碼在地化字串 — `packages/economy/src/localization/dice-game-messages.ts` L15, L26, L52
- **P2-11**: Dice game 用 `creditReward(0)` 只為取得餘額（浪費 DB round-trip）
- **P2-12**: 重複的 validation pair (`validateName` vs `tryValidateName`) — `packages/economy/src/currency/services/currency-config-service.ts`

### shop-payment (11 findings)

- **P2-13**: 搜尋按鈕回傳純文字而非 Modal (R2.5) — `packages/shop/src/commands/shop-handler.ts` L122-126
- **P2-14**: 法幣付款按鈕顯示佔位文字 (R4.1) — `packages/shop/src/commands/shop-handler.ts` L102-108
- **P2-15**: 貨幣購買確認僅為佔位符 (R3.1) — `packages/shop/src/commands/shop-handler.ts` L172-181
- **P2-16**: `buildShopComponents()` 匯出但從未被呼叫 (R1.5) — `packages/shop/src/commands/shop-handler.ts` L51
- **P2-17**: `CODE_LENGTH` 和 `CHARACTERS` 在兩個檔案重複定義 (R10.5)
- **P2-18**: `MERCHANT_TRADE_NO_TIME_FORMAT` regex 未使用 (R4.2)
- **P2-19**: `createPendingSimple()` 和 `ShopPageHelper` 為 invented helpers (不在 spec) — `packages/shop/src/domain/fiat-order.ts`
- **P2-20**: `createProduct()` factory 在 shop 而非 admin (scope out of spec) — `packages/shop/src/domain/product-types.ts`
- **P2-21**: `buildPaymentMethodChoiceEmbed()` / `buildPurchaseConfirmEmbed()` 不在 spec — `packages/shop/src/services/shop-view.ts`
- **P2-22**: `DrizzleProductRepository.getProduct()` 為冗餘 alias — `packages/shop/src/persistence/drizzle-product-repository.ts`
- **P2-23**: `formData.get('Data')` 未處理 URI decode 例外 — `packages/shop/src/services/fiat-payment-callback.service.ts`

### escort-dispatch (8 findings)

- **P2-24**: 重複的 `generateUniqueOrderNumber` 邏輯 — handoff service vs order service
- **P2-25**: `withAssignedEscort` 定義但從未被使用 (死碼) — `packages/dispatch/src/domain/escort-dispatch-order.ts` L360-372
- **P2-26**: `buildOrderTimedOutEmbed` 定義但從未被使用 (死碼) — `packages/dispatch/src/panel/DispatchPanelMessageFactory.ts` L114-126
- **P2-27**: `createPending` 未驗證 `customerUserId > 0` (R2.2) — `packages/dispatch/src/domain/escort-dispatch-order.ts` L199-215
- **P2-28**: Schema `escortDispatchOrder.id` 缺少 `primaryKey()` — `packages/dispatch/src/schema/escort-dispatch-order.sql.ts` L19
- **P2-29**: `notifyCustomerConfirmed` 額外通知了 `assignedByUserId` (R6.1) — spec 只要求通知護航者
- **P2-30**: `handleOrderSelected` 中的 dead null check — `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts` L508-512
- **P2-31**: Handoff 對 `escortOptionCode` 有 spec 未定義的額外驗證 — `packages/dispatch/src/service/escort-dispatch-handoff.service.ts` L72-73

### ai-chat-agent (10 findings)

- **P2-32**: `sendToChannel()` 使用不安全的 `as unknown as` 型別轉換 — `packages/ai/src/commands/ai-chat-mention-listener.ts` L150-151
- **P2-33**: `createRole` 的 permission schema 與其他 tools 不一致 — `packages/ai/src/tools/CreateRoleTool.ts` L8-18
- **P2-34**: Drizzle schema table 名稱與 Java Flyway migration 不同 — `packages/ai/src/persistence/schema.ts` (`ai_channel_restriction` vs `ai_allowed_channels`)
- **P2-35**: Code fence 計數 regex 邏輯不正確 — `packages/ai/src/markdown/services/DiscordMarkdownPaginator.ts` L146-149
- **P2-36**: `ConversationIdBuilder` 使用 magic string `'none'` — `packages/ai/src/services/memory/tool-call-history.ts` L29
- **P2-37**: `ModifyRolePermissionsTool` 有不相關的 `id`/`type` 欄位 — `packages/ai/src/tools/ModifyRolePermissionsTool.ts` L5-15
- **P2-38**: `TokenEstimator` 註冊在 DI 但從未被呼叫 — `packages/ai/src/di/ai-module.ts` L283-284
- **P2-39**: `DiscordMarkdownStreamProcessor` import 了但從未被實例化 (R13.4) — `packages/ai/src/markdown/services/MarkdownValidatingAIChatService.ts`
- **P2-40**: `PromptLoader` base prompts dir 不存在時可能調用 `.getValue()` 於 Err — `packages/ai/src/prompts/prompt-loader.ts`
- **P2-41**: `MessageSplitter.split()` 對空白內容回傳 `[]`，listener 可能發送空白

### administration (11 findings)

- **P2-42**: `BaseAdminHandler` 完全未被使用 (死碼) — `packages/admin/src/panel/admin/BaseAdminHandler.ts`
- **P2-43**: `AdminPanelViewFactory` 和 `AdminProductPanelViewFactory` 功能重疊
- **P2-44**: `AdminPanelModalFactory` 和 `AdminProductPanelModalFactory` 功能重疊
- **P2-45**: `AIConfigManagementFacade` 包含冗餘的 deprecated 方法 (R13.4)
- **P2-46**: `CurrencyManagementFacade.deductBalance` 不在 spec 定義中 (R13.1)
- **P2-47**: `BalanceAdjustMode` enum 從未被產品程式碼使用
- **P2-48**: `ProductManagementHandler.ts` 是冗餘的 re-export — `packages/admin/src/panel/admin/handlers/ProductManagementHandler.ts`
- **P2-49**: `AdminPanelRouter` 為無效路由 — 僅回覆佔位符 (R1.5)
- **P2-50**: Token view 按鈕標籤複用貨幣管理字串 (R3)
- **P2-51**: `AdminProductPanelHandler.execute` 有重複的 `getGuildId()` 呼叫
- **P2-52**: `AIConfigManagementFacade.addAllowedChannel` 接受不在 spec 中的額外參數 (R13.4)

---

## P3 — 低優先級問題

### shared-infrastructure (5 findings)

- **P3-1**: `module-declarations.d.ts` 假定了其他套件的 API 形狀
- **P3-2**: `main.ts` 使用過多 `as any` 型別斷言
- **P3-3**: `DomainEventPublisher` 缺少 Java 版本的 `debug` log (R6.1)
- **P3-4**: `MockDiscordContext` constructor 驗證過嚴（拒絕 `guildId === '0'`）
- **P3-5**: `rewardTypeSchema` 硬編碼 enum 值而非使用 `z.nativeEnum` — `packages/shop/src/domain/fiat-order.ts`

### guild-economy (2 findings)

- **P3-6**: TODO markers 顯示未完成的 feature wiring — `packages/economy/src/localization/dice-game-messages.ts`
- **P3-7**: `InsufficientBalanceError` 與 `DomainError.insufficientBalance()` 語意重複 — `packages/economy/src/currency/repositories/currency-account-repo.ts`

### shop-payment (6 findings)

- **P3-8**: `FiatOrder` 包含 3 個 Java domain record 中處理鎖欄位 (spec intro 說 36 欄位，實際 Java 28、TS 31)
- **P3-9**: `DrizzleRedemptionCodeRepository.findByCode()` 雙重 `toUpperCase()` — L141
- **P3-10**: `ShopCommandHandler` 傳入 plain object 而非 `EmbedBuilder` — L51
- **P3-11**: `FiatOrderService.createFiatOnlyOrder` 中的 `tradeDesc` 額外參數
- **P3-12**: 部分 embed builder 使用不一致的 color 常數
- **P3-13**: `formData` 解析未包裹 `decodeURIComponent` 的例外處理

### escort-dispatch (4 findings)

- **P3-14**: Footer 文字與 spec 措辭不同 (R5.3) — "等待客戶確認中（24 小時超時）" vs "24 小時未確認將視為訂單完成"
- **P3-15**: `notifyAfterSalesRequested` fallback 使用 guild system channel（spec 未定義此行）
- **P3-16**: 通知皆為純文字 DM 而非 embed + 按鈕 (R4, R6, R8, R9)
- **P3-17**: `order-number-generator.ts` 中 `randomInt(0, len)` 的 exclusive max 可能造成混淆

### ai-chat-agent (7 findings)

- **P3-18**: `AgentConfigUpdatedEvent` 用 `as never` cast 發布 (R7.5) — `packages/ai/src/services/routing/agent-config-service.ts` L214
- **P3-19**: `MessageChunkAccumulator.flush()` / `clear()` 從未在產品程式中被呼叫
- **P3-20**: `TokenEstimator` 完整實作但從未被參照（死碼）
- **P3-21**: `DiscordMarkdownStreamProcessor` 被 import 但從未被使用 (R13.4)
- **P3-22**: `LangChainExceptionMapper` 在 `updateConfig()` 後不會更新 stored config reference
- **P3-23**: `MessageSplitter` 對空白內容回傳 `[]` 可能導致 listener 發送空白訊息
- **P3-24**: `ToolExecutionInterceptor.getAndClearDuration()` 的 for-of timing 邏輯有缺陷

### administration (5 findings)

- **P3-25**: `MemberInfoFacade` 方法名 `getUserPanelView` 與 spec `getMemberSummary` 不一致 (R13.5)
- **P3-26**: `UserPanelCommand` 以純文字渲染按鈕 (類似 P0-23) — `packages/admin/src/panel/user/UserPanelCommand.ts` L51-54
- **P3-27**: Embed color 值未標準化（`0x5865F2` vs `0x2c3e50` vs `0x5865f2`）
- **P3-28**: `SlashCommandMetrics.recordStart` 忽略 `commandName` 參數 (R14.4) — `packages/admin/src/commands/infra/SlashCommandMetrics.ts` L24
- **P3-29**: `AdminPanelViewFactory` color 值使用不一致的大小寫 hex

---

## 六維度分析摘要

| 維度 | P0 | P1 | P2 | P3 | 關鍵發現 |
|------|----|----|----|----|---------|
| 幻覺代碼 | 1 | 0 | 5 | 0 | 4 個不存在於 Java 的事件型別 (P0-1); invented helpers (P2-19, P2-20, P2-21) |
| 冗餘代碼 | 1 | 1 | 11 | 3 | 大量死碼 (P0-8, P2-17, P2-24-26, P2-42); 未使用 import (P2-8-9) |
| Spec 偏移 | 5 | 19 | 12 | 6 | 錯誤 error type (P0-4); Discord API 誤用 (P0-7); snowflake 型別 (P0-24); 完整行為差異 (P1-1~P1-39) |
| Spec 遺漏 | 6 | 4 | 10 | 0 | Handler 全部 stub (P0-22, P0-25); notification 不觸發 (P0-16); update listener 空殼 (P0-26) |
| 架構瑕疵 | 7 | 7 | 7 | 4 | DI 忽略 config (P0-2); 權限永不檢查 (P0-10); main.ts 跨模組接線 (P1-5); 原始 SQL injection (P1-38) |
| 性能隱患 | 0 | 0 | 0 | 0 | 無 P0/P1 級別性能問題（creditReward(0) DB 浪費已在 P2） |

---

## 跨模組依賴整合風險

1. **DI 鍵結缺失**: `EscortOptionCatalogRepository` 未在任何 module 註冊，block administration 模組的護航目錄 CRUD。
2. **事件型別未對齊**: shared 中定義了 4 個 Java 不存在的事件，AI 模組的事件用 `as never` cast 發布。
3. **Discord.js API 誤用模式**: 多處 handler 在 `deferReply()` 後仍呼叫 `reply()` 而非 `editReply()`。
4. **snowflake 型別不一致**: admin 模組使用 `number`，其他模組使用 `string`，跨模組呼叫時必然錯誤。

---

## 優先級行動清單

| 優先級 | 行動 | 負責模組 |
|--------|------|---------| 
| P0 | 修正所有 26 個 P0 問題（詳見上方 P0 清單） | 全部 |
| P1 | 修正 Agent loop、通知策略、DM 檢查、Facade 型別等 39 個 P1 問題 | 全部 |
| P2 | 清理死碼、修正 spec 偏移、接線未完成的 UI 互動等 52 個 P2 問題 | 全部 |
| P3 | 修正命名不一致、補齊日誌、移除冗餘等 29 個 P3 問題 | 全部 |

**總評**: 核心業務邏輯層（domain model、repository、service）完成度約 85%，但 Discord 互動層（command/panel handler）缺口極大，多個模組的 UI 入口完全未實作或僅有 stub。最關鍵的阻斷問題集中在 dispatch（面板完全無互動元件、權限檢查失效）、administration（9 個 handler 全為 stub、snowflake ID 精度遺失）、ai-chat-agent（Agent 模式完全未接線）和 shop-payment（ShopCommandHandler 只有佔位符）。
