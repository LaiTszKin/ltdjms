# Quick Start: External Prompts Loader

**Feature**: External Prompts Loader
**Branch**: `004-external-prompts-loader`
**Date**: 2025-12-28

## Overview

外部提示詞載入功能允許您將機器人的系統提示詞（system prompt）從程式碼中分離，存放在專案根目錄的 `prompts/` 資料夾中。修改提示詞後立即生效，無需重啟機器人。

---

## Setup Guide

### Step 1: 建立 prompts 資料夾

在專案根目錄（與 `pom.xml` 同層級）建立 `prompts` 資料夾：

```bash
cd /path/to/LTDJMS
mkdir prompts
```

### Step 2: 建立提示詞檔案

在 `prompts/` 資料夾中建立 markdown 檔案（`.md` 副檔名）：

```bash
# 範例：建立機器人人格檔案
cat > prompts/personality.md << 'EOF'
# 機器人人格

你是一個友善且有幫助的 AI 助手，名為「小幫手」。

## 特點
- 禮貌且友善
- 提供準確的資訊
- 承認不知道的事情
EOF

# 範例：建立使用規則檔案
cat > prompts/rules.md << 'EOF'
# 使用規則

1. 使用繁體中文回應
2. 簡潔明確，避免冗長
3. 不生成有害或不當內容
4. 保護使用者隱私
EOF
```

### Step 3: 設定配置（可選）

使用預設配置時無需任何設定。如需自訂，在 `.env` 檔案中添加：

```properties
# prompts 資料夾路徑（相對或絕對）
PROMPTS_DIR_PATH=./prompts

# 單一檔案大小限制（位元組，預設 1MB）
PROMPT_MAX_SIZE_BYTES=1048576
```

### Step 4: 重新啟動機器人

```bash
mvn clean package
java -jar target/ltdjms-0.14.4.jar
```

或使用 Docker：

```bash
make start-dev
```

### Step 5: 測試提示詞

在 Discord 中提及機器人並發送訊息：

```
@小幫手 你好！
```

機器人應該根據 `prompts/` 中的檔案回應，符合您設定的人格和規則。

---

## 提示詞檔案格式

### 檔案命名規則

- 副檔名必須是 `.md`
- 檔案名稱將被轉換為區間標題：
  - 移除 `.md` 副檔名
  - 替換連字符（`-`）和底線（`_`）為空格
  - 轉換為大寫

**範例**：
| 檔案名稱 | 區間標題 |
|---------|---------|
| `personality.md` | `PERSONALITY` |
| `bot-rules_v2.md` | `BOT RULES V2` |
| `系統設定.md` | `系統設定` |

### 提示詞內容格式

使用標準 Markdown 語法：

```markdown
# 標題

這是提示詞的內容，支援：
- 清單
- **粗體**
- *斜體*
- [連結](https://example.com)

## 子標題

更多內容...

1. 編號清單
2. 第二項
```

### 多檔案合併結果

當 `prompts/` 資料夾包含多個檔案時，系統會按字母順序合併：

```
prompts/
├── personality.md
└── rules.md
```

合併後的 system prompt：

```
=== PERSONALITY ===
# 機器人人格
你是一個友善的 AI 助手...

=== RULES ===
# 使用規則
1. 使用繁體中文回應
2. 簡潔明確...
```

---

## 常見問題排查

### Q1: 機器人沒有使用新的提示詞

**可能原因**：
1. 檔案編碼不是 UTF-8
2. 檔案副檔名不是 `.md`
3. 檔案位於錯誤的資料夾

**解決方法**：
```bash
# 檢查檔案編碼
file -i prompts/personality.md

# 轉換為 UTF-8（如果需要）
iconv -f BIG5 -t UTF-8 personality.md > personality-utf8.md

# 檢查副檔名
ls -la prompts/

# 檢查檔案位置
pwd  # 確認在專案根目錄
```

### Q2: 某個檔案被跳過

**查看日誌**：
```bash
# Docker 部署
make logs | grep -i prompt

# 本地執行
tail -f logs/application.log | grep -i prompt
```

**可能原因**：
- 檔案超過大小限制（預設 1MB）
- 檔案讀取權限不足
- 檔案編碼無效

### Q3: prompts 資料夾不存在會怎樣？

系統會使用空的 system prompt（無額外指示），AI API 將使用模型的預設行為。

日誌會顯示：
```
INFO  PromptLoader - Prompts directory not found: ./prompts, using empty system prompt
```

### Q4: 如何測試提示詞是否載入成功？

**方法 1：查看日誌**
```bash
make logs | grep "Loaded.*prompt files"
```

輸出範例：
```
INFO  PromptLoader - Loaded 2 prompt files from ./prompts (0 skipped)
```

**方法 2：發送測試訊息**
在 Discord 中提及機器人：
```
@機器人 請用一句話描述你的人格
```

根據您的 `personality.md` 內容，機器人應該回應相符的描述。

---

## 進階使用

### 動態修改提示詞

由於系統採用即時讀取策略，您可以：

1. **修改現有檔案**：直接編輯 `prompts/` 中的檔案
2. **新增檔案**：添加新的 `.md` 檔案
3. **刪除檔案**：移除不需要的檔案

**下一次 AI 請求**會自動使用更新後的內容，無需重啟機器人。

### 使用絕對路徑

如果 prompts 資料夾位於其他位置：

```properties
# .env
PROMPTS_DIR_PATH=/opt/ltdjms/config/prompts
```

### 調整檔案大小限制

如果您的提示詞檔案較大：

```properties
# .env
PROMPT_MAX_SIZE_BYTES=5242880  # 5MB
```

**注意**：過大的提示詞可能會：
- 超過 AI API 的 token 限制
- 增加回應延遲
- 增加 API 成本

---

## 最佳實踐

### 1. 模組化提示詞

將不同類型的內容分開存放：

```
prompts/
├── personality.md    # 機器人人格
├── rules.md          # 使用規則
├── format.md         # 輸出格式要求
└── domain-knowledge.md  # 領域知識
```

### 2. 使用清確的檔案名稱

- ✅ `bot-personality.md` → `BOT PERSONALITY`
- ✅ `response-format.md` → `RESPONSE FORMAT`
- ❌ `temp.md` → `TEMP`（不清楚用途）
- ❌ `001.md` → `001`（無語義）

### 3. 控制提示詞長度

- 單一檔案建議 < 100KB
- 總提示詞建議 < 500KB
- 定期檢查 AI API 的 token 使用量

### 4. 版本控制

將 `prompts/` 資料夾加入 Git：

```bash
git add prompts/
git commit -m "feat(aichat): add initial system prompts"
```

**注意**：如果提示詞包含敏感資訊，使用 `.gitignore` 排除：

```
# .gitignore
prompts/secret-*.md
```

---

## 範例：完整的 prompts 資料夾結構

```
prompts/
├── personality.md
├── rules.md
├── format.md
└── examples/
    └── response-examples.md
```

**personality.md**：
```markdown
# 機器人人格

你是一個專門協助 LTDJ 成員的 Discord 機器人。

## 角色
- 提供準確的資訊
- 協助解決問題
- 維持友善的語氣
```

**rules.md**：
```markdown
# 互動規則

1. 使用繁體中文
2. 簡潔明確的回答
3. 不當猜測，承認不知道
4. 保護使用者隱私
```

**format.md**：
```markdown
# 回應格式

- 使用 Markdown 格式
- 清單使用 `-` 符號
- 程式碼使用 ` ``` ` 包裹
- 重要資訊使用 **粗體**
```

---

## 下一步

- [功能規格](./spec.md) - 完整的功能需求
- [資料模型](./data-model.md) - 技術實作細節
- [API 契約](./contracts/prompt-loader-api.yaml) - 介面定義
- [實作計劃](./plan.md) - 開發路徑

---

## 技術支援

如有問題或建議，請：
1. 查看 [常見問題排查](#常見問題排查)
2. 檢查應用日誌（`make logs`）
3. 提交 Issue 至專案 repository
