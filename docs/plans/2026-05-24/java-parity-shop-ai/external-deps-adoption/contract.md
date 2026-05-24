# Contract: external-deps-adoption

- Date: 2026-05-24
- Feature: external-deps-adoption
- Change Name: external-deps-adoption

## Scope

- **External deps in this doc:** 5

## Dependencies

### @langchain/langgraph

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| --- | --- |
| https://langchain-ai.github.io/langgraphjs/ | checkpoints, persistence |
| https://www.npmjs.com/package/@langchain/langgraph | peerDependencies |

**Version revision assumed:** `^1.3.2`

#### Facts we rely on

| Fact / capability needed | Doc location |
| --- | --- |
| Peer: `@langchain/core ^1.1.44`, `zod ^3.25 \|\| ^4.2` | npm peerDependencies |
| Checkpoint savers composable with state graph | LangGraph persistence docs |

#### Limits & failures

| Category | Doc fact | Meaning while executing tasks |
| --- | --- | --- |
| Agent loop vs streaming | Graph invokes model internally | PoC must confirm outer streaming wrapper still feasible |

#### Integration anchors (`EXT-001`)

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-001` | `@langchain/langgraph` StateGraph + checkpointer | Use official saver packages | createReactAgent replaces all custom streaming without PoC |

### @langchain/langgraph-checkpoint-postgres

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| --- | --- |
| https://www.npmjs.com/package/@langchain/langgraph-checkpoint-postgres | setup, PostgresSaver |

**Version revision assumed:** `^1.0.1`

#### Facts we rely on

| Fact / capability needed | Doc location |
| --- | --- |
| Peer: `pg ^8.12` | npm peerDependencies |
| Creates checkpoint tables on setup | package README |

#### Integration anchors (`EXT-002`)

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-002` | PostgresSaver | Reuse existing pg pool config | Separate DB instance required |

### @langchain/langgraph-checkpoint-redis

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| --- | --- |
| https://www.npmjs.com/package/@langchain/langgraph-checkpoint-redis | Redis Stack requirements |

**Version revision assumed:** `^1.0.5`

#### Facts we rely on

| Fact / capability needed | Doc location |
| --- | --- |
| Requires RedisJSON + RediSearch | package README |
| Uses `redis` npm client (not ioredis) | implementation notes |

#### Integration anchors (`EXT-003`)

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-003` | RedisSaver | Redis Stack in docker-compose | Plain redis:7 works without modules |

### zod-to-json-schema

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| --- | --- |
| https://www.npmjs.com/package/zod-to-json-schema | API, zod v4 |

**Version revision assumed:** `^3.25.2`

#### Integration anchors (`EXT-004`)

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-004` | `zodToJsonSchema(zodSchema)` | Compatible with zod 4 | Hand-maintained JSON Schema still required for all tools |

### @robojs/mock

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| --- | --- |
| https://www.npmjs.com/package/@robojs/mock | usage (pre-release) |

**Version revision assumed:** `0.1.1-next.1`

#### Integration anchors (`EXT-005`)

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-005` | Discord gateway mock | Pre-release instability → document fallback | Production runtime dependency |

#### Trace hooks

- Spec IDs covered: R3–R5
- Unknown / TBD: @robojs/mock API stability for modal interactions
- **PoC fallback (2026-05-24):** LTDJMS is not a Robo.js project. `sessionManager.create()` validates mock infrastructure; shop-java-parity UT-306–308 should use `@ltdjms/shared` `MockDiscordInteraction` for slash/button handler tests until a Robo entrypoint exists or @robojs/mock adds standalone discord.js harness support.

### supertest

#### Evidence

| Primary docs URL(s) | Sections / anchors used |
| --- | --- |
| https://github.com/ladjs/supertest | Express 5 compatibility |

**Version revision assumed:** `^7.2.2`

#### Integration anchors (`EXT-006`)

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-006` | HTTP POST to Express app | Works with express@5 | Replaces all ECPay crypto tests |
