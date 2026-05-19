import pino from 'pino';

/**
 * Intercepts tool execution lifecycle events for observability.
 * Logs start, completion, and failure with timing information.
 */
export class ToolExecutionInterceptor {
  private readonly logger: pino.Logger;
  private startTimes = new Map<string, number>();

  constructor(logger?: pino.Logger) {
    this.logger = logger ?? pino({ name: 'tool-execution-interceptor' });
  }

  /**
   * Called when a tool execution starts.
   * Logs the tool name and parameters at INFO level.
   *
   * @param toolName - The name of the tool being executed
   * @param params - The parameters passed to the tool
   */
  onToolExecutionStarted(toolName: string, params: Record<string, unknown>): void {
    const startTime = Date.now();
    this.startTimes.set(toolName, startTime);

    this.logger.info({
      event: 'tool_execution_started',
      timestamp: new Date().toISOString(),
      toolName,
      params,
    }, `Tool execution started: ${toolName}`);
  }

  /**
   * Called when a tool execution completes successfully.
   * Logs the result and duration at INFO level.
   *
   * @param result - The result returned by the tool
   */
  onToolExecutionCompleted(result: unknown): void {
    const duration = this.getAndClearDuration();

    this.logger.info({
      event: 'tool_execution_completed',
      timestamp: new Date().toISOString(),
      durationMs: duration,
      success: true,
      result,
    }, `Tool execution completed (${duration}ms)`);
  }

  /**
   * Called when a tool execution fails.
   * Logs the error and duration at INFO level.
   *
   * @param error - The error that occurred during tool execution
   */
  onToolExecutionFailed(error: unknown): void {
    const duration = this.getAndClearDuration();
    const message = error instanceof Error ? error.message : String(error);

    this.logger.info({
      event: 'tool_execution_failed',
      timestamp: new Date().toISOString(),
      durationMs: duration,
      success: false,
      error: message,
    }, `Tool execution failed (${duration}ms): ${message}`);
  }

  /**
   * Gets and clears the stored start time duration.
   * Returns 0 if no start time was recorded.
   */
  private getAndClearDuration(): number {
    // The last started tool is the one that finished
    // We use the last entry as a simple stack
    let duration = 0;
    let lastKey: string | undefined;

    for (const key of this.startTimes.keys()) {
      lastKey = key;
    }

    if (lastKey) {
      const startTime = this.startTimes.get(lastKey)!;
      duration = Date.now() - startTime;
      this.startTimes.delete(lastKey);
    }

    return duration;
  }
}
