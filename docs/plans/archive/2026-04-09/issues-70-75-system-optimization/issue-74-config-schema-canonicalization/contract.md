# Contract: Issue 74 設定 Schema 正規化

- Date: 2026-04-09
- Feature: Issue 74 設定 Schema 正規化
- Change Name: issue-74-config-schema-canonicalization

## Purpose
本變更的設計界線由 Lightbend Config / Typesafe Config 的標準載入行為決定：只要 repository 中同時存在多個 packaged config resource，`ConfigFactory.load()` 就可能把它們合併成最終 runtime config。因此若不先明確指定 canonical defaults owner，就會持續出現 schema 漂移。

## Usage Rule
- 任何關於 `application.conf`、`application.properties`、fallback 與 HOCON key namespace 的判斷，都必須以 Lightbend Config 官方文件為準。
- 本 spec 不改變環境變數 contract；僅校正 packaged resource 與文件對 runtime schema 的描述。

## Dependency Records

### Dependency 1: Lightbend Config / Typesafe Config Load Behavior
- Type: `library`
- Version / Scope: `Not fixed`
- Official Source: `https://github.com/lightbend/config#standard-behavior`
- Why It Matters: `EnvironmentConfig` 依賴 `ConfigFactory.load()` 合併 packaged config 與 defaults；若 repository 同時維護多個 drift schema，runtime 與文件就會分裂。
- Invocation Surface:
  - Entry points: `ConfigFactory.load()`, packaged `application.conf`, `application.properties`
  - Call pattern: `in-process library call`
  - Required inputs: classpath resource files、fallback defaults、override env mapping
  - Expected outputs: runtime `Config` 物件與最終 key lookup 行為
- Constraints:
  - Supported behavior: classpath 上的標準 resource 會被載入並參與最終 config 組裝
  - Limits: library 不會替應用程式判斷哪個檔案才是你的 canonical schema
  - Compatibility: key namespace 與 fallback chain 必須由應用層自行保持一致
  - Security / access: 不適用；此變更以一致性與可維運性為主
- Failure Contract:
  - Error modes: packaged defaults drift、文件與 runtime 不一致、required key 缺失
  - Caller obligations: 明確指定 canonical schema owner，並用測試防止資源檔 drift
  - Forbidden assumptions: 不可假設 `application.conf` 與 `application.properties` 同時存在就自然會保持一致
- Verification Plan:
  - Spec mapping: `R1.x-R3.x`
  - Design mapping: `Current Architecture`, `Proposed Architecture`
  - Planned coverage: `UT-74-01`, `UT-74-02`, `UT-74-03`, `DOC-74-01`
  - Evidence notes: 官方文件指出標準 resource loading 與 fallback 是 library 行為，schema 一致性需由應用層負責；本次實作改為只載入 `application.properties` 並以 regression tests 鎖住 `application.conf` 不再承載 live schema
