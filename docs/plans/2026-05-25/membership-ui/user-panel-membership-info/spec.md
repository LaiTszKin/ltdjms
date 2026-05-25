# Spec: user-panel-membership-info

- Date: 2026-05-25
- Feature: user-panel-membership-info
- Owner: laitszkin

## Goal

在 `/user-panel` 會員區塊補齊加入日期、距下一等級剩餘消費金額，以及當前等級完整權益說明，讓成員無需詢問管理員即可了解自身會員狀態。

## Scope

### In Scope
- 擴充 `MembershipPanelSummary`：`earliestGuildJoinAt`、`remainingToNextTierM`、`monthlyTokenGrant`
- 擴充 `MembershipQueryService.getPanelSummary()` 回傳上述欄位
- 擴充 `UserPanelView.formatMembershipField()` 繁中文案
- 更新 `UserPanelEmbedBuilderTest` / 相關 unit test
- 更新 `docs/features/membership-tiers.md` user panel 章節

### Out of Scope
- Admin 調整消費/等級
- 商店折扣列表展示
- 全 tier ladder 對照表
- 推播 tier 變更通知

## Functional Behaviors (BDD)

### Requirement 1: 顯示加入日期
**GIVEN** 用戶已存在 `global_member_membership` 且 `earliest_guild_join_at` 非空
**WHEN** 用戶開啟 `/user-panel`
**THEN** 會員區塊顯示「**加入日期：**<t:epoch:D>」

**Uncertainty Level**: Known

**Requirements**:
- [ ] R1.1 有 join 記錄時使用 Discord 動態日期格式 `<t:epochSeconds:D>`
- [ ] R1.2 無 membership 列或 join 為 null → 顯示「**加入日期：**尚未記錄」

### Requirement 2: 距下一等級剩餘消費
**GIVEN** 用戶有效 tier 低於 BLACK，且存在下一門檻 M
**WHEN** 面板渲染
**THEN** 顯示「**距下一等級：**還需 {remainingM} M」（千分位格式）

**Uncertainty Level**: Known

**Requirements**:
- [ ] R2.1 `remainingM = max(0, nextTierThresholdM - periodSpendListPriceM)`
- [ ] R2.2 NONE 用戶下一門檻為 SILVER（14,000 M）；顯示距白銀剩餘 M
- [ ] R2.3 已達最高等級（BLACK 或無下一門檻）→ 顯示「**距下一等級：**已達最高等級」
- [ ] R2.4 保留既有「下一門檻進度 X% (spent/threshold)」行（不刪除）

### Requirement 3: 目前會員權益
**GIVEN** 用戶有效 tier 非 NONE
**WHEN** 面板渲染
**THEN** 顯示「**目前權益：**」區塊，含護航折扣與每月贈幣

**Uncertainty Level**: Known

**Requirements**:
- [ ] R3.1 護航折扣沿用 `MembershipTierLabels.discountLabel(tier)`
- [ ] R3.2 每月贈幣：`monthlyTokenGrant > 0` 時顯示「每月贈幣 {N}」；0 時顯示「每月贈幣：無」
- [ ] R3.3 NONE 用戶在權益區顯示「完成 qualifying 護航法幣單可升級青銅」提示（與既有 hint 合併，避免重複段落）

## Error and Edge Cases
- [ ] membership 查詢失敗 → 沿用既有 NONE hint，不 crash handler
- [ ] period spend 為 0、remaining = threshold → 顯示完整 threshold 數值
- [ ] embed field 字元 ≤ 1024（權益 + 進度 + 日期不超限）
- [ ] `UserPanelUpdateListener` 刷新時新欄位一併更新

## Clarification Questions

None（batch coordination 已定預設：加入日 = earliest_guild_join_at；剩餘 M 公式見上；權益 = 當前 tier 折扣 + 贈幣）

## References
- Official docs:
  - [Discord Markdown — Strikethrough](https://support.discord.com/hc/en-us/articles/210298617-Markdown-Text-101-Chat-Formatting-Bold-Italic-Underline)
  - [Discord Timestamp format](https://discord.com/developers/docs/reference#message-formatting-timestamp-styles)
- Related code files:
  - `src/main/java/ltdjms/discord/membership/services/MembershipQueryService.java`
  - `src/main/java/ltdjms/discord/membership/services/MembershipPanelSummary.java`
  - `src/main/java/ltdjms/discord/panel/services/UserPanelView.java`
  - `docs/features/membership-tiers.md`
