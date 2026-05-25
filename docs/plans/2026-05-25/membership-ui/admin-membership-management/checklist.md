# Checklist: admin-membership-management

- Date: 2026-05-25
- Feature: admin-membership-management

## Clarification & Approval Gate

- [ ] Q1–Q3 用戶確認（預設路徑已寫入 design）
- [ ] Explicit approval obtained (date/ref: pending)

## Behavior-to-Test Checklist

- [ ] CL-01: 非管理員無法進入子面板 — R1.1 → handler permission test — Result: `NOT RUN`
- [ ] CL-02: 選用戶後顯示 tier/period M/join — R1.2 → UT detail — Result: `NOT RUN`
- [ ] CL-03: ADD +3000 增加 period sum — R2.1 → UT-01, IT-01 — Result: `NOT RUN`
- [ ] CL-04: DEDUCT 減少 sum — R2.2 → UT — Result: `NOT RUN`
- [ ] CL-05: SET 設為目標 M — R2.3 → UT-02 — Result: `NOT RUN`
- [ ] CL-06: setTier GOLD 發 event — R3.1,R3.3 → UT-03 — Result: `NOT RUN`
- [ ] CL-07: setTier NONE 清 bronze flag — R3.2 → UT-04 — Result: `NOT RUN`
- [ ] CL-08: 非法 amount → ephemeral error — R4.2 → UT validation — Result: `NOT RUN`
- [ ] CL-09: tier 變更刷新 open user panel — R3.1 → IT-02 — Result: `NOT RUN`

## Hardening Checklist

- [ ] Authorization: admin permission on all membership routes
- [ ] Idempotency: unique source_reference per adjust
- [ ] Concurrency: N/A（append-only ledger）
- [ ] Adversarial: negative amount input rejected

## E2E / Integration Decisions

- [ ] PostgresIntegrationTestBase for ADMIN_ADJUST — Integration
- [ ] Discord admin modal flow — N/A（handler unit + manual QA）

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`

## Completion Records

- [ ] admin-membership-management: pending implementation
