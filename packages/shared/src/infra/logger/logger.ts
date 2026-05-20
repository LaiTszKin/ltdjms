import pino from 'pino';

/**
 * Creates the root pino logger instance.
 * @param level - minimum log level (default 'info')
 *
 * Uses pino/file in non-production for readability.
 * TODO: replace with pino-pretty transport for improved dev ergonomics.
 */
export function createRootLogger(level: string = process.env.NODE_ENV === 'production' ? 'info' : 'debug'): pino.Logger {
  return pino({
    level,
    transport:
      process.env.NODE_ENV !== 'production'
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
export function createChildLogger(
  parent: pino.Logger,
  bindings: Record<string, unknown>,
): pino.Logger {
  return parent.child(bindings);
}
