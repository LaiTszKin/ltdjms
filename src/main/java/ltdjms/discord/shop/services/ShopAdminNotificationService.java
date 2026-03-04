package ltdjms.discord.shop.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

/** Sends admin DM notifications for orders that require manual dispatch. */
public class ShopAdminNotificationService {

  private static final Logger LOG = LoggerFactory.getLogger(ShopAdminNotificationService.class);

  public void notifyAdminsOrderCreated(
      long guildId, long buyerUserId, Product product, String orderType, String orderReference) {
    if (product == null) {
      return;
    }

    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      LOG.warn("Guild not found when notifying admins: guildId={}", guildId);
      return;
    }

    String message =
        buildAdminOrderNotification(guild, buyerUserId, product, orderType, orderReference);
    Set<Long> notified = new HashSet<>();

    List<Member> members = guild.getMembers();
    if (members != null) {
      for (Member member : members) {
        if (!isAdmin(member, guild)) {
          continue;
        }
        User adminUser = member.getUser();
        if (adminUser == null || !notified.add(adminUser.getIdLong())) {
          continue;
        }
        sendAdminNotification(adminUser, message);
      }
    }

    long ownerId = 0L;
    try {
      ownerId = guild.getOwnerIdLong();
    } catch (Exception e) {
      LOG.debug(
          "Unable to resolve guild owner id for order notification: guildId={}",
          guild.getIdLong(),
          e);
    }
    final long finalOwnerId = ownerId;
    if (finalOwnerId > 0 && !notified.contains(finalOwnerId)) {
      guild
          .retrieveMemberById(finalOwnerId)
          .queue(
              ownerMember -> {
                User owner = ownerMember.getUser();
                if (owner != null) {
                  sendAdminNotification(owner, message);
                }
              },
              failure ->
                  LOG.debug(
                      "Failed to retrieve guild owner for order notification: guildId={},"
                          + " ownerId={}",
                      guild.getIdLong(),
                      finalOwnerId,
                      failure));
    }
  }

  private void sendAdminNotification(User adminUser, String message) {
    adminUser
        .openPrivateChannel()
        .queue(
            channel -> channel.sendMessage(message).queue(),
            failure ->
                LOG.debug(
                    "Failed to open admin DM for order notification: adminUserId={}",
                    adminUser.getIdLong(),
                    failure));
  }

  private String buildAdminOrderNotification(
      Guild guild, long buyerUserId, Product product, String orderType, String orderReference) {
    StringBuilder builder = new StringBuilder();
    builder.append("📩 有新訂單發起，請儘速派單\n\n");
    builder
        .append("**伺服器：** ")
        .append(guild.getName())
        .append(" (`")
        .append(guild.getId())
        .append("`)\n");
    builder.append("**買家：** <@").append(buyerUserId).append(">\n");
    builder.append("**商品：** ").append(product.name()).append("\n");
    builder.append("**訂單類型：** ").append(orderType).append("\n");
    if (orderReference != null && !orderReference.isBlank()) {
      builder.append("**訂單編號：** `").append(orderReference).append("`\n");
    }
    builder.append("\n請使用 `/dispatch-panel` 進行派單分配。");
    return builder.toString();
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
}
