# Contract: ai-chat-java-parity

- Date: 2026-05-24
- Feature: ai-chat-java-parity
- Change Name: ai-chat-java-parity

## Scope

- **External deps in this doc:** 2

## Dependencies

### marked (existing)

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://marked.js.org | lexer API |

**Version revision assumed:** `^18.0.4`

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-AIC-001` | `marked.lexer()` | CommonMarkValidator uses marked AST | Replace with remark/discord-markdown-parser |

### @langchain/openai (existing)

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://js.langchain.com/docs/integrations/chat/openai | ChatOpenAI streaming |

**Version revision assumed:** `^1.4.7`

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-AIC-002` | ChatOpenAI stream | Manual iteration for REASONING chunks | AiServices high-level only without stream control |

#### Trace hooks

- Spec IDs: R3, R9, R8
- Unknown / TBD: None
