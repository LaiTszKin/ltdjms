# Tasks: dependency-upgrade-express

- Date: 2026-05-24
- Feature: dependency-upgrade-express

## **Task 1: Bump Express**

Requirements: R1.1
Scope: `packages/shop/package.json`

- T1.1 [x] **更新 express@^5.2.1、@types/express@^5.0.6**
  - Verify: `pnpm install`

## **Task 2: 遷移 callback server**

Requirements: R1.2, R1.3, R2.1
Scope: shop HTTP server 源碼

- T2.1 [x] **審計 route 定義** — 修復 Express 5 path/wildcard breaking changes
- T2.2 [x] **審計 middleware 鏈** — body parser、error handler
- T2.3 [x] **修復測試** — supertest/mock 適配
  - Verify: `pnpm vitest run --project @ltdjms/shop`

## **Task 3: E2E 驗證（可選）**

Requirements: R2.2
Scope: ECPay E2E 測試

- T3.1 [x] **RUN_ECPAY_E2E=true make test** — 若 stage credentials 可用
  - Verify: E2E 測試通過或記錄 N/A 原因
  - N/A: Docker runtime 不可用；ECPay stage E2E 需 `RUN_ECPAY_E2E=true` 與 stage credentials
