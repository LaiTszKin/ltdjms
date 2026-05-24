# Checklist: membership-payment-discount

- [x] C1 白銀 9折 escort 法幣單 ECPay 金額正確
- [x] C2 非 escort 商品無折扣
- [x] C3 callback 驗證通過
- [x] C4 Shop 確認頁顯示會員價
- [x] C5 貨幣 escort 商品折後扣款
- [x] C6 `make verify`

## Test Strategy

| 類型 | 案例 |
|------|------|
| Unit | PricingService 各 tier |
| Integration | FiatOrderService create + mock ECPay |
| Regression | 既有 FiatPaymentCallbackService tests |
