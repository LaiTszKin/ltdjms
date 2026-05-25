# Design: membership-settlement

## 排程

```java
// MembershipSettlementScheduler — 每 24h（或 1h 精度）掃描
membershipRepository.findDueForSettlement(clock.instant())
  .forEach(id -> settlementService.settle(id));
```

## settle 算法

```
periodStart = last_settlement_at ?? earliest_guild_join_at ?? epoch
periodEnd   = next_settlement_at (本次結算時刻)
totalM      = spendRepo.sumListPriceInPeriod(userId, periodStart, periodEnd)
avgM        = totalM   // 一個結算週期 = 一個月，總 M 即月平均 M
newTier     = TierEvaluator.resolve(avgM, hasQualifyingBronzeOrder)
if newTier != oldTier → publish MembershipTierChangedEvent
update tier, last_settlement_at, next_settlement_at += 1 month anchor
```

## Claim 鎖

`UPDATE global_member_membership SET ... WHERE discord_user_id=? AND next_settlement_at <= ?`  
或使用 `settlement_claimed_at` 欄位（若需）；首版可用 transaction + row lock。

## Event

```java
record MembershipTierChangedEvent(
  long userId,
  String previousTierCode,
  String currentTierCode,
  long periodAvgListPriceM,
  Instant settledAt
) {}
```

同步 EventBus（與 `BalanceChangedEvent` 相同模式）。

## Lifecycle

`DiscordCurrencyBot.start()` → scheduler.start()  
`DiscordCurrencyBot.stop()` → scheduler.shutdown()

## 檔案

- `membership/services/MembershipSettlementService.java`
- `membership/services/MembershipSettlementScheduler.java`
- `membership/events/MembershipTierChangedEvent.java`
