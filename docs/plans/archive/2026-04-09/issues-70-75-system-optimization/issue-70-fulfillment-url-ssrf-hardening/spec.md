# Spec: Issue 70 履約 URL SSRF / DNS Rebinding 收斂

- Date: 2026-04-09
- Feature: Issue 70 履約 URL DNS Rebinding 強化
- Owner: Codex

## Goal
在不改變商品 fulfillment payload 與既有 shop 主流程的前提下，把 backend fulfillment target 的驗證結果與實際 outbound 連線固定為同一個解析快照，並補齊回歸保護，避免未來改動把系統退回到可被 DNS rebinding 利用的模式。

## Scope

### In Scope
- 盤點並固化 `ProductFulfillmentApiService` 的 target resolution → transport send 行為。
- 明確定義「只允許 public HTTPS target、使用已驗證 IP 連線、以原 host 僅作 Host/SNI」的安全不變式。
- 補齊 DNS rebinding、private/special-use address、non-2xx / network failure 的 regression 測試。
- 必要時將現有實作整理為更不易被後續 refactor 破壞的結構／命名。

### Out of Scope
- 修改商品資料模型或 fulfillment payload 欄位。
- 新增 fulfillment retry / queue / async delivery。
- 變更 escort pricing、order claim、payment callback、webhook 簽章格式。
- 支援 redirect-based fulfillment endpoint。

## Functional Behaviors (BDD)

### Requirement 1: 驗證與連線必須共用同一份 target snapshot
**GIVEN** 商品設定了 `https://` 的 backend fulfillment URL  
**AND** service 在送出 fulfillment 前已完成 hostname 解析與 public-address 驗證  
**WHEN** `notifyFulfillment()` 建立 outbound target  
**THEN** transport 必須直接使用已驗證的解析結果連線，而不是在送出時重新依 hostname 做第二次 DNS lookup  
**AND** 原始 hostname 只能用於 Host header 與 TLS SNI，不可再次決定實際 socket 目的地

**Requirements**:
- [x] R1.1 `resolveAndValidateTargetUri()` 必須輸出完整 `ResolvedTarget` 快照（original URI、resolved IP、port、host header、request path）。
- [x] R1.2 transport 僅能消費 `ResolvedTarget` 進行連線，禁止內部以 hostname 重解。
- [x] R1.3 對外行為仍維持既有 fulfillment payload 與簽章標頭格式。

### Requirement 2: 只允許 public HTTPS target，且不接受逃逸路徑
**GIVEN** 商品 backend fulfillment URL 可能來自管理員設定  
**AND** 系統必須避免對 localhost、RFC1918、special-use address 或未知 host 發送請求  
**WHEN** service 驗證 URL 或處理回應  
**THEN** 非 public HTTPS target 必須在送出前被拒絕  
**AND** 不可因 redirect、fallback host 或 transport-level convenience 行為繞過原始驗證結果

**Requirements**:
- [x] R2.1 僅接受 `https://` URL，拒絕 localhost、loopback、site-local、link-local、multicast、ULA 與現有已封鎖的 special-use ranges。
- [x] R2.2 transport 不自動跟隨 redirect 到另一個 host / scheme / port。
- [x] R2.3 non-2xx、TLS / socket failure、DNS 解析失敗都要轉成可觀測的 `DomainError`，且不得留下 partial fulfillment side effect。

## Error and Edge Cases
- [x] 管理員輸入 `http://`、空 host、localhost、`.localhost` 或無法解析的 URL。
- [x] host 首次解析為 public IP，但未來 refactor 若改回 hostname-based client，會重新暴露 DNS rebinding 風險。
- [x] 解析結果含 mixed public/private addresses 時必須 fail closed。
- [x] backend 回傳 3xx / 5xx / chunked error body 時，不能繞過失敗處理。
- [x] TLS handshake、socket timeout 或讀取回應失敗時，不得誤記為 fulfillment 成功。

## Clarification Questions
None

## References
- Official docs:
  - None（本 spec 僅涉及內部 transport / validation 不變式，無新增外部依賴契約）
- Related code files:
  - `src/main/java/ltdjms/discord/shop/services/ProductFulfillmentApiService.java`
  - `src/test/java/ltdjms/discord/shop/services/ProductFulfillmentApiServiceTest.java`
  - GitHub issue `#70`
