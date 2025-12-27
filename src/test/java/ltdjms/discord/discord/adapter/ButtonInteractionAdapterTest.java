package ltdjms.discord.discord.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/** ButtonInteractionAdapter 單元測試 */
@DisplayName("ButtonInteractionAdapter 測試")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ButtonInteractionAdapterTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_USER_ID = 987654321L;
  private static final String TEST_BUTTON_ID = "test_button";

  @Mock private ButtonInteractionEvent event;

  @Mock private ReplyCallbackAction replyAction;

  @Mock
  private net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
      messageEditAction;

  @Mock private InteractionHook hook;

  private ButtonInteractionAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ButtonInteractionAdapter(event);
  }

  @Test
  @DisplayName("建構子應該建立 adapter")
  void constructorShouldCreateAdapter() {
    assertThat(adapter).isNotNull();
    assertThat(adapter.isAcknowledged()).isFalse();
  }

  @Test
  @DisplayName("getJdaEvent 應該返回原始事件")
  void getJdaEventShouldReturnOriginalEvent() {
    assertThat(adapter.getJdaEvent()).isSameAs(event);
  }

  @Test
  @DisplayName("reply 應該標記為已確認")
  void replyShouldMarkAsAcknowledged() {
    when(event.reply(anyString())).thenReturn(replyAction);

    adapter.reply("test");

    assertThat(adapter.isAcknowledged()).isTrue();
  }

  @Test
  @DisplayName("getGuildId 應該返回 Guild ID")
  void getGuildIdShouldReturnGuildId() {
    when(event.getGuild()).thenReturn(mock(net.dv8tion.jda.api.entities.Guild.class));
    when(event.getGuild().getIdLong()).thenReturn(TEST_GUILD_ID);

    long guildId = adapter.getGuildId();

    assertThat(guildId).isEqualTo(TEST_GUILD_ID);
  }

  @Test
  @DisplayName("getUserId 應該返回 User ID")
  void getUserIdShouldReturnUserId() {
    when(event.getUser()).thenReturn(mock(net.dv8tion.jda.api.entities.User.class));
    when(event.getUser().getIdLong()).thenReturn(TEST_USER_ID);

    long userId = adapter.getUserId();

    assertThat(userId).isEqualTo(TEST_USER_ID);
  }

  @Test
  @DisplayName("isEphemeral 應該反映事件確認狀態")
  void isEphemeralShouldReflectEventAcknowledgedState() {
    when(event.isAcknowledged()).thenReturn(false);
    assertThat(adapter.isEphemeral()).isFalse();

    when(event.isAcknowledged()).thenReturn(true);
    assertThat(adapter.isEphemeral()).isTrue();
  }

  @Test
  @DisplayName("replyEmbed 應該標記為已確認")
  void replyEmbedShouldMarkAsAcknowledged() {
    MessageEmbed embed = mock(MessageEmbed.class);
    when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);

    adapter.replyEmbed(embed);

    assertThat(adapter.isAcknowledged()).isTrue();
    verify(event).replyEmbeds(embed);
  }

  @Test
  @DisplayName("editEmbed 應該標記為已確認")
  void editEmbedShouldMarkAsAcknowledged() {
    MessageEmbed embed = mock(MessageEmbed.class);
    when(event.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(messageEditAction);

    adapter.editEmbed(embed);

    assertThat(adapter.isAcknowledged()).isTrue();
    verify(event).editMessageEmbeds(embed);
  }

  @Test
  @DisplayName("editComponents 應該標記為已確認")
  void editComponentsShouldMarkAsAcknowledged() {
    List<ActionRow> components = List.of(mock(ActionRow.class));
    when(event.editComponents(anyList())).thenReturn(messageEditAction);

    adapter.editComponents(components);

    assertThat(adapter.isAcknowledged()).isTrue();
    verify(event).editComponents(components);
  }

  @Test
  @DisplayName("deferReply 應該標記為已確認")
  void deferReplyShouldMarkAsAcknowledged() {
    when(event.deferReply()).thenReturn(replyAction);

    adapter.deferReply();

    assertThat(adapter.isAcknowledged()).isTrue();
    verify(event).deferReply();
  }

  @Test
  @DisplayName("getHook 應該返回 InteractionHook")
  void getHookShouldReturnInteractionHook() {
    when(event.getHook()).thenReturn(hook);

    InteractionHook result = adapter.getHook();

    assertThat(result).isSameAs(hook);
  }

  @Test
  @DisplayName("isAcknowledged 應該返回 true 當事件已確認")
  void isAcknowledgedShouldReturnTrueWhenEventAcknowledged() {
    when(event.isAcknowledged()).thenReturn(true);

    assertThat(adapter.isAcknowledged()).isTrue();
  }

  @Test
  @DisplayName("isAcknowledged 應該返回 true 當 adapter 已確認")
  void isAcknowledgedShouldReturnTrueWhenAdapterAcknowledged() {
    // 使用 lenient() 因為 reply() 內部會呼叫 event.reply()
    lenient().when(event.reply(anyString())).thenReturn(replyAction);
    when(event.isAcknowledged()).thenReturn(false);

    adapter.reply("test");

    assertThat(adapter.isAcknowledged()).isTrue();
  }

  @Test
  @DisplayName("getButtonId 應該返回按鈕 ID")
  void getButtonIdShouldReturnButtonId() {
    when(event.getComponentId()).thenReturn(TEST_BUTTON_ID);

    String buttonId = adapter.getButtonId();

    assertThat(buttonId).isEqualTo(TEST_BUTTON_ID);
  }

  @Test
  @DisplayName("getButtonId 應該返回 null 當按鈕 ID 不存在")
  void getButtonIdShouldReturnNullWhenButtonIdNotExists() {
    when(event.getComponentId()).thenReturn(null);

    String buttonId = adapter.getButtonId();

    assertThat(buttonId).isNull();
  }
}
