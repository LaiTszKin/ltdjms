# Checklist: Issue 75 貨幣持久層單一路徑化

- Date: 2026-04-09
- Feature: Issue 75 貨幣持久層單一路徑化

## Clarification & Approval Gate
- [ ] User clarification responses are recorded — `N/A`（目前需求已足夠明確）
- [x] Affected plans are reviewed/updated — 已依實作與驗證結果回填
- [x] Explicit user approval on updated specs is obtained（使用者已明確要求執行此 spec）

## Behavior-to-Test Checklist

- [x] CL-75-01 主要 integration / performance suites 改走 JOOQ account + config path
  - Requirement mapping: `R1.1-R1.2`
  - Actual test case IDs: `IT-75-01`, `PT-75-01`
  - Test level: `Integration / Performance`
  - Risk class: `regression`
  - Property/matrix focus: `external state matrix`
  - External dependency strategy: `near-real dependency`
  - Oracle/assertion focus: `persisted state`
  - Test result: `SKIPPED (Testcontainers disabled without Docker)`
  - Notes (optional): 已改為共用 `PostgresIntegrationTestBase.dslContext` 建構 JOOQ account/config repository

- [x] CL-75-02 `JooqGuildCurrencyConfigRepository` 具有與 account repository 對等的回歸覆蓋
  - Requirement mapping: `R2.1-R2.3`
  - Actual test case IDs: `IT-75-02`
  - Test level: `Integration`
  - Risk class: `data integrity`
  - Property/matrix focus: `external state matrix`
  - External dependency strategy: `near-real dependency`
  - Oracle/assertion focus: `persisted state`
  - Test result: `SKIPPED (Testcontainers disabled without Docker)`
  - Notes (optional): `JooqRepositoryIntegrationTest` 已補齊 save / find / update / saveOrUpdate / delete

- [x] CL-75-03 JDBC account / config 不再作為主 regression 預設路徑
  - Requirement mapping: `R3.1-R3.3`
  - Actual test case IDs: `RG-75-01`
  - Test level: `Regression`
  - Risk class: `regression`
  - Property/matrix focus: `invariant`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `exact output`
  - Test result: `PASS`
  - Notes (optional): `rg -n "JdbcMemberCurrencyAccountRepository|JdbcGuildCurrencyConfigRepository" src/main/java src/test/java` 無結果，且 main-source JDBC 實作已刪除

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason
- [x] External services in the business logic chain are mocked/faked for scenario testing, or `N/A` is recorded with a concrete reason
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations, or `N/A` is recorded with a concrete reason
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw"
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures)

## E2E / Integration Decision Records

### Decision Record 1: repository owner alignment
- Requirement mapping: `R1.x-R2.x / CL-75-01~02`
- Decision: `Cover with integration instead`
- Linked case IDs: `IT-75-01`, `IT-75-02`
- Reason: 風險在 repository 實作與資料映射，必須直接對真實 Postgres + JOOQ path 驗證

### Decision Record 2: transitional JDBC cleanup
- Requirement mapping: `R3.x / CL-75-03`
- Decision: `N/A`
- Linked case IDs: `RG-75-01`
- Reason: 這是結構與命名責任問題，不是使用者 E2E 流程；需留下回歸檢查點而非 UI 測試

## Execution Summary
- [x] Unit tests: `N/A`（本次未新增 unit-only 邏輯，重點在 integration/performance path 對齊）
- [x] Regression tests: `PASS`（以路徑搜尋與編譯驗證確認 JDBC account/config 已退出主路徑）
- [x] Property-based tests: `N/A`（未新增需驗證新不變式的業務邏輯，只是對齊既有 persistence owner）
- [x] Integration tests: `SKIPPED (Testcontainers disabled without Docker)`（`mvn -Dtest='RepositoryIntegrationTest,JooqRepositoryIntegrationTest,BalanceServiceIntegrationTest,BalanceAdjustmentCommandIntegrationTest,CurrencyConfigCommandIntegrationTest,BotRestartIntegrationTest,SlashCommandPerformanceTest' test`）
- [x] E2E tests: `N/A`（repository owner 對齊屬 persistence/regression 風險）
- [x] External service mock scenarios: `N/A`（無外部服務）
- [x] Adversarial/penetration-style cases: `N/A`（本次不涉及新輸入面或權限邊界）

## Completion Records

### Completion Record 1: 規格與設計
- Requirement mapping: `R1.x-R3.x / Task 1-3 / CL-75-01~03`
- Completion status: `completed`
- Remaining applicable items: `None`
- Notes: 已將 currency integration/performance 測試切到 JOOQ account/config path，並移除 main-source JDBC account/config repositories

### Completion Record 2: 實作與測試
- Requirement mapping: `Task 1-3`
- Completion status: `completed with environment-limited verification`
- Remaining applicable items: `Docker-enabled rerun of Testcontainers suites`
- Notes: Maven 測試命令與編譯已成功；但本機缺少 Docker，Testcontainers 整合/效能案例被自動略過
