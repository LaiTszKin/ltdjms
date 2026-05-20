# Design: Shared Infrastructure

- Date: 2026-05-20
- Feature: Shared Infrastructure
- Change Name: shared-infrastructure

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1 (Result), R2 (DomainError), R3 (Config), R4 (Database), R5 (Redis), R6 (Events), R7 (Logging), R8 (Discord), R9 (DI) |
| In-scope modules (≤3)       | `packages/shared/src/types/`, `packages/shared/src/infra/`, `packages/shared/src/discord/` |
| External systems touched    | PostgreSQL, Redis, Discord API (via discord.js) — full truth in contract.md |
| Batch coordination          | `../coordination.md` |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | Java `ltdjms.discord.shared.*` / `ltdjms.discord.discord.*` / Maven modules | TypeScript `@ltdjms/shared` package with `types/`, `infra/`, `discord/` subdirectories |
| DI framework | Dagger 2.52 | tsyringe |
| Database | JOOQ + JDBC + HikariCP + Flyway | Drizzle ORM + node-postgres + drizzle-kit |
| Redis | Lettuce 6.3.2 | ioredis |
| Config | Typesafe Config + DotEnvLoader | dotenv + Zod |
| Logging | SLF4J + Logback | pino |
| Events | CopyOnWriteArrayList<Consumer> | EventEmitter (synchronous) |
| Discord | JDA 5.2.2 | discord.js v14 |

## Boundaries

- Entry surface(s): DI container (程式進入點 `main.ts` 透過 container 解析所有服務)
- Trust boundary crossed: Discord API token (環境變數 → Config → discord.js Client)
- Outside → inside (one line): `Discord User` → `discord.js Client` → `DiscordInteraction/DiscordContext` → `Command Handler (business logic)`

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `types` | 定義所有跨模組共享的型別與介面 | Result<T,E>, DomainError, DomainEvent (12 種), DiscordInteraction, DiscordContext, DiscordEmbedBuilder, EmbedView, ButtonView, CacheService |
| `infra` | 提供執行期的基礎設施實作 | Config, Database (pool + migration), Redis Cache, EventPublisher, Logger, DI registration |
| `discord` | Discord.js 的抽象層實作與 Mock | JdaDiscordInteraction→DiscordJsInteraction, JdaDiscordContext→DiscordJsContext, JdaDiscordEmbedBuilder→DiscordJsEmbedBuilder, DiscordRuntimeGateway, Mock implementations |

---

## Interaction anchors (`INT-###`)

| ID        | Intent (when this coupling matters) | Caller → Callee | Coupling kind | Information / state crossing | Failure / propagation expectation |
| --------- | ------------------------------------ | --------------- | ------------- | ---------------------------- | ------------------------------------------- |
| `INT-001` | Config 初始化——所有模組啟動的第一步 | `main` → `infra` | sync call `loadConfig()` | env vars + .env file → typed Config object | 缺少必要欄位 → 拋出錯誤、阻止啟動 |
| `INT-002` | Database 連線——所有 repository 都依賴 | `infra` → `PostgreSQL` | node-postgres Pool | connection string + credentials | 連線失敗 → 重試 3 次、失敗後 exit(1) |
| `INT-003` | Migration 執行——必須在 repository 使用前完成 | `infra` → `PostgreSQL` | drizzle-kit migrate | SQL migration files → applied schema | 失敗 → SchemaMigrationException、阻止啟動 |
| `INT-004` | DomainEvent 發布——業務層與監聽層的解耦 | `business` → `infra` | sync `publish(event)` | DomainEvent payload → N listeners | listener 例外被捕獲並 logged、不傳播 |
| `INT-005` | Discord Interaction 抽象——所有 command handler 的入口 | `discord.js events` → `discord` | adapter pattern | JDA event → DiscordInteraction/DiscordContext | handle 錯誤 → BotErrorHandler 統一處理 |
| `INT-006` | Discord Embed 建構——所有 UI 輸出 | `business` → `discord` | sync builder | EmbedView + ButtonView → discord.js MessageEmbed/ActionRow | 超長 → 自動截斷 + log warning |
| `INT-007` | Redis 快取——所有需要快取的服務 | `business` → `infra` | sync `get/put/invalidate` | key + value (JSON) → Redis | Redis 不可用 → 優雅降級（no-op） |
| `INT-008` | DI 容器解析——所有模組的啟動 | `main` → `tsyringe` | `container.resolve()` | 註冊的 singleton → 實例 | 循環依賴 → 拋出明確錯誤 |

## Requirement linkage (coarse ordering)

### R1+R2 (Types) → R3 (Config) → R7 (Logging) → R4 (Database) → R5 (Redis) → R6 (Events) → R8 (Discord) → R9 (DI)

- Anchor order hint: `INT-001` → `INT-002` → `INT-003` → `INT-007` → `INT-004` → `INT-005`
- 型別定義 (R1, R2) 必須最先完成，因為所有模組都依賴它們
- Config (R3) 和 Logging (R7) 可並行，兩者僅依賴 types
- Database (R4) 和 Redis (R5) 可並行
- Events (R6) 僅依賴 types，可在 Database 完成後開始
- Discord 抽象 (R8) 可與 Events 並行
- DI (R9) 是最後的組裝步驟

## Data & persistence (design-level)

| Resource                      | Typical readers/writers | Consistency expectation |
| ----------------------------- | ----------------------- | ----------------------- |
| PostgreSQL (18 tables) | 所有 Repository 實作 (Drizzle ORM) | 單一 write source（bot 單程序），無分散式一致性需求 |
| Redis (key-value cache) | CacheService (讀寫)、CacheInvalidationListener (刪除) | 盡力而為（best-effort），miss 時回退到 DB 查詢 |
| .env file | EnvironmentConfig（唯讀，啟動時載入一次） | 不可變（啟動後不重新讀取） |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------------ | -------------------- |
| 所有跨模組依賴必須透過 `@ltdjms/shared` 的型別 | 模組直接 import discord.js 或資料庫特定型別 | 模組間耦合、難以測試、無法替換實作 |
| 所有 DomainEvent listener 例外不得傳播 | Listener 中未捕捉的例外 | 事件發布中斷、其他 listener 無法收到事件 |
| Database migration 必須在 repository 使用前完成 | 跳過 migration 執行 | 資料表不存在、SQL 錯誤 |
| Config 值在啟動時凍結 | 執行期修改 config | 行為不一致、難以除錯 |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in |
| -------- | -------------------- | -------- |
| tsyringe (decorator-based DI) | Inversify (太重)、手動 DI (無型別安全) | `@singleton()`、`@inject()` 裝飾器語法 |
| Drizzle ORM (SQL-like, type-safe) | Prisma (過重的 codegen)、Kysely (較低階) | Drizzle schema 定義語法、drizzle-kit migration |
| EventEmitter (同步) | RxJS (過重)、手動 pub/sub | `publish()` 同步分發，listener 必須快速返回 |
| pino (輕量、高效) | winston (較重) | pino logger API 和設定格式 |
| 單一 shared package | 多個細分 package (types、infra、discord 分開) | 簡化依賴管理，但 shared package 較大 |

## Batch-only

本 spec 的產出（`@ltdjms/shared`）是其他 5 個 member spec 的編譯時與執行時依賴。所有型別（Result, DomainError, DomainEvent, DiscordInteraction 等）和服務介面（Config, Database, Cache, EventPublisher）必須在本 spec 完成後凍結，其他 spec 才能開始實作。
