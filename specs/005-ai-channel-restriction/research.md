# Research: AI Channel Restriction

**Feature**: AI Channel Restriction (005-ai-channel-restriction)
**Date**: 2025-12-29
**Status**: Complete

## Overview

本文記錄 AI 頻道限制功能的技術決策與研究結果。由於專案已有明確的技術堆疊與架構模式，此研究主要確認現有模式的適用性與最佳實踐。

---

## Technical Decisions

### 1. 資料持久化策略

**Decision**: 使用 PostgreSQL + JDBC 實作 `AIChannelRestrictionRepository`

**Rationale**:
- 專案已有成熟的 PostgreSQL 基礎設施 (Flyway 遷移、Testcontainers)
- AI 頻道限制需要持久化儲存（伺服器重啟後保持設定）
- 資料模型簡單 (guild_id + 頻道清單)，JDBC 已足夠，不需要 jOOQ
- 與現有 `JdbcMemberCurrencyAccountRepository` 模式一致

**Alternatives Considered**:
- **Redis**: 因需要持久化而被拒絕（Redis 作為快取層，非主要儲存）
- **jOOQ**: 對簡單 CRUD 操作過度工程化

**Schema Design**:
```sql
CREATE TABLE ai_channel_restriction (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    channel_name TEXT NOT NULL,  -- 冗餘儲存以便顯示
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, channel_id)
);
```

---

### 2. 頻道檢查時機與方式

**Decision**: 在 `AIChatMentionListener.onMessageReceived()` 早期檢查，未允許則直接 return

**Rationale**:
- 最早攔截可避免不必要的資源消耗（AI API 呼叫、提示詞載入）
- 符合「fail fast」原則
- 檢查邏輯簡單 (Set 查詢)，延遲 < 1ms，遠低於 100ms 目標
- 不產生錯誤日誌或錯誤訊息（符合 FR-004 需求）

**Implementation Pattern**:
```java
@Override
public void onMessageReceived(MessageReceivedEvent event) {
    // 早期檢查：忽略未允許頻道
    if (!aiChannelRestrictionService.isChannelAllowed(guildId, channelId)) {
        return; // 安靜忽略
    }
    // 繼續現有 AI 處理流程...
}
```

**Alternatives Considered**:
- **在 AIChatService 中檢查**: 會浪費提示詞載入等前置作業
- **非同步檢查**: 增加複雜度且無實際效益

---

### 3. 允許清單為空時的行為

**Decision**: 空清單 = 無限制模式（AI 在所有頻道可用）

**Rationale**:
- 符合 spec 中的明確假設：「允許清單為空時視為『無限制』模式」
- 向後相容：現有伺服器未設定時行為不變
- 簡化首次使用體驗（管理員不需設定即可使用）

**Implementation**:
```java
boolean isChannelAllowed(long guildId, long channelId) {
    Set<AllowedChannel> allowedChannels = repository.findByGuildId(guildId);
    return allowedChannels.isEmpty()  // 空清單 = 允許所有頻道
        || allowedChannels.stream().anyMatch(c -> c.channelId() == channelId);
}
```

---

### 4. 管理面板整合策略

**Decision**: 擴充 `AdminPanelButtonHandler`，新增 `admin_panel_ai_channel` 按鈕 ID

**Rationale**:
- 與現有管理面板模式一致（貨幣設定、代幣設定等）
- 使用現有的 `AdminPanelSessionManager` 追蹤狀態
- 利用 `DomainEventPublisher` 發布 `AIChannelRestrictionChangedEvent`

**UI Flow**:
1. 管理員執行 `/admin-panel`
2. 主選單顯示「AI 頻道設定」按鈕
3. 點擊後顯示目前允許頻道清單
4. 提供選單選擇要新增/移除的頻道（使用 Discord Select Menu）
5. 確認後更新並顯示結果

**Alternatives Considered**:
- **獨立 Slash 指令**: 與現有管理面板 UX 不一致
- **Modal 輸入**: 頻道 ID 輸入不友善，Select Menu 更佳

---

### 5. 權限檢查實作

**Decision**: 新增頻道前檢查機器人在該頻道的 `MESSAGE_SEND` 權限

**Rationale**:
- 符合 FR-014/FR-015 需求
- 避免管理員設定無效頻道（導致 AI 功能無法運作）
- 使用 JDA `Guild.getMember(bot).hasPermission(channel, Permission.MESSAGE_SEND)`

**Implementation**:
```java
Result<AllowedChannel, DomainError> validateAndAddChannel(long guildId, long channelId) {
    // 1. 檢查是否重複
    // 2. 檢查機器人權限
    if (!botHasPermission(guildId, channelId, Permission.MESSAGE_SEND)) {
        return Result.err(DomainError.of(
            DomainError.Category.INSUFFICIENT_PERMISSIONS,
            "機器人在該頻道沒有發言權限"
        ));
    }
    // 3. 儲存至資料庫
}
```

---

### 6. 已刪除頻道處理

**Decision**: 檢視時自動移除無效頻道，清單變空時恢復無限制模式

**Rationale**:
- 符合 FR-012/FR-013 需求
- 避免資料庫累積無效資料
- 檢視時處理比排程任務更簡單（惰性清理）

**Implementation**:
```java
Set<AllowedChannel> getAllowedChannels(long guildId) {
    Set<AllowedChannel> channels = repository.findByGuildId(guildId);

    // 過濾掉已刪除的頻道
    Set<AllowedChannel> validChannels = channels.stream()
        .filter(c -> guild.getTextChannelById(c.channelId()) != null)
        .collect(Collectors.toSet());

    // 移除無效頻道
    if (validChannels.size() < channels.size()) {
        repository.deleteRemovedChannels(guildId, validChannels);
    }

    return validChannels;
}
```

---

### 7. 並發修改衝突處理

**Decision**: 使用資料庫 UNIQUE 約束處理重複，簡單樂觀合併

**Rationale**:
- FR-016 要求合併無衝突設定並告知衝突
- 資料庫 PRIMARY KEY (guild_id, channel_id) 自動處理重複
- 衝突時以「先到先得」為原則，告知管理員哪些設定未套用

**Implementation**:
```java
Result<Set<AllowedChannel>, DomainError> batchAddChannels(
    long guildId, Set<Long> channelIds
) {
    Set<AllowedChannel> added = new HashSet<>();
    Set<String> conflicts = new HashSet<>();

    for (Long channelId : channelIds) {
        Result<AllowedChannel, DomainError> result = addChannel(guildId, channelId);
        if (result.isOk()) {
            added.add(result.getValue());
        } else {
            conflicts.add("頻道 " + channelId + ": " + result.getError().message());
        }
    }

    if (!conflicts.isEmpty()) {
        return Result.okWithWarning(added, "部分頻道新增失敗: " + conflicts);
    }
    return Result.ok(added);
}
```

---

### 8. 快取策略

**Decision**: 不使用快取（直接查詢資料庫）

**Rationale**:
- 頻道檢查頻率相對較低（僅當使用者提及機器人時）
- 查詢簡單 (SELECT WHERE guild_id = ?)，資料庫索引效率高
- 設定變更不頻繁，快取一致性收益有限
- 簡化實作，減少潛在 bug

**Performance Validation**:
- 預期查詢時間 < 5ms (PostgreSQL 索引查詢)
- 遠低於 100ms 目標

**Alternatives Considered**:
- **Redis 快取**: 增加複雜度，收益有限（可未來優化）

---

### 9. 測試策略

**Decision**: 遵循 TDD 原則，三層測試金字塔

**Unit Tests** (JUnit + Mockito):
- `AIChannelRestrictionTest`: 領域模型驗證邏輯
- `JdbcAIChannelRestrictionRepositoryTest`: Repository CRUD 操作
- `DefaultAIChannelRestrictionServiceTest`: 服務層業務邏輯

**Integration Tests** (Testcontainers):
- `AIChannelRestrictionIntegrationTest`: 端對端流程測試
- 測試完整流程：新增頻道 → 頻道檢查 → 移除頻道

**Coverage Target**: 80% (JaCoCo)

---

### 10. DomainError 類別擴充

**Decision**: 新增以下錯誤類別至 `DomainError.Category`

| 類別 | 使用時機 |
|------|----------|
| `CHANNEL_NOT_ALLOWED` | 頻道不在允許清單中 (僅用於測試，實際不回報錯誤) |
| `DUPLICATE_CHANNEL` | 嘗試新增重複頻道 |
| `INSUFFICIENT_PERMISSIONS` | 機器人沒有頻道發言權限 |
| `CHANNEL_NOT_FOUND` | 頻道已從伺服器刪除 |

**Rationale**: 與現有 `DomainError` 模式一致，提供清楚的錯誤分類。

---

## Architecture Alignment

### Layered Architecture Compliance

| 層級 | 類別 | 職責 |
|------|------|------|
| **Domain** | `AIChannelRestriction`, `AllowedChannel` | 純領域邏輯，無基礎設施依賴 |
| **Persistence** | `AIChannelRestrictionRepository`, `JdbcAIChannelRestrictionRepository` | 資料存取，JDBC 實作 |
| **Services** | `AIChannelRestrictionService`, `DefaultAIChannelRestrictionService` | 業務邏輯編排 |
| **Commands** | `AdminPanelButtonHandler` (擴充) | JDA 事件處理與 Discord 回應 |

### Event-Driven Integration

**新增事件**:
```java
public record AIChannelRestrictionChangedEvent(
    long guildId,
    Set<AllowedChannel> allowedChannels,
    Instant timestamp
) implements DomainEvent {}
```

**監聽器**: `AIChannelRestrictionChangeListener` (可選，用於日誌或監控)

---

## Open Questions (None)

所有技術決策已確定，無待釋清項目。

---

## Dependencies

**新增 Maven 依賴**: 無（使用現有依賴）

**現有依賴版本**:
- JDA 5.2.2
- Dagger 2.52
- PostgreSQL JDBC 42.x
- JUnit 5.11.3
- Mockito 5.14.2
- Testcontainers (PostgreSQL module)

---

## Next Steps

1. ✅ Technical decisions documented
2. 🔄 Phase 1: Generate `data-model.md`
3. ⏳ Phase 1: Generate `quickstart.md`
4. ⏳ Phase 1: Update agent context
5. ⏳ Phase 2: Generate `tasks.md` (via `/speckit.tasks`)
