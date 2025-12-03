package ltdjms.discord.panel.unit;

import ltdjms.discord.panel.services.PanelSessionManager;
import ltdjms.discord.panel.services.UserPanelService;
import ltdjms.discord.panel.services.UserPanelUpdateListener;
import ltdjms.discord.panel.services.UserPanelView;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.GameTokenChangedEvent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserPanelUpdateListener}.
 *
 * 這裡主要驗證：
 * - 收到 BalanceChangedEvent / GameTokenChangedEvent 時會呼叫 UserPanelService 取得最新資料
 * - 會透過 PanelSessionManager 綁定的 InteractionHook 更新原本的面板 Embed
 */
class UserPanelUpdateListenerTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    private PanelSessionManager sessionManager;
    private UserPanelService userPanelService;
    private UserPanelUpdateListener listener;

    private InteractionHook interactionHook;
    private WebhookMessageEditAction<Message> editAction;

    @BeforeEach
    void setUp() {
        sessionManager = new PanelSessionManager();
        userPanelService = mock(UserPanelService.class);
        listener = new UserPanelUpdateListener(sessionManager, userPanelService);

        interactionHook = mock(InteractionHook.class);
        //noinspection unchecked
        editAction = (WebhookMessageEditAction<Message>) mock(WebhookMessageEditAction.class);

        when(interactionHook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
        // 避免 queue() 呼叫真的發出 HTTP 請求，只要吞掉 callback 即可
        doAnswer(invocation -> null).when(editAction).queue(any(), any());

        // 模擬使用者已經開啟 /user-panel，並註冊了 session
        sessionManager.registerSession(TEST_GUILD_ID, TEST_USER_ID, interactionHook, "<@" + TEST_USER_ID + ">");
    }

    @Nested
    @DisplayName("BalanceChangedEvent")
    class BalanceChangedEventTests {

        @Test
        @DisplayName("收到餘額變更事件時應更新個人面板 Embed")
        void shouldUpdatePanelOnBalanceChangedEvent() {
            // Given 最新餘額與面板 view
            long newBalance = 2_000L;
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    newBalance,
                    "星幣",
                    "✨",
                    50L
            );
            when(userPanelService.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID))
                    .thenReturn(Result.ok(view));

            // When 觸發 BalanceChangedEvent
            listener.accept(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, newBalance));

            // Then 應呼叫 service 取得最新面板資料
            verify(userPanelService).getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

            // 並更新原始 Embed，內容包含新的餘額數值
            ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
            verify(interactionHook).editOriginalEmbeds(embedCaptor.capture());
            MessageEmbed embed = embedCaptor.getValue();

            assertThat(embed.getFields()).hasSize(2);
            // 第一個欄位為貨幣餘額欄位，應包含最新餘額與貨幣名稱 / 圖示
            MessageEmbed.Field currencyField = embed.getFields().get(0);
            assertThat(currencyField.getName()).contains("星幣").contains("餘額");
            assertThat(currencyField.getValue())
                    .contains("✨")
                    .contains("2,000")
                    .contains("星幣");
        }
    }

    @Nested
    @DisplayName("GameTokenChangedEvent")
    class GameTokenChangedEventTests {

        @Test
        @DisplayName("收到遊戲代幣變更事件時應更新個人面板 Embed")
        void shouldUpdatePanelOnGameTokenChangedEvent() {
            // Given 最新遊戲代幣與面板 view
            long newTokens = 999L;
            UserPanelView view = new UserPanelView(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    1_000L,
                    "星幣",
                    "✨",
                    newTokens
            );
            when(userPanelService.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID))
                    .thenReturn(Result.ok(view));

            // When 觸發 GameTokenChangedEvent
            listener.accept(new GameTokenChangedEvent(TEST_GUILD_ID, TEST_USER_ID, newTokens));

            // Then 應呼叫 service 取得最新面板資料
            verify(userPanelService).getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

            // 並更新原始 Embed，第二個欄位為遊戲代幣欄位
            ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
            verify(interactionHook).editOriginalEmbeds(embedCaptor.capture());
            MessageEmbed embed = embedCaptor.getValue();

            assertThat(embed.getFields()).hasSize(2);
            MessageEmbed.Field tokenField = embed.getFields().get(1);
            assertThat(tokenField.getName()).contains("遊戲代幣");
            assertThat(tokenField.getValue())
                    .contains("🎮")
                    .contains("999");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("當取得面板資料失敗時不應嘗試更新 Embed")
        void shouldNotUpdateEmbedWhenServiceFails() {
            // Given service 回傳錯誤
            DomainError error = DomainError.persistenceFailure("db error", null);
            when(userPanelService.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID))
                    .thenReturn(Result.err(error));

            // When
            listener.accept(new BalanceChangedEvent(TEST_GUILD_ID, TEST_USER_ID, 123L));

            // Then
            verify(userPanelService).getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);
            // 不應呼叫 editOriginalEmbeds
            verify(interactionHook, org.mockito.Mockito.never()).editOriginalEmbeds(any(MessageEmbed.class));
        }
    }
}

