# Checklist: membership-benefits-ui

- [ ] C1 結算日 GOLD 發 200 幣
- [ ] C2 同週期不重複發
- [ ] C3 餘額累積無上限
- [ ] C4 user-panel 顯示等級與進度
- [ ] C5 transaction source MEMBERSHIP_GRANT 可查
- [ ] C6 `make verify`

## Test Strategy

| 類型 | 案例 |
|------|------|
| Unit | Grant idempotency |
| Integration | settle → grant → token balance |
| UI | EmbedBuilder 欄位存在性 |
