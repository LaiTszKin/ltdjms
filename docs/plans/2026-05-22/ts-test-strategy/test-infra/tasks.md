# Tasks: Integration PBT Test Infrastructure

- Date: 2026-05-22
- Feature: Integration PBT Test Infrastructure

## **Task 1: 安裝依賴與 vitest 配置**

Purpose: 新增 testcontainers、fast-check 等 devDependencies 並設定 vitest globalSetup/teardown
Requirements: R1.x
Scope: `package.json`、`vitest.config.ts`、`vitest.globalSetup.ts`
Out of scope: 任何測試案例撰寫、業務邏輯變更

- T1.1 [x] **根目錄 `package.json`** — 在 devDependencies 新增 `@testcontainers/postgresql`、`fast-check`、`testcontainers`
  - Verify: `pnpm install` 成功無報錯

- T1.2 [x] **各 package `vitest.config.ts`** — 確認或新增 `globalSetup` / `setupFile` 欄位指向共享配置
  - Verify: `vitest run --config packages/economy/vitest.config.ts` 可正常啟動

- T1.3 [x] **`packages/shared/src/__tests__/vitest.globalSetup.ts`** — 建立 globalSetup，啟動 testcontainers PostgreSQL 容器，執行 migration，匯出 `DB_CONNECTION_URL` 環境變數
  - Verify: 單獨執行 globalSetup script 確認容器啟動成功

- T1.4 [x] **`packages/shared/src/__tests__/vitest.globalTeardown.ts`** — 建立 globalTeardown，停止並移除容器
  - Verify: globalTeardown 執行後 `docker ps` 確認容器已移除

## **Task 2: Template DB 快速 Reset**

Purpose: 實作 template database 機制，讓每個 PBT run 在 100ms 內重置 DB
Requirements: R2.x
Scope: `packages/shared/src/infra/database/test-db-reset.ts`
Out of scope: migration runner 修改

- T2.1 [x] **`packages/shared/src/infra/database/test-db-reset.ts`** — 匯出 `createTemplateDatabase(pool)` 函數，建立 template_clean DB（純 schema 無資料）
  - Verify: 檢查 template_clean DB 存在且 table count > 0、row count = 0

- T2.2 [x] **`packages/shared/src/infra/database/test-db-reset.ts`** — 匯出 `resetDatabase(pool, dbName)` 函數，DROP/CREATE DATABASE FROM TEMPLATE
  - Verify: measure 連續 10 次 reset 平均時間 ≤ 100ms

- T2.3 [x] **`packages/shared/src/infra/database/test-db-reset.ts`** — 匯出 `getTestPool(connectionUrl)` 函數，建立連到 template 之外的 test db 的 pg Pool
  - Verify: pool.query('SELECT 1') 成功

## **Task 3: Seed Data Factory**

Purpose: 建立可組合的 factory function，支援多 guild、多用戶、多貨幣參數場景
Requirements: R3.x
Scope: `packages/shared/src/__tests__/seed-factory.ts`
Out of scope: 業務邏輯測試

- T3.1 [x] **`packages/shared/src/__tests__/seed-factory.ts`** — 匯出 `seedGuild(db, overrides?)`，在 currency_config 插入 guild 配置，回傳完整 guild config
  - Verify: 呼叫後 DB 查詢確認 currency_config row 存在

- T3.2 [x] **`packages/shared/src/__tests__/seed-factory.ts`** — 匯出 `seedUserAccount(db, overrides?)`，在 currency_account 插入用戶帳戶，回傳完整帳戶
  - Verify: 呼叫後 DB 查詢確認 currency_account row 存在且餘額正確

- T3.3 [x] **`packages/shared/src/__tests__/seed-factory.ts`** — 匯出 `seedProduct(db, overrides?)`，在 product table 插入商品
  - Verify: 呼叫後 DB 查詢確認 product row 存在

- T3.4 [x] **`packages/shared/src/__tests__/seed-factory.ts`** — 匯出 `seedRedemptionCode(db, overrides?)`，在 redemption_code table 插入兌換碼
  - Verify: 呼叫後 DB 查詢確認 redemption_code row 存在

- T3.5 [x] **`packages/shared/src/__tests__/seed-factory.ts`** — 匯出 `seedDiceConfig(db, overrides?)`，插入 dice 遊戲配置
  - Verify: 確認 dice config row 存在

## **Task 4: fast-check Arbitrary 封裝**

Purpose: 封裝業務領域型別的 fast-check arbitrary，讓測試直接使用
Requirements: R4.x
Scope: `packages/shared/src/__tests__/arbitrary.ts`
Out of scope: 具體 PBT 測試案例

- T4.1 [x] **`packages/shared/src/__tests__/arbitrary.ts`** — 匯出 `guildId()` arbitrary（discord snowflake 格式字串）
  - Verify: `fc.assert(fc.property(guildId(), id => /^\d{17,19}$/.test(id)))`

- T4.2 [x] **`packages/shared/src/__tests__/arbitrary.ts`** — 匯出 `userId()` arbitrary（discord snowflake 格式字串）
  - Verify: 同上驗證

- T4.3 [x] **`packages/shared/src/__tests__/arbitrary.ts`** — 匯出 `positiveAmount(min?, max?)`（正整數）
  - Verify: 所有產生值 > 0 且為整數

- T4.4 [x] **`packages/shared/src/__tests__/arbitrary.ts`** — 匯出 `multiplier()`（常見賠率）
  - Verify: 所有產生值在預定義集合中

- T4.5 [x] **`packages/shared/src/__tests__/arbitrary.ts`** — 匯出 `transferRequest()` arbitrary（組合 guildId + senderId + receiverId + amount）
  - Verify: 產生物件結構符合 TransferRequest 型別

## **Task 5: 測試 DI Container Helper**

Purpose: 提供 `createTestContainer()` 快速建立測試用 DI 容器
Requirements: R5.x
Scope: `packages/shared/src/__tests__/test-container.ts`
Out of scope: 修改任何 DI module 配置

- T5.1 [x] **`packages/shared/src/__tests__/test-container.ts`** — 匯出 `createTestContainer(options)`，註冊真實 DB pool + NoOpCache + silent logger + mock Discord gateway
  - Verify: container.resolve(TOKENS.DatabasePool) 回傳正確 pool

- T5.2 [x] **`packages/shared/src/__tests__/test-container.ts`** — 支援 `options.overrides` 部分覆蓋 token
  - Verify: 傳入 `{ overrides: { [TOKENS.Logger]: customLogger } }`，resolve 確認回傳 customLogger

- T5.3 [x] **`packages/shared/src/__tests__/test-container.ts`** — 匯出 `configureEconomyInTest(container)` 等 helper，呼叫各 module 的 configure 函數
  - Verify: resolve ECONOMY_TOKENS.BalanceService 成功

## **Task 6: 共用 Assertion Helper**

Purpose: 提供測試常用的 assertion helper
Requirements: R6.x
Scope: `packages/shared/src/__tests__/assertion-helper.ts`
Out of scope: 特定業務邏輯的 assertion

- T6.1 [x] **`packages/shared/src/__tests__/assertion-helper.ts`** — 匯出 `assertBalanceConserved(before, after)` 比較前後餘額總和
  - Verify: 單元測試驗證 helper 本身正確

- T6.2 [x] **`packages/shared/src/__tests__/assertion-helper.ts`** — 匯出 `assertStateTransition(from, to, allowed)` 驗證狀態轉移合法性
  - Verify: 合法轉移不回傳錯誤、非法轉移拋 assertion error

- T6.3 [x] **`packages/shared/src/__tests__/assertion-helper.ts`** — 匯出 `measureResponseTime(fn, maxMs?)` 量測執行時間
  - Verify: 確認回傳 `{ result, durationMs }` 結構，超過 maxMs 時拋 assertion error

## **Task 7: 整合驗證**

Purpose: 確保所有基礎設施協同工作，為後續 spec 建立可用的基準
Requirements: 全部 R1-R6
Scope: `packages/shared/src/__tests__/infra-smoke.test.ts`
Out of scope: 具體業務 PBT

- T7.1 [x] **`packages/shared/src/__tests__/infra-smoke.test.ts`** — 撰寫 smoke test：啟動 container → seed guild + 2 users → reset → 確認 DB 乾淨
  - Verify: `vitest run` 通過，總時間 < 5 秒（不含首次 container 啟動）
