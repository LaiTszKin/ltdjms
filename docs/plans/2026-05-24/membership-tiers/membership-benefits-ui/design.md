# Design: membership-benefits-ui

## Grant 時機

在 `MembershipSettlementService.settle()` **結尾**呼叫：

```java
tokenGrantService.grantForSettlement(userId, periodEnd, membership.currentTier());
```

比監聽 tier changed 更可靠（tier 不變仍發幣）。

## Schema (`V031`)

```sql
CREATE TABLE membership_token_grant_log (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discord_user_id       BIGINT NOT NULL,
  settlement_period_end TIMESTAMPTZ NOT NULL,
  tier                  VARCHAR(16) NOT NULL,
  tokens_granted        INT NOT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (discord_user_id, settlement_period_end)
);
```

## GameTokenTransaction.Source

```java
MEMBERSHIP_GRANT("會員結算贈幣")
```

## User Panel 欄位

| 欄位 | 來源 |
|------|------|
| tierDisplayName | MembershipTier 中文標籤 |
| discountLabel | e.g.「護航 9 折」 |
| periodSpendM | sum spend 本週期 |
| nextTier / progress | TierEvaluator 下一門檻 |
| nextSettlementAt | membership row |

## 檔案

- `membership/services/MembershipTokenGrantService.java`
- `membership/listeners/MembershipSettlementGrantHook.java`（或 inline in settlement）
- `panel/services/MemberInfoFacade.java` — 新增 membership section
- `panel/services/UserPanelEmbedBuilder.java`

## UI 文案（繁中）

- 等級：青銅/白銀/…/黑金
- 未達青銅：「完成一筆 NT$500 以上護航法幣訂單即可升級青銅」
