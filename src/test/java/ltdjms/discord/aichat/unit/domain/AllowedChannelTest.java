package ltdjms.discord.aichat.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AllowedChannel;

/** 測試 {@link AllowedChannel} 值物件的驗證邏輯。 */
@DisplayName("AllowedChannel 值物件測試")
class AllowedChannelTest {

  @Nested
  @DisplayName("建構式驗證")
  class ConstructorValidation {

    @Test
    @DisplayName("當 channelId 有效且 channelName 非空時，應成功建立")
    void shouldCreateWhenValid() {
      AllowedChannel channel = new AllowedChannel(123L, "general");
      assertEquals(123L, channel.channelId());
      assertEquals("general", channel.channelName());
    }

    @Test
    @DisplayName("當 channelId <= 0 時，應拋出 IllegalArgumentException")
    void shouldThrowWhenChannelIdInvalid() {
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> new AllowedChannel(0L, "general"));
      assertEquals("頻道 ID 必須大於 0", ex.getMessage());

      assertThrows(IllegalArgumentException.class, () -> new AllowedChannel(-1L, "general"));
    }

    @Test
    @DisplayName("當 channelName 為 null 時，應拋出 IllegalArgumentException")
    void shouldThrowWhenChannelNameNull() {
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> new AllowedChannel(123L, null));
      assertEquals("頻道名稱不可為空", ex.getMessage());
    }

    @Test
    @DisplayName("當 channelName 為空白時，應拋出 IllegalArgumentException")
    void shouldThrowWhenChannelNameBlank() {
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> new AllowedChannel(123L, "   "));
      assertEquals("頻道名稱不可為空", ex.getMessage());
    }

    @Test
    @DisplayName("當 channelName 為空字串時，應拋出 IllegalArgumentException")
    void shouldThrowWhenChannelNameEmpty() {
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> new AllowedChannel(123L, ""));
      assertEquals("頻道名稱不可為空", ex.getMessage());
    }
  }
}
