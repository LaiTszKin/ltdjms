# Tasks: Issue 74 設定 Schema 正規化

- Date: 2026-04-09
- Feature: Issue 74 設定 Schema 正規化

## **Task 1: 確立 canonical schema owner 與 packaged defaults 檔案**

對應 `R1.1-R1.3`，核心目標是讓 runtime schema 只由一個來源定義，並消除 `application.conf` / `application.properties` 的平行真相。

- 1. [x] 收斂 `EnvironmentConfig` 與 packaged defaults
  - 1.1 [x] 明確列出 canonical config path 與 env→config 映射
  - 1.2 [x] 決定 `application.properties` 為唯一 packaged defaults，並移除或降級 `application.conf` 的獨立 schema 角色

## **Task 2: 對齊文件與實際 fallback chain**

對應 `R2.1-R2.3`，核心目標是讓維運文件與實際 runtime 行為完全一致。

- 2. [x] 更新開發文件與說明範例
  - 2.1 [x] 修正 `docs/development/configuration.md` 中對 packaged defaults 與 key namespace 的描述
  - 2.2 [x] 若存在 compatibility shim / legacy note，文件需清楚標示其非 canonical 性質

## **Task 3: 補齊 drift regression tests**

對應 `R3.1-R3.3`，核心目標是讓未來若有人再次引入平行 schema，測試能第一時間失敗。

- 3. [x] 擴充 shared config 測試
  - 3.1 [x] 驗證 canonical packaged defaults 檔與主要 key 的 fallback 行為
  - 3.2 [x] 驗證 `.env`、packaged defaults、built-in defaults 的順序與文件聲明一致

## Notes
- 本 spec 偏向「單一真相 + 文件同步」，不做新的 config 功能擴張。
- 若有舊 key 需要短期 alias，必須在 spec 實作時明確標示清理終點；不能把 alias 當第二套 canonical schema。
