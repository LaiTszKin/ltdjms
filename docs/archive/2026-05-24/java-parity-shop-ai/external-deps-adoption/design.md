# Design: external-deps-adoption

- Date: 2026-05-24
- Feature: external-deps-adoption
- Change Name: external-deps-adoption

> High-level context for dependency adoption and PoC validation—not implementation steps.

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.x–R6.x |
| In-scope modules (≤3) | `packages/ai`, `packages/shop`, `packages/shared` (test helper) |
| External systems touched | LangGraph checkpoint (Postgres, Redis), @robojs/mock, supertest |
| Batch coordination | `../coordination.md` |

## Target vs baseline

| | Baseline (today) | Target (after this change) |
| --- | --- | --- |
| AI deps | `@langchain/core`, `@langchain/openai` only | + LangGraph + checkpoint + zod-to-json-schema |
| Shop test deps | vitest, hand mocks | + @robojs/mock, supertest |
| Parity testing | user-panel pattern ad hoc | Shared JSON snapshot helper |

## Boundaries

- Entry surface(s): pnpm install, vitest PoC tests
- Trust boundary crossed: Postgres/Redis (checkpoint PoC only)
- Outside → inside: Test runner → checkpoint adapters → existing pg/redis clients

## Modules (nouns only)

| Module key | Responsibility | Owned artifacts |
| --- | --- | --- |
| `ai-deps` | LangGraph checkpoint PoC | PoC test files |
| `shop-test-harness` | Mock/supertest PoC | PoC test files |
| `parity-test-kit` | JSON snapshot helpers | shared test utilities |

## Interaction anchors (`INT-###`)

| ID | Intent | Caller → Callee | Coupling kind | Information crossing | Failure expectation |
| --- | --- | --- | --- | --- | --- |
| `INT-001` | Postgres checkpoint roundtrip | PoC test → PostgresSaver | sync IO | thread_id, checkpoint blob | test fail → ai-agent uses Drizzle-only fallback |
| `INT-002` | Redis checkpoint roundtrip | PoC test → RedisSaver | sync IO | thread_id, checkpoint blob | test fail → Postgres-only documented |
| `INT-003` | Zod → LangChain tool schema | PoC test → zod-to-json-schema | pure | Zod schema → JSON Schema | invalid schema → test fail before agent spec |
| `INT-004` | Discord interaction mock | PoC test → @robojs/mock | test double | slash/button payload | unstable → hand mock fallback in shop spec |

## Requirement linkage

### R3 LangGraph PoC
- Anchor order: `INT-001` → `INT-002` → design conclusion
- Postgres must work before Redis optional path
- PoC conclusion gates ai-agent memory strategy

## Data & persistence

| Resource | Readers/writers | Consistency |
| --- | --- | --- |
| LangGraph checkpoint tables | PoC only | idempotent write/read test |
| Redis checkpoint keys | PoC only | TTL per LangGraph docs |

## Invariants

| Invariant | What breaks it | Symptoms |
| --- | --- | --- |
| No business logic change | Editing shop/ai handlers in this spec | Parity diffs mixed with deps |
| Peer compat with LC 1.x | Wrong langgraph version | build/test fail |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in |
| --- | --- | --- |
| PoC before full agent rewrite | Direct createReactAgent migration | Hand loop retained until PoC passes |
| @robojs/mock pre-release | Only hand mocks | Fallback path required in contract |

## Batch-only

Shop/AI parity behavior owned by sibling specs; this spec only delivers deps + PoC + test kit.

## PoC conclusions (2026-05-24)

### LangGraph checkpoint (R3)

| Backend | Result | Notes |
| --- | --- | --- |
| **Postgres** (`PostgresSaver` + existing `pg` pool) | **Adopt** | `setup()` creates checkpoint tables; write/read roundtrip verified against testcontainers Postgres. |
| **Redis** (`RedisSaver` + Redis Stack) | **Optional** | Works when `REDIS_URI` points at Redis Stack (RedisJSON + RediSearch). If unavailable, ai-agent spec uses **Postgres-only** checkpoint — no blocker. |

### Streaming outer control (R3.3)

LangGraph `StateGraph` / `createReactAgent` invokes the chat model **inside** the graph. That means token streaming is owned by the graph runtime, not the current hand-written loop in `LangChainAIChatService`.

**Decision for ai-agent-java-parity:** Keep **REASONING** and **TOOL_INTENT** formatting in an **outer wrapper** around graph invocation (same pattern as Java `ReasoningMessageTracker` + mention listener). Do not rely on graph-internal streaming for Discord spoiler prefixes (`-# `) or tool-intent UX. The graph handles tool-call state; the outer layer maps chunks to Discord messages.

### zod-to-json-schema (R4)

Tool parameter schemas use **Zod 4** (`z.object(...)`). `z.toJSONSchema()` is the primary conversion path for LangChain `DynamicStructuredTool` binding — verified for `create_channel` and `list_channels`. The `zod-to-json-schema` package remains installed for **zod/v3** fixture schemas (`import { z } from 'zod/v3'`) when importing Java oracle definitions; it does not emit properties from Zod 4 native schemas (empty `{}` definitions). ai-agent spec should use `z.toJSONSchema()` for all 17 tools.

### @robojs/mock (R5)

`sessionManager.create()` works for isolated guild/channel fixtures. **Full `/shop` reply capture requires a Robo.js bot entrypoint** — LTDJMS is not Robo-based. **Fallback:** use `@ltdjms/shared` `MockDiscordInteraction` for shop handler parity (documented in contract.md EXT-005).

### supertest (R6)

Express 5 callback route smoke tests pass with `supertest@^7.2.2` + `@types/supertest@^6`. PoC mirrors `EcpayCallbackHttpServer` POST handler without binding a real port; coexists with existing `payment-callback.test.ts`.
