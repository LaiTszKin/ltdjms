package ltdjms.discord.panel.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.services.MembershipPanelSummary;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.GameTokenChangedEvent;
import ltdjms.discord.shared.events.MembershipPeriodSpendChangedEvent;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Listener for domain events that triggers real-time updates for active user panels. */
public class UserPanelUpdateListener implements Consumer<DomainEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(UserPanelUpdateListener.class);
  private static final String UPDATE_FOOTER = "點擊下方按鈕查看流水紀錄";

  private final PanelSessionManager sessionManager;
  private final UserPanelService userPanelService;
  private final ExecutorService panelUpdateExecutor =
      Executors.newFixedThreadPool(
          4,
          runnable -> {
            Thread thread = new Thread(runnable, "user-panel-update");
            thread.setDaemon(true);
            return thread;
          });

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
      panelUpdateExecutor.execute(() -> updateUserPanel(e.guildId(), e.userId()));
    } else if (event instanceof CurrencyConfigChangedEvent e) {
      updateAllGuildPanels(e.guildId());
    } else if (event instanceof MembershipTierChangedEvent e) {
      panelUpdateExecutor.execute(() -> updatePanelsForUser(e.userId()));
    } else if (event instanceof MembershipPeriodSpendChangedEvent e) {
      panelUpdateExecutor.execute(() -> updatePanelsForUser(e.userId()));
    }
  }

  private void updatePanelsForUser(long userId) {
    LOG.debug("Updating user panels for userId={} due to membership tier change", userId);
    MembershipPanelSummary membershipSummary = userPanelService.getMembershipSummary(userId);
    sessionManager.updatePanelsByUser(
        userId,
        ctx -> {
          Result<UserPanelView, DomainError> result =
              userPanelService.getUserPanelView(ctx.guildId(), ctx.userId(), membershipSummary);
          if (result.isOk()) {
            UserPanelView view = result.getValue();
            MessageEmbed embed =
                UserPanelEmbedBuilder.buildPanelEmbed(view, ctx.userMention(), UPDATE_FOOTER);
            ctx.hook()
                .editOriginalEmbeds(embed)
                .queue(
                    msg -> LOG.trace("Updated panel message for userId={}", ctx.userId()),
                    error ->
                        LOG.warn(
                            "Failed to edit panel message for userId={}", ctx.userId(), error));
          } else {
            LOG.warn(
                "Failed to fetch user panel view during membership update: {}", result.getError());
          }
        });
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
            MessageEmbed embed =
                UserPanelEmbedBuilder.buildPanelEmbed(view, ctx.userMention(), UPDATE_FOOTER);
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
            MessageEmbed embed =
                UserPanelEmbedBuilder.buildPanelEmbed(view, userMention, UPDATE_FOOTER);

            hook.editOriginalEmbeds(embed)
                .queue(
                    msg -> LOG.trace("Updated panel message for userId={}", userId),
                    error -> LOG.warn("Failed to edit panel message", error));
          } else {
            LOG.warn("Failed to fetch user panel view during update: {}", result.getError());
          }
        });
  }
}
