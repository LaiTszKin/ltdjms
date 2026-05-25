# Checklist: membership-core

- [x] C1 Flyway V029 套用成功
- [x] C2 六等常數與 coordination 表一致
- [x] C3 Tier evaluator 邊界測試全綠（含 NONE/BRONZE/BLACK）
- [x] C4 Repository findOrCreate 並發安全（至少單測 mock 或 IT）
- [x] C5 `make verify` 通過

## Test Strategy

| 類型 | 範圍 | 檔案 |
|------|------|------|
| Unit | TierEvaluator、TierConfig | `MembershipTierEvaluatorTest` |
| Integration | JDBC repository | `JdbcMembershipRepositoryIT`（Testcontainers PG） |
| Property | 任意 avgM ≥ 門檻 → tier 不低於該檔 | fast-check 可選 PBT |
