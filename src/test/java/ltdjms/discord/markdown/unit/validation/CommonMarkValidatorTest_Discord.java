package ltdjms.discord.markdown.unit.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator;

@DisplayName("CommonMarkValidator - Discord 特定檢查")
class CommonMarkValidatorTest_Discord {

  private final MarkdownValidator validator = new CommonMarkValidator();

  @Test
  @DisplayName("標題層級不超過 H6 應通過")
  void headingWithinLimit_shouldPass() {
    String markdown =
        """
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
    String markdown =
        """
        ####### 超過限制的標題
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasHeadingError =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_LEVEL_EXCEEDED);
    assertTrue(hasHeadingError, "應檢測到標題層級超限");
  }

  @Test
  @DisplayName("標題格式缺少空格應檢測為錯誤")
  void headingWithoutSpace_shouldDetectError() {
    String markdown =
        """
        ###abc
        ##def
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasHeadingFormatError =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_FORMAT);
    assertTrue(hasHeadingFormatError, "應檢測到標題格式錯誤");
    assertEquals(2, invalid.errors().size(), "應檢測到兩個標題格式錯誤");
  }

  @Test
  @DisplayName("行內標題應檢測為錯誤")
  void inlineHeading_shouldDetectError() {
    String markdown =
        """
        前文##標題
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasHeadingFormatError =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_FORMAT);
    assertTrue(hasHeadingFormatError, "應檢測到行內標題格式錯誤");
  }

  @Test
  @DisplayName("標題格式正確應通過")
  void headingWithSpace_shouldPass() {
    String markdown =
        """
        ### abc
        ## def
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("標題後面只有井號應通過")
  void headingOnlyHashes_shouldPass() {
    String markdown =
        """
        ###
        ##
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("正確的表格格式應檢測為 Discord 渲染問題")
  void validTable_shouldDetectDiscordIssue() {
    String markdown =
        """
        | 欄位 A | 欄位 B | 欄位 C |
        |--------|--------|--------|
        | 值 1   | 值 2   | 值 3   |
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasDiscordIssue =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE);
    assertTrue(hasDiscordIssue, "應檢測到 Discord 不支援表格");
  }

  @Test
  @DisplayName("水平分隔線應檢測為 Discord 渲染問題")
  void horizontalRule_shouldDetectDiscordIssue() {
    String markdown =
        """
        前言

        ---

        後記
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasDiscordIssue =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE);
    assertTrue(hasDiscordIssue, "應檢測到 Discord 不支援水平分隔線");
  }

  @Test
  @DisplayName("星號水平分隔線應檢測為 Discord 渲染問題")
  void asteriskHorizontalRule_shouldDetectDiscordIssue() {
    String markdown =
        """
        前言

        ***

        後記
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasDiscordIssue =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE);
    assertTrue(hasDiscordIssue, "應檢測到 Discord 不支援水平分隔線");
  }

  @Test
  @DisplayName("Task List 應檢測為 Discord 渲染問題")
  void taskList_shouldDetectDiscordIssue() {
    String markdown =
        """
        - [x] 已完成的項目
        - [ ] 未完成的項目
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasDiscordIssue =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE);
    assertTrue(hasDiscordIssue, "應檢測到 Discord 不支援 Task List");
  }

  @Test
  @DisplayName("粗體使用底線應檢測為 Discord 渲染問題")
  void boldWithUnderscore_shouldDetectDiscordIssue() {
    String markdown =
        """
        這是 __粗體文字__ 的範例
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;

    boolean hasDiscordIssue =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE);
    assertTrue(hasDiscordIssue, "應檢測到 Discord 粗體應使用星號");
  }

  @Test
  @DisplayName("粗體使用星號應通過")
  void boldWithAsterisk_shouldPass() {
    String markdown =
        """
        這是 **粗體文字** 的範例
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("程式碼區塊中的表格語法不應被檢測為錯誤")
  void tableInCodeBlock_shouldNotDetectError() {
    String markdown =
        """
        ```
        | 欄位 A | 欄位 B |
        |--------|--------|
        | 值 1   | 值 2   |
        ```
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    // 程式碼區塊中的表格不應該被檢測為 Discord 渲染問題
    // 應該只檢測程式碼區塊是否閉合（已閉合，所以通過）
    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("程式碼區塊中的底線不應被檢測為錯誤")
  void underscoreInCodeBlock_shouldNotDetectError() {
    String markdown =
        """
        ```
        __variable_name__
        ```
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    // 程式碼區塊中的底線不應該被檢測為 Discord 渲染問題
    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("程式碼區塊中的水平分隔線不應被檢測為錯誤")
  void horizontalRuleInCodeBlock_shouldNotDetectError() {
    String markdown =
        """
        ```
        ---
        ```
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    // 程式碼區塊中的水平分隔線不應該被檢測為 Discord 渲染問題
    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }
}
