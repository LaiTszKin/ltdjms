# Checklist: membership-spend-ledger

- [x] C1 escort 法幣單寫入 spend entry
- [x] C2 M = catalog 原價非折後價
- [x] C3 貨幣購買不寫 ledger
- [x] C4 重複 orderNumber 不重複計
- [x] C5 M≥500 設 has_qualifying_bronze_order
- [x] C6 `make verify`

## Test Strategy

| 類型 | 案例 |
|------|------|
| Unit | M resolver、qualifying bronze |
| Integration | Post-payment hook + DB unique constraint |
| Contract | 與 coordination「僅法幣 TWD」一致 |
