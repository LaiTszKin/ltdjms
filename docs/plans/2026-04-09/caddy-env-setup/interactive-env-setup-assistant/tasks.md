# Tasks: Interactive env setup assistant

- Date: 2026-04-09
- Feature: Interactive env setup assistant

## **Task 1: Define setup/update command responsibilities**

對應 `R1.1-R1.3`, `R2.3`，核心目標是把互動式 `setup-env` 與非互動 `update-env` 的責任邊界定清楚。

- 1. [x] 定義命令角色與入口
  - 1.1 [x] 規劃新的 `setup-env` 為互動式引導入口
  - 1.2 [x] 將現有同步流程改名為 `update-env`
  - 1.3 [x] 確認 Makefile / 使用說明的入口名稱與行為一致

## **Task 2: Implement safe interactive env writing**

對應 `R1.1-R1.3`, `R2.1-R2.2`，核心目標是在不破壞既有 `.env` 的前提下，新增可逐步回答的互動式寫入流程。

- 2. [x] 新增互動式腳本與備份/寫入邏輯
  - 2.1 [x] 建立 `.env` 不存在時的初始化與 `.env.bak` 備份流程
  - 2.2 [x] 實作公開 domain / TLS email / callback 策略的提示與正規化
  - 2.3 [x] 僅覆寫使用者確認的欄位，保留未觸及的既有 secrets

## **Task 3: Validate operator workflow**

對應 `R1.x`, `R2.x`，核心目標是驗證新舊兩條 env 流程在主要場景下都可預期。

- 3. [x] 補腳本驗證與摘要輸出
  - 3.1 [x] 覆蓋新建 `.env`、保留既有值、取消流程與非 TTY 行為
  - 3.2 [x] 驗證 `update-env` 仍保留原本同步語意
  - 3.3 [x] 更新 operator 入口說明與剩餘人工步驟提示

## Notes
- Task order should reflect actual implementation sequence.
- Every main task maps back to `spec.md` requirement IDs.
- Script validation can rely on bash fixture tests / golden-file style checks; property-based coverage is expected to be `N/A`.
- After execution, the agent must update each checkbox (`[x]` for done, `[ ]` for not done).
