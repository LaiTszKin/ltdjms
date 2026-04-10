# Spec: Issue 75 貨幣持久層單一路徑化

- Date: 2026-04-09
- Feature: Issue 75 貨幣持久層單一路徑化
- Owner: Codex

## Goal
讓 currency account / guild config repository 在 production wiring、integration tests 與 performance tests 上都對齊同一個 canonical implementation path，避免測試綠燈卻只驗證了非生產實作。

## Scope

### In Scope
- 以目前 production DI 已採用的 JOOQ repositories 作為 currency account / config 的 canonical path。
- 將主要 integration / performance tests 改為走 production-aligned repository instantiation。
- 為 `JooqGuildCurrencyConfigRepository` 補足對等的 integration coverage。
- 規劃或實作移除 `JdbcMemberCurrencyAccountRepository` / `JdbcGuildCurrencyConfigRepository` 在主程式中的平行真相角色。

### Out of Scope
- `CurrencyTransactionRepository` 的 JDBC 路徑（本 issue 未涵蓋）。
- 重寫 currency domain service 或 slash command contract。
- 變更資料表 schema 或 jOOQ codegen 流程。
- 大規模性能優化；本 spec 只確保 performance tests 驗證的是生產路徑。

## Functional Behaviors (BDD)

### Requirement 1: Production 與主要回歸測試必須走同一路徑
**GIVEN** `CurrencyRepositoryModule` 已在 production DI 中綁定 JOOQ account / config repositories  
**AND** integration / performance suites 是主要回歸安全網  
**WHEN** 測試建立 currency repositories 或 service graph  
**THEN** 它們必須預設走與 production 相同的 JOOQ implementation path  
**AND** 不可再讓主回歸套件只驗證 JDBC 平行實作

**Requirements**:
- [x] R1.1 主要 integration / performance 測試改為使用 JOOQ account / config repositories。
- [x] R1.2 測試中的 repository 建構方式需與 production wiring 對齊（直接使用 JOOQ 或共用 factory / module）。

### Requirement 2: Canonical path 的 coverage 必須覆蓋 account 與 guild config
**GIVEN** production 會同時使用 member account 與 guild currency config repositories  
**WHEN** 回歸測試驗證 repository 與 service flows  
**THEN** JOOQ account 與 JOOQ guild config 都必須有對等覆蓋  
**AND** 不可只測到其中一半而把另一半交給舊 JDBC 路徑代測

**Requirements**:
- [x] R2.1 `JooqGuildCurrencyConfigRepository` 補上完整 integration coverage。
- [x] R2.2 `BalanceServiceIntegrationTest`、`RepositoryIntegrationTest`、performance suite 等至少一條主回歸路徑必須驗證 JOOQ config + account 組合。
- [x] R2.3 測試命名與結構需讓維護者一眼看出「這是 production path coverage」。

### Requirement 3: 平行 JDBC 真相必須退出主程式責任範圍
**GIVEN** repository contract 最終只能有一套 canonical owner  
**WHEN** tests 與 production 都已對齊 JOOQ path  
**THEN** 舊 JDBC account / config implementations 必須被移除、降級為明確的 test-only compatibility layer，或至少不再被主回歸套件依賴  
**AND** 不可再讓 main-source JDBC 與 JOOQ 同時承擔相同 contract 的 live ownership

**Requirements**:
- [x] R3.1 `JdbcMemberCurrencyAccountRepository` / `JdbcGuildCurrencyConfigRepository` 必須有明確處置：刪除、移至 test fixture，或標示為 transitional adapter 並附清理終點。
- [x] R3.2 任何仍存在的 transitional path 都不得再作為主要 integration / performance coverage 的預設路徑。
- [x] R3.3 service 對 repository contract 的外部行為不變。

## Error and Edge Cases
- [x] JOOQ config repository 的 save / update / saveOrUpdate / delete / find 不可因測試切換而出現 coverage 盲點。
- [x] 多執行緒 performance test 需驗證的仍是 production path，而不是因簡化而退回 JDBC。
- [x] 若保留 transitional JDBC path，必須避免維護者誤把它當 production owner。
- [x] 測試 factory / helper 不可偷偷改變 repository 行為或交易邊界。
- [x] 既有 `Result` / exception semantics、negative balance、guild isolation 等核心行為不可回歸。

## Clarification Questions
None

## References
- Official docs:
  - `https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/`
- Related code files:
  - `src/main/java/ltdjms/discord/shared/di/CurrencyRepositoryModule.java`
  - `src/main/java/ltdjms/discord/currency/persistence/JooqMemberCurrencyAccountRepository.java`
  - `src/main/java/ltdjms/discord/currency/persistence/JooqGuildCurrencyConfigRepository.java`
  - `src/test/java/ltdjms/discord/currency/integration/RepositoryIntegrationTest.java`
  - `src/test/java/ltdjms/discord/currency/integration/BalanceServiceIntegrationTest.java`
  - `src/test/java/ltdjms/discord/currency/performance/SlashCommandPerformanceTest.java`
  - GitHub issue `#75`
