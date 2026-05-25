package ltdjms.discord.panel.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.MembershipAdminDetail;
import ltdjms.discord.membership.services.MembershipPanelSummary;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

class AdminPanelButtonHandlerMembershipTest {

  private static final long GUILD_ID = 100L;
  private static final long ADMIN_ID = 200L;
  private static final long TARGET_USER_ID = 300L;
  private static final Instant JOIN_AT = Instant.parse("2025-06-01T00:00:00Z");

  private AdminPanelService adminPanelService;
  private AdminPanelButtonHandler handler;

  @BeforeEach
  void setUp() {
    adminPanelService = mock(AdminPanelService.class);
    handler = new AdminPanelButtonHandler(adminPanelService, new AdminPanelSessionManager());
  }

  @Test
  @DisplayName("非管理員點擊會員管理按鈕應收到 ephemeral 拒絕")
  void nonAdminShouldBeRejectedForMembershipButton() {
    ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    ReplyCallbackAction reply = mock(ReplyCallbackAction.class);

    when(event.getComponentId()).thenReturn(AdminPanelButtonHandler.BUTTON_MEMBERSHIP);
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(event.getMember()).thenReturn(member);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
    when(guild.getOwnerIdLong()).thenReturn(999L);
    when(event.getUser()).thenReturn(mock(User.class));
    when(event.getUser().getIdLong()).thenReturn(ADMIN_ID);
    when(event.reply("你沒有權限使用管理面板")).thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);

    handler.onButtonInteraction(event);

    verify(event).reply("你沒有權限使用管理面板");
    verify(adminPanelService, never()).getMembershipDetail(anyLong());
  }

  @Test
  @DisplayName("選擇成員後應顯示會員詳情 embed")
  void userSelectShouldShowMembershipDetailEmbed() {
    MembershipAdminDetail detail = sampleDetail(MembershipTier.SILVER, 10_000L, 23_000L);
    when(adminPanelService.getMembershipDetail(TARGET_USER_ID)).thenReturn(Result.ok(detail));

    EntitySelectInteractionEvent event = mock(EntitySelectInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User admin = mock(User.class);
    User targetUser = mock(User.class);
    MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class);

    when(event.getComponentId()).thenReturn(AdminPanelButtonHandler.SELECT_MEMBERSHIP_USER);
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(GUILD_ID);
    when(event.getMember()).thenReturn(member);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(event.getUser()).thenReturn(admin);
    when(admin.getIdLong()).thenReturn(ADMIN_ID);
    when(event.getMentions()).thenReturn(mock(net.dv8tion.jda.api.entities.Mentions.class));
    when(event.getMentions().getUsers()).thenReturn(List.of(targetUser));
    when(targetUser.getIdLong()).thenReturn(TARGET_USER_ID);
    when(targetUser.getAsMention()).thenReturn("<@" + TARGET_USER_ID + ">");
    when(event.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);

    handler.onEntitySelectInteraction(event);

    verify(adminPanelService).getMembershipDetail(TARGET_USER_ID);
    verify(event).editMessageEmbeds(any(MessageEmbed.class));
  }

  @Test
  @DisplayName("提交消費調整 modal 成功時應呼叫 service")
  void spendModalSubmitSuccessShouldAdjustSpend() {
    ModalInteractionEvent event = mock(ModalInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User admin = mock(User.class);
    ModalMapping mapping = mock(ModalMapping.class);
    ReplyCallbackAction reply = mock(ReplyCallbackAction.class);

    when(event.getModalId())
        .thenReturn(AdminPanelButtonHandler.MODAL_MEMBERSHIP_SPEND + ":" + TARGET_USER_ID + ":add");
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(GUILD_ID);
    when(event.getMember()).thenReturn(member);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(event.getUser()).thenReturn(admin);
    when(admin.getIdLong()).thenReturn(ADMIN_ID);
    when(event.getValue("amount")).thenReturn(mapping);
    when(mapping.getAsString()).thenReturn("3000");
    when(adminPanelService.adjustMembershipSpend(GUILD_ID, TARGET_USER_ID, ADMIN_ID, "add", 3000L))
        .thenReturn(Result.okVoid());
    when(event.reply("✅ 已調整本週期消費 M")).thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);

    handler.onModalInteraction(event);

    verify(adminPanelService).adjustMembershipSpend(GUILD_ID, TARGET_USER_ID, ADMIN_ID, "add", 3000L);
    verify(event).reply("✅ 已調整本週期消費 M");
  }

  @Test
  @DisplayName("提交消費調整 modal 失敗時應顯示錯誤")
  void spendModalSubmitFailureShouldShowError() {
    ModalInteractionEvent event = mock(ModalInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User admin = mock(User.class);
    ModalMapping mapping = mock(ModalMapping.class);
    ReplyCallbackAction reply = mock(ReplyCallbackAction.class);

    when(event.getModalId())
        .thenReturn(
            AdminPanelButtonHandler.MODAL_MEMBERSHIP_SPEND + ":" + TARGET_USER_ID + ":deduct");
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(GUILD_ID);
    when(event.getMember()).thenReturn(member);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(event.getUser()).thenReturn(admin);
    when(admin.getIdLong()).thenReturn(ADMIN_ID);
    when(event.getValue("amount")).thenReturn(mapping);
    when(mapping.getAsString()).thenReturn("5000");
    when(adminPanelService.adjustMembershipSpend(
            GUILD_ID, TARGET_USER_ID, ADMIN_ID, "deduct", 5000L))
        .thenReturn(Result.err(DomainError.persistenceFailure("db error", null)));
    when(event.reply(org.mockito.ArgumentMatchers.startsWith("調整失敗："))).thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);

    handler.onModalInteraction(event);

    verify(event).reply(org.mockito.ArgumentMatchers.startsWith("調整失敗："));
  }

  @Test
  @DisplayName("未選成員時確認等級不應呼叫 setMembershipTier")
  void confirmTierWithoutSessionShouldNotSetTier() {
    ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User admin = mock(User.class);
    ReplyCallbackAction reply = mock(ReplyCallbackAction.class);

    when(event.getComponentId()).thenReturn(AdminPanelButtonHandler.BUTTON_CONFIRM_MEMBERSHIP_TIER);
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(GUILD_ID);
    when(event.getMember()).thenReturn(member);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(event.getUser()).thenReturn(admin);
    when(admin.getIdLong()).thenReturn(ADMIN_ID);
    when(event.reply("請先選擇成員和目標等級")).thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);

    handler.onButtonInteraction(event);

    verify(adminPanelService, never()).setMembershipTier(anyLong(), anyLong(), any());
  }

  @Test
  @DisplayName("選擇等級後確認應呼叫 setMembershipTier")
  void tierSelectThenConfirmShouldSetTier() {
    MembershipAdminDetail detail = sampleDetail(MembershipTier.BRONZE, 5_000L, 9_000L);
    when(adminPanelService.getMembershipDetail(TARGET_USER_ID)).thenReturn(Result.ok(detail));
    when(adminPanelService.setMembershipTier(TARGET_USER_ID, ADMIN_ID, MembershipTier.GOLD))
        .thenReturn(Result.ok(MembershipTier.GOLD));

    EntitySelectInteractionEvent selectEvent = mock(EntitySelectInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User admin = mock(User.class);
    User targetUser = mock(User.class);
    MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class);

    when(selectEvent.getComponentId()).thenReturn(AdminPanelButtonHandler.SELECT_MEMBERSHIP_USER);
    when(selectEvent.isFromGuild()).thenReturn(true);
    when(selectEvent.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(GUILD_ID);
    when(selectEvent.getMember()).thenReturn(member);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(selectEvent.getUser()).thenReturn(admin);
    when(admin.getIdLong()).thenReturn(ADMIN_ID);
    when(selectEvent.getMentions()).thenReturn(mock(net.dv8tion.jda.api.entities.Mentions.class));
    when(selectEvent.getMentions().getUsers()).thenReturn(List.of(targetUser));
    when(targetUser.getIdLong()).thenReturn(TARGET_USER_ID);
    when(targetUser.getAsMention()).thenReturn("<@" + TARGET_USER_ID + ">");
    when(selectEvent.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyList())).thenReturn(editAction);
    handler.onEntitySelectInteraction(selectEvent);

    StringSelectInteractionEvent tierSelect = mock(StringSelectInteractionEvent.class);
    when(tierSelect.getComponentId()).thenReturn(AdminPanelButtonHandler.SELECT_MEMBERSHIP_TIER);
    when(tierSelect.isFromGuild()).thenReturn(true);
    when(tierSelect.getGuild()).thenReturn(guild);
    when(tierSelect.getMember()).thenReturn(member);
    when(tierSelect.getUser()).thenReturn(admin);
    when(tierSelect.getValues()).thenReturn(List.of("GOLD"));
    when(tierSelect.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    handler.onStringSelectInteraction(tierSelect);

    ButtonInteractionEvent confirmEvent = mock(ButtonInteractionEvent.class);
    ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
    when(confirmEvent.getComponentId())
        .thenReturn(AdminPanelButtonHandler.BUTTON_CONFIRM_MEMBERSHIP_TIER);
    when(confirmEvent.isFromGuild()).thenReturn(true);
    when(confirmEvent.getGuild()).thenReturn(guild);
    when(confirmEvent.getMember()).thenReturn(member);
    when(confirmEvent.getUser()).thenReturn(admin);
    when(confirmEvent.reply(org.mockito.ArgumentMatchers.startsWith("✅ 已設定等級為")))
        .thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);
    handler.onButtonInteraction(confirmEvent);

    verify(adminPanelService).setMembershipTier(TARGET_USER_ID, ADMIN_ID, MembershipTier.GOLD);
  }

  private static MembershipAdminDetail sampleDetail(
      MembershipTier tier, long periodSpendM, long remainingM) {
    return new MembershipAdminDetail(
        new MembershipPanelSummary(
            tier,
            periodSpendM,
            periodSpendM + remainingM,
            null,
            tier.discountRate(),
            JOIN_AT,
            remainingM,
            tier.monthlyTokenGrant()),
        false);
  }
}
