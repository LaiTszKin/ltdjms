# AI 類別層級配置功能設計

**日期**: 2026-01-02
**狀態**: 設計已完成
**關聯規格**: 008-ai-category-level-config

---

## 概述

擴展現有的 AI Chat 和 AI Agent 模組，支援類別（Category）層級的配置，允許管理員一次性設定整個類別的 AI/Agent 權限。

### 核心需求

1. **覆蓋模式**：頻道層級設定優先於類別設定
2. **分開儲存**：創建新的資料表儲存類別配置
3. **動態檢查**：類別設定是獨立規則，檢查時動態判斷頻道是否屬於允許的類別
4. **保守遷移**：保持向後相容性，現有無限制模式不受影響

---

## 領域模型設計

### AI Chat 模組

#### AllowedCategory 值物件

```java
public record AllowedCategory(
    long categoryId,
    String categoryName  // 冗餘儲存以便顯示
) {
    public static AllowedCategory from(Category category);
}
```

#### AIChannelRestriction 聚合根擴展

```java
public record AIChannelRestriction(
    long guildId,
    Set<AllowedChannel> allowedChannels,
    Set<AllowedCategory> allowedCategories  // 新增
) {
    // 現有方法
    public boolean isUnrestricted();
    public boolean isChannelAllowed(long channelId);

    // 新增方法
    public boolean isCategoryAllowed(long categoryId);
    public boolean isChannelExplicitlySet(long channelId);  // 區分明確設定與未設定
    public AIChannelRestriction withCategoryAdded(AllowedCategory category);
    public AIChannelRestriction withCategoryRemoved(long categoryId);
}
```

### AI Agent 模組

#### AIAgentCategoryConfig 聚合根

```java
public record AIAgentCategoryConfig(
    long id,
    long guildId,
    long categoryId,
    boolean agentEnabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AIAgentCategoryConfig create(long guildId, long categoryId);
    public AIAgentCategoryConfig toggleAgentMode();
    public AIAgentCategoryConfig withAgentEnabled(boolean enabled);
}
```

---

## 資料庫架構

### AI Chat 模組

#### ai_category_restriction 表（新增）

```sql
CREATE TABLE ai_category_restriction (
    guild_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    category_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, category_id)
);

CREATE INDEX idx_ai_category_restriction_guild_id
    ON ai_category_restriction(guild_id);
```

#### ai_channel_restriction 表（修改）

保守遷移策略：新增 `explicitly_set` 欄位區分明確設定：

```sql
-- V010__ai_category_support.sql
ALTER TABLE ai_channel_restriction
ADD COLUMN explicitly_set BOOLEAN DEFAULT true;
```

### AI Agent 模組

#### ai_agent_category_config 表（新增）

```sql
CREATE TABLE ai_agent_category_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL UNIQUE,
    agent_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_agent_category_config_guild_id
    ON ai_agent_category_config(guild_id);
```

---

## 檢查邏輯

### AI Chat 檢查流程（覆蓋模式）

```java
public boolean isChannelAllowed(long guildId, long channelId, long categoryId) {
    AIChannelRestriction restriction = findByGuildId(guildId);

    // 1. 優先檢查頻道層級明確設定
    if (restriction.isChannelExplicitlySet(channelId)) {
        return restriction.isChannelAllowed(channelId);
    }

    // 2. 動態檢查類別層級
    if (categoryId != 0 && restriction.isCategoryAllowed(categoryId)) {
        return true;
    }

    // 3. 無限制模式（向後相容）
    if (restriction.isUnrestricted()) {
        return true;
    }

    // 4. 預設拒絕
    return false;
}
```

### AI Agent 檢查流程（覆蓋模式）

```java
public boolean isAgentEnabled(long guildId, long channelId, long categoryId) {
    // 1. 優先檢查頻道層級配置
    Optional<AIAgentChannelConfig> channelConfig = findByChannel(guildId, channelId);
    if (channelConfig.isPresent()) {
        return channelConfig.get().agentEnabled();
    }

    // 2. 動態檢查類別層級
    if (categoryId != 0) {
        Optional<AIAgentCategoryConfig> categoryConfig = findByCategory(guildId, categoryId);
        if (categoryConfig.isPresent()) {
            return categoryConfig.get().agentEnabled();
        }
    }

    // 3. 預設關閉
    return false;
}
```

---

## Repository 介面

### AIChannelRestrictionRepository 擴展

```java
public interface AIChannelRestrictionRepository {
    // 現有方法
    AIChannelRestriction findByGuildId(long guildId);
    AIChannelRestriction save(AIChannelRestriction restriction);

    // 新增：類別操作
    AIChannelRestriction addCategory(long guildId, AllowedCategory category);
    AIChannelRestriction removeCategory(long guildId, long categoryId);
    Set<AllowedCategory> findAllowedCategories(long guildId);
}
```

### AIAgentCategoryConfigRepository（新增）

```java
public interface AIAgentCategoryConfigRepository {
    Optional<AIAgentCategoryConfig> findByGuildAndCategory(long guildId, long categoryId);
    List<AIAgentCategoryConfig> findByGuildId(long guildId);
    AIAgentCategoryConfig save(AIAgentCategoryConfig config);
    void delete(long guildId, long categoryId);
}
```

---

## 領域事件

### AICategoryRestrictionChangedEvent

```java
public record AICategoryRestrictionChangedEvent(
    long guildId,
    long categoryId,
    boolean added,  // true=新增, false=移除
    Instant timestamp
) implements DomainEvent {}
```

### AIAgentCategoryConfigChangedEvent

```java
public record AIAgentCategoryConfigChangedEvent(
    long guildId,
    long categoryId,
    boolean agentEnabled,
    Instant timestamp
) implements DomainEvent {}
```

---

## UI/UX 設計

### AI 頻道與類別設定選單

```
🤖 AI 頻道與類別設定
├── 📋 查看設定
├── 📁 類別設定
│   ├── ➕ 新增類別
│   ├── ➖ 移除類別
│   └── 📋 類別清單
└── 📢 頻道設定
    ├── ➕ 新增頻道
    ├── ➖ 移除頻道
    └── 📋 頻道清單
```

### AI Agent 設定選單

```
🤖 AI Agent 設定
├── 📋 查看設定
├── 📁 類別設定
│   ├── ➕ 啟用類別
│   ├── ➖ 停用類別
│   └── 📋 類別清單
└── 📢 頻道設定
    ├── ➕ 啟用頻道
    ├── ➖ 停用頻道
    └── 📋 頻道清單
```

### 視圖顯示格式

#### AI Chat 查看設定

```
🤖 AI 頻道與類別設定

📁 允許的類別 (2):
  • 活動區 (ID: 123)
  • 公告區 (ID: 456)

📢 允許的頻道 (3):
  • 活動公告 (ID: 789) [類別: 活動區]
  • 公告 (ID: 101) [類別: 公告區]
  • 私人頻道 (ID: 202) [無類別]

⚠️ 未設定類別的頻道將不會被 AI 回應
```

#### AI Agent 查看設定

```
🤖 AI Agent 設定

📁 啟用的類別 (1):
  • 管理區 (ID: 333) ✅

📢 啟用的頻道 (2):
  • 管理公告 (ID: 444) ✅
  • 私人頻道 (ID: 555) ✅

⚠️ 未啟用的頻道/類別無法使用 AI 工具
```

---

## 測試策略

### 單元測試

**AI Chat 模組**：
- `AllowedCategoryTest` - 類別值物件驗證
- `AIChannelRestrictionTest` - 擴展測試類別邏輯
- `JdbcAIChannelRestrictionRepositoryTest` - Repository 類別操作
- `DefaultAIChannelRestrictionServiceTest` - 覆蓋模式檢查邏輯

**AI Agent 模組**：
- `AIAgentCategoryConfigTest` - 類別配置聚合根
- `JdbcAIAgentCategoryConfigRepositoryTest` - Repository 測試
- `DefaultAIAgentCategoryConfigServiceTest` - 服務層測試

### 整合測試

```bash
# AI Chat 類別功能整合測試
mvn test -Dtest=AICategoryRestrictionIntegrationTest

# AI Agent 類別功能整合測試
mvn test -Dtest=AIAgentCategoryConfigIntegrationTest
```

### 測試覆蓋範圍

- [x] 類別新增與移除
- [x] 覆蓋模式檢查邏輯（頻道優先）
- [x] 動態類別繼承
- [x] 向後相容性（無限制模式）
- [x] 空類別清單行為
- [x] 事件發布與監聽

---

## 遷移策略（保守遷移）

### V010__ai_category_support.sql

```sql
-- === AI Chat 模組 ===

-- 1. 創建類別限制表
CREATE TABLE ai_category_restriction (
    guild_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    category_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, category_id)
);

CREATE INDEX idx_ai_category_restriction_guild_id
    ON ai_category_restriction(guild_id);

-- 2. 新增 explicitly_set 欄位（區分明確設定）
ALTER TABLE ai_channel_restriction
ADD COLUMN explicitly_set BOOLEAN DEFAULT true;

-- === AI Agent 模組 ===

-- 3. 創建 Agent 類別配置表
CREATE TABLE ai_agent_category_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL UNIQUE,
    agent_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_agent_category_config_guild_id
    ON ai_agent_category_config(guild_id);
```

### 向後相容性說明

- 現有資料 `explicitly_set = true`，不受新功能影響
- 空集合仍表示「無限制模式」（向後相容）
- 新增的類別規則僅影響未明確設定的頻道
- 伺服器在首次使用新功能前保持現有行為

---

## 實作檔案清單

### AI Chat 模組

**Domain**:
- `AllowedCategory.java` - 新增
- `AIChannelRestriction.java` - 修改
- `AICategoryRestrictionChangedEvent.java` - 新增

**Persistence**:
- `AIChannelRestrictionRepository.java` - 修改介面
- `JdbcAIChannelRestrictionRepository.java` - 修改實作
- `JdbcAIChannelRestrictionRepository` 新增方法：
  - `addCategory()`
  - `removeCategory()`
  - `findAllowedCategories()`

**Services**:
- `AIChannelRestrictionService.java` - 修改介面
- `DefaultAIChannelRestrictionService.java` - 修改實作
- 新增方法：
  - `isChannelAllowed(long guildId, long channelId, long categoryId)`
  - `addAllowedCategory()`
  - `removeAllowedCategory()`
  - `getAllowedCategories()`

**Commands**:
- `AdminPanelHandler.java` - 修改選單結構

### AI Agent 模組

**Domain**:
- `AIAgentCategoryConfig.java` - 新增
- `AIAgentCategoryConfigChangedEvent.java` - 新增

**Persistence**:
- `AIAgentCategoryConfigRepository.java` - 新增介面
- `JdbcAIAgentCategoryConfigRepository.java` - 新增實作

**Services**:
- `AIAgentCategoryConfigService.java` - 新增介面
- `DefaultAIAgentCategoryConfigService.java` - 新增實作

**Commands**:
- `AdminPanelHandler.java` - 修改選單結構

**DI**:
- `AIAgentModule.java` - 註冊新元件

### 測試

**AI Chat 單元測試**:
- `AllowedCategoryTest.java` - 新增
- `AIChannelRestrictionTest.java` - 擴展
- `JdbcAIChannelRestrictionRepositoryTest.java` - 擴展
- `DefaultAIChannelRestrictionServiceTest.java` - 擴展

**AI Chat 整合測試**:
- `AICategoryRestrictionIntegrationTest.java` - 新增

**AI Agent 單元測試**:
- `AIAgentCategoryConfigTest.java` - 新增
- `JdbcAIAgentCategoryConfigRepositoryTest.java` - 新增
- `DefaultAIAgentCategoryConfigServiceTest.java` - 新增

**AI Agent 整合測試**:
- `AIAgentCategoryConfigIntegrationTest.java` - 新增

### 文件

- `docs/modules/aichat.md` - 更新
- `docs/modules/aiagent.md` - 更新

---

## 成功標準

- [ ] 管理員可以設定類別層級的 AI 權限
- [ ] 頻道層級設定優先於類別設定（覆蓋模式）
- [ ] 未明確設定的頻道繼承類別規則（動態檢查）
- [ ] 現有無限制模式伺服器不受影響（向後相容）
- [ ] 新增頻道到已允許的類別時，自動繼承權限
- [ ] 管理面板可查看和管理類別/頻道設定
- [ ] 所有測試通過，覆蓋率 ≥ 80%

---

## 未來擴展

- 支援類別層級的「明確拒絕」功能（黑名單）
- 支援類別繼承（父類別設定影響子類別）
- 批量操作（一次設定多個類別）
- 類別設定模板（預設配置快速套用）
