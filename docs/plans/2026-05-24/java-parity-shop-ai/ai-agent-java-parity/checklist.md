# Checklist: ai-agent-java-parity

- Date: 2026-05-24
- Feature: ai-agent-java-parity

## Pre-implementation
- [x] preparation P4 agent fixtures + P5 Redis Stack
- [x] external-deps-adoption LangGraph PoC 通過
- [x] ai-chat-java-parity 完成（routing + markdown + listener 基礎）

## Parity verification
- [x] R1.1-R1.4 17 tools + schema + tests 對齊 Java
- [x] R2.1-R2.3 tool_execution_log 審計對齊 Java
- [x] R3.1-R3.3 對話記憶 + redaction 對齊 Java
- [x] R4.1-R4.4 LangGraph checkpoint 持久化（Postgres 必須；Redis 若可用）
- [x] R5.1-R5.2 Agent 頻道配置 + cache 對齊 Java
- [x] R6.1-R6.3 Agent 串流 UX 對齊 Java
- [x] R7.1-R7.3 Tool/Agent Discord listeners 對齊 Java

## Automated tests
- [x] UT-501 tool schema oracle (17 tools)
- [x] UT-502–518 individual tool tests
- [x] UT-519 authorization guard
- [x] UT-520 ToolExecutionInterceptor
- [x] INT-520 tool execution log integration
- [x] UT-521 chat memory provider
- [x] UT-522 tool call history redaction
- [x] INT-521 LangGraph checkpoint integration
- [x] UT-523 agent channel config
- [x] UT-524 agent streaming path
- [x] UT-525 ToolExecutionListener
- [x] UT-526 AgentCompletionListener

## Manual smoke (Discord)
- [x] Agent 模式頻道：建立頻道工具 + Discord 通知
- [x] 非管理員觸發工具 — 權限錯誤訊息
- [x] 重啟 bot 後 Thread 對話記憶保留（Postgres checkpoint）

## Sign-off
- [x] `make verify`
- [x] architecture diff validate
- [x] batch integration checkpoint（coordination.md）
