# Checklist: Issues 71-72 綠界 Callback 驗證強化

- Date: 2026-04-09
- Feature: Issues 71-72 綠界 Callback 驗證強化

## Clarification & Approval Gate
- [x] User clarification responses are recorded — `N/A`（目前需求已足夠明確）
- [x] Affected plans are reviewed/updated — 本次依實作與驗證結果回填
- [x] Explicit user approval on updated specs is obtained（使用者已明確要求執行此 spec set）

## Behavior-to-Test Checklist

- [x] CL-71-01 建立綠界 ReturnURL 時不再附加 query token
  - Requirement mapping: `R1.1-R1.2`
  - Actual test case IDs: `UT-71-01`
  - Test level: `Unit`
  - Risk class: `regression`
  - Property/matrix focus: `exact output`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `exact output`
  - Test result: `PASS`
  - Notes (optional): `EcpayCvsPaymentServiceTest#shouldKeepReturnUrlUnchanged`

- [x] CL-71-02 `ECPAY_STAGE_MODE=true` + public bind 會在啟動階段 fail closed
  - Requirement mapping: `R2.1-R2.3`
  - Actual test case IDs: `UT-71-02`, `IT-71-01`, `IT-71-02`
  - Test level: `Unit / Integration`
  - Risk class: `authorization`
  - Property/matrix focus: `external state matrix`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `permission denial`
  - Test result: `PASS`
  - Notes (optional): `EcpayCallbackHttpServerTest#shouldRejectPublicBindWhenStageModeEnabled`、`EcpayCallbackHttpServerTest#shouldAllowPublicBindWhenStageModeDisabled`、`EcpayCallbackHttpServerTest#shouldAcceptCallbackWithoutQueryToken`

- [x] CL-71-03 legit production callback、duplicate callback 與 validation failure 不回歸
  - Requirement mapping: `R3.1-R3.3`
  - Actual test case IDs: `UT-71-03`, `UT-71-04`
  - Test level: `Unit`
  - Risk class: `data integrity`
  - Property/matrix focus: `external state matrix`
  - External dependency strategy: `mocked service states`
  - Oracle/assertion focus: `persisted state`
  - Test result: `PASS`
  - Notes (optional): `FiatPaymentCallbackServiceTest` 已覆蓋 mark-paid、duplicate callback、unpaid callback、merchant mismatch、amount mismatch、decrypt failure、missing order number

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason
- [x] External services in the business logic chain are mocked/faked for scenario testing
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw"
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures)

## E2E / Integration Decision Records

### Decision Record 1: callback exposure policy
- Requirement mapping: `R2.x / CL-71-02`
- Decision: `Cover with integration instead`
- Linked case IDs: `IT-71-01`
- Reason: 風險在 server 啟動條件與 HTTP endpoint 暴露，不需要完整外部 E2E，但需要一個近真的 server 啟動案例

### Decision Record 2: paid callback business path
- Requirement mapping: `R3.x / CL-71-03`
- Decision: `Existing coverage already sufficient`
- Linked case IDs: `FiatPaymentCallbackServiceTest` 擴充後案例
- Reason: 業務風險集中於 service 分支與 repository 協作，單元／近整合測試比真實 ECPay E2E 更穩定

## Execution Summary
- [x] Unit tests: `PASS` — `mvn -q -Dtest=EcpayCvsPaymentServiceTest,EcpayCallbackHttpServerTest,FiatPaymentCallbackServiceTest test`
- [x] Regression tests: `PASS`
- [x] Property-based tests: `N/A`（本次是 trust boundary 與 admission policy 回歸強化，無適合的生成式商業不變量可驗證）
- [x] Integration tests: `PASS`（`EcpayCallbackHttpServerTest` 以實際內嵌 HTTP server 啟停驗證）
- [x] E2E tests: `N/A`（已以 service/unit + 近整合 server coverage 覆蓋主要風險）
- [x] External service mock scenarios: `PASS`
- [x] Adversarial/penetration-style cases: `PASS`

## Completion Records

### Completion Record 1: 規格與設計
- Requirement mapping: `R1.x-R3.x / Task 1-3 / CL-71-01~03`
- Completion status: `completed`
- Remaining applicable items: `None`
- Notes: 本 spec 已依現況實作與驗證結果完成回填，測試證據已對齊實際交付

### Completion Record 2: 實作與測試
- Requirement mapping: `Task 1-3`
- Completion status: `completed`
- Remaining applicable items: `None`
- Notes: callback URL secret 移除、stage/public fail-closed 與 paid callback 回歸保護皆已在目前基線完成並驗證
