# Checklist: 綠界付款回推履約與通知時機調整

- Date: 2026-03-04
- Feature: 綠界付款回推履約與通知時機調整

## Clarification & Approval Gate
- [x] User clarification responses are recorded: `N/A`（本次需求敘述已具備明確 BDD）。
- [x] Affected specs are reviewed/updated: `N/A`（無額外澄清回覆導致二次調整）。
- [x] Explicit user approval on updated specs is obtained（date/conversation reference: 2026-03-04，使用者回覆 `accept`）。

## Behavior-to-Test Checklist

- [x] CL-01 法幣下單後建立待付款訂單，並僅私訊買家超商代碼
  - Requirement mapping: `R1.1`, `R1.2`
  - Actual test case IDs: `UT-01`, `UT-02`
  - Test level: `Unit`, `Integration`
  - Test result: `PASS`
  - Notes (optional): 對應 `FiatOrderServiceTest`（含落庫驗證）；整合層另列為未執行。

- [x] CL-02 綠界回推已付款（或等價狀態）時只觸發一次履約
  - Requirement mapping: `R2.1`, `R2.2`
  - Actual test case IDs: `UT-03`, `UT-04`
  - Test level: `Unit`, `Integration`
  - Test result: `PASS`
  - Notes (optional): 對應 `FiatPaymentCallbackServiceTest`（未付款、已付款、重複回推）。

- [x] CL-03 護航型商品管理員通知改為付款完成後發送
  - Requirement mapping: `R3.1`, `R3.2`
  - Actual test case IDs: `UT-05`
  - Test level: `Unit`, `Integration`
  - Test result: `PASS`
  - Notes (optional): `ShopSelectMenuHandler` 已移除法幣建單通知；付款後通知由 `FiatPaymentCallbackService` 驗證觸發。

## E2E Decision Record
- [x] Do not build E2E; cover with integration tests instead（alternative case: `IT-01~IT-03`；reason: 綠界外部推送依賴公開網路與環境，不適合作為穩定 CI E2E）。

## Execution Summary
- [x] Unit tests: `PASS`
- [x] Property-based tests: `N/A`（此變更屬事件/狀態流程，無明確可泛化數學不變式）
- [ ] Integration tests: `NOT RUN`（回推鏈路需外部 HTTP callback 環境，這次未執行）
- [ ] E2E tests: `NOT RUN`（`EcpayFiatPaymentE2EIntegrationTest` 需 `RUN_ECPAY_E2E=true`）

## Completion Rule
- [x] Agent has updated checkboxes, test outcomes, and necessary notes based on real execution (including added/removed items).
