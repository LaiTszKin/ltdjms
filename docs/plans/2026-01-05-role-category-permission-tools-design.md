# Discord 角色與類別權限管理工具設計

**創建日期**: 2026-01-05
**功能**: 添加四個 Discord 權限管理工具，支援類別權限和角色管理

---

## 概述

為 AI Agent 添加四個新工具，用於管理 Discord 伺服器的類別權限和角色設定。所有工具均為**回應式操作**，根據管理員的具體請求執行權限修改。

### 工具列表

1. **編輯類別權限工具** - 修改類別層級的權限覆寫
2. **創建身分組工具** - 創建新的 Discord 角色
3. **讀取身分組權限工具** - 讀取角色的伺服器層級權限
4. **編輯身分組權限工具** - 修改角色的伺服器層級權限

---

## 架構設計

### 類別結構

```
aiagent/
├── domain/
│   └── RoleCreateInfo.java                              // 創建角色的請求參數
└── services/tools/
    ├── LangChain4jModifyCategoryPermissionsTool.java   // 編輯類別權限
    ├── LangChain4jCreateRoleTool.java                  // 創建身分組
    ├── LangChain4jGetRolePermissionsTool.java          // 讀取身分組權限
    └── LangChain4jModifyRolePermissionsTool.java       // 編輯身分組權限
```

### 與現有工具的一致性

所有工具將遵循與 `LangChain4jModifyChannelPermissionsTool` 相同的模式：

- 使用 `InvocationParameters` 獲取 `guildId`、`channelId`、`userId`
- 返回標準化的 JSON 格式
- 完整的錯誤處理和日誌記錄
- 支援多種 ID 格式（純數字、Discord 格式如 `<@&123>`）

### 統一的 JSON 輸出格式

**成功回應**：
```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... }
}
```

**錯誤回應**：
```json
{
  "success": false,
  "error": "錯誤原因"
}
```

---

## 工具一：編輯類別權限工具

### LangChain4jModifyCategoryPermissionsTool

修改 Discord 類別的權限覆寫設定。

### 功能

- 添加/移除允許權限
- 添加/移除拒絕權限
- 自動處理權限衝突（同一權限不能同時在允許和拒絕集合中）

### 方法簽名

```java
@Tool(
    """
    修改 Discord 類別的權限覆寫設定。

    使用場景：
    - 當需要為特定用戶或角色添加或移除類別權限時使用
    - 需要修改現有類別權限覆寫時使用
    - 批量修改多個權限時使用

    注意：
    - 類別權限會影響該類別下設為「同步權限」的所有頻道
    - 權限修改是基於現有權限的增量操作
    """)
public String modifyCategoryPermissions(
    @P(value = "要修改權限的類別 ID", required = true) String categoryId,
    @P(value = "目標 ID（用戶 ID 或角色 ID）", required = true) String targetId,
    @P(value = "目標類型（member 或 role）", required = false) String targetType,
    @P(value = "要添加的允許權限列表", required = false) List<String> allowToAdd,
    @P(value = "要移除的允許權限列表", required = false) List<String> allowToRemove,
    @P(value = "要添加的拒絕權限列表", required = false) List<String> denyToAdd,
    @P(value = "要移除的拒絕權限列表", required = false) List<String> denyToRemove,
    InvocationParameters parameters)
```

### 技術要點

1. **類別驗證**：使用 `guild.getCategoryById()` 獲取類別
2. **權限操作**：類別實現了 `IPermissionContainer` 接口，可以直接使用 `upsertPermissionOverride()`
3. **ID 解析**：支援 `<#123>` 和純數字格式

### 成功回應格式

```json
{
  "success": true,
  "message": "類別權限修改成功",
  "categoryId": "123456789",
  "categoryName": "類別名稱",
  "targetId": "987654321",
  "targetType": "role",
  "before": {
    "allowed": [],
    "denied": []
  },
  "after": {
    "allowed": ["VIEW_CHANNEL", "MESSAGE_SEND"],
    "denied": ["VOICE_CONNECT"]
  }
}
```

### 錯誤處理

| 錯誤場景 | 錯誤訊息 |
|---------|---------|
| categoryId 未提供 | `categoryId 未提供` |
| 找不到類別 | `找不到指定的類別` |
| 找不到目標 | `找不到指定的用戶/角色` |
| 權限不足 | `權限不足: <原因>` |
| 未指定任何操作 | `未指定任何權限修改操作` |

---

## 工具二：創建身分組工具

### LangChain4jCreateRoleTool

創建新的 Discord 角色。

### Domain Model: RoleCreateInfo

```java
package ltdjms.discord.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

/**
 * 創建角色的請求參數。
 *
 * @param name 角色名稱（必填）
 * @param color 顏色 RGB 十進制值（可選，例如 0xFF0000 為紅色）
 * @param permissions 權限集合（可選）
 * @param hoist 是否分隔顯示（可選，預設 false）
 * @param mentionable 是否可提及（可選，預設 false）
 */
public record RoleCreateInfo(
    @JsonProperty("name") String name,
    @JsonProperty(value = "color", required = false) Integer color,
    @JsonProperty(value = "permissions", required = false) Set<PermissionEnum> permissions,
    @JsonProperty(value = "hoist", required = false) Boolean hoist,
    @JsonProperty(value = "mentionable", required = false) Boolean mentionable) {

  /** 權限枚舉（與 ModifyPermissionSetting.PermissionEnum 共享） */
  public enum PermissionEnum {
    ADMINISTRATOR,
    MANAGE_CHANNELS,
    MANAGE_ROLES,
    MANAGE_SERVER,
    VIEW_CHANNEL,
    MESSAGE_SEND,
    MESSAGE_HISTORY,
    MESSAGE_ATTACH_FILES,
    MESSAGE_EMBED_LINKS,
    VOICE_CONNECT,
    VOICE_SPEAK,
    PRIORITY_SPEAKER,
    MANAGE_MESSAGES,
    MESSAGE_READ,
    MESSAGE_ADD_REACTION,
    MESSAGE_EXT_EMOJI,
    MANAGE_WEBHOOKS,
    KICK_MEMBERS,
    BAN_MEMBERS
  }

  public RoleCreateInfo {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("角色名稱不能為空");
    }
    if (color == null) {
      color = 0; // 預設無顏色
    }
    if (permissions == null) {
      permissions = Set.of();
    }
    if (hoist == null) {
      hoist = false;
    }
    if (mentionable == null) {
      mentionable = false;
    }
  }
}
```

### 方法簽名

```java
@Tool(
    """
    創建新的 Discord 身分組（角色）。

    使用場景：
    - 當需要創建新的角色分組時使用
    - 設置特定權限的角色時使用

    返回資訊：
    - 新創建角色的完整資訊
    - 包括角色 ID（可用於後續操作）
    """)
public String createRole(
    @P(value = "角色名稱", required = true) String name,
    @P(value = "顏色（RGB 十六進制字串，例如 FF0000 為紅色）", required = false) String color,
    @P(value = "權限列表", required = false) List<String> permissions,
    @P(value = "是否分隔顯示（將角色在成員列表中單獨顯示）", required = false) Boolean hoist,
    @P(value = "是否可提及（允許 @role）", required = false) Boolean mentionable,
    InvocationParameters parameters)
```

### 技術要點

1. **使用 JDA 的 Role.create**：`guild.createRole().setName(name).complete()`
2. **顏色處理**：支援 RGB 十六進制字串格式轉換為整數
3. **權限設置**：使用 `Permission.getPermissions(permissions)` 轉換為位標誌
4. **返回結果**：包含新創建角色的 ID 和完整資訊

### 成功回應格式

```json
{
  "success": true,
  "message": "角色創建成功",
  "role": {
    "id": "123456789012345678",
    "name": "新角色名稱",
    "color": 16711680,
    "colorHex": "#FF0000",
    "permissions": ["VIEW_CHANNEL", "MESSAGE_SEND"],
    "permissionCount": 2,
    "hoisted": true,
    "mentionable": false,
    "position": 0
  }
}
```

### 錯誤處理

| 錯誤場景 | 錯誤訊息 |
|---------|---------|
| name 未提供 | `角色名稱不能為空` |
| 找不到伺服器 | `找不到伺服器` |
| 權限不足 | `權限不足: <原因>` |
| 名稱過長 | `角色名稱過長（最多 100 字符）` |

---

## 工具三：讀取身分組權限工具

### LangChain4jGetRolePermissionsTool

讀取 Discord 角色的伺服器層級權限。

### 方法簽名

```java
@Tool(
    """
    讀取 Discord 角色的伺服器層級權限。

    使用場景：
    - 當需要查看角色擁有的權限時使用
    - 權限審查或問題排查時使用

    返回資訊：
    - 角色基本資訊
    - 完整的伺服器層級權限列表
    - 是否為管理員角色
    """)
public String getRolePermissions(
    @P(value = "角色 ID", required = true) String roleId,
    InvocationParameters parameters)
```

### 技術要點

1. **獲取角色**：使用 `guild.getRoleById()`
2. **讀取權限**：使用 `role.getPermissions()` 獲取 `EnumSet<Permission>`
3. **管理員檢測**：檢查是否包含 `ADMINISTRATOR` 權限
4. **權限排序**：按權限名稱字母順序排列

### 成功回應格式

```json
{
  "success": true,
  "role": {
    "id": "123456789012345678",
    "name": "版主",
    "color": 3447003,
    "colorHex": "#3498DB",
    "hoisted": true,
    "mentionable": true,
    "position": 5,
    "managed": false,
    "permissions": [
      "ADMINISTRATOR",
      "MANAGE_CHANNELS",
      "MANAGE_ROLES",
      "VIEW_CHANNEL",
      "MESSAGE_SEND"
    ],
    "permissionCount": 5,
    "isAdmin": true
  }
}
```

### 錯誤處理

| 錯誤場景 | 錯誤訊息 |
|---------|---------|
| roleId 未提供 | `roleId 未提供` |
| 找不到角色 | `找不到指定的角色` |
| 無效的 ID 格式 | `無效的 ID 格式` |

---

## 工具四：編輯身分組權限工具

### LangChain4jModifyRolePermissionsTool

修改 Discord 角色的伺服器層級權限（注意：這是修改角色本身的基本權限，不是頻道層級的權限覆寫）。

### 方法簽名

```java
@Tool(
    """
    修改 Discord 角色的伺服器層級權限。

    使用場景：
    - 當需要添加或移除角色的伺服器權限時使用
    - 調整角色權限等級時使用

    注意：
    - 這會修改角色本身的基本權限
    - 不是修改頻道層級的權限覆寫
    - 權限修改是基於現有權限的增量操作
    """)
public String modifyRolePermissions(
    @P(value = "角色 ID", required = true) String roleId,
    @P(value = "要添加的權限列表", required = false) List<String> permissionsToAdd,
    @P(value = "要移除的權限列表", required = false) List<String> permissionsToRemove,
    InvocationParameters parameters)
```

### 技術要點

1. **獲取現有權限**：使用 `role.getPermissionsRaw()` 獲取 `Long` 類型的權限位標誌
2. **計算新權限**：使用位運算添加或移除權限
   - 添加：`currentPermissions | permissionToAdd`
   - 移除：`currentPermissions & ~permissionToRemove`
3. **應用修改**：使用 `role.getManager().setPermissions(newPermissions).complete()`
4. **權限解析**：使用 `Permission.getPermissions(permissionLong)` 轉換為 `EnumSet<Permission>`

### 成功回應格式

```json
{
  "success": true,
  "message": "角色權限修改成功",
  "role": {
    "id": "123456789012345678",
    "name": "版主"
  },
  "before": {
    "permissions": ["VIEW_CHANNEL", "MESSAGE_SEND"],
    "count": 2,
    "raw": 66048
  },
  "after": {
    "permissions": ["VIEW_CHANNEL", "MESSAGE_SEND", "MANAGE_CHANNELS", "MANAGE_ROLES"],
    "count": 4,
    "raw": 268501040
  },
  "changes": {
    "added": ["MANAGE_CHANNELS", "MANAGE_ROLES"],
    "removed": [],
    "addedCount": 2,
    "removedCount": 0
  }
}
```

### 錯誤處理

| 錯誤場景 | 錯誤訊息 |
|---------|---------|
| roleId 未提供 | `roleId 未提供` |
| 找不到角色 | `找不到指定的角色` |
| 權限不足 | `權限不足: <原因>` |
| 未指定任何操作 | `未指定任何權限修改操作` |
| 角色為機器人角色 | `無法修改機器人角色的權限` |

---

## 依賴注入配置

### AIAgentModule 更新

```java
@Provides
@Singleton
public LangChain4jModifyCategoryPermissionsTool provideModifyCategoryPermissionsTool() {
  return new LangChain4jModifyCategoryPermissionsTool();
}

@Provides
@Singleton
public LangChain4jCreateRoleTool provideCreateRoleTool() {
  return new LangChain4jCreateRoleTool();
}

@Provides
@Singleton
public LangChain4jGetRolePermissionsTool provideGetRolePermissionsTool() {
  return new LangChain4jGetRolePermissionsTool();
}

@Provides
@Singleton
public LangChain4jModifyRolePermissionsTool provideModifyRolePermissionsTool() {
  return new LangChain4jModifyRolePermissionsTool();
}
```

### LangChain4jAIChatService 更新

在建構函數中添加新工具參數並註冊到 Agent：

```java
.tool(modifyCategoryPermissionsTool)
.tool(createRoleTool)
.tool(getRolePermissionsTool)
.tool(modifyRolePermissionsTool)
```

---

## 測試策略

### 單元測試

每個工具都需要對應的單元測試類別：

- `LangChain4jModifyCategoryPermissionsToolTest`
- `LangChain4jCreateRoleToolTest`
- `LangChain4jGetRolePermissionsToolTest`
- `LangChain4jModifyRolePermissionsToolTest`

測試覆蓋：
- 參數驗證
- 錯誤處理
- 正常操作流程
- ID 解析（多種格式）

### 整合測試

使用 Testcontainers 進行真實 Discord API 測試：

- `LangChain4jModifyCategoryPermissionsToolIntegrationTest`
- `LangChain4jCreateRoleToolIntegrationTest`
- `LangChain4jGetRolePermissionsToolIntegrationTest`
- `LangChain4jModifyRolePermissionsToolIntegrationTest`

---

## 檔案清單

### Domain
- `src/main/java/ltdjms/discord/aiagent/domain/RoleCreateInfo.java`

### Tools
- `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyCategoryPermissionsTool.java`
- `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jCreateRoleTool.java`
- `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetRolePermissionsTool.java`
- `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyRolePermissionsTool.java`

### Unit Tests
- `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyCategoryPermissionsToolTest.java`
- `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jCreateRoleToolTest.java`
- `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetRolePermissionsToolTest.java`
- `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyRolePermissionsToolTest.java`

### Integration Tests
- `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jModifyCategoryPermissionsToolIntegrationTest.java`
- `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jCreateRoleToolIntegrationTest.java`
- `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jGetRolePermissionsToolIntegrationTest.java`
- `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jModifyRolePermissionsToolIntegrationTest.java`

### DI Configuration
- `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java` (修改)
- `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java` (修改)

---

## 技術堆疊

- Java 17
- LangChain4j
- JDA 5.2.2
- JUnit 5
- Mockito
- Testcontainers

---

## 相關文檔

- [修改頻道權限工具實作計畫](/Users/tszkinlai/Coding/LTDJMS/docs/plans/2026-01-02-modify-channel-permissions-tool.md)
- [列出角色工具設計](/Users/tszkinlai/Coding/LTDJMS/docs/plans/2026-01-01-list-roles-tool-design.md)
