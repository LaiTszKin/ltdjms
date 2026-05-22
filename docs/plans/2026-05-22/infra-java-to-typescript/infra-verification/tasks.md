# Tasks: Infrastructure Verification Suite

- Date: 2026-05-22
- Feature: infra-verification

## **Task 1: 快取層驗證測試**

Purpose: 驗證 CacheService / RedisCacheService / CacheKeyGenerator 行為與 Java 一致
Requirements: R1.1, R1.2, R1.3
Scope: `packages/shared/src/__tests__/` (新建或擴充)
Out of scope: 不修改快取層實作

- T1.1 [ ] **`packages/shared/src/__tests__/cache-service.contract.test.ts`** — CacheService 合約測試：
  - NoOpCacheService: `get` always returns null, `put`/`invalidate` are no-ops
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "CacheService contract"`

- T1.2 [ ] **`packages/shared/src/__tests__/redis-cache-service.test.ts`** — RedisCacheService 整合測試：
  - `put` then `get` returns same value
  - `get` non-existent key returns null
  - `put` with TTL → key expires after TTL
  - `invalidate` removes key
  - `get` / `put` / `invalidate` don't throw when Redis is unavailable
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "RedisCacheService"`

- T1.3 [ ] **`packages/shared/src/__tests__/cache-key-generator.test.ts`** — CacheKeyGenerator 測試：
  - `balanceKey(guildId, userId)` 回傳 `balance:{guildId}:{userId}`
  - `gameTokenKey(guildId, userId)` 回傳 `gameToken:{guildId}:{userId}`
  - 鍵格式與 Java `DefaultCacheKeyGenerator` 一致
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "CacheKeyGenerator"`

## **Task 2: 設定層驗證測試**

Purpose: 驗證 EnvironmentConfig / EnvLoader / Schema 行為與 Java 一致
Requirements: R2.1-R2.4
Scope: `packages/shared/src/infra/config/__tests__/` (擴充)
Out of scope: 不修改設定層實作

- T2.1 [ ] **`packages/shared/src/infra/config/__tests__/environment-config.test.ts`** — 擴充現有測試：
  - 系統環境變數覆蓋 .env 值
  - .env 值覆蓋預設值
  - `getDiscordBotToken()` 缺失時拋出
  - `getAIServiceApiKey()` 缺失時拋出
  - `getDatabaseUrl()` 支援 DATABASE_HOST/PORT/NAME 組合模式
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "EnvironmentConfig"`

- T2.2 [ ] **`packages/shared/src/infra/config/__tests__/env-loader.test.ts`** — 擴充：
  - 註解行 (#) 被忽略
  - 空行被忽略
  - 單引號和雙引號值正確剝離
  - 值中的 = 符號正確保留
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "EnvLoader"`

## **Task 3: 資料庫層驗證測試**

Purpose: 驗證 Connection pool 和 Migration runner 行為與 Java 一致
Requirements: R3.1-R3.3
Scope: `packages/shared/src/__tests__/` (新建)
Out of scope: 不修改資料庫層實作

- T3.1 [ ] **`packages/shared/src/__tests__/database-connection.test.ts`** — 連線池測試：
  - 無效 URL 時重試 3 次後拋出 `SchemaMigrationException`
  - 有效 URL 時成功建立連線池
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "createDatabasePool"`

- T3.2 [ ] **`packages/shared/src/__tests__/migration-runner.test.ts`** — Migration runner 測試：
  - 空白資料庫：依序套用所有 migration
  - 已套用部分：只套用未執行的 migration
  - 有既有 table 但無 tracking table：baseline 並跳過
  - Migration SQL 語法錯誤時拋出 `SchemaMigrationException`
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "runMigrations"`

## **Task 4: 事件層驗證測試**

Purpose: 驗證 DomainEventPublisher 和所有事件類型與 Java 一致
Requirements: R4.1-R4.3
Scope: `packages/shared/src/infra/events/__tests__/` (擴充)
Out of scope: 不修改事件層實作

- T4.1 [ ] **`packages/shared/src/infra/events/__tests__/event-publisher.test.ts`** — 擴充：
  - 事件同步分發給所有監聽器
  - 監聽器拋出例外不影響其他監聽器
  - 無監聽器時 publish 不拋出例外
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "DomainEventPublisher"`

- T4.2 [ ] **`packages/shared/src/__tests__/domain-events.test.ts`** — 事件類型驗證：
  - 每個事件類型的 `eventType` 字面值與 Java 對應事件一致
  - 事件介面欄位與 Java 對應事件一致
  - Verify: `make build` 通過（型別檢查）

## **Task 5: 型別層驗證測試**

Purpose: 驗證 Result / DomainError / Unit 行為與 Java 一致
Requirements: R5.1-R5.5
Scope: `packages/shared/src/types/__tests__/` (擴充)
Out of scope: 不修改型別層實作

- T5.1 [ ] **`packages/shared/src/types/__tests__/result.test.ts`** — 擴充：
  - `ok(value).map(fn)` 回傳 `ok(fn(value))`
  - `err(error).map(fn)` 回傳相同 err
  - `flatMap` 鏈：ok → ok 和 ok → err 和 err 不變
  - `mapError` 正確轉換錯誤
  - `okVoid()` 回傳 `ok(Unit.INSTANCE)`
  - `getOrElse` 在 Ok 回傳值、Err 回傳預設值
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "Result"`

- T5.2 [ ] **`packages/shared/src/types/__tests__/domain-error.test.ts`** — 擴充：
  - 每個 static factory method 回傳正確的 `DomainErrorCategory`
  - `DomainError` 的 `message` 和 `cause` 正確儲存
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "DomainError"`

## **Task 6: 工具層驗證測試**

Purpose: 驗證 Snowflake 和 Concurrency 工具函數
Requirements: 工具層的正確性驗證
Scope: `packages/shared/src/__tests__/` (擴充)
Out of scope: 不修改工具層實作

- T6.1 [ ] **`packages/shared/src/__tests__/snowflake.test.ts`** — 確認現有 Snowflake 測試覆蓋：
  - ID 唯一性
  - ID 為正整數
  - 時間戳組件正確
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "Snowflake"`

- T6.2 [ ] **`packages/shared/src/__tests__/concurrency.test.ts`** — Concurrency 工具測試（如不存在）：
  - 驗證 `Claim` / `Release` 模式的正確性
  - 驗證並發情境下的行為
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "Concurrency"`

## **Task 7: 基建 Smoke Test**

Purpose: 確認所有基建組件能協同運作
Requirements: 跨組件整合
Scope: `packages/shared/src/__tests__/infra-smoke.test.ts` (確認/擴充)
Out of scope: 不修改任何實作

- T7.1 [ ] **`packages/shared/src/__tests__/infra-smoke.test.ts`** — 確認現有 smoke test 覆蓋完整啟動流程
  - 若覆蓋不足，擴充測試
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "smoke"`
