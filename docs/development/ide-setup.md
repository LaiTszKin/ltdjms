# 開發指南：IDE 設定

本文件說明如何在 VS Code 與 IntelliJ IDEA 中設定 LTDJMS 專案的開發環境。

## 1. 前置需求

### 1.1 通用需求

| 需求 | 版本 | 說明 |
|------|------|------|
| Java | 17+ | 專案使用 Java 17 特性 |
| Maven | 3.8+ | 建置與依賴管理 |
| Git | 任意版本 | 版本控制 |

### 1.2 版本管理工具（推薦）

- **Java**: [SDKMAN!](https://sdkman.io/)（macOS/Linux）或 [Azul Zulu](https://www.azul.com/downloads/?version=java-17-lts&os=windows&architecture=x86-64-bit&package=jdk)（Windows）
- **Maven**: [mvnvm](https://github.com/mvnvm/mvnvm)（多版本管理）或直接安裝

## 2. VS Code 設定

### 2.1 安裝必要擴充套件

在 VS Code 中安裝以下擴充套件：

| 擴充套件 | ID | 用途 |
|----------|-----|------|
| Extension Pack for Java | `vscjava.vscode-java-pack` | Java 開發全套工具 |
| Maven for Java | `vscjava.vscode-maven` | Maven 支援 |
| Project Manager for Java | `vscjava.vscode-java-project-manager` | 專案管理 |
| Lombok Annotations Support | `vscjava.vscode-lombok` | Lombok 支援 |
| Spotless | `richardwillis.vscode-spotless-gradle` | 程式碼格式化 |

安裝方式：在 Extensions 面板搜尋上述 ID 或直接搜尋套件名稱。

### 2.2 開啟專案

1. 開啟 VS Code
2. `File` → `Open Folder` → 選擇 LTDJMS 根目錄
3. 等待 Java 擴充套件自動載入專案（右下角會顯示 Loading projects）

### 2.3 設定 Java 版本

建立或編輯 `.vscode/settings.json`：

```json
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.import.gradle.enabled": false,
    "java.import.maven.enabled": true,
    "java.import.maven.autoDetectChanges": true,
    "java.import.exclusions": [
        "**/target"
    ],
    "java.project.sourcePaths": [
        "src/main/java",
        "src/test/java"
    ],
    "java.compile.nullAnalysis.mode": "automatic"
}
```

### 2.4 Maven 工具視窗

按 `Ctrl+Shift+P`（macOS: `Cmd+Shift+P`）開啟 Command Palette：

- `Maven: Add a dependency` - 新增依賴
- `Maven: Execute Goals` - 執行 Maven 目標
- `Maven: Show Dependencies` - 顯示依賴樹
- `Maven: Refresh` - 刷新專案

### 2.5 程式碼格式化

安裝 Spotless 擴充套件後，設定格式化快捷鍵：

```json
// .vscode/keybindings.json
[
    {
        "key": "shift+alt+f",
        "command": "spotlessApply",
        "when": "editorTextFocus"
    }
]
```

格式化專案：
```bash
mvn spotless:apply
```

### 2.6 測試執行

安裝 Test Runner for Java 擴充套件（已包含在 Extension Pack 中）：

- 在測試類別上按右鍵 → `Run` 或 `Debug`
- 或使用 Command Palette：`Java: Run All Tests`

## 3. IntelliJ IDEA 設定

### 3.1 開啟專案

1. `File` → `Open` → 選擇 LTDJMS 根目錄的 `pom.xml`
2. IDEA 會偵測為 Maven 專案，點擊 `Open as Project`
3. 等待索引建立完成

### 3.2 設定 Java SDK

1. `File` → `Project Structure` → `Project Settings` → `Project`
2. 確認 `SDK` 為 Java 17+
3. `Language level` 設定為 `17`

### 3.3 Maven 設定

1. `File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven`
2. 設定 Maven home directory（若使用專案內建 wrapper 可留空）
3. `User settings file` 確認為 `${MAVEN_USER_HOME}/settings.xml` 或使用預設
4. 勾選 `Always update snapshots` 與 `Use Maven output directories`

### 3.4 程式碼風格（Spotless）

1. `File` → `Settings` → `Editor` → `Code Style`
2. 點擊 `Import Scheme` → 選擇 `checkstyle` 或手動設定
3. 或使用 Maven 格式化指令：
   ```bash
   mvn spotless:apply
   ```

### 3.5 Lombok 支援

1. `File` → `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`
2. 勾選 `Enable annotation processing`
3. 確認 Lombok 插件已安裝（Plugins 搜尋 "Lombok"）

### 3.6 執行與除錯設定

建立執行設定：

1. `Run` → `Edit Configurations...`
2. 按 `+` → `Application`
3. 設定：
   - **Name**: `LTDJMS Bot`
   - **Main class**: `ltdjms.discord.currency.bot.DiscordCurrencyBot`
   - **Program arguments**: （留空）
   - **Working directory**: `${MODULE_WORKING_DIR}`
   - **Environment variables**:
     ```
     DISCORD_BOT_TOKEN=your-token
     DB_URL=jdbc:postgresql://localhost:5432/currency_bot
     DB_USERNAME=postgres
     DB_PASSWORD=postgres
     ```

### 3.7 測試執行

- 右鍵測試類別/方法 → `Run` 或 `Debug`
- 或使用 `Ctrl+Shift+F10`（Windows/Linux）或 `Cmd+Shift+R`（macOS）

## 4. 程式碼範本

### 4.1 Service 類別範本

在 IDE 中建立 Live Template：

```java
/**
 * ${Description}
 */
public class ${ClassName} {

    private final ${Dependency} ${dependency};

    public ${ClassName}(${Dependency} ${dependency}) {
        this.${dependency} = ${dependency};
    }

    // TODO: 實作方法
}
```

### 4.2 Repository 介面範本

```java
/**
 * ${Description}
 */
public interface ${InterfaceName} {
    Result<${DomainType}, DomainError> findById(Long id);
    Result<List<${DomainType}>, DomainError> findByGuildId(Long guildId);
}
```

## 5. 常見問題排除

### 5.1 VS Code 無法辨識 Java 專案

- 確認已安裝 Java Extension Pack
- 嘗試 `Ctrl+Shift+P` → `Java: Reload Window`
- 刪除 `.vscode` 資料夾中的 `java.configuration.json` 讓擴充重新產生

### 5.2 Maven 依賴下載緩慢

修改 `~/.m2/settings.xml`：

```xml
<mirrors>
    <mirror>
        <id>aliyun</id>
        <mirrorOf>central</mirrorOf>
        <name>Aliyun Maven Mirror</name>
        <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
</mirrors>
```

### 5.3 Lombok 產生警告

確認 IDE 與 Lombok 版本相容：
- IntelliJ 2023.x+ 支援最新 Lombok
- VS Code 需安裝 Lombok Annotations Support 擴充

### 5.4 測試無法連線資料庫

確認：
1. PostgreSQL 容器已啟動：`make db-up`
2. 環境變數已正確設定（`.env` 檔案）
3. 資料庫已建立：`currency_bot`

## 6. 快捷鍵參考

### 6.1 VS Code

| 功能 | Windows/Linux | macOS |
|------|---------------|-------|
| 格式化程式碼 | `Shift+Alt+F` | `Shift+Option+F` |
| 快速修復 | `Ctrl+.` | `Cmd+.` |
| 搜尋符號 | `Ctrl+Shift+O` | `Cmd+Shift+O` |
| 顯示參考 | `F12` | `F12` |
| 執行測試 | `Ctrl+Shift+P` → `Java: Run Tests` | 同左 |

### 6.2 IntelliJ IDEA

| 功能 | Windows/Linux | macOS |
|------|---------------|-------|
| 格式化程式碼 | `Ctrl+Alt+L` | `Cmd+Option+L` |
| 最佳化引入 | `Ctrl+Alt+O` | `Control+Option+O` |
| 搜尋符號 | `Ctrl+Shift+F12` | `Cmd+Shift+F12` |
| 顯示參考 | `Ctrl+B` | `Cmd+B` |
| 執行測試 | `Ctrl+Shift+F10` | `Control+Shift+R` |

## 7. 下一個步驟

設定完成後，建議依序閱讀：

1. `docs/development/workflow.md` - 開發工作流程
2. `docs/development/testing.md` - 測試策略
3. `docs/getting-started/quickstart.md` - 快速入門驗證環境

如有任何設定問題，請參考 `docs/operations/troubleshooting.md` 或開立 GitHub Issue。
