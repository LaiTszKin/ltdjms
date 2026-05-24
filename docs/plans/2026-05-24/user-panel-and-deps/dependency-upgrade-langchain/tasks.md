# Tasks: dependency-upgrade-langchain

- Date: 2026-05-24
- Feature: dependency-upgrade-langchain

## **Task 1: Bump AI dependencies**

Requirements: R1.1, R2.1
Scope: `packages/ai/package.json`

- T1.1 [x] **更新 package.json** — @langchain/core 1.x、@langchain/openai 1.x、marked 18
  - Verify: `pnpm install`

## **Task 2: LangChain 1.x API 遷移**

Requirements: R1.2, R1.3
Scope: `packages/ai/src/`

- T2.1 [x] **更新 ChatOpenAI 初始化** — 適配 1.x constructor/options
- T2.2 [x] **更新 Agent / tool binding** — 適配 Runnable API
- T2.3 [x] **修復測試 mock** — 適配新 response shape
  - Verify: `pnpm vitest run --project @ltdjms/ai`

## **Task 3: Marked 18 遷移**

Requirements: R2.2, R2.3
Scope: markdown 管線檔案

- T3.1 [x] **更新 marked 呼叫** — async/sync API
- T3.2 [x] **更新 markdown 測試 oracle**
  - Verify: markdown 相關測試全綠
