# Spec: Shared Infrastructure

- Date: 2026-05-20
- Feature: Shared Infrastructure
- Owner: [To be filled]

## Goal

建立 TypeScript 專案的完整基礎設施層，包括型別系統、組態管理、資料庫層、快取、事件系統、日誌、Discord 抽象層與 DI 容器，使所有功能模組能在共通的、型別安全的基礎上開發。

## Scope

### In Scope

- `Result<T, E>` 與 `DomainError` 型別系統（對應 Java `Result.java`、`DomainError.java`）
- `EnvironmentConfig` 組態管理（dotenv + Zod schema 驗證，對應 Java `EnvironmentConfig.java`、`DotEnvLoader.java`）
- Database 連線管理與 migration runner（Drizzle ORM + node-postgres，對應 Java `DatabaseConfig.java`、`DatabaseMigrationRunner.java`）
- Redis 快取服務（ioredis，對應 Java `RedisCacheService.java`）
- DomainEvent 事件系統（EventEmitter 為基礎的同步分發，對應 Java `DomainEventPublisher.java`）
- Logging 基礎設施（pino，對應 SLF4J + Logback）
- Discord.js 抽象層（DiscordInteraction、DiscordContext、DiscordEmbedBuilder、DiscordRuntimeGateway 介面及實作）
- DI 容器設定（tsyringe，對應 Dagger 的 `AppComponent` 與各 Module）
- 所有 DomainEvent 具體型別定義（12+ event records）
- 所有 DomainError 分類定義（27 categories）

### Out of Scope

- 任何業務邏輯（貨幣、代幣、商店、派單、AI、面板）
- 資料庫 schema 定義（schema 參考已存在於 preparation P3）
- Discord slash command 註冊邏輯
- HTTP server（ECPay callback server 屬於 shop-payment spec）

## Functional Behaviors (BDD)

### Requirement 1: Result<T, E> 型別系統
**GIVEN** 一個可能失敗的函數呼叫
**WHEN** 函數回傳 `Result.ok(value)` 或 `Result.err(error)`
**THEN** 呼叫端可以使用 `isOk()` / `isErr()` 判斷結果
**AND** 可以使用 `map()` / `flatMap()` / `mapError()` 進行函數組合
**AND** `getValue()` 在 `isErr()` 為 true 時拋出錯誤
**AND** `getError()` 在 `isOk()` 為 true 時拋出錯誤

**Requirements**:
- [ ] R1.1 Result<T, E> 支援 `ok(value)`、`err(error)` 工廠方法
- [ ] R1.2 Result 支援 `isOk()`、`isErr()`、`getValue()`、`getError()`、`getOrElse(defaultValue)`
- [ ] R1.3 Result 支援 `map(fn)`、`flatMap(fn)`、`mapError(fn)` 函數組合
- [ ] R1.4 `ok()` 和 `err()` 在建構時拒絕 null/undefined
- [ ] R1.5 提供 `Result.okVoid()` 用於無回傳值的成功場景

### Requirement 2: DomainError 分類系統
**GIVEN** 業務邏輯中發生錯誤
**WHEN** 建立 `DomainError` 實例
**THEN** 必須包含 `category`（enum）、`message`（string）、`cause`（optional Error）
**AND** 提供 27 個分類的靜態工廠方法

**Requirements**:
- [ ] R2.1 DomainError 包含 Category enum（27 個值，與 Java 版本完全一致）
- [ ] R2.2 提供 `DomainError.invalidInput(msg)`、`DomainError.persistenceFailure(msg, cause)` 等工廠方法
- [ ] R2.3 Category 涵蓋: INVALID_INPUT、INSUFFICIENT_BALANCE、INSUFFICIENT_TOKENS、PERSISTENCE_FAILURE、UNEXPECTED_FAILURE、DISCORD_*（7 項）、AI_SERVICE_*（6 項）、PROMPT_*（5 項）、CHANNEL_NOT_ALLOWED、DUPLICATE_CHANNEL、INSUFFICIENT_PERMISSIONS、CHANNEL_NOT_FOUND、DUPLICATE_CATEGORY、CATEGORY_NOT_FOUND

### Requirement 3: Config 管理
**GIVEN** 應用程式啟動
**WHEN** 載入設定
**THEN** 優先級為: 系統環境變數 > `.env` 檔案 > 內建預設值
**AND** 所有值透過 Zod schema 驗證型別正確性
**AND** Discord bot token 為必要欄位，缺少時拋出錯誤阻止啟動

**Requirements**:
- [ ] R3.1 支援從 `.env` 檔案讀取 key-value 配對（支援 `#` 註解、引號值、含 `=` 的值）
- [ ] R3.2 所有設定 key 與 Java `EnvironmentConfig.java` 完全一致
- [ ] R3.3 Zod schema 驗證所有設定值的型別與範圍
- [ ] R3.4 ECPay 相關設定支援 stage/production 模式切換
- [ ] R3.5 提供型別安全的 `getConfig()` 回傳推斷型別

### Requirement 4: Database 連線與 Migration
**GIVEN** 有效的 PostgreSQL 連線資訊
**WHEN** 應用程式初始化
**THEN** Drizzle ORM 建立 connection pool
**AND** 執行待處理的 schema migration
**AND** migration 檔案與 Java Flyway migration SQL 產出的 schema 完全一致

**Requirements**:
- [ ] R4.1 使用 `node-postgres` Pool 管理連線（對應 HikariCP 的連線池）
- [ ] R4.2 Drizzle ORM schema 定義涵蓋所有 18 張資料表
- [ ] R4.3 Migration runner 支援 baseline 機制（對應 Flyway baselineOnMigrate）
- [ ] R4.4 Migration 執行失敗時拋出 `SchemaMigrationException` 並阻止啟動

### Requirement 5: Redis 快取
**GIVEN** Redis 服務可用
**WHEN** 呼叫 `cacheService.put(key, value, ttlSeconds)`
**THEN** 值被寫入 Redis 並設定 TTL
**AND** `cacheService.get(key)` 在 TTL 內回傳該值
**AND** `cacheService.invalidate(key)` 移除快取
**AND** Redis 連線失敗時優雅降級（不回傳例外、get 回傳 null）

**Requirements**:
- [ ] R5.1 CacheService 介面: `get<T>(key)`、`put(key, value, ttlSeconds)`、`invalidate(key)`
- [ ] R5.2 Redis 實作使用 ioredis，支援 JSON 序列化
- [ ] R5.3 NoOpCacheService 用於測試或停用快取時
- [ ] R5.4 CacheKeyGenerator: `balanceKey(guildId, userId)`、`gameTokenKey(guildId, userId)`

### Requirement 6: DomainEvent 事件系統
**GIVEN** 業務操作完成
**WHEN** 發布 DomainEvent
**THEN** 所有已註冊的 listener 依序被呼叫
**AND** 單一 listener 的例外不影響其他 listener
**AND** 事件攜帶 `guildId` 作為必要欄位

**Requirements**:
- [ ] R6.1 DomainEventPublisher: `register(listener)`、`publish(event)` 同步分發
- [ ] R6.2 Listener 例外被捕獲並 logged，不向上傳播
- [ ] R6.3 所有 12+ 具體 event 型別與 Java 版本欄位一致
- [ ] R6.4 DI 容器支援 multi-registration of listeners

### Requirement 7: Logging
**GIVEN** 應用程式運行中
**WHEN** 記錄日誌
**THEN** 使用 pino logger，輸出 JSON 格式
**AND** 支援 level: trace/debug/info/warn/error
**AND** 支援 child logger（帶 context 如 module name）

**Requirements**:
- [ ] R7.1 pino logger 實例可透過 DI 注入
- [ ] R7.2 支援 structured logging（key=value pairs）
- [ ] R7.3 生產環境預設 info level，開發環境預設 debug level

### Requirement 8: Discord.js 抽象層
**GIVEN** Discord 事件觸發（slash command、button click、modal submit）
**WHEN** 透過抽象層處理
**THEN** 業務邏輯不直接依賴 discord.js 型別
**AND** DiscordInteraction 提供 `reply()`、`replyEmbed()`、`editEmbed()`、`deferReply()`
**AND** DiscordContext 提供 `guildId`、`userId`、`channelId`、`userMention`
**AND** DiscordEmbedBuilder 強制 Discord API 長度限制（title 256、description 4096、fields 25、footer 2048）
**AND** DiscordRuntimeGateway 在 JDA ready 後提供 guild/channel/user 查詢

**Requirements**:
- [ ] R8.1 DiscordInteraction 介面對應 Java `DiscordInteraction.java`
- [ ] R8.2 DiscordContext 介面對應 Java `DiscordContext.java`
- [ ] R8.3 DiscordEmbedBuilder 介面對應 Java `DiscordEmbedBuilder.java`
- [ ] R8.4 提供 Mock 實作（MockDiscordInteraction、MockDiscordContext、MockDiscordEmbedBuilder）用於單元測試
- [ ] R8.5 EmbedView 和 ButtonView 值物件支援 paginated embed 建構
- [ ] R8.6 SelectMenuUtil 處理超過 25 選項的自動分割

### Requirement 9: DI 容器
**GIVEN** 應用程式啟動
**WHEN** 建立 DI 容器
**THEN** 所有服務單例可被解析
**AND** 依賴關係自動注入
**AND** 生命週期管理正確（singleton 預設）

**Requirements**:
- [ ] R9.1 使用 tsyringe 作為 DI 容器
- [ ] R9.2 所有服務以 `@singleton()` 註冊
- [ ] R9.3 支援 `@inject()` 裝飾器指定依賴
- [ ] R9.4 支援 multi-registration（`@injectAll()` 用於 event listeners）

## Error and Edge Cases

- [ ] Database 連線失敗時拋出明確錯誤並阻止啟動
- [ ] Redis 不可用時快取操作優雅降級（不拋例外）
- [ ] Config 缺少必要欄位時拋出錯誤並列出缺失項目
- [ ] Embed builder 在內容超長時自動截斷並 logged warning
- [ ] DomainEvent listener 拋出例外時不影響其他 listener
- [ ] DI 容器解析循環依賴時拋出明確錯誤訊息
- [ ] Migration 執行期間資料庫連線中斷時正確回報錯誤

## Clarification Questions

None（所有需求基於現有 Java 程式碼的明確行為）

## References

- Official docs:
  - discord.js v14: https://discord.js.org/docs
  - Drizzle ORM: https://orm.drizzle.team/docs/overview
  - tsyringe: https://github.com/microsoft/tsyringe
  - pino: https://getpino.io
  - ioredis: https://github.com/redis/ioredis
  - Vitest: https://vitest.dev
- Related Java code files:
  - `src/main/java/ltdjms/discord/shared/Result.java`
  - `src/main/java/ltdjms/discord/shared/DomainError.java`
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/java/ltdjms/discord/shared/DotEnvLoader.java`
  - `src/main/java/ltdjms/discord/shared/DatabaseConfig.java`
  - `src/main/java/ltdjms/discord/shared/DatabaseMigrationRunner.java`
  - `src/main/java/ltdjms/discord/shared/cache/RedisCacheService.java`
  - `src/main/java/ltdjms/discord/shared/cache/CacheService.java`
  - `src/main/java/ltdjms/discord/shared/events/DomainEventPublisher.java`
  - `src/main/java/ltdjms/discord/shared/events/DomainEvent.java`
  - `src/main/java/ltdjms/discord/discord/domain/DiscordInteraction.java`
  - `src/main/java/ltdjms/discord/discord/domain/DiscordContext.java`
  - `src/main/java/ltdjms/discord/discord/domain/DiscordEmbedBuilder.java`
  - `src/main/java/ltdjms/discord/shared/di/AppComponent.java`
  - `src/main/java/ltdjms/discord/shared/di/DatabaseModule.java`
