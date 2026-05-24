# Design: ai-agent-java-parity

- Date: 2026-05-24
- Feature: ai-agent-java-parity
- Change Name: ai-agent-java-parity

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.x–R7.x |
| In-scope modules | `packages/ai` tools, memory, interceptor, listeners, DI |
| External systems | Discord API, Postgres, Redis Stack, LangGraph checkpoint |
| Batch coordination | `../coordination.md` |

## Target vs baseline

| | Baseline | Target |
| --- | --- | --- |
| Tool audit | pino logs only | DB + domain events |
| Memory persist | In-memory only | LangGraph Postgres (+ Redis) |
| Tool tests | 0 | 17 Java oracle ports |
| Listeners | Missing | ToolExecution + AgentCompletion |

## Boundaries

- Entry: AGENT_ROUTE from mention listener
- Trust: administrator-only tool execution
- Flow: mention → agent service → tools (guarded) → interceptor → Discord + DB

## Modules

| Module key | Responsibility | Artifacts |
| --- | --- | --- |
| `agent-tools` | 17 Discord operations | packages/ai/src/tools/* |
| `tool-audit` | Interceptor + repository | ToolExecutionInterceptor, drizzle repo |
| `agent-memory` | Thread/tool history + checkpoint | chat-memory-provider, LangGraph saver |
| `agent-listeners` | Discord UX for tool/agent events | new listener files |

## Interaction anchors

| ID | Intent | Caller → Callee | Kind | Crossing | Failure |
| --- | --- | --- | --- | --- | --- |
| `INT-001` | Tool invoke | LangChain loop → tool class | sync | ToolExecutionContext | auth error string |
| `INT-002` | Audit write | interceptor → tool_execution_log | tx | hashed params | log error, don't block tool |
| `INT-003` | Checkpoint | agent → PostgresSaver | IO | thread_id, state | fallback in-memory warn |
| `INT-004` | Cache layer | PostgresSaver → RedisSaver | IO | checkpoint blob | Postgres-only mode |
| `INT-005` | Discord notify | listener → Discord gateway | IO | tool name, status | log warn |

## Requirement linkage

- R2 interceptor before R7 listeners (events source)
- R1 tools before R6 agent streaming (TOOL_INTENT)
- external-deps PoC before R4 checkpoint wiring
- ai-chat routing before R6 (AGENT_ROUTE entry)

## Data & persistence

| Resource | Readers/writers | Consistency |
| --- | --- | --- |
| tool_execution_log | interceptor | append-only audit |
| LangGraph checkpoints | agent memory | Postgres authoritative |
| agent:config:* Redis | agent config service | TTL 3600, invalidate on change |
| InMemoryToolCallHistory | memory provider | FIFO 50 per conversation |

## Invariants

| Invariant | Breaks if | Symptoms |
| --- | --- | --- |
| Admin-only tools | Skip guard | Security regression |
| Redacted search in memory | Store raw results | Data leak in prompts |
| Outer streaming wrapper | Full createReactAgent without PoC | REASONING/TOOL_INTENT drift |

## Tradeoffs

| Decision | Rejected | Locks in |
| --- | --- | --- |
| LangGraph checkpoint vs hand port RedisPostgresChatMemoryStore | Deprecated Java class copy | Checkpoint package API |
| zod-to-json-schema | Hand JSON Schema for 17 tools | Generator utility |

## Batch-only

Depends on external-deps-adoption and ai-chat-java-parity; shop module untouched.
