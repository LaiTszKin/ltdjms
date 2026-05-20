# Tasks: Escort Dispatch

- Date: 2026-05-20
- Feature: Escort Dispatch

## **Task 1: Drizzle Schema 定義**

Purpose: 定義三個 PostgreSQL table 的 Drizzle schema 與對應 TypeScript 型別，為所有後續層提供資料結構基礎。
Requirements: R1.1, R6.3, R10.1
Scope: `packages/dispatch/src/schema/`
Out of scope: Migration SQL（沿用現有 Flyway migration，不在本 spec 內新增）、EscortOptionCatalog table（屬於 shared/product）

- T1.1 [ ] **`packages/dispatch/src/schema/escort-dispatch-order.sql.ts`** — 定義 `escortDispatchOrder` Drizzle table schema
  - Columns: `id` (serial PK), `orderNumber` (varchar 32 UK NOT NULL), `guildId` (bigint NOT NULL), `assignedByUserId` (bigint NOT NULL), `escortUserId` (bigint NOT NULL DEFAULT 0), `customerUserId` (bigint NOT NULL), `status` (varchar 32 NOT NULL), `createdAt` (timestamp NOT NULL), `confirmedAt` (timestamp), `completionRequestedAt` (timestamp), `completedAt` (timestamp), `afterSalesRequestedAt` (timestamp), `afterSalesAssigneeUserId` (bigint), `afterSalesAssignedAt` (timestamp), `afterSalesClosedAt` (timestamp), `updatedAt` (timestamp NOT NULL), `sourceType` (varchar 20 NOT NULL), `sourceReference` (varchar 255), `sourceProductId` (bigint), `sourceProductName` (varchar 255), `sourceCurrencyPrice` (bigint), `sourceFiatPriceTwd` (bigint), `sourceEscortOptionCode` (varchar 50)
  - Index: unique index on `orderNumber`; index on `guildId, createdAt DESC`; index on `guildId, status, escortUserId`; index on `sourceType, sourceReference`
  - Export `EscortDispatchOrderRow` type（`typeof escortDispatchOrder.$inferSelect`）和 `EscortDispatchOrderInsert` type（`typeof escortDispatchOrder.$inferInsert`）
  - Verify: diff vs Java migration SQL → 所有欄位一致

- T1.2 [ ] **`packages/dispatch/src/schema/guild-escort-option-price.sql.ts`** — 定義 `guildEscortOptionPrice` Drizzle table schema
  - Columns: `id` (serial PK), `guildId` (bigint NOT NULL), `optionCode` (varchar 50 NOT NULL), `priceTwd` (bigint NOT NULL), `updatedByUserId` (bigint), `createdAt` (timestamp DEFAULT now()), `updatedAt` (timestamp DEFAULT now())
  - Constraint: unique on `(guildId, optionCode)`
  - Export `GuildEscortOptionPriceRow` type
  - Verify: diff vs Java migration SQL → 所有欄位一致

- T1.3 [ ] **`packages/dispatch/src/schema/dispatch-after-sales-staff.sql.ts`** — 定義 `dispatchAfterSalesStaff` Drizzle table schema
  - Columns: `id` (serial PK), `guildId` (bigint NOT NULL), `userId` (bigint NOT NULL), `createdAt` (timestamp DEFAULT now())
  - Constraint: unique on `(guildId, userId)`
  - Export `DispatchAfterSalesStaffRow` type
  - Verify: diff vs Java migration SQL → 所有欄位一致

- T1.4 [ ] **`packages/dispatch/src/schema/index.ts`** — barrel export 所有 schema 與型別
  - Verify: `tsc --noEmit` 通過

## **Task 2: Domain Models 與狀態機**

Purpose: 實作 EscortDispatchOrder domain record、7 狀態機、訂單編號值物件、OptionPriceView 等領域型別。
Requirements: R1.2–R1.4, R6.2–R6.4, R10.1–R10.3
Scope: `packages/dispatch/src/domain/`
Out of scope: Repository 層、Service 層（僅定義純資料轉換與驗證邏輯）

- T2.1 [ ] **`packages/dispatch/src/domain/escort-dispatch-order.ts`** — 實作 `EscortDispatchOrder` interface + factory functions + 狀態轉換方法（immutable 風格）
  - 定義 `EscortDispatchOrderStatus` enum：`PENDING_CONFIRMATION`, `CONFIRMED`, `PENDING_CUSTOMER_CONFIRMATION`, `COMPLETED`, `AFTER_SALES_REQUESTED`, `AFTER_SALES_IN_PROGRESS`, `AFTER_SALES_CLOSED`
  - 定義 `SourceType` enum：`MANUAL`, `CURRENCY_PURCHASE`, `FIAT_PAYMENT`
  - 定義 `EscortDispatchOrder` interface（22 個欄位，對應 Java record）
  - Factory functions:
    - `createPending(orderNumber, guildId, assignedByUserId, escortUserId, customerUserId)` → PENDING_CONFIRMATION，sourceType=MANUAL
    - `createManualOpenOrder(orderNumber, guildId, assignedByUserId, customerUserId, sourceEscortOptionCode)` → PENDING_CONFIRMATION，escortUserId=0
    - `createPendingFull(...)` → 完整參數版
    - `createAutoHandoff(orderNumber, guildId, escortUserId, customerUserId, sourceType, sourceReference, product snapshot fields)` → PENDING_CONFIRMATION
  - Immutable transition methods（回傳新物件）:
    - `withAssignedEscort(order, assignedByUserId, escortUserId, assignedAt)`
    - `withConfirmed(order, confirmedAt)`
    - `withCompletionRequested(order, requestedAt)`
    - `withCompleted(order, completedAt)`
    - `withAfterSalesRequested(order, requestedAt)`
    - `withAfterSalesInProgress(order, assigneeUserId, assignedAt)`
    - `withAfterSalesClosed(order, closedAt)`
  - Predicate helpers: `isPendingEscortConfirmation`, `isConfirmed`, `isPendingCustomerConfirmation`, `isAfterSalesRequested`, `isAfterSalesInProgress`, `isCompleted`（COMPLETED 或 AFTER_SALES_CLOSED）
  - Authorization checks: `canBeConfirmedBy`, `canBeCompletedByEscort`, `canBeConfirmedByCustomer`, `isAfterSalesAssignee`
  - Timeout check: `hasCustomerConfirmationTimedOut(order, now)` — 24 小時
  - `isManualSource`, `isAutoSource` helpers
  - 驗證邏輯（對應 Java compact constructor）：orderNumber 非空/≤32、sourceType 驗證、MANUAL 無 source snapshot、非 MANUAL 必須有 sourceReference/sourceProductId/sourceProductName/sourceEscortOptionCode、escortUserId !== customerUserId、各 status 對應必要欄位檢查
  - Export `CUSTOMER_CONFIRM_TIMEOUT_MS = 24 * 60 * 60 * 1000` 常數
  - Verify: 單元測試覆蓋所有 7 狀態轉換 + guard conditions + 驗證邏輯

- T2.2 [ ] **`packages/dispatch/src/domain/order-number-generator.ts`** — 實作 `EscortDispatchOrderNumberGenerator`
  - 格式：`ESC-YYYYMMDD-XXXXXX`（6 位英數字尾碼）
  - 字元集：`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`（排除 I、O、0、1 等混淆字元）
  - 使用 `crypto.randomInt()` 產生尾碼
  - 接受 `Clock` 介面注入（預設 `Date.now()`）
  - Verify: 產生 1000 個編號，格式正確，尾碼僅含允許字元

- T2.3 [ ] **`packages/dispatch/src/domain/option-price-view.ts`** — 定義 `OptionPriceView` interface
  - Fields: `optionCode`, `option`（EscortOrderOption reference）, `defaultPriceTwd`, `effectivePriceTwd`, `overridden`
  - Helper: `toDisplayLine()` 格式化為 Discord 顯示文字
  - Verify: 型別正確，display formatting 與 Java 輸出一致

- T2.4 [ ] **`packages/dispatch/src/domain/index.ts`** — barrel export
  - Verify: 無循環依賴

## **Task 3: Repository 層**

Purpose: 實作三個 Repository 介面與 Drizzle 實作，含條件式 UPDATE（WHERE 護欄 + RETURNING）。
Requirements: R3.1–R3.2, R8.2, R9.2, R11.1–R11.7, R12.1–R12.4, R13.1–R13.3
Scope: `packages/dispatch/src/repo/`
Out of scope: Service 層邏輯（僅資料存取）

- T3.1 [ ] **`packages/dispatch/src/repo/escort-dispatch-order.repo.ts`** — 定義 `EscortDispatchOrderRepo` interface
  - `save(order: EscortDispatchOrderInsert): Promise<EscortDispatchOrder>` — INSERT ... RETURNING *
  - `update(order: EscortDispatchOrder): Promise<EscortDispatchOrder>` — UPDATE ... WHERE id=?
  - `findByOrderNumber(orderNumber: string): Promise<EscortDispatchOrder | null>`
  - `findBySourceIdentity(sourceType: SourceType, sourceReference: string): Promise<EscortDispatchOrder | null>`
  - `findRecentByGuildId(guildId: string, limit: number): Promise<EscortDispatchOrder[]>` — ORDER BY created_at DESC
  - `findPendingAssignmentByGuildId(guildId: string, limit: number): Promise<EscortDispatchOrder[]>` — WHERE status=PENDING_CONFIRMATION AND escort_user_id=0, ORDER BY created_at ASC
  - `assignEscort(orderNumber, assignedByUserId, escortUserId, assignedAt): Promise<EscortDispatchOrder | null>` — conditional UPDATE: WHERE order_number=? AND status='PENDING_CONFIRMATION' AND escort_user_id=0 AND customer_user_id<>? RETURNING *
  - `claimAfterSales(orderNumber, assigneeUserId, assignedAt): Promise<EscortDispatchOrder | null>` — conditional UPDATE: WHERE order_number=? AND status='AFTER_SALES_REQUESTED' AND after_sales_assignee_user_id IS NULL RETURNING *
  - `closeAfterSales(orderNumber, assigneeUserId, closedAt): Promise<EscortDispatchOrder | null>` — conditional UPDATE: WHERE order_number=? AND status='AFTER_SALES_IN_PROGRESS' AND after_sales_assignee_user_id=? RETURNING *
  - `existsByOrderNumber(orderNumber: string): Promise<boolean>`
  - Verify: interface 編譯通過，方法簽名對應 Java `EscortDispatchOrderRepository`

- T3.2 [ ] **`packages/dispatch/src/repo/drizzle-escort-dispatch-order.repo.ts`** — Drizzle 實作
  - 使用 Drizzle 的 `sql` 模板執行條件式 UPDATE + RETURNING
  - Row mapping function：DB row → `EscortDispatchOrder` domain object（nullable 欄位、enum string → enum value）
  - Verify: 整合測試 PostgreSQL → 驗證 conditional UPDATE guard 行為正確

- T3.3 [ ] **`packages/dispatch/src/repo/escort-option-price.repo.ts`** — 定義 `EscortOptionPriceRepo` interface
  - `findAllByGuildId(guildId): Promise<Map<string, number>>`
  - `findByGuildIdAndOptionCode(guildId, optionCode): Promise<number | null>`
  - `upsert(guildId, optionCode, priceTwd, updatedByUserId): Promise<void>` — ON CONFLICT (guild_id, option_code) DO UPDATE
  - `delete(guildId, optionCode): Promise<boolean>`
  - Verify: interface 對應 Java `EscortOptionPriceRepository`

- T3.4 [ ] **`packages/dispatch/src/repo/drizzle-escort-option-price.repo.ts`** — Drizzle 實作
  - Verify: 整合測試 PostgreSQL

- T3.5 [ ] **`packages/dispatch/src/repo/dispatch-after-sales-staff.repo.ts`** — 定義 `DispatchAfterSalesStaffRepo` interface
  - `findStaffUserIds(guildId): Promise<Set<string>>`
  - `addStaff(guildId, userId): Promise<boolean>` — INSERT ON CONFLICT DO NOTHING，true=插入成功
  - `removeStaff(guildId, userId): Promise<boolean>` — DELETE，true=刪除成功
  - Verify: interface 對應 Java `DispatchAfterSalesStaffRepository`

- T3.6 [ ] **`packages/dispatch/src/repo/drizzle-dispatch-after-sales-staff.repo.ts`** — Drizzle 實作
  - Verify: 整合測試 PostgreSQL

- T3.7 [ ] **`packages/dispatch/src/repo/index.ts`** — barrel export
  - Verify: 無循環依賴

## **Task 4: 核心服務層**

Purpose: 實作所有業務邏輯服務——訂單生命週期、handoff 交接、定價覆寫、售後人員管理。
Requirements: R1–R11, R13, R15
Scope: `packages/dispatch/src/service/`
Out of scope: Discord 互動處理（屬於 panel 層）、DM 發送（屬於 notification 層）

- T4.1 [ ] **`packages/dispatch/src/service/escort-dispatch-order.service.ts`** — 實作 `EscortDispatchOrderService`
  - Constructor injection: `EscortDispatchOrderRepo`, `EscortDispatchOrderNumberGenerator`, `Clock`（optional, default `Date`）
  - Public methods（全部回傳 `Result<T, DomainError>`）:
    - `createOrder(guildId, assignedByUserId, escortUserId, customerUserId)` — verify escortUserId != customerUserId, generate unique order number (max 20 retries), save
    - `createManualOpenOrder(guildId, assignedByUserId, customerUserId, escortOptionCode)` — verify customerUserId>0, option code valid, create order with escortUserId=0
    - `assignPendingOrder(orderNumber, assignedByUserId, escortUserId)` — find order, verify status, verify not already assigned, verify not same as customer, call repo.assignEscort (atomic), handle null=race condition
    - `confirmOrder(orderNumber, confirmerUserId)` — find, verify canBeConfirmedBy + isPendingEscortConfirmation, withConfirmed + repo.update
    - `requestCompletion(orderNumber, escortUserId)` — find, verify canBeCompletedByEscort + isConfirmed, withCompletionRequested + repo.update
    - `customerConfirmCompletion(orderNumber, customerUserId)` — find, verify canBeConfirmedByCustomer, idempotent if COMPLETED, verify isPendingCustomerConfirmation, ensureTimeoutCompletion → withCompleted + repo.update
    - `requestAfterSales(orderNumber, customerUserId)` — find, verify canBeConfirmedByCustomer, ensureTimeoutCompletion, verify not already in after-sales, verify PENDING_CUSTOMER_CONFIRMATION or COMPLETED, withAfterSalesRequested + repo.update
    - `claimAfterSales(orderNumber, afterSalesUserId)` — find, verify status=AFTER_SALES_REQUESTED, call repo.claimAfterSales, if null→re-query to distinguish race condition
    - `closeAfterSales(orderNumber, afterSalesUserId)` — find, verify isAfterSalesInProgress + isAfterSalesAssignee, call repo.closeAfterSales, if null→error
    - `findRecentOrders(guildId, limit?)` — default 10, max 20, normalize each order via ensureTimeoutCompletion
    - `findPendingAssignmentOrders(guildId, limit?)` — default 5, max 25
  - Private helpers: `findOrder`, `ensureTimeoutCompletion`, `generateUniqueOrderNumber` (max 20 retries), `normalizeLimit`, `normalizeEscortOption`
  - Method count: 11 個 public methods（含 createOrder、createManualOpenOrder、assignPendingOrder、confirmOrder、requestCompletion、customerConfirmCompletion、requestAfterSales、claimAfterSales、closeAfterSales、findRecentOrders、findPendingAssignmentOrders）+ 4 個 private helpers
  - Verify: 單元測試覆蓋所有方法（mock repo），對應 Java `EscortDispatchOrderServiceTest`

- T4.2 [ ] **`packages/dispatch/src/service/escort-dispatch-handoff.service.ts`** — 實作 `EscortDispatchHandoffService`
  - Constructor injection: `EscortDispatchOrderRepo`, `EscortDispatchOrderNumberGenerator`（可選）
  - `handoffFromCurrencyPurchase(guildId, buyerUserId, product, sourceReference): Result`
  - `handoffFromFiatPayment(guildId, buyerUserId, product, sourceReference): Result`
  - Private `handoff(guildId, buyerUserId, product, sourceReference, sourceType)`:
    - Validate product not null, shouldAutoCreateEscortOrder, sourceReference not blank, escortOptionCode not blank
    - Idempotency: `repo.findBySourceIdentity` → if exists return existing
    - Create auto handoff order (escortUserId=0, assignedByUserId=0) with full product snapshot
    - Exception fallback: catch → `findBySourceIdentity` again → if found return existing, else return persistenceFailure
  - Verify: 單元測試覆蓋 idempotency + fallback，對應 Java `EscortDispatchHandoffServiceTest`

- T4.3 [ ] **`packages/dispatch/src/service/dispatch-after-sales-staff.service.ts`** — 實作 `DispatchAfterSalesStaffService`
  - Constructor injection: `DispatchAfterSalesStaffRepo`
  - `getStaffUserIds(guildId)`, `addStaff(guildId, userId)`, `removeStaff(guildId, userId)`, `isAfterSalesStaff(guildId, userId)` — exception safe (returns false)
  - Verify: 單元測試

- T4.4 [ ] **`packages/dispatch/src/service/escort-option-pricing.service.ts`** — 實作 `EscortOptionPricingService`
  - Constructor injection: `EscortOptionPriceRepo`, `EscortOptionCatalogRepository`（from shared）
  - `listOptionPrices(guildId)` — merge catalog defaults with guild overrides
  - `updateOptionPrice(guildId, updatedByUserId, optionCode, priceTwd)` — validate priceTwd>0, code valid, repo.upsert
  - `resetOptionPrice(guildId, optionCode)` — validate code exists, repo.delete
  - `getEffectivePrice(guildId, optionCode)` — return override ?? catalog default
  - Verify: 單元測試

- T4.5 [ ] **`packages/dispatch/src/service/index.ts`** — barrel export
  - Verify: 無循環依賴

## **Task 5: 通知服務**

Purpose: 實作所有 DM 通知管線——護航者、客戶、售後人員的通知發送。
Requirements: R3.4, R4.3, R5.3, R6.4, R7.4–R7.5, R8.4, R9.3
Scope: `packages/dispatch/src/notification/`
Out of scope: Embed 內容建構（訊息格式在 panel/MessageFactory 中定義，此處僅負責發送）

- T5.1 [ ] **`packages/dispatch/src/notification/dispatch-notification.service.ts`** — 實作 `DispatchNotificationService`
  - Constructor injection: `DiscordRuntimeGateway`（from shared）、`DispatchAfterSalesStaffService`
  - `notifyEscortPendingOrder(order, customerMention, confirmButtonId)` — DM to escort with embed + Confirm button
  - `notifyCustomerOrderConfirmed(order, escortMention)` — DM to customer
  - `notifyCustomerCompletionOptions(order, escortMention)` — DM to customer with "Confirm Completion" + "Request After-Sales" buttons, footer with 24h timeout note
  - `notifyEscortOrderCompleted(order)` — DM to escort
  - `notifyAfterSalesStaff(order, claimButtonId): Promise<AfterSalesNotifyResult>` — query staff list, prioritize online, fallback all, DM each with embed + Claim button
  - `notifyCustomerAfterSalesAssigned(order, assigneeMention)` — DM to customer
  - `notifyCustomerAfterSalesClosed(order, closerMention)` — DM to customer
  - 所有 DM 發送失敗僅記錄 warn log，不拋出例外（best-effort delivery）
  - Verify: 單元測試 mock DiscordRuntimeGateway，對應 Java notification 邏輯

- T5.2 [ ] **`packages/dispatch/src/notification/index.ts`** — barrel export
  - Verify: 無循環依賴

## **Task 6: Panel 面板互動層**

Purpose: 實作 Discord slash command 與所有 button/select interaction 處理器，含 session state、embed 建構、component 組裝。
Requirements: R1–R9, R14, R15
Scope: `packages/dispatch/src/panel/`
Out of scope: 通知發送（使用 NotificationService）

- T6.1 [ ] **`packages/dispatch/src/panel/dispatch-panel-view.ts`** — 實作 `DispatchPanelView`（常數 + embed + component builder）
  - 定義所有 component custom ID 常數（對應 Java `DispatchPanelView`）
  - Embed builders: `buildModeEmbed`, `buildCreateOrderEmbed`, `buildAssignOrderEmbed`
  - Component builders: `buildModeComponents`, `buildCreateOrderComponents`, `buildAssignOrderComponents`
  - 護航品類 select menu 自動分頁：超過 25 個選項時 split 為兩個 select
  - 待派單訂單 select menu 自動分頁
  - Format helpers: `formatPendingAssignmentOrders`, `formatSourceSummary`, `formatUserMention`
  - Verify: component ID 與 Java 一致；embed 顏色 `0x5865F2` 一致

- T6.2 [ ] **`packages/dispatch/src/panel/dispatch-panel-message-factory.ts`** — 實作 `DispatchPanelMessageFactory`（10+ embed variants）
  - `buildHistoryEmbed`, `buildEscortPendingEmbed`, `buildEscortConfirmedEmbed`, `buildEscortCompletionRequestedEmbed`, `buildCustomerOrderConfirmedEmbed`, `buildCustomerCompletionActionEmbed`, `buildCustomerCompletedEmbed`, `buildEscortOrderCompletedEmbed`, `buildCustomerAfterSalesRequestedEmbed`, `buildAfterSalesNotificationEmbed`, `buildAfterSalesClaimedEmbed`, `buildAfterSalesClosedEmbed`
  - 顏色: INFO=0x57F287, WARNING=0xFEE75C, ERROR=0xED4245
  - Verify: embed 文字與 Java `DispatchPanelMessageFactory` 一致

- T6.3 [ ] **`packages/dispatch/src/panel/dispatch-panel-command-handler.ts`** — 實作 `/dispatch-panel` slash command handler
  - Admin only（ADMINISTRATOR 權限 或 guild owner）
  - 顯示 mode embed + mode components（ephemeral）
  - Verify: 權限檢查與 Java `DispatchPanelCommandHandler` 一致

- T6.4 [ ] **`packages/dispatch/src/panel/dispatch-panel-interaction-handler.ts`** — 實作 `DispatchPanelInteractionHandler`
  - Constructor injection: `EscortDispatchOrderService`, `DispatchAfterSalesStaffService`, `DispatchNotificationService`
  - Session state: `Map<string, SessionState>`（key=`${guildId}:${userId}`）
  - `SessionState` 型別：mode, escortUserId/Mention, customerUserId/Mention, escortOptionCode, selectedOrderNumber, pendingOrders, statusMessage；`resetForMode`, `isCreateMode`, `isAssignMode`, `canCreateOpenOrder`, `canAssignOrder` 方法
  - Event handlers: entity select → escort/customer select；string select → mode/option/pending order；button → create/assign/back/history + DM flow buttons
  - Button DM flow handlers（全部 DM-only）:
    - `handleOrderConfirmation` → confirmOrder → update escort DM + notify customer
    - `handleEscortCompletionRequest` → requestCompletion → update escort DM + notify customer
    - `handleCustomerCompletionConfirmation` → customerConfirmCompletion → update customer DM + notify escort
    - `handleCustomerAfterSalesRequest` → requestAfterSales → update customer DM + notify staff
    - `handleAfterSalesClaim` → verify staff → claimAfterSales → update staff DM + notify customer
    - `handleAfterSalesClose` → closeAfterSales → update staff DM + notify customer
  - Admin check / Guild-only check / DM-only check
  - DM 發送失敗處理：操作仍視為成功但提示管理員手動通知
  - Verify: 所有互動流程與 Java `DispatchPanelInteractionHandler` 一致

- T6.5 [ ] **`packages/dispatch/src/panel/index.ts`** — barrel export
  - Verify: 無循環依賴

## **Task 7: DI 註冊**

Purpose: 將所有 service、repository、handler 註冊到 shared DI container。
Requirements: 所有模組
Scope: `packages/dispatch/src/di/`

- T7.1 [ ] **`packages/dispatch/src/di/dispatch-module.ts`** — 實作 `dispatchModule` 註冊函數
  - Register all repositories (singleton): `DrizzleEscortDispatchOrderRepo`, `DrizzleEscortOptionPriceRepo`, `DrizzleDispatchAfterSalesStaffRepo`
  - Register all services (singleton): `EscortDispatchOrderService`, `EscortDispatchHandoffService`, `DispatchAfterSalesStaffService`, `EscortOptionPricingService`, `EscortDispatchOrderNumberGenerator`
  - Register notification (singleton): `DispatchNotificationService`
  - Register panel handlers (singleton): `DispatchPanelCommandHandler`, `DispatchPanelInteractionHandler`
  - Verify: 所有 dependency 線對應 Java `DispatchModule` 的 Dagger `@Provides` 方法

- T7.2 [ ] **`packages/dispatch/src/di/index.ts`** — barrel export
  - Verify: 無循環依賴

## **Task 8: Package 設定與 entry point**

Purpose: 設定 `packages/dispatch/` 的 package.json、tsconfig.json、export map。
Requirements: 所有模組
Scope: `packages/dispatch/`

- T8.1 [ ] **`packages/dispatch/package.json`** — package 設定
  - name: `@ltdjms/dispatch`, dependency: `@ltdjms/shared` (workspace:*)
  - Scripts: build, test, typecheck
  - Verify: `pnpm install` 通過

- T8.2 [ ] **`packages/dispatch/tsconfig.json`** — TypeScript 設定，extends root base config
  - Verify: `tsc --noEmit` 通過

## **Task 9: 單元測試**

Purpose: 為所有 service、domain model、repository、notification、panel handler 編寫 Vitest 單元測試。
Requirements: R1–R15
Scope: `packages/dispatch/src/__tests__/`

- T9.1 [ ] **`__tests__/domain/escort-dispatch-order.test.ts`** — Domain model 測試：factory functions、狀態轉換、驗證邏輯、predicate/guard、timeout 邊界條件
- T9.2 [ ] **`__tests__/domain/order-number-generator.test.ts`** — 格式驗證、字元集、無重複
- T9.3 [ ] **`__tests__/service/escort-dispatch-order.service.test.ts`** — 所有 create/assign/confirm/complete/after-sales 流程、24h timeout、idempotency、race condition
- T9.4 [ ] **`__tests__/service/escort-dispatch-handoff.service.test.ts`** — handoff flow、idempotency、exception fallback
- T9.5 [ ] **`__tests__/service/escort-option-pricing.service.test.ts`** — list/update/reset/effective price
- T9.6 [ ] **`__tests__/service/dispatch-after-sales-staff.service.test.ts`** — add/remove/query/isStaff
- T9.7 [ ] **`__tests__/repo/drizzle-escort-dispatch-order.repo.test.ts`** — 整合測試 PostgreSQL，conditional UPDATE guard
- T9.8 [ ] **`__tests__/notification/dispatch-notification.service.test.ts`** — DM 發送流程、失敗不拋出、在線優先
- T9.9 [ ] **`__tests__/panel/dispatch-panel-interaction-handler.test.ts`** — 互動流程、session state、權限檢查
- T9.10 [ ] **`__tests__/panel/dispatch-panel-message-factory.test.ts`** — embed 輸出驗證

## **Task 10: 最終整合驗證**

Purpose: 確保所有檔案編譯通過，測試全部通過，package export 正確。
Requirements: 所有模組
Scope: 全 `packages/dispatch/`

- T10.1 [ ] **TypeScript 編譯** — `tsc --noEmit` 零錯誤
- T10.2 [ ] **單元測試** — `vitest run` 全部通過
- T10.3 [ ] **Lint / Format** — ESLint + Prettier 檢查通過
- T10.4 [ ] **與 shared 整合驗證** — import `@ltdjms/shared` 的所有型別可正常解析
