package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.PromptSection;
import ltdjms.discord.aichat.domain.SystemPrompt;

/** 測試 {@link SystemPrompt} 的行為。 */
class SystemPromptTest {

  @Test
  void testEmptyPrompt_toCombinedString_returnsEmptyString() {
    // Given
    SystemPrompt emptyPrompt = SystemPrompt.empty();

    // When
    String result = emptyPrompt.toCombinedString();

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void testEmptyPrompt_isEmpty_returnsTrue() {
    // Given
    SystemPrompt emptyPrompt = SystemPrompt.empty();

    // When & Then
    assertThat(emptyPrompt.isEmpty()).isTrue();
    assertThat(emptyPrompt.sectionCount()).isZero();
  }

  @Test
  void testSingleSection_toCombinedString_returnsFormattedContent() {
    // Given
    PromptSection section = new PromptSection("PERSONALITY", "You are a helpful bot.");
    SystemPrompt prompt = SystemPrompt.of(section);

    // When
    String result = prompt.toCombinedString();

    // Then
    assertThat(result).isEqualTo("=== PERSONALITY ===\nYou are a helpful bot.");
  }

  @Test
  void testSingleSection_isEmpty_returnsFalse() {
    // Given
    PromptSection section = new PromptSection("PERSONALITY", "You are a helpful bot.");
    SystemPrompt prompt = SystemPrompt.of(section);

    // When & Then
    assertThat(prompt.isEmpty()).isFalse();
    assertThat(prompt.sectionCount()).isOne();
  }

  @Test
  void testSingleSection_withEmptyContent_returnsFormattedTitle() {
    // Given
    PromptSection section = new PromptSection("RULES", "");
    SystemPrompt prompt = SystemPrompt.of(section);

    // When
    String result = prompt.toCombinedString();

    // Then
    assertThat(result).isEqualTo("=== RULES ===");
  }

  @Test
  void testOfSingleSection_createsPromptWithOneSection() {
    // Given
    PromptSection section = new PromptSection("FORMAT", "Use markdown.");

    // When
    SystemPrompt prompt = SystemPrompt.of(section);

    // Then
    assertThat(prompt.sectionCount()).isOne();
    assertThat(prompt.sections()).containsExactly(section);
  }

  @Test
  void testSections_returnsUnmodifiableList() {
    // Given
    PromptSection section = new PromptSection("TEST", "Content");
    SystemPrompt prompt = SystemPrompt.of(section);

    // When & Then - 驗證返回的 sections 列表是不可修改的
    assertThat(prompt.sections()).isNotNull();
    // 嘗試修改應該拋出 UnsupportedOperationException
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> prompt.sections().add(section))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testMultipleSections_toCombinedString_returnsAllFormatted() {
    // Given
    PromptSection section1 = new PromptSection("RULES", "Be helpful");
    PromptSection section2 = new PromptSection("FORMAT", "Use markdown");
    SystemPrompt prompt = SystemPrompt.of(List.of(section1, section2));

    // When
    String result = prompt.toCombinedString();

    // Then
    assertThat(result).contains("=== RULES ===");
    assertThat(result).contains("=== FORMAT ===");
    assertThat(result).contains("Be helpful");
    assertThat(result).contains("Use markdown");
  }

  @Test
  void testMultipleSections_withSectionsContainingAllSeparators_hasCorrectFormat() {
    // Given
    PromptSection section1 = new PromptSection("PERSONALITY", "You are helpful");
    PromptSection section2 = new PromptSection("RULES", "Be concise");
    SystemPrompt prompt = SystemPrompt.of(List.of(section1, section2));

    // When
    String result = prompt.toCombinedString();

    // Then - 區間之間應該有兩個換行符分隔
    assertThat(result)
        .isEqualTo("=== PERSONALITY ===\nYou are helpful\n\n=== RULES ===\nBe concise");
  }

  @Test
  void testMultipleSections_sectionCount_returnsCorrectCount() {
    // Given
    PromptSection section1 = new PromptSection("A", "Content A");
    PromptSection section2 = new PromptSection("B", "Content B");
    PromptSection section3 = new PromptSection("C", "Content C");
    SystemPrompt prompt = SystemPrompt.of(List.of(section1, section2, section3));

    // When & Then
    assertThat(prompt.sectionCount()).isEqualTo(3);
  }

  @Test
  void testOfList_createsPromptWithMultipleSections() {
    // Given
    List<PromptSection> sections =
        List.of(
            new PromptSection("FORMAT", "Use markdown"), new PromptSection("RULES", "Be concise"));

    // When
    SystemPrompt prompt = SystemPrompt.of(sections);

    // Then
    assertThat(prompt.sectionCount()).isEqualTo(2);
    assertThat(prompt.sections()).hasSize(2);
  }
}
