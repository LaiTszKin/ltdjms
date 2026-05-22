# Checklist: ECPay Payment E2E Tests

- Date: 2026-05-22
- Feature: ECPay Payment E2E Tests

## Usage Notes

- `RUN_ECPAY_E2E=true` 環境變數控制是否執行真實 API 測試。無此變數時全部 skip。
- ECPay Stage API 可能有維護時段，測試失敗時需先排除外部因素。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded — Question: SimulatePaid=1 flag 檢查 → 決定不實作（保持與 Java 版一致）
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [ ] CL-01: CVS 取號成功回傳 paymentNo → R1.1–R1.4 → ecpay-cvs-e2e.test.ts — Result: `NOT RUN`
- [ ] CL-02: SimulatePaid=1 回撥 → DB 訂單狀態 PAID → R2.1–R2.5 → ecpay-callback-e2e.test.ts — Result: `NOT RUN`
- [ ] CL-03: CheckMacValue 驗證失敗回傳 400 → R3.1–R3.3 → ecpay-callback-e2e.test.ts — Result: `NOT RUN`
- [ ] CL-04: 對帳排程同步訂單狀態 → R4.1–R4.4 → ecpay-reconciliation-e2e.test.ts — Result: `NOT RUN`
- [ ] CL-05: AES 加解密 round-trip 與 golden data 一致 → R5.1–R5.3 → ecpay-crypto-e2e.test.ts — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior — CheckMacValue 簽章錯誤、AES padding 錯誤
- [ ] Property-based coverage for business logic — N/A（此為 E2E，非 PBT）
- [ ] External services mocked/faked — ECPay Stage API 為真實服務（非 mock）
- [ ] Adversarial cases — 竄改 CheckMacValue、缺少必要欄位、不存在訂單回撥
- [ ] Authorization, idempotency, concurrency risks — 回撥冪等性為核心不變量
- [ ] Assertions verify outcomes/side-effects — 驗證 HTTP status + DB state（訂單狀態、paidAt）
- [ ] Fixtures reproducible (fixed seed/clock) — 使用固定 merchant trade no 確保可重現

## E2E / Integration Decisions

- [ ] CVS 取號：E2E — Reason: 需真實 ECPay Stage API 驗證加解密與簽章
- [ ] 回撥處理：E2E (local callback server + 模擬 payload) — Reason: 驗證完整回撥處理管線
- [ ] 對帳排程：E2E — Reason: 需真實 TradeQuery API 驗證
- [ ] 加解密：E2E + crosscheck golden data — Reason: 確保與 Java/ECPay 相容

## Execution Summary

- [ ] Unit: `N/A`
- [ ] Regression: `NOT RUN`
- [ ] Property-based: `N/A`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `NOT RUN`
- [ ] Mock scenarios: `N/A`
- [ ] Adversarial: `NOT RUN`

## Completion Records

- [ ] Task 1 (CVS E2E): pending
- [ ] Task 2 (Callback E2E): pending
- [ ] Task 3 (CheckMacValue failure): pending
- [ ] Task 4 (Reconciliation E2E): pending
- [ ] Task 5 (Crypto E2E): pending
