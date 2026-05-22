# Spec: Shop Business Invariant PBT

- Date: 2026-05-22
- Feature: Shop Business Invariant PBT
- Owner: [To be filled]

## Goal

透過 Integration PBT 驗證 Shop 模組所有用戶可操作功能的業務不變量（兌換碼冪等、庫存遞減、貨幣購買守恆），一次走完整 handler→service→repository→real DB 管線。

## Scope

### In Scope
- 兌換碼兌換：冪等性（同一 code 僅能兌換一次）、庫存遞減、獎勵正確發放
- 商店購買（貨幣）：`balance - cost === balance_after`、交易記錄完整性
- 法幣訂單建立：orderNumber 唯一、paymentNo 格式正確、狀態初始為 PENDING_PAYMENT
- 貨幣購買（Fiat 付款後貨幣發放）：獎勵金額正確

### Out of Scope
- ECPay 外部 API 呼叫（由 ecpay-e2e 負責）
- 管理員商品上架／下架流程（由 admin-pbt 負責）
- Discord 付款面板 UI 交互
- Fiat 付款回撥處理邏輯（由 ecpay-e2e 負責）

## Functional Behaviors (BDD)

### Requirement 1: 兌換碼兌換 — 冪等性
**GIVEN** 商品 P 有一個兌換碼 CODE-A（quantity=1）
**WHEN** U1 成功兌換 CODE-A
**AND** U2 嘗試兌換同一 CODE-A
**THEN** U2 的操作回傳錯誤（code already redeemed）
**AND** 兌換碼庫存變為 0（已使用）
**AND** U1 收到商品對應的獎勵

**Requirements**:
- [ ] R1.1 同一兌換碼僅能被兌換一次
- [ ] R1.2 第二次兌換回傳 DomainError
- [ ] R1.3 首次兌換後庫存正確遞減
- [ ] R1.4 獎勵正確發放給首次兌換者

### Requirement 2: 兌換碼兌換 — 庫存管理
**GIVEN** 商品 P 有 3 個兌換碼（CODE-A, CODE-B, CODE-C），每個 quantity=1
**WHEN** 三個不同用戶分別兌換這三個 code
**THEN** 三個兌換均成功
**AND** 每個兌換碼狀態變為 used
**AND** 每個用戶收到對應獎勵
**AND** 第四個用戶嘗試兌換任一個已使用的 code 均失敗

**Requirements**:
- [ ] R2.1 批量兌換碼各自獨立不互相影響
- [ ] R2.2 所有兌換完成後所有 code 狀態為 used
- [ ] R2.3 所有用戶 reward 總和 = 三個商品的 reward 總和

### Requirement 3: 商店購買（貨幣）— 餘額正確扣減
**GIVEN** U1 有 10000 金幣
**AND** 商品 P 價格為 X 金幣
**WHEN** U1 購買商品 P（X ≤ 10000）
**THEN** U1 餘額 = 10000 - X
**AND** 交易記錄顯示正確金額與商品
**AND** U1 收到商品獎勵

**Requirements**:
- [ ] R3.1 購買後餘額精確扣減
- [ ] R3.2 購買成功產生 transaction record
- [ ] R3.3 餘額不足時回傳 DomainError 且不扣款
- [ ] R3.4 獎勵正確發放

### Requirement 4: 法幣訂單建立 — 資料完整性
**GIVEN** Guild A 中有商品 P（TWD 價格 = 100）
**WHEN** U1 建立法幣訂單購買商品 P
**THEN** 訂單建立成功，狀態為 PENDING_PAYMENT
**AND** orderNumber 格式為唯一識別碼
**AND** fiat_order table 中所有必要欄位已寫入
**AND** amountTwd = 商品 TWD 價格

**Requirements**:
- [ ] R4.1 訂單建立後 DB 中可查詢
- [ ] R4.2 orderNumber 唯一
- [ ] R4.3 訂單初始狀態為 PENDING_PAYMENT
- [ ] R4.4 amountTwd 與商品價格一致
- [ ] R4.5 expireAt 正確設定（建立時間 + 逾期期限）

### Requirement 5: 貨幣購買 — 獎勵計算正確
**GIVEN** 商品 P 設定 rewardAmount = 500 金幣
**WHEN** 觸發貨幣購買流程（模擬付款完成後的獎勵發放）
**THEN** 用戶收到 500 金幣
**AND** currencyBalanceAfter 正確反映新餘額

**Requirements**:
- [ ] R5.1 獎勵金額 = 商品設定的 rewardAmount
- [ ] R5.2 rewardGrantedAt timestamp 正確記錄

## Error and Edge Cases
- [ ] 兌換碼已被兌換時的錯誤訊息清晰
- [ ] 兌換碼不存在時的錯誤處理
- [ ] 購買已下架商品的行為
- [ ] 同一用戶重複購買同一商品的行為
- [ ] 法幣訂單建立時 productId 不存在
- [ ] 商品價格為 0 的邊界情況
- [ ] 兌換碼數量為 0 的商品（無兌換碼商品）

## Clarification Questions
None

## References
- Project docs:
  - `docs/features/shop-and-payment.md` — 商店與付款功能說明
  - `docs/principles/state-transition-patterns.md` — 狀態轉移模式
- Related code files:
  - `packages/shop/src/services/redemption.service.ts` — 兌換碼服務
  - `packages/shop/src/services/shop.service.ts` — 商店服務
  - `packages/shop/src/services/fiat-order.service.ts` — 法幣訂單服務
  - `packages/shop/src/services/currency-purchase.service.ts` — 貨幣購買服務
  - `packages/shop/src/domain/fiat-order.ts` — FiatOrder domain
  - `packages/shop/src/domain/redemption-code.ts` — RedemptionCode domain
  - `packages/shop/src/persistence/schema.ts` — DB schema
  - `packages/shop/src/__tests__/redemption-code.test.ts` — 現有單元測試
  - `packages/shop/src/__tests__/fiat-order.test.ts` — 現有單元測試
