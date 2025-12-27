# 開發指南：測試策略與指令

本文件說明 LTDJMS 專案中現有的測試分類、常用執行指令，以及在新增或修改功能時撰寫測試的建議。

## 1. 測試分類與目錄結構

所有測試程式碼位於：

- `src/test/java/ltdjms/discord/...`

主要依功能與測試類型拆分：

- **貨幣系統（currency）**
  - `unit/`：服務與指令 handler 的單元測試  
    例如 `BalanceServiceTest`、`CurrencyConfigServiceTest`、`BalanceCommandHandlerTest`。
  - `integration/`：包含資料庫與 JDA 模擬的整合測試  
    例如 `BalanceServiceIntegrationTest`、`CurrencyConfigCommandIntegrationTest`。
  - `contract/`：針對對外行為（如指令回應格式）的契約測試。  
  - `performance/`：針對 slash commands 的效能與延遲測試。

- **遊戲代幣與小遊戲（gametoken）**
  - `domain/`：domain 類別的行為測試（例如 `GameTokenAccountTest`）。
  - `services/`：遊戲服務與代幣服務的單元測試（例如 `DiceGame1ServiceTest`）。
  - `integration/`：Repository 與資料庫整合測試。
  - `unit/`：指令 handler 與交易服務的單元測試。

- **面板（panel）**
  - `unit/`：面板服務（`UserPanelService` 等）的單元測試。

- **共用模組（shared）**
  - `EnvironmentConfig`、`DotEnvLoader`、`Result` 等核心元件的單元測試。
  - DI 組態測試（例如 `AppComponentLoadTest`）。

- **Discord API 抽象層（discord）**
  - `domain/`：抽象介面的行為測試（例如 `DiscordContextTest`、`DiscordInteractionTest`）。
  - `adapter/`：Adapter 層的單元測試（例如 `SlashCommandAdapterTest`）。
  - `mock/`：Mock 實作的驗證測試（例如 `MockDiscordInteractionTest`）。
  - `services/`：JDA 實作的單元測試與整合測試（例如 `JdaDiscordContextTest`、`InteractionSessionManagerTest`）。

## 2. 常用測試指令

所有指令皆在專案根目錄執行。

### 2.1 使用 Make

```bash
# 執行單元測試
make test

# 執行所有測試（含整合測試）
make test-integration

# 完整驗證（重新建置並跑所有測試）
make verify

# 產生 coverage 報告
make coverage
```

### 2.2 直接使用 Maven

```bash
# 單元測試
mvn test

# 全部測試 + 驗證
mvn clean verify
```

## 3. 新增功能時的測試建議流程

當你要新增功能或修改行為時，建議使用類似 TDD 的流程：

1. **先找既有測試**  
   - 在 `src/test/java` 中尋找與你修改的模組相對應的測試檔案。
   - 觀察命名慣例與使用的測試工具（例如 JUnit、Mockito）。

2. **為新行為撰寫或修改測試**  
   - 若是新增 service 行為，優先新增「單元測試」。
   - 若是新增指令或改變回應格式，可考慮：
     - 單元測試（針對 handler 的邏輯與錯誤處理）。
     - 契約測試（確認回應文字符合預期）。

3. **先讓新測試失敗**  
   - 執行 `make test` 或 `mvn test`，確認新測試確實因尚未實作而失敗。

4. **實作最小必要程式碼讓測試通過**  
   - 實作或修改對應程式碼。
   - 重複執行測試直到所有相關測試通過。

5. **必要時再補整合測試**  
   - 若新行為跨越多層（指令＋服務＋資料庫），可在 `integration/` 目錄補上整合測試。

## 4. 修改既有功能時的測試策略

### 4.1 行為不應變更的情況

例如重構、效能優化或內部實作調整，但對外行為應維持不變：

- 請確認相關的單元與整合測試都涵蓋了關鍵案例。
- 修改完成後，至少執行：

  ```bash
  make test
  make test-integration
  ```

### 4.2 行為有意義變更的情況

例如新增錯誤訊息、改變指令回應格式或調整邊界條件：

- 優先更新對應的測試，讓測試描述「新的預期行為」。
- 確認舊的測試不會錯誤地描述過時需求。

## 5. 撰寫測試時的實務提醒

- **善用測試工具與共用 util**  
  - 對 JDA 相關測試，可重用 `src/test/java/ltdjms/discord/currency/unit/JdaTestUtils.java` 等工具。

- **將預期錯誤視為正常情境測試**  
  - 由於專案使用 `Result<T, DomainError>`，請記得也測試錯誤情境（如餘額不足、輸入無效等），確保 `DomainError.Category` 使用正確。

- **維持測試命名一致性**
  - 測試類別與方法命名盡量與現有風格一致，便於未來維護與搜尋。

## 6. 使用 Discord API 抽象層 Mock 進行單元測試

Discord API 抽象層提供 Mock 實作，讓您可以在不啟動 Discord Bot 的情況下測試 Command Handler 和 Service 的邏輯。

### 6.1 使用 MockDiscordInteraction 測試 Handler

**傳統方式（需要 Mock JDA 事件）**：

```java
@Test
void testBalanceCommand_withSufficientBalance_returnsEmbed() {
    // 需要 Mock 複雜的 JDA 事件
    SlashCommandInteractionEvent mockEvent = mock(SlashCommandInteractionEvent.class);
    when(mockEvent.getGuild()).thenReturn(mockGuild);
    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.getHook()).thenReturn(mockHook);

    // ... 更多的 Mock 設定
}
```

**使用 Discord 抽象層 Mock**：

```java
@Test
void testBalanceCommand_withSufficientBalance_returnsEmbed() {
    // Arrange
    MockDiscordInteraction mockInteraction = new MockDiscordInteraction(123L, 456L);
    MockDiscordContext mockContext = new MockDiscordContext(123L, 456L, 789L);
    MockDiscordEmbedBuilder mockBuilder = new MockDiscordEmbedBuilder();

    BalanceService balanceService = mock(BalanceService.class);
    when(balanceService.getBalance(123L, 456L))
        .thenReturn(Result.ok(new BalanceView("1000", "500")));

    BalanceCommandHandler handler = new BalanceCommandHandler(
        balanceService, mockBuilder
    );

    // Act
    handler.handleInternal(mockInteraction, mockContext);

    // Assert
    assertTrue(mockInteraction.isReplied());
    assertEquals(1, mockBuilder.getBuildCount());
    verify(balanceService).getBalance(123L, 456L);
}
```

### 6.2 測試錯誤處理

```java
@Test
void testBalanceCommand_withServiceError_returnsErrorMessage() {
    // Arrange
    MockDiscordInteraction mockInteraction = new MockDiscordInteraction(123L, 456L);
    MockDiscordContext mockContext = new MockDiscordContext(123L, 456L, 789L);

    BalanceService balanceService = mock(BalanceService.class);
    when(balanceService.getBalance(123L, 456L))
        .thenReturn(Result.err(DomainError.persistenceFailure("DB 錯誤", null)));

    BalanceCommandHandler handler = new BalanceCommandHandler(balanceService, embedBuilder);

    // Act
    handler.handleInternal(mockInteraction, mockContext);

    // Assert
    assertTrue(mockInteraction.isReplied());
    assertTrue(mockInteraction.getLastMessage().contains("錯誤"));
}
```

### 6.3 測試 Session 管理

```java
@Test
void testUserPanel_withSessionCreation_sessionPersisted() {
    // Arrange
    DiscordSessionManager<PanelSessionType> sessionManager =
        new InteractionSessionManager<>();

    MockDiscordInteraction mockInteraction = new MockDiscordInteraction(123L, 456L);
    MockDiscordContext mockContext = new MockDiscordContext(123L, 456L, 789L);

    UserPanelService service = new UserPanelService(sessionManager, ...);

    // Act
    service.createUserPanel(mockInteraction, mockContext);

    // Assert
    Optional<Session<PanelSessionType>> sessionOpt =
        sessionManager.getSession(PanelSessionType.USER_PANEL, 123L, 456L);

    assertTrue(sessionOpt.isPresent());
    assertFalse(sessionOpt.get().isExpired());
}
```

### 6.4 測試 DiscordEmbedBuilder

```java
@Test
void testEmbedBuilder_withLongText_truncatesCorrectly() {
    // Arrange
    DiscordEmbedBuilder builder = new JdaDiscordEmbedBuilder();

    // Act
    String longText = "A".repeat(300); // 超過標題限制
    MessageEmbed embed = builder.setTitle(longText).build();

    // Assert
    assertEquals(256 + "...".length(), embed.getTitle().length());
}
```

### 6.5 優勢總結

| 優勢 | 說明 |
|------|------|
| **簡化 Mock 設定** | 不需要 Mock 複雜的 JDA 事件物件 |
| **更清晰的測試意圖** | 專注於業務邏輯，非 JDA 細節 |
| **更快的測試執行** | 不需要初始化 JDA 與 Discord 連線 |
| **更好的可維護性** | JDA API 變更不影響測試程式碼 |

---

## 7. 代碼格式化（Spotless）

本專案使用 Spotless Maven Plugin 自動化代碼格式檢查和格式化，確保代碼庫遵循統一的 Google Java Format 風格。

### 7.1 Spotless Maven Goals 參考

| Goal | 說明 | 何時使用 |
|------|------|----------|
| `spotless:check` | 檢查格式，違規時失敗 | CI/CD、提交前 |
| `spotless:apply` | 自動修正格式問題 | 開發過程、修正違規 |

### 7.2 使用 Make 執行格式檢查

```bash
# 格式化代碼
make format

# 檢查格式
make format-check

# 執行測試（包含格式檢查）
make test
```

### 7.3 IDE 整合

#### IntelliJ IDEA

**方法一：Spotless Applier Plugin（推薦）**

1. 安裝插件：
   - `File` → `Settings` → `Plugins` → 搜尋 "Spotless Applier"
   - 或訪問 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/22455-spotless-applier)

2. 使用方式：
   - 右鍵檔案 → `Spotless: Format Current File`
   - 右鍵專案 → `Spotless: Format Project`

**方法二：外部工具**

1. 建立外部工具：
   ```
   File → Settings → Tools → External Tools → +
   Name: Spotless Apply
   Program: mvn
   Arguments: spotless:apply
   Working directory: $ProjectFileDir$
   ```

2. 設定快捷鍵（可選）：
   ```
   Keymap → 搜尋 "Spotless Apply" → 設定快捷鍵（如 Cmd+Shift+F）
   ```

#### VS Code

本專案已配置 `.vscode/tasks.json`，可直接使用 Maven tasks：

1. 執行格式化：
   - `Cmd+Shift+P` → "Tasks: Run Task" → "Spotless: Apply"
   - `Cmd+Shift+P` → "Tasks: Run Task" → "Spotless: Check"

2. 格式規則已與命令行完全同步，確保一致性。

### 7.4 常見工作流程範例

#### 開發新功能

```bash
# 1. 開發功能
# ... 編寫代碼 ...

# 2. 格式化代碼
make format

# 3. 執行測試
make test

# 4. 提交
git add .
git commit -m "feat: new feature"
```

#### 修復格式違規

```bash
# 1. CI/CD 失敗，提示格式違規
# Running 'mvn spotless:check'

# 2. 修正格式
make format

# 3. 驗證修正
make format-check

# 4. 提交修正
git add .
git commit -m "chore: fix code format"
```

---

## 8. 本文件與實際執行情況

此文件僅說明測試策略與指令，**不會自動執行任何測試**。  
實際開發時請依下列指令在本機或 CI 中執行測試：

- 單元測試：`make test`
- 全部測試：`make test-integration` 或 `mvn clean verify`

