# Tasks: user-panel-membership-info

- Date: 2026-05-25
- Feature: user-panel-membership-info

## **Task 1: 擴充 Panel Summary DTO**

Purpose: Query 層回傳加入日、剩餘 M、贈幣欄位
Requirements: R1.x, R2.x, R3.x
Scope: `MembershipPanelSummary.java`, `MembershipQueryService.java`
Out of scope: UserPanelView 文案、測試以外檔案

- T1.1 [ ] **`MembershipPanelSummary.java`** — 新增 `earliestGuildJoinAt`、`remainingToNextTierM`、`monthlyTokenGrant` 欄位；新增 static `computeRemaining(spent, threshold)`
  - Verify: `make build` 編譯通過

- T1.2 [ ] **`MembershipQueryService.getPanelSummary()`** — 從 `GlobalMemberMembership` 讀 join_at；計算 remaining；帶入 `effectiveTier.monthlyTokenGrant()`
  - Verify: 新增/更新 `MembershipQueryServiceTest` 或 integration test 斷言三欄位

## **Task 2: User Panel 文案**

Purpose: Embed 顯示三項新資訊
Requirements: R1.x, R2.x, R3.x
Scope: `UserPanelView.java`
Out of scope: Admin、Shop、settlement

- T2.1 [ ] **`UserPanelView.formatMembershipField()`** — 重排欄位順序；加入「加入日期」「距下一等級：還需 N M」「目前權益」段落；NONE/BLACK 邊界文案
  - Verify: `UserPanelEmbedBuilderTest` 綠

- T2.2 [ ] **測試 fixtures** — 更新 `UserPanelUpdateListenerTest`、`MemberInfoFacadeTest` 中 `MembershipPanelSummary` 建構子呼叫
  - Verify: `mvn test -Dtest=UserPanelEmbedBuilderTest,UserPanelUpdateListenerTest,MemberInfoFacadeTest`

## **Task 3: 文件**

Purpose: Feature doc 與 spec 一致
Requirements: R1–R3
Scope: `docs/features/membership-tiers.md`
Out of scope: architecture atlas merge（batch 完成後）

- T3.1 [ ] **`docs/features/membership-tiers.md`** — User Panel 章節補上加入日、剩餘 M、權益三項
  - Verify: 人工對照 spec.md
