# Tasks: Cache Invalidation Listener

- Date: 2026-05-22
- Feature: cache-invalidation-listener

## **Task 1: 建立 CacheInvalidationListener 類別**

Purpose: 在 `packages/shared/src/infra/cache/` 建立事件驅動的快取失效監聽器，對齊 Java `CacheInvalidationListener`
Requirements: R1.1, R1.2, R1.3, R2.1, R2.2
Scope: `packages/shared/src/infra/cache/cache-invalidation-listener.ts` (新建)
Out of scope: 不修改既有 service 層的快取失效邏輯

- T1.1 [ ] **`packages/shared/src/infra/cache/cache-invalidation-listener.ts`** — 新建 `CacheInvalidationListener` 類別：
  - 建構子接受 `CacheService` 和 `CacheKeyGenerator` 兩個依賴
  - 實作 `onEvent(event: DomainEvent): void` 方法
  - 使用 discriminated union 檢查 `event.eventType`
  - `balance_changed` → 呼叫 `cacheKeyGenerator.balanceKey()` 生成鍵 → `cacheService.invalidate(key)`
  - `game_token_changed` → 呼叫 `cacheKeyGenerator.gameTokenKey()` 生成鍵 → `cacheService.invalidate(key)`
  - 其他事件類型直接忽略（no-op）
  - 快取失效失敗時 catch 例外並記錄 warning 日誌（graceful degradation）
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "CacheInvalidationListener"`

- T1.2 [ ] **`packages/shared/src/infra/cache/index.ts`** — 匯出 `CacheInvalidationListener` 類別
  - Verify: `make build` 通過

- T1.3 [ ] **`packages/shared/src/index.ts`** — 在 shared 套件的公開 API 中匯出 `CacheInvalidationListener`
  - Verify: `make build` 通過

## **Task 2: 在 DI 容器初始化時註冊監聽器**

Purpose: 確保應用啟動時 `CacheInvalidationListener` 被註冊到 `DomainEventPublisher`
Requirements: R3.1, R3.2, R3.3
Scope: `apps/bot/src/main.ts`
Out of scope: 不修改 `initializeContainer()` 的簽名

- T2.1 [ ] **`apps/bot/src/main.ts`** — 在 `initializeContainer({ eventListeners })` 中加入 `CacheInvalidationListener` 實例：
  - 從 `@ltdjms/shared` import `CacheInvalidationListener`
  - 建立 `new CacheInvalidationListener(cacheService, new DefaultCacheKeyGenerator())` 並傳入 `eventListeners` 陣列
  - Verify: `make build` 通過；啟動 bot 後檢查日誌確認監聽器已註冊

## **Task 3: 撰寫單元測試**

Purpose: 驗證 `CacheInvalidationListener` 的行為與 Java 端一致
Requirements: R1.x, R2.x
Scope: `packages/shared/src/__tests__/` (新建測試檔)
Out of scope: 不測試既有 service 層的快取失效邏輯

- T3.1 [ ] **`packages/shared/src/__tests__/cache-invalidation-listener.test.ts`** — 撰寫單元測試：
  - Test: `BalanceChangedEvent` 觸發時呼叫 `cacheService.invalidate` 且鍵格式為 `balance:{guildId}:{userId}`
  - Test: `GameTokenChangedEvent` 觸發時呼叫 `cacheService.invalidate` 且鍵格式為 `gameToken:{guildId}:{userId}`
  - Test: 非相關事件類型（如 `CurrencyConfigChangedEvent`）不觸發快取失效
  - Test: `cacheService.invalidate` 拋出例外時不向上傳播（catch 並 log）
  - Test: 事件缺少 guildId / userId 欄位時不觸發快取失效（graceful handling）
  - 使用 vitest mock 模擬 `CacheService` 和 `CacheKeyGenerator`
  - Verify: `pnpm vitest run --project @ltdjms/shared -t "CacheInvalidationListener"`
