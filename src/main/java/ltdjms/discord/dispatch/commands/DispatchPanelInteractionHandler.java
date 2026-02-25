package ltdjms.discord.dispatch.commands;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.DispatchAfterSalesStaffService;
import ltdjms.discord.dispatch.services.EscortDispatchOrderService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * 派單面板互動處理器：
 *
 * <ul>
 *   <li>管理員面板上的成員選取
 *   <li>建立派單與歷史查詢
 *   <li>護航者私訊中的確認接單、送出完成
 *   <li>客戶私訊中的確認完成、申請售後
 *   <li>售後私訊中的接手與結案
 * </ul>
 */
public class DispatchPanelInteractionHandler extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(DispatchPanelInteractionHandler.class);

  private static final Color INFO_COLOR = new Color(0x57F287);
  private static final Color WARNING_COLOR = new Color(0xFEE75C);
  private static final Color ERROR_COLOR = new Color(0xED4245);

  private final EscortDispatchOrderService orderService;
  private final DispatchAfterSalesStaffService afterSalesStaffService;
  private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

  public DispatchPanelInteractionHandler(
      EscortDispatchOrderService orderService,
      DispatchAfterSalesStaffService afterSalesStaffService) {
    this.orderService = orderService;
    this.afterSalesStaffService = afterSalesStaffService;
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

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用派單面板").setEphemeral(true).queue();
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
    if (DispatchPanelView.BUTTON_HISTORY.equals(buttonId)) {
      handleHistory(event);
      return;
    }
    if (buttonId.startsWith(DispatchPanelView.BUTTON_CONFIRM_ORDER_PREFIX)) {
      handleOrderConfirmation(event, buttonId);
      return;
    }
    if (buttonId.startsWith(DispatchPanelView.BUTTON_COMPLETE_ORDER_PREFIX)) {
      handleEscortCompletionRequest(event, buttonId);
      return;
    }
    if (buttonId.startsWith(DispatchPanelView.BUTTON_CUSTOMER_CONFIRM_COMPLETION_PREFIX)) {
      handleCustomerCompletionConfirmation(event, buttonId);
      return;
    }
    if (buttonId.startsWith(DispatchPanelView.BUTTON_CUSTOMER_REQUEST_AFTER_SALES_PREFIX)) {
      handleCustomerAfterSalesRequest(event, buttonId);
      return;
    }
    if (buttonId.startsWith(DispatchPanelView.BUTTON_AFTER_SALES_CLAIM_PREFIX)) {
      handleAfterSalesClaim(event, buttonId);
      return;
    }
    if (buttonId.startsWith(DispatchPanelView.BUTTON_AFTER_SALES_CLOSE_PREFIX)) {
      handleAfterSalesClose(event, buttonId);
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

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用派單面板").setEphemeral(true).queue();
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

  private void handleHistory(ButtonInteractionEvent event) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    if (!isAdmin(event.getMember(), event.getGuild())) {
      event.reply("你沒有權限使用派單面板").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    Result<List<EscortDispatchOrder>, DomainError> result =
        orderService.findRecentOrders(guildId, 10);

    if (result.isErr()) {
      event.reply("查詢歷史失敗：" + result.getError().message()).setEphemeral(true).queue();
      return;
    }

    event.replyEmbeds(buildHistoryEmbed(result.getValue())).setEphemeral(true).queue();
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
        extractOrderNumber(buttonId, DispatchPanelView.BUTTON_CONFIRM_ORDER_PREFIX);
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
    String completeButtonId =
        DispatchPanelView.BUTTON_COMPLETE_ORDER_PREFIX + confirmedOrder.orderNumber();

    event
        .editMessageEmbeds(buildEscortConfirmedEmbed(confirmedOrder))
        .setComponents(ActionRow.of(Button.primary(completeButtonId, "🏁 完成訂單")))
        .queue(
            success -> notifyCustomerOrderConfirmed(event, confirmedOrder),
            failure -> {
              LOG.warn(
                  "Failed to update escort confirmation message: orderNumber={}",
                  confirmedOrder.orderNumber(),
                  failure);
              notifyCustomerOrderConfirmed(event, confirmedOrder);
            });
  }

  private void handleEscortCompletionRequest(ButtonInteractionEvent event, String buttonId) {
    if (event.isFromGuild()) {
      event.reply("請在機器人私訊中操作").setEphemeral(true).queue();
      return;
    }

    String orderNumber =
        extractOrderNumber(buttonId, DispatchPanelView.BUTTON_COMPLETE_ORDER_PREFIX);
    if (orderNumber.isBlank()) {
      event.reply("訂單編號無效").setEphemeral(true).queue();
      return;
    }

    Result<EscortDispatchOrder, DomainError> result =
        orderService.requestCompletion(orderNumber, event.getUser().getIdLong());
    if (result.isErr()) {
      event.reply(result.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder order = result.getValue();
    event
        .editMessageEmbeds(buildEscortCompletionRequestedEmbed(order))
        .setComponents()
        .queue(
            success ->
                notifyCustomerCompletionOptions(
                    order, event.getUser().getAsMention(), event.getJDA()),
            failure -> {
              LOG.warn(
                  "Failed to update escort completion request message: orderNumber={}",
                  order.orderNumber(),
                  failure);
              notifyCustomerCompletionOptions(
                  order, event.getUser().getAsMention(), event.getJDA());
            });
  }

  private void handleCustomerCompletionConfirmation(ButtonInteractionEvent event, String buttonId) {
    if (event.isFromGuild()) {
      event.reply("請在機器人私訊中操作").setEphemeral(true).queue();
      return;
    }

    String orderNumber =
        extractOrderNumber(buttonId, DispatchPanelView.BUTTON_CUSTOMER_CONFIRM_COMPLETION_PREFIX);
    if (orderNumber.isBlank()) {
      event.reply("訂單編號無效").setEphemeral(true).queue();
      return;
    }

    Result<EscortDispatchOrder, DomainError> result =
        orderService.customerConfirmCompletion(orderNumber, event.getUser().getIdLong());
    if (result.isErr()) {
      event.reply(result.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder order = result.getValue();
    event
        .editMessageEmbeds(buildCustomerCompletedEmbed(order))
        .setComponents()
        .queue(
            success -> notifyEscortOrderCompleted(order, event.getJDA()),
            failure -> {
              LOG.warn(
                  "Failed to update customer completion message: orderNumber={}",
                  order.orderNumber(),
                  failure);
              notifyEscortOrderCompleted(order, event.getJDA());
            });
  }

  private void handleCustomerAfterSalesRequest(ButtonInteractionEvent event, String buttonId) {
    if (event.isFromGuild()) {
      event.reply("請在機器人私訊中操作").setEphemeral(true).queue();
      return;
    }

    String orderNumber =
        extractOrderNumber(buttonId, DispatchPanelView.BUTTON_CUSTOMER_REQUEST_AFTER_SALES_PREFIX);
    if (orderNumber.isBlank()) {
      event.reply("訂單編號無效").setEphemeral(true).queue();
      return;
    }

    Result<EscortDispatchOrder, DomainError> result =
        orderService.requestAfterSales(orderNumber, event.getUser().getIdLong());
    if (result.isErr()) {
      event.reply(result.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder order = result.getValue();
    AfterSalesNotifyResult notifyResult = notifyAfterSalesStaff(order, event.getJDA());

    event
        .editMessageEmbeds(buildCustomerAfterSalesRequestedEmbed(order, notifyResult.message()))
        .setComponents()
        .queue();
  }

  private void handleAfterSalesClaim(ButtonInteractionEvent event, String buttonId) {
    if (event.isFromGuild()) {
      event.reply("請在機器人私訊中操作").setEphemeral(true).queue();
      return;
    }

    String orderNumber =
        extractOrderNumber(buttonId, DispatchPanelView.BUTTON_AFTER_SALES_CLAIM_PREFIX);
    if (orderNumber.isBlank()) {
      event.reply("訂單編號無效").setEphemeral(true).queue();
      return;
    }

    Optional<EscortDispatchOrder> orderOpt = orderService.findByOrderNumber(orderNumber);
    if (orderOpt.isEmpty()) {
      event.reply("找不到該訂單").setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder currentOrder = orderOpt.get();
    long userId = event.getUser().getIdLong();
    if (!afterSalesStaffService.isAfterSalesStaff(currentOrder.guildId(), userId)) {
      event.reply("你不是此伺服器設定的售後人員").setEphemeral(true).queue();
      return;
    }

    Result<EscortDispatchOrder, DomainError> claimResult =
        orderService.claimAfterSales(orderNumber, userId);
    if (claimResult.isErr()) {
      event.reply(claimResult.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder claimedOrder = claimResult.getValue();
    String closeButtonId =
        DispatchPanelView.BUTTON_AFTER_SALES_CLOSE_PREFIX + claimedOrder.orderNumber();

    event
        .editMessageEmbeds(
            buildAfterSalesClaimedEmbed(claimedOrder, event.getUser().getAsMention()))
        .setComponents(ActionRow.of(Button.success(closeButtonId, "✅ 完成 / close file")))
        .queue(
            success ->
                notifyCustomerAfterSalesAssigned(
                    claimedOrder, event.getUser().getAsMention(), event.getJDA()),
            failure -> {
              LOG.warn(
                  "Failed to update after-sales claim message: orderNumber={}",
                  claimedOrder.orderNumber(),
                  failure);
              notifyCustomerAfterSalesAssigned(
                  claimedOrder, event.getUser().getAsMention(), event.getJDA());
            });
  }

  private void handleAfterSalesClose(ButtonInteractionEvent event, String buttonId) {
    if (event.isFromGuild()) {
      event.reply("請在機器人私訊中操作").setEphemeral(true).queue();
      return;
    }

    String orderNumber =
        extractOrderNumber(buttonId, DispatchPanelView.BUTTON_AFTER_SALES_CLOSE_PREFIX);
    if (orderNumber.isBlank()) {
      event.reply("訂單編號無效").setEphemeral(true).queue();
      return;
    }

    Result<EscortDispatchOrder, DomainError> closeResult =
        orderService.closeAfterSales(orderNumber, event.getUser().getIdLong());
    if (closeResult.isErr()) {
      event.reply(closeResult.getError().message()).setEphemeral(true).queue();
      return;
    }

    EscortDispatchOrder closedOrder = closeResult.getValue();
    event
        .editMessageEmbeds(buildAfterSalesClosedEmbed(closedOrder))
        .setComponents()
        .queue(
            success ->
                notifyCustomerAfterSalesClosed(
                    closedOrder, event.getUser().getAsMention(), event.getJDA()),
            failure -> {
              LOG.warn(
                  "Failed to update after-sales closed message: orderNumber={}",
                  closedOrder.orderNumber(),
                  failure);
              notifyCustomerAfterSalesClosed(
                  closedOrder, event.getUser().getAsMention(), event.getJDA());
            });
  }

  private void notifyCustomerOrderConfirmed(
      ButtonInteractionEvent event, EscortDispatchOrder order) {
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
                                    buildCustomerOrderConfirmedEmbed(
                                        order, event.getUser().getAsMention()))
                                .queue(),
                        failure ->
                            LOG.warn(
                                "Failed to open customer DM channel for confirmation notice:"
                                    + " orderNumber={}, customerUserId={}",
                                order.orderNumber(),
                                order.customerUserId(),
                                failure)),
            failure ->
                LOG.warn(
                    "Failed to retrieve customer user for confirmation notice: orderNumber={},"
                        + " customerUserId={}",
                    order.orderNumber(),
                    order.customerUserId(),
                    failure));
  }

  private void notifyCustomerCompletionOptions(
      EscortDispatchOrder order, String escortMention, net.dv8tion.jda.api.JDA jda) {
    String customerCompleteButtonId =
        DispatchPanelView.BUTTON_CUSTOMER_CONFIRM_COMPLETION_PREFIX + order.orderNumber();
    String afterSalesButtonId =
        DispatchPanelView.BUTTON_CUSTOMER_REQUEST_AFTER_SALES_PREFIX + order.orderNumber();

    jda.retrieveUserById(order.customerUserId())
        .queue(
            customerUser ->
                customerUser
                    .openPrivateChannel()
                    .queue(
                        channel ->
                            channel
                                .sendMessageEmbeds(
                                    buildCustomerCompletionActionEmbed(order, escortMention))
                                .addActionRow(
                                    Button.success(customerCompleteButtonId, "✅ 確認完成"),
                                    Button.secondary(afterSalesButtonId, "🧰 申請售後"))
                                .queue(),
                        failure ->
                            LOG.warn(
                                "Failed to open customer DM channel for completion actions:"
                                    + " orderNumber={}, customerUserId={}",
                                order.orderNumber(),
                                order.customerUserId(),
                                failure)),
            failure ->
                LOG.warn(
                    "Failed to retrieve customer user for completion actions: orderNumber={},"
                        + " customerUserId={}",
                    order.orderNumber(),
                    order.customerUserId(),
                    failure));
  }

  private void notifyEscortOrderCompleted(EscortDispatchOrder order, net.dv8tion.jda.api.JDA jda) {
    jda.retrieveUserById(order.escortUserId())
        .queue(
            escortUser ->
                escortUser
                    .openPrivateChannel()
                    .queue(
                        channel ->
                            channel
                                .sendMessageEmbeds(buildEscortOrderCompletedEmbed(order))
                                .queue(),
                        failure ->
                            LOG.warn(
                                "Failed to notify escort order completion: orderNumber={},"
                                    + " escortUserId={}",
                                order.orderNumber(),
                                order.escortUserId(),
                                failure)),
            failure ->
                LOG.warn(
                    "Failed to retrieve escort user for completion notice: orderNumber={},"
                        + " escortUserId={}",
                    order.orderNumber(),
                    order.escortUserId(),
                    failure));
  }

  private AfterSalesNotifyResult notifyAfterSalesStaff(
      EscortDispatchOrder order, net.dv8tion.jda.api.JDA jda) {
    Result<Set<Long>, DomainError> staffResult =
        afterSalesStaffService.getStaffUserIds(order.guildId());
    if (staffResult.isErr()) {
      LOG.warn(
          "Failed to query after-sales staff, orderNumber={}, reason={}",
          order.orderNumber(),
          staffResult.getError().message());
      return new AfterSalesNotifyResult("⚠️ 已送出售後申請，但查詢售後人員失敗，請聯絡管理員。");
    }

    Set<Long> staffUserIds = staffResult.getValue();
    if (staffUserIds.isEmpty()) {
      return new AfterSalesNotifyResult("⚠️ 已送出售後申請，但目前尚未設定售後人員。");
    }

    List<Long> onlineStaffUserIds = findOnlineAfterSalesStaff(order.guildId(), staffUserIds, jda);
    List<Long> targetUserIds =
        onlineStaffUserIds.isEmpty() ? new ArrayList<>(staffUserIds) : onlineStaffUserIds;

    String claimButtonId = DispatchPanelView.BUTTON_AFTER_SALES_CLAIM_PREFIX + order.orderNumber();
    MessageEmbed embed = buildAfterSalesNotificationEmbed(order);

    for (Long staffUserId : targetUserIds) {
      jda.retrieveUserById(staffUserId)
          .queue(
              user ->
                  user.openPrivateChannel()
                      .queue(
                          channel ->
                              channel
                                  .sendMessageEmbeds(embed)
                                  .addActionRow(Button.primary(claimButtonId, "🧰 接手案件"))
                                  .queue(),
                          failure ->
                              LOG.warn(
                                  "Failed to DM after-sales staff: orderNumber={}, staffUserId={}",
                                  order.orderNumber(),
                                  staffUserId,
                                  failure)),
              failure ->
                  LOG.warn(
                      "Failed to retrieve after-sales staff user: orderNumber={}, staffUserId={}",
                      order.orderNumber(),
                      staffUserId,
                      failure));
    }

    if (onlineStaffUserIds.isEmpty()) {
      return new AfterSalesNotifyResult("✅ 已通知全部售後人員，等待接手。");
    }
    return new AfterSalesNotifyResult("✅ 已通知 " + onlineStaffUserIds.size() + " 位在線售後人員，等待接手。");
  }

  private List<Long> findOnlineAfterSalesStaff(
      long guildId, Set<Long> staffUserIds, net.dv8tion.jda.api.JDA jda) {
    Guild guild = jda.getGuildById(guildId);
    if (guild == null) {
      return List.of();
    }

    List<Long> onlineStaffUserIds = new ArrayList<>();
    for (Long staffUserId : staffUserIds) {
      try {
        Member member = guild.retrieveMemberById(staffUserId).complete();
        if (member != null && member.getOnlineStatus() == OnlineStatus.ONLINE) {
          onlineStaffUserIds.add(staffUserId);
        }
      } catch (Exception e) {
        LOG.debug(
            "Failed to resolve after-sales staff online status: guildId={}, staffUserId={}",
            guildId,
            staffUserId,
            e);
      }
    }
    return onlineStaffUserIds;
  }

  private void notifyCustomerAfterSalesAssigned(
      EscortDispatchOrder order, String assigneeMention, net.dv8tion.jda.api.JDA jda) {
    jda.retrieveUserById(order.customerUserId())
        .queue(
            customerUser ->
                customerUser
                    .openPrivateChannel()
                    .queue(
                        channel ->
                            channel
                                .sendMessageEmbeds(
                                    new EmbedBuilder()
                                        .setTitle("🧰 售後已接手")
                                        .setColor(INFO_COLOR)
                                        .setDescription("你的售後申請已有專人接手。")
                                        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
                                        .addField("接手售後", assigneeMention, false)
                                        .build())
                                .queue(),
                        failure ->
                            LOG.warn(
                                "Failed to DM customer after after-sales assigned: orderNumber={},"
                                    + " customerUserId={}",
                                order.orderNumber(),
                                order.customerUserId(),
                                failure)),
            failure ->
                LOG.warn(
                    "Failed to retrieve customer for after-sales assigned message: orderNumber={},"
                        + " customerUserId={}",
                    order.orderNumber(),
                    order.customerUserId(),
                    failure));
  }

  private void notifyCustomerAfterSalesClosed(
      EscortDispatchOrder order, String closerMention, net.dv8tion.jda.api.JDA jda) {
    jda.retrieveUserById(order.customerUserId())
        .queue(
            customerUser ->
                customerUser
                    .openPrivateChannel()
                    .queue(
                        channel ->
                            channel
                                .sendMessageEmbeds(
                                    new EmbedBuilder()
                                        .setTitle("✅ 售後已結案")
                                        .setColor(INFO_COLOR)
                                        .setDescription("你的售後案件已完成處理並結案。")
                                        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
                                        .addField("結案人員", closerMention, false)
                                        .build())
                                .queue(),
                        failure ->
                            LOG.warn(
                                "Failed to DM customer after after-sales closed: orderNumber={},"
                                    + " customerUserId={}",
                                order.orderNumber(),
                                order.customerUserId(),
                                failure)),
            failure ->
                LOG.warn(
                    "Failed to retrieve customer for after-sales closed message: orderNumber={},"
                        + " customerUserId={}",
                    order.orderNumber(),
                    order.customerUserId(),
                    failure));
  }

  private MessageEmbed buildHistoryEmbed(List<EscortDispatchOrder> orders) {
    EmbedBuilder builder = new EmbedBuilder().setTitle("📜 護航派單歷史").setColor(INFO_COLOR);

    if (orders.isEmpty()) {
      return builder.setDescription("目前沒有歷史訂單。").build();
    }

    StringBuilder history = new StringBuilder();
    int index = 1;
    for (EscortDispatchOrder order : orders) {
      history
          .append("**")
          .append(index++)
          .append(".** `")
          .append(order.orderNumber())
          .append("` | ")
          .append(toStatusText(order.status()))
          .append("\n")
          .append("護航：<@")
          .append(order.escortUserId())
          .append(">　客戶：<@")
          .append(order.customerUserId())
          .append(">\n")
          .append("建立：<t:")
          .append(order.createdAt().getEpochSecond())
          .append(":R>\n\n");
    }

    return builder.setDescription(history.toString()).build();
  }

  private String toStatusText(EscortDispatchOrder.Status status) {
    return switch (status) {
      case PENDING_CONFIRMATION -> "等待護航者確認";
      case CONFIRMED -> "護航者已確認";
      case PENDING_CUSTOMER_CONFIRMATION -> "等待客戶確認";
      case COMPLETED -> "已完成";
      case AFTER_SALES_REQUESTED -> "售後待接手";
      case AFTER_SALES_IN_PROGRESS -> "售後處理中";
      case AFTER_SALES_CLOSED -> "售後已結案";
    };
  }

  private MessageEmbed buildEscortPendingEmbed(EscortDispatchOrder order, String customerMention) {
    return new EmbedBuilder()
        .setTitle("📩 新護航派單通知")
        .setColor(INFO_COLOR)
        .setDescription("你收到一張新的護航訂單，請點擊下方按鈕確認接單。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("客戶", customerMention, false)
        .addField("建立時間", "<t:" + order.createdAt().getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildEscortConfirmedEmbed(EscortDispatchOrder order) {
    Instant confirmedAt = order.confirmedAt() != null ? order.confirmedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("✅ 已確認接單")
        .setColor(INFO_COLOR)
        .setDescription("你已成功確認此護航訂單。服務完成後請點擊下方按鈕。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("確認時間", "<t:" + confirmedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildEscortCompletionRequestedEmbed(EscortDispatchOrder order) {
    Instant requestedAt =
        order.completionRequestedAt() != null ? order.completionRequestedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("⏳ 已送出完成請求")
        .setColor(WARNING_COLOR)
        .setDescription("系統已通知客戶確認完成，若客戶 24 小時未回應將自動完成。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("送出時間", "<t:" + requestedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildCustomerOrderConfirmedEmbed(
      EscortDispatchOrder order, String escortMention) {
    Instant confirmedAt = order.confirmedAt() != null ? order.confirmedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("📣 護航訂單已確認")
        .setColor(INFO_COLOR)
        .setDescription("你的護航訂單已由護航者確認。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("護航者", escortMention, false)
        .addField("確認時間", "<t:" + confirmedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildCustomerCompletionActionEmbed(
      EscortDispatchOrder order, String escortMention) {
    Instant requestedAt =
        order.completionRequestedAt() != null ? order.completionRequestedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("🧾 請確認護航訂單狀態")
        .setColor(WARNING_COLOR)
        .setDescription("護航者已提交完成，請選擇確認完成或申請售後。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("護航者", escortMention, false)
        .addField("送出時間", "<t:" + requestedAt.getEpochSecond() + ":F>", false)
        .setFooter("24 小時未確認將視為訂單完成")
        .build();
  }

  private MessageEmbed buildCustomerCompletedEmbed(EscortDispatchOrder order) {
    Instant completedAt = order.completedAt() != null ? order.completedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("✅ 訂單已完成")
        .setColor(INFO_COLOR)
        .setDescription("你已確認此訂單完成。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("完成時間", "<t:" + completedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildEscortOrderCompletedEmbed(EscortDispatchOrder order) {
    Instant completedAt = order.completedAt() != null ? order.completedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("✅ 客戶已確認完成")
        .setColor(INFO_COLOR)
        .setDescription("客戶已確認訂單完成。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("完成時間", "<t:" + completedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildCustomerAfterSalesRequestedEmbed(
      EscortDispatchOrder order, String statusText) {
    Instant requestedAt =
        order.afterSalesRequestedAt() != null ? order.afterSalesRequestedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("🧰 已提交售後申請")
        .setColor(ERROR_COLOR)
        .setDescription(statusText)
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("申請時間", "<t:" + requestedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildAfterSalesNotificationEmbed(EscortDispatchOrder order) {
    Instant requestedAt =
        order.afterSalesRequestedAt() != null ? order.afterSalesRequestedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("🧰 新售後申請")
        .setColor(ERROR_COLOR)
        .setDescription("有客戶提交了護航售後申請，請接手處理。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("護航者", "<@" + order.escortUserId() + ">", true)
        .addField("客戶", "<@" + order.customerUserId() + ">", true)
        .addField("申請時間", "<t:" + requestedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildAfterSalesClaimedEmbed(
      EscortDispatchOrder order, String assigneeMention) {
    Instant assignedAt =
        order.afterSalesAssignedAt() != null ? order.afterSalesAssignedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("🛠️ 已接手售後案件")
        .setColor(INFO_COLOR)
        .setDescription("你已成功接手此售後案件，完成後請點擊 close file。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("接手人員", assigneeMention, false)
        .addField("接手時間", "<t:" + assignedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private MessageEmbed buildAfterSalesClosedEmbed(EscortDispatchOrder order) {
    Instant closedAt =
        order.afterSalesClosedAt() != null ? order.afterSalesClosedAt() : Instant.now();
    return new EmbedBuilder()
        .setTitle("✅ 售後案件已結案")
        .setColor(INFO_COLOR)
        .setDescription("你已完成此售後案件。")
        .addField("訂單編號", "`" + order.orderNumber() + "`", false)
        .addField("結案時間", "<t:" + closedAt.getEpochSecond() + ":F>", false)
        .build();
  }

  private String extractOrderNumber(String buttonId, String prefix) {
    return buttonId.substring(prefix.length()).trim().toUpperCase();
  }

  private String getSessionKey(long userId, long guildId) {
    return guildId + ":" + userId;
  }

  private boolean isAdmin(Member member, Guild guild) {
    if (member == null || guild == null) {
      return false;
    }
    if (member.hasPermission(Permission.ADMINISTRATOR)) {
      return true;
    }
    try {
      return guild.getOwnerIdLong() == member.getIdLong();
    } catch (Exception ignored) {
      return false;
    }
  }

  private record AfterSalesNotifyResult(String message) {}

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
