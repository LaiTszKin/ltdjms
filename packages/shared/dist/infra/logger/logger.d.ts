import pino from 'pino';
/**
 * Creates the root pino logger instance.
 * @param level - minimum log level (default 'info')
 *
 * NOTE: In dev environments, pino's default JSON output is hard to read by humans.
 * The Java equivalent uses Logback's human-readable pattern layout.
 * To improve dev ergonomics, add pino-pretty as a devDependency and use it here:
 *
 *   transport: { target: 'pino-pretty', options: { colorize: true } }
 *
 * Currently uses pino/file to avoid adding a dependency that may not be available.
 */
export declare function createRootLogger(level?: string): pino.Logger;
/**
 * Creates a child logger with additional context bindings.
 * @param parent - parent pino logger
 * @param bindings - context bindings (module, etc.)
 */
export declare function createChildLogger(parent: pino.Logger, bindings: Record<string, unknown>): pino.Logger;
