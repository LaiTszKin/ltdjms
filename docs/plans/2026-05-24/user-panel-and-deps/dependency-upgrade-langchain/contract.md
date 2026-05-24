# Contract: dependency-upgrade-langchain

## Version targets

| Package | Target |
| ------- | ------ |
| @langchain/core | ^1.1.48 |
| @langchain/openai | ^1.4.7 |
| marked | ^18.0.4 |

## Behavioral invariants

- AI 頻道白名單 gating 邏輯不變
- Agent tool 執行結果與錯誤處理語意不變
- Markdown → Discord embed 轉換不遺失連結/code block
