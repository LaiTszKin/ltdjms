# Tasks: Issues 71-72 綠界 Callback 驗證強化

- Date: 2026-04-09
- Feature: Issues 71-72 綠界 Callback 驗證強化

## **Task 1: 移除 URL secret 耦合**

對應 `R1.1-R1.2`，核心目標是讓 callback URL 本身不再攜帶可外洩授權資訊。

- 1. [x] 調整 `EcpayCvsPaymentService` 與對應測試
  - 1.1 [x] 讓 `buildCallbackReturnUrl()` 回傳 operator 設定值，不再追加 `token`
  - 1.2 [x] 更新既有單元測試與相關文件敘述，移除 query-token 假設

## **Task 2: 建立 stage/public exposure fail-closed 規則**

對應 `R2.1-R2.3`，核心目標是用既有 config 決定 callback server 可否對外暴露，而不是倚賴 query token 充當授權。

- 2. [x] 收斂 `EcpayCallbackHttpServer` 授權與啟動判斷
  - 2.1 [x] 移除 query-token 驗證邏輯
  - 2.2 [x] 在 `ECPAY_STAGE_MODE=true` + public bind 時於啟動階段 fail closed

## **Task 3: 驗證 production happy path 與 idempotency 不回歸**

對應 `R3.1-R3.3`，核心目標是保留 callback 內容驗證與 paid transition 冪等語意，僅替換 trust boundary。

- 3. [x] 擴充 callback 服務與 HTTP server 測試
  - 3.1 [x] 覆蓋 production legit callback、duplicate callback、unpaid callback 與 validation failure
  - 3.2 [x] 覆蓋 stage/public 啟動拒絕、production public bind 允許與無 query token 的新行為

## Notes
- 本 spec 故意不新增新的 config key，避免與 issue 74 的 canonical schema 正規化互相耦合。
- `ECPAY_CALLBACK_SHARED_SECRET` 若在 shop 端不再被使用，後續清理與文件對齊交由 issue 74 或獨立 cleanup 處理。
