# Design: dependency-upgrade-langchain

- Date: 2026-05-24
- Feature: dependency-upgrade-langchain

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.1-R2.3 |
| In-scope modules | `packages/ai/` |
| Prerequisites | `dependency-upgrade-core-runtime`（zod 4 已就緒） |

## Target vs baseline

| Package | Baseline | Target |
| ------- | -------- | ------ |
| @langchain/core | ^0.3.0 | ^1.1.48 |
| @langchain/openai | ^0.4.0 | ^1.4.7 |
| marked | ^15.0.0 | ^18.0.4 |

## Modules

| Module | Responsibility |
| ------ | -------------- |
| `ai/routing` | 頻道白名單、路由決策 |
| `ai/agent` | Agent tool 執行 |
| `ai/markdown` | marked 渲染管線 |

## Test strategy

| Layer | Cases |
| ----- | ----- |
| Unit | markdown renderer output |
| Unit | tool schema binding |
| Integration | mock OpenAI response → agent reply |
