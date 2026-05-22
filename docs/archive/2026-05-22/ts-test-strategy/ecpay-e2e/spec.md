# Spec: ECPay Payment E2E Tests

- Date: 2026-05-22
- Feature: ECPay Payment E2E Tests
- Owner: [To be filled]

## Goal

使用綠界 Stage API 與測試商戶 MerchantID `2000132`，驗證法幣付款系統的取號合約、回撥處理、對帳排程與 ECPay 加解密在真實網路環境下完全正確。

## Scope

### In Scope
- 取號合約測試（CVS 超商繳費）：實際呼叫 ECPay Stage API 產生繳費代碼
- 回撥處理測試：手動建構 `SimulatePaid=1` payload → 直接 POST callback server → assert DB 訂單狀態變更
- 對帳排程測試：手動建構多筆待對帳訂單 → 觸發 reconciliation → assert 狀態
- AES-256-CBC 加解密正確性（與 Java 版 golden data 交叉驗證）
- CheckMacValue 簽章正確性

### Out of Scope
- 真實金流（使用測試特店，不涉及真實貨幣）
- 信用卡付款測試（僅測試 CVS 超商繳費）
- ECPay 後台操作
- Discord 通知發送（mock 驗證即可）

## Functional Behaviors (BDD)

### Requirement 1: CVS 超商繳費取號
**GIVEN** Testcontainer PostgreSQL 中有一筆 PENDING_PAYMENT 的法幣訂單
**AND** ECPay Stage 環境可連線
**AND** 使用 MerchantID `2000132`、HashKey `ejCk326UnaZWKisg`、HashIV `q9jcZX8Ib9LM8wYk`
**WHEN** 呼叫 EcpayCvsPaymentService.createPaymentForm(orderNumber, amountTwd)
**THEN** ECPay Stage API 回傳成功
**AND** 回傳的 paymentNo（繳費代碼）格式正確（非空字串）
**AND** DB 中訂單的 paymentNo 已更新
**AND** CheckMacValue 驗證通過

**Requirements**:
- [ ] R1.1 成功取號後 paymentNo 非空
- [ ] R1.2 同訂單取號兩次回傳相同 paymentNo（ECPay 端冪等）
- [ ] R1.3 CheckMacValue 驗證正確
- [ ] R1.4 網路逾時重試機制正確（最多 3 次）

### Requirement 2: ECPay 回撥處理 — SimulatePaid=1
**GIVEN** Testcontainer PostgreSQL 中有一筆已取號的法幣訂單（狀態 PENDING_PAYMENT）
**WHEN** 建構 SimulatePaid=1 的回撥 payload → AES 加密 → 直接 POST 到 callback server
**THEN** callback server 回傳 HTTP 200 + "1|OK"
**AND** DB 中訂單狀態變更為 PAID
**AND** paidAt timestamp 已設定
**AND** fulfillment 流程已觸發（post-payment worker）

**Requirements**:
- [ ] R2.1 回撥 payload AES 加密正確（ECPay 可解密）
- [ ] R2.2 callback server 正確解析並驗證 CheckMacValue
- [ ] R2.3 訂單狀態正確轉移（PENDING_PAYMENT → PAID）
- [ ] R2.4 重複回撥冪等（同一訂單第二次 callback 不回傳錯誤）
- [ ] R2.5 不存在的訂單回撥回傳 HTTP 200（ECPay 協議要求）

### Requirement 3: ECPay 回撥處理 — CheckMacValue 驗證失敗
**GIVEN** 一筆回撥 payload 的 CheckMacValue 被篡改
**WHEN** POST 到 callback server
**THEN** callback server 回傳 HTTP 400 + "0|FAIL"
**AND** DB 訂單狀態不變

**Requirements**:
- [ ] R3.1 CheckMacValue 不符時回傳 400
- [ ] R3.2 不變更任何 DB 狀態
- [ ] R3.3 錯誤日誌記錄完整

### Requirement 4: 對帳排程
**GIVEN** DB 中有多筆 PENDING_PAYMENT 訂單（已過期或即將過期）
**WHEN** 觸發 FiatPaymentReconciliationService
**THEN** 對每筆訂單呼叫 ECPay TradeQuery API
**AND** 根據查詢結果更新訂單狀態
**AND** 對帳次數 (reconciliationAttemptCount) 遞增

**Requirements**:
- [ ] R4.1 TradeQuery API 查詢成功並正確解析
- [ ] R4.2 已付款但本地未更新的訂單被正確同步
- [ ] R4.3 已逾期的未付款訂單被標記為 EXPIRED
- [ ] R4.4 對帳次數上限後停止查詢

### Requirement 5: AES 加解密交叉驗證
**GIVEN** Java 版產生的已知 golden data（明文 + 密文 + key/iv）
**WHEN** TS 版使用相同 key/iv 進行 AES-256-CBC 加密
**THEN** TS 版加密結果與 Java 版一致（或可互相解密）
**AND** TS 版解密 Java 版密文得到正確明文

**Requirements**:
- [ ] R5.1 TS 版 AES 加密結果可被 ECPay Server 解密（透過實際 API call 驗證）
- [ ] R5.2 TS 版可正確解密 ECPay Server 回傳的加密 payload
- [ ] R5.3 URL 編碼與 Java 版 `URLEncoder.encode()` 行為一致

## Error and Edge Cases
- [ ] ECPay Stage API 無法連線時 skip 測試（非 fail），透過 `RUN_ECPAY_E2E` 環境變數控制
- [ ] ECPay 回傳非預期格式的錯誤處理
- [ ] 網路逾時重試（最多 3 次）後仍失敗則 skip
- [ ] callback payload JSON 格式錯誤（非 AES 加密內容）
- [ ] callback payload 缺少必要欄位（MerchantTradeNo, RtnCode 等）
- [ ] SimulatePaid=1 flag：TS 和 Java 目前都不檢查此 flag，僅做文件記錄

## Clarification Questions
- [ ] 是否需要為 SimulatePaid=1 flag 加入檢查邏輯？（目前 TS/Java 版皆不檢查，但綠界建議在測試環境中檢查此 flag 以區分真實付款與模擬付款）

## References
- Official docs:
  - https://developers.ecpay.com.tw/ — 綠界開發者文件
  - https://www.ecpay.com.tw/Content/files/ecpay_cvs.pdf — CVS 超商繳費 API 文件
- Test credentials:
  - MerchantID: `2000132`
  - HashKey: `ejCk326UnaZWKisg`
  - HashIV: `q9jcZX8Ib9LM8wYk`
  - 測試信用卡: `4311-9522-2222-2222` / `222`
  - 測試後台: https://vendor-stage.ecpay.com.tw (Stagetest1234 / test1234 / 53538851)
- Related code files:
  - `packages/shop/src/services/ecpay-cvs-payment.service.ts` — CVS 取號
  - `packages/shop/src/services/ecpay-trade-query.service.ts` — 交易查詢
  - `packages/shop/src/services/fiat-payment-callback.service.ts` — 回撥處理
  - `packages/shop/src/services/fiat-payment-reconciliation.service.ts` — 對帳排程
  - `packages/shop/src/crypto/ecpay-aes.ts` — AES 加解密
  - `packages/shop/src/crypto/ecpay-checkmac.ts` — CheckMacValue
  - `packages/shop/src/crypto/url-encoder.ts` — URL 編碼
  - `packages/shop/src/web/ecpay-callback-server.ts` — HTTP callback server
  - `packages/shop/src/__tests__/ecpay-crypto.test.ts` — 現有加解密單元測試
  - `packages/shop/src/__tests__/ecpay-crypto.crosscheck.test.ts` — 交叉驗證測試
