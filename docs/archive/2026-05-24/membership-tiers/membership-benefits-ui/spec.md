# Spec: membership-benefits-ui

- Date: 2026-05-24
- Feature: membership-benefits-ui
- Owner: laitszkin

## Goal

結算日依等級發放贈幣（可累積、無上限），並在 `/user-panel` 展示會員等級、週期消費進度與下次結算日。

## Scope

### In Scope
- `GameTokenTransaction.Source.MEMBERSHIP_GRANT`
- `MembershipTokenGrantService` 訂閱 settlement 完成或監聽 `MembershipTierChangedEvent` + 週期 grant
- Idempotency：`membership_token_grant_log (user_id, settlement_period_end)` UNIQUE
- 在**每次結算**發放**當前 tier** 的 monthlyTokenGrant（含 tier 未變也發）
- `MemberInfoFacade` / `UserPanelView` / `UserPanelEmbedBuilder` 擴充

### Out of Scope
- Admin 調等
- 推播通知 tier 變更（可 enhancement）

## Functional Behaviors (BDD)

### Requirement 1: 結算日贈幣
**GIVEN** 用戶結算完成，current_tier=GOLD（200 幣/月）
**WHEN** grant 執行
**THEN** `GameTokenService.tryAdjustTokens(+200)` + transaction MEMBERSHIP_GRANT
**AND** 同一 settlement_period_end 不重複發

**Requirements**:
- [x] R1.1 BRONZE/NONE → 0 幣，skip
- [x] R1.2 餘額可累積，無上限
- [x] R1.3 grant 在 settle 成功後執行（同事件或 settlement 尾端）

### Requirement 2: User Panel
**GIVEN** 用戶開啟 `/user-panel`
**WHEN** 面板渲染
**THEN** 顯示：等級名稱、當前折扣、本週期累計 M、下一門檻進度、下次結算日

**Requirements**:
- [x] R2.1 無 membership 記錄 → 顯示「尚未達標」/ NONE
- [x] R2.2 進度條：距下一 tier 門檻百分比（可選文字）

## Error and Edge Cases
- [x] tryAdjustTokens 失敗 → log + retry next tick（不 rollback tier）
- [x] Panel session 刷新不額外查 heavy aggregate（快取或單次 query）

## Clarification Questions
None

## References
- `GameTokenService.java`, `UserPanelService.java`, `MemberInfoFacade.java`
- `MembershipTierChangedEvent` / settlement hook
