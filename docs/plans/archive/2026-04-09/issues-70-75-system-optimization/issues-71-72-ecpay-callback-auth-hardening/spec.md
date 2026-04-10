# Spec: Issues 71-72 綠界 Callback 驗證強化

- Date: 2026-04-09
- Feature: Issues 71-72 綠界 Callback 驗證強化
- Owner: Codex

## Goal
在不改變 production 付款完成主流程與既有 paid→fulfillment 冪等語意的前提下，移除 callback URL 上的 query secret 洩漏面，並讓 stage/public callback 暴露風險預設改為安全失敗。

## Scope

### In Scope
- 移除 `EcpayCvsPaymentService.buildCallbackReturnUrl()` 對 `?token=` 的依賴。
- 移除 `EcpayCallbackHttpServer` 以 query string secret 做授權的機制。
- 以現有 `ECPAY_STAGE_MODE`、bind host / port / path 決定 callback exposure policy，阻止危險的 stage/public 預設。
- 保留 `FiatPaymentCallbackService` 對 merchant / amount / decrypt / idempotency 的既有 paid callback 驗證，並補齊對新 trust boundary 的 regression coverage。

### Out of Scope
- 重新設計 ECPay API payload、MerchantTradeNo 生成規則或 DM 文案。
- 變更商品 fulfillment、admin notification、order repository schema。
- 新增 reverse proxy、WAF、IP allowlist 等外部部署元件。
- 正規化 `EnvironmentConfig` 文件與舊 config key 清理（由 issue 74 擁有）。

## Functional Behaviors (BDD)

### Requirement 1: Callback ReturnURL 不可再夾帶 shared secret
**GIVEN** 系統向綠界建立 CVS payment code  
**AND** operator 已在環境中配置 callback URL  
**WHEN** `EcpayCvsPaymentService` 建構送往綠界的 `ReturnURL`  
**THEN** 產出的 URL 不得再附加 `token` 或其他 query-string 型態的本地授權秘密  
**AND** callback admission control 必須改由伺服器端 exposure policy 決定，而非 URL secret

**Requirements**:
- [x] R1.1 `buildCallbackReturnUrl()` 回傳的 URL 與 operator 設定值一致，不追加 query token。
- [x] R1.2 對應單元測試與文件不得再固化 `?token=` 行為。

### Requirement 2: 危險的 stage/public callback 暴露預設必須 fail closed
**GIVEN** `ECPAY_STAGE_MODE=true` 代表使用公開測試金鑰與測試環境  
**AND** callback server 可能被綁到非 loopback host 讓外部可直接送達  
**WHEN** 應用啟動 callback server 或處理 callback request  
**THEN** 系統必須拒絕危險的 stage/public 暴露組合  
**AND** 不可再以 query token 當作唯一授權因子來彌補這個風險

**Requirements**:
- [x] R2.1 `EcpayCallbackHttpServer` 不再接受 query token 授權。
- [x] R2.2 當 `ECPAY_STAGE_MODE=true` 且 bind host 為 public / non-loopback 時，server 必須在啟動階段 fail closed 並給出可操作錯誤訊息。
- [x] R2.3 production + public bind 的合法場景仍可保留 callback server 啟動能力。

### Requirement 3: Paid callback 驗證邊界收斂且不破壞既有冪等流程
**GIVEN** 綠界 callback 送來加密 `Data`，且系統需驗證 merchant / amount / order / paid status  
**WHEN** callback 通過既有內容驗證並位於允許的 exposure policy 下  
**THEN** `FiatPaymentCallbackService` 仍應沿用既有 paid transition、duplicate callback、fulfillment / admin notification 語意  
**AND** exposure policy 不得造成 production happy path 的誤阻擋

**Requirements**:
- [x] R3.1 既有 decrypt、merchant、amount、paid-status、idempotency 驗證語意保持不變。
- [x] R3.2 duplicate paid callback 仍只會記錄狀態與跳過重複 fulfillment。
- [x] R3.3 測試需同時覆蓋 legit production flow 與 stage/public fail-closed flow。

## Error and Edge Cases
- [x] callback body 缺少 `Data`、解密失敗、merchant 不符、amount 不符、order 不存在。
- [x] `ECPAY_STAGE_MODE=true` 且 bind host 為 `0.0.0.0` / public IP / `localhost` 以外值時，server 不可默默啟動。
- [x] 移除 query token 後，legacy 測試或部署若仍假設 `?token=` 存在，應得到明確失敗訊號。
- [x] production public callback 不可因 stage 限制邏輯而被誤拒。
- [x] duplicate callback 與未付款 callback 不可破壞既有 callback payload 記錄與 claim / mark 流程。

## Implementation Status
- Completed on current codebase baseline.
- Evidence:
  - `EcpayCvsPaymentService.buildCallbackReturnUrl()` 直接回傳 operator 設定值，不再附加 query token。
  - `EcpayCallbackHttpServer.start()` 在 stage + public/non-loopback bind 時 fail closed，production 模式仍允許 public bind。
  - `FiatPaymentCallbackService` 維持既有 decrypt、merchant、amount、paid-status、idempotency 驗證與 duplicate side-effect 防重語意。
  - 目標測試已通過：`EcpayCvsPaymentServiceTest`、`EcpayCallbackHttpServerTest`、`FiatPaymentCallbackServiceTest`。

## Clarification Questions
None

## References
- Official docs:
  - `https://developers.ecpay.com.tw/`（綠界開發者中心；包含 Payment Results Notification 與站內付取號流程）
- Related code files:
  - `src/main/java/ltdjms/discord/shop/services/EcpayCvsPaymentService.java`
  - `src/main/java/ltdjms/discord/shop/services/EcpayCallbackHttpServer.java`
  - `src/main/java/ltdjms/discord/shop/services/FiatPaymentCallbackService.java`
  - `src/test/java/ltdjms/discord/shop/services/EcpayCvsPaymentServiceTest.java`
  - `src/test/java/ltdjms/discord/shop/services/EcpayCallbackHttpServerTest.java`
  - `src/test/java/ltdjms/discord/shop/services/FiatPaymentCallbackServiceTest.java`
  - GitHub issues `#71`, `#72`
