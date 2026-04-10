# Tasks: Issue 75 貨幣持久層單一路徑化

- Date: 2026-04-09
- Feature: Issue 75 貨幣持久層單一路徑化

## **Task 1: 對齊 production 與測試的 repository 建構方式**

對應 `R1.1-R1.2`，核心目標是讓主要回歸測試改走 production-aligned JOOQ path。

- 1. [x] 收斂 currency 測試中的 repository instantiation
  - 1.1 [x] 盤點目前直接 new `Jdbc*` repository 的 integration / performance tests
  - 1.2 [x] 改為使用 JOOQ repository 或共用的 production-aligned test factory / helper

## **Task 2: 補足 JOOQ guild config coverage**

對應 `R2.1-R2.3`，核心目標是讓 account 與 guild config 都有對等的 production path coverage。

- 2. [x] 擴充 repository 與 service regression tests
  - 2.1 [x] 為 `JooqGuildCurrencyConfigRepository` 加入完整 integration cases
  - 2.2 [x] 讓 `BalanceServiceIntegrationTest`、`RepositoryIntegrationTest` 等覆蓋 JOOQ config + account 組合

## **Task 3: 退出平行 JDBC 真相**

對應 `R3.1-R3.3`，核心目標是讓 main-source JDBC account / config 不再承擔 live ownership。

- 3. [x] 為 JDBC account / config repositories 做明確處置
  - 3.1 [x] 若可安全移除，直接刪除主程式中的平行 JDBC implementations
  - 3.2 [x] 若需短期保留，限制為 transitional/test-only 路徑並寫明清理終點

## Notes
- 本 spec 故意不處理 transaction repository，避免把 issue 75 擴大成整個 currency persistence 重寫。
- 若需要共用 test factory，該 factory 的目的應是「對齊 production path」，不是再包一層新的 repository abstraction。
