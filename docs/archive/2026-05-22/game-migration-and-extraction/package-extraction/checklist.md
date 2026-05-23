# Checklist: package-extraction

- Date: 2026-05-22
- Feature: package-extraction

## Usage Notes

- 本次變更為純程式碼組織重構（移動檔案、更新 import），不變更業務邏輯
- 無需 property-based testing
- 測試重點：確保所有現有測試繼續通過 + make build 成功

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A — 範圍已明確)
- [ ] Affected plans updated after clarification (N/A)
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [x] CL-01: `make build` 編譯通過 — R1-R10 → T10.3 — Result: `PASS`
- [x] CL-02: `make test` 全部通過 — R1-R10 → T10.4 — Result: `PASS`
- [x] CL-03: `make verify` 完整驗證通過 — R1-R10 → T10.5 — Result: `PASS`
- [x] CL-04: economy package 不再導出遊戲相關類型 — R7 → T8.1 — Result: `PASS`
- [x] CL-05: games package 完整導出所有公共 API — R8 → T7.1 — Result: `PASS`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior (N/A — 不變更業務邏輯)
- [ ] Unit drift checks for non-trivial tasks (N/A — 現有測試覆蓋)
- [ ] Property-based coverage for business logic (N/A — 不變更業務邏輯)
- [ ] External services mocked/faked (N/A)
- [ ] Adversarial cases for abuse paths (N/A)
- [ ] Authorization, idempotency, concurrency risks evaluated (N/A)
- [ ] Assertions verify outcomes/side-effects (N/A — 由現有測試驗證)
- [ ] Fixtures reproducible (N/A)

## E2E / Integration Decisions

- [ ] DI 初始化順序: 現有測試覆蓋 — Reason: shared → economy → games → admin 順序由 main.ts 確保
- [ ] 跨 package import: N/A — Reason: TypeScript project references 在編譯期驗證

## Execution Summary

- [x] Unit: `PASS`
- [x] Regression: `PASS`
- [ ] Property-based: `N/A`
- [x] Integration: `PASS`
- [ ] E2E: `N/A`
- [ ] Mock scenarios: `N/A`
- [ ] Adversarial: `N/A`

## Completion Records

- [x] package-extraction: completed — Remaining: None
