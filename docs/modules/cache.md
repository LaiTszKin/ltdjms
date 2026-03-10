# 緩存模組 (Cache Module)

## 概述

緩存模組提供統一的快取抽象層，基於 Redis 實現，為高頻查詢場景提供效能優化。

## 架構

### 核心組件

```
src/main/java/ltdjms/discord/shared/cache/
├── CacheService.java           # 緩存服務介面
├── RedisCacheService.java      # Redis 實作
├── CacheKeyGenerator.java      # 鍵生成器介面
├── DefaultCacheKeyGenerator.java # 預設鍵生成器
└── CacheInvalidationListener.java # 緩存失效監聽器
```

### 服務整合

緩存已整合到以下服務中：

- `DefaultBalanceService`：貨幣餘額查詢
- `BalanceAdjustmentService`：貨幣餘額調整後更新緩存
- `GameTokenService`：遊戲代幣查詢和調整

### 依賴注入

- `CacheModule`：提供所有緩存相關的 Dagger 依賴
- `CurrencyServiceModule`：為 `BalanceService` 和 `BalanceAdjustmentService` 注入緩存依賴
- `GameTokenServiceModule`：為 `GameTokenService` 注入緩存依賴
- `AppComponent`：暴露 `cacheService()`、`cacheKeyGenerator()`、`domainEventPublisher()`
- `CacheModule`：透過 Dagger `@IntoSet` 將 `CacheInvalidationListener` 納入統一 `DomainEventPublisher` 事件管道

## 緩存鍵格式

統一的鍵格式避免衝突並便於管理：

```
{namespace}:{entityType}:{guildId}:{userId}
```

範例：
- 貨幣餘額：`cache:balance:123456:789012`
- 遊戲代幣：`cache:gametoken:123456:789012`

## API 使用

### CacheService

```java
// 獲取緩存值
Optional<Long> balance = cacheService.get(key, Long.class);

// 設定緩存值（帶 TTL）
cacheService.put(key, 1000L, 60); // 60 秒過期

// 使緩存失效
cacheService.invalidate(key);
```

### CacheKeyGenerator

```java
String balanceKey = keyGenerator.balanceKey(guildId, userId);
String gameTokenKey = keyGenerator.gameTokenKey(guildId, userId);
```

### 事件驅動的緩存失效

`CacheInvalidationListener` 自動監聽以下事件並失效相關緩存：

- `BalanceChangedEvent`：失效貨幣餘額緩存
- `GameTokenChangedEvent`：失效遊戲代幣緩存

### 服務層緩存使用範例

貨幣餘額查詢（先查緩存，未命中則查資料庫並回寫）：

```java
// DefaultBalanceService 内部實現
Long balance = getCachedBalance(guildId, userId);
if (balance == null) {
    MemberCurrencyAccount account = accountRepository.findOrCreate(guildId, userId);
    balance = account.balance();
    putCachedBalance(guildId, userId, balance);
}
```

貨幣餘額調整（資料庫更新後同步更新緩存）：

```java
// BalanceAdjustmentService 内部實現
MemberCurrencyAccount updated = accountRepository.adjustBalance(guildId, userId, amount);
updateCachedBalance(guildId, userId, updated.balance());
eventPublisher.publish(new BalanceChangedEvent(guildId, userId, updated.balance()));
```

## 配置

### 環境變數

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `REDIS_URI` | `redis://localhost:6379` | Redis 連線 URI |

### Docker Compose

Redis 服務已加入 `docker-compose.yml`：

```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
```

### TTL 設定

| 數據類型 | TTL | 說明 |
|---------|-----|------|
| 貨幣餘額 | 300 秒 (5 分鐘) | 餘額變更頻率相對較低，配合事件失效實現最終一致性 |
| 遊戲代幣 | 300 秒 (5 分鐘) | 同上 |

## 技術決策

### 為何選擇 Lettuce？

- 非阻塞 I/O（基於 Netty），效能優於 Jedis
- 原生連線池支援
- Spring Data Redis 預設選擇，生態成熟

### 為何使用 String 序列化？

- 簡單直接，無需額外序列化框架
- Redis 原生支援
- 便於除錯（可用 `redis-cli` 直接查看）

### 緩存寫入時機

緩存在資料庫更新成功後、發布領域事件前更新：
- 確保緩存中的數據與資料庫一致
- 如果資料庫更新失敗，不會更新緩存
- 緩存失效監聽器仍然有用，作為雙重保護

### 緩存失敗降級策略

任何緩存操作失敗（連線斷開、超時等）都優雅處理，不拋出未捕獲的例外。緩存為效能優化，失敗不應導致應用崩潰。

## 測試

### 單元測試

- `CacheKeyGeneratorTest`：驗證鍵生成格式
- `CacheInvalidationListenerTest`：驗證監聽器行為（使用 Mockito）
- `DefaultBalanceServiceCacheTest`：驗證貨幣餘額緩存命中/未命中/降級場景
- `GameTokenServiceCacheTest`：驗證遊戲代幣緩存命中/未命中/更新場景

### 整合測試

- `RedisCacheServiceIntegrationTest`：驗證 Redis 操作（使用 Testcontainers）
- `CacheModuleIntegrationTest`：驗證 DI 配置
- `CacheInfrastructureIntegrationTest`：端對端驗證完整緩存基礎設施
- `PanelCacheIntegrationTest`：驗證面板緩存正確整合到服務中

## 故障排除

### Redis 連線失敗

症狀：日誌顯示 Redis 連線錯誤

處理：
1. 確認 Redis 容器正在運行：`docker ps | grep redis`
2. 檢查 Redis 健康狀態：`docker exec redis redis-cli ping`
3. 驗證 `REDIS_URI` 配置正確

**影響**：服務會自動回退到直接查詢資料庫，功能正常但效能可能下降。

### 緩存未失效

症狀：資料變更後仍看到舊值

處理：
1. 確認 `CacheInvalidationListener` 已註冊
2. 檢查事件是否正確發布
3. 驗證鍵格式一致

**注意**：由於 TTL 為 5 分鐘，即使緩存失效失敗，最壞情況下 5 分鐘後緩存會自動過期。

### 記憶體使用過高

症狀：Redis 記憶體持續增長

處理：
1. 檢查 TTL 設定是否合理
2. 使用 `redis-cli` 監控：`INFO memory`
3. 考慮設定 `maxmemory` 與淘汰策略

