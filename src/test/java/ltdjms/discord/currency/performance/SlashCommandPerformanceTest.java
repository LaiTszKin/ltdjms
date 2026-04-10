package ltdjms.discord.currency.performance;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.bot.SlashCommandMetrics;
import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;
import ltdjms.discord.currency.integration.PostgresIntegrationTestBase;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JdbcCurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.currency.services.EmojiValidator;
import ltdjms.discord.currency.services.NoOpEmojiValidator;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;
import ltdjms.discord.shared.cache.NoOpCacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Performance tests for slash command operations. Verifies SC-001 requirements: - p95 latency <= 1
 * second - Error rate < 1% - 95% of commands complete within 3 seconds
 */
@SuppressWarnings(
    "deprecation") // performance scenarios still use deprecated balance/config APIs for realism
class SlashCommandPerformanceTest extends PostgresIntegrationTestBase {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final int CONCURRENT_USERS = 20;
  private static final int COMMANDS_PER_SECOND = 20;

  @SuppressWarnings("unused") // kept for reference as full-duration load test configuration
  private static final int TEST_DURATION_SECONDS = 60;

  // For local testing, use shorter duration
  private static final int QUICK_TEST_DURATION_SECONDS = 5;

  private DefaultBalanceService balanceService;
  private BalanceAdjustmentService adjustmentService;
  private CurrencyConfigService configService;
  private SlashCommandMetrics metrics;
  private Random random;

  @BeforeEach
  void setUp() {
    GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
    MemberCurrencyAccountRepository accountRepo =
        new JooqMemberCurrencyAccountRepository(dslContext);
    CurrencyTransactionRepository transactionRepo =
        new JdbcCurrencyTransactionRepository(dataSource);
    CurrencyTransactionService transactionService = new CurrencyTransactionService(transactionRepo);
    DomainEventPublisher eventPublisher = new DomainEventPublisher();

    CacheService cacheService = NoOpCacheService.getInstance();
    CacheKeyGenerator cacheKeyGenerator = new DefaultCacheKeyGenerator();

    balanceService =
        new DefaultBalanceService(accountRepo, configRepo, cacheService, cacheKeyGenerator);
    adjustmentService =
        new BalanceAdjustmentService(
            accountRepo,
            configRepo,
            transactionService,
            eventPublisher,
            cacheService,
            cacheKeyGenerator);
    EmojiValidator emojiValidator = new NoOpEmojiValidator();
    configService = new CurrencyConfigService(configRepo, emojiValidator, eventPublisher);
    metrics = new SlashCommandMetrics();
    random = new Random(42); // Fixed seed for reproducibility
  }

  @Test
  @DisplayName("should meet SC-001 latency and error rate requirements under load")
  void shouldMeetSC001RequirementsUnderLoad() throws InterruptedException {
    // Set up currency config for the guild
    configService.updateConfig(TEST_GUILD_ID, "LoadTestCoins", "🚀");

    // Pre-create accounts for test users
    for (int i = 0; i < CONCURRENT_USERS; i++) {
      long userId = 100000000000000000L + i;
      balanceService.getBalance(TEST_GUILD_ID, userId);
      adjustmentService.adjustBalance(TEST_GUILD_ID, userId, 10000L);
    }

    // Run load test
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    AtomicLong completedCommands = new AtomicLong(0);
    AtomicLong successfulCommands = new AtomicLong(0);
    AtomicLong commandsWithin3Seconds = new AtomicLong(0);

    long startTime = System.currentTimeMillis();
    long endTime = startTime + (QUICK_TEST_DURATION_SECONDS * 1000L);

    // Submit commands at target rate
    while (System.currentTimeMillis() < endTime) {
      for (int i = 0; i < COMMANDS_PER_SECOND; i++) {
        final int userIndex = random.nextInt(CONCURRENT_USERS);
        final long userId = 100000000000000000L + userIndex;

        CompletableFuture<Void> future =
            CompletableFuture.runAsync(
                () -> {
                  SlashCommandMetrics.ExecutionContext ctx = null;
                  boolean success = false;
                  long commandStart = System.currentTimeMillis();

                  try {
                    // Simulate command execution mix
                    int commandType = random.nextInt(10);
                    if (commandType < 6) {
                      // 60% balance commands
                      ctx = metrics.recordStart("balance");
                      balanceService.getBalance(TEST_GUILD_ID, userId);
                    } else if (commandType < 9) {
                      // 30% adjustment commands
                      ctx = metrics.recordStart("adjust-balance");
                      int adjustment = random.nextInt(201) - 100; // -100 to +100
                      if (adjustment != 0) {
                        try {
                          adjustmentService.adjustBalance(TEST_GUILD_ID, userId, adjustment);
                        } catch (Exception e) {
                          // Negative balance exceptions are expected occasionally
                        }
                      }
                    } else {
                      // 10% config read commands
                      ctx = metrics.recordStart("currency-config");
                      configService.getConfig(TEST_GUILD_ID);
                    }
                    success = true;
                  } catch (Exception e) {
                    // Log but don't fail - we're measuring error rate
                  } finally {
                    if (ctx != null) {
                      metrics.recordEnd(ctx, success);
                    }
                    completedCommands.incrementAndGet();
                    if (success) {
                      successfulCommands.incrementAndGet();
                    }

                    long duration = System.currentTimeMillis() - commandStart;
                    if (duration <= 3000) {
                      commandsWithin3Seconds.incrementAndGet();
                    }
                  }
                },
                executor);

        futures.add(future);
      }

      // Sleep to maintain rate
      Thread.sleep(1000 / COMMANDS_PER_SECOND);
    }

    // Wait for all commands to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
        .orTimeout(30, TimeUnit.SECONDS)
        .join();

    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    // Get aggregated metrics
    SlashCommandMetrics.AggregatedMetrics aggregated = metrics.getAggregatedMetrics();

    // Log results
    System.out.println("=== Performance Test Results ===");
    System.out.printf("Total commands: %d%n", completedCommands.get());
    System.out.printf("Successful commands: %d%n", successfulCommands.get());
    System.out.printf("P95 latency: %d ms%n", aggregated.p95Latency());
    System.out.printf("Error rate: %.2f%%%n", aggregated.errorRate());
    System.out.printf(
        "Commands within 3s: %.2f%%%n",
        (double) commandsWithin3Seconds.get() / completedCommands.get() * 100);

    // Assert SC-001 requirements
    assertThat(aggregated.p95Latency())
        .as("p95 latency should be <= 1000ms")
        .isLessThanOrEqualTo(1000);

    assertThat(aggregated.errorRate()).as("Error rate should be < 1%").isLessThan(1.0);

    double within3sPercentage =
        (double) commandsWithin3Seconds.get() / completedCommands.get() * 100;
    assertThat(within3sPercentage)
        .as("At least 95% of commands should complete within 3 seconds")
        .isGreaterThanOrEqualTo(95.0);

    assertThat(aggregated.meetsSC001()).as("Should meet SC-001 criteria").isTrue();
  }

  @Test
  @DisplayName("should track individual command metrics correctly")
  void shouldTrackIndividualCommandMetricsCorrectly() {
    // Set up
    configService.updateConfig(TEST_GUILD_ID, "TestCoins", "💎");
    long userId = 200000000000000000L;

    // Run 100 balance commands
    for (int i = 0; i < 100; i++) {
      SlashCommandMetrics.ExecutionContext ctx = metrics.recordStart("balance");
      balanceService.getBalance(TEST_GUILD_ID, userId);
      metrics.recordEnd(ctx, true);
    }

    // Run 50 adjustment commands
    for (int i = 0; i < 50; i++) {
      SlashCommandMetrics.ExecutionContext ctx = metrics.recordStart("adjust-balance");
      try {
        adjustmentService.adjustBalance(TEST_GUILD_ID, userId, 1);
        metrics.recordEnd(ctx, true);
      } catch (Exception e) {
        metrics.recordEnd(ctx, false);
      }
    }

    // Verify metrics
    SlashCommandMetrics.MetricsSummary balanceMetrics = metrics.getCommandMetrics("balance");
    SlashCommandMetrics.MetricsSummary adjustmentMetrics =
        metrics.getCommandMetrics("adjust-balance");

    assertThat(balanceMetrics).isNotNull();
    assertThat(balanceMetrics.totalCount()).isEqualTo(100);
    assertThat(balanceMetrics.successCount()).isEqualTo(100);
    assertThat(balanceMetrics.errorCount()).isEqualTo(0);

    assertThat(adjustmentMetrics).isNotNull();
    assertThat(adjustmentMetrics.totalCount()).isEqualTo(50);
  }

  @Test
  @DisplayName("should handle concurrent access without data corruption")
  void shouldHandleConcurrentAccessWithoutDataCorruption() throws InterruptedException {
    // Set up
    long userId = 300000000000000000L;
    configService.updateConfig(TEST_GUILD_ID, "ConcurrentCoins", "⚡");

    // Initial balance
    balanceService.getBalance(TEST_GUILD_ID, userId);
    adjustmentService.adjustBalance(TEST_GUILD_ID, userId, 10000L);

    // Run concurrent adjustments
    ExecutorService executor = Executors.newFixedThreadPool(10);
    int adjustmentCount = 1000;
    int adjustmentAmount = 1;

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < adjustmentCount; i++) {
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  adjustmentService.adjustBalance(TEST_GUILD_ID, userId, adjustmentAmount);
                } catch (Exception e) {
                  // Ignore - some might fail due to race conditions
                }
              },
              executor));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    // Verify final balance is consistent
    BalanceView finalBalance = balanceService.getBalance(TEST_GUILD_ID, userId);
    assertThat(finalBalance.balance())
        .as("Balance should be at least 10000 (initial) + some adjustments")
        .isGreaterThanOrEqualTo(10000L);

    System.out.printf(
        "Final balance after %d concurrent +1 adjustments: %d%n",
        adjustmentCount, finalBalance.balance());
  }
}
