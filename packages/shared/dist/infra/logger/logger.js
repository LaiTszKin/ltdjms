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
export function createRootLogger(level = process.env.NODE_ENV === 'production' ? 'info' : 'debug') {
    return pino({
        level,
        transport: process.env.NODE_ENV !== 'production'
            ? {
                target: 'pino/file',
                options: { destination: 1 },
            }
            : undefined,
        formatters: {
            level(label) {
                return { level: label };
            },
        },
        timestamp: pino.stdTimeFunctions.isoTime,
    });
}
/**
 * Creates a child logger with additional context bindings.
 * @param parent - parent pino logger
 * @param bindings - context bindings (module, etc.)
 */
export function createChildLogger(parent, bindings) {
    return parent.child(bindings);
}
//# sourceMappingURL=logger.js.map