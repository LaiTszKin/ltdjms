# Tasks: external-deps-adoption

- Date: 2026-05-24
- Feature: external-deps-adoption

## **Task 1: 安裝 runtime/dev 依賴**

Purpose: 鎖定 batch 批准的外部套件版本
Requirements: R1.1-R1.3
Scope: `packages/ai/package.json`, `packages/shop/package.json`, `pnpm-lock.yaml`
Out of scope: 業務邏輯修改

- T1.1 [ ] **packages/ai 新增 LangGraph deps** — `@langchain/langgraph@^1.3.2`, checkpoint-postgres, checkpoint-redis, `zod-to-json-schema@^3.25.2`
  - Verify: `pnpm --filter @ltdjms/ai list @langchain/langgraph`

- T1.2 [ ] **packages/shop 新增 test deps** — `@robojs/mock@0.1.1-next.1`, `supertest@^7.2.2`, `@types/supertest@^6.0.0`
  - Verify: `pnpm --filter @ltdjms/shop list @robojs/mock`

- T1.3 [ ] **pnpm install + make build**
  - Verify: exit code 0

## **Task 2: Vitest JSON parity helper**

Purpose: 三模組共用 oracle 比對工具
Requirements: R2.1-R2.2
Scope: `packages/shared/src/__tests__/parity/`
Out of scope: 各模組 parity 測試本體

- T2.1 [ ] **建立 json-snapshot helper** — `assertJsonParity`, `normalizeEmbedForSnapshot`
  - Verify: 單元測試 helper 本身通過

- T2.2 [ ] **README 片段** — 在 batch preparation 或 shared test docs 記錄 Java fixture → snapshot 流程
  - Verify: 文件可被 shop spec Task 1 引用

## **Task 3: LangGraph checkpoint PoC**

Purpose: 驗證 Agent 持久化可行路徑
Requirements: R3.1-R3.3
Scope: `packages/ai/src/__tests__/integration/langgraph-checkpoint.poc.test.ts`
Out of scope: 生產 DI wiring

- T3.1 [ ] **Postgres checkpoint write/read PoC**
  - Verify: `pnpm vitest run --project @ltdjms/ai -t "postgres checkpoint"`

- T3.2 [ ] **Redis checkpoint write/read PoC**（或 document Postgres-only fallback）
  - Verify: PoC test 綠或 `design.md` 記錄 fallback

- T3.3 [ ] **PoC 結論寫入 design.md** — streaming 外層保留策略
  - Verify: ai-agent spec 可引用結論

## **Task 4: zod-to-json-schema PoC**

Purpose: 降低 17 tool schema 維護成本
Requirements: R4.1-R4.2
Scope: `packages/ai/src/__tests__/unit/zod-tool-schema.poc.test.ts`
Out of scope: 全部 17 tool 遷移

- T4.1 [ ] **create_channel + list_channels schema PoC**
  - Verify: generated JSON schema 含 required fields

- T4.2 [ ] **StructuredTool.fromZod 或等價 binding 驗證**
  - Verify: PoC test 綠

## **Task 5: @robojs/mock PoC**

Purpose: 驗證 Shop interaction 測試 harness
Requirements: R5.1-R5.2
Scope: `packages/shop/src/__tests__/poc/robojs-mock.poc.test.ts`
Out of scope: 完整 ShopView parity

- T5.1 [ ] **Mock /shop slash interaction smoke**
  - Verify: test 綠或 fallback 策略寫入 contract.md

## **Task 6: supertest PoC**

Purpose: 驗證 callback HTTP 測試 harness
Requirements: R6.1-R6.2
Scope: `packages/shop/src/__tests__/poc/supertest-callback.poc.test.ts`
Out of scope: ECPay 簽章完整 E2E

- T6.1 [ ] **Callback route smoke with supertest**
  - Verify: test 綠且不破坏既有 `payment-callback.test.ts`
