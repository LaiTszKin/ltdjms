import pino from 'pino';
/**
 * Creates the root pino logger instance.
 * @param level - minimum log level (default 'info')
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