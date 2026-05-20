# Contract: Guild Economy

- Date: 2026-05-20
- Feature: Guild Economy
- Change Name: guild-economy

## Scope

- **External deps in this doc:** 0
- 無對外 API 呼叫——所有依賴（Database、Redis、Discord、Event）皆透過 `@ltdjms/shared` 的介面

## Dependencies

**None.** 本模組不直接依賴任何外部 SDK 或 API。所有基礎設施（PostgreSQL、Redis、discord.js）透過 `@ltdjms/shared` package 的抽象層存取。
