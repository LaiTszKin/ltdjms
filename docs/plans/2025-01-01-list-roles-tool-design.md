# List Roles Tool 設計文檔

**創建日期**: 2025-01-01
**功能**: 獲取 Discord 伺服器內所有角色的 ID 和名稱

## 概述

為 AI Agent 添加一個新工具，能夠獲取 Discord 伺服器內所有角色的 ID 和名稱，按權限等級排序。

## 需求總結

- 輸出內容：ID + 角色名稱
- 篩選功能：不需要
- 排序方式：按權限等級排序（從高到低，@everyone 在最後）
- @everyone 角色：包含在結果中
- JSON 格式：與 `LangChain4jListChannelsTool` 一致

## 架構設計

### 類別結構

```
LangChain4jListRolesTool
├── @Tool("獲取 Discord 伺服器中的所有角色資訊...")
├── listRoles(InvocationParameters) -> String
├── buildRoleInfo(Role) -> Map<String, Object>
├── buildJsonResult(List<Map<String, Object>>) -> String
└── buildErrorResponse(String) -> String
```

### 資料結構

每個角色資訊：
```java
{
  "id": long,      // 角色 ID
  "name": String   // 角色名稱
}
```

### JSON 輸出格式

```json
{
  "count": 3,
  "roles": [
    {
      "id": 123456789,
      "name": "管理員"
    },
    {
      "id": 987654321,
      "name": "版主"
    },
    {
      "id": 111111111,
      "name": "@everyone"
    }
  ]
}
```

## 實作細節

### 排序邏輯

使用 JDA 的 `Role.compareTo()` 方法：
```java
List<Role> roles = new ArrayList<>(guild.getRoles());
roles.sort(Role::compareTo);
```

這會自動按權限等級排序，@everyone 會排在最後。

### 錯誤處理

| 錯誤場景 | 錯誤訊息 |
|---------|---------|
| guildId 未設置 | `{"success": false, "error": "guildId 未設置"}` |
| 找不到伺服器 | `{"success": false, "error": "找不到伺服器"}` |
| 其他異常 | `{"success": false, "error": "獲取角色列表失敗: <原因>"}` |

## 測試策略

### 單元測試

`LangChain4jListRolesToolTest`：
- 成功案例：驗證 JSON 格式和排序
- guildId 未設置
- 找不到伺服器
- 空角色列表

### 整合測試

`LangChain4jListRolesToolIntegrationTest`：使用 Testcontainers 測試真實 API 互動。

## 依賴注入

在 `AIAgentModule` 中註冊：
```java
@Provides
@Singleton
LangChain4jListRolesTool provideListRolesTool() {
  return new LangChain4jListRolesTool();
}
```

## 檔案清單

- `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jListRolesTool.java`
- `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jListRolesToolTest.java`
- `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jListRolesToolIntegrationTest.java`
