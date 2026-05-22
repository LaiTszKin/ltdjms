# Checklist: Admin Panel PBT

- Date: 2026-05-22
- Feature: Admin Panel PBT

## Usage Notes

- 直接測試 admin facade 層（而非 Discord interaction handler），跳過 Discord UI 層。
- 每個 PBT 獨立 seed 資料，確保隔離。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A).
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [x] CL-01: CurrencyConfig 變更後立即生效且不影響餘額 → R1.1–R1.4 → currency-config.pbt.test.ts — Result: `PASS`
- [x] CL-02: 商品下架後不可購買、上架後恢復 → R2.1–R2.3 → product-management.pbt.test.ts — Result: `PASS`
- [x] CL-03: 價格更新後新訂單用新價格、舊訂單保持原價 → R3.1–R3.3 → product-management.pbt.test.ts — Result: `PASS`
- [x] CL-04: 兌換碼批量生成唯一、格式正確 → R4.1–R4.4 → redemption-code-gen.pbt.test.ts — Result: `PASS`
- [x] CL-05: Dice 參數變更後新遊戲生效、既有記錄不變 → R5.1–R5.4 → game-config.pbt.test.ts — Result: `PASS`
- [x] CL-06: 遊戲代幣調整數量正確、不可為負 → R6.1–R6.3 → game-token-management.pbt.test.ts — Result: `PASS`
- [x] CL-07: 護航選項 CRUD 操作正確 → R7.1–R7.3 → dispatch-config.pbt.test.ts — Result: `PASS`
- [x] CL-08: AI 頻道白名單新增／移除 → R8.1–R8.3 → ai-channel-config.pbt.test.ts — Result: `PASS`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior — 設定變更未觸發 DomainEvent、商品下架仍可購買
- [ ] Property-based coverage for business logic — 七個 PBT 檔案覆蓋全部 admin 功能
- [ ] External services mocked/faked — Discord interaction 層由 facade 測試跳過
- [ ] Adversarial cases — 非管理員操作、無效參數（負價格、空名稱）、並行設定衝突
- [ ] Authorization, idempotency, concurrency risks — 管理操作冪等性（重複設定同一值）
- [ ] Assertions verify outcomes/side-effects — 驗證 DB state（config 值、商品狀態、order 價格）
- [ ] Fixtures reproducible (fixed seed/clock) — fixed seed

## E2E / Integration Decisions

- [ ] Admin 全功能：Integration PBT — Reason: 需真實 PostgreSQL 驗證設定變更的 DB 一致性

## Execution Summary

- [x] Unit: `N/A`
- [x] Regression: `PASS`
- [x] Property-based: `PASS`
- [x] Integration: `PASS`
- [x] E2E: `N/A`

## Completion Records

- [x] Task 1 (Currency Config PBT): completed
- [x] Task 2 (Product Management PBT): completed
- [x] Task 3 (Redeem Code Gen PBT): completed
- [x] Task 4 (Game Config PBT): completed
- [x] Task 5 (Game Token Management PBT): completed
- [x] Task 6 (Dispatch Config PBT): completed
- [x] Task 7 (AI Channel Config PBT): completed
