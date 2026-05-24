# Preparation: java-parity-shop-ai

- Date: 2026-05-24
- Batch: java-parity-shop-ai

## **Task P1: 鎖定 batch 品質基線**

Purpose: 確保 parity batch 開始前 repo 可編譯、可測試，後續 regression 可歸因。
Scope: 根目錄驗證命令
Out of scope: 修改業務邏輯、引入外部依賴

- P1.1 [x] **執行 build 驗證** — `make build` 通過
  - Verify: exit code 0

- P1.2 [x] **記錄 test 基線** — `make test` 或 CI Node 22 job 結果存至 `fixtures/pre-parity-test-baseline.txt`
  - Verify: 檔案存在且記錄通過/失敗摘要

## **Task P2: 建立 Shop Java oracle fixtures**

Purpose: `shop-java-parity` 需要可重複執行的 structural oracle。
Scope: `shop-java-parity/fixtures/`
Out of scope: 實作 TypeScript parity 測試

- P2.1 [x] **擷取 ShopView customId 常數** — 從 `ShopView.java` 整理至 `fixtures/java-shop-custom-ids.json`
  - Verify: 含 `shop_prev_`、`shop_next_`、`shop_buy`、`shop_confirm_purchase_`、`shop_cancel_purchase`、`shop_search_modal` 等

- P2.2 [x] **擷取 ShopView embed/components oracle** — 從 `ShopViewTest.java` 整理 browse/buy/search/payment/confirm 場景至 `fixtures/java-shop-view-oracle.json`
  - Verify: 含空商店、分頁 disabled buttons、兩列 pagination layout、confirm embed

- P2.3 [x] **擷取 ShopService pagination oracle** — 從 `ShopServiceTest.java` 整理 0-based input / 1-based output、empty catalog `totalPages=0` 至 `fixtures/java-shop-service-oracle.json`
  - Verify: 含 page clamping 與 search 案例

## **Task P3: 建立 AI Chat Java oracle fixtures**

Purpose: `ai-chat-java-parity` 需要 routing、streaming、markdown 輸出 oracle。
Scope: `ai-chat-java-parity/fixtures/`
Out of scope: 實作 TypeScript parity 測試

- P3.1 [x] **擷取 routing decision matrix** — 從 `AIChatMentionRoutingDecisionTest.java` 整理 route/source 組合至 `fixtures/java-routing-oracle.json`
  - Verify: 含 AGENT_ROUTE、AI_CHAT_ROUTE、DENY 及 thread 繼承案例

- P3.2 [x] **擷取 markdown pipeline oracle** — 從 `CommonMarkValidatorTest_*`、`MarkdownAutoFixerTest`、`DiscordMarkdownPaginatorTest` 整理輸入/輸出對至 `fixtures/java-markdown-oracle.json`
  - Verify: 含 8 種 ErrorType 與 autofix 順序案例

- P3.3 [x] **擷取 streaming chunk 期望** — 從 `LangChain4jAIChatServiceTest.java`、`MessageSplitterTest.java` 整理 REASONING/CONTENT/TOOL_INTENT 格式化至 `fixtures/java-streaming-oracle.json`
  - Verify: 含 `-# ` spoiler 前綴、1980 分割邊界

## **Task P4: 建立 AI Agent Java oracle fixtures**

Purpose: `ai-agent-java-parity` 需要 17 tool schema 與 interceptor 審計 oracle。
Scope: `ai-agent-java-parity/fixtures/`
Out of scope: 實作 TypeScript parity 測試

- P4.1 [x] **擷取 17 tool 定義** — 從各 `LangChain4j*Tool.java` + `AIAgentTools.java` 整理 name/description/parameters 至 `fixtures/java-agent-tools-oracle.json`
  - Verify: 17 個 tool 名稱與 Java `@Tool` 一致

- P4.2 [x] **擷取 ToolExecutionInterceptor 審計欄位** — 從 `ToolExecutionInterceptorTest.java` 整理 redaction/hashing 期望至 `fixtures/java-tool-audit-oracle.json`
  - Verify: 含 parameter hash、不含 raw search results

- P4.3 [x] **建立 Java test → TS test 對照表** — `fixtures/java-test-mapping.md` 列出 74 個 Java AI/markdown 測試檔與計劃中的 TS 測試 ID
  - Verify: shop 4 個 handler/view/service 測試檔已映射

## **Task P5: 基礎設施前置（LangGraph Redis checkpoint）**

Purpose: `ai-agent-java-parity` 的 Redis checkpoint 需要 RedisJSON + RediSearch。
Scope: `docker-compose.yml`、`.env.example`
Out of scope: 業務邏輯

- P5.1 [x] **評估並更新 Redis image** — 若現有 Redis 不支援 RedisJSON，改為 Redis Stack 或 Redis 8+ image
  - Verify: `docker compose config` 顯示更新後 image；本地 `redis-cli MODULE LIST` 含 ReJSON

- P5.2 [x] **文件化 checkpoint 環境變數** — 在 `.env.example` 新增 LangGraph checkpoint 相關欄位（若需）
  - Verify: `make update-env` 不報錯

## Validation

- Verification required:
  - `make build` 通過
  - Shop / AI Chat / AI Agent oracle fixtures 完整
  - Java test mapping 表存在
- Expected results: 所有 member spec 可在一致 Node 22 環境依 coordination merge order 實作
- Regression risks covered: 環境漂移、parity 驗收標準模糊、Redis checkpoint 本機不可用
