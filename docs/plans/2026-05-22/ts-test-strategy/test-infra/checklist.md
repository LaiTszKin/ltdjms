# Checklist: Integration PBT Test Infrastructure

- Date: 2026-05-22
- Feature: Integration PBT Test Infrastructure

## Usage Notes

- Testcontainers 需要 Docker daemon，CI 環境需確認 Docker 可用。
- `RUN_INTEGRATION_TESTS` 環境變數控制是否啟用整合測試（無 Docker 時 skip）。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A — requirements are clear).
- [ ] Affected plans updated after clarification (N/A — no clarifications needed).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [ ] CL-01: Testcontainer 啟動並執行 migration → R1.1, R1.2, R1.3 → infra-smoke.test.ts — Result: `NOT RUN`
- [ ] CL-02: Template DB reset 100 次無洩漏 → R2.1, R2.2, R2.3 → infra-smoke.test.ts — Result: `NOT RUN`
- [ ] CL-03: Seed factory 建立 guild + user → R3.1, R3.2 → infra-smoke.test.ts — Result: `NOT RUN`
- [ ] CL-04: Arbitrary 產生合法值 → R4.1-R4.6 → infra-smoke.test.ts — Result: `NOT RUN`
- [ ] CL-05: Test DI container resolve 所有 token → R5.1-R5.4 → infra-smoke.test.ts — Result: `NOT RUN`
- [ ] CL-06: Assertion helper 正確運作 → R6.1-R6.3 → infra-smoke.test.ts — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior — N/A（此 spec 為基礎設施，無業務邏輯）
- [ ] Unit drift checks for non-trivial tasks — N/A（基礎設施由 smoke test 驗證）
- [ ] Property-based coverage for business logic — N/A（此 spec 為基礎設施）
- [ ] External services mocked/faked — Testcontainers 提供 real PostgreSQL，不需 mock
- [ ] Adversarial cases for abuse paths — N/A（基礎設施層）
- [ ] Authorization, idempotency, concurrency risks evaluated — DB reset 使用 template 機制確保隔離
- [ ] Assertions verify outcomes/side-effects, not just "returns 200" — 驗證 DB state
- [ ] Fixtures reproducible (fixed seed/clock) — Seed factory 支援 fixed seed

## E2E / Integration Decisions

- [ ] Testcontainer PostgreSQL 生命週期：Integration (vitest globalSetup/globalTeardown) — Reason: 需要真實 PostgreSQL 驗證 schema/sql 正確性
- [ ] Template DB reset：Integration — Reason: 效能需求（100ms 內重設）需要真實 DB 驗證

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Regression: `N/A`
- [ ] Property-based: `N/A`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`
- [ ] Mock scenarios: `N/A`
- [ ] Adversarial: `N/A`

## Completion Records

- [ ] Task 1 (Dependencies & vitest config): pending — Remaining: 全部
- [ ] Task 2 (Template DB reset): pending — Remaining: 全部
- [ ] Task 3 (Seed data factory): pending — Remaining: 全部
- [ ] Task 4 (fast-check arbitrary): pending — Remaining: 全部
- [ ] Task 5 (Test DI container): pending — Remaining: 全部
- [ ] Task 6 (Assertion helper): pending — Remaining: 全部
- [ ] Task 7 (Integration smoke test): pending — Remaining: 全部
