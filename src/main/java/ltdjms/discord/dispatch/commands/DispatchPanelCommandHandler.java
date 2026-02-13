package ltdjms.discord.dispatch.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.SlashCommandListener;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/** /dispatch-panel 指令：開啟派單面板。 */
public class DispatchPanelCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(DispatchPanelCommandHandler.class);

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    long adminUserId = event.getUser().getIdLong();
    LOG.debug("Opening dispatch panel: guildId={}, adminUserId={}", guildId, adminUserId);

    event
        .replyEmbeds(DispatchPanelView.buildPanelEmbed(null, null, null))
        .addComponents(DispatchPanelView.buildPanelComponents(false))
        .setEphemeral(true)
        .queue();
  }
}
