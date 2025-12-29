package ltdjms.discord.aichat.services;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.aichat.persistence.AIChannelRestrictionRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * AI 頻道限制服務預設實作。
 *
 * <p>提供 AI 頻道限制設定的業務邏輯操作，包括：
 *
 * <ul>
 *   <li>檢查頻道是否被允許
 *   <li>獲取允許頻道清單
 *   <li>新增/移除允許頻道
 * </ul>
 */
public class DefaultAIChannelRestrictionService implements AIChannelRestrictionService {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultAIChannelRestrictionService.class);

  private final AIChannelRestrictionRepository repository;

  public DefaultAIChannelRestrictionService(AIChannelRestrictionRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean isChannelAllowed(long guildId, long channelId) {
    Result<Set<AllowedChannel>, DomainError> result = repository.findByGuildId(guildId);

    if (result.isErr()) {
      LOG.debug(
          "Failed to check channel allowed for guildId={}, channelId={}: {}",
          guildId,
          channelId,
          result.getError().message());
      return false;
    }

    Set<AllowedChannel> channels = result.getValue();
    // 空清單 = 無限制模式，所有頻道都允許
    if (channels.isEmpty()) {
      return true;
    }

    return channels.stream().anyMatch(c -> c.channelId() == channelId);
  }

  @Override
  public Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId) {
    return repository.findByGuildId(guildId);
  }

  @Override
  public Result<AllowedChannel, DomainError> addAllowedChannel(
      long guildId, AllowedChannel channel) {
    LOG.info(
        "Adding allowed channel: guildId={}, channelId={}, channelName={}",
        guildId,
        channel.channelId(),
        channel.channelName());

    Result<AllowedChannel, DomainError> result = repository.addChannel(guildId, channel);

    if (result.isOk()) {
      LOG.info(
          "Successfully added allowed channel: guildId={}, channelId={}",
          guildId,
          channel.channelId());
    } else {
      LOG.warn(
          "Failed to add allowed channel: guildId={}, channelId={}, error={}",
          guildId,
          channel.channelId(),
          result.getError().message());
    }

    return result;
  }

  @Override
  public Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId) {
    LOG.info("Removing allowed channel: guildId={}, channelId={}", guildId, channelId);

    Result<Unit, DomainError> result = repository.removeChannel(guildId, channelId);

    if (result.isOk()) {
      LOG.info(
          "Successfully removed allowed channel: guildId={}, channelId={}", guildId, channelId);
    } else {
      LOG.warn(
          "Failed to remove allowed channel: guildId={}, channelId={}, error={}",
          guildId,
          channelId,
          result.getError().message());
    }

    return result;
  }
}
