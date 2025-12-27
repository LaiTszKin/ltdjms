package ltdjms.discord.discord.adapter;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ModalInteractionAdapter 單元測試
 */
@DisplayName("ModalInteractionAdapter 測試")
@ExtendWith(MockitoExtension.class)
class ModalInteractionAdapterTest {

    private static final long TEST_GUILD_ID = 123456789L;
    private static final long TEST_USER_ID = 987654321L;
    private static final String TEST_MODAL_ID = "test_modal";
    private static final String TEST_FIELD_ID = "test_field";

    @Mock
    private ModalInteractionEvent event;

    @Mock
    private ReplyCallbackAction replyAction;

    @Mock
    private net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message> webhookMessageEditAction;

    @Mock
    private InteractionHook hook;

    @Mock
    private ModalMapping modalMapping;

    private ModalInteractionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ModalInteractionAdapter(event);
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
    @DisplayName("getModalId 應該返回 Modal ID")
    void getModalIdShouldReturnModalId() {
        when(event.getModalId()).thenReturn(TEST_MODAL_ID);

        String modalId = adapter.getModalId();

        assertThat(modalId).isEqualTo(TEST_MODAL_ID);
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
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(webhookMessageEditAction);

        adapter.editEmbed(embed);

        assertThat(adapter.isAcknowledged()).isTrue();
        verify(hook).editOriginalEmbeds(embed);
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
    @DisplayName("getValueAsString 應該返回空 Optional 當值不存在")
    void getValueAsStringShouldReturnEmptyOptionalWhenValueNotExists() {
        when(event.getValue(anyString())).thenReturn(null);

        Optional<String> result = adapter.getValueAsString("field_id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getValueAsString 應該返回空 Optional 當值為空字串")
    void getValueAsStringShouldReturnEmptyOptionalWhenValueIsEmpty() {
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn("");

        Optional<String> result = adapter.getValueAsString("field_id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getValueAsString 應該返回值當值存在")
    void getValueAsStringShouldReturnValueWhenValueExists() {
        String expectedValue = "test_value";
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn(expectedValue);

        Optional<String> result = adapter.getValueAsString("field_id");

        assertThat(result).isPresent().contains(expectedValue);
    }

    @Test
    @DisplayName("getValue 應該返回 null 當值不存在")
    void getValueShouldReturnNullWhenValueNotExists() {
        when(event.getValue(anyString())).thenReturn(null);

        String result = adapter.getValue("field_id");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getValue 應該返回值當值存在")
    void getValueShouldReturnValueWhenValueExists() {
        String expectedValue = "test_value";
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn(expectedValue);

        String result = adapter.getValue("field_id");

        assertThat(result).isEqualTo(expectedValue);
    }

    @Test
    @DisplayName("getValueAsLong 應該返回空 Optional 當值不存在")
    void getValueAsLongShouldReturnEmptyOptionalWhenValueNotExists() {
        when(event.getValue(anyString())).thenReturn(null);

        Optional<Long> result = adapter.getValueAsLong("field_id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getValueAsLong 應該返回空 Optional 當值不是數字")
    void getValueAsLongShouldReturnEmptyOptionalWhenValueIsNotNumber() {
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn("not_a_number");

        Optional<Long> result = adapter.getValueAsLong("field_id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getValueAsLong 應該返回 Long 值當值是有效數字")
    void getValueAsLongShouldReturnLongValueWhenValueIsValidNumber() {
        long expectedValue = 12345L;
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn(String.valueOf(expectedValue));

        Optional<Long> result = adapter.getValueAsLong("field_id");

        assertThat(result).isPresent().contains(expectedValue);
    }

    @Test
    @DisplayName("getValueAsLong 應該處理負數")
    void getValueAsLongShouldHandleNegativeNumbers() {
        long expectedValue = -999L;
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn(String.valueOf(expectedValue));

        Optional<Long> result = adapter.getValueAsLong("field_id");

        assertThat(result).isPresent().contains(expectedValue);
    }

    @Test
    @DisplayName("getValueAsLong 應該處理零")
    void getValueAsLongShouldHandleZero() {
        when(event.getValue(anyString())).thenReturn(modalMapping);
        when(modalMapping.getAsString()).thenReturn("0");

        Optional<Long> result = adapter.getValueAsLong("field_id");

        assertThat(result).isPresent().contains(0L);
    }
}
