# Design: Issue 70 履約 URL DNS Rebinding 強化

- Date: 2026-04-09
- Feature: Issue 70 履約 URL DNS Rebinding 強化
- Change Name: issue-70-fulfillment-url-ssrf-hardening

## Design Goal
把商品 fulfillment outbound target 的安全不變式寫成更清楚的內部邊界：validation 決定唯一可連線目的地，transport 只負責使用該結果送出請求，不再保留任何會回到 hostname-based 解析的隱性捷徑。

## Change Summary
- Requested change: 依 issue #70 將 fulfillment URL 的 DNS rebinding 防線做成可驗證、不可輕易回歸的設計。
- Existing baseline: 現行程式已透過 `ResolvedTarget` + `PinnedHttpsFulfillmentTransport` 連到已解析 IP，代表 issue 描述中的核心風險已被部分緩解，但該安全意圖仍主要存在於實作細節與有限測試。
- Proposed design delta: 不重做 fulfillment pipeline，而是把 target snapshot 語意收斂進 `ResolvedTarget` helper／accessor，固定 non-public block 與 no-redirect 行為，並補齊 regression coverage。

## Scope Mapping
- Spec requirements covered: `R1.1-R1.3`, `R2.1-R2.3`
- Affected modules:
  - `src/main/java/ltdjms/discord/shop/services/ProductFulfillmentApiService.java`
  - `src/test/java/ltdjms/discord/shop/services/ProductFulfillmentApiServiceTest.java`
- External contracts involved: `None`
- Coordination reference: `../coordination.md`

## Current Architecture
`notifyFulfillment()` 會先驗證 `backendApiUrl`，再建立 `ResolvedTarget`，之後交由內部 `PinnedHttpsFulfillmentTransport` 以 `resolvedAddress` 建立 socket，並以原始 host 設定 Host header 與 TLS SNI。  
這個結構本身已朝正確方向前進，但安全需求仍隱含在實作裡：
- `ResolvedTarget` 的存在目的沒有被明確當成安全邊界描述。
- transport 不 follow redirect 的安全性是副產品，而非被測試固定的規則。
- DNS rebinding / mixed-address / transport failure 等案例的 regression coverage 不足以防止未來改回 hostname-based client。

## Proposed Architecture
保留單一 service + 內部 transport 的結構，但將其責任明確化：
- validation phase：唯一負責 URL 正規化、public-address 驗證、`ResolvedTarget` 建立。
- send phase：唯一負責以 `ResolvedTarget.resolvedAddress` 建立連線，禁止重新查 DNS。
- error mapping phase：唯一負責將 non-2xx / TLS / socket failure 映射成 `DomainError`，不製造 partial success。

## Component Changes

### Component 1: `ProductFulfillmentApiService`
- Responsibility: 驗證 backend URL、建立 immutable target snapshot、建構 payload 與簽章標頭。
- Inputs: `FulfillmentRequest`、商品 backend URL、escort pricing 結果。
- Outputs: `Result<Unit, DomainError>`、`ResolvedTarget`、已簽章的 request headers。
- Dependencies: `EscortOptionPricingService`、內部 `FulfillmentTransport`。
- Invariants:
  - 只接受 public HTTPS target。
  - `ResolvedTarget` 一旦建立後就是唯一 outbound destination。
  - fulfillment payload 與簽章格式維持現況。

### Component 2: `PinnedHttpsFulfillmentTransport`
- Responsibility: 以已驗證 IP 進行 TCP/TLS 連線並發送 HTTP request。
- Inputs: `ResolvedTarget`、JSON body、headers。
- Outputs: `TransportResponse` 或 transport-level exception。
- Dependencies: JDK socket / SSL primitives。
- Invariants:
  - 只能使用 `resolvedAddress` 連線。
  - 原始 hostname 僅供 Host header 與 SNI。
  - 不自動跟隨 redirect，不自行重解 host。

## Sequence / Control Flow
1. `notifyFulfillment()` 解析並驗證商品 `backendApiUrl`，建立 `ResolvedTarget`。
2. service 產生 fulfillment payload 與簽章標頭，將 `ResolvedTarget` 交給 transport。
3. transport 直接連至 `resolvedAddress`，回傳 `TransportResponse`；service 再依結果轉成成功或 `DomainError`。

## Data / State Impact
- Created or updated data: `None`（不新增 schema、cache 或 config）
- Consistency rules:
  - 任何 validation / transport 失敗都不得產生 fulfillment success side effect。
  - 既有 order / payment idempotency 不受影響。
- Migration / rollout needs: `None`

## Risk and Tradeoffs
- Key risks:
  - 過度收斂可能誤傷少數依賴 redirect 的 backend endpoint。
  - 將安全意圖只寫在註解而非測試，仍容易回歸。
- Rejected alternatives:
  - 改回標準 `HttpClient` + hostname URL：會重新引入 validation/use split 風險。
  - 支援自動 follow redirect：會把 target trust boundary 擴散到第二個 URL。
- Operational constraints:
  - 需維持現有同步 fulfillment 行為與超時配置。
  - 不可改變 backend 端目前依賴的 payload / header contract。

## Validation Plan
- Tests:
  - Unit / regression：驗證 `ResolvedTarget` 內容、`socketAddress()` / `tlsServerName()` 與 transport 消費方式
  - Adversarial：non-public address、mixed public/private address、unknown host、non-2xx、transport failure
- Contract checks: `contract.md` 無外部契約；僅檢查既有 webhook payload / signature header 不變。
- Rollback / fallback: 若整理後出現連線相容性問題，可回退至目前已驗證的 pinned transport 實作，但不得回退到 hostname re-resolution 模式。

## Open Questions
None
