# Spec: Interactive env setup assistant

- Date: 2026-04-09
- Feature: Interactive env setup assistant
- Owner: Codex

## Goal
提供真正的互動式 `setup-env` 體驗，讓 operator 按提示完成 `.env` 的部署關鍵值設定，同時把現有純同步腳本明確降格為 `update-env` 維護用途。

## Scope

### In Scope
- 新增互動式設定腳本，作為新的 `setup-env` 主入口。
- 將現有 `scripts/sync-env.sh` 對應的非互動同步流程改名為 `update-env`。
- 互動式流程需逐步詢問公開 domain、TLS email、callback 推導策略與必要 secrets 提示。
- 以 `.env.example` 為範本建立或更新 `.env`，保留備份並避免覆寫未確認的既有值。

### Out of Scope
- 驗證 DNS 是否已指向 VPS、Caddy 憑證是否簽出、或 Discord / ECPay 憑證是否可用。
- 在腳本中直接呼叫 DNS 供應商、ACME API 或其他外部雲端服務。
- 取代 CI / 自動化場景對非互動 env 同步的需求。

## Functional Behaviors (BDD)

### Requirement 1: `setup-env` 應成為真正的互動式部署設定入口
**GIVEN** operator 在新環境或既有環境中準備部署 LTDJMS  
**AND** repo 內已有 `.env.example` 作為 canonical template  
**WHEN** operator 執行新的 `setup-env`  
**THEN** 腳本必須以互動提示引導 operator 完成公開 domain、TLS email 與 callback 相關設定  
**AND** 腳本必須在結尾輸出摘要與下一步操作建議

**Requirements**:
- [x] R1.1 新的 `setup-env` 以互動方式詢問公開 domain / base URL、TLS email，以及是否使用 `APP_PUBLIC_BASE_URL` 自動推導 callback URL；實作於 `scripts/setup-env.sh` 與 `make setup-env`。
- [x] R1.2 腳本會把 bare host 正規化為 `https://<domain>` 形式的 `APP_PUBLIC_BASE_URL`，並在預設情況下保持 `ECPAY_RETURN_URL` 空白，讓應用程式自行推導。
- [x] R1.3 腳本會在摘要後列出敏感欄位現況，區分「已保留現值」、「仍是範例值」與「請手動填寫」。

### Requirement 2: `update-env` 應保留非互動同步能力且不破壞既有 `.env`
**GIVEN** operator 可能已經有現成 `.env`，其中包含敏感憑證與客製值  
**AND** repo 仍需要一個非互動命令來根據 `.env.example` 補齊/整理欄位  
**WHEN** operator 執行 `update-env` 或在 `setup-env` 中寫入 `.env`  
**THEN** 流程必須先建立備份並避免無預警清空既有 secrets  
**AND** 既有純同步邏輯必須以 `update-env` 名稱保留，方便自動化與維運補欄位

**Requirements**:
- [x] R2.1 互動式流程在既有 `.env` 存在時建立 `.env.bak`，不存在時從 `.env.example` 安全建立新檔並避免留下半套寫入。
- [x] R2.2 互動式流程只覆寫 operator 回答涉及的欄位，其他欄位先以 `.env.example` 對齊再保留既有值。
- [x] R2.3 `make setup-env` 已改為互動式入口，既有同步流程改由 `make update-env` 暴露，並同步更新 README / docs 說明。

## Error and Edge Cases
- [x] `.env` 不存在時，新的 `setup-env` 能安全建立新檔。
- [x] operator 輸入 bare host、完整 URL、空值、或含尾斜線時，正規化結果可預期（以 shell fixture 測試覆蓋）。
- [x] operator 選擇保留顯式 `ECPAY_RETURN_URL` override 時，腳本會明確提示與自動推導互斥。
- [x] 腳本在非 TTY 或使用者中途取消時，不會留下半套寫入的 `.env`。
- [x] 非互動 `update-env` 維持既有同步語意（新增缺漏欄位、保留現值、備份舊檔）。

## Clarification Questions
None

## References
- Official docs:
  - None（此 spec 的外部契約主要來自本 repo 的 `.env.example`、Makefile 與既有 operator workflow）
- Related code files:
  - `scripts/sync-env.sh`
  - `Makefile`
  - `.env.example`
  - `README.md`
