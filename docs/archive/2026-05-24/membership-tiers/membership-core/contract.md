# Contract: membership-core

## Public API

### `MembershipTierConfig`

| Method | Returns | Notes |
|--------|---------|-------|
| `thresholdListPriceTwd(MembershipTier)` | `long` | 原價 M 門檻 |
| `discountRate(MembershipTier)` | `BigDecimal` or `double` | 0.05 = 95折 |
| `monthlyTokenGrant(MembershipTier)` | `int` | NONE/BRONZE = 0 |

### `MembershipTierEvaluator.resolveTier(long avgListPriceM, boolean hasQualifyingBronzeOrder)`

Returns: `MembershipTier`

### `MembershipRepository`

| Method | Semantics |
|--------|-----------|
| `findByUserId(long)` | `Optional<GlobalMemberMembership>` |
| `findOrCreate(long)` | 不存在則 INSERT NONE |
| `save(GlobalMemberMembership)` | 更新 updated_at |

## DB Contract

- Table: `global_member_membership`
- `current_tier` 合法值：NONE, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, BLACK

## Test Oracle

Fixtures: `fixtures/tier-evaluator-cases.json` — 輸入 (avgM, bronzeFlag) → expected tier
