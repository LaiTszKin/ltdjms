package ltdjms.discord.markdown.unit.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator.ValidationResult;

@DisplayName("CommonMarkValidator - 程式碼區塊驗證")
class CommonMarkValidatorTest_CodeBlocks {

  private final MarkdownValidator validator =
      new ltdjms.discord.markdown.validation.CommonMarkValidator();

  @Test
  @DisplayName("正確的程式碼區塊應通過驗證")
  void validCodeBlock_shouldPass() {
    String markdown =
        """
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

    ValidationResult result = validator.validate(markdown);

    assertInstanceOf(ValidationResult.Valid.class, result);
  }

  @Test
  @DisplayName("未閉合的程式碼區塊應檢測為錯誤")
  void unclosedCodeBlock_shouldDetectError() {
    String markdown =
        """
        這是程式碼範例：

        ```java
        public class Test {
            public static void main(String[] args) {
                System.out.println("Hello");
            }
        }

        結束。（缺少結束標記）
        """;

    ValidationResult result = validator.validate(markdown);

    assertInstanceOf(ValidationResult.Invalid.class, result);
    ValidationResult.Invalid invalid = (ValidationResult.Invalid) result;

    boolean hasCodeBlockError =
        invalid.errors().stream()
            .anyMatch(e -> e.type() == MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK);
    assertTrue(hasCodeBlockError, "應檢測到未閉合的程式碼區塊");
  }
}
