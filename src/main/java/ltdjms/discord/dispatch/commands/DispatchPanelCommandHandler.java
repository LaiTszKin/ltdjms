package ltdjms.discord.dispatch.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.SlashCommandListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/** /dispatch-panel 指令：開啟派單面板。 */
public class DispatchPanelCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(DispatchPanelCommandHandler.class);

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    Guild guild = event.getGuild();
    if (!event.isFromGuild() || guild == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    if (!isAdmin(event.getMember(), guild)) {
      event.reply("你沒有權限使用派單面板").setEphemeral(true).queue();
      return;
    }

    long guildId = guild.getIdLong();
    long adminUserId = event.getUser().getIdLong();
    LOG.debug("Opening dispatch panel: guildId={}, adminUserId={}", guildId, adminUserId);

    event
        .replyEmbeds(DispatchPanelView.buildPanelEmbed(null, null, null))
        .addComponents(DispatchPanelView.buildPanelComponents(false))
        .setEphemeral(true)
        .queue();
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
