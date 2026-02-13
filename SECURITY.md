# 安全指南

本文件說明 LTDJMS Discord Bot 的安全實踐、漏洞報告流程與安全開發指引。

## 安全報告流程

### 報告安全漏洞
如果你發現安全漏洞，**請勿公開揭露**。請透過以下方式私下報告：

1. **電子郵件**：傳送詳細說明到 laitszkin1206@gmail.com
2. **GitHub 安全公告**：使用 GitHub 的 [私有漏洞報告功能](https://github.com/LaiTszKin/LTDJMS/security/advisories)

### 報告內容
請提供以下資訊以幫助我們快速理解與重現問題：
- 漏洞類型（如 SQL 注入、權限提升、資訊洩漏等）
- 受影響版本
- 重現步驟（盡可能詳細）
- 潛在影響與風險評估
- 修復建議（如有）

### 回應時程
我們致力於：
- **24 小時內**：確認收到報告
- **3 個工作日內**：初步評估與分類
- **7-14 天內**：提供修復或緩解方案
- **30 天內**：發佈安全更新

### 賞金計畫
目前未提供漏洞賞金計畫，但我們會公開致謝負責任的漏洞發現者（除非您希望匿名）。

## 安全開發實踐

### 資料保護
#### 敏感資料處理
- **Discord Token**：僅透過環境變數或安全金鑰管理系統儲存，不寫入程式碼或版本控制
- **資料庫憑證**：使用連線池與最小權限原則
- **使用者資料**：僅儲存必要資訊（Discord ID、餘額等），不收集個人識別資訊

#### 資料庫安全
- 使用參數化查詢（Prepared Statements）防止 SQL 注入
- 所有數值操作進行邊界檢查（防止整數溢出）
- 資料庫連線使用 TLS 加密（生產環境）

### 輸入驗證與消毒
#### Discord 輸入驗證
- 驗證所有 slash command 參數的型別與範圍
- 檢查使用者權限（管理員指令需驗證權限）
- 限制單次操作金額上限（目前：1,000,000）

#### 數值驗證
```java
// 範例：餘額調整驗證
public Result<BalanceAdjustmentResult, DomainError> tryAdjustBalance(
    long guildId,
    long userId,
    long delta
) {
    // 檢查 delta 是否在合理範圍內
    if (delta > MAX_ADJUSTMENT || delta < -MAX_ADJUSTMENT) {
        return Result.err(DomainError.invalidInput("調整金額超出範圍"));
    }
    // 檢查餘額不為負數
    if (currentBalance + delta < 0) {
        return Result.err(DomainError.insufficientBalance());
    }
    // ...
}
```

### 權限與存取控制
#### Discord 權限層級
1. **管理員指令**：需要 Discord 伺服器管理員權限
   - `/currency-config`：設定伺服器貨幣
   - `/admin-panel`：管理成員餘額與設定
2. **一般成員指令**：所有成員可使用
   - `/user-panel`：查看個人餘額
   - `/dice-game-1`、`/dice-game-2`：參與小遊戲

#### 資料庫權限
- 應用程式使用專用資料庫使用者，僅具備必要權限
- 遵循最小權限原則（原則上不具備 DROP、ALTER TABLE 權限）

### 依賴項安全
#### 定期更新
- 使用 Dependabot 自動檢查相依套件漏洞
- 定期執行 `mvn versions:display-dependency-updates` 檢查更新
- 優先使用有活躍維護的函式庫

#### 已知漏洞掃描
```bash
# 使用 OWASP Dependency-Check 掃描漏洞
mvn org.owasp:dependency-check-maven:check
```

### 日誌與監控
#### 安全日誌記錄
- 記錄所有管理操作（餘額調整、貨幣設定變更）
- 記錄失敗的認證嘗試
- 避免記錄敏感資料（Token、密碼）

#### 異常檢測
- 監控異常高的操作頻率（可能為濫用）
- 設定 Discord 指令處理延遲警報
- 監控資料庫連線池使用情況

## 部署安全

### 生產環境設定
#### 環境變數管理
```bash
# 生產環境應使用安全金鑰管理系統
# 例如：AWS Secrets Manager、HashiCorp Vault、Kubernetes Secrets

# 不安全的做法（避免使用）：
export DISCORD_BOT_TOKEN="plain-text-token"
```

#### 網路安全
- 使用 Docker 容器隔離應用程式
- 限制資料庫僅接受內部網路連線
- 設定適當的防火牆規則

#### 定期備份與災難恢復
- 定期備份資料庫（建議每日）
- 測試恢復流程至少每季一次
- 保留多個版本備份以應對勒索軟體攻擊

### 容器安全
#### Docker 最佳實踐
```dockerfile
# 使用非 root 使用者執行
USER 1000:1000

# 定期更新基礎映像
FROM eclipse-temurin:17-jre-alpine@sha256:...

# 移除不必要的套件
RUN apk del .build-dependencies
```

#### 映像掃描
```bash
# 使用 Trivy 掃描容器映像漏洞
trivy image your-image:tag
```

## 漏洞類別與緩解

### 常見攻擊向量與防護

| 攻擊類型 | 潛在風險 | 防護措施 |
|---------|---------|---------|
| **SQL 注入** | 資料洩漏、篡改、刪除 | 使用 jOOQ 或 Prepared Statements |
| **權限提升** | 未授權操作 | 嚴格驗證 Discord 權限與伺服器角色 |
| **整數溢出** | 餘額異常、遊戲獎勵濫用 | 所有數值操作進行邊界檢查 |
| **DDoS/濫用** | 服務中斷、資源耗盡 | 實作速率限制、監控異常請求 |
| **敏感資訊洩漏** | Token、憑證外洩 | 環境變數管理、避免日誌記錄敏感資料 |

### 速率限制
目前實作層級的緩解：
- Discord 本身有 API 速率限制
- 遊戲代幣消耗有最小間隔檢查
- 資料庫連線池限制最大連線數

未來可考慮新增：
- IP 基礎的請求限制
- 使用者基礎的操作頻率限制

## 應急回應計畫

### 漏洞確認後流程
1. **評估影響**：確認影響範圍與嚴重程度
2. **臨時緩解**：提供暫時性解決方案（如關閉受影響功能）
3. **修復開發**：優先開發安全修復
4. **測試驗證**：全面測試修復方案
5. **部署更新**：發佈安全更新
6. **事後分析**：分析根本原因，改進流程

### 溝通計畫
- **內部**：立即通知所有維護者
- **使用者**：透過 Discord 公告頻道通知受影響使用者
- **公開**：在 GitHub 發佈安全公告

## 安全資源

### 參考標準
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Discord 開發者安全性最佳實踐](https://discord.com/developers/docs/topics/security)
- [Java 安全編碼指南](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

### 工具推薦
- **靜態分析**：SonarQube、SpotBugs
- **相依套件掃描**：OWASP Dependency-Check、Snyk
- **容器掃描**：Trivy、Clair
- **滲透測試**：Burp Suite、OWASP ZAP

## 聯絡資訊

安全相關問題請聯絡：
- **主要聯絡人**：專案維護團隊
- **備用聯絡**：laitszkin1206@gmail.com

對於非安全相關問題，請使用 [GitHub Issues](https://github.com/LaiTszKin/LTDJMS/issues)。

---

**免責聲明**：本文件僅供參考，不構成任何安全保證。使用者應根據自身需求進行額外安全評估與防護。
