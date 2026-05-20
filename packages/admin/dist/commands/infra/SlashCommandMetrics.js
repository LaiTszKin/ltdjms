/**
 * Slash command latency tracking with p50/p95/p99 percentile calculation.
 * Uses a fixed-size sliding window to avoid unbounded memory usage.
 * Matches Java SlashCommandMetrics.
 */
export class SlashCommandMetrics {
    static WINDOW_SIZE = 1000;
    /** Per-command latency samples (ring buffer). */
    latencies = new Map();
    /** Per-command success counts. */
    successCounts = new Map();
    /** Per-command error counts. */
    errorCounts = new Map();
    /** Current write index per command (ring buffer). */
    indices = new Map();
    /** Current sample count per command. */
    counts = new Map();
    /**
     * Records a command execution start.
     * Returns a start timestamp for use with recordEnd.
     *
     * TODO(P3-28): The `commandName` parameter is accepted for future per-command
     * latency tracking. Currently the metrics store uses a flat ring buffer that
     * does not distinguish between commands at the start phase. When per-command
     * breakdown is needed, initialize per-command start times here instead of
     * relying solely on the aggregate timestamp.
     */
    recordStart(_commandName) {
        return performance.now();
    }
    /**
     * Records a command execution end with its duration.
     * @param commandName - The command name
     * @param elapsedMs - Duration in milliseconds
     * @param success - Whether the execution was successful
     */
    recordEnd(commandName, elapsedMs, success) {
        // Update ring buffer
        if (!this.latencies.has(commandName)) {
            this.latencies.set(commandName, new Array(SlashCommandMetrics.WINDOW_SIZE).fill(0));
            this.indices.set(commandName, 0);
            this.counts.set(commandName, 0);
        }
        const buffer = this.latencies.get(commandName);
        const idx = this.indices.get(commandName);
        buffer[idx] = elapsedMs;
        this.indices.set(commandName, (idx + 1) % SlashCommandMetrics.WINDOW_SIZE);
        this.counts.set(commandName, this.counts.get(commandName) + 1);
        // Update counters
        if (success) {
            this.successCounts.set(commandName, (this.successCounts.get(commandName) ?? 0) + 1);
        }
        else {
            this.errorCounts.set(commandName, (this.errorCounts.get(commandName) ?? 0) + 1);
        }
    }
    /**
     * Returns the current stats snapshot for all commands.
     */
    getStats() {
        const stats = {};
        for (const [name, buffer] of this.latencies) {
            const count = Math.min(this.counts.get(name) ?? 0, SlashCommandMetrics.WINDOW_SIZE);
            const samples = buffer.slice(0, count).sort((a, b) => a - b);
            stats[name] = {
                p50: percentile(samples, 50),
                p95: percentile(samples, 95),
                p99: percentile(samples, 99),
                successCount: this.successCounts.get(name) ?? 0,
                errorCount: this.errorCounts.get(name) ?? 0,
                totalCount: (this.successCounts.get(name) ?? 0) + (this.errorCounts.get(name) ?? 0),
            };
        }
        return stats;
    }
    /** Resets all metrics. */
    reset() {
        this.latencies.clear();
        this.successCounts.clear();
        this.errorCounts.clear();
        this.indices.clear();
        this.counts.clear();
    }
}
function percentile(sortedSamples, p) {
    if (sortedSamples.length === 0)
        return 0;
    const k = Math.ceil((p / 100) * sortedSamples.length) - 1;
    return sortedSamples[Math.max(0, k)];
}
//# sourceMappingURL=SlashCommandMetrics.js.map