# Tasks: membership-join-tracking

## Task 1: Join service
- T1.1 [ ] `MembershipJoinService` + settlement day 算法
  - Verify: unit tests（earliest min、day clamp、next settlement）

## Task 2: JDA listener
- T2.1 [ ] `GuildMemberJoinListener`
- T2.2 [ ] `DiscordCurrencyBot` 註冊 + GUILD_MEMBERS intent
  - Verify: `make build`

## Task 3: 文檔
- T3.1 [ ] `docs/configuration.md` 補 Server Members Intent
  - Verify: grep configuration.md
