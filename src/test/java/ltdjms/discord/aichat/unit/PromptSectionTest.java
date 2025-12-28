package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.PromptSection;

/** 測試 {@link PromptSection} 的行為。 */
class PromptSectionTest {

  @Test
  void testToFormattedString_returnsCorrectFormat() {
    // Given
    PromptSection section = new PromptSection("PERSONALITY", "You are a helpful bot.");

    // When
    String result = section.toFormattedString();

    // Then
    assertThat(result).isEqualTo("=== PERSONALITY ===\nYou are a helpful bot.");
  }

  @Test
  void testToFormattedString_withMultilineContent_preservesNewlines() {
    // Given
    PromptSection section = new PromptSection("RULES", "Rule 1\nRule 2\nRule 3");

    // When
    String result = section.toFormattedString();

    // Then
    assertThat(result).isEqualTo("=== RULES ===\nRule 1\nRule 2\nRule 3");
  }

  @Test
  void testEmptySection_toFormattedString_returnsEmptyString() {
    // Given
    PromptSection section = PromptSection.empty();

    // When
    String result = section.toFormattedString();

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void testEmptySection_isEmpty_returnsTrue() {
    // Given
    PromptSection section = PromptSection.empty();

    // When & Then
    assertThat(section.isEmpty()).isTrue();
    assertThat(section.isContentEmpty()).isTrue();
  }

  @Test
  void testSectionWithEmptyTitle_isEmpty_returnsFalse() {
    // Given
    PromptSection section = new PromptSection("", "Content");

    // When
    boolean isEmpty = section.isEmpty();

    // Then
    // 只有標題和內容都為空時才返回 true
    assertThat(isEmpty).isFalse();
  }

  @Test
  void testSectionWithEmptyContent_isContentEmpty_returnsTrue() {
    // Given
    PromptSection section = new PromptSection("TITLE", "");

    // When
    boolean isContentEmpty = section.isContentEmpty();

    // Then
    assertThat(isContentEmpty).isTrue();
  }

  @Test
  void testSectionWithBothEmpty_isEmpty_returnsTrue() {
    // Given
    PromptSection section = new PromptSection("", "");

    // When & Then
    assertThat(section.isEmpty()).isTrue();
    assertThat(section.toFormattedString()).isEmpty();
  }
}
