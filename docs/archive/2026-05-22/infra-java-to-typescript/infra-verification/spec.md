# Spec: Infrastructure Verification Suite

- Date: 2026-05-22
- Feature: infra-verification
- Owner: [To be filled]

## Goal

建立全面的基建驗證測試套件，確保每個 TypeScript 基建組件的行為與 Java 對應組件完全一致，達成 1:1 功能還原的驗收目標。

## Scope

### In Scope
- 為每個基建組件撰寫行為驗證測試（contract test / behavior test）
- 驗證快取層：CacheService、RedisCacheService、CacheKeyGenerator
- 驗證設定層：EnvironmentConfig、EnvLoader、Schema
- 驗證資料庫層：Connection pool、Migration runner
- 驗證事件層：DomainEventPublisher、所有 DomainEvent 類型
- 驗證型別層：Result、DomainError、Unit
- 驗證工具層：Snowflake ID 生成、Concurrency
- 針對已知的 Java/TS 差異撰寫 drift check

### Out of Scope
- 各業務模組（economy, shop, dispatch, ai）的功能測試
- 效能/負載測試
- Discord API 的 E2E 測試
- Java 端程式碼的修改

## Functional Behaviors (BDD)

### Requirement 1: 快取層行為驗證
**GIVEN** TypeScript `CacheService` / `RedisCacheService` 已實作
**WHEN** 執行快取操作（get / put / invalidate）
**THEN** 行為與 Java `CacheService` / `RedisCacheService` 一致：
  - `get` 不存在的 key 回傳 `null`（非拋出例外）
  - `put` 後 `get` 能取回相同值
  - TTL 過期後 `get` 回傳 `null`
  - `invalidate` 後 `get` 回傳 `null`
  - 連線失敗時 `get` 回傳 `null`（graceful degradation）

**Requirements**:
- [ ] R1.1 CacheService 介面方法簽名與 Java 一致
- [ ] R1.2 RedisCacheService CRUD 行為正確
- [ ] R1.3 Redis 不可用時 graceful degradation

### Requirement 2: 設定層行為驗證
**GIVEN** TypeScript `EnvironmentConfig` 已實作
**WHEN** 讀取各種設定值
**THEN** 優先級順序與 Java 一致：系統環境變數 > .env > 預設值
**AND** 必要設定缺失時拋出明確錯誤

**Requirements**:
- [ ] R2.1 環境變數優先於 .env 檔案值
- [ ] R2.2 .env 檔案值優先於預設值
- [ ] R2.3 `getDiscordBotToken()` 缺失時拋出錯誤
- [ ] R2.4 `getAIServiceApiKey()` 缺失時拋出錯誤

### Requirement 3: 資料庫層行為驗證
**GIVEN** TypeScript database connection 和 migration runner 已實作
**WHEN** 執行 migration
**THEN** Migration runner 行為與 Java Flyway runner 一致：
  - 空白資料庫：依序套用所有 migration
  - 已有 migration 的資料庫：只套用未執行的 migration
  - 已有 table 但無 tracking table：baseline 現有 migration

**Requirements**:
- [ ] R3.1 連線池建立含重試邏輯（最多 3 次）
- [ ] R3.2 Migration 的 baseline 偵測邏輯正確
- [ ] R3.3 Migration 失敗時拋出 `SchemaMigrationException`

### Requirement 4: 事件層行為驗證
**GIVEN** TypeScript `DomainEventPublisher` 已實作
**WHEN** 發布事件
**THEN** 所有已註冊監聽器被同步呼叫
**AND** 個別監聽器失敗不影響其他監聽器

**Requirements**:
- [ ] R4.1 事件同步分發給所有監聽器
- [ ] R4.2 監聽器例外被捕獲且不中斷其他監聽器
- [ ] R4.3 所有 DomainEvent 類型的 eventType 判別值與 Java 一致

### Requirement 5: 型別層行為驗證
**GIVEN** TypeScript `Result<T, E>` 和 `DomainError` 已實作
**WHEN** 使用 Result 進行錯誤處理
**THEN** `map`、`flatMap`、`mapError` 行為與 Java `Result` 一致
**AND** `DomainError` 的所有 category 和 factory method 與 Java 對應

**Requirements**:
- [ ] R5.1 `Ok.map(fn)` 正確轉換值
- [ ] R5.2 `Err.map(fn)` 保持錯誤不變
- [ ] R5.3 `flatMap` 支援鏈式操作
- [ ] R5.4 `mapError` 正確轉換錯誤類型
- [ ] R5.5 DomainError factory methods 回傳正確 category

## Error and Edge Cases
- [ ] 測試環境需有獨立的 PostgreSQL 和 Redis 實例（可用 testcontainers 或 Docker Compose）
- [ ] 驗證測試不依賴外部服務（Discord API 等）
- [ ] 測試應可重複執行（idempotent）

## Clarification Questions
- 是否需要跨 Java/TypeScript 的對照測試（同時執行 Java 和 TS 相同操作並比對結果）？建議先以 TS 端的行為測試為主，必要時再加入跨語言對照
- V028 migration 僅存在 Java 端還是兩邊都有？TS 端已確認有 V028，兩邊同步

## References
- Java 原始碼參考:
  - `src/main/java/ltdjms/discord/shared/cache/` — 快取層
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java` — 設定
  - `src/main/java/ltdjms/discord/shared/DatabaseConfig.java` — 資料庫
  - `src/main/java/ltdjms/discord/shared/DatabaseMigrationRunner.java` — Migration
  - `src/main/java/ltdjms/discord/shared/events/DomainEventPublisher.java` — 事件
  - `src/main/java/ltdjms/discord/shared/Result.java` — Result 型別
  - `src/main/java/ltdjms/discord/shared/DomainError.java` — DomainError
- TypeScript 現有測試:
  - `packages/shared/src/__tests__/infra-smoke.test.ts` — 現有 smoke test
  - `packages/shared/src/__tests__/snowflake.test.ts` — Snowflake 測試
  - `packages/shared/src/infra/config/__tests__/` — 設定層測試
  - `packages/shared/src/infra/events/__tests__/` — 事件層測試
  - `packages/shared/src/infra/logger/__tests__/` — Logger 測試
  - `packages/shared/src/types/__tests__/` — 型別測試
