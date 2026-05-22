# Design: Guild Economy

- Date: 2026-05-20
- Feature: Guild Economy
- Change Name: guild-economy

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1 (Balance), R2 (Adjustment), R3 (Config), R4 (Tokens), R5 (Dice1), R6 (Dice2) |
| In-scope modules             | `packages/economy/src/currency/`, `packages/economy/src/token/`, `packages/economy/src/dice/` |
| External systems touched    | None（僅透過 `@ltdjms/shared` 基礎設施存取 DB/Redis/Discord） |
| Batch coordination          | `../coordination.md` |

## Target vs baseline

|                       | Baseline (Java) | Target (TypeScript) |
| --------------------- | --------------- | ------------------- |
| 貨幣 Repository | jOOQ `JooqMemberCurrencyAccountRepository` | Drizzle ORM `DrizzleCurrencyAccountRepository` |
| 代幣 Repository | JDBC `JdbcGameTokenAccountRepository` | Drizzle ORM `DrizzleTokenAccountRepository` |
| 骰子遊戲 | `DefaultDiceGame1Service` / `DefaultDiceGame2Service` | `DiceGame1Service` / `DiceGame2Service`（相同邏輯） |
| 快取 | Redis via Lettuce | Redis via ioredis（`@ltdjms/shared` 提供） |

## Boundaries

- Entry surface: Slash command handlers（7 個指令）
- Trust boundary: 管理員指令需要 `Permission.ADMINISTRATOR` 或 guild owner
- Outside → inside: `Discord Interaction` → `Command Handler` → `Service` → `Repository` → `Database`

## Modules

| Module key | Responsibility | Owned artifacts |
| ---------- | -------------- | --------------- |
| `currency-domain` | 貨幣領域模型與 schema | Drizzle: `guild_currency_config`, `member_currency_account`, `currency_transaction` |
| `currency-service` | 貨幣業務邏輯 | BalanceService, BalanceAdjustmentService, CurrencyConfigService, CurrencyTransactionService |
| `token-domain` | 代幣領域模型與 schema | Drizzle: `game_token_account`, `game_token_transaction` |
| `token-service` | 代幣業務邏輯 | GameTokenService, GameTokenTransactionService |
| `dice-domain` | 骰子遊戲配置 schema | Drizzle: `dice_game_1_config`, `dice_game_2_config` |
| `dice-service` | 骰子遊戲邏輯 | DiceGame1Service, DiceGame2Service |
| `bridge` | 代幣遊戲 → 貨幣入帳橋接 | GameRewardService |
| `commands` | Slash command handlers | 7 個 command handler |

## Interaction anchors

| ID | Intent | Caller → Callee | Coupling | Information crossing | Failure expectation |
| -- | ------ | --------------- | -------- | -------------------- | ------------------- |
| `INT-101` | 餘額查詢 | Command → BalanceService | sync | guildId+userId → BalanceView | DB error → Result.err |
| `INT-102` | 餘額調整 | Command/Bridge → BalanceAdjustmentService | sync + event | delta+source → new balance + event | 不足/overflow → Result.err |
| `INT-103` | 代幣扣除 | DiceGame → GameTokenService | sync | guildId+userId+amount → new balance | 不足 → Result.err |
| `INT-104` | 遊戲獎勵 | DiceGame → GameRewardService | sync | reward → currency credit | 大額分割為多筆 adjust |
| `INT-105` | 快取失效 | Event → CacheInvalidationListener | event | BalanceChangedEvent → cache key | Listener 例外不傳播 |

## Invariants

| Invariant | What breaks it | Symptoms if violated |
| --------- | -------------- | -------------------- |
| 骰子遊戲獎勵計算逐 byte 與 Java 一致 | 不同的隨機演算法或計算順序 | 玩家獎勵與 Java 版本不同 |
| DiceGame2 straight/triple 分配順序不可變 | 改變優先級 | 相同骰子產出不同獎勵 |
| adjustBalance overflow 檢測等價於 Math.addExact | 遺漏檢查 | 大額操作結果不正確 |

## Tradeoffs

| Decision | Rejected alternative | Locks in |
| -------- | -------------------- | -------- |
| Drizzle ORM 統一所有 repository | Prisma (太重) / raw SQL (型別不安全) | Drizzle schema + query API |
| Pure function + injected deps | Class-based（與 Java 完全對齊） | 可測試性更高 |
| 可注入 Random interface | 直接使用 Math.random | 測試可用 seeded random |
