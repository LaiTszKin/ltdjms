# Contract: package-extraction

- Date: 2026-05-22
- Feature: package-extraction
- Change Name: package-extraction

## Scope

- **External deps in this doc:** 0
- **0:** 無外部 API / SDK 依賴。所有變更為 monorepo 內部的 package 拆分、檔案移動和 import 路徑更新。

## Dependencies

**None.** 無網路 SDK 或外部 API 依賴。僅涉及以下內部 package 之間的依賴重組：

- `@ltdjms/games` (new) → `@ltdjms/economy` (currency services + common base classes)
- `@ltdjms/games` (new) → `@ltdjms/shared` (DI, cache, events, Discord abstraction)
- `@ltdjms/admin` → `@ltdjms/games` (game services, handlers, facades)
- `@ltdjms/admin` → `@ltdjms/economy` (currency services — unchanged)

外部依賴（`drizzle-orm`、`pg`、`tsyringe`）版本不變，僅在新 package 中重複宣告。

- Spec IDs covered: R1.1-R10.6
- Related `design.md` module keys: `games/*`, `economy/currency`, `economy/common`
