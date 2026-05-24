# Checklist: ai-chat-java-parity

- Date: 2026-05-24
- Feature: ai-chat-java-parity

## Pre-implementation
- [ ] preparation P3 AI chat fixtures
- [ ] external-deps-adoption JSON snapshot helper

## Parity verification
- [ ] R1.1-R1.3 routing matrix 對齊 Java
- [ ] R2.1-R2.2 channel restriction 對齊 Java
- [ ] R3.1-R3.5 mention listener UX 對齊 Java
- [ ] R4.1-R4.2 chunk accumulator/splitter 對齊 Java
- [ ] R5.1-R5.2 markdown validator 對齊 Java
- [ ] R6.1-R6.2 autofixer 順序對齊 Java
- [ ] R7.1-R7.2 sanitizer/paginator 對齊 Java
- [ ] R8.1-R8.2 stream markdown processor 對齊 Java
- [ ] R9.1-R9.2 config/prompt loader 對齊 Java

## Automated tests
- [ ] UT-401 routing-decision
- [ ] UT-402 channel-restriction
- [ ] UT-403 message-chunk-accumulator + splitter
- [ ] UT-404 ai-chat-mention-listener
- [ ] UT-405 markdown-validator (expanded)
- [ ] UT-406 markdown-autofixer (expanded)
- [ ] UT-407 markdown stream processor
- [ ] UT-408 markdown-paginator (expanded)
- [ ] UT-409 prompt-loader + ai-service-config
- [ ] UT-410 LangChainAIChatService chat path

## Manual smoke (Discord)
- [ ] 白名單頻道 @mention 串流回應
- [ ] showReasoning on/off 行為
- [ ] 含表格/非法 Markdown 的自動修正輸出

## Sign-off
- [ ] `make verify`
- [ ] architecture diff validate
