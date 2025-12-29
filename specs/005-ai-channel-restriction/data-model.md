# Data Model: AI Channel Restriction

**Feature**: AI Channel Restriction (005-ai-channel-restriction)
**Date**: 2025-12-29

## Overview

本文記錄 AI 頻道限制功能的資料模型設計，包括領域模型、資料庫 schema 與驗證規則。

---

## Domain Entities

### 1. AIChannelRestriction (Aggregate Root)

代表一個 Discord 伺服器的 AI 頻道限制配置。

```java
package ltdjms.discord.aichat.domain;

/**
 * AI 頻道限制聚合根
 *
 * <p>管理一個 Discord 伺服器的 AI 功能允許頻道清單。
 * 空清單代表無限制模式（AI 可在所有頻道使用）。
 */
public record AIChannelRestriction(
    long guildId,                    // 伺服器 ID
    Set<AllowedChannel> allowedChannels  // 允許的頻道集合
) {
    /**
     * 建立無限制模式的新配置
     */
    public AIChannelRestriction(long guildId) {
        this(guildId, Set.of());
    }

    /**
     * 檢查是否為無限制模式
     */
    public boolean isUnrestricted() {
        return allowedChannels.isEmpty();
    }

    /**
     * 檢查頻道是否被允許
     */
    public boolean isChannelAllowed(long channelId) {
        return isUnrestricted()
            || allowedChannels.stream()
                .anyMatch(c -> c.channelId() == channelId);
    }

    /**
     * 新增允許頻道
     */
    public AIChannelRestriction withChannelAdded(AllowedChannel channel) {
        Set<AllowedChannel> newSet = new HashSet<>(allowedChannels);
        newSet.add(channel);
        return new AIChannelRestriction(guildId, newSet);
    }

    /**
     * 移除允許頻道
     */
    public AIChannelRestriction withChannelRemoved(long channelId) {
        Set<AllowedChannel> newSet = allowedChannels.stream()
            .filter(c -> c.channelId() != channelId)
            .collect(Collectors.toSet());
        return new AIChannelRestriction(guildId, newSet);
    }
}
```

**驗證規則**:
- `guildId` 必須 > 0
- `allowedChannels` 不可為 null（可為空集合）

---

### 2. AllowedChannel (Value Object)

代表一個被授權使用 AI 功能的頻道。

```java
package ltdjms.discord.aichat.domain;

/**
 * 允許使用 AI 功能的頻道值物件
 */
public record AllowedChannel(
    long channelId,      // 頻道 ID
    String channelName   // 頻道名稱（冗餘儲存以便顯示）
) {
    public AllowedChannel {
        if (channelId <= 0) {
            throw new IllegalArgumentException("頻道 ID 必須大於 0");
        }
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("頻道名稱不可為空");
        }
    }

    /**
     * 從 JDA TextChannel 建立
     */
    public static AllowedChannel from(TextChannel channel) {
        return new AllowedChannel(channel.getIdLong(), channel.getName());
    }
}
```

**驗證規則**:
- `channelId` 必須 > 0
- `channelName` 不可為空或空白

---

## Database Schema

### Table: ai_channel_restriction

```sql
-- V010__ai_channel_restriction.sql
CREATE TABLE ai_channel_restriction (
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    channel_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (guild_id, channel_id)
);

-- 索引：按伺服器查詢優化
CREATE INDEX idx_ai_channel_restriction_guild_id ON ai_channel_restriction(guild_id);

-- 觸發器：自動更新 updated_at
CREATE TRIGGER update_ai_channel_restriction_updated_at
    BEFORE UPDATE ON ai_channel_restriction
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

**Schema 說明**:

| 欄位 | 類型 | 約束 | 說明 |
|------|------|------|------|
| `guild_id` | BIGINT | PK, NOT NULL | Discord 伺服器 ID |
| `channel_id` | BIGINT | PK, NOT NULL | Discord 頻道 ID |
| `channel_name` | TEXT | NOT NULL | 頻道名稱（冗餘儲存） |
| `created_at` | TIMESTAMPTZ | NOT NULL | 建立時間 |
| `updated_at` | TIMESTAMPTZ | NOT NULL | 更新時間 |

**設計決策**:
- **複合主鍵**: `(guild_id, channel_id)` 確保同一伺服器無重複頻道
- **頻道名稱冗餘**: 避免每次查詢需呼叫 Discord API
- **索引**: `guild_id` 索引優化「查詢伺服器所有允許頻道」操作

---

## State Transitions

### AIChannelRestriction 狀態轉換

```
                    [新增頻道]
    ┌─────────────────────────────────────┐
    │                                     │
    │         無限制模式                   │
    │   (allowedChannels = {})             │
    │                                     │
    └─────────────────────────────────────┘
                    │
                    │ [新增第一個頻道]
                    ▼
    ┌─────────────────────────────────────┐
    │                                     │
    │         限制模式                     │
    │   (allowedChannels = {channel1})     │
    │                                     │
    └─────────────────────────────────────┘
                    │
    ┌───────────────┼───────────────┐
    │               │               │
    │ [新增更多]   │ [移除頻道]    │ [移除所有]
    ▼               ▼               ▼
    限制模式       限制模式      無限制模式
 (更多頻道)     (更少頻道)
```

**狀態規則**:
- 空集合 ↔ 非空集合：模式切換（無限制 ↔ 限制）
- 非空集合內變化：仍為限制模式

---

## Validation Matrix

| 操作 | 輸入驗證 | 業務規則 | 錯誤類別 |
|------|----------|----------|----------|
| 新增頻道 | channelId > 0<br>channelName 非空 | 頻道不可重複<br>機器人需有發言權限 | `DUPLICATE_CHANNEL`<br>`INSUFFICIENT_PERMISSIONS` |
| 移除頻道 | channelId > 0 | 頻道必須存在 | `CHANNEL_NOT_FOUND` |
| 查詢設定 | guildId > 0 | 自動移除已刪除頻道 | - |
| 檢查頻道 | guildId > 0<br>channelId > 0 | 空清單 = 允許所有 | - |

---

## Relationships

### 與其他實體的關係

```
┌─────────────────────┐
│   Guild             │
│  (Discord Server)   │
└──────────┬──────────┘
           │ 1
           │
           │ has
           │
┌──────────▼──────────┐
│ AIChannelRestriction│
└──────────┬──────────┘
           │ 1
           │
           │ contains (0..*)
           │
┌──────────▼──────────┐
│  AllowedChannel     │
└─────────────────────┘
```

**關係說明**:
- 一個 Guild 對應一個 AIChannelRestriction
- 一個 AIChannelRestriction 包含 0 到多個 AllowedChannel
- 0 個 AllowedChannel = 無限制模式

---

## Persistence Mapping

### Repository 介面

```java
package ltdjms.discord.aichat.persistence;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.shared.domain.Result;
import ltdjms.shared.domain.DomainError;

import java.util.Set;

/**
 * AI 頻道限制存儲庫
 */
public interface AIChannelRestrictionRepository {

    /**
     * 查詢伺服器的所有允許頻道
     *
     * @param guildId 伺服器 ID
     * @return 允許頻道集合（空集合表示無限制模式）
     */
    Result<Set<AllowedChannel>, DomainError> findByGuildId(long guildId);

    /**
     * 新增允許頻道
     *
     * @param guildId 伺服器 ID
     * @param channel 允許頻道
     * @return 成功或錯誤（如重複頻道）
     */
    Result<AllowedChannel, DomainError> addChannel(long guildId, AllowedChannel channel);

    /**
     * 移除允許頻道
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return 成功或錯誤（如頻道不存在）
     */
    Result<Void, DomainError> removeChannel(long guildId, long channelId);

    /**
     * 批次刪除無效頻道
     *
     * @param guildId 伺服器 ID
     * @param validChannelIds 有效的頻道 ID 集合
     */
    void deleteRemovedChannels(long guildId, Set<Long> validChannelIds);
}
```

### JDBC 實作要點

```java
// 查詢
SELECT channel_id, channel_name
FROM ai_channel_restriction
WHERE guild_id = ?
ORDER BY channel_name;

// 新增
INSERT INTO ai_channel_restriction (guild_id, channel_id, channel_name)
VALUES (?, ?, ?)
ON CONFLICT (guild_id, channel_id) DO NOTHING;

// 移除
DELETE FROM ai_channel_restriction
WHERE guild_id = ? AND channel_id = ?;

// 批次清理
DELETE FROM ai_channel_restriction
WHERE guild_id = ? AND channel_id NOT IN (?, ?, ...);
```

---

## Migration Strategy

### Flyway Migration: V010

**檔案**: `src/main/resources/db/migration/V010__ai_channel_restriction.sql`

**特性**:
- 非破壞性變更（新增資料表）
- 向後相容（不影響現有功能）
- 自動套用（容器啟動時）

**Rollback 策略**:
```sql
DROP TABLE IF EXISTS ai_channel_restriction CASCADE;
```

---

## Testing Considerations

### 單元測試覆蓋

| 測試案例 | 預期行為 |
|----------|----------|
| `AIChannelRestriction` 空清單檢查 | `isUnrestricted()` 返回 true |
| `AIChannelRestriction` 包含頻道檢查 | `isUnrestricted()` 返回 false |
| `isChannelAllowed()` 在無限制模式 | 對任何 channelId 返回 true |
| `isChannelAllowed()` 在限制模式 | 僅對允許的頻道返回 true |
| `AllowedChannel` 建構時 channelId <= 0 | 拋出 IllegalArgumentException |
| `AllowedChannel` 建構時 channelName 為空 | 拋出 IllegalArgumentException |

### 整合測試覆蓋

| 測試案例 | 驗證 |
|----------|------|
| 新增頻道 → 查詢 | 資料正確儲存 |
| 新增重複頻道 | 回傳 DUPLICATE_CHANNEL 錯誤 |
| 移除頻道 → 查詢 | 頻道已移除 |
| 清空所有頻道 | 恢復無限制模式 |

---

## Summary

此資料模型設計遵循 LTDJMS 專案的 DDD 原則：
- **Domain 層**: 純領域邏輯，無基礎設施依賴
- **Persistence 層**: Repository 模式，JDBC 實作
- **Schema 設計**: 符合專案慣例（複合主鍵、時間戳、觸發器）
- **驗證**: 領域模型層級的輸入驗證 + 服務層級的業務規則驗證
