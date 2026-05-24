# Checklist: external-deps-adoption

- Date: 2026-05-24
- Feature: external-deps-adoption

## Pre-implementation
- [x] preparation P1 品質基線完成

## Dependency installation
- [x] R1.1 LangGraph + zod-to-json-schema 已安裝於 @ltdjms/ai
- [x] R1.2 @robojs/mock + supertest 已安裝於 @ltdjms/shop devDeps
- [x] R1.3 pnpm install 無 peer 衝突；make build 通過

## PoC verification
- [x] R2.1-R2.2 JSON snapshot helper 可用
- [x] R3.1 Postgres checkpoint PoC 通過
- [x] R3.2 Redis checkpoint PoC 通過或 fallback 已文件化
- [x] R3.3 streaming 策略結論已寫入 design.md
- [x] R4.1-R4.2 zod-to-json-schema PoC 通過
- [x] R5.1 @robojs/mock smoke 通過或 fallback 已文件化
- [x] R6.1 supertest callback smoke 通過

## Automated tests
- [x] UT-ED-001 json-snapshot helper
- [x] POC-ED-001 langgraph postgres checkpoint
- [x] POC-ED-002 langgraph redis checkpoint (or N/A documented)
- [x] POC-ED-003 zod tool schema
- [x] POC-ED-004 robojs mock smoke
- [x] POC-ED-005 supertest callback smoke

## Sign-off
- [x] `make build` 通過
- [x] architecture diff validate
- [x] member specs (shop/ai-chat/ai-agent) 可開始實作
