# Tasks: dependency-upgrade-core-runtime

- Date: 2026-05-24
- Feature: dependency-upgrade-core-runtime

## **Task 1: 升級 shared package runtime deps**

Purpose: shared 為所有 package 基礎，優先升級
Requirements: R1.1, R2.1, R2.2, R3.1, R4.1, R4.2
Scope: `packages/shared/package.json`、`packages/shared/src/`
Out of scope: langchain, express

- T1.1 [ ] **bump shared dependencies** — zod 4、drizzle 0.45、pino 10、discord.js 14.26、pg 8.21、ioredis 5.10
  - Verify: `pnpm --filter @ltdjms/shared exec tsc --noEmit`

- T1.2 [ ] **修復 shared zod schemas** — 適配 zod 4 API
  - Verify: `pnpm vitest run --project @ltdjms/shared`

- T1.3 [ ] **修復 shared logger** — 適配 pino 10
  - Verify: shared 測試通過

## **Task 2: 升級 economy / games / dispatch**

Requirements: R1.2, R2.3
Scope: `packages/economy/`、`packages/games/`、`packages/dispatch/`

- T2.1 [ ] **bump + fix economy** — zod schema、 drizzle query
  - Verify: `pnpm vitest run --project @ltdjms/economy`

- T2.2 [ ] **bump + fix games** — 同上
  - Verify: `pnpm vitest run --project @ltdjms/games`

- T2.3 [ ] **bump + fix dispatch** — reflect-metadata 改 runtime dep
  - Verify: `pnpm vitest run --project @ltdjms/dispatch`

## **Task 3: 升級 shop / admin / ai / bot**

Requirements: R3.2, R4.3
Scope: `packages/shop/`、`packages/admin/`、`packages/ai/`、`apps/bot/`

- T3.1 [ ] **bump + fix shop** — zod + pino（express 留給 express spec）
  - Verify: shop 測試通過

- T3.2 [ ] **bump + fix admin + ai + bot**
  - Verify: `make verify`

## **Task 4: 版本一致性清理**

Requirements: R3.1, R4.2
Scope: 所有 package.json

- T4.1 [ ] **統一 pino/pg/reflect-metadata 版本** — 消除 package 間不一致
  - Verify: `pnpm why pino` 僅一個 major 版本
