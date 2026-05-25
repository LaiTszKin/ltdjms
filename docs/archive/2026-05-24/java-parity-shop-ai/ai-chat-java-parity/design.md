# Design: ai-chat-java-parity

- Date: 2026-05-24
- Feature: ai-chat-java-parity
- Change Name: ai-chat-java-parity

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.x–R9.x |
| In-scope modules | `packages/ai` commands, routing, markdown, LangChainAIChatService (chat path) |
| External systems | OpenAI-compatible API, Discord gateway |
| Batch coordination | `../coordination.md` |

## Target vs baseline

| | Baseline | Target |
| --- | --- | --- |
| Stream markdown | Batch pipeline at end | Incremental DiscordMarkdownStreamProcessor |
| Listener tests | Missing | Java oracle port |
| Routing Source enum | Partial naming drift | Java-equivalent semantics |

## Boundaries

- Entry: `MessageCreate` with @bot mention
- Trust: guild member in allowed channel or agent-enabled channel (agent path delegates partial behavior to agent spec)
- Flow: mention → routing → (AI_CHAT) → LangChainAIChatService → markdown decorator → Discord messages

## Modules

| Module key | Responsibility | Artifacts |
| --- | --- | --- |
| `mention-listener` | Discord event UX | ai-chat-mention-listener.ts |
| `routing` | Route matrix | routing-decision.ts, channel-restriction |
| `markdown-pipeline` | Validate/fix/sanitize/paginate | markdown/** |
| `chat-service` | LLM streaming (chat path) | LangChainAIChatService.ts |

## Interaction anchors

| ID | Intent | Caller → Callee | Kind | Crossing | Failure |
| --- | --- | --- | --- | --- | --- |
| `INT-001` | Route decision | listener → routing | sync | channelId, categoryId | DENY silent |
| `INT-002` | Stream response | listener → AIChatService | network | message, handler callbacks | localized error |
| `INT-003` | Markdown decorate | AIChatService → validating decorator | sync | CONTENT chunks | fallback message |
| `INT-004` | Allowlist check | routing → channel restriction | IO | guildId, channelId | deny if empty list |

## Requirement linkage

- R1 routing before R3 listener integration
- R5–R8 markdown before R8 stream processor wiring
- R4 accumulator before R3 streaming UX

## Data & persistence

| Resource | Readers/writers | Consistency |
| --- | --- | --- |
| ai_allowed_channels/categories | channel restriction | DB authoritative |
| prompts/*.md | PromptLoader | read-only at runtime |

## Invariants

| Invariant | Breaks if | Symptoms |
| --- | --- | --- |
| REASONING bypasses markdown | Decorator processes REASONING | Wrong Discord formatting |
| marked parser retained | Switch to discord-markdown-parser | Oracle drift |

## Tradeoffs

| Decision | Rejected | Locks in |
| --- | --- | --- |
| Incremental stream processor | Keep batch-only pipeline | New processor files |
| Chat/agent split in tests | Single merged test file | Agent streaming in agent spec |

## Batch-only

Agent TOOL_INTENT path completion shared with ai-agent-java-parity; LangGraph not in this spec.
