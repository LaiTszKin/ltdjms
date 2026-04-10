# Checklist: Issue 74 設定 Schema 正規化

- Date: 2026-04-09
- Feature: Issue 74 設定 Schema 正規化

## Clarification & Approval Gate
- [x] User clarification responses are recorded — `N/A`（目前需求已足夠明確）
- [x] Affected plans are reviewed/updated — `N/A`（本次未發生 clarification，僅回填實作結果）
- [x] Explicit user approval on updated specs is obtained（使用者已指示實作此 approved spec）

## Behavior-to-Test Checklist

- [x] CL-74-01 canonical config path 與 packaged defaults 使用同一套 key namespace
  - Requirement mapping: `R1.1-R1.3`
  - Actual test case IDs: `UT-74-01`
  - Test level: `Unit`
  - Risk class: `regression`
  - Property/matrix focus: `invariant`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `exact output`
  - Test result: `PASS`
  - Notes (optional): `shouldKeepApplicationPropertiesAsOnlyLivePackagedDefaultsSchema`

- [x] CL-74-02 `.env`、packaged defaults、built-in defaults 的優先序與文件聲明一致
  - Requirement mapping: `R2.1-R2.3`, `R3.2`
  - Actual test case IDs: `UT-74-02`, `UT-74-03`
  - Test level: `Unit`
  - Risk class: `data integrity`
  - Property/matrix focus: `external state matrix`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `exact output`
  - Test result: `PASS`
  - Notes (optional): `EnvironmentConfigDotEnvIntegrationTest` 既有優先序案例持續通過，並保留 malformed / empty `.env` 容錯案例

- [x] CL-74-03 文件描述與實際 canonical schema 不再矛盾
  - Requirement mapping: `R2.1-R2.3`, `R3.3`
  - Actual test case IDs: `DOC-74-01`
  - Test level: `Regression`
  - Risk class: `regression`
  - Property/matrix focus: `invariant`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `exact output`
  - Test result: `PASS`
  - Notes (optional): `shouldDocumentTheSameCanonicalSchemaAndFallbackChain`

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason — `N/A`（本變更為有限 config key/schema 對齊，非適合 property-based 的業務狀態空間）
- [x] External services in the business logic chain are mocked/faked for scenario testing, or `N/A` is recorded with a concrete reason — `N/A`（設定載入流程不涉及外部服務）
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations, or `N/A` is recorded with a concrete reason — 已涵蓋 malformed / empty / comment-only `.env`
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons — `N/A`（純本地 deterministic config loading，無授權／狀態轉移／並發副作用）
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw"
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures) or `N/A` is recorded with a concrete reason

## E2E / Integration Decision Records

### Decision Record 1: fallback chain correctness
- Requirement mapping: `R1.x-R3.2 / CL-74-01~02`
- Decision: `Existing coverage already sufficient`
- Linked case IDs: `EnvironmentConfigDotEnvIntegrationTest` 擴充後案例
- Reason: 風險集中於 config loading 與 getter fallback，單元／近整合測試即可精確驗證

### Decision Record 2: 文件同步
- Requirement mapping: `R2.1-R2.3 / CL-74-03`
- Decision: `N/A`
- Linked case IDs: `DOC-74-01`
- Reason: 文件同步不是 E2E 問題，但必須留下可回歸的審核檢查點

## Execution Summary
- [x] Unit tests: `PASS`（`EnvironmentConfigTest`, `EnvironmentConfigDotEnvIntegrationTest`）
- [x] Regression tests: `PASS`（canonical schema/documentation drift assertions）
- [ ] Property-based tests: `N/A`
- [x] Integration tests: `PASS`（`.env`/packaged defaults fallback 近整合測試）
- [ ] E2E tests: `N/A`
- [ ] External service mock scenarios: `N/A`
- [x] Adversarial/penetration-style cases: `PASS`（malformed / empty / comment-only `.env`）

## Completion Records

### Completion Record 1: 規格與設計
- Requirement mapping: `R1.x-R3.x / Task 1-3 / CL-74-01~03`
- Completion status: `completed`
- Remaining applicable items: `None`
- Notes: 已將 `application.properties` 收斂為唯一 live packaged defaults，並將 `application.conf` 降為不承載設定鍵的 compatibility shim

### Completion Record 2: 實作與測試
- Requirement mapping: `Task 1-3`
- Completion status: `completed`
- Remaining applicable items: `None`
- Notes: 已完成文件對齊與 drift regression tests，並執行 `mvn -q -Dtest=EnvironmentConfigTest,EnvironmentConfigDotEnvIntegrationTest test`
