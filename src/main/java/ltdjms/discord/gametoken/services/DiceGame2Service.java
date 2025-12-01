package ltdjms.discord.gametoken.services;

import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service for the dice-game-2 mini-game.
 * Handles dice rolling, reward calculation with straights and triples, and currency distribution.
 *
 * <p>Reward rules:</p>
 * <ul>
 *   <li>15 dice rolls (1-6 each)</li>
 *   <li>Straights (consecutive increasing sequence of length >= 3): sum × 100,000</li>
 *   <li>Triples (exactly 3 consecutive same values):
 *     <ul>
 *       <li>Sum < 10 (values 1-3): 1,500,000 bonus</li>
 *       <li>Sum >= 10 (values 4-6): 2,500,000 bonus</li>
 *     </ul>
 *   </li>
 *   <li>4+ consecutive same values: NOT a triple, counted as non-straight</li>
 *   <li>Non-straight/non-triple dice: sum × 20,000</li>
 * </ul>
 */
public class DiceGame2Service {

    private static final Logger LOG = LoggerFactory.getLogger(DiceGame2Service.class);

    /**
     * Number of dice rolls per game.
     */
    public static final int ROLLS_PER_GAME = 15;

    /**
     * Multiplier for dice values in a straight segment.
     */
    public static final long STRAIGHT_MULTIPLIER = 100_000L;

    /**
     * Multiplier for non-straight, non-triple dice values.
     */
    public static final long NON_STRAIGHT_MULTIPLIER = 20_000L;

    /**
     * Bonus for a triple where sum < 10 (values 1, 2, or 3).
     */
    public static final long TRIPLE_LOW_BONUS = 1_500_000L;

    /**
     * Bonus for a triple where sum >= 10 (values 4, 5, or 6).
     */
    public static final long TRIPLE_HIGH_BONUS = 2_500_000L;

    private final MemberCurrencyAccountRepository currencyRepository;
    private final Random random;

    public DiceGame2Service(MemberCurrencyAccountRepository currencyRepository) {
        this(currencyRepository, new Random());
    }

    /**
     * Constructor with injectable Random for testing.
     */
    public DiceGame2Service(MemberCurrencyAccountRepository currencyRepository, Random random) {
        this.currencyRepository = currencyRepository;
        this.random = random;
    }

    /**
     * Plays the dice game for a member.
     * Rolls 15 dice and calculates the total reward based on straights, triples, and remaining dice.
     * The reward is added to the member's currency account.
     *
     * @param guildId the Discord guild ID
     * @param userId  the Discord user ID
     * @return the game result
     */
    public DiceGame2Result play(long guildId, long userId) {
        LOG.debug("Playing dice-game-2 for guildId={}, userId={}", guildId, userId);

        // Roll dice
        List<Integer> diceRolls = rollDice();

        // Analyze the rolls
        RewardAnalysis analysis = analyzeRolls(diceRolls);

        // Calculate total reward
        long totalReward = analysis.totalReward();

        // Apply reward to currency account
        long previousBalance = currencyRepository.findOrCreate(guildId, userId).balance();
        applyRewardToCurrency(guildId, userId, totalReward);
        long newBalance = currencyRepository.findByGuildIdAndUserId(guildId, userId)
                .map(MemberCurrencyAccount::balance)
                .orElse(previousBalance + totalReward);

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
     * Rolls the dice for a game.
     *
     * @return list of dice values (1-6)
     */
    List<Integer> rollDice() {
        List<Integer> rolls = new ArrayList<>(ROLLS_PER_GAME);
        for (int i = 0; i < ROLLS_PER_GAME; i++) {
            rolls.add(random.nextInt(6) + 1);  // 1-6
        }
        return rolls;
    }

    /**
     * Analyzes the dice rolls to identify straights, triples, and calculate rewards.
     *
     * @param diceRolls the list of dice values
     * @return the analysis result
     */
    RewardAnalysis analyzeRolls(List<Integer> diceRolls) {
        boolean[] usedInStraight = new boolean[diceRolls.size()];
        boolean[] usedInTriple = new boolean[diceRolls.size()];

        // First pass: identify straights (consecutive increasing sequences of length >= 3)
        List<List<Integer>> straightSegments = findStraights(diceRolls, usedInStraight);

        // Second pass: identify triples (exactly 3 consecutive same values)
        // Must not overlap with straights
        List<List<Integer>> tripleSegments = findTriples(diceRolls, usedInStraight, usedInTriple);

        // Calculate rewards
        long straightReward = calculateStraightReward(straightSegments);
        long tripleReward = calculateTripleReward(tripleSegments);
        long nonStraightSum = calculateNonStraightSum(diceRolls, usedInStraight, usedInTriple);
        long nonStraightReward = nonStraightSum * NON_STRAIGHT_MULTIPLIER;

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
     * Calculates the reward for straight segments.
     */
    private long calculateStraightReward(List<List<Integer>> straightSegments) {
        long sum = 0;
        for (List<Integer> segment : straightSegments) {
            for (int value : segment) {
                sum += value;
            }
        }
        return sum * STRAIGHT_MULTIPLIER;
    }

    /**
     * Calculates the reward for triple segments.
     */
    private long calculateTripleReward(List<List<Integer>> tripleSegments) {
        long reward = 0;
        for (List<Integer> segment : tripleSegments) {
            int sum = segment.stream().mapToInt(Integer::intValue).sum();
            if (sum < 10) {
                reward += TRIPLE_LOW_BONUS;
            } else {
                reward += TRIPLE_HIGH_BONUS;
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
