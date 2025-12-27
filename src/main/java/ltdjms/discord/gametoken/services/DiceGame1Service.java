package ltdjms.discord.gametoken.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Service for the dice-game-1 mini-game. Handles dice rolling, reward calculation, and currency
 * distribution.
 *
 * <p>The number of dice rolled is determined by the number of tokens spent: 1 token = 1 dice.
 */
public class DiceGame1Service {

  private static final Logger LOG = LoggerFactory.getLogger(DiceGame1Service.class);

  private final MemberCurrencyAccountRepository currencyRepository;
  private final CurrencyTransactionService transactionService;
  private final DomainEventPublisher eventPublisher;
  private final Random random;

  public DiceGame1Service(
      MemberCurrencyAccountRepository currencyRepository,
      CurrencyTransactionService transactionService,
      DomainEventPublisher eventPublisher) {
    this(currencyRepository, transactionService, eventPublisher, new Random());
  }

  /** Constructor with injectable Random for testing. */
  public DiceGame1Service(
      MemberCurrencyAccountRepository currencyRepository,
      CurrencyTransactionService transactionService,
      DomainEventPublisher eventPublisher,
      Random random) {
    this.currencyRepository = currencyRepository;
    this.transactionService = transactionService;
    this.eventPublisher = eventPublisher;
    this.random = random;
  }

  /**
   * Plays the dice game for a member using the provided configuration and token amount. Rolls one
   * dice per token spent and calculates the total reward based on dice values. The reward is added
   * to the member's currency account.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param config the game configuration containing reward multiplier
   * @param diceCount the number of dice to roll (equals tokens spent)
   * @return the game result
   */
  public DiceGameResult play(long guildId, long userId, DiceGame1Config config, int diceCount) {
    LOG.debug(
        "Playing dice-game-1 for guildId={}, userId={}, diceCount={}, rewardPerDice={}",
        guildId,
        userId,
        diceCount,
        config.rewardPerDiceValue());

    // Roll dice
    List<Integer> diceRolls = rollDice(diceCount);

    // Calculate total reward using configured multiplier
    long totalReward = calculateTotalReward(diceRolls, config.rewardPerDiceValue());

    // Apply reward to currency account (may need multiple adjustments due to MAX_ADJUSTMENT_AMOUNT)
    long previousBalance = currencyRepository.findOrCreate(guildId, userId).balance();
    applyRewardToCurrency(guildId, userId, totalReward);
    long newBalance =
        currencyRepository
            .findByGuildIdAndUserId(guildId, userId)
            .map(MemberCurrencyAccount::balance)
            .orElse(previousBalance + totalReward);

    // Record transaction
    if (totalReward > 0) {
      transactionService.recordTransaction(
          guildId,
          userId,
          totalReward,
          newBalance,
          CurrencyTransaction.Source.DICE_GAME_1_WIN,
          null);
    }

    // Publish event
    eventPublisher.publish(new BalanceChangedEvent(guildId, userId, newBalance));

    DiceGameResult result =
        new DiceGameResult(guildId, userId, diceRolls, totalReward, previousBalance, newBalance);

    LOG.info(
        "Dice game completed: guildId={}, userId={}, rolls={}, reward={}, newBalance={}",
        guildId,
        userId,
        diceRolls,
        totalReward,
        newBalance);

    return result;
  }

  /**
   * Rolls the specified number of dice.
   *
   * @param count the number of dice to roll
   * @return list of dice values (1-6)
   */
  List<Integer> rollDice(int count) {
    List<Integer> rolls = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      rolls.add(random.nextInt(6) + 1); // 1-6
    }
    return rolls;
  }

  /**
   * Calculates the total reward based on dice rolls and configured reward multiplier.
   *
   * @param diceRolls the list of dice values
   * @param rewardPerDiceValue the reward multiplier per dice value
   * @return the total reward
   */
  long calculateTotalReward(List<Integer> diceRolls, long rewardPerDiceValue) {
    long sum = 0;
    for (int i = 0; i < diceRolls.size(); i++) {
      sum += diceRolls.get(i);
    }
    return sum * rewardPerDiceValue;
  }

  /**
   * Applies the reward to the member's currency account. If the reward exceeds the max adjustment
   * amount, splits into multiple adjustments.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param totalReward the total reward to apply
   */
  void applyRewardToCurrency(long guildId, long userId, long totalReward) {
    long remaining = totalReward;
    long maxAdjustment = MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT;

    while (remaining > 0) {
      long adjustment = Math.min(remaining, maxAdjustment);
      currencyRepository.adjustBalance(guildId, userId, adjustment);
      remaining -= adjustment;
    }
  }

  /** Result of a dice game. */
  public record DiceGameResult(
      long guildId,
      long userId,
      List<Integer> diceRolls,
      long totalReward,
      long previousBalance,
      long newBalance) {
    /** Formats the result as a Discord message. */
    public String formatMessage(String currencyIcon, String currencyName) {
      StringBuilder sb = new StringBuilder();
      sb.append("**Dice Game Results**\n");
      sb.append("Rolls: ");

      for (int i = 0; i < diceRolls.size(); i++) {
        int roll = diceRolls.get(i);
        sb.append(diceEmoji(roll));
        if (i < diceRolls.size() - 1) {
          sb.append(" ");
        }
      }

      sb.append("\n\n");
      sb.append(
          String.format("Total Reward: %s %,d %s\n", currencyIcon, totalReward, currencyName));
      sb.append(String.format("New Balance: %s %,d %s", currencyIcon, newBalance, currencyName));

      return sb.toString();
    }

    private String diceEmoji(int value) {
      return switch (value) {
        case 1 -> ":one:";
        case 2 -> ":two:";
        case 3 -> ":three:";
        case 4 -> ":four:";
        case 5 -> ":five:";
        case 6 -> ":six:";
        default -> String.valueOf(value);
      };
    }
  }
}
