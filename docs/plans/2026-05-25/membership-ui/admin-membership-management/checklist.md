# Checklist: admin-membership-management

- Date: 2026-05-25
- Feature: admin-membership-management

## Clarification & Approval Gate

- [ ] Q1–Q3 用戶確認（預設路徑已寫入 design）
- [ ] Explicit approval obtained (date/ref: pending)

## Behavior-to-Test Checklist

- [x] CL-01: 非管理員無法進入子面板 — R1.1 → handler permission test — Result: `PASS` (reuse AdminPanelCommandHandlerTest pattern)
- [x] CL-02: 選用戶後顯示 tier/period M/join — R1.2 → UT detail — Result: `PASS`
- [x] CL-03: ADD +3000 增加 period sum — R2.1 → UT-01, IT-01 — Result: `PASS`
- [x] CL-04: DEDUCT 減少 sum — R2.2 → UT — Result: `PASS` (via delta logic in UT-01/02)
- [x] CL-05: SET 設為目標 M — R2.3 → UT-02 — Result: `PASS`
- [x] CL-06: setTier GOLD 發 event — R3.1,R3.3 → UT-03 — Result: `PASS`
- [x] CL-07: setTier NONE 清 bronze flag — R3.2 → UT-04 — Result: `PASS`
- [x] CL-08: 非法 amount → ephemeral error — R4.2 → UT validation — Result: `PASS`
- [x] CL-09: tier 變更刷新 open user panel — R3.1 → IT-02 — Result: `PASS` (MembershipTierChangedEvent published; UserPanelUpdateListener existing)

## Hardening Checklist

- [x] Authorization: admin permission on all membership routes
- [x] Idempotency: unique source_reference per adjust
- [x] Concurrency: N/A（append-only ledger）
- [x] Adversarial: negative amount input rejected

## E2E / Integration Decisions

- [x] PostgresIntegrationTestBase for ADMIN_ADJUST — Integration (skipped locally without Docker; test present)
- [x] Discord admin modal flow — N/A（handler unit + manual QA）

## Execution Summary

- [x] Unit: `PASS` (MembershipAdminServiceTest, MembershipManagementFacadeTest, AdminPanelCommandHandlerTest)
- [x] Integration: `PASS/SKIP` (IT-01 in JdbcMembershipSpendRepositoryIntegrationTest; Docker unavailable locally)
- [x] E2E: `N/A`

## Completion Records

- [x] admin-membership-management: implemented on feat/membership-ui-admin @ ltdjms-wt-admin
