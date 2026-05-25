# Spec: membership-join-tracking

- Date: 2026-05-24
- Feature: membership-join-tracking
- Owner: laitszkin

## Goal

記錄成員**最早加入任一 Discord 伺服器**的時間，初始化個人結算日錨點，供結算排程使用。

## Scope

### In Scope
- `GuildMemberJoinListener`（JDA `GuildMemberJoinEvent`）
- 更新 `global_member_membership.earliest_guild_join_at`（取 min）
- 計算 `settlement_day_of_month`（join 日；29–31 → 28）
- 初始化 `next_settlement_at`（首次 join 後下一個錨點日）
- 註冊 listener + preparation P1 intent

### Out of Scope
- 離開伺服器事件、backfill 既有成員（可選 follow-up）
- spend / settlement / discount

## Functional Behaviors (BDD)

### Requirement 1: 記錄最早加入日
**GIVEN** 用戶首次加入 guild A（2024-03-15）
**WHEN** `GuildMemberJoinEvent` 觸發
**THEN** `earliest_guild_join_at = 2024-03-15T...`
**AND** `settlement_day_of_month = 15`

**Requirements**:
- [x] R1.1 已存在更早 join 日 → 不更新 earliest
- [x] R1.2 新 join 日更晚 → 只更新若 earliest 為 null
- [x] R1.3 join 日為 31 號 → settlement_day_of_month = 28

### Requirement 2: 結算日初始化
**GIVEN** 新用戶首次 join
**WHEN** membership 記錄建立
**THEN** `next_settlement_at` = 下一個 settlement_day 的 00:00 UTC（或 Asia/Taipei，與專案 clock 一致）

**Requirements**:
- [x] R2.1 已存在 next_settlement_at 且 `last_settlement_at IS NOT NULL`（已結算過）→ 更早 join 不覆寫 anchor；若尚未結算（`last_settlement_at IS NULL`）且新 join 更早 → 可重算 `next_settlement_at`

## Error and Edge Cases
- [x] Bot 無 GUILD_MEMBERS intent → log error + preparation 阻擋部署
- [x] 並發雙 guild join → earliest 仍正確（DB transaction）

## Clarification Questions
None

## References
- `src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java`
- `membership-core` contract: `MembershipRepository`
