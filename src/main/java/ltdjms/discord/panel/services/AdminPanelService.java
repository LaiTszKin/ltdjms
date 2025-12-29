package ltdjms.discord.panel.services;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.DiceGameConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Service for admin panel operations. Provides methods for managing member balances, game tokens,
 * and game settings.
 */
public class AdminPanelService {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPanelService.class);

  private final BalanceService balanceService;
  private final BalanceAdjustmentService balanceAdjustmentService;
  private final GameTokenService gameTokenService;
  private final GameTokenTransactionService transactionService;
  private final DiceGame1ConfigRepository diceGame1ConfigRepository;
  private final DiceGame2ConfigRepository diceGame2ConfigRepository;
  private final CurrencyConfigService currencyConfigService;
  private final DomainEventPublisher eventPublisher;
  private final AIChannelRestrictionService aiChannelRestrictionService;

  public AdminPanelService(
      BalanceService balanceService,
      BalanceAdjustmentService balanceAdjustmentService,
      GameTokenService gameTokenService,
      GameTokenTransactionService transactionService,
      DiceGame1ConfigRepository diceGame1ConfigRepository,
      DiceGame2ConfigRepository diceGame2ConfigRepository,
      CurrencyConfigService currencyConfigService,
      DomainEventPublisher eventPublisher,
      AIChannelRestrictionService aiChannelRestrictionService) {
    this.balanceService = balanceService;
    this.balanceAdjustmentService = balanceAdjustmentService;
    this.gameTokenService = gameTokenService;
    this.transactionService = transactionService;
    this.diceGame1ConfigRepository = diceGame1ConfigRepository;
    this.diceGame2ConfigRepository = diceGame2ConfigRepository;
    this.currencyConfigService = currencyConfigService;
    this.eventPublisher = eventPublisher;
    this.aiChannelRestrictionService = aiChannelRestrictionService;
  }

  /**
   * Gets the currency configuration for a guild.
   *
   * @param guildId the guild ID
   * @return the currency configuration with name and icon
   */
  public GuildCurrencyConfig getCurrencyConfig(long guildId) {
    return currencyConfigService.getConfig(guildId);
  }

  /** Gets a member's current currency balance. */
  public Result<Long, DomainError> getMemberBalance(long guildId, long userId) {
    return balanceService.tryGetBalance(guildId, userId).map(view -> view.balance());
  }

  /** Gets a member's current game token balance. */
  public long getMemberTokens(long guildId, long userId) {
    return gameTokenService.getBalance(guildId, userId);
  }

  /** Adjusts a member's currency balance. */
  public Result<BalanceAdjustmentResult, DomainError> adjustBalance(
      long guildId, long userId, String mode, long amount) {
    LOG.debug(
        "Admin panel adjusting balance: guildId={}, userId={}, mode={}, amount={}",
        guildId,
        userId,
        mode,
        amount);

    return switch (mode) {
      case "add" ->
          balanceAdjustmentService
              .tryAdjustBalance(guildId, userId, amount)
              .map(this::toBalanceAdjustmentResult);
      case "deduct" ->
          balanceAdjustmentService
              .tryAdjustBalance(guildId, userId, -amount)
              .map(this::toBalanceAdjustmentResult);
      case "adjust" ->
          balanceAdjustmentService
              .tryAdjustBalanceTo(guildId, userId, amount)
              .map(this::toBalanceAdjustmentResult);
      default -> Result.err(DomainError.invalidInput("Unknown adjustment mode: " + mode));
    };
  }

  private BalanceAdjustmentResult toBalanceAdjustmentResult(
      BalanceAdjustmentService.BalanceAdjustmentResult result) {
    return new BalanceAdjustmentResult(
        result.previousBalance(), result.newBalance(), result.adjustment());
  }

  /**
   * Adjusts a member's game token balance using mode-based adjustment.
   *
   * @param guildId the guild ID
   * @param userId the user ID
   * @param mode the adjustment mode: "add", "deduct", or "adjust"
   * @param amount the amount to add/deduct or target balance for "adjust" mode
   * @return the result of the adjustment
   */
  public Result<TokenAdjustmentResult, DomainError> adjustTokens(
      long guildId, long userId, String mode, long amount) {
    LOG.debug(
        "Admin panel adjusting tokens: guildId={}, userId={}, mode={}, amount={}",
        guildId,
        userId,
        mode,
        amount);

    long previousTokens = gameTokenService.getBalance(guildId, userId);

    long actualAdjustment =
        switch (mode) {
          case "add" -> amount;
          case "deduct" -> -amount;
          case "adjust" -> amount - previousTokens;
          default -> {
            yield 0L;
          }
        };

    if (mode.equals("adjust") && amount < 0) {
      return Result.err(DomainError.invalidInput("目標代幣餘額不可為負數"));
    }

    return gameTokenService
        .tryAdjustTokens(guildId, userId, actualAdjustment)
        .map(
            result -> {
              transactionService.recordTransaction(
                  guildId,
                  userId,
                  actualAdjustment,
                  result.newTokens(),
                  GameTokenTransaction.Source.ADMIN_ADJUSTMENT,
                  null);

              return new TokenAdjustmentResult(
                  previousTokens, result.newTokens(), actualAdjustment);
            });
  }

  /** Gets the full configuration for dice-game-1. */
  public DiceGame1Config getDiceGame1Config(long guildId) {
    return diceGame1ConfigRepository.findOrCreateDefault(guildId);
  }

  /** Gets the full configuration for dice-game-2. */
  public DiceGame2Config getDiceGame2Config(long guildId) {
    return diceGame2ConfigRepository.findOrCreateDefault(guildId);
  }

  /** Updates dice-game-1 configuration. */
  public Result<DiceGame1Config, DomainError> updateDiceGame1Config(
      long guildId, Long minTokens, Long maxTokens, Long rewardPerDice) {
    LOG.debug(
        "Admin panel updating dice-game-1 config: guildId={}, min={}, max={}, reward={}",
        guildId,
        minTokens,
        maxTokens,
        rewardPerDice);

    try {
      DiceGame1Config current = diceGame1ConfigRepository.findOrCreateDefault(guildId);
      DiceGame1Config updated = current;

      if (minTokens != null || maxTokens != null) {
        long newMin = minTokens != null ? minTokens : current.minTokensPerPlay();
        long newMax = maxTokens != null ? maxTokens : current.maxTokensPerPlay();
        updated = diceGame1ConfigRepository.updateTokensPerPlayRange(guildId, newMin, newMax);
      }

      if (rewardPerDice != null) {
        updated = diceGame1ConfigRepository.updateRewardPerDiceValue(guildId, rewardPerDice);
      }

      LOG.info("Dice-game-1 config updated: guildId={}", guildId);

      // Publish event after successful update
      eventPublisher.publish(
          new DiceGameConfigChangedEvent(guildId, DiceGameConfigChangedEvent.GameType.DICE_GAME_1));

      return Result.ok(updated);
    } catch (IllegalArgumentException e) {
      return Result.err(DomainError.invalidInput(e.getMessage()));
    }
  }

  /** Updates dice-game-2 configuration. */
  public Result<DiceGame2Config, DomainError> updateDiceGame2Config(
      long guildId,
      Long minTokens,
      Long maxTokens,
      Long straightMultiplier,
      Long baseMultiplier,
      Long tripleLowBonus,
      Long tripleHighBonus) {
    LOG.debug(
        "Admin panel updating dice-game-2 config: guildId={}, min={}, max={}, "
            + "straight={}, base={}, tripleLow={}, tripleHigh={}",
        guildId,
        minTokens,
        maxTokens,
        straightMultiplier,
        baseMultiplier,
        tripleLowBonus,
        tripleHighBonus);

    try {
      DiceGame2Config current = diceGame2ConfigRepository.findOrCreateDefault(guildId);
      DiceGame2Config updated = current;

      if (minTokens != null || maxTokens != null) {
        long newMin = minTokens != null ? minTokens : current.minTokensPerPlay();
        long newMax = maxTokens != null ? maxTokens : current.maxTokensPerPlay();
        updated = diceGame2ConfigRepository.updateTokensPerPlayRange(guildId, newMin, newMax);
      }

      if (straightMultiplier != null || baseMultiplier != null) {
        long newStraight =
            straightMultiplier != null ? straightMultiplier : updated.straightMultiplier();
        long newBase = baseMultiplier != null ? baseMultiplier : updated.baseMultiplier();
        updated = diceGame2ConfigRepository.updateMultipliers(guildId, newStraight, newBase);
      }

      if (tripleLowBonus != null || tripleHighBonus != null) {
        long newLow = tripleLowBonus != null ? tripleLowBonus : updated.tripleLowBonus();
        long newHigh = tripleHighBonus != null ? tripleHighBonus : updated.tripleHighBonus();
        updated = diceGame2ConfigRepository.updateTripleBonuses(guildId, newLow, newHigh);
      }

      LOG.info("Dice-game-2 config updated: guildId={}", guildId);

      // Publish event after successful update
      eventPublisher.publish(
          new DiceGameConfigChangedEvent(guildId, DiceGameConfigChangedEvent.GameType.DICE_GAME_2));

      return Result.ok(updated);
    } catch (IllegalArgumentException e) {
      return Result.err(DomainError.invalidInput(e.getMessage()));
    }
  }

  public record BalanceAdjustmentResult(long previousBalance, long newBalance, long adjustment) {
    public String formatMessage(String currencyName, String currencyIcon) {
      String action = adjustment >= 0 ? "增加" : "扣除";
      long displayAmount = Math.abs(adjustment);
      return String.format(
          "%s %,d %s %s\n調整前：%s %,d\n調整後：%s %,d",
          action,
          displayAmount,
          currencyIcon,
          currencyName,
          currencyIcon,
          previousBalance,
          currencyIcon,
          newBalance);
    }
  }

  public record TokenAdjustmentResult(long previousTokens, long newTokens, long adjustment) {
    public String formatMessage() {
      String action = adjustment >= 0 ? "增加" : "扣除";
      long displayAmount = Math.abs(adjustment);
      return String.format(
          "%s %,d 遊戲代幣\n調整前：🎮 %,d\n調整後：🎮 %,d", action, displayAmount, previousTokens, newTokens);
    }
  }

  // ========== AI 頻道設定管理 ==========

  /**
   * 獲取伺服器的所有允許頻道。
   *
   * @param guildId 伺服器 ID
   * @return 允許頻道集合（空集合表示無限制模式）
   */
  public Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId) {
    LOG.debug("Admin panel getting allowed channels for guildId={}", guildId);
    return aiChannelRestrictionService.getAllowedChannels(guildId);
  }

  /**
   * 新增允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param channelName 頻道名稱（用於顯示）
   * @return 成功返回頻道，失敗返回錯誤
   */
  public Result<AllowedChannel, DomainError> addAllowedChannel(
      long guildId, long channelId, String channelName) {
    LOG.info(
        "Admin panel adding allowed channel: guildId={}, channelId={}, channelName={}",
        guildId,
        channelId,
        channelName);
    AllowedChannel channel = new AllowedChannel(channelId, channelName);
    return aiChannelRestrictionService.addAllowedChannel(guildId, channel);
  }

  /**
   * 移除允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId) {
    LOG.info("Admin panel removing allowed channel: guildId={}, channelId={}", guildId, channelId);
    return aiChannelRestrictionService.removeAllowedChannel(guildId, channelId);
  }
}
