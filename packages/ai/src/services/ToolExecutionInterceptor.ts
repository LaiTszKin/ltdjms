import { randomUUID } from 'node:crypto';
import pino from 'pino';

/**
 * Intercepts tool execution lifecycle events for observability.
 * Logs start, completion, and failure with timing information.
 */
export class ToolExecutionInterceptor {
  private readonly logger: pino.Logger;
  private durations = new Map<string, { startTime: number; timer: ReturnType<typeof setTimeout> }>();

  constructor(logger?: pino.Logger) {
    this.logger = logger ?? pino({ name: 'tool-execution-interceptor' });
  }

  /**
   * Called when a tool execution starts.
   * Logs the tool name and parameters at INFO level.
   *
   * @param toolName - The name of the tool being executed
   * @param params - The parameters passed to the tool
   * @returns A correlation ID that should be passed to onToolExecutionCompleted/onToolExecutionFailed
   */
  onToolExecutionStarted(toolName: string, params: Record<string, unknown>): string {
    const correlationId = randomUUID();
    const timer = setTimeout(() => {
      this.durations.delete(correlationId);
    }, 60000).unref();
    this.durations.set(correlationId, { startTime: Date.now(), timer });

    this.logger.info({
      event: 'tool_execution_started',
      timestamp: new Date().toISOString(),
      correlationId,
      toolName,
      paramKeys: Object.keys(params),
    }, `Tool execution started: ${toolName}`);

    return correlationId;
  }

  /**
   * Called when a tool execution completes successfully.
   * Uses correlation ID from onToolExecutionStarted for accurate timing.
   *
   * @param correlationId - The correlation ID returned by onToolExecutionStarted
   * @param result - The result returned by the tool
   */
  onToolExecutionCompleted(correlationId: string, result: unknown): void {
    const duration = this.getAndClearDuration(correlationId);

    this.logger.info({
      event: 'tool_execution_completed',
      timestamp: new Date().toISOString(),
      correlationId,
      durationMs: duration,
      success: true,
      result,
    }, `Tool execution completed (${duration}ms)`);
  }

  /**
   * Called when a tool execution fails.
   * Uses correlation ID from onToolExecutionStarted for accurate timing.
   *
   * @param correlationId - The correlation ID returned by onToolExecutionStarted
   * @param error - The error that occurred during tool execution
   */
  onToolExecutionFailed(correlationId: string, error: unknown): void {
    const duration = this.getAndClearDuration(correlationId);
    const message = error instanceof Error ? error.message : String(error);

    this.logger.info({
      event: 'tool_execution_failed',
      timestamp: new Date().toISOString(),
      correlationId,
      durationMs: duration,
      success: false,
      error: message,
    }, `Tool execution failed (${duration}ms): ${message}`);
  }

  /**
   * Gets and clears the stored timing for a given correlation ID.
   * Returns 0 if no timing was recorded.
   */
  private getAndClearDuration(correlationId: string): number {
    const entry = this.durations.get(correlationId);
    if (entry === undefined) return 0;
    clearTimeout(entry.timer);
    this.durations.delete(correlationId);
    return Date.now() - entry.startTime;
  }
}
