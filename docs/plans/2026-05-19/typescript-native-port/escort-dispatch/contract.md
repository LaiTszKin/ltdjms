# Contract: Escort Dispatch

- Date: 2026-05-20
- Feature: Escort Dispatch
- Change Name: escort-dispatch

> **Purpose:** **High-level external-dependency context for `tasks.md`**: cite-backed facts, limits, failures, security—so integrations are not hallucinated. **Not** a runnable checklist; **`tasks.md` executes** wiring (files, calls, mocks, tests). Internal coupling intent stays in **`design.md`** (`INT-###`).
>
> **Anti-duplication:** Do not enumerate per-file edits, checkbox steps, or copy task ordering. **`EXT-###`** are **constraints / anchors** that task rows may cite.
>
> **Undocumented gaps:** **`TBD`** + clarification—never invent payloads, endpoints, or semantics.

## Scope

- **External deps in this doc:** `0`
- **`0`:** under **Dependencies** write **`None.`** plus one line (what "no deps" excludes for coders).

## Dependencies

**None.** escort-dispatch 模組沒有對外部 HTTP API、第三方 SDK、或外部服務的直接依賴。所有對外互動皆透過 `@ltdjms/shared` 提供的抽象層進行：

- **Discord API**：透過 `@ltdjms/shared` 的 `DiscordRuntimeGateway`（封裝 discord.js v14 Client）進行訊息發送、用戶查詢、互動回覆。dispatch 模組不直接 import discord.js。
- **PostgreSQL**：透過 `@ltdjms/shared` 的 Database 模組（封裝 Drizzle ORM + node-postgres connection pool）進行資料庫操作。dispatch 模組不直接 import `pg` 或建立連線。
- **DI Container**：透過 `@ltdjms/shared` 的 DI 容器註冊服務與 handler。dispatch 模組不直接 import `tsyringe` 或其他 DI 框架。
- **Domain Primitives**：`Result<T, E>`、`DomainError`、`Unit`、`DiscordInteraction`、`DiscordContext`、`EmbedBuilder` 等型別全部來自 `@ltdjms/shared`。
- **Logging**：透過 `@ltdjms/shared` 的 Logger 介面，不直接 import `pino`/`winston`。
- **Escort Option Catalog**：護航品類驗證依賴 `@ltdjms/shared` 提供的 `EscortOptionCatalogRepository` 介面（資料來自 `escort_option_catalog` table，該 table 的 schema 定義與 CRUD 管理屬於 shared 與 administration 模組的範圍）。

port 過程中不需參考任何外部 API 文件、不需處理 API key/secret、不需實現 HTTP client。所有實作僅基於 Java 原始碼的邏輯還原。
