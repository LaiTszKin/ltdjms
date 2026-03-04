package ltdjms.discord.panel.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/** 驗證管理員面板主選單的內容與按鈕配置。 */
class AdminPanelCommandHandlerTest {

  private final AdminPanelCommandHandler handler =
      new AdminPanelCommandHandler(mock(AdminPanelService.class), new AdminPanelSessionManager());

  @Test
  @DisplayName("主選單應包含商品與兌換碼管理入口")
  void mainPanelShouldIncludeProductManagementEntry() {
    MessageEmbed embed = handler.buildMainPanelEmbed("💰");

    List<String> fieldNames = embed.getFields().stream().map(MessageEmbed.Field::getName).toList();

    assertThat(fieldNames).contains("📦 商品與兌換碼管理");
  }

  @Test
  @DisplayName("主選單應包含 AI 頻道與 AI Agent 設定入口")
  void mainPanelShouldIncludeAIEntries() {
    MessageEmbed embed = handler.buildMainPanelEmbed("💰");

    List<String> fieldNames = embed.getFields().stream().map(MessageEmbed.Field::getName).toList();

    assertThat(fieldNames).contains("🤖 AI 頻道設定", "🤖 AI Agent 配置");
  }

  @Test
  @DisplayName("主選單應包含派單售後設定入口")
  void mainPanelShouldIncludeDispatchAfterSalesEntry() {
    MessageEmbed embed = handler.buildMainPanelEmbed("💰");

    List<String> fieldNames = embed.getFields().stream().map(MessageEmbed.Field::getName).toList();

    assertThat(fieldNames).contains("🧰 派單售後設定");
  }

  @Test
  @DisplayName("主選單應包含護航定價設定入口")
  void mainPanelShouldIncludeEscortPricingEntry() {
    MessageEmbed embed = handler.buildMainPanelEmbed("💰");

    List<String> fieldNames = embed.getFields().stream().map(MessageEmbed.Field::getName).toList();

    assertThat(fieldNames).contains("🛡️ 護航定價設定");
  }

  @Test
  @DisplayName("主選單按鈕應包含商品管理按鈕")
  void mainPanelButtonsShouldContainProductButton() {
    List<Button> buttons = handler.buildMainActionButtons("💰");

    assertThat(buttons)
        .extracting(Button::getId)
        .contains(AdminProductPanelHandler.BUTTON_PRODUCTS);
  }

  @Test
  @DisplayName("主選單按鈕應包含 AI 頻道與 AI Agent 設定")
  void mainPanelButtonsShouldContainAIButtons() {
    List<Button> buttons = handler.buildMainActionButtons("💰");

    assertThat(buttons)
        .extracting(Button::getId)
        .contains(
            AdminPanelCommandHandler.BUTTON_AI_CHANNEL_CONFIG,
            AdminPanelCommandHandler.BUTTON_AI_AGENT_CONFIG);
  }

  @Test
  @DisplayName("主選單按鈕應包含派單售後設定")
  void mainPanelButtonsShouldContainDispatchAfterSalesButton() {
    List<Button> buttons = handler.buildMainActionButtons("💰");

    assertThat(buttons)
        .extracting(Button::getId)
        .contains(AdminPanelCommandHandler.BUTTON_DISPATCH_AFTER_SALES_CONFIG);
  }

  @Test
  @DisplayName("主選單按鈕應包含護航定價設定")
  void mainPanelButtonsShouldContainEscortPricingButton() {
    List<Button> buttons = handler.buildMainActionButtons("💰");

    assertThat(buttons)
        .extracting(Button::getId)
        .contains(AdminPanelCommandHandler.BUTTON_ESCORT_PRICING_CONFIG);
  }

  @Test
  @DisplayName("非管理員不應該能開啟管理面板")
  void nonAdminCannotOpenPanel() {
    AdminPanelService adminPanelService = mock(AdminPanelService.class);
    AdminPanelSessionManager sessionManager = mock(AdminPanelSessionManager.class);
    AdminPanelCommandHandler handler =
        new AdminPanelCommandHandler(adminPanelService, sessionManager);

    SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User user = mock(User.class);
    ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);

    when(event.getGuild()).thenReturn(guild);
    when(event.getMember()).thenReturn(member);
    when(event.getUser()).thenReturn(user);
    when(user.getIdLong()).thenReturn(123L);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
    when(guild.getOwnerIdLong()).thenReturn(999L);
    when(event.reply("你沒有權限使用管理面板")).thenReturn(replyAction);
    when(replyAction.setEphemeral(true)).thenReturn(replyAction);

    handler.handle(event);

    verify(event).reply("你沒有權限使用管理面板");
    verify(event, never()).replyEmbeds(any(MessageEmbed.class));
    verify(sessionManager, never()).registerSession(anyLong(), anyLong(), any());
  }
}
