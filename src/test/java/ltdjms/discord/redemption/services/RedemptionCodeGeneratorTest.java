package ltdjms.discord.redemption.services;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for RedemptionCodeGenerator. */
class RedemptionCodeGeneratorTest {

  private RedemptionCodeGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new RedemptionCodeGenerator();
  }

  @Nested
  @DisplayName("Code generation")
  class CodeGenerationTests {

    @Test
    @DisplayName("should generate code of correct length")
    void shouldGenerateCodeOfCorrectLength() {
      // When
      String code = generator.generate();

      // Then
      assertThat(code).hasSize(RedemptionCodeGenerator.CODE_LENGTH);
    }

    @Test
    @DisplayName("should generate uppercase alphanumeric code")
    void shouldGenerateUppercaseAlphanumericCode() {
      // When
      String code = generator.generate();

      // Then
      assertThat(code).matches("^[A-Z2-9]+$");
    }

    @Test
    @DisplayName("should not contain confusing characters")
    void shouldNotContainConfusingCharacters() {
      // Generate many codes to ensure no confusing chars
      for (int i = 0; i < 100; i++) {
        String code = generator.generate();

        // Should not contain 0, O, 1, I, L
        assertThat(code).doesNotContain("0", "O", "1", "I", "L");
      }
    }

    @Test
    @DisplayName("should generate unique codes")
    void shouldGenerateUniqueCodes() {
      // Given
      Set<String> codes = new HashSet<>();
      int count = 1000;

      // When
      for (int i = 0; i < count; i++) {
        codes.add(generator.generate());
      }

      // Then - all codes should be unique
      assertThat(codes).hasSize(count);
    }
  }

  @Nested
  @DisplayName("Code validation")
  class CodeValidationTests {

    @Test
    @DisplayName("should validate correct format")
    void shouldValidateCorrectFormat() {
      // Uses only valid characters (no 0, 1, I, L, O)
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD2345EFGH6789")).isTrue();
    }

    @Test
    @DisplayName("should validate lowercase as valid")
    void shouldValidateLowercaseAsValid() {
      // Uses only valid characters (no 0, 1, i, l, o)
      assertThat(RedemptionCodeGenerator.isValidFormat("abcd2345efgh6789")).isTrue();
    }

    @Test
    @DisplayName("should reject null code")
    void shouldRejectNullCode() {
      assertThat(RedemptionCodeGenerator.isValidFormat(null)).isFalse();
    }

    @Test
    @DisplayName("should reject wrong length")
    void shouldRejectWrongLength() {
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD2345")).isFalse();
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD2345EFGH67890")).isFalse();
    }

    @Test
    @DisplayName("should reject code with confusing characters")
    void shouldRejectCodeWithConfusingCharacters() {
      // Contains 0 (zero)
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD0234EFGH5678")).isFalse();
      // Contains 1 (one)
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD1234EFGH5678")).isFalse();
      // Contains O (capital o)
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD2345OFGH6789")).isFalse();
      // Contains I (capital i)
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD2345IFGH6789")).isFalse();
      // Contains L (capital L)
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD2345LFGH6789")).isFalse();
    }

    @Test
    @DisplayName("should reject code with invalid characters")
    void shouldRejectCodeWithInvalidCharacters() {
      // Contains special character
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD-234EFGH5678")).isFalse();
      // Contains space
      assertThat(RedemptionCodeGenerator.isValidFormat("ABCD 234EFGH5678")).isFalse();
    }

    @Test
    @DisplayName("should validate generated codes")
    void shouldValidateGeneratedCodes() {
      // Generate many codes and validate them
      for (int i = 0; i < 100; i++) {
        String code = generator.generate();
        assertThat(RedemptionCodeGenerator.isValidFormat(code))
            .as("Generated code should be valid: " + code)
            .isTrue();
      }
    }
  }
}
