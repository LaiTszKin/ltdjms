# Coordination: java-parity-shop-ai

- Date: 2026-05-24
- Batch: java-parity-shop-ai

## Business Goals

以 Java bot 為唯一 oracle，在 TypeScript bot 上完成 Shop、AI Chat、AI Agent 三模組的 1:1 行為與 UI 對齊；並引入已批准的外部依賴降低 parity 測試與 Agent 持久化實作複雜度。

- Batch members: [external-deps-adoption, shop-java-parity, ai-chat-java-parity, ai-agent-java-parity]
- Shared outcome: TS `@ltdjms/shop` 與 `@ltdjms/ai` 在 Discord 互動（embed、button、modal、customId、分頁、串流）與後端行為（分頁查詢、購買流程、路由決策、工具審計、對話記憶）與 Java 完全一致；`make verify` 全綠；Architecture Atlas 同步更新
- Out of scope: 修改 Java bot、新增 Java 沒有的功能、管理員商品 CRUD 面板 parity（admin product panel）、更換 LLM 模型或 prompt 策略、引入 Discord Components V2

## Design Principles

- Current baseline: `@ltdjms/shop` 已有 substantial 後端實作但 browse/purchase handler 合併、分頁 off-by-one、缺少 confirm/fiat DM/escort handoff；`@ltdjms/ai` 結構已 port 但 listener/tool 測試缺失、ToolExecutionInterceptor 僅 pino、無 stream markdown processor、無 tool_execution_log 持久化
- Shared invariants: Java 為衝突解決 oracle；沿用 `@ltdjms/shared` 的 `EmbedView`/`ButtonView`/`DiscordInteraction` 抽象；UI 維持 embed + ActionRow（非 Components V2）；Markdown 保留 `marked` 管線；Node.js >=22
- Shared constraints: 不改 Java 原始碼；parity 驗收以 Java 測試/fixtures → TS structural/JSON snapshot；外部依賴版本鎖定於 `external-deps-adoption` contract；Redis Stack（RedisJSON + RediSearch）為 LangGraph Redis checkpoint 前置
- Legacy direction: Java `ShopView`/`ShopButtonHandler`/`ShopSelectMenuHandler`、`AIChatMentionListener`、`LangChain4jAIChatService`、`ToolExecutionInterceptor`、`SimplifiedChatMemoryProvider` 為行為 oracle
- Compatibility window: `shop-handler.ts` 可暫時保持單檔，parity 完成後可選拆分為 Java 形狀的三 handler
- Cleanup after cutover: 移除 parity 過渡 shim、更新 `docs/features/` 若 TS 行為已對齊 Java

## Spec Boundaries

### Ownership Map

#### Spec Set 1: external-deps-adoption
- Primary concern: 引入並 PoC 驗證 parity 所需外部依賴（LangGraph checkpoint、zod-to-json-schema、@robojs/mock、supertest）；Vitest JSON snapshot 慣例文件化
- Allowed touch points: 根/`packages/shop`/`packages/ai` `package.json`、`pnpm-lock.yaml`、dev test harness、Docker Compose Redis image（若需）、PoC 測試檔
- Must not change: 業務邏輯語意、Java 原始碼

#### Spec Set 2: shop-java-parity
- Primary concern: `/shop` 成員端商店 UI + browse/purchase 互動 + `ShopService` 分頁契約 1:1 對齊 Java
- Allowed touch points: `packages/shop/src/view/`、`packages/shop/src/commands/`、`packages/shop/src/services/shop.service.ts`、`packages/shop/src/services/product-service.ts`（`getAllPurchasableProducts`）、parity fixtures/tests
- Must not change: ECPay callback 業務語意（已有測試）、admin product panel、Java 原始碼

#### Spec Set 3: ai-chat-java-parity
- Primary concern: @mention 路由、串流輸出、Markdown 管線（含 stream processor）、頻道白名單、PromptLoader、MessageChunkAccumulator 對齊 Java
- Allowed touch points: `packages/ai/src/commands/`、`packages/ai/src/services/routing/`、`packages/ai/src/services/LangChainAIChatService.ts`、`packages/ai/src/markdown/`、parity fixtures/tests
- Must not change: 17 tool 實作細節（agent spec）、tool_execution_log（agent spec）、Java 原始碼

#### Spec Set 4: ai-agent-java-parity
- Primary concern: 17 Discord 工具、授權 guard、LangGraph checkpoint 持久化、tool_execution_log 審計、Agent/Tool listeners、Agent 頻道配置 Redis 整合測試
- Allowed touch points: `packages/ai/src/tools/`、`packages/ai/src/services/ToolExecutionInterceptor.ts`、`packages/ai/src/persistence/`、`packages/ai/src/di/ai-module.ts`、agent listeners、parity tests
- Must not change: AI Chat 路由/matrix（chat spec）、shop 模組、Java 原始碼

### Collisions & Integration

- Shared files & edit rules:
  - `packages/ai/package.json` — external-deps-adoption 先加 LangGraph 等；ai-chat/agent spec 只改 import，不重複 bump
  - `packages/shop/package.json` — external-deps-adoption 加 @robojs/mock/supertest；shop-java-parity 不改 devDeps 版本
  - `packages/ai/src/di/ai-module.ts` — ai-chat spec 註冊 chat/markdown；ai-agent spec 註冊 checkpoint/interceptor/listeners；合併前協調 DI token
  - `pnpm-lock.yaml` — 每次 merge 後 `pnpm install` 並提交
- Shared API / schema freeze: `@ltdjms/economy`、`@ltdjms/shared` Discord 抽象在 batch 期間 additive-only
- Compatibility shim retention: 手寫 agent loop 保留至 LangGraph PoC 通過（external-deps-adoption T3）
- Merge order: `preparation` → `external-deps-adoption` → (`shop-java-parity` ∥ `ai-chat-java-parity`) → `ai-agent-java-parity`
- Integration checkpoints:
  - external-deps-adoption 完成：`make build` + PoC 測試綠
  - shop-java-parity 完成：ShopView/Handler parity 測試全綠
  - ai-chat-java-parity 完成：routing + markdown + listener 測試全綠
  - ai-agent-java-parity 完成：17 tool tests + interceptor integration 全綠
  - batch 完成：`make verify` + `apltk architecture validate` + architecture diff review
- Re-coordination trigger: LangGraph PoC 無法保留 REASONING/TOOL_INTENT 串流控制 → 暫停 ai-agent spec，改用手寫 loop + Drizzle 持久化
