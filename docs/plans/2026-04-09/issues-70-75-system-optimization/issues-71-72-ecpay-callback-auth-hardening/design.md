# Design: Issues 71-72 綠界 Callback 驗證強化

- Date: 2026-04-09
- Feature: Issues 71-72 綠界 Callback 驗證強化
- Change Name: issues-71-72-ecpay-callback-auth-hardening

## Design Goal
把綠界 callback 的信任邊界從「URL 上是否帶了 query token」改為「此部署是否允許暴露 callback server」與「callback 內容是否通過既有 paid 驗證」，藉此消除 URL secret 洩漏面，同時維持 production happy path 相容。

## Final Status
- Status: `completed`
- Delivered behavior:
  - `EcpayCvsPaymentService` 已不再把 shared secret 寫進 `ReturnURL`
  - `EcpayCallbackHttpServer` 已在 stage + public/non-loopback bind 時 fail closed，production mode 保留 public bind 啟動能力
  - `FiatPaymentCallbackService` 維持原有 decrypt、merchant、amount、paid-status、idempotency 驗證與 duplicate side-effect 防重
- Verification evidence:
  - `mvn -q -Dtest=EcpayCvsPaymentServiceTest,EcpayCallbackHttpServerTest,FiatPaymentCallbackServiceTest test`

## Change Summary
- Requested change: 依 issue #71、#72 移除 query token 授權與 stage/public 危險預設。
- Historical baseline: `EcpayCvsPaymentService` 曾把 shared secret 加到 `ReturnURL?token=...`；`EcpayCallbackHttpServer` 曾從 query string 驗證 `token`；`FiatPaymentCallbackService` 依解密後欄位決定 paid transition。
- Implemented design delta: callback URL 不再攜帶 secret；HTTP server 不再信任 query token；stage + public bind 的組合在 server 啟動時直接 fail closed；callback service 保留既有內容驗證與 idempotency。

## Scope Mapping
- Spec requirements covered: `R1.1-R1.2`, `R2.1-R2.3`, `R3.1-R3.3`
- Affected modules:
  - `src/main/java/ltdjms/discord/shop/services/EcpayCvsPaymentService.java`
  - `src/main/java/ltdjms/discord/shop/services/EcpayCallbackHttpServer.java`
  - `src/main/java/ltdjms/discord/shop/services/FiatPaymentCallbackService.java`
  - `src/test/java/ltdjms/discord/shop/services/*`
- External contracts involved: `ECPay Payment Callback / ReturnURL`, `ECPay CVS Payment Code Generation`
- Coordination reference: `../coordination.md`

## Current Architecture
目前綠界 callback trust model 分成兩段：
1. `EcpayCvsPaymentService` 把本地 shared secret 附加到 `ReturnURL` query string。
2. `EcpayCallbackHttpServer` 以 `token` 作為 request admission 控制。
3. `FiatPaymentCallbackService` 驗證解密後欄位，決定是否 `markPaidIfPending()`。

問題在於：
- URL query string 很容易出現在代理、access log、APM 與例外追蹤。
- 當 `ECPAY_STAGE_MODE=true` 且 callback server 對外暴露時，使用公開測試金鑰的部署不應再被 query token 誤導成「足夠安全」。
- paid transition 驗證與 callback exposure policy 混在一起，讓真正的 trust boundary 不清楚。

## Proposed Architecture
- callback URL builder：只負責回傳 operator 設定的 callback URL，不再塞入 secret。
- callback exposure policy：由 `EcpayCallbackHttpServer.start()` 依 `ECPAY_STAGE_MODE` 與 bind host 判斷是否允許啟動。
- callback content validation：由 `FiatPaymentCallbackService` 持續負責 decrypt、merchant、amount、paid status、idempotency 驗證。

## Component Changes

### Component 1: `EcpayCvsPaymentService`
- Responsibility: 建構綠界取號請求與 `ReturnURL`。
- Inputs: 既有 ECPay merchant / hash config 與 operator 設定的 callback URL。
- Outputs: 發往綠界的取號 request。
- Dependencies: `EnvironmentConfig` 提供既有值、ECPay 取號 API。
- Invariants:
  - `ReturnURL` 不攜帶本地 shared secret。
  - 取號 payload shape 與 order 建立流程維持不變。

### Component 2: `EcpayCallbackHttpServer`
- Responsibility: 啟動 callback server、決定 exposure policy、讀取 request body 並交給 callback service。
- Inputs: `ECPAY_STAGE_MODE`、bind host / port / path、HTTP callback request。
- Outputs: 啟動成功 / 失敗、HTTP response。
- Dependencies: `FiatPaymentCallbackService`。
- Invariants:
  - 不再以 query token 做授權。
  - stage + public bind 的組合不可啟動。
  - production + public bind 仍可保留合法 callback server 啟動能力。

### Component 3: `FiatPaymentCallbackService`
- Responsibility: 驗證 callback 內容並驅動 paid / duplicate / unpaid 分支。
- Inputs: callback body、content type、現有 order / product state。
- Outputs: `CallbackResult`、order callback status 更新、必要時 fulfillment / admin notification。
- Dependencies: `FiatOrderRepository`、`ProductService`、`ProductFulfillmentApiService`、`ShopAdminNotificationService`。
- Invariants:
  - decrypt、merchant、amount、paid-status、idempotency 邏輯不退化。
  - duplicate paid callback 不重複 fulfillment。

## Sequence / Control Flow
1. 建立 CVS payment code 時，系統將 operator 設定的 `ReturnURL` 原樣帶入 request。
2. 啟動 callback server 時，若 `ECPAY_STAGE_MODE=true` 且 bind host 為 public / non-loopback，直接 fail closed。
3. 合法啟動的 callback server 接收 request 後，不做 query-token 驗證，直接交給 `FiatPaymentCallbackService` 做內容驗證與 paid transition 判斷。

## Data / State Impact
- Created or updated data: `None`（不新增 schema；僅改變 callback trust boundary）
- Consistency rules:
  - callback status 紀錄、paid transition、duplicate callback 行為保持既有 repository 語意。
  - stage/public 危險部署必須在啟動前就被阻擋，而不是等 callback request 進來才部分處理。
- Migration / rollout needs:
  - operator 若目前依賴 stage + public bind 測試，需改為 loopback / 受控網路環境。
  - 舊的 query-token 假設需一併更新測試與文件。

## Risk and Tradeoffs
- Key risks:
  - stage/public callback 直接 fail closed 會讓部分不安全的測試部署無法延續。
  - 若僅移除 query token 卻不加 exposure policy，會放大 issue #71 風險。
- Rejected alternatives:
  - 保留 query token，只是把 secret 搬到不同 query key：仍屬 URL secret，無法根治外洩面。
  - 新增自定義 callback header：綠界 callback 契約不保證支援。
- Operational constraints:
  - 不可破壞 production callback 的 paid happy path。
  - 不可引入需要新基礎設施的解法。

## Validation Plan
- Tests:
  - Unit：`buildCallbackReturnUrl()` 不再加 token
  - Unit / integration：stage + public bind 啟動拒絕
  - Regression：production legit callback、duplicate callback、validation failure 不回歸
  - Adversarial：移除 query token 後 legacy request 不得被當成授權依據
- Contract checks: 以綠界開發者中心的 ReturnURL / callback 文件確認 callback shape 仍受官方契約約束，且本地不假設官方提供額外 auth layer。
- Rollback / fallback: 若上線後誤擋 production callback，可回退到移除 query token 前的版本，但該回退需被視為暫時性 hotfix，且不得重新把 URL secret 當長期解法。

## Open Questions
None
