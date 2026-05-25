# Contract: membership-join-tracking

## `MembershipJoinService.onMemberJoin(long discordUserId, Instant joinedAt)`

- Idempotent：多次呼叫不會把 earliest 推晚
- Side effect：可能 INSERT membership row

## Settlement day 映射

| Join day | settlement_day_of_month |
|----------|-------------------------|
| 1–28 | 同值 |
| 29–31 | 28 |

## Dependencies

- Requires: `membership-core` merged
- Requires: preparation P1 GUILD_MEMBERS intent
