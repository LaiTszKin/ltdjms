import { randomUUID } from 'node:crypto';
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
export class ToolExecutionInterceptor {
    logger;
    durations = new Map();
    constructor(logger) {
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
    onToolExecutionStarted(toolName, params) {
        const correlationId = randomUUID();
        this.durations.set(correlationId, Date.now());
        this.logger.info({
            event: 'tool_execution_started',
            timestamp: new Date().toISOString(),
            correlationId,
            toolName,
            params,
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
    onToolExecutionCompleted(correlationId, result) {
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
    onToolExecutionFailed(correlationId, error) {
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
    getAndClearDuration(correlationId) {
        const startTime = this.durations.get(correlationId);
        if (startTime === undefined)
            return 0;
        this.durations.delete(correlationId);
        return Date.now() - startTime;
    }
}
//# sourceMappingURL=ToolExecutionInterceptor.js.map