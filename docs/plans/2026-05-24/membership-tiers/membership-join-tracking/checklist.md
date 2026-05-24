# Checklist: membership-join-tracking

- [ ] C1 preparation P1 完成
- [ ] C2 首次 join 寫入 earliest + settlement_day + next_settlement_at
- [ ] C3 第二次 join（更晚日期）不覆寫 earliest
- [ ] C4 join 日 31 → settlement_day=28
- [ ] C5 `make verify`

## Test Strategy

| 類型 | 重點 |
|------|------|
| Unit | MembershipJoinService + FixedClock |
| Integration | 可選：repository + service 寫入 DB |
