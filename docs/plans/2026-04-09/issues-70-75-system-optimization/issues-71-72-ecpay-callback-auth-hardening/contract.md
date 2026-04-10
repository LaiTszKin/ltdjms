# Contract: Issues 71-72 綠界 Callback 驗證強化

- Date: 2026-04-09
- Feature: Issues 71-72 綠界 Callback 驗證強化
- Change Name: issues-71-72-ecpay-callback-auth-hardening

## Purpose
本變更直接受綠界站內付 callback / ReturnURL 契約約束：系統不能任意改變綠界送來的 payload 形狀，也不能依賴綠界不保證的額外授權頭；因此 callback 的安全邊界必須建立在本地 exposure policy 與既有內容驗證，而不是 query-string secret。

## Usage Rule
- 任何關於 callback 欄位、加密資料結構、ReturnURL 用法的判斷，都必須以綠界官方開發者文件為準。
- 本 spec 不假設綠界會附帶自定義 header、nonce 或客製簽章。

## Final Verification Status
- Status: `completed`
- Official contract check: 已依綠界開發者中心的 `ReturnURL` / callback 行為確認本地只能依官方 `Data` 載荷處理，不能假設綠界提供額外本地授權層。
- Local evidence:
  - `EcpayCvsPaymentService` 僅把 operator 設定的 `ReturnURL` 帶入取號請求
  - `EcpayCallbackHttpServer` 改由本地 exposure policy 決定是否允許接收 callback
  - `FiatPaymentCallbackService` 維持 merchant / amount / paid-status / idempotency 驗證

## Dependency Records

### Dependency 1: ECPay Payment Callback / ReturnURL
- Type: `API`
- Version / Scope: `站內付 2.0 / Not fixed`
- Official Source: `https://developers.ecpay.com.tw/`
- Why It Matters: 本地 callback server 需要接收綠界送達的 `ReturnURL` request，並解析官方定義的 `Data` 加密載荷。
- Invocation Surface:
  - Entry points: `ReturnURL`, callback POST body, encrypted `Data`
  - Call pattern: `webhook`
  - Required inputs: `MerchantID`, `HashKey`, `HashIV`, callback URL
  - Expected outputs: 綠界對已設定 callback URL 發送付款結果通知；本地系統需解密並驗證欄位
- Constraints:
  - Supported behavior: 綠界主導 callback request shape；本地只能依官方欄位處理
  - Limits: 不保證附帶本地自定義授權 header；授權不可建立在 query token 之外的「綠界會幫你保密 URL」假設
  - Compatibility: stage 與 production 使用不同環境與金鑰，但 callback 流程都依賴同一組本地處理邏輯
  - Security / access: 本地必須自行決定 callback endpoint 的暴露方式與額外 admission policy
- Failure Contract:
  - Error modes: 缺少 `Data`、解密失敗、欄位不符、重複通知、未付款通知
  - Caller obligations: 驗證 merchant、amount、order、paid status；保留冪等與重複通知處理
  - Forbidden assumptions: 不可假設 callback URL query string 不會被記錄或外洩；不可假設 stage public exposure 仍具安全性
- Verification Plan:
  - Spec mapping: `R1.x-R3.x`
  - Design mapping: `Proposed Architecture`, `Component Changes`
  - Planned coverage: `UT-71-01`, `UT-71-02`, `UT-71-03`, `IT-71-01`
  - Evidence notes: 以官方開發者中心的 callback / ReturnURL 說明為外部契約基準

### Dependency 2: ECPay CVS Payment Code Generation
- Type: `API`
- Version / Scope: `站內付 CVS 取號 / Not fixed`
- Official Source: `https://developers.ecpay.com.tw/`
- Why It Matters: `EcpayCvsPaymentService` 需把本地設定的 `ReturnURL` 放進取號請求，因此 callback URL 的安全設計不能違反官方取號流程。
- Invocation Surface:
  - Entry points: `GenPaymentCode`, request `OrderInfo.ReturnURL`
  - Call pattern: `sync API request`
  - Required inputs: `MerchantID`, `HashKey`, `HashIV`, `OrderInfo`, `CVSInfo`
  - Expected outputs: 綠界回傳 payment code，之後使用提供的 `ReturnURL` 送付款結果
- Constraints:
  - Supported behavior: `ReturnURL` 是由商戶指定，綠界只負責回打；不承擔本地授權秘密保護責任
  - Limits: callback URL 一旦帶 query secret，就可能被代理／APM／access log 記錄
  - Compatibility: 不改變現有取號 payload shape 與 order 建立流程
  - Security / access: URL 不應攜帶本地安全假設所需的機密值
- Failure Contract:
  - Error modes: 取號失敗、response `TransCode` / `RtnCode` 非成功、callback URL 設定不當
  - Caller obligations: 用安全的本地 callback exposure policy 取代 URL secret
  - Forbidden assumptions: 不可把 query token 視為官方支援的 callback authentication layer
- Verification Plan:
  - Spec mapping: `R1.1-R2.2`
  - Design mapping: `Component 1`, `Component 2`
  - Planned coverage: `UT-71-01`, `UT-71-02`
  - Evidence notes: 取號流程只約束 callback URL 被帶入請求，不保證本地 URL secret 安全
