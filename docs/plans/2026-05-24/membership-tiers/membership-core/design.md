# Design: membership-core

## Architecture

```
membership/
├── domain/
│   ├── MembershipTier.java          # enum
│   ├── MembershipTierConfig.java      # 常數表
│   ├── GlobalMemberMembership.java    # aggregate
│   └── MembershipTierEvaluator.java   # pure static
├── persistence/
│   ├── MembershipRepository.java
│   └── JdbcMembershipRepository.java
└── di/MembershipModule.java
```

## Schema (`V029__create_global_member_membership.sql`)

```sql
CREATE TABLE global_member_membership (
  discord_user_id         BIGINT PRIMARY KEY,
  current_tier            VARCHAR(16) NOT NULL DEFAULT 'NONE',
  earliest_guild_join_at  TIMESTAMPTZ,
  settlement_day_of_month SMALLINT,       -- 1-28，29-31 映射為 28
  last_settlement_at      TIMESTAMPTZ,
  next_settlement_at      TIMESTAMPTZ,
  has_qualifying_bronze_order BOOLEAN NOT NULL DEFAULT FALSE,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gmm_next_settlement ON global_member_membership (next_settlement_at)
  WHERE next_settlement_at IS NOT NULL;
```

## Tier 判定算法

```java
// 由高到低檢查門檻
for (tier : [BLACK, DIAMOND, PLATINUM, GOLD, SILVER]) {
  if (avgListPriceM >= tier.threshold()) return tier;
}
if (hasQualifyingBronzeOrder) return BRONZE;
return NONE;
```

## 模組邊界

- **輸出給其他 spec**：`MembershipRepository`, `MembershipTier`, `MembershipTierConfig`, `MembershipTierEvaluator`
- **不依赖**：shop、dispatch、JDA

## 錯誤處理

- Repository DB 失敗 → 拋出 unchecked 或 `Result` 依專案慣例（與 `JdbcGameTokenAccountRepository` 一致）
