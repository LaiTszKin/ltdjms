# Design: membership-join-tracking

## 流程

```
GuildMemberJoinEvent
  → MembershipJoinService.onMemberJoin(userId, joinedAt)
      → findOrCreate(userId)
      → if earliest == null || joinedAt < earliest:
            earliest = joinedAt
            settlementDay = clampDay(joinedAt)
            nextSettlement = computeNext(settlementDay, joinedAt)
      → save
```

## `clampDayOfMonth`

- 1–28 保持；29、30、31 → **28**（避免 2 月無結算日）

## `computeNextSettlement`

- 使用注入的 `Clock`（可測）
- 若 join 日 == 今天且未到結算時刻 → next = 下月同 anchor day
- 若 join 日已過本月 anchor → next = 下月 anchor

## 檔案

| 檔案 | 職責 |
|------|------|
| `membership/listeners/GuildMemberJoinListener.java` | JDA adapter |
| `membership/services/MembershipJoinService.java` | 業務邏輯 |
| `DiscordCurrencyBot.java` | 註冊 listener、GUILD_MEMBERS intent |

## 時區

與 `FiatOrderProcessingScheduler` 相同：使用 `Clock.systemDefaultZone()` 或明確 `Asia/Taipei`（在 design 與 contract 固定一種）。

## 測試

- Unit: `MembershipJoinServiceTest` with fixed Clock
- 不需 Discord 整合測試（mock event）
