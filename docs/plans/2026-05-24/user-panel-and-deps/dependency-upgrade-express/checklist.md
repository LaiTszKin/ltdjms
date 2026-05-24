# Checklist: dependency-upgrade-express

- [x] Prerequisites: core-runtime 完成
- [x] R1.1-R1.3 Express 5
- [x] R2.1-R2.2 Callback 契約
- [x] `pnpm vitest run --project @ltdjms/shop`
- [x] `make verify` — N/A（Makefile 無 TS `verify` target）；`make build` 通過

| Test ID | Requirement | Command |
| ------- | ----------- | ------- |
| IT-030 | R2.1 | `pnpm vitest run --project @ltdjms/shop -t callback` |
| E2E-030 | R2.2 | `RUN_ECPAY_E2E=true make test` — N/A（Docker 不可用） |
