package ltdjms.discord.currency.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 單元測試：SlashCommandMetrics
 */
class SlashCommandMetricsTest {

    @Test
    @DisplayName("當沒有任何指令時，聚合指標應為 0 且符合 SC-001")
    void shouldReturnZeroAggregatedMetricsWhenNoCommands() {
        SlashCommandMetrics metrics = new SlashCommandMetrics();

        SlashCommandMetrics.AggregatedMetrics aggregated = metrics.getAggregatedMetrics();

        assertThat(aggregated.totalCommands()).isZero();
        assertThat(aggregated.totalErrors()).isZero();
        assertThat(aggregated.errorRate()).isZero();
        assertThat(aggregated.p95Latency()).isZero();
        assertThat(aggregated.meetsSC001()).isTrue();
    }

    @Test
    @DisplayName("應能追蹤並重設指令指標")
    void shouldTrackAndResetMetricsCorrectly() {
        SlashCommandMetrics metrics = new SlashCommandMetrics();

        // 模擬執行成功與失敗的指令
        for (int i = 0; i < 10; i++) {
            SlashCommandMetrics.ExecutionContext ctx = metrics.recordStart("balance");
            metrics.recordEnd(ctx, true);
        }

        for (int i = 0; i < 3; i++) {
            SlashCommandMetrics.ExecutionContext ctx = metrics.recordStart("adjust-balance");
            metrics.recordEnd(ctx, false);
        }

        SlashCommandMetrics.MetricsSummary balanceSummary = metrics.getCommandMetrics("balance");
        SlashCommandMetrics.MetricsSummary adjustSummary = metrics.getCommandMetrics("adjust-balance");

        assertThat(balanceSummary).isNotNull();
        assertThat(balanceSummary.totalCount()).isEqualTo(10);
        assertThat(balanceSummary.successCount()).isEqualTo(10);
        assertThat(balanceSummary.errorCount()).isZero();

        assertThat(adjustSummary).isNotNull();
        assertThat(adjustSummary.totalCount()).isEqualTo(3);
        assertThat(adjustSummary.errorCount()).isEqualTo(3);

        // 重設後，所有指標應清空
        metrics.reset();

        assertThat(metrics.getCommandMetrics("balance")).isNull();
        assertThat(metrics.getCommandMetrics("adjust-balance")).isNull();

        SlashCommandMetrics.AggregatedMetrics aggregatedAfterReset = metrics.getAggregatedMetrics();
        assertThat(aggregatedAfterReset.totalCommands()).isZero();
        assertThat(aggregatedAfterReset.totalErrors()).isZero();
    }

    @Test
    @DisplayName("對不存在的指令名稱應回傳 null 指標")
    void shouldReturnNullForUnknownCommand() {
        SlashCommandMetrics metrics = new SlashCommandMetrics();

        assertThat(metrics.getCommandMetrics("unknown-command")).isNull();
    }

    @Test
    @DisplayName("logMetricsSummary 不應丟出例外")
    void logMetricsSummaryShouldNotThrow() {
        SlashCommandMetrics metrics = new SlashCommandMetrics();

        // 建立一些指標資料
        for (int i = 0; i < 5; i++) {
            SlashCommandMetrics.ExecutionContext ctx = metrics.recordStart("balance");
            metrics.recordEnd(ctx, i % 2 == 0);
        }

        // 僅驗證呼叫不會丟出例外（實際輸出由 logger 處理）
        metrics.logMetricsSummary();
    }
}

