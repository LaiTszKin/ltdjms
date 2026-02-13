package ltdjms.discord.dispatch.commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.EscortDispatchOrderService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * 派單面板互動處理器：
 *
 * <ul>
 *   <li>管理員面板上的成員選取
 *   <li>建立派單按鈕
 *   <li>護航者私訊中的確認接單按鈕
 * </ul>
 */
public class DispatchPanelInteractionHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(DispatchPanelInteractionHandler.class);

  private static final Color INFO_COLOR = new Color(0x57F287);
  private static final Color WARNING_COLOR = new Color(0xFEE75C);

  private final EscortDispatchOrderService orderService;
  private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

  public DispatchPanelInteractionHandler(EscortDispatchOrderService orderService) {
    this.orderService = orderService;
  }

  @Override
  public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
    String selectId = event.getComponentId();

    if (!isDispatchSelect(selectId)) {
      return;
    }

    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    String sessionKey = getSessionKey(event.getUser().getIdLong(), event.getGuild().getIdLong());
    SessionState state = sessionStates.computeIfAbsent(sessionKey, key -> new SessionState());

    try {
      switch (selectId) {
        case DispatchPanelView.SELECT_ESCORT_USER -> handleEscortUserSelect(event, state);
        case DispatchPanelView.SELECT_CUSTOMER_USER -> handleCustomerUserSelect(event, state);
        default -> LOG.warn("Unknown dispatch select menu: {}", selectId);
      }
    } catch (Exception e) {
      LOG.error("Error handling dispatch select interaction: {}", selectId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    if (DispatchPanelView.BUTTON_CREATE_ORDER.equals(buttonId)) {
      handleCreateOrder(event);
      return;
    }

    if (buttonId.startsWith(DispatchPanelView.BUTTON_CONFIRM_ORDER_PREFIX)) {
      handleOrderConfirmation(event, buttonId);
    }
  }

  private boolean isDispatchSelect(String selectId) {
    return DispatchPanelView.SELECT_ESCORT_USER.equals(selectId)
        || DispatchPanelView.SELECT_CUSTOMER_USER.equals(selectId);
  }

  private void handleEscortUserSelect(EntitySelectInteractionEvent event, SessionState state) {
    List<User> users = event.getMentions().getUsers();
    if (users.isEmpty()) {
      event.reply("請選擇護航者").setEphemeral(true).queue();
      return;
    }

    User user = users.get(0);
    state.escortUserId = user.getIdLong();
    state.escortUserMention = user.getAsMention();

    refreshPanel(event, state);
  }

  private void handleCustomerUserSelect(EntitySelectInteractionEvent event, SessionState state) {
    List<User> users = event.getMentions().getUsers();
    if (users.isEmpty()) {
      event.reply("請選擇客戶").setEphemeral(true).queue();
      return;
    }

    User user = users.get(0);
    state.customerUserId = user.getIdLong();
    state.customerUserMention = user.getAsMention();

    refreshPanel(event, state);
  }

  private void refreshPanel(EntitySelectInteractionEvent event, SessionState state) {
    event
        .editMessageEmbeds(
            DispatchPanelView.buildPanelEmbed(
                state.escortUserMention, state.customerUserMention, state.getStatusMessage()))
        .setComponents(DispatchPanelView.buildPanelComponents(state.canCreateOrder()))
        .queue();
  }

  private void handleCreateOrder(ButtonInteractionEvent event) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    long adminUserId = event.getUser().getIdLong();
    String sessionKey = getSessionKey(adminUserId, guildId);

    SessionState state = sessionStates.get(sessionKey);
    if (state == null || !state.hasCompleteSelection()) {
      event.reply("請先完整選擇護航者與客戶").setEphemeral(true).queue();
      return;
    }

    if (state.isSameUser()) {
      event.reply("護航者與客戶不能是同一人").setEphemeral(true).queue();
      return;
    }

    event
        .deferReply(true)
        .queue(
            hook ->
                validateMembersAndCreateOrder(event, hook, sessionKey, guildId, adminUserId, state),
            failure -> LOG.warn("Failed to defer dispatch order creation interaction", failure));
  }

  private void validateMembersAndCreateOrder(
      ButtonInteractionEvent event,
      InteractionHook hook,
      String sessionKey,
      long guildId,
      long adminUserId,
      SessionState state) {

    event
        .getGuild()
        .retrieveMemberById(state.escortUserId)
        .queue(
            escortMember ->
                validateCustomerMember(
                    event, hook, sessionKey, guildId, adminUserId, state, escortMember),
            failure -> hook.sendMessage("找不到被派單的護航者，請確認該成員仍在伺服器中").setEphemeral(true).queue());
  }

  private void validateCustomerMember(
      ButtonInteractionEvent event,
      InteractionHook hook,
      String sessionKey,
      long guildId,
      long adminUserId,
      SessionState state,
      Member escortMember) {
    event
        .getGuild()
        .retrieveMemberById(state.customerUserId)
        .queue(
            customerMember ->
                createOrderAndNotifyEscort(
                    hook,
                    sessionKey,
                    guildId,
                    adminUserId,
                    state,
                    escortMember.getUser(),
                    customerMember.getUser()),
            failure -> hook.sendMessage("找不到指定客戶，請確認該成員仍在伺服器中").setEphemeral(true).queue());
  }

  private void createOrderAndNotifyEscort(
      InteractionHook hook,
      String sessionKey,
      long guildId,
      long adminUserId,
      SessionState state,
      User escortUser,
      User customerUser) {

    Result<EscortDispatchOrder, DomainError> result =
        orderService.createOrder(guildId, adminUserId, state.escortUserId, state.customerUserId);

    if (result.isErr()) {
      hook.sendMessage("建立派單失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder order = result.getValue();
    sendPendingOrderDm(hook, sessionKey, order, escortUser, customerUser);
  }

  private void sendPendingOrderDm(
      InteractionHook hook,
      String sessionKey,
      EscortDispatchOrder order,
      User escortUser,
      User customerUser) {
    String confirmButtonId = DispatchPanelView.BUTTON_CONFIRM_ORDER_PREFIX + order.orderNumber();
    MessageEmbed dmEmbed = buildEscortPendingEmbed(order, customerUser.getAsMention());

    escortUser
        .openPrivateChannel()
        .queue(
            channel ->
                channel
                    .sendMessageEmbeds(dmEmbed)
                    .addActionRow(Button.success(confirmButtonId, "✅ 確認接單"))
                    .queue(
                        success -> {
                          sessionStates.remove(sessionKey);
                          hook.sendMessage("✅ 派單已建立：`" + order.orderNumber() + "`，已通知護航者。")
                              .setEphemeral(true)
                              .queue();
                        },
                        failure -> {
                          sessionStates.remove(sessionKey);
                          LOG.warn(
                              "Escort DM send failed: orderNumber={}, escortUserId={}",
                              order.orderNumber(),
                              escortUser.getIdLong(),
                              failure);
                          hook.sendMessage("⚠️ 派單已建立：`" + order.orderNumber() + "`，但無法私訊護航者，請手動通知。")
                              .setEphemeral(true)
                              .queue();
                        }),
            failure -> {
              sessionStates.remove(sessionKey);
              LOG.warn(
                  "Failed to open escort DM channel: orderNumber={}, escortUserId={}",
                  order.orderNumber(),
                  escortUser.getIdLong(),
                  failure);
              hook.sendMessage("⚠️ 派單已建立：`" + order.orderNumber() + "`，但無法開啟護航者私訊，請手動通知。")
                  .setEphemeral(true)
                  .queue();
            });
  }

  private void handleOrderConfirmation(ButtonInteractionEvent event, String buttonId) {
    if (event.isFromGuild()) {
      event.reply("請在機器人私訊中確認接單").setEphemeral(true).queue();
      return;
    }

    String orderNumber =
        buttonId.substring(DispatchPanelView.BUTTON_CONFIRM_ORDER_PREFIX.length()).trim();
    if (orderNumber.isBlank()) {
      event.reply("訂單編號無效").setEphemeral(true).queue();
      return;
    }

    Result<EscortDispatchOrder, DomainError> result =
        orderService.confirmOrder(orderNumber, event.getUser().getIdLong());

    if (result.isErr()) {
      event.reply(result.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder confirmedOrder = result.getValue();

    event
        .editMessageEmbeds(buildEscortConfirmedEmbed(confirmedOrder))
        .setComponents()
        .queue(
            success -> notifyConfirmationResult(event, confirmedOrder),
            failure -> {
              LOG.warn(
                  "Failed to edit confirmation message: orderNumber={}",
                  confirmedOrder.orderNumber(),
                  failure);
              notifyConfirmationResult(event, confirmedOrder);
            });
  }

  private void notifyConfirmationResult(ButtonInteractionEvent event, EscortDispatchOrder order) {
    MessageEmbed escortNotice = buildEscortNoticeEmbed(order);
    event
        .getUser()
        .openPrivateChannel()
        .queue(
            channel -> channel.sendMessageEmbeds(escortNotice).queue(),
            failure ->
                LOG.warn(
                    "Failed to notify escort after confirmation: orderNumber={}, escortUserId={}",
                    order.orderNumber(),
                    order.escortUserId(),
                    failure));

    event
        .getJDA()
        .retrieveUserById(order.customerUserId())
        .queue(
            customerUser ->
                customerUser
                    .openPrivateChannel()
                    .queue(
                        channel ->
                            channel
                                .sendMessageEmbeds(
                                    buildCustomerNoticeEmbed(order, event.getUser().getAsMention()))
                                .queue(),
                        failure ->
                            LOG.warn(
                                "Failed to DM customer: orderNumber={}, customerUserId={}",
                                order.orderNumber(),
                                order.customerUserId(),
                                failure)),
            failure ->
                LOG.warn(
                    "Failed to retrieve customer user: orderNumber={}, customerUserId={}",
                    order.orderNumber(),
                    order.customerUserId(),
                    failure));
  }

  private MessageEmbed buildEscortPendingEmbed(EscortDispatchOrder order, String customerMention) {
    return new EmbedBuilder()
        .setTitle("📩 新護航派單通知")
        .setColor(INFO_COLOR)
        .setDescription("你收到一張新的護航訂單，請點擊下方按鈕確認接單。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("客戶", customerMention, false)
        .addField("建立時間", "<t:" + order.createdAt().getEpochSecond() + ":F>", false)
        .setFooter("確認後系統會通知客戶與你")
        .build();
  }

  private MessageEmbed buildEscortConfirmedEmbed(EscortDispatchOrder order) {
    Instant confirmedAt = order.confirmedAt() != null ? order.confirmedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("✅ 已確認接單")
        .setColor(INFO_COLOR)
        .setDescription("你已成功確認此護航訂單。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("確認時間", "<t:" + confirmedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildEscortNoticeEmbed(EscortDispatchOrder order) {
    Instant confirmedAt = order.confirmedAt() != null ? order.confirmedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("🛡️ 訂單通知")
        .setColor(INFO_COLOR)
        .setDescription("系統已完成訂單確認流程。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("客戶", "<@" + order.customerUserId() + ">", false)
        .addField("確認時間", "<t:" + confirmedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildCustomerNoticeEmbed(EscortDispatchOrder order, String escortMention) {
    Instant confirmedAt = order.confirmedAt() != null ? order.confirmedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("📣 護航訂單已確認")
        .setColor(WARNING_COLOR)
        .setDescription("你的護航訂單已由護航者確認。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("護航者", escortMention, false)
        .addField("確認時間", "<t:" + confirmedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private String getSessionKey(long userId, long guildId) {
    return guildId + ":" + userId;
  }

  private static final class SessionState {
    private Long escortUserId;
    private String escortUserMention;

    private Long customerUserId;
    private String customerUserMention;

    boolean hasCompleteSelection() {
      return escortUserId != null && customerUserId != null;
    }

    boolean isSameUser() {
      return escortUserId != null && customerUserId != null && escortUserId.equals(customerUserId);
    }

    boolean canCreateOrder() {
      return hasCompleteSelection() && !isSameUser();
    }

    String getStatusMessage() {
      if (isSameUser()) {
        return "⚠️ 護航者與客戶不能是同一人";
      }
      if (canCreateOrder()) {
        return "✅ 已完成選擇，可建立派單";
      }
      return null;
    }
  }
}
