# Contract: Admin Panel PBT

- Date: 2026-05-22
- Feature: Admin Panel PBT
- Change Name: admin-pbt

## Scope

- **External deps in this doc:** 0
- **0:** 無網路 SDK/API 依賴；僅依賴 test-infra (內部) + 各業務 module (economy, shop, dispatch, ai)

## Dependencies

**None.** Admin PBT 僅使用 test-infra 提供的 DI container、seed factory、arbitrary、assertion helper，以及各業務 module 的 facade。無額外外部依賴。
