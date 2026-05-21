# Tasks: Shared Infrastructure

- Date: 2026-05-20
- Feature: Shared Infrastructure

## Task 1: 定義核心型別系統 (Result + DomainError)

Purpose: 建立 `Result<T, E>` 和 `DomainError` 型別，所有後續模組的錯誤處理基礎。
Requirements: R1.1-R1.5, R2.1-R2.3
Scope: `packages/shared/src/types/result.ts`、`packages/shared/src/types/domain-error.ts`
Out of scope: 任何業務邏輯、資料庫操作

- T1.1 [ ] **`packages/shared/src/types/result.ts`** — 定義 `Result<T, E>` type（discriminated union of `Ok<T>` + `Err<E>`），提供 `ok(value)`、`err(error)`、`okVoid()` 工廠函數，以及 `isOk()`、`isErr()` type guard、`getValue()`、`getError()`、`getOrElse(defaultValue)`、`map(fn)`、`flatMap(fn)`、`mapError(fn)` 組合子
  - Verify: `npx vitest run --reporter=verbose` 相關測試全部通過（見 checklist CL-01）

- T1.2 [ ] **`packages/shared/src/types/domain-error.ts`** — 定義 `DomainErrorCategory` enum（27 個值），`DomainError` interface（`{ category, message, cause? }`），27 個靜態工廠方法（`invalidInput(msg)`、`persistenceFailure(msg, cause)` 等等）
  - Verify: `npx vitest run --reporter=verbose` 相關測試通過（見 CL-02）

- T1.3 [ ] **`packages/shared/src/types/index.ts`** — Export barrel: re-export 所有 type、interface、factory function
  - Verify: `import { Result, DomainError, ok, err } from '@ltdjms/shared'` 可成功 import

## Task 2: Config 管理

Purpose: 實現 dotenv + Zod 的 Config 管理系統，對應 Java `EnvironmentConfig.java`。
Requirements: R3.1-R3.5
Scope: `packages/shared/src/infra/config/`
Out of scope: ECPay/Shop 相關 config（屬於 shop-payment spec 依賴 shared config 基礎）

- T2.1 [ ] **`packages/shared/src/infra/config/env-loader.ts`** — 實現 `loadDotEnv(filePath)` 函數，從 `.env` 檔案讀取 key=value 配對，支援 `#` 註解、引號包圍值、值包含 `=`
  - Verify: 單元測試：測試各種 `.env` 格式（含註解、引號、特殊字元）→ 正確解析

- T2.2 [ ] **`packages/shared/src/infra/config/schema.ts`** — 定義 Zod schema (`ConfigSchema`)，包含所有 Java `EnvironmentConfig.java` 中的設定欄位: `DISCORD_BOT_TOKEN`（required）、`DATABASE_*`（host/port/name/user/password，或 `DB_URL`）、`REDIS_URI`（預設 `redis://localhost:6379`）、`AI_SERVICE_*`（baseUrl/apiKey/model/temperature/timeout）、`PROMPTS_DIR`、`MARKDOWN_VALIDATION_*`
  - Verify: Zod schema 的 `safeParse` 對有效 env 回傳 success、對缺少 token 回傳 error

- T2.3 [ ] **`packages/shared/src/infra/config/environment-config.ts`** — 實現 `EnvironmentConfig` class，初始化時執行優先級合併（`process.env` > `.env` 檔案 > Zod defaults），`parse()` 回傳 validated config 或拋出彙總錯誤。公開所有 getter 方法
  - Verify: 整合測試：給定有效 `.env` → `config.parse()` 回傳完整 config；給定缺少 token → 拋出

- T2.4 [ ] **`packages/shared/src/infra/config/index.ts`** — Export barrel
  - Verify: import 路徑正確

## Task 3: Logging 基礎設施

Purpose: 建立 pino-based logging，對應 Java 的 SLF4J + Logback。
Requirements: R7.1-R7.3
Scope: `packages/shared/src/infra/logger/`
Out of scope: 業務層的 log 調用（由各模組自行加入）

- T3.1 [ ] **`packages/shared/src/infra/logger/logger.ts`** — 建立 `createRootLogger(level)` 函數，回傳 pino Logger 實例（JSON 輸出格式），提供 `createChildLogger(parent, bindings)` 建立帶 module/context 的 child logger
  - Verify: 單元測試：驗證 logger 輸出 JSON 格式、child logger 攜帶 bindings、level 過濾正確

- T3.2 [ ] **`packages/shared/src/infra/logger/index.ts`** — Export barrel
  - Verify: import 正確

## Task 4: Database 連線與 Migration

Purpose: 使用 Drizzle ORM + node-postgres 建立 database 層，對應 Java `DatabaseConfig.java` + `DatabaseMigrationRunner.java`。
Requirements: R4.1-R4.4
Scope: `packages/shared/src/infra/database/`
Out of scope: 各業務表單的 Drizzle schema 定義（屬於各自 spec 的 tasks，本 task 只建立連線與 migration 基礎設施）

- T4.1 [ ] **`packages/shared/src/infra/database/connection.ts`** — 實現 `createDatabasePool(config)` 函數，建立 node-postgres `Pool`（max=5、connectionTimeoutMillis=5000、idleTimeoutMillis=30000），失敗時重試 3 次（每次延遲 2 秒），最終失敗拋出 `SchemaMigrationException`
  - Verify: 整合測試：給定有效 DB config → pool 成功建立且 `pool.query('SELECT 1')` 成功

- T4.2 [ ] **`packages/shared/src/infra/database/migration-runner.ts`** — 實現 `runMigrations(pool, migrationsDir)` 函數，使用 drizzle-kit 的 `migrate()` API 執行 migration。失敗時拋出 `SchemaMigrationException` 並 log 詳細錯誤
  - Verify: 整合測試：給定已 migrator 的 DB → `runMigrations` 為 no-op；給定空 DB → migration 成功執行

- T4.3 [ ] **`packages/shared/src/infra/database/schema-migration-exception.ts`** — 定義 `SchemaMigrationException` class（extends Error）
  - Verify: `throw new SchemaMigrationException('msg')` 正確被捕獲

- T4.4 [ ] **`packages/shared/src/infra/database/index.ts`** — Export barrel
  - Verify: import 正確

## Task 5: Redis 快取

Purpose: 使用 ioredis 實現 CacheService，對應 Java `RedisCacheService.java` + `CacheService.java`。
Requirements: R5.1-R5.4
Scope: `packages/shared/src/infra/cache/`
Out of scope: 業務層的快取使用

- T5.1 [ ] **`packages/shared/src/infra/cache/cache-service.ts`** — 定義 `CacheService` interface: `get<T>(key: string): Promise<T | null>`、`put(key: string, value: unknown, ttlSeconds: number): Promise<void>`、`invalidate(key: string): Promise<void>`
  - Verify: TypeScript 編譯通過，interface 可被其他模組 import

- T5.2 [ ] **`packages/shared/src/infra/cache/redis-cache-service.ts`** — 實現 `RedisCacheService`（implements CacheService），使用 ioredis `Redis` client。`get<T>`: JSON.parse + type assertion；`put`: JSON.stringify + SETEX；`invalidate`: DEL。所有方法 catch 例外並優雅降級（get 回傳 null、put/invalidate no-op）
  - Verify: 整合測試：給定 Redis 可用 → put/get/invalidate 正確；給定 Redis 不可用 → get 回傳 null、不拋例外

- T5.3 [ ] **`packages/shared/src/infra/cache/noop-cache-service.ts`** — 實現 `NoOpCacheService`（implements CacheService），`get` 永遠回傳 null、`put`/`invalidate` 為 no-op
  - Verify: 單元測試：驗證 no-op 行為

- T5.4 [ ] **`packages/shared/src/infra/cache/cache-key-generator.ts`** — 定義 `CacheKeyGenerator` interface 和 `DefaultCacheKeyGenerator` 實作: `balanceKey(guildId, userId)` → `cache:balance:{guildId}:{userId}`
  - Verify: 單元測試：驗證 key 格式

- T5.5 [ ] **`packages/shared/src/infra/cache/index.ts`** — Export barrel
  - Verify: import 正確

## Task 6: DomainEvent 事件系統

Purpose: 實現 EventEmitter-based 的 DomainEvent 發布/訂閱系統。
Requirements: R6.1-R6.4
Scope: `packages/shared/src/types/events/`、`packages/shared/src/infra/events/`
Out of scope: 具體的業務 listener 實作（屬於各模組）

- T6.1 [ ] **`packages/shared/src/types/events/domain-event.ts`** — 定義 `DomainEvent` base interface（`{ guildId: number }`），以及 12+ 具體 event type: `BalanceChangedEvent`、`GameTokenChangedEvent`、`CurrencyConfigChangedEvent`、`DiceGameConfigChangedEvent`、`ProductChangedEvent`、`RedemptionCodesGeneratedEvent`、`ProductRedemptionCompletedEvent`、`AIMessageEvent`、`AIAgentChannelConfigChangedEvent`、`AgentCompletedEvent`、`AgentFailedEvent`、`LangChain4jToolExecutionStartedEvent`、`LangChain4jToolExecutedEvent`
  - Verify: 對比 Java DomainEvent.java 的所有欄位 → TypeScript 版本完全一致（名稱、型別）

- T6.2 [ ] **`packages/shared/src/infra/events/domain-event-publisher.ts`** — 實現 `DomainEventPublisher` class。內部使用 `EventEmitter`。`register(listener: (event: DomainEvent) => void): void` 註冊監聽器；`publish(event: DomainEvent): void` 同步分發給所有 listener，每個 listener 包在 try/catch 中，例外 logged 但不傳播
  - Verify: 單元測試：兩個 listener，第一個拋錯 → 第二個仍被呼叫；publish 不拋出例外

- T6.3 [ ] **`packages/shared/src/infra/events/index.ts`** — Export barrel
  - Verify: import 正確

## Task 7: Discord.js 抽象層

Purpose: 將 discord.js 封裝在抽象層後，業務邏輯只依賴抽象介面，方便測試。
Requirements: R8.1-R8.6
Scope: `packages/shared/src/discord/`
Out of scope: Slash command 註冊邏輯（屬於 administration spec）

- T7.1 [ ] **`packages/shared/src/discord/domain/discord-interaction.ts`** — 定義 `DiscordInteraction` interface: `getGuildId()`、`getUserId()`、`getChannelId()`、`reply(content)`、`replyEmbed(embed)`、`editEmbed(embed)`、`deferReply()`、`isAcknowledged()`、`isEphemeral()`、`getHook()`
  - Verify: TypeScript 編譯通過

- T7.2 [ ] **`packages/shared/src/discord/domain/discord-context.ts`** — 定義 `DiscordContext` interface: `getGuildId()`、`getUserId()`、`getChannelId()`、`getUserMention()`、`getOption(name, type)`
  - Verify: TypeScript 編譯通過

- T7.3 [ ] **`packages/shared/src/discord/domain/discord-embed-builder.ts`** — 定義 `DiscordEmbedBuilder` interface，以及 `EmbedView`/`ButtonView` 值物件 type。builder 強制 Discord 長度限制（title ≤256、description ≤4096、fields ≤25 field、footer ≤2048）。提供 `buildPaginated(embedView)` 自動分頁
  - Verify: TypeScript 編譯通過

- T7.4 [ ] **`packages/shared/src/discord/domain/discord-runtime-gateway.ts`** — 定義 `DiscordRuntimeGateway` interface: `isReady()`、`publishReady(client)`、`requireReadyClient()`、`findGuild(guildId)`、`findGuildChannel(guildId, channelId)`、`findThreadChannel(guildId, threadId)`、`selfUserId()`
  - Verify: TypeScript 編譯通過

- T7.5 [ ] **`packages/shared/src/discord/domain/embed-view.ts`** — 定義 `EmbedView` type（title, description, color, fields, footer）和 `ButtonView` type（id, label, style, disabled）以及 `FieldView` type（name, value, inline）。提供 `toDiscordJsEmbed()` 和 `toDiscordJsButton()` 轉換函數
  - Verify: 單元測試：驗證 EmbedView 轉換為 discord.js EmbedBuilder 後符合長度限制

- T7.6 [ ] **`packages/shared/src/discord/domain/index.ts`** — Export barrel for domain types
  - Verify: import 正確

- T7.7 [ ] **`packages/shared/src/discord/services/discord-js-interaction.ts`** — 實現 `DiscordInteraction`（包裝 discord.js `CommandInteraction` / `ButtonInteraction` / `ModalSubmitInteraction`），使用 adapter pattern 從 JDA 事件轉換為統一的 `DiscordInteraction`
  - Verify: 單元測試（使用 Mock discord.js interaction）驗證 reply/edit/deferReply 操作

- T7.8 [ ] **`packages/shared/src/discord/services/discord-js-context.ts`** — 實現 `DiscordContext`（包裝 discord.js interaction）
  - Verify: 單元測試：驗證 guildId/userId/channelId/userMention 提取正確

- T7.9 [ ] **`packages/shared/src/discord/services/discord-js-embed-builder.ts`** — 實現 `DiscordEmbedBuilder`，用 discord.js `EmbedBuilder` 建構 embed，強制長度限制。實現 `buildPaginated()` 自動分頁
  - Verify: 單元測試：驗證超長 title/description 被截斷；分頁邏輯正確

- T7.10 [ ] **`packages/shared/src/discord/services/discord-js-runtime-gateway.ts`** — 實現 `DiscordRuntimeGateway`，使用 `AtomicRef<Client>` pattern。`publishReady(client)` 設定一次，重複呼叫拋出 `IllegalStateException`
  - Verify: 單元測試：publishReady 前呼叫 requireReadyClient 拋出例外；publishReady 後正確回傳 client

- T7.11 [ ] **`packages/shared/src/discord/services/select-menu-util.ts`** — 實現 `splitSelectMenus(options, selectId)` 函數，將超過 25 個選項的選單自動分割為多個 `StringSelectMenu`
  - Verify: 單元測試：30 選項 → 2 個選單（25+5）；50 選項 → 2 個選單（25+25）

- T7.12 [ ] **`packages/shared/src/discord/mock/mock-discord-interaction.ts`** — 實現 MockDiscordInteraction（記錄所有 reply/edit/defer 呼叫，提供 getter 供測試驗證）
  - Verify: 單元測試：驗證 mock 可正確記錄 reply 呼叫

- T7.13 [ ] **`packages/shared/src/discord/mock/mock-discord-context.ts`** — 實現 MockDiscordContext（建構子接受 guildId/userId/channelId/userMention，支援 `setOption()` 測試輔助）
  - Verify: TypeScript 編譯通過

- T7.14 [ ] **`packages/shared/src/discord/mock/mock-discord-embed-builder.ts`** — 實現 MockDiscordEmbedBuilder（記錄 builder 呼叫，提供 getter 供測試斷言）
  - Verify: TypeScript 編譯通過

- T7.15 [ ] **`packages/shared/src/discord/index.ts`** — Export barrel
  - Verify: import 正確

## Task 8: DI 容器組裝

Purpose: 使用 tsyringe 將所有 shared infrastructure 服務註冊到 DI 容器。
Requirements: R9.1-R9.4
Scope: `packages/shared/src/infra/di/`、`packages/shared/src/index.ts`
Out of scope: 業務模組的 DI 註冊

- T8.1 [ ] **`packages/shared/src/infra/di/container.ts`** — 建立 tsyringe `container` 實例，註冊所有 shared 服務為 singleton: `EnvironmentConfig`、`DatabasePool`、`CacheService`（Redis 或 NoOp）、`DomainEventPublisher`、`Logger`、`DiscordEmbedBuilder`、`DiscordRuntimeGateway`、`CacheKeyGenerator`
  - Verify: 整合測試：`container.resolve(EnvironmentConfig)` 回傳同一實例

- T8.2 [ ] **`packages/shared/src/infra/di/tokens.ts`** — 定義所有 injection token（用於 interface-based injection）
  - Verify: TypeScript 編譯通過

- T8.3 [ ] **`packages/shared/src/index.ts`** — 頂層 export barrel: re-export types + infra + discord。確保 `@ltdjms/shared` 的 consumer 可以 import `{ Result, DomainError, Config, CacheService, DomainEventPublisher, DiscordInteraction, ... }`
  - Verify: TypeScript 專案中 `import { Result } from '@ltdjms/shared'` 成功

## Task 9: 根層級啟動程式

Purpose: 實現 `main.ts`——應用程式的進入點，對應 Java `DiscordCurrencyBot.java` 的啟動序列。
Requirements: R3, R4, R8, R9（跨需求整合）
Scope: `packages/admin/src/main.ts`（實際實作位置；T9.1 原定義於 shared，但啟動程式依賴所有模組的 DI 初始化，故放於 admin 套件）
Out of scope: 具體的 command handler 註冊（各模組自行註冊）

- T9.1 [ ] **`packages/admin/src/main.ts`** — 實現 `main()` 非同步函數。啟動序列: 1) 載入 Config (`EnvironmentConfig.parse()`)、2) 建立 Logger、3) 建立 Database pool + 執行 migration、4) 建立 Redis client、5) 建立 DomainEventPublisher、6) 建立 discord.js Client、7) 設定 DiscordRuntimeGateway (`publishReady`)、8) `client.login(token)`、9) 註冊 shutdown hook（SIGTERM → 優雅關閉: stop HTTP server、close DB pool、close Redis、destroy discord client）
  - Verify: 整合測試：給定完整 .env + 可連線 DB/Redis → `main()` 成功啟動，`client.user.tag` 非空
