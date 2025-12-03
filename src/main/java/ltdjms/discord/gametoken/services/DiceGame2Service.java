package ltdjms.discord.gametoken.services;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.shared.events.BalanceChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service for the dice-game-2 mini-game.
 * Handles dice rolling, reward calculation with straights and triples, and currency distribution.
 *
 * <p>The number of dice rolled is determined by the number of tokens spent:
 * 1 token = 3 dice.</p>
 *
 * <p>Reward rules:</p>
 * <ul>
 *   <li>Straights (consecutive increasing sequence of length >= 3): sum × straightMultiplier</li>
 *   <li>Triples (exactly 3 consecutive same values):
 *     <ul>
 *       <li>Sum < 10 (values 1-3): tripleLowBonus</li>
 *       <li>Sum >= 10 (values 4-6): tripleHighBonus</li>
 *     </ul>
 *   </li>
 *   <li>4+ consecutive same values: NOT a triple, counted as non-straight</li>
 *   <li>Non-straight/non-triple dice: sum × baseMultiplier</li>
 * </ul>
 */
public class DiceGame2Service {

    private static final Logger LOG = LoggerFactory.getLogger(DiceGame2Service.class);

    private final MemberCurrencyAccountRepository currencyRepository;
    private final CurrencyTransactionService transactionService;
    private final DomainEventPublisher eventPublisher;
    private final Random random;

    public DiceGame2Service(
            MemberCurrencyAccountRepository currencyRepository,
            CurrencyTransactionService transactionService,
            DomainEventPublisher eventPublisher) {
        this(currencyRepository, transactionService, eventPublisher, new Random());
    }

    /**
     * Constructor with injectable Random for testing.
     */
    public DiceGame2Service(
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
     * Plays the dice game for a member using the provided configuration and token amount.
     * Rolls dice (3 per token) and calculates the total reward based on straights, triples, and remaining dice.
     * The reward is added to the member's currency account.
     *
     * @param guildId   the Discord guild ID
     * @param userId    the Discord user ID
     * @param config    the game configuration containing multipliers and bonuses
     * @param diceCount the number of dice to roll (equals tokensSpent * 3)
     * @return the game result
     */
    public DiceGame2Result play(long guildId, long userId, DiceGame2Config config, int diceCount) {
        LOG.debug("Playing dice-game-2 for guildId={}, userId={}, diceCount={}, straightMult={}, baseMult={}, tripleLow={}, tripleHigh={}",
                guildId, userId, diceCount, config.straightMultiplier(), config.baseMultiplier(),
                config.tripleLowBonus(), config.tripleHighBonus());

        // Roll dice
        List<Integer> diceRolls = rollDice(diceCount);

        // Analyze the rolls with configured multipliers
        RewardAnalysis analysis = analyzeRolls(diceRolls, config);

        // Calculate total reward
        long totalReward = analysis.totalReward();

        // Apply reward to currency account
        long previousBalance = currencyRepository.findOrCreate(guildId, userId).balance();
        applyRewardToCurrency(guildId, userId, totalReward);
        long newBalance = currencyRepository.findByGuildIdAndUserId(guildId, userId)
                .map(MemberCurrencyAccount::balance)
                .orElse(previousBalance + totalReward);

        // Record transaction
        if (totalReward > 0) {
            transactionService.recordTransaction(
                    guildId,
                    userId,
                    totalReward,
                    newBalance,
                    CurrencyTransaction.Source.DICE_GAME_2_WIN,
                    null
            );
        }
        
        // Publish event
        eventPublisher.publish(new BalanceChangedEvent(guildId, userId, newBalance));

        DiceGame2Result result = new DiceGame2Result(
                guildId,
                userId,
                diceRolls,
                totalReward,
                previousBalance,
                newBalance,
                analysis.straightSegments(),
                analysis.tripleSegments(),
                analysis.straightReward(),
                analysis.nonStraightReward(),
                analysis.tripleReward()
        );

        LOG.info("Dice game 2 completed: guildId={}, userId={}, rolls={}, reward={}, newBalance={}",
                guildId, userId, diceRolls, totalReward, newBalance);

        return result;
    }

    /**
     * Rolls the specified number of dice for a game.
     *
     * @param count the number of dice to roll
     * @return list of dice values (1-6)
     */
    List<Integer> rollDice(int count) {
        List<Integer> rolls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rolls.add(random.nextInt(6) + 1);  // 1-6
        }
        return rolls;
    }

    /**
     * Analyzes the dice rolls to identify straights, triples, and calculate rewards
     * using configured multipliers.
     *
     * @param diceRolls the list of dice values
     * @param config    the game configuration
     * @return the analysis result
     */
    RewardAnalysis analyzeRolls(List<Integer> diceRolls, DiceGame2Config config) {
        boolean[] usedInStraight = new boolean[diceRolls.size()];
        boolean[] usedInTriple = new boolean[diceRolls.size()];

        // First pass: identify straights (consecutive increasing sequences of length >= 3)
        List<List<Integer>> straightSegments = findStraights(diceRolls, usedInStraight);

        // Second pass: identify triples (exactly 3 consecutive same values)
        // Must not overlap with straights
        List<List<Integer>> tripleSegments = findTriples(diceRolls, usedInStraight, usedInTriple);

        // Calculate rewards using configured multipliers
        long straightReward = calculateStraightReward(straightSegments, config.straightMultiplier());
        long tripleReward = calculateTripleReward(tripleSegments, config.tripleLowBonus(), config.tripleHighBonus());
        long nonStraightSum = calculateNonStraightSum(diceRolls, usedInStraight, usedInTriple);
        long nonStraightReward = nonStraightSum * config.baseMultiplier();

        long totalReward = straightReward + nonStraightReward + tripleReward;

        return new RewardAnalysis(
                straightSegments,
                tripleSegments,
                straightReward,
                nonStraightReward,
                tripleReward,
                totalReward
        );
    }

    /**
     * Finds all straight segments (consecutive increasing sequences of length >= 3).
     */
    private List<List<Integer>> findStraights(List<Integer> diceRolls, boolean[] usedInStraight) {
        List<List<Integer>> straights = new ArrayList<>();

        int i = 0;
        while (i < diceRolls.size()) {
            int start = i;
            // Find the longest increasing sequence starting at i
            while (i + 1 < diceRolls.size() && diceRolls.get(i + 1) == diceRolls.get(i) + 1) {
                i++;
            }
            int length = i - start + 1;

            if (length >= 3) {
                List<Integer> segment = new ArrayList<>();
                for (int j = start; j <= i; j++) {
                    segment.add(diceRolls.get(j));
                    usedInStraight[j] = true;
                }
                straights.add(segment);
            }
            i++;
        }

        return straights;
    }

    /**
     * Finds all triple segments (exactly 3 consecutive same values).
     * A run of 4+ consecutive same values is NOT a triple.
     * Triples cannot overlap with straights.
     */
    private List<List<Integer>> findTriples(List<Integer> diceRolls, boolean[] usedInStraight, boolean[] usedInTriple) {
        List<List<Integer>> triples = new ArrayList<>();

        int i = 0;
        while (i < diceRolls.size()) {
            // Skip if already used in a straight
            if (usedInStraight[i]) {
                i++;
                continue;
            }

            int start = i;
            int value = diceRolls.get(i);

            // Count consecutive same values
            while (i + 1 < diceRolls.size() &&
                    diceRolls.get(i + 1).equals(value) &&
                    !usedInStraight[i + 1]) {
                i++;
            }

            int length = i - start + 1;

            // Exactly 3 consecutive same values = triple
            if (length == 3) {
                List<Integer> segment = new ArrayList<>();
                for (int j = start; j <= i; j++) {
                    segment.add(diceRolls.get(j));
                    usedInTriple[j] = true;
                }
                triples.add(segment);
            }
            // If length > 3, it's NOT a triple - these dice will be counted as non-straight

            i++;
        }

        return triples;
    }

    /**
     * Calculates the reward for straight segments using configured multiplier.
     */
    private long calculateStraightReward(List<List<Integer>> straightSegments, long straightMultiplier) {
        long sum = 0;
        for (int i = 0; i < straightSegments.size(); i++) {
            List<Integer> segment = straightSegments.get(i);
            for (int j = 0; j < segment.size(); j++) {
                sum += segment.get(j);
            }
        }
        return sum * straightMultiplier;
    }

    /**
     * Calculates the reward for triple segments using configured bonuses.
     */
    private long calculateTripleReward(List<List<Integer>> tripleSegments, long tripleLowBonus, long tripleHighBonus) {
        long reward = 0;
        for (int i = 0; i < tripleSegments.size(); i++) {
            List<Integer> segment = tripleSegments.get(i);
            // Each triple segment always has exactly 3 elements with the same value
            int sum = segment.get(0) * 3;
            if (sum < 10) {
                reward += tripleLowBonus;
            } else {
                reward += tripleHighBonus;
            }
        }
        return reward;
    }

    /**
     * Calculates the sum of dice values not used in straights or triples.
     */
    private long calculateNonStraightSum(List<Integer> diceRolls, boolean[] usedInStraight, boolean[] usedInTriple) {
        long sum = 0;
        for (int i = 0; i < diceRolls.size(); i++) {
            if (!usedInStraight[i] && !usedInTriple[i]) {
                sum += diceRolls.get(i);
            }
        }
        return sum;
    }

    /**
     * Applies the reward to the member's currency account.
     * If the reward exceeds the max adjustment amount, splits into multiple adjustments.
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

    /**
     * Internal record for reward analysis results.
     */
    record RewardAnalysis(
            List<List<Integer>> straightSegments,
            List<List<Integer>> tripleSegments,
            long straightReward,
            long nonStraightReward,
            long tripleReward,
            long totalReward
    ) {}

    /**
     * Result of a dice game 2.
     */
    public record DiceGame2Result(
            long guildId,
            long userId,
            List<Integer> diceRolls,
            long totalReward,
            long previousBalance,
            long newBalance,
            List<List<Integer>> straightSegments,
            List<List<Integer>> tripleSegments,
            long straightReward,
            long nonStraightReward,
            long tripleReward
    ) {
        /**
         * Formats the result as a Discord message.
         */
        public String formatMessage(String currencyIcon, String currencyName) {
            StringBuilder sb = new StringBuilder();
            sb.append("**Dice Game 2 Results**\n");
            sb.append("Rolls: ");

            for (int i = 0; i < diceRolls.size(); i++) {
                int roll = diceRolls.get(i);
                sb.append(diceEmoji(roll));
                if (i < diceRolls.size() - 1) {
                    sb.append(" ");
                }
            }

            sb.append("\n\n");

            // Show reward breakdown
            if (!straightSegments.isEmpty()) {
                sb.append(String.format("Straights: %s %,d %s\n", currencyIcon, straightReward, currencyName));
            }
            if (!tripleSegments.isEmpty()) {
                sb.append(String.format("Triples: %s %,d %s (%d group%s)\n",
                        currencyIcon, tripleReward, currencyName,
                        tripleSegments.size(), tripleSegments.size() > 1 ? "s" : ""));
            }
            if (nonStraightReward > 0) {
                sb.append(String.format("Base: %s %,d %s\n", currencyIcon, nonStraightReward, currencyName));
            }

            sb.append("\n");
            sb.append(String.format("**Total Reward:** %s %,d %s\n", currencyIcon, totalReward, currencyName));
            sb.append(String.format("**New Balance:** %s %,d %s", currencyIcon, newBalance, currencyName));

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
