# Checklist: Shop Business Invariant PBT

- Date: 2026-05-22
- Feature: Shop Business Invariant PBT

## Usage Notes

- 依賴 test-infra 的 seed factory (`seedProduct`, `seedRedemptionCode`)。
- ECPay API 不在本 spec 範圍內（由 ecpay-e2e 負責）。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A).
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [x] CL-01: 兌換碼僅能被兌換一次、第二次回傳 DomainError → R1.1–R1.4 → redemption.pbt.test.ts — Result: `PASS`
- [x] CL-02: 批量兌換碼全部兌換後狀態為 used、reward 正確 → R2.1–R2.3 → redemption.pbt.test.ts — Result: `PASS`
- [x] CL-03: 購買後餘額正確扣減、餘額不足回傳錯誤 → R3.1–R3.4 → shop-purchase.pbt.test.ts — Result: `PASS`
- [x] CL-04: 法幣訂單 orderNumber 唯一、狀態初始化 PENDING_PAYMENT → R4.1–R4.5 → fiat-order-creation.pbt.test.ts — Result: `PASS`
- [x] CL-05: 貨幣購買 reward 計算正確 → R5.1–R5.2 → currency-purchase.pbt.test.ts — Result: `PASS`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior — 兌換碼重複兌換、庫存超賣
- [ ] Property-based coverage for business logic — 四個 PBT 檔案
- [ ] External services mocked/faked — ECPay API 在此 spec 範圍外（ecpay-e2e）
- [ ] Adversarial cases — 兌換不存在 code、購買已下架商品、價格為 0 商品
- [ ] Authorization, idempotency, concurrency risks — 兌換碼冪等更新為核心不變量
- [ ] Assertions verify outcomes/side-effects — 驗證 DB state（code 狀態、餘額、交易記錄）
- [ ] Fixtures reproducible (fixed seed/clock) — 使用 `fc` 固定 seed

## E2E / Integration Decisions

- [ ] Shop 全功能：Integration PBT — Reason: 需真實 PostgreSQL 驗證冪等更新、交易完整性

## Execution Summary

- [x] Unit: `N/A`
- [x] Regression: `PASS`
- [x] Property-based: `PASS`
- [x] Integration: `PASS`
- [x] E2E: `N/A`

## Completion Records

- [x] Task 1 (Redemption PBT): completed
- [x] Task 2 (Shop Purchase PBT): completed
- [x] Task 3 (Fiat Order Creation PBT): completed
- [x] Task 4 (Currency Purchase PBT): completed
