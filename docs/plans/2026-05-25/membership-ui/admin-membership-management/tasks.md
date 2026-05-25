# Tasks: admin-membership-management

- Date: 2026-05-25
- Feature: admin-membership-management

## **Task 1: Spend ledger admin insert**

Purpose: 支援 ADMIN_ADJUST 寫入
Requirements: R2.x
Scope: `MembershipSpendRepository`, `JdbcMembershipSpendRepository` or coordinator
Out of scope: Admin UI

- T1.1 [ ] **`MembershipSpendRepository`** — 新增 `insertAdminAdjust(...)` 介面；常數 `SOURCE_ADMIN_ADJUST = "ADMIN_ADJUST"`
  - Verify: compile

- T1.2 [ ] **JDBC impl** — INSERT with unique source_reference；复用既有 ON CONFLICT DO NOTHING 或 throw on duplicate
  - Verify: IT-01 integration test

## **Task 2: MembershipAdminService**

Purpose: 業務規則與 tier 更新
Requirements: R2.x, R3.x
Scope: `membership/services/MembershipAdminService.java`, `MembershipModule.java`
Out of scope: Panel handlers

- T2.1 [ ] **`MembershipAdminService.adjustPeriodSpend`** — ADD/DEDUCT/SET delta 計算 + insert
  - Verify: UT-01, UT-02

- T2.2 [ ] **`MembershipAdminService.setTier`** — update membership row + bronze flag + event
  - Verify: UT-03, UT-04

- T2.3 [ ] **`MembershipAdminService.getDetail`** — compose admin detail DTO
  - Verify: unit test with mocked repos

## **Task 3: Panel facade & AdminPanelService**

Purpose: Panel 邊界對齊既有 facade 模式
Requirements: R1.x
Scope: `MembershipManagementFacade.java`, `AdminPanelService.java`, `CommandHandlerModule.java`
Out of scope: Button handler UI

- T3.1 [ ] **`MembershipManagementFacade`** — delegate read/write to admin service
  - Verify: compile + facade unit test

- T3.2 [ ] **`AdminPanelService`** — 暴露三個 membership 方法
  - Verify: compile

- T3.3 [ ] **`CommandHandlerModule`** — wire facade + admin service
  - Verify: `make build`

## **Task 4: Admin UI**

Purpose: 子面板互動完整流程
Requirements: R1.x, R4.x
Scope: `AdminPanelCommandHandler.java`, `AdminPanelButtonHandler.java`, view factory
Out of scope: Shop, settlement scheduler

- T4.1 [ ] **Main menu** — 新增「🏅 會員等級管理」按鈕與 customId 路由
  - Verify: AdminPanelCommandHandlerTest 若存在

- T4.2 [ ] **User select + detail embed** — 顯示 R1.2 欄位
  - Verify: manual admin panel

- T4.3 [ ] **Adjust spend flow** — mode select + modal + submit handler
  - Verify: integration test or handler unit test with mocks

- T4.4 [ ] **Set tier flow** — tier StringSelect + confirm
  - Verify: event published mock verify

## **Task 5: 文件與回歸**

Purpose: 文件同步、全量驗證
Requirements: R1–R4
Scope: `docs/features/administration.md`
Out of scope: —

- T5.1 [ ] **administration.md** — 會員等級管理章節
  - Verify: 對照 spec

- T5.2 [ ] **`make verify`** — 全專案綠
  - Verify: `make verify`
