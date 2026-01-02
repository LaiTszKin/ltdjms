package ltdjms.discord.markdown.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
  @DisplayName("正確的表格格式應通過")
  void validTable_shouldPass() {
    String markdown =
        """
        | 欄位 A | 欄位 B | 欄位 C |
        |--------|--------|--------|
        | 值 1   | 值 2   | 值 3   |
        """;

    MarkdownValidator.ValidationResult result = validator.validate(markdown);

    assertInstanceOf(MarkdownValidator.ValidationResult.Valid.class, result);
  }
}
