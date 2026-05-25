# Contract: ai-agent-java-parity

- Date: 2026-05-24
- Feature: ai-agent-java-parity
- Change Name: ai-agent-java-parity

## Scope

- **External deps in this doc:** 4

## Dependencies

### @langchain/langgraph + checkpoint packages

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://langchain-ai.github.io/langgraphjs/ | persistence, checkpointer |
| https://www.npmjs.com/package/@langchain/langgraph-checkpoint-postgres | PostgresSaver.setup |
| https://www.npmjs.com/package/@langchain/langgraph-checkpoint-redis | Redis Stack req |

**Version revision assumed:** langgraph `^1.3.2`, postgres `^1.0.1`, redis `^1.0.5`

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-AG-001` | PostgresSaver | setup() before use; reuse pg pool | Same tables as Java chat_messages without migration plan |
| `EXT-AG-002` | RedisSaver | RedisJSON + RediSearch | ioredis client works directly |

### zod-to-json-schema

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://www.npmjs.com/package/zod-to-json-schema | zod v4 support |

**Version revision assumed:** `^3.25.2`

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-AG-003` | Tool parameter schemas | Match Java @Tool parameter names | Optional params same as Java without oracle check |

### @robojs/mock

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://www.npmjs.com/package/@robojs/mock | guild/channel mocks |

**Version revision assumed:** `0.1.1-next.1` (via shop devDep or shared devDep)

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-AG-004` | Guild/channel for tool tests | Fallback gateway fake if mock fails | Live Discord API in unit tests |

#### Trace hooks

- Spec IDs: R1, R4
- Unknown / TBD: Exact LangGraph thread_id mapping to Java conversationId format
