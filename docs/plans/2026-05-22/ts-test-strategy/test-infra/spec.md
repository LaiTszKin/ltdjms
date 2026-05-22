# Spec: Integration PBT Test Infrastructure

- Date: 2026-05-22
- Feature: Integration PBT Test Infrastructure
- Owner: [To be filled]

## Goal

建立共享的 Integration Property-Based Testing 基礎設施，讓後續各模組 PBT 能直接使用 Testcontainers PostgreSQL + fast-check + seed data factory + 測試 DI container，無需重複造輪子。

## Scope

### In Scope
- Testcontainers PostgreSQL 容器生命週期管理（globalSetup/globalTeardown）
- Template DB 快速 reset 機制（每輪 PBT run 50-100ms）
- Seed data factory：支援多 guild、多用戶、多貨幣參數場景的 factory function
- fast-check 任意值產生器（Arbitrary）封裝：guildId、userId、金額、賠率等業務領域型別
- 測試用 DI container helper：注入真實 DB pool + Drizzle ORM + mock Discord/Redis，快速建立 service instance
- vitest globalSetup / setupFile 配置，整合 Testcontainers 生命週期
- 共用 assertion helper：餘額守恆檢查、狀態轉移驗證、response time 量測
- 根目錄 `vitest` 新增 devDependencies：`@testcontainers/postgresql`, `fast-check`, `testcontainers`

### Out of Scope
- 任何具體業務邏輯的 PBT（由 economy-pbt、shop-pbt、admin-pbt 負責）
- ECPay 外部 API 整合測試基礎設施（由 ecpay-e2e 負責）
- Discord.js mock 實作（已有 `MockDiscordContext` 等）
- CI/CD pipeline 配置（由維運團隊負責）

## Functional Behaviors (BDD)

### Requirement 1: Testcontainer PostgreSQL 生命週期
**GIVEN** 開發者執行 `vitest run`
**AND** Docker daemon 正在運行
**WHEN** vitest globalSetup 觸發
**THEN** 自動啟動 testcontainers/postgresql:16-alpine 容器
**AND** 執行所有 Flyway migration 建立完整 schema
**AND** 測試結束後 globalTeardown 自動停止並移除容器

**Requirements**:
- [ ] R1.1 容器啟動不超過 30 秒
- [ ] R1.2 Migration 執行順序與 production 一致
- [ ] R1.3 容器 port 動態綁定（不硬編 5432），透過環境變數傳遞連線字串
- [ ] R1.4 globalTeardown 即使測試中途 crash 也能清理容器（signal handler）

### Requirement 2: Template DB 快速 Reset
**GIVEN** PostgreSQL 容器已啟動且 migration 完成
**AND** 參考資料庫中有一份「純 schema、無資料」的 template database
**WHEN** 每個 PBT run 開始前呼叫 `resetDatabase()`
**THEN** 用 `DROP DATABASE IF EXISTS testdb; CREATE DATABASE testdb TEMPLATE template_clean;` 在 50-100ms 內重置
**AND** 確保前次 PBT run 的殘留資料不影響下次

**Requirements**:
- [ ] R2.1 reset 時間穩定在 100ms 以內
- [ ] R2.2 連續 100 次 reset 無連線洩漏
- [ ] R2.3 若 template DB 不存在則自動建立

### Requirement 3: Seed Data Factory
**GIVEN** 乾淨的測試資料庫
**WHEN** 測試呼叫 `seedGuild({ guildId: '123', currencyName: '金幣', ... })`
**THEN** 自動在 DB 中建立 guild 的貨幣配置、初始帳戶等必要資料
**AND** 支援建立多個 guild（資料隔離場景）
**AND** 支援建立多個用戶帳戶（多用戶互動場景）

**Requirements**:
- [ ] R3.1 `seedGuild()` factory function 支援貨幣名稱、初始匯率、符號
- [ ] R3.2 `seedUserAccount()` factory function 支援初始餘額、遊戲代幣
- [ ] R3.3 `seedProduct()` factory function 支援貨幣/Fiat 雙價格、護航選項
- [ ] R3.4 `seedRedemptionCode()` factory function 支援批量生成
- [ ] R3.5 所有 factory 回傳型別完整（含自動生成的 ID）
- [ ] R3.6 Factory 支援 partial override（只傳要覆蓋的欄位，其餘用 sensible default）

### Requirement 4: fast-check Arbitrary 封裝
**GIVEN** 需要產生隨機測試輸入
**WHEN** 測試使用 `arbitrary.guildId()`、`arbitrary.userId()`、`arbitrary.positiveAmount()` 等
**THEN** 產生的值符合業務約束（如金額為正整數、userId 為 Discord snowflake 格式）
**AND** 支援 `fc.commands()` 所需的 model-based testing 原語

**Requirements**:
- [ ] R4.1 `guildId` arbitrary 產生 discord snowflake 格式字串
- [ ] R4.2 `userId` arbitrary 產生 discord snowflake 格式字串
- [ ] R4.3 `positiveAmount` arbitrary 產生 1-100000 範圍的正整數
- [ ] R4.4 `betAmount` arbitrary 產生 1-1000000 範圍的正整數
- [ ] R4.5 `multiplier` arbitrary 產生常見賠率（0.1, 0.5, 1.0, 1.5, 2.0 等）
- [ ] R4.6 支援 `fc.record()` 組合多個 arbitrary 產生完整請求物件

### Requirement 5: 測試 DI Container Helper
**GIVEN** 需要測試某個 service（如 BalanceService）
**WHEN** 測試呼叫 `createTestContainer({ dbPool })`
**THEN** 自動註冊真實 DB pool、Drizzle ORM、NoOpCacheService、NullLogger
**AND** 允許個別 override（如注入 spy/mock eventPublisher）
**AND** 回傳可 resolve 所有 service token 的 tsyringe container

**Requirements**:
- [ ] R5.1 `createTestContainer()` 接受 `Pool` 參數並註冊全部 shared tokens
- [ ] R5.2 支援 token-level override：`{ overrides: { [TOKENS.Logger]: customLogger } }`
- [ ] R5.3 支援呼叫各 module 的 `configure*Container()` 函數（economy、shop、dispatch 等）
- [ ] R5.4 每個 `createTestContainer()` 呼叫回傳獨立的 container instance（隔離測試）

### Requirement 6: 共用 Assertion Helper
**GIVEN** 測試需要驗證業務不變量
**WHEN** 測試呼叫 `assertBalanceConserved(guildId, before, after)`
**THEN** 計算 `sum(before.balances) === sum(after.balances)` 並回傳比對結果
**AND** 提供 `measureResponseTime(fn)` 包裝器，記錄執行時間並斷言不超過閾值

**Requirements**:
- [ ] R6.1 `assertBalanceConserved()` 支援多用戶餘額比較
- [ ] R6.2 `assertStateTransition()` 驗證狀態機轉移合法性
- [ ] R6.3 `measureResponseTime()` 回傳 `{ result, durationMs }` 並可選 assert max

## Error and Edge Cases
- [ ] Docker daemon 無法連線時，globalSetup 給出清晰錯誤訊息並 skip（不 crash）
- [ ] Template DB 建立失敗時自動 fallback：drop/create 取代 template 模式
- [ ] 多個測試檔並行執行時，各自使用獨立 DB（或透過 mutex 序列化 DB reset）
- [ ] seed factory 產生的資料違反 unique constraint 時自動 retry
- [ ] Testcontainer port 衝突時自動重試不同 port

## Clarification Questions
None

## References
- Official docs:
  - https://node.testcontainers.org/ — Testcontainers for Node.js
  - https://fast-check.dev/ — fast-check PBT framework
  - https://vitest.dev/config/ — vitest configuration
- Related code files:
  - `packages/shared/src/infra/di/container.ts` — DI container initialize
  - `packages/shared/src/infra/database/connection.ts` — DB pool factory
  - `packages/shared/src/infra/database/migration-runner.ts` — Flyway migration runner
  - `packages/economy/src/di/economy-module.ts` — Economy DI registration
  - `packages/shop/src/di/shop-module.ts` — Shop DI registration
  - `packages/shared/db/migrations/` — 所有 migration SQL
