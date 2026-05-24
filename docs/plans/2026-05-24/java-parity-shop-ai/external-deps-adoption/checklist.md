# Checklist: external-deps-adoption

- Date: 2026-05-24
- Feature: external-deps-adoption

## Pre-implementation
- [ ] preparation P1 品質基線完成

## Dependency installation
- [ ] R1.1 LangGraph + zod-to-json-schema 已安裝於 @ltdjms/ai
- [ ] R1.2 @robojs/mock + supertest 已安裝於 @ltdjms/shop devDeps
- [ ] R1.3 pnpm install 無 peer 衝突；make build 通過

## PoC verification
- [ ] R2.1-R2.2 JSON snapshot helper 可用
- [ ] R3.1 Postgres checkpoint PoC 通過
- [ ] R3.2 Redis checkpoint PoC 通過或 fallback 已文件化
- [ ] R3.3 streaming 策略結論已寫入 design.md
- [ ] R4.1-R4.2 zod-to-json-schema PoC 通過
- [ ] R5.1 @robojs/mock smoke 通過或 fallback 已文件化
- [ ] R6.1 supertest callback smoke 通過

## Automated tests
- [ ] UT-ED-001 json-snapshot helper
- [ ] POC-ED-001 langgraph postgres checkpoint
- [ ] POC-ED-002 langgraph redis checkpoint (or N/A documented)
- [ ] POC-ED-003 zod tool schema
- [ ] POC-ED-004 robojs mock smoke
- [ ] POC-ED-005 supertest callback smoke

## Sign-off
- [ ] `make build` 通過
- [ ] architecture diff validate
- [ ] member specs (shop/ai-chat/ai-agent) 可開始實作
