# Checklist: user-panel-membership-info

- Date: 2026-05-25
- Feature: user-panel-membership-info

## Usage Notes

- 測試分布：Unit 為主（格式化 + 純計算）；Integration 驗證 query 與 DB。
- 跳過 E2E：無新 slash command；Discord 時間戳由字串 unit test 覆蓋。

## Clarification & Approval Gate

- [x] Clarification responses recorded — batch coordination 預設已採用
- [ ] Affected plans updated after clarification — N/A
- [ ] Explicit approval obtained (date/ref: pending user review)

## Behavior-to-Test Checklist

- [ ] CL-01: 有 join_at 時面板顯示 Discord 日期 — R1.1 → UT-02 — Result: `NOT RUN`
- [ ] CL-02: join_at 為 null 顯示「尚未記錄」 — R1.2 → UT-03 — Result: `NOT RUN`
- [ ] CL-03: remainingM = threshold - spent（下限 0） — R2.1 → UT-01 — Result: `NOT RUN`
- [ ] CL-04: NONE 用戶距白銀剩餘 M — R2.2 → UT-03 — Result: `NOT RUN`
- [ ] CL-05: BLACK 顯示已達最高等級 — R2.3 → UT-04 — Result: `NOT RUN`
- [ ] CL-06: GOLD 權益含護航 85 折 + 每月贈幣 200 — R3.1,R3.2 → UT-02 — Result: `NOT RUN`
- [ ] CL-07: tier 變更後 open panel 刷新含新欄位 — R1–R3 → UT-UpdateListener — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior — 更新既有 embed test
- [x] Unit drift checks for non-trivial tasks — computeRemaining 純函式
- [ ] Property-based coverage — N/A（無複雜 invariant，公式 trivial）
- [x] External services mocked/faked — N/A
- [ ] Adversarial cases — N/A（read-only UI）
- [ ] Authorization — N/A（self panel）
- [x] Fixtures reproducible — 固定 clock in query tests

## E2E / Integration Decisions

- [ ] MembershipQueryService + Postgres: Integration — Reason: 驗證 join_at 從 DB 流入 summary
- [ ] Discord /user-panel: N/A — Reason: unit 覆蓋 format 字串

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Regression: `NOT RUN`
- [ ] Property-based: `N/A`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`

## Completion Records

- [ ] user-panel-membership-info: pending implementation
