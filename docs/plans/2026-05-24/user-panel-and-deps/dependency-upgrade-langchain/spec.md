# Spec: dependency-upgrade-langchain

- Date: 2026-05-24
- Feature: dependency-upgrade-langchain
- Owner: laitszkin

## Goal

將 `@ltdjms/ai` 的 LangChain 生態與 Markdown 解析依賴升級至最新穩定版本（@langchain/core 1.x、@langchain/openai 1.x、marked 18），確保 AI 聊天與 Agent 功能行為不變。

## Scope

### In Scope
- 升級 `packages/ai/package.json`：
  - `@langchain/core@^1.1.48`
  - `@langchain/openai@^1.4.7`
  - `marked@^18.0.4`
- 修復 LangChain 1.x API breaking changes（ChatModel、Runnable、tool binding、streaming）
- 修復 marked 18 API 變更（renderer、async parse）
- 更新 AI 相關單元/整合測試

### Out of Scope
- 新增 AI 功能或更換模型
- user-panel、shop express
- 非 AI package 的 zod/runtime 升級（core-runtime spec）

## Functional Behaviors (BDD)

### Requirement 1: LangChain 1.x 遷移
**GIVEN** AI 模組使用 @langchain/core 0.3 + @langchain/openai 0.4
**WHEN** 升級至 1.x
**THEN** AI 聊天回應、Agent tool 執行、頻道 gating 行為不變
**AND** AI 相關測試全綠

**Requirements**:
- [ ] R1.1 bump `@langchain/core`、`@langchain/openai` 至 1.x latest
- [ ] R1.2 修復 ChatOpenAI / Agent executor / tool schema 綁定 API
- [ ] R1.3 `pnpm vitest run --project @ltdjms/ai` 全綠

### Requirement 2: Marked 18 遷移
**GIVEN** Markdown 管線使用 marked 15
**WHEN** 升級至 marked 18
**THEN** Discord embed 渲染結果與升級前一致（允許 whitespace 微差，不允許語意/連結遺失）

**Requirements**:
- [ ] R2.1 bump `marked@^18.0.4`
- [ ] R2.2 修復 markdown parser/renderer 呼叫方式
- [ ] R2.3 markdown 渲染 snapshot/單元測試全綠

## Error and Edge Cases
- [ ] OpenAI API response schema 變更 — 更新 zod validation（配合 zod 4）
- [ ] LangChain tool call 格式變更 — 更新 Agent tool executor
- [ ] marked async API — 確保 await 正確

## Clarification Questions
None

## References
- [LangChain JS v1 Migration](https://js.langchain.com/docs/versions/v0_2/)
- [@langchain/openai npm](https://www.npmjs.com/package/@langchain/openai)
- [marked Changelog](https://marked.js.org/)
- `packages/ai/src/`
