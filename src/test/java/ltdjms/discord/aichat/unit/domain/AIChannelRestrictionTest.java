package ltdjms.discord.aichat.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AIChannelRestriction;
import ltdjms.discord.aichat.domain.AllowedChannel;

/** 測試 {@link AIChannelRestriction} 聚合根的業務邏輯。 */
@DisplayName("AIChannelRestriction 聚合根測試")
class AIChannelRestrictionTest {

  private static final long GUILD_ID = 123L;
  private static final AllowedChannel CHANNEL_1 = new AllowedChannel(1001L, "general");
  private static final AllowedChannel CHANNEL_2 = new AllowedChannel(1002L, "ai-chat");

  @Nested
  @DisplayName("無限制模式判斷")
  class UnrestrictedMode {

    @Test
    @DisplayName("當允許頻道清單為空時，應為無限制模式")
    void shouldReturnTrueWhenEmpty() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of());
      assertTrue(restriction.isUnrestricted());
    }

    @Test
    @DisplayName("當使用無參數建構式時，應為無限制模式")
    void shouldReturnTrueWhenUsingNoArgConstructor() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID);
      assertTrue(restriction.isUnrestricted());
    }

    @Test
    @DisplayName("當允許頻道清單非空時，不應為無限制模式")
    void shouldReturnFalseWhenNotEmpty() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1));
      assertFalse(restriction.isUnrestricted());
    }
  }

  @Nested
  @DisplayName("頻道檢查")
  class ChannelCheck {

    @Test
    @DisplayName("當為無限制模式時，任何頻道都應被允許")
    void shouldAllowAllChannelsWhenUnrestricted() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of());
      assertTrue(restriction.isChannelAllowed(1001L));
      assertTrue(restriction.isChannelAllowed(9999L));
      assertTrue(restriction.isChannelAllowed(0L)); // 無論是否有效 ID
    }

    @Test
    @DisplayName("當頻道在允許清單中時，應被允許")
    void shouldAllowChannelInList() {
      AIChannelRestriction restriction =
          new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1, CHANNEL_2));
      assertTrue(restriction.isChannelAllowed(1001L));
      assertTrue(restriction.isChannelAllowed(1002L));
    }

    @Test
    @DisplayName("當頻道不在允許清單中時，不應被允許")
    void shouldNotAllowChannelNotInList() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1));
      assertFalse(restriction.isChannelAllowed(1002L));
      assertFalse(restriction.isChannelAllowed(9999L));
    }
  }

  @Nested
  @DisplayName("新增頻道")
  class AddChannel {

    @Test
    @DisplayName("應成功新增頻道至清單")
    void shouldAddChannel() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of());
      AllowedChannel newChannel = new AllowedChannel(1003L, "new-channel");

      AIChannelRestriction updated = restriction.withChannelAdded(newChannel);

      // 新物件包含新頻道且不再為無限制模式
      assertTrue(updated.isChannelAllowed(1003L));
      assertFalse(updated.isUnrestricted());
      assertEquals(1, updated.allowedChannels().size());

      // 原物件仍為無限制模式（空集合）
      assertTrue(restriction.isUnrestricted());
      assertEquals(0, restriction.allowedChannels().size());
    }

    @Test
    @DisplayName("新增已存在的頻道時，不應重複加入")
    void shouldNotDuplicateExistingChannel() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1));

      AIChannelRestriction updated = restriction.withChannelAdded(CHANNEL_1);

      assertTrue(updated.isChannelAllowed(1001L));
      // Set 自動去重，所以大小應仍為 1
      assertTrue(updated.allowedChannels().size() == 1);
    }
  }

  @Nested
  @DisplayName("移除頻道")
  class RemoveChannel {

    @Test
    @DisplayName("應成功移除頻道")
    void shouldRemoveChannel() {
      AIChannelRestriction restriction =
          new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1, CHANNEL_2));

      AIChannelRestriction updated = restriction.withChannelRemoved(1001L);

      assertFalse(updated.isChannelAllowed(1001L));
      assertTrue(updated.isChannelAllowed(1002L));
      assertTrue(restriction.isChannelAllowed(1001L)); // 原物件不變
    }

    @Test
    @DisplayName("移除所有頻道後，應變為無限制模式")
    void shouldBecomeUnrestrictedWhenAllRemoved() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1));

      AIChannelRestriction updated = restriction.withChannelRemoved(1001L);

      assertTrue(updated.isUnrestricted());
    }

    @Test
    @DisplayName("移除不存在的頻道時，不應有影響")
    void shouldDoNothingWhenRemovingNonExistentChannel() {
      AIChannelRestriction restriction = new AIChannelRestriction(GUILD_ID, Set.of(CHANNEL_1));

      AIChannelRestriction updated = restriction.withChannelRemoved(9999L);

      assertTrue(updated.isChannelAllowed(1001L));
      assertEquals(1, updated.allowedChannels().size());
    }
  }
}
