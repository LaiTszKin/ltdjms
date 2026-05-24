# Design: dependency-upgrade-express

- Date: 2026-05-24
- Feature: dependency-upgrade-express

## Traceability

| | |
| --- | --- |
| Requirement IDs | R1.1-R2.2 |
| In-scope modules | `packages/shop/` HTTP server |
| Prerequisites | `dependency-upgrade-core-runtime` |

## Target vs baseline

| Package | Baseline | Target |
| ------- | -------- | ------ |
| express | ^4.21.0 | ^5.2.1 |
| @types/express | ^4.17.21 | ^5.0.6 |

## Key migration points

1. Route path matching changes — audit all `app.get/post/use` patterns
2. `req.query` parsing — verify ECPay callback query params
3. Body parser middleware — confirm raw/json body for signature verification
4. Error handling middleware — 4-arg handler signature unchanged but async errors auto-forwarded

## Test strategy

| Layer | Cases |
| ----- | ----- |
| Unit | route handler mock request/response |
| Integration | supertest against callback app |
| E2E | ECPay stage callback (optional env) |
