package ltdjms.discord.aiagent.commands;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.services.MessageSplitter;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.LangChain4jToolExecutedEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * LangChain4J 工具執行事件監聽器。
 *
 * <p>接收 {@link LangChain4jToolExecutedEvent} 並將通知訊息發送到對應的 Discord 討論串。
 */
public final class ToolExecutionListener implements Consumer<DomainEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutionListener.class);

  @Inject
  public ToolExecutionListener() {}

  @Override
  public void accept(DomainEvent event) {
    if (event instanceof LangChain4jToolExecutedEvent e) {
      handleToolExecuted(e);
    }
  }

  private void handleToolExecuted(LangChain4jToolExecutedEvent event) {
    try {
      MessageChannel channel = resolveMessageChannel(event.guildId(), event.channelId());
      if (channel == null) {
        LOGGER.warn("無法取得工具通知頻道: guildId={}, channelId={}", event.guildId(), event.channelId());
        return;
      }

      String message =
          event.success()
              ? "✅ 工具「%s」執行成功".formatted(event.toolName())
              : "❌ 工具「%s」執行失敗：%s".formatted(event.toolName(), event.result());

      List<String> chunks = MessageSplitter.split(message);
      for (String chunk : chunks) {
        channel.sendMessage(chunk).queue();
      }

    } catch (Exception e) {
      LOGGER.error("處理工具執行事件時發生錯誤", e);
    }
  }

  private MessageChannel resolveMessageChannel(long guildId, long channelId) {
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      return null;
    }

    GuildChannel guildChannel = guild.getGuildChannelById(channelId);
    if (guildChannel instanceof MessageChannel messageChannel) {
      return messageChannel;
    }

    return guild.getThreadChannelById(channelId);
  }
}
