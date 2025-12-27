package ltdjms.discord.discord.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DiscordError 單元測試
 *
 * <p>測試 DiscordError record 的工廠方法和驗證邏輯。
 */
@DisplayName("DiscordError 測試")
class DiscordErrorTest {

  private static final String TEST_ID = "test_id_123";

  @Test
  @DisplayName("建構子應該正確建立 DiscordError")
  void constructorShouldCreateDiscordError() {
    DiscordError error = new DiscordError(DiscordError.Category.INTERACTION_TIMEOUT, "測試錯誤", null);

    assertThat(error.category()).isEqualTo(DiscordError.Category.INTERACTION_TIMEOUT);
    assertThat(error.message()).isEqualTo("測試錯誤");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("建構子應該拋出異常當 category 為 null")
  void constructorShouldThrowWhenCategoryIsNull() {
    assertThatThrownBy(() -> new DiscordError(null, "message", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("category");
  }

  @Test
  @DisplayName("建構子應該拋出異常當 message 為 null")
  void constructorShouldThrowWhenMessageIsNull() {
    assertThatThrownBy(
            () -> new DiscordError(DiscordError.Category.INTERACTION_TIMEOUT, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("message");
  }

  @Test
  @DisplayName("建構子應該接受 cause 為 null")
  void constructorShouldAcceptNullCause() {
    DiscordError error =
        new DiscordError(DiscordError.Category.INTERACTION_TIMEOUT, "message", null);

    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("建構子應該正確設定 cause")
  void constructorShouldSetCause() {
    Throwable cause = new RuntimeException("原始異常");
    DiscordError error =
        new DiscordError(DiscordError.Category.INTERACTION_TIMEOUT, "message", cause);

    assertThat(error.cause()).isSameAs(cause);
  }

  @Test
  @DisplayName("interactionTimeout 應該建立逾時錯誤")
  void interactionTimeoutShouldCreateTimeoutError() {
    DiscordError error = DiscordError.interactionTimeout(TEST_ID);

    assertThat(error.category()).isEqualTo(DiscordError.Category.INTERACTION_TIMEOUT);
    assertThat(error.message()).contains(TEST_ID);
    assertThat(error.message()).contains("已超時");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("unknownMessage 應該建立未知訊息錯誤")
  void unknownMessageShouldCreateUnknownMessageError() {
    DiscordError error = DiscordError.unknownMessage(TEST_ID);

    assertThat(error.category()).isEqualTo(DiscordError.Category.UNKNOWN_MESSAGE);
    assertThat(error.message()).contains(TEST_ID);
    assertThat(error.message()).contains("不存在或已刪除");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("rateLimited 應該建立速率限制錯誤")
  void rateLimitedShouldCreateRateLimitError() {
    int retryAfter = 30;
    DiscordError error = DiscordError.rateLimited(retryAfter);

    assertThat(error.category()).isEqualTo(DiscordError.Category.RATE_LIMITED);
    assertThat(error.message()).contains(String.valueOf(retryAfter));
    assertThat(error.message()).contains("秒後重試");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("hookExpired 應該建立 Hook 過期錯誤")
  void hookExpiredShouldCreateHookExpiredError() {
    DiscordError error = DiscordError.hookExpired();

    assertThat(error.category()).isEqualTo(DiscordError.Category.HOOK_EXPIRED);
    assertThat(error.message()).contains("過期");
    assertThat(error.message()).contains("重新開啟");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("missingPermissions 應該建立缺少權限錯誤")
  void missingPermissionsShouldCreateMissingPermissionsError() {
    String permission = "ADMINISTRATOR";
    DiscordError error = DiscordError.missingPermissions(permission);

    assertThat(error.category()).isEqualTo(DiscordError.Category.MISSING_PERMISSIONS);
    assertThat(error.message()).contains(permission);
    assertThat(error.message()).contains("缺少必要權限");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("invalidComponentId 應該建立無效元件 ID 錯誤")
  void invalidComponentIdShouldCreateInvalidComponentIdError() {
    DiscordError error = DiscordError.invalidComponentId(TEST_ID);

    assertThat(error.category()).isEqualTo(DiscordError.Category.INVALID_COMPONENT_ID);
    assertThat(error.message()).contains(TEST_ID);
    assertThat(error.message()).contains("無效的元件 ID");
    assertThat(error.cause()).isNull();
  }

  @Test
  @DisplayName("Category 枚舉應該包含所有預期的值")
  void categoryEnumShouldContainAllExpectedValues() {
    DiscordError.Category[] categories = DiscordError.Category.values();

    assertThat(categories)
        .containsExactlyInAnyOrder(
            DiscordError.Category.INTERACTION_TIMEOUT,
            DiscordError.Category.HOOK_EXPIRED,
            DiscordError.Category.UNKNOWN_MESSAGE,
            DiscordError.Category.RATE_LIMITED,
            DiscordError.Category.MISSING_PERMISSIONS,
            DiscordError.Category.INVALID_COMPONENT_ID);
  }
}
