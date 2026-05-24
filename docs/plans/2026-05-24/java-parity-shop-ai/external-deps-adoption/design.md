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
