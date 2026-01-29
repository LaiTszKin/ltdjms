package ltdjms.discord.markdown.unit.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator;

@DisplayName("CommonMarkValidator - 列表格式驗證")
class CommonMarkValidatorTest_Lists {

  private final MarkdownValidator validator = new CommonMarkValidator();

  @Test
  @DisplayName("正確的無序列表應通過驗證")
  void validUnorderedList_shouldPass() {
    String markdown =
        """
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
    String markdown =
        """
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
    String markdown =
        """
        * 項目一
        * 項目二
        * 項目三
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("純強調語法不應被當成列表錯誤")
  void emphasisOnly_shouldPass() {
    String markdown =
        """
        **關於機器人功能：**
        *斜體內容*
        ***加強斜體***
        **段落標題**：
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("缺少空格的無序列表應檢測為格式錯誤")
  void unorderedListWithoutSpace_shouldFail() {
    String markdown =
        """
        -項目一
        - 項目二
        *沒有空格
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;
    assertFalse(invalid.errors().isEmpty(), "應該檢測到列表格式錯誤");
  }

  @Test
  @DisplayName("缺少空格的有序列表應檢測為格式錯誤")
  void orderedListWithoutSpace_shouldFail() {
    String markdown =
        """
        1.第一步
        2. 第二步
        10.沒有空格
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;
    assertFalse(invalid.errors().isEmpty(), "應該檢測到列表格式錯誤");
  }

  @Test
  @DisplayName("分隔線應檢測為 Discord 渲染問題")
  void horizontalRule_shouldDetectDiscordIssue() {
    String markdown =
        """
        ---
        ***
        ___
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;
    assertTrue(
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE),
        "應檢測到 Discord 不支援分隔線");
  }

  @Test
  @DisplayName("程式碼區塊中的列表不應被檢測為格式錯誤")
  void listInCodeBlock_shouldNotFail() {
    String markdown =
        """
        ```
        -沒有空格也沒關係
        1.這是程式碼
        ```
        -這才是真正的列表錯誤
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
    MarkdownValidator.ValidationResult.Invalid invalid =
        (MarkdownValidator.ValidationResult.Invalid) result;
    assertFalse(invalid.errors().isEmpty(), "應該檢測到程式碼區塊外的列表格式錯誤");
  }

  @Nested
  @DisplayName("標題中的列表標記檢測")
  class HeadingWithListMarkerTests {

    @Test
    @DisplayName("標題中包含無序列表標記應被檢測為錯誤")
    void headingWithUnorderedListMarker_shouldFail() {
      String markdown =
          """
          ### - 標題包含列表標記
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
      MarkdownValidator.ValidationResult.Invalid invalid =
          (MarkdownValidator.ValidationResult.Invalid) result;
      assertFalse(invalid.errors().isEmpty(), "應該檢測到標題中的列表標記");
      assertTrue(
          invalid.errors().stream()
              .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_CONTAINS_LIST_MARKER),
          "應該是 HEADING_CONTAINS_LIST_MARKER 錯誤");
    }

    @Test
    @DisplayName("標題中包含有序列表標記應被檢測為錯誤")
    void headingWithOrderedListMarker_shouldFail() {
      String markdown =
          """
          ## 1. 標題包含數字標記
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
      MarkdownValidator.ValidationResult.Invalid invalid =
          (MarkdownValidator.ValidationResult.Invalid) result;
      assertFalse(invalid.errors().isEmpty(), "應該檢測到標題中的有序列表標記");
      assertTrue(
          invalid.errors().stream()
              .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_CONTAINS_LIST_MARKER),
          "應該是 HEADING_CONTAINS_LIST_MARKER 錯誤");
    }

    @Test
    @DisplayName("標題中行內列表標記應被檢測為錯誤")
    void headingWithInlineListMarker_shouldFail() {
      String markdown =
          """
          ## 標題- 項目一
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
      MarkdownValidator.ValidationResult.Invalid invalid =
          (MarkdownValidator.ValidationResult.Invalid) result;
      assertFalse(invalid.errors().isEmpty(), "應該檢測到標題中的列表標記");
      assertTrue(
          invalid.errors().stream()
              .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.HEADING_CONTAINS_LIST_MARKER),
          "應該是 HEADING_CONTAINS_LIST_MARKER 錯誤");
    }

    @Test
    @DisplayName("標題中包含數字但非列表格式應通過")
    void headingWithNumber_shouldPass() {
      String markdown =
          """
          ### Chapter 1 簡介
          ## 2023年報告
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }
  }

  @Nested
  @DisplayName("嵌套列表驗證")
  class NestedListTests {

    @Test
    @DisplayName("正確的嵌套列表應通過驗證")
    void validNestedList_shouldPass() {
      String markdown =
          """
          - 第一層項目
              - 第二層項目
                  - 第三層項目
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("正確的有序嵌套列表應通過驗證")
    void validNestedOrderedList_shouldPass() {
      String markdown =
          """
          1. 第一層
              1. 第二層
                  1. 第三層
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("縮排不足的嵌套列表應被檢測為錯誤")
    void insufficientNestedIndent_shouldFail() {
      String markdown =
          """
          - 第一層項目
           - 第二層項目（縮排不足）
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
      MarkdownValidator.ValidationResult.Invalid invalid =
          (MarkdownValidator.ValidationResult.Invalid) result;
      assertFalse(invalid.errors().isEmpty(), "應該檢測到嵌套列表縮排不足");
      assertTrue(
          invalid.errors().stream()
              .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.MALFORMED_NESTED_LIST),
          "應該是 MALFORMED_NESTED_LIST 錯誤");
    }

    @Test
    @DisplayName("嵌套列表縮排非 4 個空格應被檢測為錯誤")
    void nestedIndentNotFourSpaces_shouldFail() {
      String markdown = "- 第一層項目\n      - 第二層項目（縮排 6 個空格）";

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
      MarkdownValidator.ValidationResult.Invalid invalid =
          (MarkdownValidator.ValidationResult.Invalid) result;
      assertFalse(invalid.errors().isEmpty(), "應該檢測到嵌套列表縮排錯誤");
      assertTrue(
          invalid.errors().stream()
              .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.MALFORMED_NESTED_LIST),
          "應該是 MALFORMED_NESTED_LIST 錯誤");
    }

    @Test
    @DisplayName("嵌套列表中缺少空格應被檢測為錯誤")
    void nestedListWithoutSpace_shouldFail() {
      String markdown =
          """
          - 第一層項目
              -第二層沒有空格
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Invalid.class, result);
      MarkdownValidator.ValidationResult.Invalid invalid =
          (MarkdownValidator.ValidationResult.Invalid) result;
      assertFalse(invalid.errors().isEmpty(), "應該檢測到嵌套列表缺少空格");
      assertTrue(
          invalid.errors().stream()
              .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.MALFORMED_LIST),
          "應該是 MALFORMED_LIST 錯誤");
    }

    @Test
    @DisplayName("多重嵌套列表應正確驗證")
    void multiLevelNestedList_shouldValidateCorrectly() {
      String markdown =
          """
          - 第一層
              - 第二層
                  - 第三層
              - 回到第二層（縮排正確）
          """;

      MarkdownValidator.ValidationResult result = validator.validate(markdown);

      assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
    }
  }
}
