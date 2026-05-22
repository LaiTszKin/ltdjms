# Spec: Cache Invalidation Listener

- Date: 2026-05-22
- Feature: cache-invalidation-listener
- Owner: [To be filled]

## Goal

將 Java `shared/cache/CacheInvalidationListener.java` 的事件驅動快取失效模式復刻到 TypeScript，確保 `BalanceChangedEvent` 和 `GameTokenChangedEvent` 發生時能自動失效對應的餘額／代幣快取條目。

## Scope

### In Scope
- 在 `packages/shared/src/infra/cache/` 建立 `CacheInvalidationListener` 類別
- 監聽 `BalanceChangedEvent` → 失效 `balance:{guildId}:{userId}` 快取
- 監聽 `GameTokenChangedEvent` → 失效 `gameToken:{guildId}:{userId}` 快取
- 在 DI 容器初始化時註冊此監聽器到 `DomainEventPublisher`
- 保留既有 service 層的直接快取失效邏輯（雙重防禦）

### Out of Scope
- 修改 `BalanceService` / `GameTokenService` / `BalanceAdjustmentService` 既有的快取失效邏輯
- 新增其他事件類型的快取失效
- 修改 `CacheService` 或 `CacheKeyGenerator` 介面

## Functional Behaviors (BDD)

### Requirement 1: Balance Changed → Cache Invalidation
**GIVEN** 已註冊 `CacheInvalidationListener` 到 `DomainEventPublisher`
**AND** Redis 快取中存在 key `balance:{guildId}:{userId}` 的快取值
**WHEN** `BalanceAdjustmentService` 執行餘額調整並發布 `BalanceChangedEvent`
**THEN** `CacheInvalidationListener` 收到事件後呼叫 `cacheService.invalidate("balance:{guildId}:{userId}")`
**AND** 下次讀取餘額時直接查詢資料庫（cache miss）

**Requirements**:
- [ ] R1.1 `CacheInvalidationListener` 實作 `(event: DomainEvent) => void` 簽名以相容 `DomainEventPublisher.register()`
- [ ] R1.2 僅在 `event.eventType === 'balance_changed'` 時觸發快取失效
- [ ] R1.3 快取失效失敗時記錄警告日誌但不拋出例外（graceful degradation）

### Requirement 2: GameToken Changed → Cache Invalidation
**GIVEN** 已註冊 `CacheInvalidationListener` 到 `DomainEventPublisher`
**AND** Redis 快取中存在 key `gameToken:{guildId}:{userId}` 的快取值
**WHEN** `GameTokenService` 執行代幣調整並發布 `GameTokenChangedEvent`
**THEN** `CacheInvalidationListener` 收到事件後呼叫 `cacheService.invalidate("gameToken:{guildId}:{userId}")`
**AND** 下次讀取代幣時直接查詢資料庫

**Requirements**:
- [ ] R2.1 僅在 `event.eventType === 'game_token_changed'` 時觸發快取失效
- [ ] R2.2 使用 `CacheKeyGenerator.gameTokenKey()` 生成快取鍵（而非手動拼接）

### Requirement 3: DI Registration
**GIVEN** 應用啟動時呼叫 `initializeContainer()`
**WHEN** 傳入 `eventListeners` 陣列包含 `CacheInvalidationListener` 實例
**THEN** `DomainEventPublisher.register()` 被呼叫
**AND** 後續所有領域事件都會經過此監聽器

**Requirements**:
- [ ] R3.1 `CacheInvalidationListener` 在建構時接受 `CacheService` 和 `CacheKeyGenerator`
- [ ] R3.2 `main.ts` 在 `initializeContainer` 時傳入監聽器實例
- [ ] R3.3 支援多個監聽器同時註冊（不影響其他監聽器）

## Error and Edge Cases
- [ ] Redis 連線中斷時，`cacheService.invalidate()` 內部已處理例外，不應導致事件處理鏈中斷
- [ ] 收到非預期格式的事件（缺少 guildId/userId 欄位）時，記錄警告並跳過
- [ ] 高頻率事件發布時（如大量餘額調整），快取失效不應造成效能瓶頸
- [ ] 監聽器本身拋出例外時，`DomainEventPublisher` 應捕獲並記錄（既有行為，不需修改）

## Clarification Questions
- 是否需要為 `CurrencyConfigChangedEvent` 和 `ProductChangedEvent` 也加入快取失效？Java 版 `CacheInvalidationListener` 僅處理 BalanceChanged 和 GameTokenChanged，建議保持 1:1
- 既有 service 層的直接快取失效邏輯是否保留？建議保留作為雙重防禦

## References
- Java 原始碼:
  - `src/main/java/ltdjms/discord/shared/cache/CacheInvalidationListener.java` — 快取失效監聽器
  - `src/main/java/ltdjms/discord/shared/cache/CacheKeyGenerator.java` — 快取鍵生成器介面
  - `src/main/java/ltdjms/discord/shared/cache/DefaultCacheKeyGenerator.java` — 預設快取鍵生成
- TypeScript 現有程式碼:
  - `packages/shared/src/infra/cache/cache-service.ts` — CacheService 介面
  - `packages/shared/src/infra/cache/cache-key-generator.ts` — CacheKeyGenerator 介面與實作
  - `packages/shared/src/infra/events/domain-event-publisher.ts` — 事件發布器
  - `packages/shared/src/types/events/index.ts` — BalanceChangedEvent, GameTokenChangedEvent 定義
  - `packages/ai/src/services/routing/agent-config-cache-invalidation-listener.ts` — 參考實作（AI 模組已有類似模式）
  - `apps/bot/src/main.ts` — 應用啟動與 DI 初始化
