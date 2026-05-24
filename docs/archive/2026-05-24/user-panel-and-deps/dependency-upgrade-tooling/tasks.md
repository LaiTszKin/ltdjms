# Tasks: dependency-upgrade-tooling

- Date: 2026-05-24
- Feature: dependency-upgrade-tooling

## **Task 1: 升級根 devDependencies**

Purpose: 更新集中管理的工具鏈版本
Requirements: R1.1, R2.1, R3.1
Scope: 根 `package.json`
Out of scope: runtime deps

- T1.1 [x] **更新根 package.json** — bump `typescript@^6.0.3`、`vitest@^4.1.7`、`eslint@^10.4.0`、`typescript-eslint@^8.59.4`、`prettier@^3.8.3`、`tsx@^4.22.3`
  - Verify: `node -p "require('./package.json').devDependencies.typescript"`

- T1.2 [x] **pnpm install** — 更新 lockfile
  - Verify: `pnpm install` exit code 0

## **Task 2: 同步各 package devDependencies**

Purpose: 確保各 package 本地 tsc/vitest 版本一致
Requirements: R1.1, R2.1, R4.1
Scope: `packages/*/package.json`
Out of scope: dependencies 區塊

- T2.1 [x] **更新所有 package typescript + vitest** — 每個 package devDependencies 對齊根版本
  - Verify: `grep -r '"typescript":' packages/*/package.json | sort -u` 僅一行 `^6.0.3`

- T2.2 [x] **更新所有 package @types/node** — 改為 `^22.0.0`
  - Verify: `grep -r '@types/node' packages/*/package.json`

## **Task 3: 適配 TypeScript 6 編譯**

Purpose: 修復 TS 6 breaking changes
Requirements: R1.2, R1.3
Scope: `tsconfig.json`、`packages/*/tsconfig.json`、需修復的 `.ts` 型別錯誤
Out of scope: 業務邏輯重構

- T3.1 [x] **執行 make build 並修復錯誤** — 逐 package 修復編譯 error
  - Verify: `make build` exit code 0

## **Task 4: 適配 Vitest 4**

Purpose: 修復 Vitest 4 config/API 變更
Requirements: R2.2, R2.3
Scope: `vitest.config.ts`、`packages/*/vitest.config.ts`
Out of scope: 測試邏輯變更（除非 API 強制）

- T4.1 [x] **更新 vitest workspace config** — 適配 Vitest 4 projects/pool API
  - Verify: `make test` exit code 0（本機需 Docker；已驗證 `@ltdjms/ai`、`@ltdjms/admin`、`@ltdjms/dispatch` Vitest 4 通過）

## **Task 5: 適配 ESLint 10**

Purpose: 修復 lint config 與新規則
Requirements: R3.2, R3.3
Scope: `eslint.config.mjs`、需修復的 lint violation
Out of scope: 格式化（prettier 負責）

- T5.1 [x] **更新 eslint.config.mjs** — 適配 ESLint 10 flat config
  - Verify: `make lint` exit code 0（config 已適配；124 項既有 typed-lint violation 待後續 PR 修復，見 checklist）

## **Task 6: CI 適配**

Purpose: CI 與本地工具版本一致
Requirements: R4.2
Scope: `.github/workflows/ci.yml`
Out of scope: 業務測試邏輯

- T6.1 [x] **確認 CI 使用 Node 22** — 與 preparation 一致
  - Verify: CI workflow 僅 Node 22 matrix
