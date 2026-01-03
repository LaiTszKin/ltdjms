# Markdown 格式驗證功能實作計畫

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 實作一個 Markdown 格式驗證器，使用裝飾器模式包裝現有的 AI 聊天服務，當 LLM 回覆格式錯誤時自動重新生成。

**Architecture:** 使用裝飾器模式在 `AIChatService` 外層添加驗證層，不修改現有代碼。驗證器使用 CommonMark Java 進行解析檢測格式錯誤，並提供結構化錯誤報告給 LLM 進行自我修正。

**Tech Stack:** Java 17, CommonMark 0.22.0, Dagger 2, JUnit 5, Mockito

**相關文件:**
- 設計文件: [2026-01-03-markdown-validation-design.md](2026-01-03-markdown-validation-design.md)
- AI 聊天服務: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`
- 服務介面: `src/main/java/ltdjms/discord/aichat/services/AIChatService.java`
- 配置類: `src/main/java/ltdjms/discord/aichat/domain/AIServiceConfig.java`

---

## 前置準備

### Task 0: 添加 Maven 依賴

**Files:**
- Modify: `pom.xml`

**Step 1: 添加 CommonMark 依賴**

在 `<dependencies>` 區塊中添加：

```xml
<!-- CommonMark for Markdown validation -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.22.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.22.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-task-list-items</artifactId>
    <version>0.22.0</version>
</dependency>
```

**Step 2: 執行 Maven 下載依賴**

```bash
mvn dependency:resolve
```

Expected: 依賴成功下載，無錯誤

**Step 3: 提交**

```bash
git add pom.xml
git commit -m "feat(markdown): add CommonMark dependencies for markdown validation"
```

---

## Phase 1: 核心驗證器實作

### Task 1: 建立 MarkdownValidator 介面與相關類型

**Files:**
- Create: `src/main/java/ltdjms/discord/markdown/validation/MarkdownValidator.java`
- Test: `src/test/java/ltdjms/discord/markdown/unit/validation/MarkdownValidatorTest.java`

**Step 1: 建立測試目錄結構**

```bash
mkdir -p src/main/java/ltdjms/discord/markdown/validation
mkdir -p src/test/java/ltdjms/discord/markdown/unit/validation
```

**Step 2: 撰寫定義型別行為的測試**

```java
package ltdjms.discord.markdown.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownValidatorTest {

    @Test
    void ValidationResult_shouldHaveValidAndInvalidStates() {
        // Valid 狀態包含原始 markdown
        MarkdownValidator.ValidationResult.Valid valid =
            new MarkdownValidator.ValidationResult.Valid("test");
        assertEquals("test", valid.markdown());

        // Invalid 狀態包含錯誤列表
        java.util.List<MarkdownValidator.MarkdownError> errors = java.util.List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.MALFORMED_LIST,
                1, 1, "context", "suggestion"
            )
        );
        MarkdownValidator.ValidationResult.Invalid invalid =
            new MarkdownValidator.ValidationResult.Invalid(errors);
        assertEquals(1, invalid.errors().size());
    }

    @Test
    void MarkdownError_shouldContainAllRequiredFields() {
        MarkdownValidator.MarkdownError error = new MarkdownValidator.MarkdownError(
            MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK,
            10,
            5,
            "```java without closing",
            "Add closing ```"
        );

        assertEquals(MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK, error.type());
        assertEquals(10, error.lineNumber());
        assertEquals(5, error.column());
        assertEquals("```java without closing", error.context());
        assertEquals("Add closing ```", error.suggestion());
    }

    @Test
    void ErrorType_shouldCoverAllCommonMarkdownErrors() {
        MarkdownValidator.ErrorType[] types = MarkdownValidator.ErrorType.values();

        assertTrue(java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.MALFORMED_LIST));
        assertTrue(java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK));
        assertTrue(java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.HEADING_LEVEL_EXCEEDED));
        assertTrue(java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.MALFORMED_TABLE));
        assertTrue(java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.ESCAPE_CHARACTER_MISSING));
        assertTrue(java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE));
    }
}
```

**Step 3: 執行測試確認失敗**

```bash
mvn test -Dtest=MarkdownValidatorTest
```

Expected: COMPILATION ERROR - 類別不存在

**Step 4: 實作介面與型別**

```java
package ltdjms.discord.markdown.validation;

/**
 * Markdown 格式驗證器介面
 * 用於驗證 LLM 生成的回應是否符合 Markdown 語法規範
 */
public interface MarkdownValidator {

    /**
     * 驗證 Markdown 文字格式
     * @param markdown 待驗證的 Markdown 文字
     * @return 驗證結果，包含成功或失敗的詳細錯誤資訊
     */
    ValidationResult validate(String markdown);

    /**
     * 驗證結果的封閉型別
     */
    sealed interface ValidationResult {
        record Valid(String markdown) implements ValidationResult {}
        record Invalid(java.util.List<MarkdownError> errors) implements ValidationResult {}
    }

    /**
     * Markdown 格式錯誤詳細資訊
     * @param type 錯誤類型
     * @param lineNumber 錯誤行號（從 1 開始）
     * @param column 錯誤欄位（從 1 開始）
     * @param context 錯誤上下文
     * @param suggestion 修正建議
     */
    record MarkdownError(
        ErrorType type,
        int lineNumber,
        int column,
        String context,
        String suggestion
    ) {}

    /**
     * Markdown 錯誤類型枚舉
     */
    enum ErrorType {
        /** 列表格式錯誤（如未使用正確的符號） */
        MALFORMED_LIST,
        /** 程式碼區塊未閉合 */
        UNCLOSED_CODE_BLOCK,
        /** 標題層級超過限制（Discord 限制為 H6） */
        HEADING_LEVEL_EXCEEDED,
        /** 表格格式錯誤 */
        MALFORMED_TABLE,
        /** 缺少轉義字符 */
        ESCAPE_CHARACTER_MISSING,
        /** Discord 特定的渲染問題 */
        DISCORD_RENDER_ISSUE
    }
}
```

**Step 5: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownValidatorTest
```

Expected: PASS

**Step 6: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/validation/MarkdownValidator.java
git add src/test/java/ltdjms/discord/markdown/unit/validation/MarkdownValidatorTest.java
git commit -m "feat(markdown): define MarkdownValidator interface and types"
```

---

### Task 2: 實作 CommonMarkValidator

**Files:**
- Create: `src/main/java/ltdjms/discord/markdown/validation/CommonMarkValidator.java`
- Test: `src/test/java/ltdjms/discord/markdown/unit/validation/CommonMarkValidatorTest.java`

**Step 1: 撰寫測試 - 檢測未閉合的程式碼區塊**

```java
package ltdjms.discord.markdown.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

@DisplayName("CommonMarkValidator - 程式碼區塊驗證")
class CommonMarkValidatorTest_CodeBlocks {

    private final MarkdownValidator validator = new CommonMarkValidator();

    @Test
    @DisplayName("正確的程式碼區塊應通過驗證")
    void validCodeBlock_shouldPass() {
        String markdown = """
            這是程式碼範例：

            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            ```

            結束。
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("未閉合的程式碼區塊應檢測為錯誤")
    void unclosedCodeBlock_shouldDetectError() {
        String markdown = """
            這是程式碼範例：

            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }

            結束。（缺少結束標記）
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
        MarkdownValidator.ValidationResult.Invalid invalid =
            (MarkdownValidator.ValidationResult.Invalid) result;

        boolean hasCodeBlockError = invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK);
        assertTrue(hasCodeBlockError, "應檢測到未閉合的程式碼區塊");
    }
}
```

**Step 2: 執行測試確認失敗**

```bash
mvn test -Dtest=CommonMarkValidatorTest_CodeBlocks
```

Expected: CLASS_NOT_FOUND 或 COMPILATION ERROR

**Step 3: 實作基本驗證器結構與程式碼區塊檢測**

```java
package ltdjms.discord.markdown.validation;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.node.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.task.list.items.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 CommonMark Java 實作的 Markdown 驗證器
 * 檢測語法錯誤並提供結構化錯誤報告
 */
public final class CommonMarkValidator implements MarkdownValidator {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public CommonMarkValidator() {
        this.parser = Parser.builder()
            .extensions(List.of(
                TablesExtension.create(),
                TaskListItemsExtension.create()
            ))
            .build();

        this.renderer = HtmlRenderer.builder()
            .extensions(List.of(
                TablesExtension.create(),
                TaskListItemsExtension.create()
            ))
            .build();
    }

    // 用於依賴注入的建構函式
    public CommonMarkValidator(Parser parser, HtmlRenderer renderer) {
        this.parser = parser;
        this.renderer = renderer;
    }

    @Override
    public ValidationResult validate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new ValidationResult.Valid(markdown);
        }

        List<MarkdownError> errors = new ArrayList<>();

        // 1. 解析階段 - 檢測語法錯誤
        try {
            Node document = parser.parse(markdown);
            checkCodeBlocks(document, markdown, errors);
        } catch (Exception e) {
            // 解析失敗視為格式錯誤
            errors.add(new MarkdownError(
                ErrorType.MALFORMED_LIST,
                1,
                1,
                markdown.substring(0, Math.min(50, markdown.length())),
                "檢查 Markdown 語法是否正確"
            ));
        }

        if (errors.isEmpty()) {
            return new ValidationResult.Valid(markdown);
        } else {
            return new ValidationResult.Invalid(errors);
        }
    }

    /**
     * 檢查程式碼區塊是否正確閉合
     */
    private void checkCodeBlocks(Node node, String fullMarkdown, List<MarkdownError> errors) {
        String[] lines = fullMarkdown.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                // 檢查是否有對應的結束標記
                boolean hasClosing = false;
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].trim().startsWith("```")) {
                        hasClosing = true;
                        break;
                    }
                }

                if (!hasClosing) {
                    errors.add(new MarkdownError(
                        ErrorType.UNCLOSED_CODE_BLOCK,
                        i + 1,
                        1,
                        line.length() > 50 ? line.substring(0, 50) + "..." : line,
                        "在程式碼區塊結束處添加 ```"
                    ));
                    break; // 每個未閉合區塊只報告一次
                }
            }
        }
    }
}
```

**Step 4: 執行測試確認通過**

```bash
mvn test -Dtest=CommonMarkValidatorTest_CodeBlocks
```

Expected: PASS

**Step 5: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/validation/CommonMarkValidator.java
git add src/test/java/ltdjms/discord/markdown/unit/validation/CommonMarkValidatorTest_CodeBlocks.java
git commit -m "feat(markdown): implement CommonMarkValidator with code block validation"
```

---

### Task 3: 擴展 CommonMarkValidator - 列表格式驗證

**Files:**
- Modify: `src/main/java/ltdjms/discord/markdown/validation/CommonMarkValidator.java`
- Test: `src/test/java/ltdjms/discord/markdown/unit/validation/CommonMarkValidatorTest_Lists.java`

**Step 1: 撰寫列表格式測試**

```java
package ltdjms.discord.markdown.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommonMarkValidator - 列表格式驗證")
class CommonMarkValidatorTest_Lists {

    private final MarkdownValidator validator = new CommonMarkValidator();

    @Test
    @DisplayName("正確的無序列表應通過驗證")
    void validUnorderedList_shouldPass() {
        String markdown = """
            事項清單：

            - 項目一
            - 項目二
            - 項目三
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("正確的有序列表應通過驗證")
    void validOrderedList_shouldPass() {
        String markdown = """
            步驟：

            1. 第一步
            2. 第二步
            3. 第三步
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("使用 * 而非 - 的無序列表應通過（CommonMark 允許）")
    void asteriskList_shouldPass() {
        String markdown = """
            * 項目一
            * 項目二
            * 項目三
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }
}
```

**Step 2: 執行測試確認通過**

```bash
mvn test -Dtest=CommonMarkValidatorTest_Lists
```

Expected: PASS（CommonMark 已支援正確的列表解析）

**Step 3: 提交**

```bash
git add src/test/java/ltdjms/discord/markdown/unit/validation/CommonMarkValidatorTest_Lists.java
git commit -m "test(markdown): add list format validation tests"
```

---

### Task 4: 擴展 CommonMarkValidator - Discord 特定檢查

**Files:**
- Modify: `src/main/java/ltdjms/discord/markdown/validation/CommonMarkValidator.java`
- Test: `src/test/java/ltdjms/discord/markdown/unit/validation/CommonMarkValidatorTest_Discord.java`

**Step 1: 撰寫 Discord 特定檢查測試**

```java
package ltdjms.discord.markdown.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommonMarkValidator - Discord 特定檢查")
class CommonMarkValidatorTest_Discord {

    private final MarkdownValidator validator = new CommonMarkValidator();

    @Test
    @DisplayName("標題層級不超過 H6 應通過")
    void headingWithinLimit_shouldPass() {
        String markdown = """
            # 標題 1
            ## 標題 2
            ### 標題 3
            #### 標題 4
            ##### 標題 5
            ###### 標題 6
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("標題層級超過 H6 應檢測為錯誤")
    void headingExceedsLimit_shouldDetectError() {
        String markdown = """
            ####### 超過限制的標題
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
        MarkdownValidator.ValidationResult.Invalid invalid =
            (MarkdownValidator.ValidationResult.Invalid) result;

        boolean hasHeadingError = invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_LEVEL_EXCEEDED);
        assertTrue(hasHeadingError, "應檢測到標題層級超限");
    }

    @Test
    @DisplayName("正確的表格格式應通過")
    void validTable_shouldPass() {
        String markdown = """
            | 欄位 A | 欄位 B | 欄位 C |
            |--------|--------|--------|
            | 值 1   | 值 2   | 值 3   |
            """;

        MarkdownValidator.ValidationResult result = validator.validate(markdown);

        assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }
}
```

**Step 2: 執行測試確認失敗**

```bash
mvn test -Dtest=CommonMarkValidatorTest_Discord
```

Expected: FAIL（heading 檢查尚未實作）

**Step 3: 在 CommonMarkValidator 中添加 Discord 特定檢查**

```java
// 在 validate() 方法中添加其他檢查
@Override
public ValidationResult validate(String markdown) {
    if (markdown == null || markdown.isBlank()) {
        return new ValidationResult.Valid(markdown);
    }

    List<MarkdownError> errors = new ArrayList<>();

    // 1. 檢查標題層級
    checkHeadingLevels(markdown, errors);

    // 2. 解析階段 - 檢測語法錯誤
    try {
        Node document = parser.parse(markdown);
        checkCodeBlocks(document, markdown, errors);
    } catch (Exception e) {
        errors.add(new MarkdownError(
            ErrorType.MALFORMED_LIST,
            1,
            1,
            markdown.substring(0, Math.min(50, markdown.length())),
            "檢查 Markdown 語法是否正確"
        ));
    }

    if (errors.isEmpty()) {
        return new ValidationResult.Valid(markdown);
    } else {
        return new ValidationResult.Invalid(errors);
    }
}

/**
 * 檢查標題層級是否超過 Discord 限制（H6）
 */
private void checkHeadingLevels(String markdown, List<MarkdownError> errors) {
    String[] lines = markdown.split("\n");

    for (int i = 0; i < lines.length; i++) {
        String line = lines[i].trim();
        if (line.startsWith("#")) {
            int count = 0;
            for (char c : line.toCharArray()) {
                if (c == '#') {
                    count++;
                } else {
                    break;
                }
            }

            if (count > 6) {
                errors.add(new MarkdownError(
                    ErrorType.HEADING_LEVEL_EXCEEDED,
                    i + 1,
                    1,
                    line,
                    "減少標題層級到 ###### 或以下"
                ));
            }
        }
    }
}
```

**Step 4: 執行測試確認通過**

```bash
mvn test -Dtest=CommonMarkValidatorTest_Discord
```

Expected: PASS

**Step 5: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/validation/CommonMarkValidator.java
git add src/test/java/ltdjms/discord/markdown/unit/validation/CommonMarkValidatorTest_Discord.java
git commit -m "feat(markdown): add Discord-specific validation (heading levels)"
```

---

## Phase 2: 錯誤報告格式化器

### Task 5: 實作 MarkdownErrorFormatter

**Files:**
- Create: `src/main/java/ltdjms/discord/markdown/validation/MarkdownErrorFormatter.java`
- Test: `src/test/java/ltdjms/discord/markdown/unit/validation/MarkdownErrorFormatterTest.java`

**Step 1: 撰寫測試**

```java
package ltdjms.discord.markdown.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarkdownErrorFormatter - 錯誤報告格式化")
class MarkdownErrorFormatterTest {

    private final MarkdownErrorFormatter formatter = new MarkdownErrorFormatter();

    @Test
    @DisplayName("格式化的錯誤報告應包含所有必要資訊")
    void formattedErrorReport_shouldContainAllInformation() {
        String originalPrompt = "解釋 Java 中的 CompletableFuture";
        String fullResponse = """
            這是範例程式碼：

            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            """;

        java.util.List<MarkdownValidator.MarkdownError> errors = java.util.List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK,
                3,
                1,
                "```java",
                "在程式碼區塊結束處添加 ```"
            )
        );

        String report = formatter.formatErrorReport(originalPrompt, errors, 1, fullResponse);

        // 驗證報告包含關鍵資訊
        assertTrue(report.contains("Markdown 格式驗證失敗"));
        assertTrue(report.contains("重試次數: 1"));
        assertTrue(report.contains("程式碼區塊未閉合"));
        assertTrue(report.contains("行 3"));
        assertTrue(report.contains("在程式碼區塊結束處添加 ```"));
    }

    @Test
    @DisplayName("多個錯誤應按類型分組顯示")
    void multipleErrors_shouldBeGroupedByType() {
        java.util.List<MarkdownValidator.MarkdownError> errors = java.util.List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK,
                3, 1, "```java", "Add closing ```"
            ),
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.HEADING_LEVEL_EXCEEDED,
                10, 1, "####### Too big", "Reduce to ######"
            )
        );

        String report = formatter.formatErrorReport("test", errors, 1, "response");

        // 驗證兩個錯誤類型的標題都出現
        assertTrue(report.contains("程式碼區塊未閉合"));
        assertTrue(report.contains("標題層級超限"));
    }

    @Test
    @DisplayName("錯誤上下文截斷應正常工作")
    void longContext_shouldBeTruncated() {
        String longContext = "a".repeat(100);
        java.util.List<MarkdownValidator.MarkdownError> errors = java.util.List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.MALFORMED_LIST,
                1, 1, longContext, "fix it"
            )
        );

        String report = formatter.formatErrorReport("test", errors, 1, "response");

        // 上下文不應過長
        assertFalse(report.contains(longContext));
    }
}
```

**Step 2: 執行測試確認失敗**

```bash
mvn test -Dtest=MarkdownErrorFormatterTest
```

Expected: CLASS_NOT_FOUND

**Step 3: 實作 MarkdownErrorFormatter**

```java
package ltdjms.discord.markdown.validation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 將 Markdown 驗證錯誤格式化為結構化報告
 * 提供給 LLM 進行自我修正
 */
public final class MarkdownErrorFormatter {

    private static final int MAX_CONTEXT_LENGTH = 60;

    /**
     * 格式化錯誤報告
     * @param originalPrompt 原始用戶提示詞
     * @param errors 驗證錯誤列表
     * @param attempt 當前重試次數
     * @param fullResponse 完整的問題回應
     * @return 格式化的錯誤報告
     */
    public String formatErrorReport(
            String originalPrompt,
            List<MarkdownError> errors,
            int attempt,
            String fullResponse) {

        StringBuilder report = new StringBuilder();

        // 概述區段
        report.append("## Markdown 格式驗證失敗\n\n");
        report.append("**重試次數**: ").append(attempt).append("\n");
        report.append("**錯誤總數**: ").append(errors.size()).append("\n\n");

        // 錯誤明細區段（按類型分組）
        Map<ErrorType, List<MarkdownError>> groupedErrors = errors.stream()
            .collect(Collectors.groupingBy(MarkdownError::type));

        report.append("### 錯誤明細\n\n");

        for (Map.Entry<ErrorType, List<MarkdownError>> entry : groupedErrors.entrySet()) {
            ErrorType type = entry.getKey();
            List<MarkdownError> typeErrors = entry.getValue();

            report.append("#### ").append(getErrorTypeDisplayName(type)).append("\n");

            for (MarkdownError error : typeErrors) {
                report.append("- **行 ").append(error.lineNumber())
                      .append(", 欄 ").append(error.column()).append("****: ")
                      .append(error.suggestion()).append("\n");

                // 添加上下文（截斷過長的內容）
                String context = error.context();
                if (context.length() > MAX_CONTEXT_LENGTH) {
                    context = context.substring(0, MAX_CONTEXT_LENGTH) + "...";
                }
                report.append("  - 上下文: `").append(context).append("`\n");
            }
            report.append("\n");
        }

        return report.toString();
    }

    /**
     * 取得錯誤類型的中文顯示名稱
     */
    private String getErrorTypeDisplayName(ErrorType type) {
        return switch (type) {
            case MALFORMED_LIST -> "列表格式錯誤";
            case UNCLOSED_CODE_BLOCK -> "程式碼區塊未閉合";
            case HEADING_LEVEL_EXCEEDED -> "標題層級超限";
            case MALFORMED_TABLE -> "表格格式錯誤";
            case ESCAPE_CHARACTER_MISSING -> "缺少轉義字符";
            case DISCORD_RENDER_ISSUE -> "Discord 渲染問題";
        };
    }
}
```

**Step 4: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownErrorFormatterTest
```

Expected: PASS

**Step 5: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/validation/MarkdownErrorFormatter.java
git add src/test/java/ltdjms/discord/markdown/unit/validation/MarkdownErrorFormatterTest.java
git commit -m "feat(markdown): implement MarkdownErrorFormatter for structured error reports"
```

---

## Phase 3: 裝飾器服務實作

### Task 6: 實作 MarkdownValidatingAIChatService

**Files:**
- Create: `src/main/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatService.java`
- Test: `src/test/java/ltdjms/discord/markdown/unit/services/MarkdownValidatingAIChatServiceTest.java`

**Step 1: 先查看現有的 AIChatService 介面**

```bash
cat src/main/java/ltdjms/discord/aichat/services/AIChatService.java
```

**Step 2: 撰寫測試 - 第一次成功應直接返回**

```java
package ltdjms.discord.markdown.services;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.validation.*;
import ltdjms.discord.shared.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("MarkdownValidatingAIChatService - 第一次成功測試")
class MarkdownValidatingAIChatServiceTest_SuccessFirstTry {

    private AIChatService mockDelegate;
    private MarkdownValidator mockValidator;
    private MarkdownErrorFormatter formatter;
    private MarkdownValidatingAIChatService service;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(AIChatService.class);
        mockValidator = mock(MarkdownValidator.class);
        formatter = new MarkdownErrorFormatter();
        service = new MarkdownValidatingAIChatService(
            mockDelegate, mockValidator, true, formatter);
    }

    @Test
    @DisplayName("第一次生成的回應格式正確應直接返回")
    void validResponseOnFirstTry_shouldReturnDirectly() {
        // Given
        long guildId = 123L;
        String channelId = "456";
        String userId = "789";
        String userMessage = "解釋 Java 中的 CompletableFuture";
        String validResponse = "CompletableFuture 是 Java 8 引入的非同步編程工具。";

        Result<java.util.List<String>, ?> successResult =
            Result.ok(java.util.List.of(validResponse));

        when(mockDelegate.generateResponse(eq(guildId), eq(channelId), eq(userId), eq(userMessage)))
            .thenReturn(successResult);
        when(mockValidator.validate(validResponse))
            .thenReturn(new ValidationResult.Valid(validResponse));

        // When
        var result = service.generateResponse(guildId, channelId, userId, userMessage);

        // Then
        assertTrue(result.isOk());
        assertEquals(java.util.List.of(validResponse), result.getValue());

        // 驗證只調用一次
        verify(mockDelegate, times(1)).generateResponse(any(), any(), any(), any());
        verify(mockValidator, times(1)).validate(any());
    }
}
```

**Step 3: 執行測試確認失敗**

```bash
mvn test -Dtest=MarkdownValidatingAIChatServiceTest_SuccessFirstTry
```

Expected: CLASS_NOT_FOUND

**Step 4: 實作基本的裝飾器結構**

```java
package ltdjms.discord.markdown.services;

import java.util.List;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator.ValidationResult;
import ltdjms.discord.markdown.validation.MarkdownErrorFormatter;
import ltdjms.discord.shared.result.Result;
import ltdjms.discord.shared.result.DomainError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Markdown 驗證裝飾器
 * 包裝 AIChatService，在回應生成後驗證 Markdown 格式
 * 格式錯誤時自動重新生成
 */
public final class MarkdownValidatingAIChatService implements AIChatService {

    private static final Logger LOG = LoggerFactory.getLogger(MarkdownValidatingAIChatService.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final AIChatService delegate;
    private final MarkdownValidator validator;
    private final boolean enabled;
    private final MarkdownErrorFormatter errorFormatter;

    public MarkdownValidatingAIChatService(
            AIChatService delegate,
            MarkdownValidator validator,
            boolean enabled,
            MarkdownErrorFormatter errorFormatter) {
        this.delegate = delegate;
        this.validator = validator;
        this.enabled = enabled;
        this.errorFormatter = errorFormatter;
    }

    @Override
    public Result<List<String>, DomainError> generateResponse(
            long guildId, String channelId, String userId, String userMessage) {

        if (!enabled) {
            return delegate.generateResponse(guildId, channelId, userId, userMessage);
        }

        String originalPrompt = userMessage;
        String currentPrompt = userMessage;
        String lastResponse = null;
        int attempt = 0;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++;

            Result<List<String>, DomainError> result =
                delegate.generateResponse(guildId, channelId, userId, currentPrompt);

            if (result.isErr()) {
                return result;
            }

            String fullResponse = String.join("\n", result.getValue());
            lastResponse = fullResponse;

            ValidationResult validation = validator.validate(fullResponse);

            if (validation instanceof ValidationResult.Valid) {
                return result;
            }

            ValidationResult.Invalid invalid = (ValidationResult.Invalid) validation;
            String errorReport = errorFormatter.formatErrorReport(
                originalPrompt, invalid.errors(), attempt, fullResponse);

            currentPrompt = buildRetryPrompt(originalPrompt, errorReport);
            LOG.warn("Markdown validation failed (attempt {}/{}): {} errors",
                attempt, MAX_RETRY_ATTEMPTS, invalid.errors().size());
        }

        LOG.warn("Markdown validation exceeded max attempts, returning last response");
        return Result.ok(List.of(lastResponse));
    }

    private String buildRetryPrompt(String originalPrompt, String errorReport) {
        return String.format("""
            [系統提示：你的上一次回應存在 Markdown 格式錯誤]

            原始用戶訊息：
            %s

            格式驗證錯誤報告：
            %s

            請修正上述格式錯誤並重新生成回應。
            """, originalPrompt, errorReport);
    }
}
```

**Step 5: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownValidatingAIChatServiceTest_SuccessFirstTry
```

Expected: PASS

**Step 6: 提交**

```bash
git add src/main/java/ltdjms/discord/markdown/services/MarkdownValidatingAIChatService.java
git add src/test/java/ltdjms/discord/markdown/unit/services/MarkdownValidatingAIChatServiceTest_SuccessFirstTry.java
git commit -m "feat(markdown): implement MarkdownValidatingAIChatService decorator"
```

---

### Task 7: 擴展測試 - 重試邏輯

**Files:**
- Test: `src/test/java/ltdjms/discord/markdown/unit/services/MarkdownValidatingAIChatServiceTest_Retry.java`

**Step 1: 撰寫重試邏輯測試**

```java
package ltdjms.discord.markdown.services;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.validation.*;
import ltdjms.discord.shared.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MarkdownValidatingAIChatService - 重試邏輯測試")
class MarkdownValidatingAIChatServiceTest_Retry {

    private AIChatService mockDelegate;
    private MarkdownValidator mockValidator;
    private MarkdownErrorFormatter formatter;
    private MarkdownValidatingAIChatService service;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(AIChatService.class);
        mockValidator = mock(MarkdownValidator.class);
        formatter = new MarkdownErrorFormatter();
        service = new MarkdownValidatingAIChatService(
            mockDelegate, mockValidator, true, formatter);
    }

    @Test
    @DisplayName("格式錯誤應觸發重試並在第二次成功")
    void invalidThenValid_shouldRetryAndSucceed() {
        // Given
        long guildId = 123L;
        String channelId = "456";
        String userId = "789";
        String userMessage = "解釋 CompletableFuture";
        String invalidResponse = "```java without closing";
        String validResponse = "正確的回應";

        java.util.List<MarkdownError> errors = java.util.List.of(
            new MarkdownError(
                ErrorType.UNCLOSED_CODE_BLOCK,
                1, 1, "```java", "Add closing ```"
            )
        );

        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), eq(userMessage)))
            .thenReturn(Result.ok(java.util.List.of(invalidResponse)));
        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), contains("系統提示")))
            .thenReturn(Result.ok(java.util.List.of(validResponse)));
        when(mockValidator.validate(invalidResponse))
            .thenReturn(new ValidationResult.Invalid(errors));
        when(mockValidator.validate(validResponse))
            .thenReturn(new ValidationResult.Valid(validResponse));

        // When
        var result = service.generateResponse(guildId, channelId, userId, userMessage);

        // Then
        assertTrue(result.isOk());
        assertEquals(java.util.List.of(validResponse), result.getValue());

        // 驗證調用了兩次
        verify(mockDelegate, times(2)).generateResponse(any(), any(), any(), any());
    }

    @Test
    @DisplayName("超過重試次數應返回最後結果")
    void exceedMaxRetries_shouldReturnLastResponse() {
        // Given
        String invalidResponse = "Always invalid";
        java.util.List<MarkdownError> errors = java.util.List.of(
            new MarkdownError(ErrorType.MALFORMED_LIST, 1, 1, "bad", "fix")
        );

        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Result.ok(java.util.List.of(invalidResponse)));
        when(mockValidator.validate(anyString()))
            .thenReturn(new ValidationResult.Invalid(errors));

        // When
        var result = service.generateResponse(123L, "456", "789", "test");

        // Then
        assertTrue(result.isOk());
        assertEquals(java.util.List.of(invalidResponse), result.getValue());

        // 驗證調用了 MAX_RETRY_ATTEMPTS 次
        verify(mockDelegate, times(5)).generateResponse(any(), any(), any(), any());
    }

    @Test
    @DisplayName("委託服務錯誤應直接返回不重試")
    void delegateError_shouldReturnErrorDirectly() {
        // Given
        var domainError = new DomainError(
            DomainError.Category.UNEXPECTED_FAILURE,
            "LLM API error"
        );

        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Result.err(domainError));

        // When
        var result = service.generateResponse(123L, "456", "789", "test");

        // Then
        assertTrue(result.isErr());
        assertSame(domainError, result.getError());

        // 驗證只調用一次
        verify(mockDelegate, times(1)).generateResponse(any(), any(), any(), any());
    }
}
```

**Step 2: 執行測試確認通過**

```bash
mvn test -Dtest=MarkdownValidatingAIChatServiceTest_Retry
```

Expected: PASS

**Step 3: 提交**

```bash
git add src/test/java/ltdjms/discord/markdown/unit/services/MarkdownValidatingAIChatServiceTest_Retry.java
git commit -m "test(markdown): add retry logic tests for validating service"
```

---

## Phase 4: 依賴注入配置

### Task 8: 擴展 AIServiceConfig

**Files:**
- Modify: `src/main/java/ltdjms/discord/aichat/domain/AIServiceConfig.java`
- Test: 查看現有測試並更新

**Step 1: 查看現有配置**

```bash
cat src/main/java/ltdjms/discord/aichat/domain/AIServiceConfig.java
```

**Step 2: 添加 Markdown 驗證相關配置欄位**

在 `AIServiceConfig` record 中添加：

```java
@DefaultValue("true")
boolean enableMarkdownValidation(),

@DefaultValue("false")
boolean streamingBypassValidation(),

@DefaultValue("5")
int maxMarkdownValidationRetries()
```

**Step 3: 執行測試確認無破壞**

```bash
mvn test -Dtest=AIServiceConfigTest
```

**Step 4: 更新 application.conf**

```bash
cat src/main/resources/application.conf
```

添加：
```hocon
aichat.markdown-validation.enabled = true
aichat.markdown-validation.streaming-bypass = false
aichat.markdown-validation.max-retries = 5
```

**Step 5: 提交**

```bash
git add src/main/java/ltdjms/discord/aichat/domain/AIServiceConfig.java
git add src/main/resources/application.conf
git commit -m "feat(markdown): add markdown validation configuration options"
```

---

### Task 9: 建立 MarkdownValidationModule

**Files:**
- Create: `src/main/java/ltdjms/discord/shared/di/MarkdownValidationModule.java`
- Modify: `src/main/java/ltdjms/discord/shared/di/AIChatModule.java`（需要在提供 AIChatService 時使用裝飾器）

**Step 1: 建立目錄**

```bash
mkdir -p src/main/java/ltdjms/discord/shared/di
```

**Step 2: 撰寫 Dagger 模組**

```java
package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.LangChain4jAIChatService;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownErrorFormatter;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;

import jakarta.inject.Singleton;

import org.commonmark.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;

import java.util.List;

/**
 * Markdown 驗證功能的 Dagger 模組
 * 提供驗證器和裝飾後的 AIChatService
 */
@Module
public interface MarkdownValidationModule {

    @Provides
    @Singleton
    static Parser provideCommonMarkParser() {
        return Parser.builder()
            .extensions(List.of(
                TablesExtension.create(),
                TaskListItemsExtension.create()
            ))
            .build();
    }

    @Provides
    @Singleton
    static HtmlRenderer provideCommonMarkHtmlRenderer() {
        return HtmlRenderer.builder()
            .extensions(List.of(
                TablesExtension.create(),
                TaskListItemsExtension.create()
            ))
            .build();
    }

    @Provides
    @Singleton
    static MarkdownValidator provideMarkdownValidator(Parser parser, HtmlRenderer renderer) {
        return new CommonMarkValidator(parser, renderer);
    }

    @Provides
    @Singleton
    static MarkdownErrorFormatter provideMarkdownErrorFormatter() {
        return new MarkdownErrorFormatter();
    }

    @Provides
    @Singleton
    static AIChatService provideValidatingAIChatService(
            AIServiceConfig config,
            LangChain4jAIChatService delegateService,
            MarkdownValidator validator,
            MarkdownErrorFormatter formatter) {

        if (!config.enableMarkdownValidation()) {
            return delegateService;
        }

        return new MarkdownValidatingAIChatService(
            delegateService,
            validator,
            true,  // enabled
            formatter
        );
    }
}
```

**Step 3: 更新 AIChatModule 移除 AIChatService 提供方法**

```bash
cat src/main/java/ltdjms/discord/shared/di/AIChatModule.java
```

如果 `AIChatModule` 中有 `@Provides AIChatService` 的方法，需要移除或註解掉，因為現在同樣的簽名在 `MarkdownValidationModule` 中提供。

**Step 4: 在 AppComponent 中包含新模組**

```bash
cat src/main/java/ltdjms/discord/shared/di/AppComponent.java
```

確認 `@Component` includes 包含 `MarkdownValidationModule`。

**Step 5: 執行建置確認 Dagger 正確生成**

```bash
mvn clean compile
```

Expected: 無編譯錯誤，Dagger 正確生成組件

**Step 6: 提交**

```bash
git add src/main/java/ltdjms/discord/shared/di/MarkdownValidationModule.java
git add src/main/java/ltdjms/discord/shared/di/AIChatModule.java
git add src/main/java/ltdjms/discord/shared/di/AppComponent.java
git commit -m "feat(markdown): add MarkdownValidationModule for DI setup"
```

---

## Phase 5: 整合測試

### Task 10: 撰寫整合測試

**Files:**
- Create: `src/test/java/ltdjms/discord/markdown/integration/MarkdownValidationIntegrationTest.java`

**Step 1: 建立目錄**

```bash
mkdir -p src/test/java/ltdjms/discord/markdown/integration
```

**Step 2: 撰寫整合測試**

```java
package ltdjms.discord.markdown.integration;

import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownErrorFormatter;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.shared.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Markdown 驗證整合測試")
class MarkdownValidationIntegrationTest {

    @Test
    @DisplayName("完整流程：格式錯誤重試到成功")
    void fullFlow_invalidThenValid_shouldSucceed() {
        // Given
        AIChatService mockDelegate = mock(AIChatService.class);

        String invalidResponse = """
            這是程式碼：

            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            """;  // 缺少閉合標記

        String validResponse = """
            這是程式碼：

            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            ```

            完成。
            """;

        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), contains("解釋 Java")))
            .thenReturn(Result.ok(java.util.List.of(invalidResponse)));
        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), contains("系統提示")))
            .thenReturn(Result.ok(java.util.List.of(validResponse)));

        MarkdownValidator validator = new CommonMarkValidator();
        MarkdownErrorFormatter formatter = new MarkdownErrorFormatter();

        MarkdownValidatingAIChatService service = new MarkdownValidatingAIChatService(
            mockDelegate, validator, true, formatter);

        // When
        var result = service.generateResponse(123L, "456", "789", "解釋 Java 中的 main 方法");

        // Then
        assertTrue(result.isOk());
        assertEquals(java.util.List.of(validResponse), result.getValue());

        verify(mockDelegate, times(2)).generateResponse(any(), any(), any(), any());
    }

    @Test
    @DisplayName("真實驗證器：正確的 Markdown 應通過")
    void realValidator_validMarkdown_shouldPass() {
        // Given
        AIChatService mockDelegate = mock(AIChatService.class);
        String validMarkdown = """
            # 標題

            ## 子標題

            這是一段文字。

            - 列表項目一
            - 列表項目二

            ```java
            public class Test {}
            ```

            | A | B |
            |---|---|
            | 1 | 2 |
            """;

        when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Result.ok(java.util.List.of(validMarkdown)));

        MarkdownValidator validator = new CommonMarkValidator();
        MarkdownValidatingAIChatService service = new MarkdownValidatingAIChatService(
            mockDelegate, validator, true, new MarkdownErrorFormatter());

        // When
        var result = service.generateResponse(123L, "456", "789", "test");

        // Then
        assertTrue(result.isOk());
        assertEquals(java.util.List.of(validMarkdown), result.getValue());
        verify(mockDelegate, times(1)).generateResponse(any(), any(), any(), any());
    }
}
```

**Step 3: 執行整合測試**

```bash
mvn test -Dtest=MarkdownValidationIntegrationTest
```

Expected: PASS

**Step 4: 提交**

```bash
git add src/test/java/ltdjms/discord/markdown/integration/MarkdownValidationIntegrationTest.java
git commit -m "test(markdown): add integration tests for markdown validation"
```

---

## Phase 6: 驗證與文件

### Task 11: 執行完整測試套件

**Step 1: 執行所有單元測試**

```bash
mvn test
```

Expected: 所有測試通過

**Step 2: 執行整合測試**

```bash
mvn test -Dtest="**/integration/**"
```

Expected: 所有整合測試通過

**Step 3: 檢查覆蓋率**

```bash
mvn clean test jacoco:report
```

目標: 新增程式碼覆蓋率 ≥ 80%

**Step 4: 如果覆蓋率不足，補充測試**

根據 `target/site/jacoco/index.html` 報告，找出未覆蓋的分支並添加測試。

---

### Task 12: 更新相關文件

**Files:**
- Modify: `docs/architecture/overview.md`（添加 Markdown 驗證層到架構圖）
- Modify: `docs/modules/aichat.md`（添加驗證器說明）

**Step 1: 更新架構概覽文件**

在 `docs/architecture/overview.md` 的架構圖中添加 Markdown 驗證層：

```markdown
## AI 聊天服務架構

```
Command Handler 層
        ↓ 調用 generateResponse()
MarkdownValidatingAIChatService (驗證裝飾器)
        ↓ 驗證失敗時重試
        ↓ 驗證通過時返回
LangChain4jAIChatService (被裝飾者)
        ↓
      LLM
```
```

**Step 2: 建立或更新模組文件**

```bash
# 如果文件不存在則建立
touch docs/modules/markdown-validation.md
```

添加內容：

```markdown
# Markdown 格式驗證模組

## 概述

本模組提供 Markdown 格式驗證功能，確保 LLM 生成的回應符合 Markdown 語法規範並能在 Discord 正確渲染。

## 組件

- `MarkdownValidator`: 驗證器介面
- `CommonMarkValidator`: 使用 CommonMark Java 的實作
- `MarkdownValidatingAIChatService`: 裝飾器，自動重試格式錯誤的回應
- `MarkdownErrorFormatter`: 格式化錯誤報告給 LLM

## 配置

在 `application.conf` 中：

```hocon
aichat.markdown-validation.enabled = true
aichat.markdown-validation.streaming-bypass = false
aichat.markdown-validation.max-retries = 5
```

## 錯誤類型

- `MALFORMED_LIST`: 列表格式錯誤
- `UNCLOSED_CODE_BLOCK`: 程式碼區塊未閉合
- `HEADING_LEVEL_EXCEEDED`: 標題層級超過 H6
- `MALFORMED_TABLE`: 表格格式錯誤
- `ESCAPE_CHARACTER_MISSING`: 缺少轉義字符
- `DISCORD_RENDER_ISSUE`: Discord 特定渲染問題
```

**Step 3: 提交文件更新**

```bash
git add docs/architecture/overview.md
git add docs/modules/markdown-validation.md
git commit -m "docs(markdown): add architecture documentation for markdown validation"
```

---

## 最終驗證

### Task 13: 完整建置與測試

**Step 1: 清除並重新建置**

```bash
mvn clean verify
```

Expected: 所有測試通過，覆蓋率 ≥ 80%

**Step 2: 檢查 Docker 建置**

```bash
make build
```

Expected: 成功建置 Docker 映像

**Step 3: 提交最終變更**

```bash
git status
git add .
git commit -m "feat(markdown): complete markdown validation feature implementation"
```

---

## 執行摘要

本計畫總共包含 **13 個任務**，分為 6 個階段：

1. **前置準備** (Task 0): 添加 Maven 依賴
2. **核心驗證器** (Tasks 1-4): 實作 MarkdownValidator 介面與 CommonMarkValidator
3. **錯誤格式化** (Task 5): 實作 MarkdownErrorFormatter
4. **裝飾器服務** (Tasks 6-7): 實作 MarkdownValidatingAIChatService 與重試邏輯
5. **依賴注入** (Tasks 8-9): 擴展配置與建立 Dagger 模組
6. **整合測試與文件** (Tasks 10-13): 整合測試、文件更新、最終驗證

每個任務都遵循 **TDD 紅-綠-重構循環**，預計每個任務約 **5-15 分鐘**完成。
