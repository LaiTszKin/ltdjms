# Contract: user-panel-membership-info

## `MembershipQueryService.getPanelSummary(userId)`

**Returns** extended `MembershipPanelSummary`:

```java
record MembershipPanelSummary(
  MembershipTier tier,
  long periodSpendListPriceM,
  long nextTierThresholdM,
  Instant nextSettlementAt,
  BigDecimal discountRate,
  Instant earliestGuildJoinAt,  // null if no row or null column
  long remainingToNextTierM,
  int monthlyTokenGrant
)
```

**Invariants:**
- `remainingToNextTierM = max(0, nextTierThresholdM - periodSpendListPriceM)` when threshold > 0
- `monthlyTokenGrant = effectiveTier.monthlyTokenGrant()`（非 stored tier 若 bronze floor 適用則用 effective）
- Read-only; no side effects

## `UserPanelView.formatMembershipField()`

**Output** zh-TW multiline string containing:
- 加入日期行
- 等級行
- 目前權益行（護航折扣 + 每月贈幣）
- 本週期累計 M
- 距下一等級行
- 下一門檻進度（既有）
- 下次結算日（既有）

## Dependencies

- membership-core / join-tracking（`earliest_guild_join_at` 欄位已存在）
- membership-spend-ledger（period spend sum）
- user-panel `MemberInfoFacade`（existing wiring）

## External

- Discord `<t:unix:D>` timestamp in embed field values — [Message Formatting](https://discord.com/developers/docs/reference#message-formatting-timestamp-styles)
