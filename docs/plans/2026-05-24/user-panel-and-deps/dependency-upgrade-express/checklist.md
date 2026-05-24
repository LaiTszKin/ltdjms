# Checklist: dependency-upgrade-express

- [ ] Prerequisites: core-runtime 完成
- [ ] R1.1-R1.3 Express 5
- [ ] R2.1-R2.2 Callback 契約
- [ ] `pnpm vitest run --project @ltdjms/shop`
- [ ] `make verify`

| Test ID | Requirement | Command |
| ------- | ----------- | ------- |
| IT-030 | R2.1 | `pnpm vitest run --project @ltdjms/shop -t callback` |
| E2E-030 | R2.2 | `RUN_ECPAY_E2E=true make test` |
