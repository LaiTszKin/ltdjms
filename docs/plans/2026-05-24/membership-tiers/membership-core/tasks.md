# Tasks: membership-core

- Date: 2026-05-24
- Feature: membership-core

## **Task 1: Flyway migration**

Purpose: 建立 membership 主表
Requirements: R3.1–R3.2
Scope: `V029__create_global_member_membership.sql`

- T1.1 [ ] **撰寫 migration** — 見 design.md schema
  - Verify: `make build`；Flyway migrate 成功

## **Task 2: Domain 常數與 evaluator**

Purpose: 定稿六等參數與純函式判定
Requirements: R1.1–R1.3, R2.1–R2.3
Scope: `membership/domain/*`

- T2.1 [ ] **MembershipTier enum + MembershipTierConfig**
  - Verify: unit test 斷言門檻/折扣/贈幣
- T2.2 [ ] **MembershipTierEvaluator**
  - Verify: `MembershipTierEvaluatorTest` 覆蓋 coordination 表邊界值

## **Task 3: Repository + DI**

Purpose: 持久化與注入
Requirements: R3.3
Scope: `membership/persistence/*`, `MembershipModule.java`

- T3.1 [ ] **JdbcMembershipRepository**
  - Verify: integration test findOrCreate + save
- T3.2 [ ] **MembershipModule 註冊**
  - Verify: `make build`
