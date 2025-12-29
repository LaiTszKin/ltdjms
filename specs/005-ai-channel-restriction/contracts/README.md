# Contracts: AI Channel Restriction

**Feature**: AI Channel Restriction (005-ai-channel-restriction)

## Overview

此功能不涉及外部 API 契約。AI 頻道限制是 Discord 機器人內部功能，所有互動透過：
1. **Discord UI**: 管理面板按鈕與選單
2. **內部服務介面**: `AIChannelRestrictionService`

## Internal Service Contract

### AIChannelRestrictionService

```java
package ltdjms.discord.aichat.services;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.shared.domain.Result;
import ltdjms.shared.domain.DomainError;

import java.util.Set;

/**
 * AI 頻道限制服務
 */
public interface AIChannelRestrictionService {

    /**
     * 檢查頻道是否被允許使用 AI 功能
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return true 如果頻道被允許（或在無限制模式）
     */
    boolean isChannelAllowed(long guildId, long channelId);

    /**
     * 獲取伺服器的所有允許頻道
     *
     * @param guildId 伺服器 ID
     * @return 允許頻道集合（空集合表示無限制模式）
     */
    Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId);

    /**
     * 新增允許頻道
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return 新增的頻道或錯誤
     */
    Result<AllowedChannel, DomainError> addAllowedChannel(long guildId, long channelId);

    /**
     * 移除允許頻道
     *
     * @param guildId 伺服器 ID
     * @param channelId 頻道 ID
     * @return 成功或錯誤
     */
    Result<Void, DomainError> removeAllowedChannel(long guildId, long channelId);

    /**
     * 批次新增允許頻道
     *
     * @param guildId 伺服器 ID
     * @param channelIds 頻道 ID 集合
     * @return 成功新增的頻道與失敗的頻道錯誤訊息
     */
    Result<BatchResult, DomainError> batchAddAllowedChannels(
        long guildId, Set<Long> channelIds
    );

    /**
     * 批次移除允許頻道
     *
     * @param guildId 伺服器 ID
     * @param channelIds 頻道 ID 集合
     * @return 成功或錯誤
     */
    Result<Void, DomainError> batchRemoveAllowedChannels(
        long guildId, Set<Long> channelIds
    );
}

/**
 * 批次操作結果
 */
public record BatchResult(
    Set<AllowedChannel> succeeded,  // 成功的頻道
    Map<Long, String> failed        // 失敗的頻道 ID → 錯誤訊息
) {}
```

## Discord UI Interactions

### 管理面板流程

```
┌─────────────────────────────────────────────────────┐
│  管理員執行 /admin-panel                              │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  顯示主選單                                          │
│  ┌─────────────────────────────────────────────┐   │
│  │ [📊 餘額管理] [🎮 代幣管理] [🏪 商品管理]      │   │
│  │ [🤖 AI 頻道設定]                              │   │
│  └─────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────┘
                       │ 點擊「AI 頻道設定」
                       ▼
┌─────────────────────────────────────────────────────┐
│  顯示 AI 頻道設定頁面                                │
│  ┌─────────────────────────────────────────────┐   │
│  │ 目前允許的頻道：                              │   │
│  │ • #general                                  │   │
│  │ • #ai-chat                                  │   │
│  │                                              │   │
│  │ [➕ 新增頻道] [➖ 移除頻道] [⬅️ 返回]          │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 按鈕 ID 約定

| 按鈕 ID | 說明 |
|---------|------|
| `admin_panel_ai_channel` | 進入 AI 頻道設定頁面 |
| `admin_panel_ai_channel_add` | 開啟新增頻道選單 |
| `admin_panel_ai_channel_remove` | 開啟移除頻道選單 |
| `admin_panel_ai_channel_back` | 返回主選單 |

### 選單 ID 約定

| 選單 ID | 說明 |
|---------|------|
| `ai_channel_select_add` | 選擇要新增的頻道 |
| `ai_channel_select_remove` | 選擇要移除的頻道 |

## Error Response Format

所有錯誤透過 Discord ephemeral 訊息顯示：

```
❌ 操作失敗

該頻道已在清單中
```

```
✅ 頻道已新增

已將 #ai-chat 加入允許清單
```

## Event Publishing

### AIChannelRestrictionChangedEvent

```java
package ltdjms.discord.aichat.domain;

import ltdjms.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Set;

/**
 * AI 頻道限制變更事件
 */
public record AIChannelRestrictionChangedEvent(
    long guildId,
    Set<AllowedChannel> allowedChannels,
    Instant timestamp
) implements DomainEvent {}
```

**發布時機**: 新增或移除允許頻道後

**監聽器**:
- `AIChannelRestrictionCacheListener` (如啟用快取)

---

## Summary

此功能的「契約」主要是內部服務介面與 Discord UI 互動模式，不涉及外部 API 呼叫。
