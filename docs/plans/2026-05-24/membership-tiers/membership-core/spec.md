# Spec: membership-core

- Date: 2026-05-24
- Feature: membership-core
- Owner: laitszkin

## Goal

建立會員等級的 domain、DB schema、等級常數與純函式 tier 判定，作為 batch 其餘 spec 的共用基礎。

## Scope

### In Scope
- Flyway `V029__create_global_member_membership.sql`
- `MembershipTier` enum、`MembershipTierConfig` 定稿常數
- `GlobalMemberMembership` aggregate、`MembershipRepository`
- `MembershipTierEvaluator` 純函式（依平均 M 判定 SILVER+；青銅 qualifying flag）
- Dagger `MembershipModule` 骨架

### Out of Scope
- JDA join listener、排程、付款 hook、UI、token grant

## Functional Behaviors (BDD)

### Requirement 1: 等級常數
**GIVEN** coordination 定稿表
**WHEN** 程式讀取 `MembershipTierConfig`
**THEN** 六等折扣率、贈幣數、門檻 M 與 spec 一致
**AND** `NONE` 表示未達青銅

**Requirements**:
- [ ] R1.1 `MembershipTier` 含 NONE, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, BLACK
- [ ] R1.2 每 tier 暴露 `discountRate()`（d）、`monthlyTokenGrant()`、`thresholdListPriceTwd()`
- [ ] R1.3 門檻值：500 / 14_000 / 33_000 / 100_000 / 120_000 / 250_000

### Requirement 2: Tier 判定（純函式）
**GIVEN** 週期平均 listPriceM、hasQualifyingBronzeOrder=true
**WHEN** 呼叫 `MembershipTierEvaluator.resolveTier(avgM, hasQualifyingBronzeOrder)`
**THEN** 回傳符合最高門檻的 tier
**AND** avgM 不足時若 hasQualifyingBronzeOrder 則至少 BRONZE，否則 NONE

**Requirements**:
- [ ] R2.1 avgM ≥ 250_000 → BLACK（且依序檢查）
- [ ] R2.2 avgM < 14_000 且 hasQualifyingBronzeOrder → BRONZE
- [ ] R2.3 avgM < 500 且 !hasQualifyingBronzeOrder → NONE

### Requirement 3: 持久化
**GIVEN** discordUserId
**WHEN** `MembershipRepository.findOrCreate(userId)`
**THEN** 回傳 `GlobalMemberMembership`，預設 tier=NONE

**Requirements**:
- [ ] R3.1 表 `global_member_membership` 主鍵 `discord_user_id`
- [ ] R3.2 欄位：`current_tier`, `earliest_guild_join_at`, `settlement_day_of_month`, `last_settlement_at`, `next_settlement_at`, `has_qualifying_bronze_order`, timestamps
- [ ] R3.3 JDBC repository 實作

## Error and Edge Cases
- [ ] avgM 為負或 null → treat as 0
- [ ] tier 字串 DB  corrupt → migration 用 CHECK 或 app 層 validate
- [ ] 同一 userId 並發 findOrCreate → UPSERT 或 transaction 安全

## Clarification Questions
None（batch coordination 已確認）

## References
- Related code files:
  - `src/main/java/ltdjms/discord/product/domain/EscortOptionCatalog.java`
  - `src/main/java/ltdjms/discord/gametoken/domain/GameTokenAccount.java`（findOrCreate 模式參考）
  - `docs/plans/2026-05-24/membership-tiers/coordination.md`
