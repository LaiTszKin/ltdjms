package ltdjms.discord.currency.bot;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects and reports metrics for slash command execution. Tracks latency (p50, p95, p99),
 * success/error counts, and throughput.
 */
public class SlashCommandMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(SlashCommandMetrics.class);

  // Per-command metrics storage
  private final Map<String, CommandMetrics> commandMetrics = new ConcurrentHashMap<>();

  // Global metrics
  private final LongAdder totalCommands = new LongAdder();
  private final LongAdder totalErrors = new LongAdder();

  /**
   * Records the start of a command execution.
   *
   * @param commandName the name of the command
   * @return a context object to pass to recordEnd
   */
  public ExecutionContext recordStart(String commandName) {
    return new ExecutionContext(commandName, Instant.now());
  }

  /**
   * Records the end of a command execution.
   *
   * @param context the execution context from recordStart
   * @param succeeded true if the command succeeded, false if it failed
   */
  public void recordEnd(ExecutionContext context, boolean succeeded) {
    Duration duration = Duration.between(context.startTime(), Instant.now());
    long latencyMs = duration.toMillis();

    CommandMetrics metrics =
        commandMetrics.computeIfAbsent(context.commandName(), k -> new CommandMetrics());

    metrics.recordExecution(latencyMs, succeeded);
    totalCommands.increment();

    if (!succeeded) {
      totalErrors.increment();
    }

    LOG.debug(
        "Command {} completed in {}ms, success={}", context.commandName(), latencyMs, succeeded);
  }

  /**
   * Gets metrics for a specific command.
   *
   * @param commandName the command name
   * @return the metrics summary, or null if no data
   */
  public MetricsSummary getCommandMetrics(String commandName) {
    CommandMetrics metrics = commandMetrics.get(commandName);
    if (metrics == null) {
      return null;
    }
    return metrics.getSummary(commandName);
  }

  /**
   * Gets aggregated metrics for all currency-related commands.
   *
   * @return the aggregated metrics summary
   */
  public AggregatedMetrics getAggregatedMetrics() {
    long total = totalCommands.sum();
    long errors = totalErrors.sum();
    double errorRate = total > 0 ? (double) errors / total * 100 : 0;

    // Combine all latencies for p95 calculation
    long[] allLatencies =
        commandMetrics.values().stream()
            .flatMapToLong(cm -> Arrays.stream(cm.getLatencies()))
            .toArray();

    long p95 = calculatePercentile(allLatencies, 95);

    return new AggregatedMetrics(total, errors, errorRate, p95);
  }

  /** Logs a summary of all metrics. */
  public void logMetricsSummary() {
    LOG.info("=== Slash Command Metrics Summary ===");

    commandMetrics.forEach(
        (name, metrics) -> {
          MetricsSummary summary = metrics.getSummary(name);
          LOG.info(
              "Command [{}]: total={}, success={}, errors={}, p50={}ms, p95={}ms, p99={}ms",
              summary.commandName(),
              summary.totalCount(),
              summary.successCount(),
              summary.errorCount(),
              summary.p50Latency(),
              summary.p95Latency(),
              summary.p99Latency());
        });

    AggregatedMetrics aggregated = getAggregatedMetrics();
    LOG.info(
        "Aggregated: total={}, errors={}, errorRate={:.2f}%, p95={}ms",
        aggregated.totalCommands(),
        aggregated.totalErrors(),
        aggregated.errorRate(),
        aggregated.p95Latency());
  }

  /** Resets all metrics. Useful for testing. */
  public void reset() {
    commandMetrics.clear();
    totalCommands.reset();
    totalErrors.reset();
  }

  /** Calculates a percentile from a sorted array of latencies. */
  private static long calculatePercentile(long[] values, int percentile) {
    if (values.length == 0) {
      return 0;
    }
    long[] sorted = values.clone();
    Arrays.sort(sorted);
    int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
  }

  /** Execution context for tracking command duration. */
  public record ExecutionContext(String commandName, Instant startTime) {}

  /** Summary of metrics for a single command. */
  public record MetricsSummary(
      String commandName,
      long totalCount,
      long successCount,
      long errorCount,
      long p50Latency,
      long p95Latency,
      long p99Latency) {}

  /** Aggregated metrics across all commands. */
  public record AggregatedMetrics(
      long totalCommands, long totalErrors, double errorRate, long p95Latency) {
    /** Checks if the metrics meet SC-001 criteria. - p95 latency <= 1000ms - error rate < 1% */
    public boolean meetsSC001() {
      return p95Latency <= 1000 && errorRate < 1.0;
    }
  }

  /** Internal class for tracking per-command metrics. */
  private static class CommandMetrics {
    private static final int MAX_SAMPLES = 10000;

    private final LongAdder successCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final long[] latencies = new long[MAX_SAMPLES];
    private final AtomicLong sampleIndex = new AtomicLong(0);

    void recordExecution(long latencyMs, boolean succeeded) {
      if (succeeded) {
        successCount.increment();
      } else {
        errorCount.increment();
      }

      // Store latency in circular buffer
      int index = (int) (sampleIndex.getAndIncrement() % MAX_SAMPLES);
      latencies[index] = latencyMs;
    }

    long[] getLatencies() {
      int count = (int) Math.min(sampleIndex.get(), MAX_SAMPLES);
      return Arrays.copyOf(latencies, count);
    }

    MetricsSummary getSummary(String commandName) {
      long total = successCount.sum() + errorCount.sum();
      long[] samples = getLatencies();

      return new MetricsSummary(
          commandName,
          total,
          successCount.sum(),
          errorCount.sum(),
          calculatePercentile(samples, 50),
          calculatePercentile(samples, 95),
          calculatePercentile(samples, 99));
    }
  }
}
