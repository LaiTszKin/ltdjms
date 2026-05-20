import pino from 'pino';
/**
 * Creates the root pino logger instance.
 * @param level - minimum log level (default 'info')
 *
 * Uses pino/file in non-production for readability.
 * TODO: replace with pino-pretty transport for improved dev ergonomics.
 */
export declare function createRootLogger(level?: string): pino.Logger;
/**
 * Creates a child logger with additional context bindings.
 * @param parent - parent pino logger
 * @param bindings - context bindings (module, etc.)
 */
export declare function createChildLogger(parent: pino.Logger, bindings: Record<string, unknown>): pino.Logger;
