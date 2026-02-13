# 貢獻指南

感謝你有興趣為 LTDJMS Discord Bot 貢獻！本文件提供貢獻流程的指引，幫助你快速上手並確保貢獻品質。

## 開發環境設定

### 前置需求
- Java 17 或更高版本
- Maven 3.8+ 或相容版本
- Docker 與 Docker Compose（用於本地開發與測試）
- Git

### 初始設定
1. Fork 本專案到你的 GitHub 帳戶
2. 克隆你的 fork 到本地：
   ```bash
   git clone https://github.com/<your-username>/LTDJMS.git
   cd LTDJMS
   ```
3. 設定上游遠端倉庫：
   ```bash
   git remote add upstream https://github.com/LaiTszKin/LTDJMS.git
   ```

### 環境變數設定
複製環境變數範本並填入你的 Discord Bot Token：
```bash
cp .env.example .env
# 編輯 .env 檔案，填入 DISCORD_BOT_TOKEN 等設定
```

## 開發流程

### 測試驅動開發 (TDD)
本專案嚴格遵循測試驅動開發流程。新增功能或修改時請遵守：

1. **先寫測試**：為新功能或變更撰寫測試，描述預期行為
2. **執行測試**：執行 `make test` 確認新測試失敗（紅燈）
3. **實作最小功能**：撰寫最簡單的程式碼讓測試通過（綠燈）
4. **重構**：在測試保持通過的情況下優化程式碼

詳細測試指南請參閱 [docs/development/testing.md](docs/development/testing.md)。

### 分支策略
- `main` 分支：穩定版本，僅接受通過審查的 Pull Request
- 功能分支：從 `main` 分支建立，命名格式：`feature/<簡述>` 或 `fix/<問題簡述>`
- 發佈分支：`release/<版本號>`，用於版本發佈準備

### 提交訊息規範
使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：
```
<類型>[可選範圍]: <描述>

[可選正文]

[可選頁尾]
```

常用類型：
- `feat`: 新功能
- `fix`: 錯誤修復
- `docs`: 文件更新
- `style`: 程式碼風格調整（不影響功能）
- `refactor`: 重構（不新增功能或修復錯誤）
- `test`: 測試相關
- `chore`: 構建過程或輔助工具的變動

範例：
```
feat(panel): 新增使用者面板代幣流水查詢功能
fix(currency): 修復餘額調整時的負數檢查問題
docs: 更新快速入門指南
```

## 代碼風格與品質

### Java 程式碼規範
- 遵循 Java 命名慣例（類別使用 PascalCase，變數使用 camelCase）
- 使用 4 空格縮排（非 Tab）
- 行長度限制：120 字元
- 為公開 API 提供 Javadoc 註解
- 使用 `@Nullable` 和 `@Nonnull` 註解標示可空性

### 架構模式
- **分層設計**：遵循 domain → persistence → services → commands 分層
- **依賴注入**：使用 Dagger 2 管理依賴，避免直接 new 物件
- **錯誤處理**：使用 `Result<T, DomainError>` 處理預期錯誤，非預期錯誤使用例外
- **不可變性**：領域模型應盡可能設計為不可變（immutable）

### 測試規範
- 單元測試：測試單一類別或方法，使用 Mockito 模擬依賴
- 整合測試：測試跨層功能，使用 Testcontainers 啟動真實資料庫
- 契約測試：確保對外介面（如 Discord 指令回應）符合契約
- 效能測試：監控指令處理延遲與資源使用

測試覆蓋率要求：**至少 80% 行覆蓋率**，使用 JaCoCo 檢查。

## 貢獻流程

### 1. 建立議題 (Issue)
在提交 Pull Request 前，建議先建立議題討論：
- 錯誤報告：描述問題、重現步驟、預期與實際行為
- 功能建議：說明需求、使用場景、預期效益
- 改進提案：提出具體優化方案

### 2. 開發新功能
1. 從 `main` 分支建立新分支
2. 遵循 TDD 流程實作功能
3. 確保所有測試通過
4. 更新相關文件（如有需要）

### 3. 提交 Pull Request
1. 推送分支到你的 fork
2. 在 GitHub 建立 Pull Request 到上游 `main` 分支
3. 填寫 PR 模板，包括：
   - 變更摘要
   - 相關議題編號
   - 測試計畫
   - 檢查清單

### 4. 審查流程
- 至少需要一位維護者審核通過
- 確保 CI/CD 流程全部通過
- 可能需要根據回饋進行修改

### 5. 合併與清理
- 使用 squash merge 合併 PR
- 刪除已合併的功能分支
- 同步你的 fork 與上游倉庫

## 專案結構導覽

```
LTDJMS/
├── src/main/java/ltdjms/discord/
│   ├── currency/           # 貨幣系統模組
│   ├── dispatch/           # 派單護航模組
│   ├── gametoken/          # 遊戲代幣與小遊戲模組
│   ├── panel/              # 使用者與管理面板模組
│   └── shared/             # 共用基礎設施
├── src/test/              # 測試程式碼
├── docs/                  # 專案文件
└── prompts/               # AI 提示詞
```

詳細架構說明請參閱：
- [系統架構總覽](docs/architecture/overview.md)
- [模組說明](docs/modules/)
- [開發指南](docs/development/)

## 特殊工作流程

### 功能計畫文件（docs/plans）
對於中大型需求（例如新模組、資料庫變更、跨模組流程調整），請先補齊計畫文件：
1. 在 `docs/plans/` 建立新計畫（建議命名：`YYYY-MM-DD-feature-name.md`）
2. 說明需求背景、核心流程、資料影響與測試策略
3. 在 PR 內附上計畫文件連結
4. 依核准內容實作並同步更新模組文件

### 本地化 (i18n)
本專案支援繁體中文（zh-TW）與英文：
- Slash Command 的本地化集中在 `src/main/java/ltdjms/discord/shared/localization/CommandLocalizations.java`
- 新增指令時，請同步補上 `name`、`description` 與 option 的本地化映射
- 更新後請執行對應測試（例如 `CommandLocalizationsTest`）確認 key 完整

## 疑難排解

### 常見問題
1. **測試失敗**：確認 Docker 正在運行，資料庫容器已啟動
2. **建置錯誤**：執行 `mvn clean` 後重新建置
3. **Discord 連線問題**：檢查 Bot Token 權限與網路設定

### 尋求協助
- 查看 [docs/](docs/) 目錄中的相關文件
- 檢查現有議題中是否有類似問題
- 在 PR 或議題中描述詳細錯誤訊息與重現步驟

## 行為準則

本專案遵守 [貢獻者公約](CODE_OF_CONDUCT.md)。請尊重所有貢獻者，維持友善與專業的討論環境。

---

感謝你的貢獻！你的每一行程式碼、每一份文件、每一個建議都能讓這個專案變得更好。
