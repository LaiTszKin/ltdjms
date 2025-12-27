package ltdjms.discord.redemption.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for RedemptionCode domain model. */
class RedemptionCodeTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_PRODUCT_ID = 1L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Nested
  @DisplayName("RedemptionCode creation")
  class RedemptionCodeCreationTests {

    @Test
    @DisplayName("should create code without expiration")
    void shouldCreateCodeWithoutExpiration() {
      // When
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // Then
      assertThat(code.id()).isNull();
      assertThat(code.code()).isEqualTo("ABCD1234EFGH5678");
      assertThat(code.productId()).isEqualTo(TEST_PRODUCT_ID);
      assertThat(code.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(code.expiresAt()).isNull();
      assertThat(code.redeemedBy()).isNull();
      assertThat(code.redeemedAt()).isNull();
      assertThat(code.isRedeemed()).isFalse();
      assertThat(code.isExpired()).isFalse();
      assertThat(code.isValid()).isTrue();
      assertThat(code.quantity()).isEqualTo(1); // Default quantity
    }

    @Test
    @DisplayName("should create code with expiration")
    void shouldCreateCodeWithExpiration() {
      // Given
      Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

      // When
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, expiresAt);

      // Then
      assertThat(code.expiresAt()).isEqualTo(expiresAt);
      assertThat(code.isExpired()).isFalse();
      assertThat(code.isValid()).isTrue();
    }

    @Test
    @DisplayName("should convert code to uppercase")
    void shouldConvertCodeToUppercase() {
      // When
      RedemptionCode code =
          RedemptionCode.create("abcd1234efgh5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // Then
      assertThat(code.code()).isEqualTo("ABCD1234EFGH5678");
    }

    @Test
    @DisplayName("should reject null code")
    void shouldRejectNullCode() {
      assertThatThrownBy(() -> RedemptionCode.create(null, TEST_PRODUCT_ID, TEST_GUILD_ID, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject blank code")
    void shouldRejectBlankCode() {
      assertThatThrownBy(() -> RedemptionCode.create("   ", TEST_PRODUCT_ID, TEST_GUILD_ID, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blank");
    }
  }

  @Nested
  @DisplayName("Redemption operations")
  class RedemptionOperationsTests {

    @Test
    @DisplayName("should mark code as redeemed")
    void shouldMarkCodeAsRedeemed() {
      // Given
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // When
      RedemptionCode redeemed = code.withRedeemed(TEST_USER_ID);

      // Then
      assertThat(redeemed.isRedeemed()).isTrue();
      assertThat(redeemed.redeemedBy()).isEqualTo(TEST_USER_ID);
      assertThat(redeemed.redeemedAt()).isNotNull();
      assertThat(redeemed.isValid()).isFalse();
    }

    @Test
    @DisplayName("should reject redemption of already redeemed code")
    void shouldRejectRedemptionOfAlreadyRedeemedCode() {
      // Given
      Instant now = Instant.now();
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              TEST_PRODUCT_ID,
              TEST_GUILD_ID,
              null,
              TEST_USER_ID,
              now,
              now,
              null,
              1);

      // When/Then
      assertThatThrownBy(() -> code.withRedeemed(TEST_USER_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already been redeemed");
    }
  }

  @Nested
  @DisplayName("Invalidation operations")
  class InvalidationOperationsTests {

    @Test
    @DisplayName("should mark code as invalidated")
    void shouldMarkCodeAsInvalidated() {
      // Given
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // When
      RedemptionCode invalidated = code.withInvalidated();

      // Then
      assertThat(invalidated.isInvalidated()).isTrue();
      assertThat(invalidated.invalidatedAt()).isNotNull();
      assertThat(invalidated.productId()).isNull(); // Product ID should be null after invalidation
      assertThat(invalidated.isValid()).isFalse();
    }

    @Test
    @DisplayName("should reject invalidation of already invalidated code")
    void shouldRejectInvalidationOfAlreadyInvalidatedCode() {
      // Given
      Instant now = Instant.now();
      Instant redeemedAt = now.minusSeconds(100);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              null,
              TEST_GUILD_ID,
              null,
              TEST_USER_ID,
              redeemedAt,
              now,
              now,
              1);

      // When/Then
      assertThatThrownBy(() -> code.withInvalidated())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already been invalidated");
    }

    @Test
    @DisplayName("should detect invalidated code")
    void shouldDetectInvalidatedCode() {
      // Given
      Instant now = Instant.now();
      Instant redeemedAt = now.minusSeconds(100);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              null,
              TEST_GUILD_ID,
              null,
              TEST_USER_ID,
              redeemedAt,
              now,
              now,
              1);

      // Then
      assertThat(code.isInvalidated()).isTrue();
      assertThat(code.isValid()).isFalse();
    }

    @Test
    @DisplayName("should consider invalidated code as invalid even if not redeemed or expired")
    void shouldConsiderInvalidatedCodeAsInvalidEvenIfNotRedeemedOrExpired() {
      // Given - code is invalidated but not redeemed or expired
      Instant now = Instant.now();
      Instant createdAt = now.minusSeconds(100);
      Instant redeemedAt = now.minusSeconds(50);
      RedemptionCode code =
          new RedemptionCode(
              1L,
              "ABCD1234EFGH5678",
              null,
              TEST_GUILD_ID,
              null,
              TEST_USER_ID,
              redeemedAt,
              createdAt,
              now,
              1);

      // Then
      assertThat(code.isInvalidated()).isTrue();
      assertThat(code.isRedeemed()).isTrue();
      assertThat(code.isExpired()).isFalse();
      assertThat(code.isValid()).isFalse(); // Invalid because it's invalidated
    }
  }

  @Nested
  @DisplayName("Expiration checks")
  class ExpirationChecksTests {

    @Test
    @DisplayName("should detect expired code")
    void shouldDetectExpiredCode() {
      // Given
      Instant pastDate = Instant.now().minus(1, ChronoUnit.DAYS);
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, pastDate);

      // Then
      assertThat(code.isExpired()).isTrue();
      assertThat(code.isValid()).isFalse();
    }

    @Test
    @DisplayName("should detect non-expired code")
    void shouldDetectNonExpiredCode() {
      // Given
      Instant futureDate = Instant.now().plus(1, ChronoUnit.DAYS);
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, futureDate);

      // Then
      assertThat(code.isExpired()).isFalse();
      assertThat(code.isValid()).isTrue();
    }

    @Test
    @DisplayName("should handle null expiration as never expires")
    void shouldHandleNullExpirationAsNeverExpires() {
      // Given
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // Then
      assertThat(code.isExpired()).isFalse();
    }
  }

  @Nested
  @DisplayName("Guild checks")
  class GuildChecksTests {

    @Test
    @DisplayName("should confirm code belongs to guild")
    void shouldConfirmCodeBelongsToGuild() {
      // Given
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // Then
      assertThat(code.belongsToGuild(TEST_GUILD_ID)).isTrue();
    }

    @Test
    @DisplayName("should detect code from different guild")
    void shouldDetectCodeFromDifferentGuild() {
      // Given
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // Then
      assertThat(code.belongsToGuild(999999999999999999L)).isFalse();
    }
  }

  @Nested
  @DisplayName("Code masking")
  class CodeMaskingTests {

    @Test
    @DisplayName("should mask code properly")
    void shouldMaskCodeProperly() {
      // Given
      RedemptionCode code =
          RedemptionCode.create("ABCD1234EFGH5678", TEST_PRODUCT_ID, TEST_GUILD_ID, null);

      // Then
      assertThat(code.getMaskedCode()).isEqualTo("ABCD****5678");
    }

    @Test
    @DisplayName("should not mask short code")
    void shouldNotMaskShortCode() {
      // Given
      Instant now = Instant.now();
      RedemptionCode code =
          new RedemptionCode(
              1L, "ABCD1234", TEST_PRODUCT_ID, TEST_GUILD_ID, null, null, null, now, null, 1);

      // Then
      assertThat(code.getMaskedCode()).isEqualTo("ABCD1234");
    }
  }
}
