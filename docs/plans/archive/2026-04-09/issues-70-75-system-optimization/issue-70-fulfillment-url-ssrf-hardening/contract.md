# Contract: Issue 70 履約 URL DNS Rebinding 強化

- Date: 2026-04-09
- Feature: Issue 70 履約 URL DNS Rebinding 強化
- Change Name: issue-70-fulfillment-url-ssrf-hardening

## Purpose
本變更聚焦於內部 fulfillment transport 與 host validation 的責任邊界，沒有新增或改變任何外部平台／API 契約；因此本文件只記錄「無外部依賴契約變更」的判定。

## Usage Rule
- 若後續實作引入新的 HTTP client、proxy trust model 或外部 webhook platform，需回頭補上正式 dependency record。
- 在目前規劃內，所有安全約束都以本 repo 現有程式碼與測試為主要證據來源。

## Dependency Records

None

原因：本 spec 不改動外部 fulfillment API payload、簽章格式或第三方平台互動，只收斂內部 `ResolvedTarget` / transport 實作與測試；沒有外部 dependency contract 足以主導設計選擇。
