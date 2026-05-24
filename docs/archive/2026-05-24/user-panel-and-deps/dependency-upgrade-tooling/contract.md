# Contract: dependency-upgrade-tooling

- Date: 2026-05-24
- Feature: dependency-upgrade-tooling

## External dependency contracts

| Package | From | To | Breaking changes handled |
| ------- | ---- | -- | ------------------------ |
| typescript | ^5.5.0 | ^6.0.3 | stricter type checks — fix at source |
| vitest | ^3.0.0 | ^4.1.7 | config API — update vitest.config.ts |
| eslint | ^9.0.0 | ^10.4.0 | flat config plugin compatibility |
| typescript-eslint | ^8.0.0 | ^8.59.4 | rule updates — fix violations |
| prettier | ^3.0.0 | ^3.8.3 | none expected |
| tsx | ^4.0.0 | ^4.22.3 | none expected |
| @types/node | ^20.0.0 | ^22.0.0 | Node 22 API types |

## Internal contracts (unchanged)

- `make build`、`make test`、`make lint`、`make verify` 命令介面不變
- 各 package public API export 不變（本 spec 不修改 runtime 程式碼語意）

## Rollback

- Revert `package.json` + lockfile + config changes
- Re-run `make verify` on previous commit
