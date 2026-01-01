package ltdjms.discord.aiagent.commands;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.services.MessageSplitter;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.AgentCompletedEvent;
import ltdjms.discord.shared.events.AgentFailedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Agent 完成監聽器。
 *
 * <p>監聽 {@link AgentCompletedEvent} 和 {@link AgentFailedEvent}， 將最終回應或錯誤訊息發送給 Discord 用戶。
 */
public final class AgentCompletionListener implements Consumer<DomainEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentCompletionListener.class);

  @Inject
  public AgentCompletionListener() {
    // Dagger 構造函數
  }

  @Override
  public void accept(DomainEvent event) {
    if (event instanceof AgentCompletedEvent e) {
      handleAgentCompleted(e);
    } else if (event instanceof AgentFailedEvent e) {
      handleAgentFailed(e);
    }
  }

  /**
   * 處理 Agent 完成事件。
   *
   * @param event 完成事件
   */
  private void handleAgentCompleted(AgentCompletedEvent event) {
    try {
      MessageChannel channel = resolveMessageChannel(event.guildId(), event.channelId());
      if (channel == null) {
        LOGGER.warn("無法取得可發送訊息的頻道: guildId={}, channelId={}", event.guildId(), event.channelId());
        return;
      }

      List<String> messages = MessageSplitter.split(event.finalResponse());
      for (String message : messages) {
        channel.sendMessage(message).queue();
      }

      LOGGER.info(
          "Agent 完成，已發送最終回應: conversationId={}, length={}",
          event.conversationId(),
          messages.size());

    } catch (Exception e) {
      LOGGER.error("處理 Agent 完成事件時發生錯誤", e);
    }
  }

  /**
   * 處理 Agent 失敗事件。
   *
   * @param event 失敗事件
   */
  private void handleAgentFailed(AgentFailedEvent event) {
    try {
      MessageChannel channel = resolveMessageChannel(event.guildId(), event.channelId());
      if (channel == null) {
        LOGGER.warn("無法取得可發送訊息的頻道: guildId={}, channelId={}", event.guildId(), event.channelId());
        return;
      }

      String errorMessage = "❌ " + event.reason();
      channel.sendMessage(errorMessage).queue();

      LOGGER.warn(
          "Agent 失敗，已發送錯誤訊息: conversationId={}, reason={}", event.conversationId(), event.reason());

    } catch (Exception e) {
      LOGGER.error("處理 Agent 失敗事件時發生錯誤", e);
    }
  }

  /**
   * 解析訊息頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID 字串
   * @return 訊息頻道，如果無法解析則返回 null
   */
  private MessageChannel resolveMessageChannel(long guildId, String channelId) {
    try {
      Guild guild = JDAProvider.getJda().getGuildById(guildId);
      if (guild == null) {
        return null;
      }

      long channelIdLong = Long.parseLong(channelId);
      GuildChannel guildChannel = guild.getGuildChannelById(channelIdLong);
      if (guildChannel instanceof MessageChannel messageChannel) {
        return messageChannel;
      }

      return guild.getThreadChannelById(channelIdLong);
    } catch (NumberFormatException e) {
      LOGGER.warn("無法解析頻道 ID: {}", channelId);
      return null;
    }
  }
}
