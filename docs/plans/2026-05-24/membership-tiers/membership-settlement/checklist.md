# Checklist: membership-settlement

- [x] C1 到期用戶被 settle
- [x] C2 avgM 正確反映週期內 spend 總和
- [x] C3 降級場景通過
- [x] C4 青銅保底（avgM=0 但有 qualifying flag）
- [x] C5 tier 變更 event 發送
- [x] C6 `make verify`

## Test Strategy

| 類型 | 案例 |
|------|------|
| Unit | SettlementService + mock spend repo |
| Integration | 插入 spend → settle → assert tier |
| Edge | 並發 settle idempotent |
