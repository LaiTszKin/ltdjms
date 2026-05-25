# Contract: membership-benefits-ui

## `MembershipTokenGrantService.grantForSettlement(userId, periodEnd, tier)`

- Idempotent on `(userId, periodEnd)`
- tokens = `MembershipTierConfig.monthlyTokenGrant(tier)`
- Skip if tokens == 0

## `MemberInfoFacade.getMembershipSummary(userId)`

```java
record MembershipPanelSummary(
  MembershipTier tier,
  long periodSpendListPriceM,
  long nextTierThresholdM,
  Instant nextSettlementAt,
  BigDecimal discountRate
) {}
```

## Dependencies

- membership-settlement（grant hook）
- membership-core, membership-spend-ledger（period spend）
- guild-economy GameTokenService
