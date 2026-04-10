# Design: Issue 75 貨幣持久層單一路徑化

- Date: 2026-04-09
- Feature: Issue 75 貨幣持久層單一路徑化
- Change Name: issue-75-currency-persistence-source-unification

## Design Goal
讓 currency account / guild config repository 的「生產真相」與「主要測試真相」完全重合，避免雙實作長期並存造成 production-only regressions。

## Change Summary
- Requested change: 依 issue #75 收斂 JDBC 與 JOOQ 的平行真相，讓主要 regression coverage 驗證 production path。
- Existing baseline: `CurrencyRepositoryModule` 在 production 綁定 `JooqMemberCurrencyAccountRepository` 與 `JooqGuildCurrencyConfigRepository`；但多數 integration / performance tests 直接 new JDBC account/config repositories；JOOQ integration coverage 也只完整覆蓋了 account repository。
- Proposed design delta: JOOQ 成為 account / config 的唯一 canonical owner；主要測試切到 JOOQ path；舊 JDBC account / config 退出 main-source ownership。

## Scope Mapping
- Spec requirements covered: `R1.1-R1.2`, `R2.1-R2.3`, `R3.1-R3.3`
- Affected modules:
  - `src/main/java/ltdjms/discord/shared/di/CurrencyRepositoryModule.java`
  - `src/main/java/ltdjms/discord/currency/persistence/*`
  - `src/test/java/ltdjms/discord/currency/integration/*`
  - `src/test/java/ltdjms/discord/currency/performance/*`
- External contracts involved: `jOOQ DSLContext-based Persistence`
- Coordination reference: `../coordination.md`

## Current Architecture
目前 currency bounded context 有兩條 account / config persistence path：
1. production path：Dagger module 建立 JOOQ repositories。
2. test-heavy path：integration / performance suites 仍直接建立 JDBC repositories。

這造成三個問題：
- production regressions 可能不會被主要測試抓到。
- `JooqGuildCurrencyConfigRepository` 幾乎沒有對等 coverage。
- 維護者很難判斷哪個實作才是真正 owner。

## Proposed Architecture
- canonical owner：JOOQ account / config repositories。
- test construction path：主要 integration / performance suites 一律透過 JOOQ path 建構 repository/service graph。
- legacy path handling：JDBC account / config repositories 被移除，或降級為短期 transitional/test-only compatibility layer，但不再是主要 coverage owner。

## Implemented Delta
- `PostgresIntegrationTestBase` 現在建立共用 `DSLContext`，讓 currency integration/performance suites 直接使用 production-aligned JOOQ repository path。
- `RepositoryIntegrationTest`、`BalanceServiceIntegrationTest`、`BalanceAdjustmentCommandIntegrationTest`、`CurrencyConfigCommandIntegrationTest`、`BotRestartIntegrationTest` 與 `SlashCommandPerformanceTest` 已切換到 `JooqMemberCurrencyAccountRepository` / `JooqGuildCurrencyConfigRepository`。
- `JooqRepositoryIntegrationTest` 已補上 guild config 的 save / find / update / saveOrUpdate / delete 覆蓋。
- `JdbcMemberCurrencyAccountRepository` 與 `JdbcGuildCurrencyConfigRepository` 已自 main source 移除，只保留 transaction repository 的 JDBC path。

## Component Changes

### Component 1: `CurrencyRepositoryModule`
- Responsibility: 定義 production repository owner。
- Inputs: `DSLContext`, `DataSource`
- Outputs: account / config / transaction repositories
- Dependencies: Dagger, JOOQ, JDBC transaction repository
- Invariants:
  - account / config owner 為 JOOQ
  - transaction repository 不在本 spec 內變更

### Component 2: Currency Account / Config Repositories
- Responsibility: 實作 `MemberCurrencyAccountRepository` 與 `GuildCurrencyConfigRepository`。
- Inputs: guildId、userId、config payload、balance adjustments
- Outputs: domain objects、`Result` / exceptions
- Dependencies: JOOQ `DSLContext`（canonical path）
- Invariants:
  - service contract 不變
  - negative balance / guild isolation / saveOrUpdate semantics 不回歸

### Component 3: Currency Integration / Performance Tests
- Responsibility: 驗證 repository 與 service flows 是否符合 production path 行為。
- Inputs: 測試資料庫、JOOQ repositories、service graph
- Outputs: regression safety net、performance metrics
- Dependencies: `PostgresIntegrationTestBase`, JOOQ repositories, metrics helpers
- Invariants:
  - 主測試路徑預設走 JOOQ
  - 測試命名與結構應清楚表達 coverage 的 canonical path

## Sequence / Control Flow
1. 測試 setup 階段不再 new `JdbcMemberCurrencyAccountRepository` / `JdbcGuildCurrencyConfigRepository` 作為主要路徑。
2. repository / service integration tests 與 performance tests 直接使用 JOOQ repositories 或 production-aligned helper。
3. 若 JDBC account / config 仍暫留，明確標示其 transitional 性質，並禁止主 regression 套件依賴。

## Data / State Impact
- Created or updated data: 測試建構方式、repository 實作 ownership；不變更資料表 schema
- Consistency rules:
  - account / config repository contract 對 service 層不變
  - performance test 測量的必須是 production path
- Migration / rollout needs:
  - 若刪除 JDBC account / config，需要同步更新所有直接依賴它們的測試
  - 若短期保留 transitional path，需在文件／命名上清楚標註清理終點

## Risk and Tradeoffs
- Key risks:
  - 一次性切換大量測試到 JOOQ path 可能暴露先前被 JDBC 掩蓋的真實回歸。
  - 若保留 transitional path 但沒有清理計畫，雙真相仍會回來。
- Rejected alternatives:
  - 繼續讓 JDBC 與 JOOQ 並存，靠人工同步：已證明不可持續。
  - 新增更高一層抽象包住兩套 repository：會掩蓋而非消除雙真相。
- Operational constraints:
  - 不影響既有 slash command contract 與 currency domain service API。
  - 不將 transaction repository 一併捲入，避免 scope 擴張。

## Validation Plan
- Tests:
  - Integration：JOOQ account + config repository 對等 coverage
  - Integration：`BalanceServiceIntegrationTest` 等主回歸路徑改走 JOOQ
  - Performance：`SlashCommandPerformanceTest` 量測 JOOQ path
  - Regression：若保留 transitional JDBC path，需證明主回歸套件已不依賴它
- Contract checks: 以 jOOQ `DSLContext` 官方 manual 確認 production path 的 query 構建中心為 `DSLContext`，因此測試應驗證相同路徑。
- Rollback / fallback: 若切換後暴露大量 production-only regressions，可暫時保留 transitional JDBC path 輔助比對，但不可恢復為主回歸預設。

## Open Questions
None
