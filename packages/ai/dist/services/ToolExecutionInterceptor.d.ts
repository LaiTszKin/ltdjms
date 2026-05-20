import pino from 'pino';
/**
 * Intercepts tool execution lifecycle events for observability.
 * Logs start, completion, and failure with timing information.
 *
 * TODO (P0-18): Wire this interceptor into the agent flow via AgentServiceFactory.
 *   The interceptor should be registered with a correlation ID per tool invocation,
 *   and onToolExecutionStarted/onToolExecutionCompleted/onToolExecutionFailed
 *   should be called by the LangChain agent executor callback layer.
 *   Once AgentServiceFactory.createAgent() configures the tool-execution pipeline,
 *   pass this interceptor so each tool call is wrapped with start/completion/failure logging.
 */
export declare class ToolExecutionInterceptor {
    private readonly logger;
    private durations;
    constructor(logger?: pino.Logger);
    /**
     * Called when a tool execution starts.
     * Logs the tool name and parameters at INFO level.
     *
     * @param toolName - The name of the tool being executed
     * @param params - The parameters passed to the tool
     * @returns A correlation ID that should be passed to onToolExecutionCompleted/onToolExecutionFailed
     */
    onToolExecutionStarted(toolName: string, params: Record<string, unknown>): string;
    /**
     * Called when a tool execution completes successfully.
     * Uses correlation ID from onToolExecutionStarted for accurate timing.
     *
     * @param correlationId - The correlation ID returned by onToolExecutionStarted
     * @param result - The result returned by the tool
     */
    onToolExecutionCompleted(correlationId: string, result: unknown): void;
    /**
     * Called when a tool execution fails.
     * Uses correlation ID from onToolExecutionStarted for accurate timing.
     *
     * @param correlationId - The correlation ID returned by onToolExecutionStarted
     * @param error - The error that occurred during tool execution
     */
    onToolExecutionFailed(correlationId: string, error: unknown): void;
    /**
     * Gets and clears the stored timing for a given correlation ID.
     * Returns 0 if no timing was recorded.
     */
    private getAndClearDuration;
}
