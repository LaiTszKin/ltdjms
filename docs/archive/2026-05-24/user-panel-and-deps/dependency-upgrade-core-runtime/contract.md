# Contract: dependency-upgrade-core-runtime

- Date: 2026-05-24
- Feature: dependency-upgrade-core-runtime

## Version targets

| Package | Target |
| ------- | ------ |
| zod | ^4.4.3 |
| drizzle-orm | ^0.45.2 |
| drizzle-kit | ^0.31.10 |
| pg | ^8.21.0 |
| pino | ^10.3.1 |
| discord.js | ^14.26.4 |
| ioredis | ^5.10.1 |
| tsyringe | ^4.10.0 |
| reflect-metadata | ^0.2.2 |

## Behavioral invariants (must not change)

- Zod schema 驗證結果（accept/reject）與錯誤分類語意不變
- Drizzle query 結果與 transaction 邊界不變
- Discord interaction reply/defer/edit 行為不變
- Log level 與結構化欄位不變

## Prerequisites

- `dependency-upgrade-tooling` merged
