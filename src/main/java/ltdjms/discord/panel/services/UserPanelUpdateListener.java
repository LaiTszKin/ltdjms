package ltdjms.discord.panel.services;

import java.awt.Color;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.GameTokenChangedEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Listener for domain events that triggers real-time updates for active user panels. */
public class UserPanelUpdateListener implements Consumer<DomainEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(UserPanelUpdateListener.class);
  private static final Color EMBED_COLOR = new Color(0x5865F2); // Discord blurple

  private final PanelSessionManager sessionManager;
  private final UserPanelService userPanelService;

  public UserPanelUpdateListener(
      PanelSessionManager sessionManager, UserPanelService userPanelService) {
    this.sessionManager = sessionManager;
    this.userPanelService = userPanelService;
  }

  @Override
  public void accept(DomainEvent event) {
    if (event instanceof BalanceChangedEvent e) {
      updateUserPanel(e.guildId(), e.userId());
    } else if (event instanceof GameTokenChangedEvent e) {
      updateUserPanel(e.guildId(), e.userId());
    } else if (event instanceof CurrencyConfigChangedEvent e) {
      updateAllGuildPanels(e.guildId());
    }
  }

  private void updateAllGuildPanels(long guildId) {
    LOG.debug("Updating all user panels for guildId={} due to currency config change", guildId);
    sessionManager.updatePanelsByGuildWithContext(
        guildId,
        ctx -> {
          Result<UserPanelView, DomainError> result =
              userPanelService.getUserPanelView(guildId, ctx.userId());
          if (result.isOk()) {
            UserPanelView view = result.getValue();
            MessageEmbed embed = buildPanelEmbed(view, ctx.userMention());
            ctx.hook()
                .editOriginalEmbeds(embed)
                .queue(
                    msg -> LOG.trace("Updated panel message for userId={}", ctx.userId()),
                    error ->
                        LOG.warn(
                            "Failed to edit panel message for userId={}", ctx.userId(), error));
          } else {
            LOG.warn(
                "Failed to fetch user panel view during guild-wide update: {}", result.getError());
          }
        });
  }

  private void updateUserPanel(long guildId, long userId) {
    sessionManager.updatePanel(
        guildId,
        userId,
        (hook, userMention) -> {
          LOG.debug("Updating user panel for guildId={}, userId={}", guildId, userId);

          Result<UserPanelView, DomainError> result =
              userPanelService.getUserPanelView(guildId, userId);

          if (result.isOk()) {
            UserPanelView view = result.getValue();
            MessageEmbed embed = buildPanelEmbed(view, userMention);

            // Edit the original message
            hook.editOriginalEmbeds(embed)
                .queue(
                    msg -> LOG.trace("Updated panel message for userId={}", userId),
                    error -> LOG.warn("Failed to edit panel message", error));
          } else {
            LOG.warn("Failed to fetch user panel view during update: {}", result.getError());
          }
        });
  }

  private MessageEmbed buildPanelEmbed(UserPanelView view, String userMention) {
    return new EmbedBuilder()
        .setTitle(view.getEmbedTitle())
        .setDescription(userMention + " 的帳戶資訊")
        .setColor(EMBED_COLOR)
        .addField(view.getCurrencyFieldName(), view.formatCurrencyField(), true)
        .addField(view.getGameTokensFieldName(), view.formatGameTokensField(), true)
        .setFooter("點擊下方按鈕查看流水紀錄")
        .build();
  }
}
