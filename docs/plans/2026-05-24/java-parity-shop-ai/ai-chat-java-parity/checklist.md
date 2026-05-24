# Checklist: ai-chat-java-parity

- Date: 2026-05-24
- Feature: ai-chat-java-parity

## Pre-implementation
- [x] preparation P3 AI chat fixtures
- [x] external-deps-adoption JSON snapshot helper

## Parity verification
- [x] R1.1-R1.3 routing matrix 對齊 Java
- [x] R2.1-R2.2 channel restriction 對齊 Java
- [x] R3.1-R3.5 mention listener UX 對齊 Java
- [x] R4.1-R4.2 chunk accumulator/splitter 對齊 Java
- [x] R5.1-R5.2 markdown validator 對齊 Java
- [x] R6.1-R6.2 autofixer 順序對齊 Java
- [x] R7.1-R7.2 sanitizer/paginator 對齊 Java
- [x] R8.1-R8.2 stream markdown processor 對齊 Java
- [x] R9.1-R9.2 config/prompt loader 對齊 Java

## Automated tests
- [x] UT-401 routing-decision
- [x] UT-402 channel-restriction
- [x] UT-403 message-chunk-accumulator + splitter
- [x] UT-404 ai-chat-mention-listener
- [x] UT-405 markdown-validator (expanded)
- [x] UT-406 markdown-autofixer (expanded)
- [x] UT-407 markdown stream processor
- [x] UT-408 markdown-paginator (expanded)
- [x] UT-409 prompt-loader + ai-service-config
- [x] UT-410 LangChainAIChatService chat path

## Manual smoke (Discord)
- [x] 白名單頻道 @mention 串流回應
- [x] showReasoning on/off 行為
- [x] 含表格/非法 Markdown 的自動修正輸出

## Sign-off
- [x] `make verify`
- [x] architecture diff validate
