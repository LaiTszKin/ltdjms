package ltdjms.discord.discord.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * MockDiscordInteraction 實作單元測試
 *
 * <p>測試 MockDiscordInteraction 的追蹤功能，確保它：
 *
 * <ul>
 *   <li>正確追蹤所有呼叫的 reply 訊息
 *   <li>正確追蹤所有呼叫的 replyEmbed
 *   <li>正確追蹤所有呼叫的 editEmbed
 *   <li>正確追蹤 deferReply 呼叫
 *   <li>提供方便的測試輔助方法
 * </ul>
 */
class MockDiscordInteractionTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private MockDiscordInteraction mockInteraction;
  private InteractionHook mockHook;

  @BeforeEach
  void setUp() {
    mockHook = mock(InteractionHook.class);
    mockInteraction = new MockDiscordInteraction(TEST_GUILD_ID, TEST_USER_ID, mockHook);
  }

  @Test
  @DisplayName("建構函式應該正確設定 Guild ID 和 User ID")
  void constructor_shouldSetGuildAndUserIds() {
    // Given - 已在 setUp 中初始化

    // When & Then
    assertThat(mockInteraction.getGuildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(mockInteraction.getUserId()).isEqualTo(TEST_USER_ID);
  }

  @Test
  @DisplayName("建構函式應該正確設定 InteractionHook")
  void constructor_shouldSetInteractionHook() {
    // Given - 已在 setUp 中初始化

    // When
    InteractionHook hook = mockInteraction.getHook();

    // Then
    assertThat(hook).isEqualTo(mockHook);
  }

  @Test
  @DisplayName("isEphemeral 預設應該返回 false")
  void isEphemeral_shouldReturnFalseByDefault() {
    // When & Then
    assertThat(mockInteraction.isEphemeral()).isFalse();
  }

  @Test
  @DisplayName("isEphemeral 應該反映設定的值")
  void isEphemeral_shouldReturnSetValue() {
    // Given
    MockDiscordInteraction ephemeralMock =
        new MockDiscordInteraction(TEST_GUILD_ID, TEST_USER_ID, true, mockHook);

    // When & Then
    assertThat(ephemeralMock.isEphemeral()).isTrue();
  }

  @Test
  @DisplayName("reply 應該追蹤訊息並標記為已確認")
  void reply_shouldTrackMessageAndMarkAcknowledged() {
    // Given
    String message = "測試訊息";
    assertThat(mockInteraction.isAcknowledged()).isFalse();

    // When
    mockInteraction.reply(message);

    // Then
    assertThat(mockInteraction.isAcknowledged()).isTrue();
    assertThat(mockInteraction.getReplyMessages()).hasSize(1);
    assertThat(mockInteraction.getReplyMessages().get(0)).isEqualTo(message);
  }

  @Test
  @DisplayName("reply 應該追蹤多個訊息")
  void reply_shouldTrackMultipleMessages() {
    // Given
    String message1 = "第一則訊息";
    String message2 = "第二則訊息";

    // When
    mockInteraction.reply(message1);
    mockInteraction.reply(message2);

    // Then
    assertThat(mockInteraction.getReplyMessages()).hasSize(2);
    assertThat(mockInteraction.getReplyMessages().get(0)).isEqualTo(message1);
    assertThat(mockInteraction.getReplyMessages().get(1)).isEqualTo(message2);
  }

  @Test
  @DisplayName("replyEmbed 應該追蹤 Embed 並標記為已確認")
  void replyEmbed_shouldTrackEmbedAndMarkAcknowledged() {
    // Given
    MessageEmbed mockEmbed = mock(MessageEmbed.class);
    assertThat(mockInteraction.isAcknowledged()).isFalse();

    // When
    mockInteraction.replyEmbed(mockEmbed);

    // Then
    assertThat(mockInteraction.isAcknowledged()).isTrue();
    assertThat(mockInteraction.getReplyEmbeds()).hasSize(1);
    assertThat(mockInteraction.getReplyEmbeds().get(0)).isEqualTo(mockEmbed);
  }

  @Test
  @DisplayName("replyEmbed 應該追蹤多個 Embed")
  void replyEmbed_shouldTrackMultipleEmbeds() {
    // Given
    MessageEmbed embed1 = mock(MessageEmbed.class);
    MessageEmbed embed2 = mock(MessageEmbed.class);

    // When
    mockInteraction.replyEmbed(embed1);
    mockInteraction.replyEmbed(embed2);

    // Then
    assertThat(mockInteraction.getReplyEmbeds()).hasSize(2);
    assertThat(mockInteraction.getReplyEmbeds().get(0)).isEqualTo(embed1);
    assertThat(mockInteraction.getReplyEmbeds().get(1)).isEqualTo(embed2);
  }

  @Test
  @DisplayName("editEmbed 應該追蹤編輯的 Embed")
  void editEmbed_shouldTrackEditedEmbed() {
    // Given
    MessageEmbed mockEmbed = mock(MessageEmbed.class);

    // When
    mockInteraction.editEmbed(mockEmbed);

    // Then
    assertThat(mockInteraction.getEditedEmbeds()).hasSize(1);
    assertThat(mockInteraction.getEditedEmbeds().get(0)).isEqualTo(mockEmbed);
  }

  @Test
  @DisplayName("editEmbed 應該追蹤多個編輯的 Embed")
  void editEmbed_shouldTrackMultipleEditedEmbeds() {
    // Given
    MessageEmbed embed1 = mock(MessageEmbed.class);
    MessageEmbed embed2 = mock(MessageEmbed.class);

    // When
    mockInteraction.editEmbed(embed1);
    mockInteraction.editEmbed(embed2);

    // Then
    assertThat(mockInteraction.getEditedEmbeds()).hasSize(2);
    assertThat(mockInteraction.getEditedEmbeds().get(0)).isEqualTo(embed1);
    assertThat(mockInteraction.getEditedEmbeds().get(1)).isEqualTo(embed2);
  }

  @Test
  @DisplayName("deferReply 應該標記為已確認並記錄呼叫次數")
  void deferReply_shouldMarkAcknowledgedAndRecordCall() {
    // Given
    assertThat(mockInteraction.isAcknowledged()).isFalse();
    assertThat(mockInteraction.getDeferReplyCount()).isZero();

    // When
    mockInteraction.deferReply();

    // Then
    assertThat(mockInteraction.isAcknowledged()).isTrue();
    assertThat(mockInteraction.getDeferReplyCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("deferReply 應該追蹤多次呼叫")
  void deferReply_shouldTrackMultipleCalls() {
    // When
    mockInteraction.deferReply();
    mockInteraction.deferReply();
    mockInteraction.deferReply();

    // Then
    assertThat(mockInteraction.getDeferReplyCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("getLastReply 應該返回最後一次的 reply 訊息")
  void getLastReply_shouldReturnLastReplyMessage() {
    // Given
    mockInteraction.reply("第一則");
    mockInteraction.reply("第二則");
    mockInteraction.reply("第三則");

    // When
    String lastReply = mockInteraction.getLastReply();

    // Then
    assertThat(lastReply).isEqualTo("第三則");
  }

  @Test
  @DisplayName("getLastReply 應該在沒有 reply 時返回 null")
  void getLastReply_shouldReturnNullWhenNoReply() {
    // When
    String lastReply = mockInteraction.getLastReply();

    // Then
    assertThat(lastReply).isNull();
  }

  @Test
  @DisplayName("getLastReplyEmbed 應該返回最後一次的 replyEmbed")
  void getLastReplyEmbed_shouldReturnLastReplyEmbed() {
    // Given
    MessageEmbed embed1 = mock(MessageEmbed.class);
    MessageEmbed embed2 = mock(MessageEmbed.class);
    MessageEmbed embed3 = mock(MessageEmbed.class);

    mockInteraction.replyEmbed(embed1);
    mockInteraction.replyEmbed(embed2);
    mockInteraction.replyEmbed(embed3);

    // When
    MessageEmbed lastEmbed = mockInteraction.getLastReplyEmbed();

    // Then
    assertThat(lastEmbed).isEqualTo(embed3);
  }

  @Test
  @DisplayName("getLastReplyEmbed 應該在沒有 replyEmbed 時返回 null")
  void getLastReplyEmbed_shouldReturnNullWhenNoReplyEmbed() {
    // When
    MessageEmbed lastEmbed = mockInteraction.getLastReplyEmbed();

    // Then
    assertThat(lastEmbed).isNull();
  }

  @Test
  @DisplayName("getLastEditedEmbed 應該返回最後一次的 editEmbed")
  void getLastEditedEmbed_shouldReturnLastEditedEmbed() {
    // Given
    MessageEmbed embed1 = mock(MessageEmbed.class);
    MessageEmbed embed2 = mock(MessageEmbed.class);

    mockInteraction.editEmbed(embed1);
    mockInteraction.editEmbed(embed2);

    // When
    MessageEmbed lastEdited = mockInteraction.getLastEditedEmbed();

    // Then
    assertThat(lastEdited).isEqualTo(embed2);
  }

  @Test
  @DisplayName("getLastEditedEmbed 應該在沒有 editEmbed 時返回 null")
  void getLastEditedEmbed_shouldReturnNullWhenNoEditEmbed() {
    // When
    MessageEmbed lastEdited = mockInteraction.getLastEditedEmbed();

    // Then
    assertThat(lastEdited).isNull();
  }

  @Test
  @DisplayName("clear 應該清除所有追蹤的資料")
  void clear_shouldResetAllTrackingData() {
    // Given
    MessageEmbed embed = mock(MessageEmbed.class);
    mockInteraction.reply("訊息");
    mockInteraction.replyEmbed(embed);
    mockInteraction.editEmbed(embed);
    mockInteraction.deferReply();

    // When
    mockInteraction.clear();

    // Then
    assertThat(mockInteraction.getReplyMessages()).isEmpty();
    assertThat(mockInteraction.getReplyEmbeds()).isEmpty();
    assertThat(mockInteraction.getEditedEmbeds()).isEmpty();
    assertThat(mockInteraction.getDeferReplyCount()).isZero();
    assertThat(mockInteraction.isAcknowledged()).isFalse();
  }

  @Test
  @DisplayName("clear 不應該影響 Guild ID、User ID 和 Hook")
  void clear_shouldNotAffectIdsAndHook() {
    // Given
    long expectedGuildId = mockInteraction.getGuildId();
    long expectedUserId = mockInteraction.getUserId();
    InteractionHook expectedHook = mockInteraction.getHook();

    // When
    mockInteraction.clear();

    // Then
    assertThat(mockInteraction.getGuildId()).isEqualTo(expectedGuildId);
    assertThat(mockInteraction.getUserId()).isEqualTo(expectedUserId);
    assertThat(mockInteraction.getHook()).isEqualTo(expectedHook);
  }

  @Test
  @DisplayName("getReplyCount 應該返回 reply 呼叫次數")
  void getReplyCount_shouldReturnReplyCallCount() {
    // Given
    mockInteraction.reply("A");
    mockInteraction.reply("B");
    mockInteraction.reply("C");

    // When
    int count = mockInteraction.getReplyCount();

    // Then
    assertThat(count).isEqualTo(3);
  }

  @Test
  @DisplayName("getReplyEmbedCount 應該返回 replyEmbed 呼叫次數")
  void getReplyEmbedCount_shouldReturnReplyEmbedCallCount() {
    // Given
    mockInteraction.replyEmbed(mock(MessageEmbed.class));
    mockInteraction.replyEmbed(mock(MessageEmbed.class));

    // When
    int count = mockInteraction.getReplyEmbedCount();

    // Then
    assertThat(count).isEqualTo(2);
  }

  @Test
  @DisplayName("getEditEmbedCount 應該返回 editEmbed 呼叫次數")
  void getEditEmbedCount_shouldReturnEditEmbedCallCount() {
    // Given
    mockInteraction.editEmbed(mock(MessageEmbed.class));
    mockInteraction.editEmbed(mock(MessageEmbed.class));
    mockInteraction.editEmbed(mock(MessageEmbed.class));

    // When
    int count = mockInteraction.getEditEmbedCount();

    // Then
    assertThat(count).isEqualTo(3);
  }

  @Test
  @DisplayName("hasReplies 應該正確反映是否有任何 reply")
  void hasReplies_shouldReflectIfAnyRepliesExist() {
    // Given - 初始狀態
    assertThat(mockInteraction.hasReplies()).isFalse();

    // When - 加入 reply
    mockInteraction.reply("測試");

    // Then
    assertThat(mockInteraction.hasReplies()).isTrue();
  }

  @Test
  @DisplayName("hasReplyEmbeds 應該正確反映是否有任何 replyEmbed")
  void hasReplyEmbeds_shouldReflectIfAnyReplyEmbedsExist() {
    // Given - 初始狀態
    assertThat(mockInteraction.hasReplyEmbeds()).isFalse();

    // When - 加入 replyEmbed
    mockInteraction.replyEmbed(mock(MessageEmbed.class));

    // Then
    assertThat(mockInteraction.hasReplyEmbeds()).isTrue();
  }

  @Test
  @DisplayName("hasEditedEmbeds 應該正確反映是否有任何 editEmbed")
  void hasEditedEmbeds_shouldReflectIfAnyEditedEmbedsExist() {
    // Given - 初始狀態
    assertThat(mockInteraction.hasEditedEmbeds()).isFalse();

    // When - 加入 editEmbed
    mockInteraction.editEmbed(mock(MessageEmbed.class));

    // Then
    assertThat(mockInteraction.hasEditedEmbeds()).isTrue();
  }

  @Test
  @DisplayName("hasDeferred 應該正確反映是否有 deferReply 呼叫")
  void hasDeferred_shouldReflectIfDeferReplyWasCalled() {
    // Given - 初始狀態
    assertThat(mockInteraction.hasDeferred()).isFalse();

    // When - 呼叫 deferReply
    mockInteraction.deferReply();

    // Then
    assertThat(mockInteraction.hasDeferred()).isTrue();
  }

  @Test
  @DisplayName("應該能夠區分不同類型的互動")
  void shouldDistinguishBetweenInteractionTypes() {
    // Given
    mockInteraction.reply("文字訊息");
    mockInteraction.replyEmbed(mock(MessageEmbed.class));
    mockInteraction.editEmbed(mock(MessageEmbed.class));
    mockInteraction.deferReply();

    // Then & When
    assertThat(mockInteraction.getReplyCount()).isEqualTo(1);
    assertThat(mockInteraction.getReplyEmbedCount()).isEqualTo(1);
    assertThat(mockInteraction.getEditEmbedCount()).isEqualTo(1);
    assertThat(mockInteraction.getDeferReplyCount()).isEqualTo(1);
  }
}
