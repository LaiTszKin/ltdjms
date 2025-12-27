package ltdjms.discord.product.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Product domain model. */
class ProductTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;

  @Nested
  @DisplayName("Product creation")
  class ProductCreationTests {

    @Test
    @DisplayName("should create product without reward")
    void shouldCreateProductWithoutReward() {
      // When
      Product product = Product.createWithoutReward(TEST_GUILD_ID, "VIP 護航服務", "專人服務");

      // Then
      assertThat(product.id()).isNull();
      assertThat(product.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(product.name()).isEqualTo("VIP 護航服務");
      assertThat(product.description()).isEqualTo("專人服務");
      assertThat(product.rewardType()).isNull();
      assertThat(product.rewardAmount()).isNull();
      assertThat(product.hasReward()).isFalse();
    }

    @Test
    @DisplayName("should create product with currency reward")
    void shouldCreateProductWithCurrencyReward() {
      // When
      Product product =
          Product.create(TEST_GUILD_ID, "新手禮包", "歡迎新手", Product.RewardType.CURRENCY, 1000L, null);

      // Then
      assertThat(product.name()).isEqualTo("新手禮包");
      assertThat(product.rewardType()).isEqualTo(Product.RewardType.CURRENCY);
      assertThat(product.rewardAmount()).isEqualTo(1000L);
      assertThat(product.hasReward()).isTrue();
    }

    @Test
    @DisplayName("should create product with token reward")
    void shouldCreateProductWithTokenReward() {
      // When
      Product product =
          Product.create(TEST_GUILD_ID, "代幣包", "50 代幣", Product.RewardType.TOKEN, 50L, null);

      // Then
      assertThat(product.rewardType()).isEqualTo(Product.RewardType.TOKEN);
      assertThat(product.rewardAmount()).isEqualTo(50L);
    }

    @Test
    @DisplayName("should reject null name")
    void shouldRejectNullName() {
      assertThatThrownBy(() -> Product.create(TEST_GUILD_ID, null, "desc", null, null, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject blank name")
    void shouldRejectBlankName() {
      assertThatThrownBy(() -> Product.create(TEST_GUILD_ID, "   ", "desc", null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("should reject name exceeding 100 characters")
    void shouldRejectNameExceeding100Characters() {
      String longName = "a".repeat(101);
      assertThatThrownBy(() -> Product.create(TEST_GUILD_ID, longName, "desc", null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("100");
    }

    @Test
    @DisplayName("should reject negative reward amount")
    void shouldRejectNegativeRewardAmount() {
      assertThatThrownBy(
              () ->
                  Product.create(
                      TEST_GUILD_ID, "Test", "desc", Product.RewardType.CURRENCY, -100L, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("should reject reward type without amount")
    void shouldRejectRewardTypeWithoutAmount() {
      assertThatThrownBy(
              () ->
                  Product.create(
                      TEST_GUILD_ID, "Test", "desc", Product.RewardType.CURRENCY, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("both");
    }

    @Test
    @DisplayName("should reject reward amount without type")
    void shouldRejectRewardAmountWithoutType() {
      assertThatThrownBy(() -> Product.create(TEST_GUILD_ID, "Test", "desc", null, 100L, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("both");
    }
  }

  @Nested
  @DisplayName("Product update")
  class ProductUpdateTests {

    @Test
    @DisplayName("should update product details")
    void shouldUpdateProductDetails() {
      // Given
      Instant now = Instant.now();
      Product original =
          new Product(
              1L, TEST_GUILD_ID, "舊名稱", "舊描述", Product.RewardType.CURRENCY, 500L, null, now, now);

      // When
      Product updated =
          original.withUpdatedDetails("新名稱", "新描述", Product.RewardType.TOKEN, 100L, null);

      // Then
      assertThat(updated.id()).isEqualTo(1L);
      assertThat(updated.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(updated.name()).isEqualTo("新名稱");
      assertThat(updated.description()).isEqualTo("新描述");
      assertThat(updated.rewardType()).isEqualTo(Product.RewardType.TOKEN);
      assertThat(updated.rewardAmount()).isEqualTo(100L);
      assertThat(updated.createdAt()).isEqualTo(now);
      assertThat(updated.updatedAt()).isAfterOrEqualTo(now);
    }

    @Test
    @DisplayName("should update product to remove reward")
    void shouldUpdateProductToRemoveReward() {
      // Given
      Instant now = Instant.now();
      Product original =
          new Product(
              1L, TEST_GUILD_ID, "名稱", "描述", Product.RewardType.CURRENCY, 500L, null, now, now);

      // When
      Product updated = original.withUpdatedDetails("名稱", "描述", null, null, null);

      // Then
      assertThat(updated.rewardType()).isNull();
      assertThat(updated.rewardAmount()).isNull();
      assertThat(updated.hasReward()).isFalse();
    }
  }

  @Nested
  @DisplayName("Reward formatting")
  class RewardFormattingTests {

    @Test
    @DisplayName("should format currency reward")
    void shouldFormatCurrencyReward() {
      // Given
      Product product =
          Product.create(TEST_GUILD_ID, "Test", null, Product.RewardType.CURRENCY, 1000L, null);

      // When
      String formatted = product.formatReward();

      // Then
      assertThat(formatted).isEqualTo("1,000 貨幣");
    }

    @Test
    @DisplayName("should format token reward")
    void shouldFormatTokenReward() {
      // Given
      Product product =
          Product.create(TEST_GUILD_ID, "Test", null, Product.RewardType.TOKEN, 50L, null);

      // When
      String formatted = product.formatReward();

      // Then
      assertThat(formatted).isEqualTo("50 代幣");
    }

    @Test
    @DisplayName("should return null for no reward")
    void shouldReturnNullForNoReward() {
      // Given
      Product product = Product.createWithoutReward(TEST_GUILD_ID, "Test", null);

      // When
      String formatted = product.formatReward();

      // Then
      assertThat(formatted).isNull();
    }
  }
}
