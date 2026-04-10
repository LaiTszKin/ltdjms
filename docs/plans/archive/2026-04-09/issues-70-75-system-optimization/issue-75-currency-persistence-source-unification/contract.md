# Contract: Issue 75 貨幣持久層單一路徑化

- Date: 2026-04-09
- Feature: Issue 75 貨幣持久層單一路徑化
- Change Name: issue-75-currency-persistence-source-unification

## Purpose
本變更受 jOOQ `DSLContext` 與 fluent query path 約束：既然 production DI 已選擇 JOOQ 作為 account / config repositories 的實作基礎，測試若改用另一套 JDBC implementation，就等於驗證了不同的 SQL 組裝與資料映射路徑。

## Usage Rule
- 本 spec 的目標不是抽象化 repository，而是讓主要驗證路徑與 production path 一致。
- 若未來仍保留 JDBC account / config，必須把它們明確降為非 canonical path。

## Dependency Records

### Dependency 1: jOOQ DSLContext-based Persistence
- Type: `library`
- Version / Scope: `Not fixed`
- Official Source: `https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/`
- Why It Matters: production `CurrencyRepositoryModule` 以 `DSLContext` 建立 JOOQ repositories，因此回歸測試若不走同一路徑，就無法真正驗證生產實作。
- Invocation Surface:
  - Entry points: `DSLContext`, JOOQ-based repository methods
  - Call pattern: `in-process library call`
  - Required inputs: `DataSource` / `DSLContext`, table / field mappings, repository contract inputs
  - Expected outputs: 與 production 相同的 SQL 組裝、資料映射與 exception semantics
- Constraints:
  - Supported behavior: JOOQ repository 行為取決於 DSL 查詢與映射實作
  - Limits: 若測試改走 JDBC 平行實作，便無法代表 production SQL path
  - Compatibility: repository contract 對 service 層保持不變
  - Security / access: 不適用；本 spec 主要是 correctness / regression coverage
- Failure Contract:
  - Error modes: 映射錯誤、SQL 行為漂移、只在 production path 才出現的回歸
  - Caller obligations: 讓主要 integration / performance tests 對齊 production repository owner
  - Forbidden assumptions: 不可假設 JDBC 與 JOOQ 兩條路徑會自然保持一致
- Verification Plan:
  - Spec mapping: `R1.x-R3.x`
  - Design mapping: `Current Architecture`, `Proposed Architecture`
  - Planned coverage: `IT-75-01`, `IT-75-02`, `PT-75-01`
  - Evidence notes: 官方 jOOQ manual 將 `DSLContext` 視為查詢構建的中心，production path 與測試 path 若不同，就不是同一實作真相；本次已讓 `PostgresIntegrationTestBase` 提供共用 `DSLContext`，並把主 currency integration/performance suites 對齊到同一路徑
