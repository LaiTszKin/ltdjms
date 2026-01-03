# Markdown 自動修復功能實施計劃

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目標:** 增強現有的 Markdown 驗證器，新增自動修復常見格式錯誤的功能，減少對 LLM 重試的依賴。

**架構:** 在現有的 `MarkdownValidator` 和 `MarkdownErrorFormatter` 基礎上，新增 `MarkdownAutoFixer` 介面和 `RegexBasedAutoFixer` 實作類別。透過修飾器模式將自動修復整合到驗證流程中，使其在驗證失敗時先嘗試自動修復，再決定是否需要觸發 LLM 重試。

**技術堆疊:** Java 17 | CommonMark Java 0.22.0 | JUnit 5 | Mockito | Dagger 2.52

---

## 前置知識

### 現有架構概覽

專案已有一個完整的 Markdown 驗證系統：

- **`MarkdownValidator`** (介面): 位於 `src/main/java/ltdjms/discord/markdown/validation/`
- **`CommonMarkValidator`**: 使用 CommonMark Java 進行解析和驗證
- **`MarkdownErrorFormatter`**: 格式化錯誤報告給 LLM
- **`MarkdownValidatingAIChatService`**: 裝飾器，包裝 AIChatService 進行驗證

### 現有 ErrorType 枚舉

```java
enum ErrorType {
    MALFORMED_LIST,          // 列表格式錯誤
    UNCLOSED_CODE_BLOCK,     // 程式碼區塊未閉合
    HEADING_LEVEL_EXCEEDED,  // 標題層級超過 H6
    HEADING_FORMAT,          // 標題格式錯誤（缺少空格）
    MALFORMED_TABLE,         // 表格格式錯誤
    ESCAPE_CHARACTER_MISSING, // 缺少轉義字符
    DISCORD_RENDER_ISSUE     // Discord 渲染問題
}
```

### 可自動修復的錯誤類型

基於現有驗證邏輯，以下錯誤類型適合自動修復：

| 錯誤類型 | 修復策略 | 安全性 |
|---------|---------|--------|
| `HEADING_FORMAT` | 在 `#` 和標題文字之間插入空格 | 高（格式規範） |
| `UNCLOSED_CODE_BLOCK` | 在文末添加閉合的 ``` | 中（可能改變意圖） |
| `MALFORMED_LIST` | 修正列表項目縮排 | 中（可能改變結構） |
| `ESCAPE_CHARACTER_MISSING` | 轉義特殊字符 | 低（可能改變語意） |

**不適合自動修復的類型**：
- `HEADING_LEVEL_EXCEEDED`: 需要重寫標題結構
- `MALFORMED_TABLE`: 需要理解表格語意
- `DISCORD_RENDER_ISSUE`: 需要平台特定知識

---

## Task 1: 定義 MarkdownAutoFixer 介面

**檔案:**
- 新增: `src/main/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixer.java`

**步驟 1: 撰寫失敗測試**

新增測試檔案: `src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java`

```java
package ltdjms.discord.markdown.autofix;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownAutoFixerTest {

    @Test
    void shouldDefineInterfaceContract() {
        // 此測試確保介面存在且有正確的方法簽名
        // 實際實作會在後續步驟完成
        assertNotNull(MarkdownAutoFixer.class);
    }
}
```

**步驟 2: 執行測試確認失敗**

```bash
mvn test -Dtest=MarkdownAutoFixerTest -pl .
```

預期: FAIL - 類別不存在

**步驟 3: 建立最小實作**

新增: `src/main/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixer.java`

```java
package ltdjms.discord.markdown.autofix;

/**
 * 自動修復 Markdown 格式錯誤的介面。
 *
 * <p>實作此介面的類別應該能夠識別並修復常見的 Markdown 格式問題，
 * 例如標題格式錯誤、未閉合的程式碼區塊等。</p>
 */
public interface MarkdownAutoFixer {

    /**
     * 嘗試自動修復 Markdown 文字中的格式錯誤。
     *
     * @param markdown 原始 Markdown 文字
     * @return 修復後的 Markdown 文字，如果無法修復則返回原始文字
     */
    String autoFix(String markdown);
}
```

**步驟 4: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownAutoFixerTest
```

預期: PASS

**步驟 5: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixer.java \
        src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java
git commit -m "feat(markdown): add MarkdownAutoFixer interface"
```

---

## Task 2: 實作 RegexBasedAutoFixer - 標題格式修復

**檔案:**
- 新增: `src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java`
- 修改: `src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java`

**步驟 1: 撰寫標題格式修復的失敗測試**

在 `MarkdownAutoFixerTest.java` 中新增:

```java
package ltdjms.discord.markdown.autofix;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownAutoFixerTest {

    // ... 之前的測試 ...

    @Test
    @DisplayName("應該修復缺少空格的標題格式")
    void shouldFixHeadingFormatMissingSpace() {
        MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

        String input = "#This is a heading\n##Another heading";
        String expected = "# This is a heading\n## Another heading";

        String result = fixer.autoFix(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("不應該修復正確的標題格式")
    void shouldNotModifyCorrectHeadingFormat() {
        MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

        String input = "# This is correct\n## So is this";
        String expected = "# This is correct\n## So is this";

        String result = fixer.autoFix(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("應該處理混合正確與錯誤的標題")
    void shouldHandleMixedHeadingFormats() {
        MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

        String input = "#Wrong format\n# Correct format\n##Also wrong";
        String expected = "# Wrong format\n# Correct format\n## Also wrong";

        String result = fixer.autoFix(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("不應該將程式碼區塊中的 # 視為標題")
    void shouldNotFixHashInCodeBlocks() {
        MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

        String input = "```\n#This is code\n```\n#This is heading";
        String expected = "```\n#This is code\n```\n# This is heading";

        String result = fixer.autoFix(input);
        assertEquals(expected, result);
    }
}
```

**步驟 2: 執行測試確認失敗**

```bash
mvn test -Dtest=MarkdownAutoFixerTest#shouldFixHeadingFormatMissingSpace
```

預期: FAIL - RegexBasedAutoFixer 不存在

**步驟 3: 實作 RegexBasedAutoFixer 類別骨架**

新增: `src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java`

```java
package ltdjms.discord.markdown.autofix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基於正規表達式的 Markdown 自動修復器。
 *
 * <p>此實作使用正規表達式來識別和修復常見的 Markdown 格式錯誤。</p>
 */
public class RegexBasedAutoFixer implements MarkdownAutoFixer {

    private static final Logger log = LoggerFactory.getLogger(RegexBasedAutoFixer.class);

    /**
     * 正規表達式：匹配行首的連續 # 符號後直接跟隨非空白字符
     * 例如: "#Heading" 或 "##Subheading"
     */
    private static final Pattern HEADING_WITHOUT_SPACE =
        Pattern.compile("^(#{1,6})([^\\s#].*)$", Pattern.MULTILINE);

    @Override
    public String autoFix(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        String result = markdown;

        // 應用標題格式修復
        result = fixHeadingFormat(result);

        return result;
    }

    /**
     * 修復標題格式錯誤。
     *
     * <p>在 # 符號和標題文字之間插入空格。</p>
     *
     * @param markdown 原始 Markdown
     * @return 修復後的 Markdown
     */
    private String fixHeadingFormat(String markdown) {
        // 先保護程式碼區塊
        String protectedMarkdown = protectCodeBlocks(markdown);
        String[] parts = protectedMarkdown.split("\u0000CODE_BLOCK_\\d+\u0000");

        StringBuilder result = new StringBuilder();
        int codeBlockIndex = 0;
        Pattern codeBlockPlaceholder = Pattern.compile("\u0000CODE_BLOCK_(\\d+)\u0000");

        // 處理非程式碼區塊的部分
        Matcher placeholderMatcher = codeBlockPlaceholder.matcher(protectedMarkdown);
        int lastEnd = 0;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            // 修復這部分中的標題
            String fixedPart = HEADING_WITHOUT_SPACE.matcher(part).replaceAll("$1 $2");
            result.append(fixedPart);

            // 如果還有程式碼區塊佔位符，添加回去
            if (placeholderMatcher.find()) {
                result.append(placeholderMatcher.group());
            }
        }

        return restoreCodeBlocks(result.toString());
    }

    /**
     * 保護程式碼區塊，替換為佔位符。
     */
    private String protectCodeBlocks(String markdown) {
        // 簡化版本：只處理 ``` 區塊
        return markdown.replaceAll("```[\\s\\S]*?```", "\u0000CODE_BLOCK_$0\u0000");
    }

    /**
     * 還原程式碼區塊。
     */
    private String restoreCodeBlocks(String markdown) {
        return markdown.replace("\u0000CODE_BLOCK_", "```").replace("\u0000", "```");
    }
}
```

**步驟 4: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownAutoFixerTest
```

預期: 部分通過（標題格式測試通過）

**步驟 5: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java \
        src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java
git commit -m "feat(markdown): implement heading format auto-fix in RegexBasedAutoFixer"
```

---

## Task 3: 實作未閉合程式碼區塊修復

**檔案:**
- 修改: `src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java`
- 修改: `src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java`

**步驟 1: 撰寫失敗測試**

在 `MarkdownAutoFixerTest.java` 中新增:

```java
@Test
@DisplayName("應該修復未閉合的程式碼區塊")
void shouldFixUnclosedCodeBlock() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\nconsole.log('hello');\nSome text after";
    String expected = "```\nconsole.log('hello');\n```\nSome text after";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
}

@Test
@DisplayName("應該處理多個未閉合的程式碼區塊")
void shouldFixMultipleUnclosedCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\nblock1\n```\n```\nblock2\nText";
    String expected = "```\nblock1\n```\n```\nblock2\n```\nText";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
}

@Test
@DisplayName("不應該修復已閉合的程式碼區塊")
void shouldNotModifyClosedCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\nconst x = 1;\n```\nNormal text";
    String expected = "```\nconst x = 1;\n```\nNormal text";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
}

@Test
@DisplayName("應該處理帶語言標籤的程式碼區塊")
void shouldFixCodeBlocksWithLanguageSpec() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```java\npublic class Test {}\nText";
    String expected = "```java\npublic class Test {}\n```\nText";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
}
```

**步驟 2: 執行測試確認失敗**

```bash
mvn test -Dtest=MarkdownAutoFixerTest#shouldFixUnclosedCodeBlock
```

預期: FAIL - 功能未實作

**步驟 3: 實作修復邏輯**

在 `RegexBasedAutoFixer.java` 的 `autoFix` 方法中新增修復邏輯：

```java
@Override
public String autoFix(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
        return markdown;
    }

    String result = markdown;

    // 應用修復（順序很重要）
    result = fixUnclosedCodeBlocks(result);
    result = fixHeadingFormat(result);

    return result;
}

/**
 * 修復未閉合的程式碼區塊。
 *
 * <p>計算程式碼區塊的開啟和閉合標記數量，如果不匹配則在文末添加閉合標記。</p>
 *
 * @param markdown 原始 Markdown
 * @return 修復後的 Markdown
 */
private String fixUnclosedCodeBlocks(String markdown) {
    // 使用狀態機追蹤程式碼區塊
    StringBuilder result = new StringBuilder();
    boolean inCodeBlock = false;
    int codeBlockStart = -1;

    for (int i = 0; i < markdown.length(); i++) {
        char c = markdown.charAt(i);

        // 檢查是否是程式碼區塊標記 (```)
        if (c == '`' && i + 2 < markdown.length()) {
            if (markdown.charAt(i + 1) == '`' && markdown.charAt(i + 2) == '`') {
                // 確保不是四個連續的 `
                boolean isQuad = i + 3 < markdown.length() && markdown.charAt(i + 3) == '`';
                if (!isQuad) {
                    inCodeBlock = !inCodeBlock;
                    if (inCodeBlock) {
                        codeBlockStart = i;
                    }
                    result.append("```");
                    i += 2; // 跳過後兩個 `
                    continue;
                }
            }
        }

        result.append(c);
    }

    // 如果結束時仍在程式碼區塊中，添加閉合標記
    if (inCodeBlock) {
        log.debug("偵測到未閉合的程式碼區塊（位置: {}），自動閉合", codeBlockStart);
        result.append("\n```");
    }

    return result.toString();
}
```

**步驟 4: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownAutoFixerTest#shouldFixUnclosedCodeBlock
```

預期: PASS

**步驟 5: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java \
        src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java
git commit -m "feat(markdown): implement unclosed code block auto-fix"
```

---

## Task 4: 新增可配置的自動修復開關

**檔案:**
- 修改: `src/main/java/ltdjms/discord/markdown/config/MarkdownValidationConfig.java`
- 修改: `src/test/java/ltdjms/discord/markdown/config/MarkdownValidationConfigTest.java`

**步驟 1: 檢查現有配置類別**

```bash
cat src/main/java/ltdjms/discord/markdown/config/MarkdownValidationConfig.java
```

確認現有欄位：
```java
private final boolean enableMarkdownValidation;
private final boolean streamingBypassValidation;
private final int maxMarkdownValidationRetries;
```

**步驟 2: 撰寫失敗測試**

在 `MarkdownValidationConfigTest.java` 中新增:

```java
@Test
@DisplayName("應該支援自動修復開關配置")
void shouldSupportAutoFixEnabledConfig() {
    MarkdownValidationConfig config = new MarkdownValidationConfig(
        true,   // enableMarkdownValidation
        false,  // streamingBypassValidation
        5,      // maxRetries
        true    // enableAutoFix
    );

    assertTrue(config.isAutoFixEnabled());
}

@Test
@DisplayName("預設應該啟用自動修復")
void shouldEnableAutoFixByDefault() {
    // 測試從環境變數讀取時的預設行為
    // 需要確認是否有預設建構函式或工廠方法
}
```

**步驟 3: 修改配置類別**

在 `MarkdownValidationConfig.java` 中新增:

```java
// 在類別中新增欄位
private final boolean enableAutoFix;

// 在建構函式中新增參數
public MarkdownValidationConfig(
    boolean enableMarkdownValidation,
    boolean streamingBypassValidation,
    int maxRetries,
    boolean enableAutoFix
) {
    this.enableMarkdownValidation = enableMarkdownValidation;
    this.streamingBypassValidation = streamingBypassValidation;
    this.maxMarkdownValidationRetries = maxRetries;
    this.enableAutoFix = enableAutoFix;
}

// 新增 getter 方法
public boolean isAutoFixEnabled() {
    return enableAutoFix;
}
```

**步驟 4: 更新配置讀取邏輯**

檢查並修改配置來源（可能是 `AIConfig` 或環境變數讀取）：

```bash
# 查找配置來源
grep -r "enableMarkdownValidation" src/main/java/
```

假設找到 `AIConfig.java`，在對應位置新增:
```java
// 新增環境變數讀取
private static final boolean DEFAULT_AUTO_FIX_ENABLED = true;
private final boolean enableAutoFix;

// 在建構函式中
this.enableAutoFix = getBooleanEnv("AI_MARKDOWN_AUTO_FIX_ENABLED", DEFAULT_AUTO_FIX_ENABLED);

// 新增 getter
public boolean isMarkdownAutoFixEnabled() {
    return enableAutoFix;
}
```

**步驟 5: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownValidationConfigTest
```

預期: PASS

**步驟 6: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/config/MarkdownValidationConfig.java \
        src/test/java/ltdjms/discord/markdown/config/MarkdownValidationConfigTest.java
git commit -m "feat(markdown): add auto-fix enabled configuration option"
```

---

## Task 5: 整合自動修復到驗證流程

**檔案:**
- 修改: `src/main/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatService.java`
- 修改: `src/test/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatServiceTest.java`

**步驟 1: 檢查現有服務架構**

```bash
cat src/main/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatService.java
```

**步驟 2: 撰寫整合測試**

在 `MarkdownValidatingAIChatServiceTest.java` 中新增:

```java
@Test
@DisplayName("當啟用自動修復時，應該先嘗試修復再重試")
@ExtendWith(MockitoExtension.class)
void shouldAutoFixBeforeRetrying() {
    // Arrange
    AIChatService mockDelegate = mock(AIChatService.class);
    MarkdownValidator validator = mock(MarkdownValidator.class);
    MarkdownAutoFixer autofixer = mock(MarkdownAutoFixer.class);
    MarkdownErrorFormatter formatter = mock(MarkdownErrorFormatter.class);

    MarkdownValidationConfig config = new MarkdownValidationConfig(
        true,   // enableValidation
        false,  // streamingBypass
        3,      // maxRetries
        true    // enableAutoFix
    );

    MarkdownValidatingAIChatService service =
        new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, formatter, config);

    String userInput = "Test input";
    ValidationResult originalError = ValidationResult.invalid(
        List.of(new ValidationError(ErrorType.HEADING_FORMAT, 1, 1, "#Bad heading", "Add space"))
    );

    // 第一次回應有錯誤
    when(mockDelegate.processChat(any()))
        .thenReturn(AIResponse.text("#Bad heading\nMore text"));
    when(validator.validate(any()))
        .thenReturn(originalError);

    // 自動修復後的內容通過驗證
    when(autofixer.autoFix("#Bad heading\nMore text"))
        .thenReturn("# Bad heading\nMore text");
    when(validator.validate("# Bad heading\nMore text"))
        .thenReturn(ValidationResult.valid());

    // Act
    AIResponse result = service.processChat(userInput);

    // Assert
    assertNotNull(result);
    verify(autofixer).autoFix(any());
    verify(mockDelegate, times(1)).processChat(any()); // 只調用一次，因為自動修復成功
}
```

**步驟 3: 修改服務類別**

在 `MarkdownValidatingAIChatService.java` 中整合自動修復:

```java
// 在類別中新增依賴
private final MarkdownAutoFixer autofixer;

// 在建構函式中注入
public MarkdownValidatingAIChatService(
    AIChatService delegate,
    MarkdownValidator validator,
    MarkdownAutoFixer autofixer,
    MarkdownErrorFormatter formatter,
    MarkdownValidationConfig config
) {
    this.delegate = delegate;
    this.validator = validator;
    this.autofixer = autofixer;
    this.formatter = formatter;
    this.config = config;
}

// 修改驗證邏輯
private AIResponse processWithValidation(ChatRequest request) {
    AIResponse response = delegate.processChat(request);

    // 跳過驗證的情況...
    if (shouldSkipValidation(request)) {
        return response;
    }

    String content = response.text();
    ValidationResult result = validator.validate(content);

    if (result.isValid()) {
        return response;
    }

    // 嘗試自動修復
    if (config.isAutoFixEnabled()) {
        String fixedContent = autofixer.autoFix(content);
        ValidationResult fixedResult = validator.validate(fixedContent);

        if (fixedResult.isValid()) {
            log.info("自動修復成功: {} 個錯誤被修復", result.getErrors().size());
            return AIResponse.text(fixedContent);
        }

        // 自動修復不完全，記錄差異
        log.debug("自動修復部分成功: {} 個錯誤中修復了 {} 個",
            result.getErrors().size(),
            result.getErrors().size() - fixedResult.getErrors().size());
    }

    // 觸發重試邏輯...
    return processWithRetry(request, content, result);
}
```

**步驟 4: 更新 Dagger 模組**

修改 `src/main/java/ltdjms/discord/markdown/config/MarkdownValidationModule.java`:

```java
@Provides
@Singleton
public static MarkdownAutoFixer provideMarkdownAutoFixer() {
    return new RegexBasedAutoFixer();
}

// 修改 provideMarkdownValidatingAIChatService
@Provides
@Singleton
public static AIChatService provideMarkdownValidatingAIChatService(
    AIChatService delegate,
    MarkdownValidator validator,
    MarkdownAutoFixer autofixer,
    MarkdownErrorFormatter formatter,
    MarkdownValidationConfig config
) {
    return new MarkdownValidatingAIChatService(
        delegate,
        validator,
        autofixer,  // 新增參數
        formatter,
        config
    );
}
```

**步驟 5: 執行整合測試**

```bash
mvn test -Dtest=MarkdownValidatingAIChatServiceTest
```

預期: PASS

**步驟 6: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatService.java \
        src/main/java/ltdjms/discord/markdown/config/MarkdownValidationModule.java \
        src/test/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatServiceTest.java
git commit -m "feat(markdown): integrate auto-fixer into validation flow"
```

---

## Task 6: 新增自動修復日誌和監控

**檔案:**
- 修改: `src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java`
- 新增: `src/test/java/ltdjms/discord/markdown/autofix/AutoFixResult.java`

**步驟 1: 定義修復結果類別**

新增: `src/main/java/ltdjms/discord/markdown/autofix/AutoFixResult.java`

```java
package ltdjms.discord.markdown.autofix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自動修復操作的結果。
 */
public class AutoFixResult {

    private final String original;
    private final String fixed;
    private final List<FixAction> actions;
    private final boolean wasModified;

    public AutoFixResult(String original, String fixed, List<FixAction> actions) {
        this.original = original;
        this.fixed = fixed;
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        this.wasModified = !original.equals(fixed);
    }

    public String getOriginal() {
        return original;
    }

    public String getFixed() {
        return fixed;
    }

    public List<FixAction> getActions() {
        return actions;
    }

    public boolean wasModified() {
        return wasModified;
    }

    public static AutoFixResult notModified(String content) {
        return new AutoFixResult(content, content, Collections.emptyList());
    }

    /**
     * 表示單一修復動作。
     */
    public static record FixAction(
        FixType type,
        String description,
        int line
    ) {}

    public enum FixType {
        HEADING_FORMAT_ADDED,
        CODE_BLOCK_CLOSED,
        SPECIAL_CHAR_ESCAPED
    }
}
```

**步驟 2: 撰寫測試**

新增: `src/test/java/ltdjms/discord/markdown/autofix/AutoFixResultTest.java`

```java
package ltdjms.discord.markdown.autofix;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoFixResultTest {

    @Test
    void shouldDetectModification() {
        AutoFixResult result = new AutoFixResult(
            "#Heading",
            "# Heading",
            List.of(new AutoFixResult.FixAction(
                AutoFixResult.FixType.HEADING_FORMAT_ADDED,
                "Added space after #",
                1
            ))
        );

        assertTrue(result.wasModified());
    }

    @Test
    void shouldDetectNoModification() {
        AutoFixResult result = AutoFixResult.notModified("# Heading");

        assertFalse(result.wasModified());
        assertTrue(result.getActions().isEmpty());
    }
}
```

**步驟 3: 修改 AutoFixer 介面返回結果**

修改 `MarkdownAutoFixer.java`:

```java
/**
 * 嘗試自動修復 Markdown 文字中的格式錯誤。
 *
 * @param markdown 原始 Markdown 文字
 * @return 包含修復結果和動作詳情的 AutoFixResult
 */
AutoFixResult autoFix(String markdown);
```

**步驟 4: 更新 RegexBasedAutoFixer 實作**

修改 `RegexBasedAutoFixer.java` 返回 `AutoFixResult` 並記錄動作:

```java
@Override
public AutoFixResult autoFix(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
        return AutoFixResult.notModified(markdown);
    }

    String result = markdown;
    List<FixAction> actions = new ArrayList<>();

    // 修復並記錄動作
    CodeBlockFixResult codeBlockResult = fixUnclosedCodeBlocks(result);
    result = codeBlockResult.fixed();
    if (codeBlockResult.wasFixed()) {
        actions.add(new FixAction(
            FixType.CODE_BLOCK_CLOSED,
            "Closed unclosed code block",
            codeBlockResult.lineNumber()
        ));
    }

    HeadingFixResult headingResult = fixHeadingFormat(result);
    result = headingResult.fixed();
    actions.addAll(headingResult.actions());

    if (actions.isEmpty()) {
        log.debug("無需修復: Markdown 格式正確");
        return AutoFixResult.notModified(markdown);
    }

    log.info("自動修復完成: {} 個動作", actions.size());
    for (FixAction action : actions) {
        log.debug("  - {}: {}", action.type(), action.description());
    }

    return new AutoFixResult(markdown, result, actions);
}

// 輔助類別用於追蹤修復
private record CodeBlockFixResult(String fixed, boolean wasFixed, int lineNumber) {}
private record HeadingFixResult(String fixed, List<FixAction> actions) {}
```

**步驟 5: 執行測試**

```bash
mvn test -Dtest=AutoFixResultTest,MarkdownAutoFixerTest
```

預期: PASS

**步驟 6: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/autofix/AutoFixResult.java \
        src/main/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixer.java \
        src/main/java/ltdjms/discord/markdown/autofix/RegexBasedAutoFixer.java \
        src/test/java/ltdjms/discord/markdown/autofix/AutoFixResultTest.java
git commit -m "feat(markdown): add detailed auto-fix result tracking"
```

---

## Task 7: 更新現有測試以支援新的 AutoFixResult

**檔案:**
- 修改: `src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java`
- 修改: `src/test/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatServiceTest.java`

**步驟 1: 更新 MarkdownAutoFixerTest**

修改測試以使用新的返回類型:

```java
@Test
@DisplayName("應該修復缺少空格的標題格式")
void shouldFixHeadingFormatMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "#This is a heading";
    AutoFixResult result = fixer.autoFix(input);

    assertEquals("# This is a heading", result.getFixed());
    assertTrue(result.wasModified());
    assertEquals(1, result.getActions().size());
    assertEquals(FixType.HEADING_FORMAT_ADDED, result.getActions().get(0).type());
}
```

**步驟 2: 更新 MarkdownValidatingAIChatServiceTest**

修改服務測試:

```java
@Test
@DisplayName("當啟用自動修復時，應該先嘗試修復再重試")
void shouldAutoFixBeforeRetrying() {
    // ...

    when(autofixer.autoFix("#Bad heading"))
        .thenReturn(AutoFixResult.notModified("# Bad heading")); // 或使用實際的結果

    // ...
}
```

**步驟 3: 執行所有測試**

```bash
mvn test -Dtest=*MarkdownAutoFix*,*MarkdownValidating*
```

預期: PASS

**步驟 4: 提交**

```bash
git add src/test/java/ltdjms/discord/markdown/autofix/MarkdownAutoFixerTest.java \
        src/test/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatServiceTest.java
git commit -m "test(markdown): update tests for AutoFixResult"
```

---

## Task 8: 撰寫整合測試驗證端到端流程

**檔案:**
- 修改: `src/test/java/ltdjms/discord/markdown/integration/MarkdownValidationIntegrationTest.java`

**步驟 1: 新增整合測試案例**

```java
@Test
@DisplayName("端到端: 應該自動修復格式錯誤並避免重試")
@Tag("integration")
void shouldAutoFixErrorsEndToEnd() {
    // Arrange - 使用真實的 DI 組裝
    AIChatService service = createServiceWithAutoFixEnabled();

    String userInput = "生成一段包含標題和程式碼的內容";

    // Act - 模擬 AI 返回格式錯誤的內容
    // (這裡需要 mock AI 服務或使用測試用的 AI)

    // Assert - 驗證自動修復被觸發且內容被修正
}

@Test
@DisplayName("端到端: 應該在自動修復失敗後觸發重試")
@Tag("integration")
void shouldFallbackToRetryOnAutoFixFailure() {
    // 類似的測試，驗證降級邏輯
}
```

**步驟 2: 執行整合測試**

```bash
mvn test -Dtest=MarkdownValidationIntegrationTest -P integration
```

預期: PASS

**步驟 3: 提交**

```bash
git add src/test/java/ltdjms/discord/markdown/integration/MarkdownValidationIntegrationTest.java
git commit -m "test(markdown): add end-to-end integration tests for auto-fix"
```

---

## Task 9: 更新文件

**檔案:**
- 修改: `docs/architecture/overview.md`
- 修改: `docs/development/configuration.md`
- 新增: `docs/modules/markdown-validation.md` (如果尚未存在)

**步驟 1: 更新架構文件**

在 `docs/architecture/overview.md` 中新增自動修復說明:

```markdown
## Markdown 處理與驗證

系統包含完整的 Markdown 處理流程：

1. **自動修復** - 修復常見格式錯誤（標題、程式碼區塊等）
2. **驗證** - 使用 CommonMark 檢測語法錯誤
3. **重試** - 將錯誤報告回傳給 LLM 進行修正
```

**步驟 2: 更新配置文件**

在 `docs/development/configuration.md` 中新增:

```markdown
### Markdown 自動修復配置

| 環境變數 | 預設值 | 說明 |
|---------|--------|------|
| `AI_MARKDOWN_AUTO_FIX_ENABLED` | `true` | 是否啟用自動修復功能 |
```

**步驟 3: 執行文件檢查**

```bash
# 確認 Markdown 格式正確（諷刺的是...）
cat docs/architecture/overview.md | head -50
```

**步驟 4: 提交**

```bash
git add docs/architecture/overview.md \
        docs/development/configuration.md
git commit -m "docs(markdown): document auto-fix feature"
```

---

## Task 10: 驗證覆蓋率和最終測試

**檔案:**
- 執行覆蓋率檢查

**步驟 1: 執行完整測試套件**

```bash
mvn clean test
```

預期: 所有測試通過

**步驟 2: 執行覆蓋率檢查**

```bash
mvn jacoco:report
```

預期: 覆蓋率 >= 80%

**步驟 3: 檢查新增程式碼的覆蓋率**

```bash
# 查看報告
open target/site/jacoco/index.html
```

預期: 所有新增類別覆蓋率 >= 80%

**步驟 4: 如果覆蓋率不足，補充測試**

(根據報告結果決定)

**步驟 5: 最終提交**

```bash
git add .
git commit -m "test(markdown): achieve 80% coverage for auto-fix feature"
```

---

## 測試清單

在完成實施後，驗證以下功能：

- [ ] 標題格式錯誤自動修復
- [ ] 未閉合程式碼區塊自動修復
- [ ] 配置開關正常運作
- [ ] 自動修復失敗時正確降級到重試機制
- [ ] 日誌正確記錄修復動作
- [ ] 所有單元測試通過
- [ ] 整合測試通過
- [ ] 覆蓋率 >= 80%
- [ ] 文件已更新

---

## 風險與注意事項

1. **修復可能改變原意**: 自動修復應該保守，只在確定無誤的情況下修復
2. **效能考量**: 正規表達式處理大量文字可能較慢，監控效能影響
3. **邊界情況**: 測試各種邊界情況，如空輸入、純程式碼、混合格式等
4. **配置相容性**: 確保新配置項目向後相容

---

## 後續改進方向

完成此計劃後，可考慮以下增強：

1. 支援更多修復類型（列表格式、表格格式等）
2. 新增修復預覽功能，讓用戶確認修復
3. 支援自定義修復規則
4. 整合 LLM 進行智能修復決策
