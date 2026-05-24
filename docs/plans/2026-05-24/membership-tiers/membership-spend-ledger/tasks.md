# Tasks: membership-spend-ledger

## Task 1: Migration + repository
- T1.1 [ ] V030 spend entry + fiat_order columns
- T1.2 [ ] `MembershipSpendRepository`

## Task 2: Spend service
- T2.1 [ ] M resolver + record + bronze flag
- T2.2 [ ] `sumListPriceInPeriod`

## Task 3: Worker hook
- T3.1 [ ] `FiatOrderPostPaymentWorker` 整合
  - Verify: unit test mock worker 路徑；IT 驗 idempotent
