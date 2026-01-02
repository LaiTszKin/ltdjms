package ltdjms.discord.aichat.services;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.domain.AIChannelRestriction;
import ltdjms.discord.aichat.domain.AllowedCategory;
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
  public boolean isChannelAllowed(long guildId, long channelId, long categoryId) {
    Result<AIChannelRestriction, DomainError> result = repository.findRestrictionByGuildId(guildId);

    if (result.isErr()) {
      LOG.debug(
          "Failed to check channel allowed for guildId={}, channelId={}, categoryId={}: {}",
          guildId,
          channelId,
          categoryId,
          result.getError().message());
      return false;
    }

    AIChannelRestriction restriction = result.getValue();
    boolean allowed = restriction.isChannelAllowed(channelId, categoryId);

    LOG.debug(
        "Channel allowed check: guildId={}, channelId={}, categoryId={}, allowed={}",
        guildId,
        channelId,
        categoryId,
        allowed);

    return allowed;
  }

  /** 向後相容的覆寫，無類別資訊時預設傳入 0。 */
  @Override
  public boolean isChannelAllowed(long guildId, long channelId) {
    return isChannelAllowed(guildId, channelId, 0L);
  }

  @Override
  public Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId) {
    return repository.findByGuildId(guildId);
  }

  @Override
  public Result<Set<AllowedCategory>, DomainError> getAllowedCategories(long guildId) {
    return repository.findAllowedCategories(guildId);
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
  public Result<AllowedCategory, DomainError> addAllowedCategory(
      long guildId, AllowedCategory category) {
    LOG.info(
        "Adding allowed category: guildId={}, categoryId={}, categoryName={}",
        guildId,
        category.categoryId(),
        category.categoryName());

    Result<AllowedCategory, DomainError> result = repository.addCategory(guildId, category);

    if (result.isOk()) {
      LOG.info(
          "Successfully added allowed category: guildId={}, categoryId={}",
          guildId,
          category.categoryId());
    } else {
      LOG.warn(
          "Failed to add allowed category: guildId={}, categoryId={}, error={}",
          guildId,
          category.categoryId(),
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

  @Override
  public Result<Unit, DomainError> removeAllowedCategory(long guildId, long categoryId) {
    LOG.info("Removing allowed category: guildId={}, categoryId={}", guildId, categoryId);

    Result<Unit, DomainError> result = repository.removeCategory(guildId, categoryId);

    if (result.isOk()) {
      LOG.info(
          "Successfully removed allowed category: guildId={}, categoryId={}", guildId, categoryId);
    } else {
      LOG.warn(
          "Failed to remove allowed category: guildId={}, categoryId={}, error={}",
          guildId,
          categoryId,
          result.getError().message());
    }

    return result;
  }
}
