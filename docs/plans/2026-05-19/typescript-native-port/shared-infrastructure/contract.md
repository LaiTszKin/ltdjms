# Contract: Shared Infrastructure

- Date: 2026-05-20
- Feature: Shared Infrastructure
- Change Name: shared-infrastructure

## Scope

- **External deps in this doc:** 7（discord.js、Drizzle ORM、ioredis、pino、tsyringe、Zod、node-postgres）

## Dependencies

### discord.js v14

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://discord.js.org/docs/packages/discord.js/14.18.0 | Client, GatewayIntentBits, Events (InteractionCreate, MessageCreate), CommandInteraction, ButtonInteraction, ModalSubmitInteraction, EmbedBuilder, ActionRowBuilder, StringSelectMenuBuilder, REST (SlashCommandBuilder) |

**Version revision assumed:** `^14.18.0`（package.json 中 pin minor version）

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| Client 登入 token 並監聽 interaction/message 事件 | Client class + Events |
| Slash command 的 reply/edit/deferReply 操作 | CommandInteraction / InteractionReplyOptions |
| Embed 建構（title ≤256、description ≤4096、fields ≤25） | EmbedBuilder API reference |
| ActionRow + Button + StringSelectMenu 建構 | ComponentBuilder API reference |
| 從 interaction 提取 guildId、userId、channelId | CommandInteraction / BaseInteraction properties |
| Gateway intents（Guilds、GuildMessages、MessageContent） | GatewayIntentBits |

#### Limits & failures

| Category | Doc fact | Meaning while executing tasks.md |
| -------- | -------- | -------------------------------- |
| Embed field 上限 25、title 256 chars、description 4096 chars | Embed limits | DiscordEmbedBuilder 必須強制這些上限並自動截斷 |
| Button label 上限 80 chars | Component limits | ButtonView 建構時驗證 |
| StringSelectMenu 最多 25 選項 | Component limits | SelectMenuUtil 自動分割超過 25 選項的選單 |
| Interaction token 有效期 15 分鐘 | InteractionToken lifetime | SessionManager TTL 設為 15 分鐘 |
| Rate limit: 50 requests/second per token | Global rate limit | 不需要特別處理（bot 單用戶請求量遠低於上限） |

#### Security & secrets

| Concern | Constraint |
| ------- | ---------- |
| Bot Token | 透過 `DISCORD_BOT_TOKEN` 環境變數注入，絕不硬編碼或 commit |
| OAuth scopes | `bot`、`applications.commands` |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-001` | `Client.login(token)` — 啟動時登入 Discord | Token 來自 Config，缺少時拋出錯誤並 exit(1) | 不假設 token 格式或權限範圍 |
| `EXT-002` | `client.on('interactionCreate', handler)` — 監聽所有 interaction | Handler 內必須 try/catch 所有例外並透過 BotErrorHandler 處理 | 不假設 interaction 型別（runtime 判斷） |
| `EXT-003` | `EmbedBuilder` / `ActionRowBuilder` — 建構 Discord UI | 強制 Discord 長度限制，超長時截斷並 log warning | 不假設 embed 一定可以發送成功 |

---

### Drizzle ORM

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://orm.drizzle.team/docs/overview | Schema definition, Querying (select, insert, update, delete with RETURNING), Connections (node-postgres Pool), Migrations (drizzle-kit) |

**Version revision assumed:** `^0.42.0`（package.json 中 pin minor version）

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| PostgreSQL schema 定義（pgTable、pgEnum） | Drizzle PG schema docs |
| Conditional UPDATE: `WHERE col IS NULL` 搭配 `RETURNING` | Drizzle update + where + returning |
| Connection pool 共用（node-postgres Pool 傳入 drizzle） | Drizzle node-postgres connection |
| drizzle-kit generate + migrate 命令 | drizzle-kit CLI docs |

#### Limits & failures

| Category | Doc fact | Meaning while executing tasks.md |
| -------- | -------- | -------------------------------- |
| Connection pool 預設大小 = 10 | node-postgres defaults | 設定 `max: 5`（bot 為單用戶、低並發） |
| Migration 檔案命名: `0000_snake_case.sql` | drizzle-kit convention | 直接使用現有 Flyway SQL 作為參考手寫 Drizzle schema |
| No built-in connection retry | pg Pool error handling | 實作 `DatabaseConfig` 包裝層，連線失敗重試 3 次 |

#### Security & secrets

| Concern | Constraint |
| ------- | ---------- |
| DB Password | 透過 `DATABASE_PASSWORD` 環境變數注入 |
| Connection string | 不記錄完整 connection string（redact password 後才能 log） |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-004` | `drizzle(pool)` — Drizzle 實例建立 | pool 來自 node-postgres Pool，設定與 Java HikariCP 對齊 | 不假設 pool 永遠可用 |
| `EXT-005` | `db.update(table).set(...).where(eq(...)).returning()` — Conditional UPDATE 模式 | 所有 `mark*IfPending` / `mark*IfNeeded` 操作必須使用 RETURNING 確認更新成功 | 不假設更新一定會影響 rows |
| `EXT-006` | drizzle-kit `migrate()` — Migration 執行 | 必須在 repository 使用前執行，失敗時阻止啟動 | 不假設 migration 檔案與 schema 永遠一致 |

---

### ioredis

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://github.com/redis/ioredis | Connection (new Redis()), Basic commands (get, set, del, expire), Error handling |

**Version revision assumed:** `^5.6.0`

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| Redis 連線（支援 redis:// URI） | ioredis README - Connect to Redis |
| `set(key, value, 'EX', ttl)` 支援 TTL | ioredis README - Basic |
| `get(key)` 回傳 string 或 null | ioredis README - Basic |
| `del(key)` 刪除 key | ioredis README - Basic |

#### Limits & failures

| Category | Doc fact | Meaning while executing tasks.md |
| -------- | -------- | -------------------------------- |
| Redis 連線失敗時指令拋出例外 | ioredis error handling | CacheService 包裝層捕捉所有例外，get 回傳 null，put/invalidate 靜默忽略 |
| 預設 maxRetriesPerRequest = 20 | ioredis options | 設定 `maxRetriesPerRequest: 3`、`retryStrategy` 總等待不超過 10 秒 |

#### Security & secrets

| Concern | Constraint |
| ------- | ---------- |
| Redis password | 包含在 `REDIS_URI` 環境變數中 |
| URI logging | log 時 redact password（`redis://user:***@host:port` 格式） |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-007` | `new Redis(uri)` — Redis client 建立 | URI 來自 Config，連線失敗不阻止啟動（優雅降級） | 不假設 Redis 永遠可用 |

---

### pino

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://getpino.io/#/docs/api | Logger (trace/debug/info/warn/error/fatal), child loggers, level setting, pretty printing |

**Version revision assumed:** `^9.6.0`

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `pino(options)` 建立 root logger | pino API docs |
| `logger.child({ module: 'name' })` 建立 context logger | pino child loggers docs |
| `logger.info(obj, msg)` / `logger.error(obj, msg)` | pino API docs |
| level 設定（開發: debug、生產: info） | pino level docs |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-008` | `pino({ level })` — Logger 建立 | JSON 輸出格式，生產環境不輸出 debug/trace | 不硬編碼 level |

---

### tsyringe

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://github.com/microsoft/tsyringe | @singleton, @inject, @injectable, @injectAll, container, DependencyContainer |

**Version revision assumed:** `^4.8.0`

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `@singleton()` 註冊單例服務 | tsyringe README - Usage |
| `@inject(Token)` 指定注入標記 | tsyringe README - Injection |
| `@injectAll(Token)` 多重注入（用於 event listeners） | tsyringe README - Inject All |
| `container.resolve<T>(Token)` 解析服務 | tsyringe README - Container |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-009` | `container.resolve<App>(App)` — 啟動時解析根組件 | 循環依賴必須在啟動時被 tsyringe 偵測並拋出錯誤 | 不依賴 lazy injection |

---

### Zod

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://zod.dev | Schema definition (z.string, z.number, z.enum, z.object), parse/safeParse, refine/superRefine, default values |

**Version revision assumed:** `^3.24.0`

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `z.string().min(1)` 非空字串驗證 | Zod string validations |
| `z.number().int().positive()` 正整數驗證 | Zod number validations |
| `z.enum([...])` enum 驗證 | Zod enums |
| `z.object({...}).parse(env)` 結構化物件驗證 | Zod object schema + parse |
| `.default(value)` 提供預設值 | Zod defaults |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-010` | `ConfigSchema.parse(env)` — 啟動時驗證所有設定 | 驗證失敗時拋出 ZodError 並列出所有無效欄位 | 不假設 env vars 一定存在 |

---

### node-postgres

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| ------------------- | ----------------------- |
| https://node-postgres.com/ | Pool, PoolClient, Connection options, Error handling |

**Version revision assumed:** `^8.13.0`

#### Facts we rely on

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| `new Pool({ connectionString, max })` 建立連線池 | pg-pool docs |
| `pool.query(sql, params)` 執行查詢 | pg Client docs |
| Pool error handling（`pool.on('error', handler)`） | pg-pool error handling |

#### Integration anchors (`EXT-###`)

| ID | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| -- | ---------------------------------- | --------------- | --------------------- |
| `EXT-011` | `new Pool(config)` — 建立 PostgreSQL 連線池 | 連線失敗重試 3 次（每次延遲 2s），最終失敗 exit(1) | 不假設 pool 建立一定成功 |
