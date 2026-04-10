# Checklist: Issue 70 履約 URL DNS Rebinding 強化

- Date: 2026-04-09
- Feature: Issue 70 履約 URL DNS Rebinding 強化

## Clarification & Approval Gate
- [ ] User clarification responses are recorded — `N/A`（目前需求已足夠明確）
- [x] Affected plans are reviewed/updated — 已依實作與測試結果回填
- [x] Explicit user approval on updated specs is obtained（使用者已明確要求執行此 spec）

## Behavior-to-Test Checklist

- [x] CL-01 已驗證的 IP snapshot 會被 transport 直接使用，且不再以 hostname 決定 socket 目的地
  - Requirement mapping: `R1.1-R1.2`
  - Actual test case IDs: `UT-70-01`
  - Test level: `Unit`
  - Risk class: `regression`
  - Property/matrix focus: `invariant`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `exact output`
  - Test result: `PASS`
  - Notes (optional): 以 fake resolver + mocked transport 驗證 `ResolvedTarget.resolvedAddress`、`socketAddress()` 與 `tlsServerName()`

- [x] CL-02 localhost / RFC1918 / special-use / unknown host 會在送出前被拒絕
  - Requirement mapping: `R2.1`
  - Actual test case IDs: `UT-70-02`
  - Test level: `Unit`
  - Risk class: `adversarial abuse`
  - Property/matrix focus: `external state matrix`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `permission denial`
  - Test result: `PASS`
  - Notes (optional): 包含 IPv4 special-use、IPv6 ULA、mixed public/private address 與 unknown host

- [x] CL-03 non-2xx 與 transport failure 不會留下 fulfillment 成功副作用
  - Requirement mapping: `R2.2-R2.3`
  - Actual test case IDs: `UT-70-03`
  - Test level: `Unit`
  - Risk class: `external failure`
  - Property/matrix focus: `adversarial case`
  - External dependency strategy: `mocked service states`
  - Oracle/assertion focus: `no partial write`
  - Test result: `PASS`
  - Notes (optional): 驗證 non-2xx 與 TLS/IO 失敗時回傳 `DomainError`

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason — `N/A`（本次僅固化 transport snapshot 與失敗映射，沒有可抽象的商業邏輯輸入空間）
- [x] External services in the business logic chain are mocked/faked for scenario testing
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons — `N/A`（本 spec 不改動授權、狀態轉移、冪等或併發邊界）
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw"
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures)

## E2E / Integration Decision Records

### Decision Record 1: DNS rebinding / target snapshot
- Requirement mapping: `R1.1-R1.2 / CL-01`
- Decision: `Cover with unit regression tests`
- Linked case IDs: `UT-70-01`
- Reason: 核心風險在 internal snapshot / transport 邊界；以 fake resolver + mocked transport 可直接驗證不變式，較 integration 更穩定

### Decision Record 2: non-public target rejection
- Requirement mapping: `R2.1 / CL-02`
- Decision: `Existing coverage already sufficient`
- Linked case IDs: `ProductFulfillmentApiServiceTest` 擴充後案例
- Reason: 風險集中於 validation function，單元測試比 E2E 更穩定、更能覆蓋特殊位址矩陣

## Execution Summary
- [x] Unit tests: `PASS`（`mvn -Dtest=ProductFulfillmentApiServiceTest test`）
- [x] Regression tests: `PASS`（target snapshot / mixed-address / unknown host）
- [x] Property-based tests: `N/A`（本次不是商業邏輯狀態空間變更）
- [x] Integration tests: `N/A`（internal boundary 以單元測試即可直接覆蓋）
- [x] E2E tests: `N/A`（不涉及使用者可見流程變更）
- [x] External service mock scenarios: `PASS`
- [x] Adversarial/penetration-style cases: `PASS`

## Completion Records

### Completion Record 1: 規格與設計
- Requirement mapping: `R1.x-R2.x / Task 1-3 / CL-01~03`
- Completion status: `completed`
- Remaining applicable items: `Implementation and verification`
- Notes: 已確認 issue #70 的核心風險在現行程式中已被部分緩解，因此本 spec 以「固化與回歸保護」為主

### Completion Record 2: 實作與測試
- Requirement mapping: `Task 1-3`
- Completion status: `completed`
- Remaining applicable items: `None`
- Notes: 已完成 target snapshot 收斂、回歸／對抗測試與規格回填
