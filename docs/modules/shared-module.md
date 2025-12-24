# 共用模組設計與實作

本文件說明 `ltdjms.discord.shared` 模組中的核心共用元件，包括設定管理、錯誤處理、依賴注入與領域事件系統。

## 1. 模組結構總覽

```
src/main/java/ltdjms/discord/shared/
├── EnvironmentConfig.java      # 環境設定載入
├── DotEnvLoader.java           # .env 檔案讀取
├── DatabaseConfig.java         # 資料庫配置
├── Result.java                 # Result<T, E> 類型
├── DomainError.java            # 領域錯誤
├── Unit.java                   # Void 標記類型
├── DatabaseMigrationRunner.java # Flyway 遷移執行
├── SchemaMigrationException.java # 遷移異常
├── JooqDSLContextFactory.java  # jOOQ DSL 工廠
├── localization/               # 本地化訊息
│   ├── DiceGameMessages.java
│   └── CommandLocalizations.java
├── di/                         # Dagger 依賴注入
│   ├── AppComponent.java       # 主 DI 組件
│   ├── AppComponentFactory.java
│   ├── DatabaseModule.java
│   ├── CurrencyRepositoryModule.java
│   ├── CurrencyServiceModule.java
│   ├── GameTokenRepositoryModule.java
│   ├── GameTokenServiceModule.java
│   ├── ProductRepositoryModule.java
│   ├── ProductServiceModule.java
│   ├── CommandHandlerModule.java
│   └── EventModule.java
└── events/                     # 領域事件
    ├── DomainEvent.java        # 事件基底介面
    ├── DomainEventPublisher.java
    ├── BalanceChangedEvent.java
    ├── GameTokenChangedEvent.java
    ├── CurrencyConfigChangedEvent.java
    ├── DiceGameConfigChangedEvent.java
    ├── ProductChangedEvent.java
    └── RedemptionCodesGeneratedEvent.java
```

## 2. 設定管理

### 2.1 EnvironmentConfig

`EnvironmentConfig` 是設定管理的核心類別，負責從多個來源載入設定並合併。

```java
// src/main/java/ltdjms/discord/shared/EnvironmentConfig.java
public final class EnvironmentConfig {
    // 優先順序：系統環境變數 > .env > application.conf > 內建預設值
}
```

**主要功能：**

- 載入 `.env` 檔案（支援自訂目錄）
- 整合 Typesafe Config 系統
- 支援環境變數覆寫
- 提供型別安全的設定值取得方法

**取得方式：**

```java
EnvironmentConfig config = new EnvironmentConfig();
String token = config.getDiscordBotToken();
String dbUrl = config.getDatabaseUrl();
int poolSize = config.getPoolMaxSize();
```

### 2.2 DotEnvLoader

負責從指定目錄讀取 `.env` 檔案：

```java
// src/main/java/ltdjms/discord/shared/DotEnvLoader.java
public class DotEnvLoader {
    public Map<String, String> load() {
        // 讀取 .env 檔案，回傳 key-value map
    }
}
```

### 2.3 DatabaseConfig

封裝資料庫連線與連線池設定：

```java
// src/main/java/ltdjms/discord/shared/DatabaseConfig.java
public record DatabaseConfig(
    String url,
    String username,
    String password,
    int maximumPoolSize,
    int minimumIdle,
    long connectionTimeout,
    long idleTimeout,
    long maxLifetime
) {
    // 驗證設定有效性
    // 建立 HikariCP DataSource
}
```

## 3. 錯誤處理模式

### 3.1 Result<T, E> 類型

專案使用 `Result<T, E>` 類別來表示操作結果，類似 Rust 的 Result 類型：

```java
// src/main/java/ltdjms/discord/shared/Result.java
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    // 工廠方法
    static <T, E> Result<T, E> ok(T value)
    static <E> Result<Unit, E> okVoid()
    static <T, E> Result<T, E> err(E error)

    // 判斷方法
    boolean isOk()
    boolean isErr()

    // 取值方法
    T getValue()      // 失敗時拋出 IllegalStateException
    E getError()      // 成功時拋出 IllegalStateException
    T getOrElse(T defaultValue)

    // 轉換方法
    <U> Result<U, E> map(Function<? super T, ? extends U> mapper)
    <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper)
    <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper)
}
```

**使用範例：**

```java
Result<MemberCurrencyAccount, DomainError> result =
    accountRepository.findByGuildIdAndUserId(guildId, userId);

if (result.isErr()) {
    DomainError error = result.getError();
    return Result.err(error);
}

MemberCurrencyAccount account = result.getValue();
return Result.ok(account.withAdjustedBalance(amount));
```

### 3.2 DomainError

領域錯誤的封裝類別，包含錯誤分類與訊息：

```java
// src/main/java/ltdjms/discord/shared/DomainError.java
public record DomainError(
    Category category,
    String message,
    Throwable cause
) {
    public enum Category {
        INVALID_INPUT,        // 輸入無效
        INSUFFICIENT_BALANCE, // 餘額不足
        INSUFFICIENT_TOKENS,  // 代幣不足
        PERSISTENCE_FAILURE,  // 資料庫失敗
        UNEXPECTED_FAILURE    // 非預期錯誤
    }

    // 工廠方法
    public static DomainError invalidInput(String message)
    public static DomainError insufficientBalance(String message)
    public static DomainError insufficientTokens(String message)
    public static DomainError persistenceFailure(String message, Throwable cause)
    public static DomainError unexpectedFailure(String message, Throwable cause)
}
```

### 3.3 Unit 類型

`Unit` 是 Java 的 `Void` 標記類型，用於 `Result<Unit, DomainError>` 表示不需回傳值的操作：

```java
// src/main/java/ltdjms/discord/shared/Unit.java
public final class Unit {
    public static final Unit INSTANCE = new Unit();
    private Unit() {}
}
```

## 4. Dagger 依賴注入

### 4.1 AppComponent

主 Dagger 組件，定義所有可注入的依賴：

```java
// src/main/java/ltdjms/discord/shared/di/AppComponent.java
@Singleton
@Component(modules = {
    DatabaseModule.class,
    CurrencyRepositoryModule.class,
    CurrencyServiceModule.class,
    GameTokenRepositoryModule.class,
    GameTokenServiceModule.class,
    ProductRepositoryModule.class,
    ProductServiceModule.class,
    CommandHandlerModule.class,
    EventModule.class
})
public interface AppComponent {

    // 配置
    EnvironmentConfig environmentConfig();
    DatabaseConfig databaseConfig();

    // 事件
    DomainEventPublisher domainEventPublisher();
    UserPanelUpdateListener userPanelUpdateListener();
    AdminPanelUpdateListener adminPanelUpdateListener();

    // 資料庫
    DataSource dataSource();
    DSLContext dslContext();

    // 服務
    BalanceService balanceService();
    GameTokenService gameTokenService();
    // ... 其他服務

    // Repository
    MemberCurrencyAccountRepository memberCurrencyAccountRepository();
    // ... 其他 Repository

    // Command Handlers
    SlashCommandListener slashCommandListener();
}
```

### 4.2 AppComponentFactory

建立 AppComponent 的工廠類別：

```java
// src/main/java/ltdjms/discord/shared/di/AppComponentFactory.java
public final class AppComponentFactory {
    public static AppComponent create(EnvironmentConfig envConfig) {
        return DaggerAppComponent.factory().create(envConfig);
    }
}
```

### 4.3 DI 模組

各模組透過 `@Module` 類別提供依賴：

| 模組 | 職責 |
|------|------|
| `DatabaseModule` | 提供 `DataSource`、`DSLContext` |
| `CurrencyRepositoryModule` | 提供貨幣相關 Repository |
| `CurrencyServiceModule` | 提供貨幣相關 Service |
| `GameTokenRepositoryModule` | 提供遊戲代幣相關 Repository |
| `GameTokenServiceModule` | 提供遊戲代幣相關 Service |
| `ProductRepositoryModule` | 提供產品相關 Repository |
| `ProductServiceModule` | 提供產品相關 Service |
| `CommandHandlerModule` | 提供 Command Handler |
| `EventModule` | 提供事件發布器與監聽器 |

## 5. 領域事件系統

### 5.1 DomainEvent 介面

所有領域事件的基底介面：

```java
// src/main/java/ltdjms/discord/shared/events/DomainEvent.java
public sealed interface DomainEvent permits
        BalanceChangedEvent,
        GameTokenChangedEvent,
        CurrencyConfigChangedEvent,
        DiceGameConfigChangedEvent,
        ProductChangedEvent,
        RedemptionCodesGeneratedEvent {

    /**
     * @return 事件發生的 Discord 伺服器 ID
     */
    long guildId();
}
```

### 5.2 領域事件類型

| 事件類型 | 說明 | 觸發時機 |
|----------|------|----------|
| `BalanceChangedEvent` | 餘額變更 | 成員貨幣餘額調整 |
| `GameTokenChangedEvent` | 代幣變更 | 成員遊戲代幣異動 |
| `CurrencyConfigChangedEvent` | 貨幣設定變更 | 管理員變更貨幣名稱/圖示 |
| `DiceGameConfigChangedEvent` | 遊戲設定變更 | 管理員調整遊戲代幣消耗 |
| `ProductChangedEvent` | 產品變更 | 新增/更新/刪除產品 |
| `RedemptionCodesGeneratedEvent` | 兌換碼生成 | 為產品生成新兌換碼 |

### 5.3 DomainEventPublisher

事件發布器，負責同步發送事件給所有監聽器：

```java
// src/main/java/ltdjms/discord/shared/events/DomainEventPublisher.java
public class DomainEventPublisher {

    private final List<Consumer<DomainEvent>> listeners =
        new CopyOnWriteArrayList<>();

    /**
     * 註冊事件監聽器
     */
    public void register(Consumer<DomainEvent> listener) {
        listeners.add(listener);
    }

    /**
     * 發布事件給所有監聽器
     */
    public void publish(DomainEvent event) {
        LOG.debug("Publishing event: {}", event);
        for (Consumer<DomainEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.error("Error handling domain event: {}", event, e);
            }
        }
    }
}
```

**使用範例：**

```java
// 在 Service 中發布事件
domainEventPublisher.publish(
    new BalanceChangedEvent(guildId, userId, oldBalance, newBalance)
);

// 在面板更新監聽器中訂閱事件
domainEventPublisher.register(event -> {
    if (event instanceof BalanceChangedEvent balanceEvent) {
        // 更新對應的 Discord 訊息
        updateUserPanelMessage(balanceEvent);
    }
});
```

## 6. 資料庫遷移

### 6.1 DatabaseMigrationRunner

使用 Flyway 執行資料庫遷移：

```java
// src/main/java/ltdjms/discord/shared/DatabaseMigrationRunner.java
public class DatabaseMigrationRunner {

    public static DatabaseMigrationRunner forDefaultMigrations() {
        // 掃描 src/main/resources/db/migration/
        return new DatabaseMigrationRunner();
    }

    public void migrate(DataSource dataSource) {
        // 執行 pending migrations
        // 若失敗拋出 SchemaMigrationException
    }
}
```

### 6.2 SchemaMigrationException

遷移失敗時的例外：

```java
// src/main/java/ltdjms/discord/shared/SchemaMigrationException.java
public class SchemaMigrationException extends RuntimeException {
    public SchemaMigrationException(String message) {
        super(message);
    }
}
```

## 7. 本地化系統

### 7.1 DiceGameMessages

骰子遊戲的本地化訊息：

```java
// src/main/java/ltdjms/discord/shared/localization/DiceGameMessages.java
public class DiceGameMessages {
    public String getDiceGame1Result(Locale locale, DiceGameResult result)
    public String getDiceGame2Result(Locale locale, DiceGame2Result result)
}
```

### 7.2 CommandLocalizations

指令回應的本地化支援：

```java
// src/main/java/ltdjms/discord/shared/localization/CommandLocalizations.java
public class CommandLocalizations {
    public String getInsufficientTokensMessage(Locale locale, long required, long have)
    public String getBalanceAdjustmentSuccess(Locale locale, long newBalance)
    // ... 其他本地化訊息
}
```

## 8. 開發建議

### 8.1 新增 Service 時的 DI 配置

1. 在對應的 `*ServiceModule` 中新增 `@Provides` 方法
2. 將 Service 新增到 `AppComponent` 介面
3. 在 `AppComponentImpl`（若有）中實作

### 8.2 新增 Repository 時的 DI 配置

1. 在對應的 `*RepositoryModule` 中新增 `@Binds` 方法
2. 將 Repository 介面新增到 `AppComponent` 介面

### 8.3 新增領域事件

1. 建立新的 `*Event` 類別，实现 `DomainEvent` 介面
2. 在 `EventModule` 中註冊事件監聽器
3. 在 `AppComponent` 中暴露 `DomainEventPublisher`

---

熟悉以上共用模組的設計後，您可以在開發新功能時正確使用設定載入、錯誤處理與依賴注入模式。
