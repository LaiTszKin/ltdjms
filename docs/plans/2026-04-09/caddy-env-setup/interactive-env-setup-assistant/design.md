# Design: Interactive env setup assistant

- Date: 2026-04-09
- Feature: Interactive env setup assistant
- Change Name: interactive-env-setup-assistant

## Design Goal
把現有偏機械式的 `.env` 同步流程拆成兩條清楚的 operator 路徑：新的 `setup-env` 負責互動式初始化/引導，`update-env` 保留非互動同步與補欄位用途。

## Change Summary
- Requested change: 新增互動式 terminal bash 腳本完成部署設定，並將現有 `setup-env` 重命名為 `update-env`
- Existing baseline: `make setup-env` 目前直接呼叫 `scripts/sync-env.sh`，只做 `.env.example` 與 `.env` 的無互動同步與備份
- Proposed design delta: 保留 `scripts/sync-env.sh` 的核心同步行為作為 `update-env`，新增互動式腳本接手 `setup-env` 並引導 operator 填寫 domain / callback / TLS 等關鍵值

## Scope Mapping
- Spec requirements covered: `R1.1-R1.3`, `R2.1-R2.3`
- Affected modules: `scripts/sync-env.sh`, `scripts/setup-env.sh`, `scripts/setup-env.test.sh`, `Makefile`, `.env.example`, `README.md`, `docs/getting-started.md`, `docs/configuration.md`
- External contracts involved: `None`
- Coordination reference: `../coordination.md`

## Current Architecture
目前 `.env` 維護只有一條路：
1. `make setup-env` 呼叫 `scripts/sync-env.sh`
2. `scripts/sync-env.sh` 以 `.env.example` 為模板，備份舊 `.env`、刪除過時鍵、補上新鍵、保留既有值
3. operator 仍需手動閱讀 README / docs 判斷哪些值要填、哪些值要留空、domain 與 callback 應如何搭配

這造成的問題：
- `setup-env` 名稱暗示初始化，但實際只是在做同步。
- 新部署者要自己從多份文件拼湊 `APP_PUBLIC_BASE_URL`、`ECPAY_RETURN_URL`、Caddy domain/email 等關係。
- 既有 secrets 很容易因人工編輯失誤而被覆蓋或留錯。

## Proposed Architecture
- 保留 `scripts/sync-env.sh` 的純同步角色，並透過 `make update-env` 暴露該能力。
- 新增互動式 `setup-env` 腳本，先確保 `.env` 存在與已備份，再逐步詢問 operator 所需值。
- 互動式腳本應重用現有同步/讀寫 helper 或沿用相同檔案格式處理方式，而不是發明平行 `.env` 寫入器。
- `Makefile` 與 operator 文件將明確區分：
  - `setup-env`：首次/手動引導式設定
  - `update-env`：根據 `.env.example` 做非互動同步

## Component Changes

### Component 1: `scripts/setup-env.sh`（新互動式入口）
- Responsibility: 引導 operator 回答部署關鍵問題、正規化輸入、輸出摘要並安全回寫 `.env`
- Inputs: TTY 使用者輸入、既有 `.env` / `.env.example`
- Outputs: 更新後的 `.env`、備份檔、操作摘要
- Dependencies: `scripts/sync-env.sh` 可重用的讀寫/同步邏輯（若重構成可共享 helper）
- Invariants: 不應無預警清空既有 secrets；預設應鼓勵用 `APP_PUBLIC_BASE_URL` 推導 callback

### Component 2: `scripts/sync-env.sh` + `Makefile`
- Responsibility: 保留非互動 `.env` 同步能力，並把命令名稱調整為 `update-env`
- Inputs: `.env.example`, `.env`
- Outputs: 同步後的 `.env`
- Dependencies: shell file manipulation
- Invariants: 現有同步語意不可因命名調整而改變；`make setup-env`/`make update-env` 的語意必須清楚不混淆

## Sequence / Control Flow
1. operator 執行新的 `setup-env`。
2. 腳本確保 `.env` 存在，必要時從 `.env.example` 建立並先做備份。
3. 腳本依序詢問公開 domain、TLS email、是否保留顯式 callback override、以及敏感值是否要維持現值或稍後手填。
4. 腳本將回答正規化成 `.env` 內容，輸出摘要與後續步驟。
5. 若 operator 只想把 `.env.example` 的新增欄位同步進現有 `.env`，則執行 `update-env` 走非互動同步路徑。

## Data / State Impact
- Created or updated data: `.env`, `.env.bak`, 新增 `scripts/setup-env.sh` / `scripts/setup-env.test.sh`，以及更新後的 `.env.example` / operator docs
- Consistency rules: `.env.example` 仍是模板真相來源；互動式流程不得產生 `.env.example` 未知的新 key，除非 batch coordination 明確定義
- Migration / rollout needs: 更新 Makefile 命令名稱與 README 入口說明；提醒 operator 舊 `setup-env` 已改為 `update-env`

## Risk and Tradeoffs
- Key risks: 互動腳本錯誤正規化 domain；命令重命名造成 operator 習慣斷裂；既有 secrets 被不小心覆寫
- Rejected alternatives:
  - 只在 README 補更多說明：仍然無法降低首次部署時的人工拼裝成本
  - 直接把 `sync-env.sh` 改成互動式：會破壞 CI/維運對非互動同步的預期用途
  - 完全不保留舊同步腳本：會失去低風險補欄位能力
- Operational constraints: script 必須在一般 bash 環境可執行；非 TTY/取消流程需保守退出

## Validation Plan
- Tests: `scripts/setup-env.test.sh` 覆蓋新建 `.env`、保留既有值、取消流程、非 TTY 防護與 `update-env` 同步語意回歸
- Contract checks: `None`（此 spec 無外部依賴契約）
- Rollback / fallback: 若互動式入口有問題，可暫時保留/回退到純 `update-env` 同步並手動編輯 `.env`

## Open Questions
None
