# Checklist: dependency-upgrade-langchain

- [ ] Prerequisites: core-runtime 完成
- [ ] R1.1-R1.3 LangChain 1.x
- [ ] R2.1-R2.3 Marked 18
- [ ] `pnpm vitest run --project @ltdjms/ai`
- [ ] `make verify`

| Test ID | Requirement | Command |
| ------- | ----------- | ------- |
| UT-020 | R1.3 | `pnpm vitest run --project @ltdjms/ai` |
| UT-021 | R2.3 | `pnpm vitest run --project @ltdjms/ai -t markdown` |
