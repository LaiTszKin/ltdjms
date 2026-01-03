package ltdjms.discord.markdown.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MarkdownValidatorTest {

  @Test
  void ValidationResult_shouldHaveValidAndInvalidStates() {
    // Valid 狀態包含原始 markdown
    MarkdownValidator.ValidationResult.Valid valid =
        new MarkdownValidator.ValidationResult.Valid("test");
    assertEquals("test", valid.markdown());

    // Invalid 狀態包含錯誤列表
    java.util.List<MarkdownValidator.MarkdownError> errors =
        java.util.List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.MALFORMED_LIST, 1, 1, "context", "suggestion"));
    MarkdownValidator.ValidationResult.Invalid invalid =
        new MarkdownValidator.ValidationResult.Invalid(errors);
    assertEquals(1, invalid.errors().size());
  }

  @Test
  void MarkdownError_shouldContainAllRequiredFields() {
    MarkdownValidator.MarkdownError error =
        new MarkdownValidator.MarkdownError(
            MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK,
            10,
            5,
            "```java without closing",
            "Add closing ```");

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
    assertTrue(
        java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK));
    assertTrue(
        java.util.Arrays.asList(types)
            .contains(MarkdownValidator.ErrorType.HEADING_LEVEL_EXCEEDED));
    assertTrue(
        java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.MALFORMED_TABLE));
    assertTrue(
        java.util.Arrays.asList(types)
            .contains(MarkdownValidator.ErrorType.ESCAPE_CHARACTER_MISSING));
    assertTrue(
        java.util.Arrays.asList(types).contains(MarkdownValidator.ErrorType.DISCORD_RENDER_ISSUE));
  }
}
