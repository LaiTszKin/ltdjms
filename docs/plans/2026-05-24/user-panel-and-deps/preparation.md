# Preparation: user-panel-and-deps

- Date: 2026-05-24
- Batch: user-panel-and-deps

## **Task P1: 確立 Node.js 執行基線**

Purpose: 所有 member spec 的依賴升級與編譯目標需一致；避免 major 升級後在不同 Node 版本上行為不一致。
Scope: 根 `package.json`、`.nvmrc`、`Dockerfile.ts`、`.github/workflows/ci.yml`
Out of scope: 業務邏輯、user-panel 功能實作

- P1.1 [x] **根 `package.json` 新增 `engines`** — 設定 `"node": ">=22.0.0"`，並新增 `"packageManager": "pnpm@9.0.0"`（與 CI 一致）
  - Verify: `node -e "console.log(require('./package.json').engines.node)"` 輸出 `>=22.0.0`

- P1.2 [x] **建立 `.nvmrc`** — 內容為 `22`
  - Verify: `cat .nvmrc` 輸出 `22`

- P1.3 [x] **更新 CI matrix** — `.github/workflows/ci.yml` 僅保留 Node 22（移除 Node 20），pnpm 版本鎖定 9
  - Verify: `grep -E "node-version|22" .github/workflows/ci.yml` 顯示 Node 22

- P1.4 [x] **確認 Docker 基線** — `Dockerfile.ts` 維持 `node:22-alpine`
  - Verify: `grep "node:22" Dockerfile.ts`

## **Task P2: 建立 Java 對照測試基線（User Panel Parity Oracle）**

Purpose: `user-panel-java-parity` 需要可重複執行的 oracle，以 Java 測試與常數為 1:1 驗收依據。
Scope: `docs/plans/2026-05-24/user-panel-and-deps/user-panel-java-parity/fixtures/`（已建立 oracle JSON，P2 可標記完成）
Out of scope: 實作 TypeScript parity 測試（屬於 member spec）

- P2.1 [x] **擷取 Java customId 常數表** — 從 `UserPanelButtonHandler.java`、`UserPanelCommandHandler.java` 整理所有 `user_panel_*` 常數至 `fixtures/java-custom-ids.json`
  - Verify: JSON 包含 `user_panel_token_history`、`user_panel_currency_history`、`user_panel_product_redemption_history`、`user_panel_redeem`、`user_panel_modal_redeem`、`user_panel_back` 及三種 `_page_{n}` 前綴

- P2.2 [x] **擷取 Java embed 期望欄位** — 從 `UserPanelEmbedBuilderTest.java` 整理主面板 embed 期望（title、description 格式、field names、footer、button labels/styles/rows）至 `fixtures/java-main-panel-oracle.json`
  - Verify: oracle 含 title=`個人面板`、兩個 inline fields、四個 action buttons 分兩列、redeem 為 SUCCESS style

- P2.3 [x] **擷取 Java history pagination oracle** — 從 `UserPanelHistoryViewFactoryTest.java` 整理分頁按鈕組合（back + prev + next）與 embed 標題/空狀態文案至 `fixtures/java-history-oracle.json`
  - Verify: 三種 history type 的空狀態與分頁 indicator 格式 `"第 {current}/{total} 頁（共 {count} 筆）"` 存在

## **Task P3: 鎖定依賴升級前品質基線**

Purpose: 確保 batch 開始前 repo 處於可編譯、可測試狀態，後續升級 regression 可歸因。
Scope: 根目錄驗證命令
Out of scope: 修改任何 package 版本

- P3.1 [x] **執行完整驗證** — `make build` 通過；`make test` 需 Docker runtime（testcontainers）於本機不可用，CI Node 22 job 為完整驗證路徑
  - Verify: `make verify` exit code 0

- P3.2 [x] **記錄當前 lockfile 摘要** — 將 `pnpm list --depth=0` 輸出存至 `fixtures/pre-upgrade-lockfile-snapshot.txt`
  - Verify: 檔案存在且包含 `@ltdjms/shared`、`discord.js`、`zod` 版本

## Validation

- Verification required:
  - Node 22 為唯一 CI/Docker 目標
  - Java parity oracle fixtures 完整
  - `make verify` 通過
- Expected results: 所有 member spec 可在一致 Node 22 環境並行或依序實作，user-panel parity 有明確 oracle
- Regression risks covered: 環境漂移、parity 驗收標準模糊
