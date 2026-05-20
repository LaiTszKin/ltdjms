/**
 * Slash command latency tracking with p50/p95/p99 percentile calculation.
 * Uses a fixed-size sliding window to avoid unbounded memory usage.
 * Matches Java SlashCommandMetrics.
 */
export declare class SlashCommandMetrics {
    private static readonly WINDOW_SIZE;
    /** Per-command latency samples (ring buffer). */
    private readonly latencies;
    /** Per-command success counts. */
    private readonly successCounts;
    /** Per-command error counts. */
    private readonly errorCounts;
    /** Current write index per command (ring buffer). */
    private readonly indices;
    /** Current sample count per command. */
    private readonly counts;
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
    recordStart(_commandName: string): number;
    /**
     * Records a command execution end with its duration.
     * @param commandName - The command name
     * @param elapsedMs - Duration in milliseconds
     * @param success - Whether the execution was successful
     */
    recordEnd(commandName: string, elapsedMs: number, success: boolean): void;
    /**
     * Returns the current stats snapshot for all commands.
     */
    getStats(): Record<string, CommandStats>;
    /** Resets all metrics. */
    reset(): void;
}
export interface CommandStats {
    readonly p50: number;
    readonly p95: number;
    readonly p99: number;
    readonly successCount: number;
    readonly errorCount: number;
    readonly totalCount: number;
}
