# Checklist: Economy Business Invariant PBT

- Date: 2026-05-22
- Feature: Economy Business Invariant PBT

## Usage Notes

- 依賴 test-infra spec 產出的 `createTestContainer`、`seedGuild`、`seedUserAccount` 等。
- 使用 `fc.assert` 預設 100 runs，CI 可設 `numRuns: 1000`。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A).
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [ ] CL-01: 任意合法轉帳後 sum(balances) 守恆 → R1.1–R1.4 → balance-transfer.pbt.test.ts — Result: `NOT RUN`
- [ ] CL-02: 透支轉帳回傳 DomainError → R2.1–R2.3 → balance-transfer.pbt.test.ts — Result: `NOT RUN`
- [ ] CL-03: 自我轉帳回傳 DomainError → R3.1–R3.2 → balance-transfer.pbt.test.ts — Result: `NOT RUN`
- [ ] CL-04: DiceGame1 payout = bet × multiplier → R4.1–R4.3 → dice-game-1.pbt.test.ts — Result: `NOT RUN`
- [ ] CL-05: DiceGame2 組合 payout = bet × multiplier → R5.1–R5.3 → dice-game-2.pbt.test.ts — Result: `NOT RUN`
- [ ] CL-06: GameToken 操作數量正確、不可為負 → R6.1–R6.4 → game-token.pbt.test.ts — Result: `NOT RUN`
- [ ] CL-07: CurrencyConfig 查詢回傳正確值 → R7.1–R7.2 → currency-config.pbt.test.ts — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior — 轉帳超扣、Dice 賠率計算精度
- [ ] Unit drift checks for non-trivial tasks — N/A（PBT 本身就是 drift check）
- [ ] Property-based coverage for business logic — 五個 PBT 檔案覆蓋全部 economy 業務邏輯
- [ ] External services mocked/faked — N/A（無外部 API；Redis 使用 NoOpCache）
- [ ] Adversarial cases for abuse paths — 金額為 0、負數、MAX_SAFE_INTEGER；token 超扣；自我轉帳
- [ ] Authorization, idempotency, concurrency risks evaluated — 轉帳不冪等（每次呼叫產生新 transaction）
- [ ] Assertions verify outcomes/side-effects, not just "returns 200" — 所有 assert 驗證 DB state（餘額、交易記錄）
- [ ] Fixtures reproducible (fixed seed/clock) — 使用 `fc` 固定 seed 重現失敗案例

## E2E / Integration Decisions

- [ ] Economy 全功能：Integration PBT — Reason: 需真實 PostgreSQL 驗證餘額一致性、transaction 完整性

## Execution Summary

- [ ] Unit: `N/A`（此 spec 為 Integration PBT，不包含單元測試）
- [ ] Regression: `NOT RUN`
- [ ] Property-based: `NOT RUN`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`
- [ ] Mock scenarios: `N/A`
- [ ] Adversarial: `NOT RUN`

## Completion Records

- [ ] Task 1 (Balance Transfer PBT): pending — Remaining: 全部
- [ ] Task 2 (DiceGame1 PBT): pending — Remaining: 全部
- [ ] Task 3 (DiceGame2 PBT): pending — Remaining: 全部
- [ ] Task 4 (Game Token PBT): pending — Remaining: 全部
- [ ] Task 5 (Currency Config PBT): pending — Remaining: 全部
